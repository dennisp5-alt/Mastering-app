package com.dennis.aimusic;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import android.media.MediaPlayer;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Fully offline experimental native Android app. No Internet, microphone or filesystem permission. */
public final class MainActivity extends Activity {
    private static final int REQUEST_IMPORT=51,REQUEST_EXPORT=52;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final int BG=0xff101619, PANEL=0xff192427, ACCENT=0xff76dbc3;
    private LibraryStore library;
    private TinyMusicBrain brain=new TinyMusicBrain();
    private TextView status,stats,subtitle;
    private EditText bpm;
    private Spinner genre,duration;
    private Button importButton,composeButton,playButton,exportButton,resetButton,newSeedButton;
    private CheckBox useTraining;
    private long comparisonSeed=4837291021L;
    private volatile File lastSong;
    private MediaPlayer mediaPlayer;
    private boolean busy;

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        buildUI();
        try {library=new LibraryStore(getFilesDir());startTraining();}
        catch(Exception e){setStatus("Library issue: "+e.getMessage());}
    }
    private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String value,int size,int color){
        TextView t=new TextView(this);t.setText(value);t.setTextColor(color);t.setTextSize(size);
        t.setPadding(0,dp(6),0,dp(6));return t;
    }
    private void buildUI(){
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        LinearLayout body=new LinearLayout(this);body.setPadding(dp(18),dp(26),dp(18),dp(40));body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);setContentView(scroll);
        TextView heading=text("DENNIS  /  MUSIC STUDIO",24,Color.WHITE);heading.setTypeface(Typeface.DEFAULT,Typeface.BOLD);body.addView(heading);
        subtitle=text("v0.2   •   EXPERIMENTAL   •   100% OFFLINE",12,ACCENT);body.addView(subtitle);
        body.addView(text("Teach a small neural composer using music you have training rights to. Then generate a 3–5-minute instrumental sketch without leaving your phone.",14,0xffb9c8cb));
        stats=text("Preparing training library…",15,Color.WHITE);stats.setPadding(dp(12),dp(14),dp(12),dp(14));stats.setBackgroundColor(PANEL);body.addView(stats);
        importButton=button(body,"1   IMPORT & LEARN FROM WAV",true);importButton.setOnClickListener(v->pickWav());
        body.addView(text("Input: WAV 16/24/32-bit PCM or 32-bit float. The app analyses dominant pitch-class patterns, not voice identity or professional production detail.",12,0xff9aafb0));
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);body.addView(row);
        row.addView(text("TEMPO",13,ACCENT));
        bpm=new EditText(this);bpm.setSingleLine(true);bpm.setText("104");bpm.setTextColor(Color.WHITE);
        bpm.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);bpm.setSelectAllOnFocus(true);
        LinearLayout.LayoutParams editParams=new LinearLayout.LayoutParams(dp(90),dp(55));editParams.leftMargin=dp(14);row.addView(bpm,editParams);
        body.addView(text("GENERATION STYLE",13,ACCENT));
        genre=spinner(body,new String[]{"Country sketch","Rock sketch","Dance sketch","R&B sketch"});
        body.addView(text("SONG LENGTH",13,ACCENT));
        duration=spinner(body,new String[]{"3 minutes","4 minutes","5 minutes"});
        useTraining=new CheckBox(this);
        useTraining.setText("Use learned music model");
        useTraining.setChecked(true);
        useTraining.setTextColor(Color.WHITE);
        body.addView(useTraining);
        body.addView(text("A/B TEST: Switch learned model on/off and generate twice with the same seed. Only the model changes. Save each WAV to compare.",12,0xff9aafb0));
        newSeedButton=button(body,"NEW COMPOSITION SEED",false);
        newSeedButton.setOnClickListener(v->{comparisonSeed=System.nanoTime();setStatus("New composition seed selected. Generate for another controlled A/B comparison.");});
        composeButton=button(body,"2   COMPOSE INSTRUMENTAL WAV",true);composeButton.setOnClickListener(v->compose());
        playButton=button(body,"PLAY LAST COMPOSITION",false);playButton.setEnabled(false);playButton.setOnClickListener(v->togglePlay());
        exportButton=button(body,"3   SAVE WAV TO PHONE",false);exportButton.setEnabled(false);exportButton.setOnClickListener(v->saveWav());
        resetButton=button(body,"CLEAR TRAINING LIBRARY",false);resetButton.setOnClickListener(v->confirmClear());
        status=text("Import a recording to begin.",13,0xffb2c8c7);body.addView(status);
        body.addView(text("REALISTIC LIMITS  •  Locally trained note-sequence predictor and simple synthesised instruments only. This build does not generate vocals, lyrics, cloned voices, or Suno-quality music.",12,0xff91a5a8));
    }
    private Spinner spinner(LinearLayout parent,String[] items){
        Spinner s=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,items);
        s.setAdapter(adapter);parent.addView(s,new LinearLayout.LayoutParams(-1,dp(52)));return s;
    }
    private Button button(LinearLayout parent,String label,boolean primary){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(13);
        b.setTextColor(primary?BG:Color.WHITE);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary?ACCENT:PANEL));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(54));lp.topMargin=dp(12);parent.addView(b,lp);return b;
    }
    private void setStatus(String msg){if(status!=null)status.setText(msg);}
    private void syncStats(){
        if(library==null)return;
        int n=library.size();
        stats.setText("TRAINING LIBRARY   "+n+" / 75 WAVs\n"+
                "Local network: "+(brain.isTrained()?"TRAINED ("+brain.getExamples()+" examples)":"NOT TRAINED")+"\n"+
                "Estimated tonal root: "+new String[]{"C","C#","D","D#","E","F","F#","G","G#","A","A#","B"}[WavAnalyzer.estimatedRoot(library.tracks())]);
    }
    private void setBusy(boolean value) {
        busy=value;importButton.setEnabled(!value&&library!=null);composeButton.setEnabled(!value);
        playButton.setEnabled(!value&&lastSong!=null);exportButton.setEnabled(!value&&lastSong!=null);
        resetButton.setEnabled(!value&&library!=null);
        newSeedButton.setEnabled(!value);
        useTraining.setEnabled(!value&&brain.isTrained());
    }
    private void pickWav() {
        Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("*/*");pick.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"audio/wav","audio/x-wav","audio/wave","audio/vnd.wave","application/octet-stream"});
        startActivityForResult(pick,REQUEST_IMPORT);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(requestCode==REQUEST_IMPORT) {
            setBusy(true);setStatus("Reading recording and extracting musical features…");
            worker.execute(()->{
                try(InputStream in=getContentResolver().openInputStream(uri)) {
                    if(in==null)throw new IOException("Cannot read selected recording");
                    String name=uri.getLastPathSegment();if(name==null)name="Untitled";
                    final String title=name.length()>160?name.substring(name.length()-160):name;
                    WavAnalyzer.SongProfile profile=WavAnalyzer.analyse(in,title,null);
                    library.add(profile);
                    brain=rebuild(library.tracks());
                    main(()->{syncStats();setBusy(false);setStatus("Learned "+title+" ("+profile.notes.length+" feature events). Stored locally.");});
                } catch(Exception e){main(()->{setBusy(false);setStatus("Import failed: "+e.getMessage());});}
            });
        } else if(requestCode==REQUEST_EXPORT){
            File source=lastSong;
            if(source==null)return;
            setBusy(true);setStatus("Copying rendered WAV to chosen location…");
            worker.execute(()->{
                try(InputStream in=new BufferedInputStream(new FileInputStream(source));OutputStream out=getContentResolver().openOutputStream(uri,"w")){
                    if(out==null)throw new IOException("Cannot open output file");
                    byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
                    main(()->{setBusy(false);setStatus("WAV saved successfully.");});
                }catch(Exception ex){main(()->{setBusy(false);setStatus("Save failed: "+ex.getMessage());});}
            });
        }
    }
    private TinyMusicBrain rebuild(java.util.List<WavAnalyzer.SongProfile> tracks){
        ArrayList<int[]> seq=new ArrayList<>();for(WavAnalyzer.SongProfile p:tracks)seq.add(p.notes);
        TinyMusicBrain fresh=new TinyMusicBrain();fresh.fit(seq);return fresh;
    }
    private void startTraining(){
        setBusy(true);setStatus("Training neural composer from local library…");
        worker.execute(()->{
            TinyMusicBrain fresh=rebuild(library.tracks());
            brain=fresh;main(()->{syncStats();setBusy(false);setStatus("Ready. You can import additional tracks or compose a sketch.");});
        });
    }
    private void compose(){
        int beats;try{beats=Integer.parseInt(bpm.getText().toString().trim());}catch(Exception e){beats=104;}
        if(beats<65||beats>170){setStatus("Tempo must be between 65 and 170 BPM.");return;}
        final int finalBpm=beats,seconds=(3+duration.getSelectedItemPosition())*60,style=genre.getSelectedItemPosition();
        final int root=library==null?0:WavAnalyzer.estimatedRoot(library.tracks());
        final boolean trained=useTraining.isChecked()&&brain.isTrained();
        final TinyMusicBrain snapshot=trained?brain:new TinyMusicBrain();
        final long seed=comparisonSeed;
        stopPlayback();setBusy(true);setStatus("Composing "+(trained?"LEARNED":"UNTRAINED")+" model using fixed seed "+seed+"…");
        worker.execute(()->{
            try {
                File out=new File(getFilesDir(),"dennis_v02_"+(trained?"learned":"control")+"_"+seconds+"s_"+seed+".wav");
                SongComposer.Plan plan=SongComposer.compose(snapshot,finalBpm,seconds,root,style,seed);
                SongRenderer.render(plan,out,progress->{if(progress%12==0)main(()->setStatus("Rendering locally… "+progress+"%"));});
                lastSong=out;
                main(()->{syncStats();setBusy(false);setStatus("Complete: "+seconds/60+"-minute, "+finalBpm+" BPM instrumental WAV. " +
                    (trained?"Learned model A. Save this WAV, then uncheck training and regenerate for B.":"Untrained comparison B. Save and compare to learned model A."));});
            }catch(Exception e){main(()->{setBusy(false);setStatus("Render failed: "+e.getMessage());});}
        });
    }
    private void togglePlay(){
        if(mediaPlayer!=null&&mediaPlayer.isPlaying()){stopPlayback();playButton.setText("PLAY LAST COMPOSITION");return;}
        if(lastSong==null)return;
        stopPlayback();
        try{
            mediaPlayer=new MediaPlayer();mediaPlayer.setDataSource(lastSong.getAbsolutePath());
            mediaPlayer.setOnCompletionListener(mp->{stopPlayback();playButton.setText("PLAY LAST COMPOSITION");});
            mediaPlayer.prepare();mediaPlayer.start();playButton.setText("STOP PLAYBACK");
        }catch(Exception e){stopPlayback();setStatus("Playback failed: "+e.getMessage());}
    }
    private void stopPlayback(){
        if(mediaPlayer!=null){try{mediaPlayer.stop();}catch(Exception ignore){}mediaPlayer.release();mediaPlayer=null;}
        if(playButton!=null)playButton.setText("PLAY LAST COMPOSITION");
    }
    private void saveWav(){
        if(lastSong==null)return;
        Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT);save.addCategory(Intent.CATEGORY_OPENABLE);
        save.setType("audio/wav");save.putExtra(Intent.EXTRA_TITLE,"Dennis_AI_Music_"+System.currentTimeMillis()+".wav");
        startActivityForResult(save,REQUEST_EXPORT);
    }
    private void confirmClear(){
        new AlertDialog.Builder(this).setTitle("Delete learning library?")
            .setMessage("This permanently deletes all imported feature profiles on this phone. Original WAV files are untouched.")
            .setNegativeButton("Cancel",null).setPositiveButton("Clear",(d,w)->{
                try{library.clear();brain=new TinyMusicBrain();syncStats();setStatus("Training library cleared.");}
                catch(IOException e){setStatus("Could not clear: "+e.getMessage());}
            }).show();
    }
    private void main(Runnable runnable){runOnUiThread(runnable);}
    @Override protected void onDestroy(){stopPlayback();worker.shutdownNow();super.onDestroy();}
}