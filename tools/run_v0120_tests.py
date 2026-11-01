#!/usr/bin/env python3
"""Host regression gate. Build the APK first. Requires Java 17, Python and NumPy.
Android codec availability is intentionally reported as CONDITIONAL on the host.
"""
from pathlib import Path
import os, subprocess, sys, tempfile
root=Path(__file__).resolve().parents[1]
sdk=Path(os.environ.get('DAML_ANDROID_SDK','/tmp/autoplus-android-sdk'))
jar=sdk/'platforms/android-34/android.jar'
builds=list((root/'build').glob('manual-v0120-*'))
if not builds or not jar.is_file(): raise SystemExit('Run tools/build_apk.sh with DAML_ANDROID_SDK first')
build=max(builds,key=lambda f:f.stat().st_mtime)
work=Path(tempfile.mkdtemp(prefix='v0120-tests-',dir=root/'build'));classes=work/'classes';classes.mkdir()
sources=list((root/'app/src/main/java').rglob('*.java'))+list((root/'tests').rglob('*.java'))+list((build/'generated').rglob('*.java'))
subprocess.run(['java','com.sun.tools.javac.Main','-encoding','UTF-8','-cp',str(jar),'-d',str(classes)]+list(map(str,sources)),check=True)
fixtures=work/'fixtures'
subprocess.run([sys.executable,str(root/'tests/generate_v010_fixtures.py'),str(fixtures)],check=True)
cp=str(classes)+os.pathsep+str(jar)
def test(name,*args):
 print('RUN',name,*map(str,args),flush=True)
 subprocess.run(['java','-cp',cp,'com.dennis.aimasteringlab.'+name]+list(map(str,args)),check=True)
test('V012ExportsHarness',work/'exports')
test('V012AudioHarness',work/'v012-audio')
test('NaturalStudioPolicyHarness')
test('PassageAndComparisonHarness',work/'passage-and-comparison')
test('GentlerRenderHarness',work/'gentler-render')
test('CompliantMasterPolicyHarness')
test('RadioPreparationHarness',work/'radio')
test('NaturalStudioRecoveryHarness','sparse',fixtures/'sparse.wav',work/'sparse')
test('NaturalStudioRecoveryHarness','repair',fixtures/'isolated-clips.wav',work/'repair')
test('NaturalStudioRecoveryHarness','negative',fixtures/'dense-clips.wav',work/'negative')
test('NaturalStudioRecoveryHarness','negative',fixtures/'rounded-peaks.wav',work/'negative')
test('NaturalStudioRecoveryHarness','recover',fixtures/'clean.wav',work/'recovery')
test('NaturalStudioRecoveryHarness','cancel',fixtures/'clean.wav',work/'cancel')
test('NaturalStudioRecoveryHarness','reprocess',work/'sparse/sparse-master.wav',work/'reprocess')
test('ClickDropoutFixtureGenerator',fixtures/'clicks.wav')
test('ClickDropoutRepairHarness',fixtures/'clicks.wav',work/'clicks')
test('SourceIntegrityFixtureGenerator',fixtures/'integrity.wav')
test('SourceIntegrityHarness',fixtures/'integrity.wav',work/'integrity')
test('AdvancedIntelligenceFixtureGenerator',fixtures/'advanced.wav')
test('AdvancedIntelligenceHarness',fixtures/'advanced.wav',work/'advanced')
test('V09FixtureGenerator','hum',fixtures/'hum.wav')
test('NaturalStudioToneHarness',fixtures/'hum.wav',work/'tone')
test('FullAiPackageHarness',work/'sparse/sparse-master.wav',work/'package')
test('ProfessionalMasteringFixtureGenerator',fixtures/'professional.wav')
test('QualityFirstHarness',fixtures/'professional.wav',work/'quality-first')
test('QualityCounterexampleHarness',work/'quality-counterexamples')
test('BackgroundJobHarness')
if len(sys.argv) > 1:
 test('CompliantMasterRecoveryHarness',Path(sys.argv[1]).resolve(),work/'real-file')
print('V0120_HOST_GATE_PASS',work,flush=True)
