#!/usr/bin/env python3
"""Focused Android-path/import/recovery regression plus the full Studio integration harness."""
from pathlib import Path
import json,os,subprocess,sys,tempfile
root=Path(__file__).resolve().parents[1]
sdk=Path(os.environ.get('DAML_ANDROID_SDK','/tmp/autoplus-android-sdk'))
jar=sdk/'platforms/android-34/android.jar'
build=max((root/'build').glob('manual-v0131-*'),key=lambda p:p.stat().st_mtime)
work=Path(tempfile.mkdtemp(prefix='v0131-tests-',dir=root/'build'))
classes=work/'classes';classes.mkdir()
sources=list((root/'app/src/main/java').rglob('*.java'))+list((root/'tests').rglob('*.java'))+list((build/'generated').rglob('*.java'))
subprocess.run(['java','com.sun.tools.javac.Main','-encoding','UTF-8','-cp',str(jar),'-d',str(classes)]+list(map(str,sources)),check=True)
cp=str(classes)+os.pathsep+str(jar);results=[]
def test(name,*args):
 print('RUN',name,*map(str,args),flush=True)
 log=f'{len(results)+1:02d}-{name}.log'
 with (work/log).open('w') as f:
  process=subprocess.run(['java','-Xmx1500m','-cp',cp,'com.dennis.aimasteringlab.'+name]+list(map(str,args)),stdout=f,stderr=subprocess.STDOUT)
 results.append({'test':name,'arguments':list(map(str,args)),'exit_code':process.returncode,'log':log})
 (work/'RESULTS.json').write_text(json.dumps(results,indent=2))
 print((work/log).read_text()[-6000:],flush=True)
 if process.returncode:raise SystemExit(process.returncode)
test('StudioImportPathHarness',work/'path-alias')
test('StudioImportRecoveryHarness',work/'import-recovery',*([Path(sys.argv[1]).resolve()] if len(sys.argv)>1 else []))
test('StudioV013Harness',work/'studio')
print('V0131_HOST_GATE_PASS',work,flush=True)
