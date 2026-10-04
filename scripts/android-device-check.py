#!/usr/bin/env python3
"""Disposable CI emulator only: storage, bundled editors, and real Parlons approval/IPC."""
import os, re, subprocess, time, xml.etree.ElementTree as ET
from pathlib import Path

serial=os.environ.get('ANDROID_SERIAL','emulator-5554')
if not serial.startswith('emulator-'):raise SystemExit('Device checks require a disposable emulator')

def adb(*args,timeout=300):
    return subprocess.check_output(['adb','-s',serial,*args],text=True,timeout=timeout)

def instrument(mode=''):
    args=['shell','am','instrument','-w']
    if mode:args+=['-e','mode',mode]
    result=adb(*args,'com.eurobuddha.minimadocs.test/org.mininotes.android.DocumentInstrumentation',timeout=1200)
    print(result,flush=True)
    if 'PASS:' not in result or 'FAIL:' in result:raise RuntimeError('Instrumentation failed: '+mode)

def nodes():
    adb('shell','uiautomator','dump','/data/local/tmp/minimadocs-check.xml',timeout=35)
    return ET.fromstring(adb('shell','cat','/data/local/tmp/minimadocs-check.xml')).iter('node')

def click(pattern,seconds=40,scroll=False):
    until=time.monotonic()+seconds
    while time.monotonic()<until:
        try:
            for node in nodes():
                if re.fullmatch(pattern,node.get('text',''),re.I) or re.fullmatch(pattern,node.get('content-desc',''),re.I):
                    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds','')))
                    if x2>x1 and y2>y1:
                        adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));return True
            if scroll:adb('shell','input','swipe','500','1600','500','500','400')
        except (subprocess.SubprocessError,ET.ParseError):pass
        time.sleep(1)
    return False

def require(pattern,**kwargs):
    if not click(pattern,**kwargs):raise RuntimeError('UI control unavailable: '+pattern)

adb('install','-r','android/app/build/outputs/apk/debug/app-debug.apk')
adb('install','-r','android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
instrument()
instrument('editors')
adb('install','-r','android/build/parlons/app/build/outputs/apk/debug/app-debug.apk')
adb('shell','am','start','-n','com.eurobuddha.maxima.app/.MainActivity')
require('Create new identity')
# A new, disposable CI identity; never read, print, copy or retain its seed.
require("I've saved them")
click('Allow',seconds=5)
instrument('parlons-pending')
adb('shell','am','start','-n','com.eurobuddha.maxima.app/.MainActivity')
require('More')
require('Settings')
require('minimaDocs',seconds=60,scroll=True)
require('Approve')
instrument('parlons')
print('PASS: Parlons user approval and contact API verified',flush=True)
