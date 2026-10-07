'use strict';
// electron-builder's normal file walker cannot see Mach-O libraries inside JARs.
// Use its exact certificate/keychain selection, then sign the packaged copy only.
const fs = require('node:fs/promises');
const path = require('node:path');
const {execFileSync} = require('node:child_process');
const {findIdentity} = require('app-builder-lib/out/codeSign/macCodeSign');

module.exports = async context => {
  if (context.electronPlatformName !== 'darwin') return;
  const packager = context.packager;
  const {keychainFile} = await packager.codeSigningInfo.value;
  const identity = await findIdentity('Developer ID Application', packager.platformSpecificBuildOptions.identity, keychainFile);
  if (!identity) throw new Error('A Developer ID certificate is required to sign the bundled SQLite libraries.');

  const resources = path.join(context.appOutDir, packager.appInfo.productFilename + '.app', 'Contents', 'Resources');
  const jars = (await fs.readdir(path.join(resources, 'backend'))).filter(name => /^sqlite-jdbc-.*\.jar$/.test(name));
  if (jars.length !== 1) throw new Error('Expected one pinned SQLite JDBC archive.');
  const jar = path.join(resources, 'backend', jars[0]);
  const entries = ['aarch64', 'x86_64'].map(arch => 'org/sqlite/native/Mac/' + arch + '/libsqlitejdbc.dylib');
  const listed = execFileSync('/usr/bin/unzip', ['-Z1', jar], {encoding: 'utf8'}).trim().split('\n');
  if (listed.some(name => /\.(SF|RSA|DSA)$/i.test(name))) throw new Error('The JDBC archive has a JAR signature; inspect its signing requirements before modifying it.');
  const libraries = listed.filter(name => name.endsWith('.dylib'));
  if (libraries.length !== entries.length || entries.some(name => !libraries.includes(name))) throw new Error('The JDBC native library layout changed; review it before release.');

  const temp = await fs.mkdtemp(path.join(__dirname, '../build/java-sign-'));
  try {
    for (const entry of entries) {
      const file = path.join(temp, entry);
      await fs.mkdir(path.dirname(file), {recursive: true});
      await fs.writeFile(file, execFileSync('/usr/bin/unzip', ['-p', jar, entry], {maxBuffer: 16 * 1024 * 1024}));
      const args = ['--force', '--sign', identity.hash, '--timestamp', '--options', 'runtime'];
      if (keychainFile) args.push('--keychain', keychainFile);
      execFileSync('/usr/bin/codesign', [...args, file], {stdio: 'inherit'});
      execFileSync('/usr/bin/codesign', ['--verify', '--strict', file], {stdio: 'inherit'});
    }
    execFileSync('/usr/bin/zip', ['-q', jar, ...entries], {cwd: temp, stdio: 'inherit'});
    for (const entry of entries) {
      const stored = execFileSync('/usr/bin/unzip', ['-p', jar, entry], {maxBuffer: 16 * 1024 * 1024});
      if (!stored.equals(await fs.readFile(path.join(temp, entry)))) throw new Error('The signed library was not preserved in the JDBC archive.');
    }
    console.log('Signed and verified both native SQLite libraries inside the packaged JDBC archive.');
  } finally {
    await fs.rm(temp, {recursive: true, force: true});
  }
};
