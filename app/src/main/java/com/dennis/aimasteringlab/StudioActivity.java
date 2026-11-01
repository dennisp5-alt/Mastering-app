package com.dennis.aimasteringlab;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;

/** Offline project/review workspace. No cloud accounts, networking or desktop plug-ins. */
public final class StudioActivity extends Activity {
    private static final int WAVS=701,REFERENCE=702,SAVE=703,STEM_ZIP=704,MASTER_B_WAV=705;
    private LinearLayout root;private TextView status,position;private File projects;
    private String projectId="",pickerProject="",exportPath="",page="home";
    private ComparisonPlayer player;private SeekBar seek;private boolean dragging;
    private double playbackSeconds,playbackDuration;
    private long generation;private boolean alive,visible;
    private final Handler handler=new Handler();
    private final MasteringJobControl.Listener listener=s->runOnUiThread(()->{
        if(!alive||status==null)return;status.setText(s.stage+(s.running&&s.percent>=0?" • "+s.percent+"%":""));
    });
    interface Action{void run()throws Exception;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);alive=true;projects=new File(getFilesDir(),"studio-projects");projects.mkdirs();
        if(state!=null){projectId=state.getString("project","");pickerProject=state.getString("picker","");exportPath=state.getString("export","");}
        if(!StudioJobs.busy())for(StudioProject p:StudioProject.list(projects,null))if(StudioProject.interruptedState(p.state()))try{p.state("PAUSED","Interrupted project recovered. Resume from the last verified stage.");}catch(Exception ignored){}
        home();
    }
    @Override protected void onResume(){super.onResume();visible=true;MasteringJobControl.attach(listener);}
    @Override protected void onPause(){visible=false;stop();MasteringJobControl.detach(listener);super.onPause();}
    @Override protected void onDestroy(){alive=false;stop();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle b){b.putString("project",projectId);b.putString("picker",pickerProject);b.putString("export",exportPath);super.onSaveInstanceState(b);}
    @Override public void onBackPressed(){if(!page.equals("home")){home();}else super.onBackPressed();}
    private void shell(String title){
        stop();ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        scroll.setBackground(StudioVisuals.surface(StudioVisuals.BG,0xFF071D2C,Color.TRANSPARENT,0,this));
        root=new LinearLayout(this);root.setOrientation(1);
        root.setPadding(dp(16),dp(12),dp(16),dp(34));scroll.addView(root);setContentView(scroll);
        StudioVisuals.header(this,root);
        StudioVisuals.section(this,root,title);
        if(!page.equals("home"))button("BACK TO PROJECTS / QUEUE",this::home);
        status=StudioVisuals.label(this,MasteringJobControl.snapshot().stage,12,StudioVisuals.MUTED,false);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);
        sp.setMargins(0,dp(3),0,dp(7));root.addView(status,sp);
        if(StudioJobs.busy())button("PAUSE CURRENT JOB",()->{MasteringJobControl.cancel();status.setText("Pausing at a safe boundary; saved results retained.");});
    }
    private TextView text(String s,int size,boolean bright){
        TextView v=StudioVisuals.label(this,s,size,bright?StudioVisuals.TEXT:StudioVisuals.MUTED,bright);
        v.setPadding(0,dp(5),0,dp(5));root.addView(v);return v;
    }
    private View button(String title,Action action){
        View view=StudioVisuals.tile(this,title,"",false,title.startsWith("DELETE"),()->act(action));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(4),0,dp(6));root.addView(view,p);return view;
    }
    private void actions(String left,String leftSub,Action leftAction,String right,String rightSub,Action rightAction,boolean primary){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        View a=StudioVisuals.tile(this,left,leftSub,primary,false,()->act(leftAction));
        LinearLayout.LayoutParams la=new LinearLayout.LayoutParams(0,-2,1);la.setMargins(0,0,dp(4),0);row.addView(a,la);
        View b=StudioVisuals.tile(this,right,rightSub,false,false,()->act(rightAction));
        LinearLayout.LayoutParams lb=new LinearLayout.LayoutParams(0,-2,1);lb.setMargins(dp(4),0,0,0);row.addView(b,lb);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.setMargins(0,0,0,dp(9));root.addView(row,rp);
    }
    private void projectSummary(StudioProject p,boolean latest){
        LinearLayout block=StudioVisuals.column(this);
        block.setPadding(dp(13),dp(12),dp(13),dp(12));
        block.setBackground(StudioVisuals.surface(0xFF102C42,0xFF09192A,0xFF235C79,13,this));
        String state=p.state(),prefix=state.equals("COMPLETE")?"✓ ":state.equals("FAILED")?"! ":"• ";
        block.addView(StudioVisuals.label(this,p.name(),15,StudioVisuals.TEXT,true));
        String summary=prefix+state+(p.data.has("master_meters")?"  •  "+fmt(p.data.optJSONObject("master_meters").optDouble("lufs"))+" LUFS":"");
        block.addView(StudioVisuals.label(this,summary,12,state.equals("FAILED")?StudioVisuals.RED:state.equals("COMPLETE")?StudioVisuals.GREEN:StudioVisuals.CYAN,false));
        if(latest){String note=p.data.optString("message","");if(!note.isEmpty()){
            TextView excerpt=StudioVisuals.label(this,note,11,StudioVisuals.MUTED,false);excerpt.setMaxLines(2);block.addView(excerpt);
        }}
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.setMargins(0,0,0,dp(9));root.addView(block,bp);
        block.setOnClickListener(v->act(()->{projectId=p.dir.getName();detail();}));
        block.setClickable(true);block.setFocusable(true);
    }
    private EditText field(String label,String value,boolean number){text(label,13,false);EditText e=new EditText(this);e.setTextColor(Color.WHITE);e.setSingleLine(true);e.setText(value);e.setInputType(number?InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED:InputType.TYPE_CLASS_TEXT);root.addView(e);return e;}
    private Spinner spinner(String label,String[] values,int selected){text(label,13,false);Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values);s.setAdapter(a);s.setSelection(selected);root.addView(s);return s;}
    private CheckBox check(String label,boolean value){CheckBox c=new CheckBox(this);c.setText(label);c.setTextColor(Color.WHITE);c.setChecked(value);root.addView(c);return c;}
    private void act(Action a){try{a.run();}catch(Exception e){error(e);}}
    private void error(Exception e){if(alive)new AlertDialog.Builder(this).setTitle("Studio needs attention").setMessage(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()).setPositiveButton("OK",null).show();}
    private void idle()throws IOException{if(StudioJobs.busy())throw new IOException("A job is running. Pause it or wait for completion before changing saved projects.");}
    private void permission(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},709);}
    private void job(String name,StudioJobs.Task task)throws Exception{idle();permission();StudioJobs.task(this,name,task);status.setText("Started: "+name+". You can reopen Projects to check progress.");}
    private StudioProject project()throws Exception{
        if(!projectId.matches("[a-fA-F0-9-]{36}"))throw new IOException("Select a project first");return StudioProject.load(new File(projects,projectId));
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private static double number(EditText e)throws IOException{try{double v=Double.parseDouble(e.getText().toString().trim());if(!Double.isFinite(v))throw new NumberFormatException();return v;}catch(Exception x){throw new IOException("Enter a valid number in each numeric field");}}
    private static String fmt(double v){return String.format(Locale.US,"%.3f",v);}
    private void home(){
        page="home";shell("Projects & Bulk Mastering");
        text("Offline mastering for full WAV tracks and independent stem ZIPs. The proven v0.15.7 engine is unchanged.",14,false);
        button("IMPORT STEM ZIP — VOCALS + INSTRUMENTAL",()->{idle();pick(STEM_ZIP,false);});
        actions("IMPORT WAVS","Select one or more WAV files",()->{idle();pick(WAVS,true);},
                "MASTER QUEUE / RESUME","Process saved projects",()->{idle();permission();StudioJobs.queue(this,projects,false);home();},true);
        actions("ALBUM WORKSPACE","Organise finished tracks",this::album,
                "CLASSIC MASTERING","Optional stem support",()->{idle();startActivity(new Intent(this,AiLabActivity.class));},false);
        actions("RETRY FAILED EXPORTS","Resume verified work",()->{idle();permission();StudioJobs.queue(this,projects,true);home();},
                "REFRESH PROJECTS","Scan saved projects",this::home,false);
        actions("RECOVERED TRIAL B FINISH","Verified v0.5.1 project-based Trial B",this::bulkExperiments,
                "EXPORT TRIAL B QC CSV","Measured results for baseline and Trial B",this::bulkReport,false);
        button("BULK IMPORT MASTERED WAVS → TRIAL B (TEST)",()->{
            idle();
            new AlertDialog.Builder(this)
                .setTitle("Trial B on an already-mastered WAV?")
                .setMessage("Select up to 40 finished 48 kHz stereo WAVs. Verified source copies are saved first, then processed sequentially using original Trial B EQ only. MASTER QUEUE / RESUME can finish interrupted projects. No full mastering or limiter is added. This remains an isolated listening experiment.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("SELECT MASTERED WAVS",(d,w)->pick(MASTER_B_WAV,true)).show();
        });
        button("SELECT PROJECTS — BULK EXPORT / CLEAR",this::manageProjects);
        ArrayList<String> errors=new ArrayList<>();List<StudioProject> list=StudioProject.list(projects,errors);
        StudioVisuals.section(this,root,"Recent Projects  •  "+list.size());
        if(list.isEmpty())text("No saved projects yet. Import a WAV to get started.",13,false);
        for(int i=list.size()-1;i>=Math.max(0,list.size()-12);i--)projectSummary(list.get(i),true);
        for(String e:errors)text(e,12,false);
    }
    private void manageProjects() throws Exception {
        idle();page="bulk-projects";shell("Selected projects — export or clear");
        text("Select local projects. Export selected completed masters as one ZIP, or clear selected project folders after confirmation. Clearing removes app-owned source copies, masters and QC; files exported elsewhere remain untouched.",13,false);
        final LinkedHashSet<String> selection=new LinkedHashSet<>();
        List<StudioProject> entries=StudioProject.list(projects,null);
        for(int i=entries.size()-1;i>=0;i--){
            StudioProject p=entries.get(i);
            CheckBox choice=check(p.name()+"  •  "+p.state(),false);
            choice.setOnCheckedChangeListener((v,on)->{
                if(on)selection.add(p.dir.getName());else selection.remove(p.dir.getName());
            });
        }
        button("EXPORT SELECTED COMPLETED MASTERS (ZIP)",()->{
            idle();if(selection.isEmpty())throw new IOException("Select at least one project.");
            List<String> ids=new ArrayList<>(selection);
            job("export selected masters",()->{
                File zip=new File(getFilesDir(),"StemLab_Selected_Masters_"+System.currentTimeMillis()+".zip");
                StudioSelectedProjects.exportMasters(projects,ids,zip);
                StudioJobs.after(()->{if(alive&&visible)act(()->save(zip,"application/zip"));});
            });
        });
        button("CLEAR SELECTED PROJECTS",()->{
            idle();if(selection.isEmpty())throw new IOException("Select the projects you want removed.");
            List<String> ids=new ArrayList<>(selection);
            StringBuilder names=new StringBuilder();
            for(String id:ids){
                StudioProject p=StudioSelectedProjects.loadSelected(projects,id);
                names.append("\n• ").append(p.name());
                if(names.length()>900){names.append("\n…and others");break;}
            }
            new AlertDialog.Builder(this).setTitle("Permanently clear "+ids.size()+" local project(s)?")
                .setMessage("Deletes app-owned project copies, including source WAVs, Trial B WAVs and QC reports. Export any files you need FIRST. Previously exported files outside this app are unaffected.\n"+names)
                .setNegativeButton("KEEP PROJECTS",null)
                .setPositiveButton("DELETE "+ids.size()+" PROJECTS",(d,w)->act(()->{
                    idle();job("clear selected local projects",()->{
                        StudioSelectedProjects.delete(projects,ids);
                        StudioJobs.after(()->{if(alive&&visible)home();});
                    });
                })).show();
        });
    }
    private void importedMasterReview() throws Exception {
        idle();StudioProject p=project();
        if(!p.data.optBoolean("imported_master_trial_b",false)
                || !p.validFile("source","source_hash") || !p.validFile("master","master_hash"))
            throw new IOException("Both verified original and finished Trial B WAVs are required for comparison.");
        page="imported-review";shell("Imported master vs Trial B");
        text("A = unmodified imported master. B = direct Trial B test. Listening levels are matched; no files change during playback.",14,false);
        controls();preparePlayer(p.file("source"),p.file("master"));
        button("BACK TO TEST PROJECT",this::detail);
    }
    private void bulkExperiments() throws Exception {
        idle();page="experiments";shell("Recovered v0.5.1 Trial B");
        text("Choose up to ten completed WAV or stem projects. Only the original v0.5.1 Trial B will be generated per song. The new project reuses the verified original mastering base and source before delivery. Existing masters remain untouched.",14,false);
        List<StudioProject> originals = new ArrayList<>();
        for (StudioProject p:StudioProject.list(projects,null))if(StudioBulkExperiment.eligible(p))originals.add(p);
        List<String> selected=new ArrayList<>();
        int count=Math.min(30,originals.size());
        if(originals.size()>30)text("Showing the 30 most recent eligible projects. Use smaller batches for reliable tests.",12,false);
        for(int i=originals.size()-1;i>=Math.max(0,originals.size()-count);i--){
            StudioProject p=originals.get(i);
            CheckBox choice=check(p.name()+"  •  "+StudioBulkExperiment.kind(p),false);
            choice.setOnCheckedChangeListener((view,on)->{
                if(on&&!selected.contains(p.dir.getName()))selected.add(p.dir.getName());
                else if(!on)selected.remove(p.dir.getName());
            });
        }
        button("CREATE TRIAL B FOR SELECTED SONGS",()->{
            idle();if(selected.isEmpty()||selected.size()>10)throw new IOException("Choose 1–10 projects for one experiment batch");
            List<String> ids=new ArrayList<>(selected);
            new AlertDialog.Builder(this).setTitle("Prepare "+ids.size()+" Trial B project(s)?")
                .setMessage("Creates one separately queued original v0.5.1 Trial B project per selected song. Existing masters remain untouched.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Create Trial B",(dialog,which)->act(()->job("preparing original v0.5.1 Trial B",()->{
                    StudioBulkExperiment.create(projects,ids,MasteringForegroundService::update);
                    StudioJobs.after(()->{if(alive&&visible)home();});
                }))).show();
        });
        text("After creating Trial B projects, return to Projects and choose MASTER QUEUE / RESUME. Completed Trial B projects export under the original song title with a TrialB suffix. Compare the original master and Trial B at equal listening loudness.",12,false);
    }
    private void bulkReport() throws Exception {
        job("exporting mastering QC table",()->{
            File output=new File(getFilesDir(),"StemLab_Bulk_QC_"+System.currentTimeMillis()+".csv");
            StudioBulkExperiment.report(projects,output,MasteringForegroundService::update);
            StudioJobs.after(()->{if(alive&&visible)act(()->save(output,"text/csv"));});
        });
    }
    private void detail()throws Exception{
        StudioProject p=project();page="project";shell(p.name());
        String state=p.state(),message=p.data.optString("message","");
        double duration=p.data.optDouble("duration",0);
        JSONObject m=p.data.optJSONObject("master_meters");if(m!=null)duration=m.optDouble("duration",duration);
        String dur=duration>0?String.format(Locale.US,"%d:%02d",(int)(duration/60),((int)duration)%60):"Unknown duration";
        text("PROJECT  •  "+dur+"  •  "+StudioBulkExperiment.kind(p)
                +(p.data.has("trial_variant")?"  /  "+p.data.optString("trial_variant"):"")
                +"  •  "+(m==null?"Source ready":"24-bit / 48 kHz / Stereo"),12,false);
        File preview=p.file(m==null?"source":"master");
        LinearLayout wavePanel=StudioVisuals.column(this);wavePanel.setPadding(dp(10),dp(8),dp(10),dp(5));
        wavePanel.setBackground(StudioVisuals.surface(0xFF0E3248,0xFF05182C,StudioVisuals.CYAN,14,this));
        wavePanel.addView(new StudioWaveformView(this,preview),new LinearLayout.LayoutParams(-1,dp(112)));
        wavePanel.addView(StudioVisuals.label(this,m==null?"Source waveform preview":"Finished WAV waveform preview • Tap to open level-matched A/B review",11,StudioVisuals.MUTED,false));
        LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,-2);wp.setMargins(0,dp(5),0,dp(13));root.addView(wavePanel,wp);
        if(m!=null){wavePanel.setClickable(true);wavePanel.setFocusable(true);wavePanel.setOnClickListener(v->act(this::review));}
        if(m!=null){
            LinearLayout first=new LinearLayout(this);first.setOrientation(LinearLayout.HORIZONTAL);
            StudioVisuals.metric(this,first,"LOUDNESS",fmt(m.optDouble("lufs"))+" LUFS",StudioVisuals.CYAN);
            double tp=m.optDouble("true_peak");StudioVisuals.metric(this,first,"TRUE PEAK",fmt(tp)+" dBTP",tp>-1.1?StudioVisuals.RED:StudioVisuals.GREEN);
            root.addView(first,new LinearLayout.LayoutParams(-1,-2));
            LinearLayout second=new LinearLayout(this);second.setOrientation(LinearLayout.HORIZONTAL);
            StudioVisuals.metric(this,second,"LOUDNESS RANGE",fmt(m.optDouble("lra"))+" LU",StudioVisuals.TEXT);
            StudioVisuals.metric(this,second,"FORMAT","24-bit  •  48 kHz",StudioVisuals.CYAN);
            LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,-2);slp.setMargins(0,dp(5),0,dp(5));root.addView(second,slp);
        }
        if(state.equals("FAILED"))StudioVisuals.notice(this,root,"Mastering needs attention",message,true);
        else if(state.equals("COMPLETE"))StudioVisuals.notice(this,root,"Studio master complete","Verified audio and reports are ready to review or export.",false);
        else StudioVisuals.notice(this,root,state,message,false);
        if(p.data.has("source_recovery_notice")&&!p.data.optString("source_recovery_notice","").isEmpty())
            StudioVisuals.notice(this,root,"Source recovery notice",p.data.optString("source_recovery_notice",""),true);
        if(p.data.optInt("revision",0)!=p.data.optInt("rendered_revision",-1)&&m!=null)
            StudioVisuals.notice(this,root,"Saved edits pending","The current master still represents the last rendered recipe.",true);
        if(p.data.optBoolean("imported_master_trial_b",false)){
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
        StudioVisuals.section(this,root,"Mastering Controls");
        actions("RENDER THIS PROJECT","Process and verify",()->{idle();permission();StudioJobs.project(this,project());detail();},
                "ADD TO QUEUE","Queue current settings",()->{idle();project().state("QUEUED","Saved edits queued for render");detail();},true);
        if(m!=null)button("REVIEW / LOOPED A–B",this::review);
        actions("PASSAGE EDITS + BASS CONTROL","Fine-tune sections",this::edits,
                "REFERENCE TRACKS","Level-matched A/B",this::references,false);
        actions("TRIMS, FADES, DELIVERY + METADATA","Final export settings",this::settings,
                "UNDO LAST RECIPE CHANGE","Restore earlier edits",()->{idle();StudioProject q=project();if(!q.undo())throw new IOException("No saved recipe change to undo");detail();},false);
        if(m!=null){
            button(p.data.has("trial_original_title") ? "SAVE TRIAL B WAV — SONG NAME" : "SAVE 24-BIT / 48 kHz WAV",
                    ()->{StudioProject q=project();
                         if(q.data.has("trial_original_title"))saveWithName(q.file("master"),"audio/wav",trialBFilename(q));
                         else save(q.file("master"),"audio/wav");});
            if(p.file("wav16")!=null)button("SAVE 16-BIT / 44.1 kHz WAV",()->save(project().file("wav16"),"audio/wav"));
        }
        actions("SAVE FULL PROJECT BACKUP","Preserve source and edits",()->{StudioProject q=project();job("project backup",()->{
            File zip=new File(getFilesDir(),q.dir.getName()+"-Project.zip");backup(q,zip);StudioJobs.after(()->{if(alive&&visible)act(()->save(zip,"application/zip"));});
        });},"REFRESH THIS PROJECT","Reload saved details",this::detail,false);
        View danger=StudioVisuals.tile(this,"DELETE THIS SAVED PROJECT","Removes local project files",false,true,()->act(()->{
            idle();new AlertDialog.Builder(this).setTitle("Delete "+p.name()+"?")
                    .setMessage("This removes its imported source copy, masters, edits and reports from the app. Files already saved elsewhere remain.")
                    .setNegativeButton("Keep",null).setPositiveButton("Delete",(d,w)->{StudioPipeline.removeTree(p.dir);home();}).show();
        }));LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(-1,-2);dp.setMargins(0,dp(8),0,dp(10));root.addView(danger,dp);
    }
    private void settings()throws Exception{
        idle();StudioProject p=project();StudioRecipe r=p.recipe();page="settings";shell("Finishing and delivery");double duration=p.data.optDouble("duration",0);
        text("All times are seconds on the original song. End 0 means the complete song. A trim is an explicit arrangement edit; fades never shorten the file.",14,false);
        EditText start=field("Trim start",fmt(r.trimStart),true),end=field("Trim end (0 = full song)",fmt(r.trimEnd),true);
        EditText fadeIn=field("Fade in (seconds)",fmt(r.fadeIn),true),fadeOut=field("Fade out (seconds)",fmt(r.fadeOut),true);
        CheckBox edges=check("Repair nonzero start/end boundaries (5 / 10 ms)",r.boundaryRepair);
        text("Boundary repair fades only a flagged edge. Listen to confirm any abrupt ending is intentional.",13,false);
        Spinner profile=spinner("Delivery starting point",new String[]{"Keep/custom","Streaming −14 / −1.1","Dynamic −16 / −1.1","Louder −12 / −1.1","Broadcast starting point −23 / −2"},0);
        EditText target=field("Target LUFS-I",fmt(r.target),true),ceiling=field("Maximum true peak dBTP",fmt(r.ceiling),true);
        profile.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> a){}public void onItemSelected(android.widget.AdapterView<?> a,View v,int i,long id){if(i>0){target.setText(new String[]{"","-14","-16","-12","-23"}[i]);ceiling.setText(i==4?"-2":"-1.1");}}});
        text("Requested loudness may be reduced to protect dynamics. A broadcast preset must be checked against the station's requirements.",13,false);
        EditText title=field("Track title",r.title,false),artist=field("Artist",r.artist,false),album=field("Album",r.album,false),isrc=field("ISRC (optional, 12 characters)",r.isrc,false),track=field("Track number",Integer.toString(r.track),true);
        button("SAVE FINISHING SETTINGS",()->{idle();r.trimStart=number(start);r.trimEnd=number(end);r.fadeIn=number(fadeIn);r.fadeOut=number(fadeOut);r.boundaryRepair=edges.isChecked();r.target=number(target);r.ceiling=number(ceiling);
            r.title=title.getText().toString().trim();r.artist=artist.getText().toString().trim();r.album=album.getText().toString().trim();r.isrc=isrc.getText().toString().trim();r.track=(int)number(track);
            StudioRecipe.range(r.track,1,999,"Track number");r.validate(duration);p.recipe(r);detail();});
    }
    private void edits()throws Exception{
        idle();StudioProject p=project();StudioRecipe r=p.recipe();page="edits";shell("Passage edits and bass control");
        text("Edits use original-song times. Each render starts from the retained base master. Gain rides are capped at ±3 dB; dynamic EQ cuts at 1.5 dB; overlapping EQ shares a 4 dB budget.",14,false);
        CheckBox bass=check("Allow verified automatic bass / low-mid corrections",r.lowEnd);
        bass.setOnCheckedChangeListener((v,on)->act(()->{idle();StudioProject q=project();StudioRecipe recipe=q.recipe();recipe.lowEnd=on;q.recipe(recipe);}));
        button("ADD PASSAGE EDIT",this::addEdit);
        if(r.edits.isEmpty())text("No passage edits. Automatic bass proposals appear after mastering when evidence supports them.",14,false);
        for(StudioRecipe.Edit e:r.edits){
            CheckBox enabled=check(String.format(Locale.US,"%s • %.2f–%.2fs • %+.2f dB%s • %s",e.kind,e.start,e.end,e.db,e.kind.equals("GAIN")?"":" at "+Math.round(e.hz)+" Hz",e.channel),e.enabled);
            text(e.reason,12,false);
            enabled.setOnCheckedChangeListener((v,on)->act(()->{idle();StudioProject q=project();StudioRecipe recipe=q.recipe();for(StudioRecipe.Edit x:recipe.edits)if(x.id.equals(e.id))x.enabled=on;q.recipe(recipe);}));
            if(p.data.has("master"))button("AUDITION THIS EDIT BYPASSED",()->bypass(e.id));
            button("REMOVE THIS EDIT",()->{idle();StudioProject q=project();StudioRecipe recipe=q.recipe();recipe.edits.removeIf(x->x.id.equals(e.id));q.recipe(recipe);edits();});
        }
        button("BACK TO PROJECT / RENDER",this::detail);
    }
    private void addEdit()throws Exception{
        StudioProject p=project();page="add-edit";shell("New passage edit");
        Spinner kind=spinner("Type",new String[]{"GAIN","EQ","DYNAMIC_EQ"},0),channel=spinner("EQ channel",new String[]{"STEREO","MID","SIDE"},0);
        EditText start=field("Start seconds","0",true),end=field("End seconds",fmt(p.data.optDouble("duration",0)),true);
        EditText db=field("Amount dB (dynamic EQ: −1.5 to 0)","-0.5",true),hz=field("EQ centre Hz","160",true),q=field("EQ Q","1.2",true),threshold=field("Dynamic band RMS threshold dBFS","-28",true);
        text("Mid is shared centre audio; Side is the difference between channels. These are not isolated vocals or instruments. Gain rides affect both channels together.",13,false);
        button("ADD EDIT",()->{idle();StudioRecipe r=p.recipe();StudioRecipe.Edit e=new StudioRecipe.Edit();e.kind=kind.getSelectedItem().toString();e.channel=e.kind.equals("GAIN")?"STEREO":channel.getSelectedItem().toString();e.start=number(start);e.end=number(end);e.db=number(db);e.hz=number(hz);e.q=number(q);e.threshold=number(threshold);r.edits.add(e);r.validate(p.data.optDouble("duration",0));p.recipe(r);edits();});
    }
    private void review()throws Exception{
        idle();StudioProject p=project();if(!p.validFile("master","master_hash"))throw new IOException("Master missing or changed. Render again.");page="review";shell("Review "+p.recipe().title);
        text("A = original, aligned to the edited duration. B = finished master. Playback is loudness-matched; the saved master is unchanged.",14,false);
        controls();File a=p.file("aligned_source"),b=p.file("master");preparePlayer(a,b);
        JSONObject radio=p.data.getJSONObject("final_qc").optJSONObject("radio_preparation");JSONArray issues=radio==null?null:radio.optJSONArray("listening_checks");
        if(issues!=null)for(int i=0;i<issues.length();i++){JSONObject issue=issues.getJSONObject(i);double at=issue.optDouble("start_seconds",0);button("CHECK "+fmt(at)+"s: "+issue.optString("detail"),()->{if(player!=null){player.seekSeconds(Math.max(0,at-1));player.play(true);}});}
        StudioRecipe used=StudioRecipe.read(p.data.optJSONObject("last_recipe"));for(StudioRecipe.Edit e:used.edits)if(e.enabled){double at=Math.max(0,e.start-used.trimStart);button("EDIT MARKER "+fmt(at)+"s • "+e.kind,()->{if(player!=null){player.loopSeconds(Math.max(0,at-1),Math.min(playbackDuration,e.end-used.trimStart+1),true);player.play(true);}});}
        button("BACK TO PROJECT",this::detail);
    }
    private void controls(){
        position=text("Preparing aligned playback…",14,true);seek=new SeekBar(this);seek.setMax(10000);root.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){dragging=true;}public void onStopTrackingTouch(SeekBar s){dragging=false;if(player!=null)player.seekSeconds(s.getProgress()*playbackDuration/10000);}public void onProgressChanged(SeekBar s,int n,boolean user){}});
        button("PLAY / SWITCH TO A",()->{if(player!=null)player.play(false);});button("PLAY / SWITCH TO B",()->{if(player!=null)player.play(true);});button("PAUSE PLAYBACK",()->{if(player!=null)player.pause();});
        CheckBox mono=check("Mono audition",false),delta=check("Hear level-matched difference (half level)",false);
        mono.setOnCheckedChangeListener((v,b)->{if(player!=null)player.mono(b);});delta.setOnCheckedChangeListener((v,b)->{if(player!=null)player.delta(b);});
        EditText from=field("Loop start seconds","0",true),to=field("Loop end seconds","15",true);
        button("LOOP SELECTED PASSAGE",()->{double start=number(from),end=number(to);if(start<0||end<=start+.05||end>playbackDuration)throw new IOException("Choose a loop within this playback timeline");if(player!=null){player.loopSeconds(start,end,true);player.play(true);}});
        button("CLEAR LOOP",()->{if(player!=null)player.loopSeconds(0,0,false);});
    }
    private void preparePlayer(File a,File b)throws Exception{
        long token=generation;job("preparing review",()->{
            AnalysisResult aa=StudioAudio.measure(a,StudioAudio.QUIET),bb=StudioAudio.measure(b,StudioAudio.QUIET);
            runOnUiThread(()->{if(!alive||generation!=token)return;playbackDuration=aa.wav.durationSeconds;
                player=new ComparisonPlayer(this,a,aa,b,bb,new ComparisonPlayer.Listener(){
                    public void position(double seconds,boolean playing){runOnUiThread(()->{if(!alive||generation!=token)return;playbackSeconds=seconds;if(position!=null)position.setText(fmt(seconds)+" / "+fmt(playbackDuration)+" s" );if(seek!=null&&!dragging)seek.setProgress((int)(seconds*10000/playbackDuration));});}
                    public void failure(String message){runOnUiThread(()->error(new IOException(message)));}
                });position.setText("Ready • "+fmt(playbackDuration)+" seconds");
            });
        });
    }
    private void bypass(String id)throws Exception{
        idle();StudioProject p=project();if(p.data.optInt("revision",0)!=p.data.optInt("rendered_revision",-1))throw new IOException("Render saved edits first, then audition individual bypasses");
        StudioRecipe r=StudioRecipe.read(p.data.getJSONObject("last_recipe"));boolean found=false;for(StudioRecipe.Edit e:r.edits)if(e.id.equals(id)&&e.enabled){e.enabled=false;found=true;}if(!found)throw new IOException("This edit was not enabled in the finished master");
        File cache=new File(getCacheDir(),"studio-bypass");cache.mkdirs();File floated=new File(cache,"bypass-float.wav"),out=new File(cache,"bypass.wav");
        page="bypass";shell("Audition one correction bypassed");text("A = finished master. B = same saved recipe with this correction bypassed. Playback remains level-matched.",14,false);controls();long token=generation;
        job("rendering correction bypass",()->{File base=p.file("base");AnalysisResult a=StudioAudio.measure(base,StudioAudio.QUIET);StudioAudio.EditResult edit=StudioAudio.edit(base,a,r,floated,StudioAudio.QUIET);StudioAudio.deliver(floated,edit.analysis,r,out,StudioAudio.QUIET);
            StudioJobs.after(()->{if(alive&&generation==token)act(()->preparePlayer(p.file("master"),out));});
        });
    }
    private void references()throws Exception{
        idle();StudioProject p=project();page="references";shell("Reference tracks");
        text("Use local 48 kHz stereo WAVs. Compare equivalent musical passages at matched loudness. Suggestions start bypassed and never impose a catalogue curve.",14,false);
        button("IMPORT REFERENCE WAV",()->{idle();pickerProject=projectId;pick(REFERENCE,false);});
        JSONArray refs=p.data.optJSONArray("references");if(refs!=null)for(int i=0;i<refs.length();i++){final int index=i;JSONObject ref=refs.getJSONObject(i);button(ref.optString("name","Reference"),()->referencePassage(index));}
        button("BACK TO PROJECT",this::detail);
    }
    private void referencePassage(int index)throws Exception{
        StudioProject p=project();if(p.file("master")==null)throw new IOException("Master this project before reference comparison");
        if(p.data.optInt("revision",0)!=p.data.optInt("rendered_revision",-1))throw new IOException("Render saved edits before comparing reference passages");
        JSONObject ref=p.data.getJSONArray("references").getJSONObject(index);
        page="reference-passage";shell(ref.getString("name"));
        EditText a=field("Finished song start seconds","0",true),b=field("Reference start seconds","0",true),duration=field("Passage length (5–60 seconds)","20",true);
        button("PREPARE MATCHED PASSAGE COMPARISON",()->{
            double sa=number(a),sb=number(b),length=number(duration);StudioRecipe.range(length,5,60,"Passage length");StudioRecipe.range(sa,0,86400,"Song start");StudioRecipe.range(sb,0,86400,"Reference start");
            File cache=new File(getCacheDir(),"studio-reference");cache.mkdirs();File left=new File(cache,"song.wav"),right=new File(cache,"reference.wav");
            long token=generation;
            job("preparing reference passages",()->{
                StudioAudio.region(p.file("master"),left,sa,length,StudioAudio.QUIET);StudioAudio.region(p.child(ref.getString("file")),right,sb,length,StudioAudio.QUIET);
                JSONObject lp=StudioReferences.profile(left,StudioAudio.QUIET),rp=StudioReferences.profile(right,StudioAudio.QUIET);
                StudioRecipe current=p.recipe();JSONArray suggestions=StudioReferences.suggestions(lp,rp,sa+current.trimStart,sa+current.trimStart+length);
                StudioJobs.after(()->{if(!alive||!visible||generation!=token)return;act(()->{page="reference-review";shell("Reference audition");text("A = your master passage. B = reference passage. Both have independent start positions and matched passage loudness.",14,false);controls();preparePlayer(left,right);
                    text(suggestions.length()+" optional broad EQ suggestion(s). A difference can be intentional.",14,false);
                    button("ADD SUGGESTIONS TO EDITS — BYPASSED",()->{idle();StudioProject latest=project();StudioRecipe r=latest.recipe();for(int n=0;n<suggestions.length();n++)r.edits.add(StudioRecipe.Edit.read(suggestions.getJSONObject(n)));r.validate(latest.data.optDouble("duration",0));latest.recipe(r);edits();});
                });});
            });
        });
        button("REMOVE REFERENCE FROM PROJECT",()->{idle();JSONArray all=p.data.getJSONArray("references"),keep=new JSONArray();for(int i=0;i<all.length();i++)if(i!=index)keep.put(all.get(i));p.data.put("references",keep);p.save();references();});
    }
    private void album()throws Exception{
        page="album";shell("Album comparison and sequence");List<StudioProject> list=StudioProject.list(projects,null);
        list.removeIf(p->!p.data.has("master_meters"));
        text("Compare the actual finished masters, choose track order and spacing, then export a continuous WAV listening sequence and report. Each project keeps its verified 24-bit/48 kHz and 16-bit/44.1 kHz WAV outputs.",14,false);
        for(int i=0;i<list.size();i++){StudioProject p=list.get(i);if(!p.data.has("master_meters"))continue;final int index=i;
            CheckBox included=check(p.recipe().title,p.data.optBoolean("include_album",true));included.setOnCheckedChangeListener((v,on)->act(()->{idle();StudioProject q=StudioProject.load(p.dir);q.data.put("include_album",on);q.save();}));
            if(i>0)button("MOVE "+p.recipe().title+" UP",()->{idle();StudioProject prev=StudioProject.load(list.get(index-1).dir),cur=StudioProject.load(p.dir);long order=cur.data.optLong("order",0);cur.data.put("order",prev.data.optLong("order",0));prev.data.put("order",order);cur.save();prev.save();album();});
        }
        button("COMPARE FINISHED SONGS",()->{JSONObject j=StudioAlbum.compare(StudioProject.list(projects,null));JSONArray songs=j.getJSONArray("songs");StringBuilder s=new StringBuilder("Album median "+fmt(j.getDouble("median_lufs"))+" LUFS\n\n");
            for(int i=0;i<songs.length();i++){JSONObject song=songs.getJSONObject(i);s.append(song.getString("title")).append(": ").append(fmt(song.getDouble("lufs"))).append(" LUFS; tonal difference up to ").append(fmt(song.getDouble("maximum_broad_band_difference_db"))).append(" dB").append(song.getBoolean("review")?" — REVIEW":"").append('\n');}
            s.append("\n").append(j.getString("meaning"));new AlertDialog.Builder(this).setTitle("Album consistency").setMessage(s).setPositiveButton("OK",null).show();});
        EditText title=field("Album sequence title","Album",false),gap=field("Silence between songs (seconds)","2",true);
        button("EXPORT ALBUM SEQUENCE + QC",()->{double seconds=number(gap);String name=title.getText().toString();StudioRecipe.range(seconds,0,10,"Spacing");job("album sequence",()->{
            File out=StudioAlbum.export(StudioProject.list(projects,null),new File(getFilesDir(),"studio-albums/"+System.currentTimeMillis()),name,seconds,MasteringForegroundService::update);
            StudioJobs.after(()->{if(alive&&visible)act(()->save(out,"application/zip"));});
        });});
    }
    private void pick(int code,boolean multiple){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType(code==STEM_ZIP?"application/zip":"audio/*");if(code==STEM_ZIP)i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/zip","application/x-zip-compressed","application/octet-stream"});i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,multiple);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,code);}
    private static String trialBFilename(StudioProject project) {
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
    }
    private String name(Uri uri){try(android.database.Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}return "Song.wav";}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null)return;act(()->{
        if(request==SAVE){Uri destination=data.getData();File source=new File(exportPath);job("saving file",()->{
            if(destination==null)throw new IOException("No save destination");String expected=Hashing.sha256(source);
            try(InputStream in=new FileInputStream(source);OutputStream out=getContentResolver().openOutputStream(destination,"wt")){if(out==null)throw new IOException("Save destination could not be opened");byte[] b=new byte[262144];int n;while((n=in.read(b))!=-1){out.write(b,0,n);StudioAudio.cancel();}}
            java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");try(InputStream in=getContentResolver().openInputStream(destination)){if(in==null)throw new IOException("Saved file cannot be verified");byte[] b=new byte[262144];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}StringBuilder actual=new StringBuilder();for(byte b:digest.digest())actual.append(String.format(Locale.US,"%02x",b&255));if(!actual.toString().equals(expected))throw new IOException("Saved file checksum mismatch; the verified internal copy is retained");
        });return;}
        if(request==MASTER_B_WAV){
            ArrayList<Uri> chosen=new ArrayList<>();
            if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++)
                chosen.add(data.getClipData().getItemAt(i).getUri());
            else if(data.getData()!=null)chosen.add(data.getData());
            if(chosen.isEmpty()||chosen.size()>40)
                throw new IOException("Select between 1 and 40 mastered WAV files.");
            job("bulk imported-master Trial B",()->{
                ArrayList<StudioProject> staged=new ArrayList<>();
                ArrayList<String> errors=new ArrayList<>();
                try{
                    for(int i=0;i<chosen.size();i++){
                        StudioAudio.cancel();
                        Uri uri=chosen.get(i);String filename=name(uri);
                        try{
                            if(!filename.toLowerCase(Locale.ROOT).endsWith(".wav"))
                                throw new IOException("File is not a WAV");
                            MasteringForegroundService.update("Copying verified master "+(i+1)+"/"+chosen.size(),0);
                            try(InputStream stream=getContentResolver().openInputStream(uri)){
                                if(stream==null)throw new IOException("Cannot open WAV");
                                staged.add(ImportedMasterTrialB.stage(projects,filename,stream));
                            }
                        }catch(Exception e){
                            if(Thread.currentThread().isInterrupted())throw e;
                            errors.add(filename+": "+e.getMessage());
                        }
                    }
                    if(!staged.isEmpty())
                        StudioQueue.run(staged,StudioQueue::process,MasteringForegroundService::update);
                    if(!errors.isEmpty())throw new IOException(errors.size()+" import(s) failed. Other saved projects can resume. First: "+errors.get(0));
                }finally{
                    StudioJobs.after(()->{if(alive&&visible)home();});
                }
            });
            return;
        }
        ArrayList<Uri> uris=new ArrayList<>();if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());}else if(data.getData()!=null)uris.add(data.getData());
        if(uris.size()>(request==STEM_ZIP?1:40))throw new IOException(request==STEM_ZIP?"Select one stem ZIP at a time":"Import at most 40 WAVs at a time");String referenceProject=pickerProject;
        job("importing audio",()->{
            ArrayList<String> failures=new ArrayList<>();
            for(Uri uri:uris){StudioAudio.cancel();String title=name(uri);StudioProject p=request==REFERENCE?StudioProject.load(new File(projects,referenceProject)):StudioProject.create(projects,request==STEM_ZIP?title.replaceFirst("(?i)\\.zip$",""):title);
                try{
                    InputStream stream=getContentResolver().openInputStream(uri);if(stream==null)throw new IOException("Cannot read selected file");
                    if(request==REFERENCE){
                        String relative="reference-"+UUID.randomUUID()+".wav";File f=p.child(relative);String hash=StudioImports.copyVerified(stream,f);
                        AnalysisResult measured=StudioAudio.measure(f,MasteringForegroundService::update);StudioAudio.valid(measured,measured.wav.frames);
                        if(!hash.equals(measured.sourceSha256))throw new IOException("Reference WAV changed during import");
                        JSONArray refs=p.data.getJSONArray("references");if(refs.length()>=4){f.delete();throw new IOException("Keep at most four references per project");}refs.put(new JSONObject().put("name",title).put("file",relative).put("sha256",hash));p.save();
                    }else if(request==STEM_ZIP)StemOnlyImport.source(p,stream,MasteringForegroundService::update);
                    else StudioImports.source(p,stream,MasteringForegroundService::update);
                }catch(Exception e){if(request!=REFERENCE){p.data.put("import_error",e.getClass().getSimpleName()+": "+e.getMessage());p.state(Thread.currentThread().isInterrupted()?"PAUSED":"FAILED",e.getMessage());}if(Thread.currentThread().isInterrupted())throw e;failures.add(title+": "+e.getMessage());}
            }
            StudioJobs.after(()->{if(alive)act(()->{if(request==REFERENCE){projectId=referenceProject;references();}else home();});});
            if(!failures.isEmpty())throw new IOException(String.join("\n",failures));
        });
    });}
    private static void backup(StudioProject p,File output)throws Exception{
        ArrayList<File> files=new ArrayList<>();collect(p.dir,files);try(ZipOutputStream z=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output)))){
            z.setLevel(1);byte[] b=new byte[262144];for(File f:files){String path=p.dir.toPath().relativize(f.toPath()).toString();z.putNextEntry(new ZipEntry(path));try(InputStream in=new FileInputStream(f)){int n;while((n=in.read(b))!=-1){z.write(b,0,n);StudioAudio.cancel();}}z.closeEntry();}
        }
    }
    private static void collect(File f,List<File> list){if(f.isDirectory()){if(f.getName().startsWith("base-work"))return;File[] children=f.listFiles();if(children!=null)for(File c:children)collect(c,list);}else if(!f.getName().endsWith(".tmp")&&!f.getName().endsWith(".importing"))list.add(f);}
    private void stop(){generation++;if(player!=null){player.close();player=null;}}
}
