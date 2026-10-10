'use strict';
// Desktop adaptation of MaximaConnection/ParlonsContact. Only this main-process
// client holds credentials. Protocol: desktop/PARLONS-INTEGRATION-SPEC.txt.
const http = require('node:http');
const fs = require('node:fs/promises');
const path = require('node:path');
const crypto = require('node:crypto');

const PROVIDERS = Object.freeze({desktop: 'Parlons Desktop', core: 'minimaCore Desktop'});
const HEX = /^[a-f0-9]{64}$/;
const PUBLIC_KEY = /^0x(?:[a-fA-F0-9]{2}){32,1024}$/;
const MAX_BODY = 32768, MAX_RESPONSE = 262144;
const ROOT = '/minimadocs/v1/';
const fail = message => { throw new Error(message); };
function text(value, limit, label) {
  if (typeof value !== 'string' || value.length > limit) fail('Invalid Parlons ' + label + '.');
  return value;
}
function fingerprint(publicKey) {
  if (!PUBLIC_KEY.test(publicKey)) fail('Invalid Parlons account key.');
  return crypto.createHash('sha256').update(Buffer.from(publicKey.slice(2), 'hex')).digest('hex');
}
function parseLink(value) {
  text(value, 512, 'connection link');
  let u;
  try { u = new URL(value.trim()); } catch (_) { fail('Paste the connection link from Parlons Connected apps.'); }
  const fields = [...u.searchParams.keys()];
  if (u.protocol !== 'minimadocs:' || u.hostname !== 'parlons' || u.pathname !== '/v1'
      || u.username || u.password || u.port || u.hash || fields.length !== 4
      || new Set(fields).size !== 4 || fields.some(k => !['port', 'provider', 'account', 'code'].includes(k))) {
    fail('Paste the connection link from Parlons Connected apps.');
  }
  const port = Number(u.searchParams.get('port')), provider = u.searchParams.get('provider');
  const account = u.searchParams.get('account'), code = u.searchParams.get('code');
  if (!/^\d{1,5}$/.test(u.searchParams.get('port')) || !Number.isInteger(port) || port < 1 || port > 65535
      || !Object.hasOwn(PROVIDERS, provider) || !HEX.test(account) || !HEX.test(code)) {
    fail('This Parlons connection link is invalid. Create a new one in Connected apps.');
  }
  return {port, provider, account, code};
}
function metadata(c) {
  return {id: c.provider + ':' + c.account, provider: c.provider, host: PROVIDERS[c.provider], name: c.name, account: c.account};
}
function contact(row) {
  if (!row || typeof row !== 'object' || !PUBLIC_KEY.test(row.key)) fail('Parlons returned an unreadable contact.');
  const name = text(row.name, 1024, 'contact name').trim().replace(/[\x00-\x1f\x7f]/g, ' ') || 'Unnamed contact';
  const address = text(row.address, 8192, 'contact address');
  return {key: row.key.toLowerCase(), name, address};
}
function contacts(rows) {
  if (!Array.isArray(rows) || rows.length > 4096) fail('Parlons returned an unreadable contact list.');
  const seen = new Set(), result = [];
  for (const row of rows) { const c = contact(row); if (!seen.has(c.key)) { seen.add(c.key); result.push(c); } }
  return result.sort((a, b) => a.name.localeCompare(b.name));
}
function invitation(row) {
  if (!row || !HEX.test(row.id) || !PUBLIC_KEY.test(row.from)) fail('Parlons returned an unreadable invitation.');
  return {id: row.id, from: row.from.toLowerCase(), line: text(row.line, 16384, 'invitation')};
}
function validateConnection(c) {
  if (!c || !Object.hasOwn(PROVIDERS, c.provider) || !HEX.test(c.account) || !HEX.test(c.token)
      || !Number.isInteger(c.port) || c.port < 1 || c.port > 65535 || fingerprint(c.publicKey) !== c.account) {
    fail('The saved Parlons connection is invalid.');
  }
  text(c.name, 1024, 'account name');
  return c;
}

