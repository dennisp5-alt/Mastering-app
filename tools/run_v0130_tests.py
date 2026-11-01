#!/usr/bin/env python3
"""v0.13 integration plus existing DSP, repair, playback and export regression gates."""
from pathlib import Path
import os,subprocess,sys,tempfile,json
root=Path(__file__).resolve().parents[1]
sdk=Path(os.environ.get('DAML_ANDROID_SDK','/tmp/autoplus-android-sdk'))
jar=sdk/'platforms/android-34/android.jar'
build=max((root/'build').glob('manual-v013*-*'),key=lambda p:p.stat().st_mtime)
work=Path(tempfile.mkdtemp(prefix='v0130-tests-',dir=root/'build'));classes=work/'classes';classes.mkdir()
sources=list((root/'app/src/main/java').rglob('*.java'))+list((root/'tests').rglob('*.java'))+list((build/'generated').rglob('*.java'))
subprocess.run(['java','com.sun.tools.javac.Main','-encoding','UTF-8','-cp',str(jar),'-d',str(classes)]+list(map(str,sources)),check=True)
cp=str(classes)+os.pathsep+str(jar)
results=[]
def test(name,*args):
 print('RUN',name,*map(str,args),flush=True)
 result=subprocess.run(['java','-Xmx1500m','-cp',cp,'com.dennis.aimasteringlab.'+name]+list(map(str,args)),capture_output=True,text=True)
 log=f'{len(results)+1:02d}-{name}.log'
 (work/log).write_text(result.stdout+result.stderr)
 results.append({'test':name,'arguments':list(map(str,args)),'exit_code':result.returncode,'log':log})
 (work/'RESULTS.json').write_text(json.dumps(results,indent=2))
 print(result.stdout[-5000:],flush=True)
 if result.returncode:print(result.stderr[-5000:],flush=True);raise SystemExit(result.returncode)
test('StudioV013Harness',work/'studio')
test('V012ExportsHarness',work/'exports')
test('V012AudioHarness',work/'audio')
test('PassageAndComparisonHarness',work/'comparison')
test('NaturalStudioPolicyHarness')
test('BackgroundJobHarness')
test('CompliantMasterPolicyHarness')
test('RadioPreparationHarness',work/'radio')
fixtures=work/'fixtures'
subprocess.run([sys.executable,str(root/'tests/generate_v010_fixtures.py'),str(fixtures)],check=True)
test('NaturalStudioRecoveryHarness','recover',fixtures/'clean.wav',work/'recovery')
test('NaturalStudioRecoveryHarness','cancel',fixtures/'clean.wav',work/'cancel')
test('QualityCounterexampleHarness',work/'counterexamples')
if len(sys.argv)>1:
 test('StudioRealMusicHarness',Path(sys.argv[1]).resolve(),work/'real-song')
print('V0130_HOST_GATE_PASS',work,flush=True)
