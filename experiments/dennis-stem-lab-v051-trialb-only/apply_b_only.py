from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv)>1 else "project")
base = root / "app/src/main/java/com/dennis/aimasteringlab"

def replace(text, old, new, name):
    if old not in text:
        raise SystemExit("Expected v0.5.1 source fragment missing: " + name)
    if text.count(old) != 1:
        raise SystemExit("Ambiguous source fragment: " + name)
    return text.replace(old, new, 1)

p = base / "StudioBulkExperiment.java"
s = p.read_text()
s = replace(s, """    private static final String[][] VARIANTS = {
        {"PRESENCE_CONTROL", "Presence control", "Conservative dynamic cuts at 4.3 and 7.6 kHz"},
        {"LOW_MID_CONTROL", "Low-mid control", "Conservative dynamic cuts at 260 Hz and 3.5 kHz"}
    };""", """    // Exact recovered v0.5.1 Trial B; the presence trial is not generated.
    private static final String[][] VARIANTS = {
        {"LOW_MID_CONTROL", "Trial B", "Original v0.5.1 low-mid control at 260 Hz and 3.5 kHz"}
    };""", "variants")
s = replace(s, "Choose between one and ten verified source projects per A/B trial batch",
            "Choose between one and ten verified completed masters for Trial B", "batch constraint")
s = replace(s, """            required = Math.addExact(required, Math.multiplyExact(2L, Math.addExact(source.length(), base.length())));
            required = Math.addExact(required, Math.multiplyExact(4L, Math.max(source.length(), base.length())));""",
            """            required = Math.addExact(required, Math.addExact(source.length(), base.length()));
            required = Math.addExact(required, Math.multiplyExact(2L, Math.max(source.length(), base.length())));""",
            "storage reservation")
s = replace(s, "Not enough free storage for independent A/B masters",
            "Not enough free storage for independent Trial B masters", "low storage")
s = replace(s, "Separate A/B trial projects are queued", "Trial B projects are queued", "queue status")
s = replace(s, '''        final String suffix = " [Trial " + (variant[0].equals("PRESENCE_CONTROL") ? "A" : "B") + "]";''',
            '''        final String suffix = " [Trial B]";''', "title suffix")
s = replace(s, '''        recipe.edits.add(edit("DYNAMIC_EQ", variant[0].equals("PRESENCE_CONTROL") ? 4300 : 260,
                variant[0].equals("PRESENCE_CONTROL") ? 1.1 : 0.95,
                variant[0].equals("PRESENCE_CONTROL") ? -0.75 : -0.55,
                variant[0].equals("PRESENCE_CONTROL") ? -30 : -24, duration, variant[1]));
        recipe.edits.add(edit("DYNAMIC_EQ", variant[0].equals("PRESENCE_CONTROL") ? 7600 : 3500,
                1.2, variant[0].equals("PRESENCE_CONTROL") ? -0.55 : -0.45,
                -30, duration, variant[1]));''',
            '''        // Original v0.5.1 Trial B parameters, not an approximation.
        recipe.edits.add(edit("DYNAMIC_EQ", 260, 0.95, -0.55, -24, duration, variant[1]));
        recipe.edits.add(edit("DYNAMIC_EQ", 3500, 1.2, -0.45, -30, duration, variant[1]));''',
            "Trial B dynamic EQ recipe")
s = replace(s, '''                    .put("trial_variant", variant[0]).put("trial_description", variant[2])''',
            '''                    .put("trial_variant", variant[0]).put("trial_description", variant[2])
                    .put("trial_original_title", title)''', "original title")
assert "PRESENCE_CONTROL" not in s, "Trial A must not be generated"
p.write_text(s)

p = base / "StudioActivity.java"
s = p.read_text()
s = replace(s, '''        actions("BULK A/B EXPERIMENTS","Isolated trials from finished WAVs and stems",this::bulkExperiments,
                "EXPORT BULK QC CSV","Measured results for baseline and trials",this::bulkReport,false);''',
            '''        actions("RECOVERED TRIAL B FINISH","Original v0.5.1 sound from finished projects",this::bulkExperiments,
                "EXPORT TRIAL B QC CSV","Measured results for baseline and Trial B",this::bulkReport,false);''',
            "home navigation")