// HTTP shape follows minimaDesk/main/parlonsapi.js, narrowed to loopback,
// fixed operations and bounded JSON. Never follow redirects or echo response bodies.
function request(c, operation, body = {}, timeout = 20000) {
  const allowed = ['connect', 'contacts', 'invitations', 'invite', 'contact/add', 'contact/remove', 'dismiss', 'disconnect'];
  if (!allowed.includes(operation)) return Promise.reject(new Error('Unknown Parlons operation.'));
  const data = Buffer.from(JSON.stringify(body));
  if (data.length > MAX_BODY) return Promise.reject(new Error('Parlons request is too large.'));
  return new Promise((resolve, reject) => {
    let settled = false;
    const done = (error, result) => { if (settled) return; settled = true; clearTimeout(deadline); error ? reject(error) : resolve(result); };
    const req = http.request({host: '127.0.0.1', port: c.port, path: ROOT + operation,
      method: 'POST', agent: false, headers: {'Content-Type': 'application/json', 'Content-Length': data.length,
        ...(operation === 'connect' ? {} : {Authorization: 'Bearer ' + c.token})}}, res => {
      const chunks = []; let size = 0;
      res.on('data', chunk => {
        size += chunk.length;
        if (size > MAX_RESPONSE) { done(new Error('Parlons response is too large.')); res.destroy(); }
        else chunks.push(chunk);
      });
      res.on('error', () => done(new Error('Parlons disconnected before replying.')));
      res.on('end', () => {
        if (res.statusCode === 401 || res.statusCode === 403) return done(new Error('Parlons refused access. Reconnect from its Connected apps settings.'));
        if (res.statusCode < 200 || res.statusCode >= 300) return done(new Error('Parlons could not complete this request (HTTP ' + res.statusCode + ').'));
        if (!/^application\/json(?:\s*;|$)/i.test(res.headers['content-type'] || '')) return done(new Error('Parlons returned an unreadable response.'));
        let result; try { result = JSON.parse(Buffer.concat(chunks).toString('utf8')); } catch (_) { return done(new Error('Parlons returned an unreadable response.')); }
        if (!result || result.ok !== true) return done(new Error('Parlons could not complete this request. Refresh and try again.'));
        if (result.provider !== c.provider || result.account !== c.account) return done(new Error('A different Parlons account answered. Reconnect to the account you want.'));
        done(null, result);
      });
    });
    const deadline = setTimeout(() => { done(new Error('Parlons did not answer. Open the connected app and retry.')); req.destroy(); }, timeout);
    req.on('error', () => done(new Error('Parlons is unavailable. Open the connected app and retry.')));
    req.end(data);
  });
}

