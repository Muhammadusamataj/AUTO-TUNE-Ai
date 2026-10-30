#!/usr/bin/env python3
"""Collect evidence using normal ADB; never alters verification/security settings or uninstalls.

Read-only by default. --install explicitly attempts adb install -r of the supplied APK.
Run on the computer connected to the actual phone, not an unrelated emulator.
"""
import argparse
import datetime
import hashlib
import json
from pathlib import Path
import re
import subprocess


def classify_install_output(output):
    primary=re.search(r'Failure\s*\[\s*(INSTALL_(?:FAILED|PARSE_FAILED)_[A-Z0-9_]+)',output)
    if primary: return primary.group(1)
    failures=re.findall(r'\b(INSTALL_(?:FAILED|PARSE_FAILED)_[A-Z0-9_]+)\b',output)
    if failures: return failures[0]
    if re.search(r'^Success\s*$',output,re.M):
        return 'ADB_INSTALL_SUCCESS (does not establish Vivo UI/Play Protect acceptance)'
    return 'UNKNOWN: inspect complete install-result.txt and device logs; no INSTALL_FAILED token returned'


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk',type=Path,required=True)
    parser.add_argument('--package',default='com.autotuneeq.mvp')
    parser.add_argument('--expected-sha256')
    parser.add_argument('--adb',default='adb')
    parser.add_argument('--serial',help='Use adb devices -l to select the actual Vivo phone')
    parser.add_argument('--out',type=Path,default=Path('autotune-install-evidence'))
    parser.add_argument('--install',action='store_true',help='Attempt normal adb install -r; preserves app data')
    parser.add_argument('--apksigner',help='Optional SDK apksigner path for incoming and installed signer comparison')
    args=parser.parse_args()
    apk=args.apk.resolve()
    if not apk.is_file(): parser.error('APK not found')
    if not re.fullmatch(r'[A-Za-z0-9_.]+',args.package): parser.error('Invalid package name')
    digest=hashlib.sha256(apk.read_bytes()).hexdigest()
    if args.expected_sha256 and args.expected_sha256.lower()!=digest:
        parser.error('SHA-256 mismatch; no installation attempted')
    if args.out.exists() and any(args.out.iterdir()): parser.error('Output folder must be empty to preserve earlier evidence')
    args.out.mkdir(parents=True,exist_ok=True)
    adb=[args.adb]+(['-s',args.serial] if args.serial else [])
    results={}
    def run(name,command,timeout=60):
        try:
            result=subprocess.run(command,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,
                                  encoding='utf-8',errors='replace',timeout=timeout)
            output=result.stdout; code=result.returncode
        except subprocess.TimeoutExpired as exc:
            data=exc.stdout or b''
            output=(data.decode('utf-8',errors='replace') if isinstance(data,bytes) else data)+'\nDIAGNOSTIC COMMAND TIMED OUT\n'
            code=None
        except OSError as exc:
            output=str(exc); code=None
        (args.out/(name+'.txt')).write_text(output,encoding='utf-8')
        results[name]={'command':command,'exit_code':code}
        return output,code
    summary={'time_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
             'apk_sha256':digest,'apk_bytes':apk.stat().st_size,'package':args.package,
             'device_rejection':'UNKNOWN: no actual phone install result yet'}
    run('adb-version',[args.adb,'version'])
    run('devices',[args.adb,'devices','-l'])
    state,code=run('device-state',adb+['get-state'])
    if code!=0 or state.strip()!='device':
        summary['device_rejection']='UNTESTED: choose exactly one authorized device with --serial'
    else:
        for name,prop in [('api','ro.build.version.sdk'),('android','ro.build.version.release'),
                          ('manufacturer','ro.product.manufacturer'),('model','ro.product.model'),
                          ('fingerprint','ro.build.fingerprint')]:
            run('device-'+name,adb+['shell','getprop',prop])
        run('device-time-before',adb+['shell','date'])
        packages,_=run('packages-before',adb+['shell','pm','list','packages','-u'])
        (args.out/'autotune-packages.txt').write_text('\n'.join(line for line in packages.splitlines() if 'autotune' in line.lower())+'\n')
        run('users',adb+['shell','pm','list','users'])
        run('package-before',adb+['shell','dumpsys','package',args.package])
        paths,_=run('installed-paths-before',adb+['shell','pm','path',args.package])
        if args.apksigner:
            run('incoming-signature',[args.apksigner,'verify','--verbose','--print-certs',str(apk)])
            base=next((line[len('package:'):].strip() for line in paths.splitlines()
                       if line.startswith('package:') and line.strip().endswith('/base.apk')),None)
            if base:
                installed=args.out.resolve()/'installed-base-before.apk'
                _,pulled=run('pull-installed-base',adb+['pull',base,str(installed)])
                if pulled==0:
                    run('installed-signature',[args.apksigner,'verify','--verbose','--print-certs',str(installed)])
        if args.install:
            output,_=run('install-result',adb+['install','-r',str(apk)],timeout=180)
            summary['device_rejection']=classify_install_output(output)
        run('device-time-after',adb+['shell','date'])
        run('package-after',adb+['shell','dumpsys','package',args.package])
        all_logs,_=run('logcat-all',adb+['logcat','-d','-b','all'],timeout=120)
        pattern=re.compile(r'INSTALL_(?:FAILED|PARSE_FAILED)|autotune|PackageInstaller|PackageManager|PackageVerifier|verification|verifier|Play Protect|vivo.*install|install.*vivo',re.I)
        (args.out/'installation-excerpts.txt').write_text('\n'.join(line for line in all_logs.splitlines() if pattern.search(line))+'\n',encoding='utf-8')
    summary['commands']=results
    (args.out/'summary.json').write_text(json.dumps(summary,indent=2),encoding='utf-8')
    print(summary['device_rejection'])
    print('Evidence saved to',args.out.resolve())
    print('Share summary.json, install-result.txt (if attempted), and installation-excerpts.txt. Full logcat stays local and may include unrelated private logs.')

if __name__=='__main__': main()