s = replace(s, '''        idle();page="experiments";shell("Bulk A/B experiment lab");
        text("Choose up to ten completed WAV or stem projects. Two separately saved, queued post-master EQ auditions will be made per source. The v0.15.7 base mastering engine and every existing master are untouched. Each trial uses a verified copy of the original base and source; allow storage for independent exports.",14,false);''',
            '''        idle();page="experiments";shell("Recovered v0.5.1 Trial B");
        text("Choose up to ten completed WAV or stem projects. Only the original v0.5.1 Trial B will be generated per song. The new project reuses the verified original mastering base and source before delivery. Existing masters remain untouched.",14,false);''',
            "Trial B screen")
s = replace(s, 'button("CREATE TWO TEST VERSIONS PER SELECTED SONG",()->{',
            'button("CREATE TRIAL B FOR SELECTED SONGS",()->{', "Trial B button")
s = replace(s, '''            new AlertDialog.Builder(this).setTitle("Prepare "+(ids.size()*2)+" A/B projects?")
                .setMessage("Creates separate queued trial projects, not new versions of your approved masters. Existing sources and finished WAVs remain untouched. These are gentle experimental dynamic-EQ edits, not a replacement mastering engine.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Create trials",(dialog,which)->act(()->job("preparing mastering A/B trials",()->{''',
            '''            new AlertDialog.Builder(this).setTitle("Prepare "+ids.size()+" Trial B project(s)?")
                .setMessage("Creates one separately queued original v0.5.1 Trial B project per selected song. Existing masters remain untouched.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Create Trial B",(dialog,which)->act(()->job("preparing original v0.5.1 Trial B",()->{''',
            "confirmation dialog")
s = replace(s, '''        text("After creation, return to Projects and use MASTER QUEUE / RESUME. Compare each Trial A/B with its original using level-matched listening. Export the bulk QC CSV to compare measurements.",12,false);''',
            '''        text("After creating Trial B projects, return to Projects and choose MASTER QUEUE / RESUME. Completed Trial B projects export under the original song title with a TrialB suffix. Compare the original master and Trial B at equal listening loudness.",12,false);''',
            "instructions")
s = replace(s, '''            button("SAVE 24-BIT / 48 kHz WAV",()->save(project().file("master"),"audio/wav"));''',
            '''            button(p.data.has("trial_original_title") ? "SAVE TRIAL B WAV — SONG NAME" : "SAVE 24-BIT / 48 kHz WAV",
                    ()->{StudioProject q=project();
                         if(q.data.has("trial_original_title"))saveWithName(q.file("master"),"audio/wav",trialBFilename(q));
                         else save(q.file("master"),"audio/wav");});''',
            "project export action")
s = replace(s, '''    private void save(File f,String mime)throws Exception{
        if(f==null||!f.isFile())throw new IOException("This output is not available. Render the project first.");exportPath=f.getAbsolutePath();Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType(mime);i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,f.getName());startActivityForResult(i,SAVE);
    }''',
            r'''    private static String trialBFilename(StudioProject project) {
        String title=project.data.optString("trial_original_title", "Song")
                .replaceFirst("(?i)\\.wav$", "")
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if(title.isEmpty())title="Song";
        if(title.length()>100)title=title.substring(0,100).trim();
        return title+"_TrialB_24bit-48kHz.wav";
    }
    private void saveWithName(File f,String mime,String filename)throws Exception {
        if(f==null||!f.isFile())throw new IOException("This output is not available. Render the project first.");
        exportPath=f.getAbsolutePath();
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.setType(mime);i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_TITLE,filename);startActivityForResult(i,SAVE);
    }
    private void save(File f,String mime)throws Exception {
        saveWithName(f,mime,f==null?"Song.wav":f.getName());
    }''',
            "title-preserving export")
p.write_text(s)
print("TRIAL_B_ONLY_SOURCE_PATCH_APPLIED")