class ParlonsClient {
  constructor({directory, key, transport = request}) {
    this.file = path.join(directory, 'parlons-connections.protected');
    this.key = Buffer.from(crypto.hkdfSync('sha256', key, Buffer.alloc(0), 'minimaDocs Parlons connections v1', 32));
    this.connections = new Map(); this.transport = transport; this.queue = Promise.resolve(); this.closed = false;
  }
  async load() {
    let raw;
    try { raw = await fs.readFile(this.file); } catch (e) { if (e.code === 'ENOENT') return; throw e; }
    if (raw.length < 29 || raw.length > MAX_RESPONSE || raw[0] !== 1) fail('The saved Parlons connections could not be opened.');
    const decipher = crypto.createDecipheriv('aes-256-gcm', this.key, raw.subarray(1, 13));
    decipher.setAAD(Buffer.from('minimaDocs Parlons v1')); decipher.setAuthTag(raw.subarray(13, 29));
    let records;
    try { records = JSON.parse(Buffer.concat([decipher.update(raw.subarray(29)), decipher.final()]).toString('utf8')); }
    catch (_) { fail('The saved Parlons connections could not be opened.'); }
    if (!Array.isArray(records) || records.length > 8) fail('The saved Parlons connections are invalid.');
    const next = new Map();
    for (const record of records) { const c = validateConnection(record); const id = metadata(c).id; if (next.has(id)) fail('Duplicate saved Parlons connection.'); next.set(id, c); }
    this.connections = next;
  }
  list() { return [...this.connections.values()].map(metadata); }
  get(id) { if (this.closed) fail('Parlons connection closed.'); const c = this.connections.get(id); if (!c) fail('Choose a connected Parlons account.'); return c; }
  mutate(action) {
    const result = this.queue.then(() => { if (this.closed) fail('Parlons connection closed.'); return action(); });
    this.queue = result.catch(() => {}); return result;
  }
  async persist(next) {
    const nonce = crypto.randomBytes(12), cipher = crypto.createCipheriv('aes-256-gcm', this.key, nonce);
    cipher.setAAD(Buffer.from('minimaDocs Parlons v1'));
    const encrypted = Buffer.concat([cipher.update(JSON.stringify([...next.values()]), 'utf8'), cipher.final()]);
    const data = Buffer.concat([Buffer.from([1]), nonce, cipher.getAuthTag(), encrypted]);
    const temp = this.file + '.' + crypto.randomUUID() + '.tmp'; let handle;
    try {
      handle = await fs.open(temp, 'wx', 0o600); await handle.writeFile(data); await handle.sync(); await handle.close(); handle = null;
      await fs.rename(temp, this.file); this.connections = next;
    } finally { await handle?.close(); await fs.rm(temp, {force: true}); }
  }
  connect(link) {
    return this.mutate(async () => {
      const parsed = parseLink(link), id = parsed.provider + ':' + parsed.account;
      if (this.connections.size >= 8 && !this.connections.has(id)) fail('Disconnect an account before adding another.');
      const r = await this.transport(parsed, 'connect', {code: parsed.code});
      if (r.provider !== parsed.provider || r.account !== parsed.account) fail('A different Parlons account answered.');
      const c = validateConnection({port: parsed.port, provider: parsed.provider, account: parsed.account,
        name: r.name, token: r.token, publicKey: r.publicKey});
      const next = new Map(this.connections); next.set(id, c); await this.persist(next);
      return metadata(c);
    });
  }
  async snapshot(id) {
    const c = this.get(id);
    const [book, inbox] = await Promise.all([this.transport(c, 'contacts'), this.transport(c, 'invitations')]);
    if (this.connections.get(id) !== c) fail('This Parlons connection changed. Refresh to continue.');
    if (!Array.isArray(inbox.invitations) || inbox.invitations.length > 64) fail('Parlons returned an unreadable inbox.');
    return {...metadata(c), contacts: contacts(book.contacts), invitations: inbox.invitations.map(invitation)};
  }
  async send(id, to, line) {
    const c = this.get(id); if (!PUBLIC_KEY.test(to)) fail('Choose a valid Parlons contact.');
    text(line, 16384, 'invitation');
    const r = await this.transport(c, 'invite', {to: to.toLowerCase(), line, requestId: crypto.randomUUID()});
    if (!['queued', 'sent'].includes(r.state)) fail('Parlons returned no invitation status. Refresh before trying again.');
    return {state: r.state};
  }
  async add(id, address) {
    const c = this.get(id); text(address, 8192, 'contact address');
    if (!address || /[\x00-\x20\x7f]/.test(address) || !/^(MAX#|Mx)/.test(address)) fail('Enter a complete Parlons contact address.');
    await this.transport(c, 'contact/add', {address}); return {};
  }
  async remove(id, key) { if (!PUBLIC_KEY.test(key)) fail('Choose a valid Parlons contact.'); await this.transport(this.get(id), 'contact/remove', {key: key.toLowerCase()}); return {}; }
  async dismiss(id, item) { if (!HEX.test(item)) fail('Choose a valid invitation.'); await this.transport(this.get(id), 'dismiss', {id: item}); return {}; }
  disconnect(id, forget = false) {
    return this.mutate(async () => {
      const c = this.get(id); if (!forget) await this.transport(c, 'disconnect');
      const next = new Map(this.connections); next.delete(id); await this.persist(next); return {};
    });
  }
  async close() { await this.queue; this.closed = true; this.key.fill(0); this.connections.clear(); }
}
module.exports = {ParlonsClient, parseLink, request, contacts, fingerprint, PROVIDERS};
