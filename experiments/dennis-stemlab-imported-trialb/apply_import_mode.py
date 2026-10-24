from pathlib import Path
import sys
root=Path(sys.argv[1] if len(sys.argv)>1 else "project")
src=root/"app/src/main/java/com/dennis/aimasteringlab"
p=src/"StudioActivity.java"
s=p.read_text()
def change(old,new,tag):
    global s
    n=s.count(old)
    if n!=1: raise SystemExit("Imported-master UI patch needs one original fragment for "+tag+"; found "+str(n))
    s=s.replace(old,new,1)

change("private static final int WAVS=701,REFERENCE=702,SAVE=703,STEM_ZIP=704;",
       "private static final int WAVS=701,REFERENCE=702,SAVE=703,STEM_ZIP=704,MASTER_B_WAV=705;", "picker ID")

change('''        actions("RECOVERED TRIAL B FINISH","Original v0.5.1 sound from finished projects",this::bulkExperiments,
                "EXPORT TRIAL B QC CSV","Measured results for baseline and Trial B",this::bulkReport,false);''',
       '''        actions("RECOVERED TRIAL B FINISH","Verified v0.5.1 project-based Trial B",this::bulkExperiments,
                "EXPORT TRIAL B QC CSV","Measured results for baseline and Trial B",this::bulkReport,false);
        button("IMPORTED MASTER → TRIAL B (TEST)",()->{
            idle();
            new AlertDialog.Builder(this)
                .setTitle("Trial B on an already-mastered WAV?")
                .setMessage("Experimental: applies original v0.5.1 Trial B dynamic EQ directly to an exported 48 kHz stereo WAV. No full mastering again. Original imported master is retained. This is NOT guaranteed to sound identical to project-based Trial B.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("SELECT MASTERED WAV",(d,w)->pick(MASTER_B_WAV,false)).show();
        });''', "home experiment button")

change('''        StudioVisuals.section(this,root,"Mastering Controls");''',
       '''        if(p.data.optBoolean("imported_master_trial_b",false)){
            StudioVisuals.section(this,root,"Imported-master Trial B — isolated test");
            text("Original WAV stays saved in this project. The existing master was NOT mastered again: only the original Trial B dynamic-EQ recipe and transparent 24-bit WAV delivery were used. This is not guaranteed to be identical to original project-based Trial B.",13,false);
            if(m!=null){
                button("REVIEW ORIGINAL WAV VS TRIAL B (LEVEL MATCHED)",this::importedMasterReview);
                button("SAVE TRIAL B WAV — SONG NAME",()->{
                    StudioProject q=project();
                    saveWithName(q.file("master"),"audio/wav",trialBFilename(q));
                });
                button("SAVE IMPORTED TRIAL B QC JSON",()->{
                    StudioProject q=project();
                    saveWithName(q.file("imported_trial_b_qc"),"application/json",
                       ImportedMasterTrialB.cleanTitle(q.data.optString("trial_original_title","Song"))+"_TrialB_ImportedMaster_QC.json");
                });
            }
            button("DELETE THIS TEST PROJECT",()->{
                idle();StudioProject q=project();
                new AlertDialog.Builder(this).setTitle("Delete imported-master Trial B test?")
                    .setMessage("Deletes only this test project's internal copies and results. Files exported elsewhere are unaffected.")
                    .setNegativeButton("Keep",null)
                    .setPositiveButton("Delete",(d,w)->{StudioPipeline.removeTree(q.dir);home();}).show();
            });
            button("BACK TO PROJECTS",this::home);
            return;
        }
        StudioVisuals.section(this,root,"Mastering Controls");''', "imported project detail isolation")

change('''    private void bulkExperiments() throws Exception {''',
       '''    private void importedMasterReview() throws Exception {
        idle();StudioProject p=project();
        if(!p.data.optBoolean("imported_master_trial_b",false)
                || !p.validFile("source","source_hash") || !p.validFile("master","master_hash"))
            throw new IOException("Both verified original and finished Trial B WAVs are required for comparison.");
        page="imported-review";shell("Imported master vs Trial B");
        text("A = unmodified imported master. B = direct Trial B test. Listening levels are matched; no files change during playback.",14,false);
        controls();preparePlayer(p.file("source"),p.file("master"));
        button("BACK TO TEST PROJECT",this::detail);
    }
    private void bulkExperiments() throws Exception {''', "imported comparison")

change('''        if(request==SAVE){Uri destination=data.getData();File source=new File(exportPath);job("saving file",()->{''',
       '''        if(request==SAVE){Uri destination=data.getData();File source=new File(exportPath);job("saving file",()->{''', "existing save identity")
# Add test picker only after the whole SAVE branch, before general WAV import loop.
change('''        ArrayList<Uri> uris=new ArrayList<>();if(data.getClipData()!=null)''',
       '''        if(request==MASTER_B_WAV){
            Uri selected=data.getData();
            if(selected==null)throw new IOException("Select one already-mastered WAV file.");
            String filename=name(selected);
            if(!filename.toLowerCase(Locale.ROOT).endsWith(".wav"))
                throw new IOException("The experimental Trial B mode accepts WAV files only.");
            job("Imported master → original Trial B test",()->{
                InputStream imported=getContentResolver().openInputStream(selected);
                if(imported==null)throw new IOException("Cannot open the selected mastered WAV.");
                StudioProject completed;
                try(InputStream stream=imported){
                    completed=ImportedMasterTrialB.render(projects,filename,stream,MasteringForegroundService::update);
                }
                String completedId=completed.dir.getName();
                StudioJobs.after(()->{if(alive&&visible)act(()->{
                    projectId=completedId;detail();
                });});
            });
            return;
        }
        ArrayList<Uri> uris=new ArrayList<>();if(data.getClipData()!=null)''', "imported master picker")

p.write_text(s)
source=Path("experiments/dennis-stemlab-imported-trialb/ImportedMasterTrialB.java")
if not source.is_file(): raise SystemExit("Missing imported-master DSP code")
dst=src/"ImportedMasterTrialB.java"
if dst.exists(): raise SystemExit("Unexpected imported-master DSP already installed")
dst.write_text(source.read_text())
b=root/"app/build.gradle"
settings=b.read_text()
old="versionName '0.5.1-trialb-only'"
assert settings.count(old)==1
settings=settings.replace(old,"versionName '0.5.1-trialb-import-test'",1)
# Separate package: preserve and coexist with known-good B-only app.
old="applicationId 'com.dennis.stemlab.trialbonly051'"
assert settings.count(old)==1
settings=settings.replace(old,"applicationId 'com.dennis.stemlab.importtrialb051'",1)
b.write_text(settings)
manifest=root/"app/src/main/AndroidManifest.xml"
t=manifest.read_text()
assert t.count('android:label="Stem Lab Trial B 5.1"')==1
manifest.write_text(t.replace('android:label="Stem Lab Trial B 5.1"',
    'android:label="Stem Lab Trial B Import TEST"',1))
print("IMPORTED_MASTER_TRIAL_B_EXPERIMENT_APPLIED — verified v0.5.1 base path untouched")
