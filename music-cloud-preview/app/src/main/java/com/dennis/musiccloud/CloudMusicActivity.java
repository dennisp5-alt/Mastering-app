package com.dennis.musiccloud;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Minimal ACE-Step cloud generator for a Galaxy S23 Ultra. No training data is uploaded by this app. */
public final class CloudMusicActivity extends Activity {
    private static final int SAVE=75;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private EditText endpoint,secret,description,lyrics,bpm;
    private Spinner model,duration;
    private Button test,generate,play,save;
    private TextView status;
    private File generated;
    private volatile boolean cancel=false;
    private MediaPlayer player;
    private static final String DEMO_PROMPT =
        "Australian country rock, 104 BPM, C major, live Nashville session with authentic pub-rock energy. "+
        "Warm weathered male baritone with natural Australian pronunciation, close-miked expressive singing. "+
        "Acoustic rhythm guitar, melodic electric bass, real acoustic drums with snare ghost notes, "+
        "pedal steel fills, twangy lead guitar and warm Hammond organ. "+
        "Storytelling verses, memorable lifting chorus, expressive guitar solo and proper final ending. "+
        "Natural timing, dynamic human performance, clean stereo studio mix, no synthetic keyboard lead, no robotic vocals.";
    private static final String DEMO_LYRICS =
        "[Intro]\n[Instrumental guitar and steel]\n\n"+
        "[Verse 1]\nI took the long way out of town\nPast the gates and broken ground\n"+
        "The morning radio was low\nStill another hundred miles to go\n"+
        "I knew each turn across that plain\nEach cattle grid and stretch of rain\n"+
        "But every road was different now\nWith all the years behind me somehow\n\n"+
        "[Chorus]\nBring the old road home to me\nWhere the hills meet the open fields\n"+
        "Let the wheels roll steady on\nTill the last of the daylight's gone\n"+
        "If there's one thing I still know\nIt's where my heart will always go\n\n"+
        "[Verse 2]\nThe sun climbed over dusty trees\nA little warmth came through the breeze\n"+
        "I passed the shop beside the bend\nWhere I once stopped to see a friend\n"+
        "Some places change and some hold true\nAnd I still see the town I knew\n"+
        "With every mile across that land\nI feel the wheel beneath my hands\n\n"+
        "[Chorus]\nBring the old road home to me\nWhere the hills meet the open fields\n"+
        "Let the wheels roll steady on\nTill the last of the daylight's gone\n"+
        "If there's one thing I still know\nIt's where my heart will always go\n\n"+
        "[Instrumental Break]\n[Electric guitar solo]\n\n"+
        "[Bridge]\nThe years don't turn the clock around\nBut there's still peace in familiar ground\n"+
        "And when I see that welcome sign\nI know the journey's worth the time\n\n"+
        "[Final Chorus]\nBring the old road home to me\nWhere the hills meet the open fields\n"+
        "Let the wheels roll steady on\nTill the last of the daylight's gone\n"+
        "If there's one thing I still know\nIt's where my heart will always go\n\n"+
        "[Outro]\n[Pedal steel and acoustic guitar resolve]";

    @Override public void onCreate(Bundle state){
        super.onCreate(state);buildUI();
    }
    private int dp(int a){return Math.round(a*getResources().getDisplayMetrics().density);}
    private TextView label(LinearLayout layout,String msg){
        TextView t=new TextView(this);t.setText(msg);t.setTextColor(0xffb6d9d5);t.setTextSize(13);
        t.setPadding(0,dp(12),0,dp(5));layout.addView(t);return t;
    }
    private EditText field(LinearLayout layout,String value,int minLines){
        EditText input=new EditText(this);
        input.setText(value);input.setTextColor(Color.WHITE);input.setHintTextColor(0xff7e9998);
        input.setTextSize(14);input.setSelectAllOnFocus(minLines==1);
        input.setMinLines(minLines);input.setGravity(Gravity.TOP);
        input.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff65d2bd));
        layout.addView(input,new LinearLayout.LayoutParams(-1,-2));
        return input;
    }
    private Button button(LinearLayout layout,String value){
        Button b=new Button(this);b.setText(value);b.setAllCaps(false);
        b.setTextColor(0xff101a1c);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff65d2bd));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(51));p.topMargin=dp(13);
        layout.addView(b,p);return b;
    }
    private Spinner options(LinearLayout layout,String[] values){
        Spinner s=new Spinner(this);
        s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values));
        layout.addView(s,new LinearLayout.LayoutParams(-1,dp(52)));return s;
    }
    private void buildUI(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(0xff10191b);
        LinearLayout content=new LinearLayout(this);content.setPadding(dp(20),dp(24),dp(20),dp(40));
        content.setOrientation(LinearLayout.VERTICAL);scroll.addView(content);setContentView(scroll);
        TextView heading=label(content,"DENNIS   /   AI MUSIC CLOUD");
        heading.setTextSize(23);heading.setTextColor(Color.WHITE);
        heading.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        label(content,"v0.3 EXPERIMENTAL • ACE-STEP XL • PHONE CONTROLS / CLOUD GENERATES");
        label(content,"This app needs your own securely hosted ACE-Step API. No GPU is included and generation may incur cloud fees.");
        label(content,"HTTPS API HOST");
        endpoint=field(content,getPreferences(MODE_PRIVATE).getString("endpoint",""),1);
        endpoint.setHint("https://your-gpu-api.example");
        label(content,"API KEY (not saved)");
        secret=field(content,"",1);
        secret.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        label(content,"MODEL (must be installed on GPU)");
        model=options(content,new String[]{"acestep-v15-xl-turbo","acestep-v15-xl-sft","acestep-v15-turbo"});
        label(content,"DURATION");
        duration=options(content,new String[]{"3 minutes","4 minutes","5 minutes"});
        label(content,"TEMPO (BPM)");
        bpm=field(content,"104",1);bpm.setInputType(InputType.TYPE_CLASS_NUMBER);
        label(content,"PRODUCTION DESCRIPTION");
        description=field(content,DEMO_PROMPT,4);
        label(content,"ORIGINAL LYRICS");
        lyrics=field(content,DEMO_LYRICS,10);
        test=button(content,"TEST SECURE GPU CONNECTION");
        generate=button(content,"GENERATE COMPLETE VOCAL SONG");
        play=button(content,"PLAY CLOUD SONG");
        save=button(content,"SAVE WAV TO PHONE");
        play.setEnabled(false);save.setEnabled(false);
        status=label(content,"No GPU connected. Test the endpoint before attempting a generation.");
        test.setOnClickListener(v->testConnection());
        generate.setOnClickListener(v->startGeneration());
        play.setOnClickListener(v->play());
        save.setOnClickListener(v->save());
    }
    private void setWorking(boolean working){
        test.setEnabled(!working);generate.setEnabled(!working);
        play.setEnabled(!working&&generated!=null);save.setEnabled(!working&&generated!=null);
    }
    private AceStepClient connect(){
        String url=endpoint.getText().toString().trim();
        AceStepClient result=new AceStepClient(url,secret.getText().toString());
        getPreferences(MODE_PRIVATE).edit().putString("endpoint",url).apply();
        return result;
    }
    private void notice(String value){runOnUiThread(()->status.setText(value));}
    private void testConnection(){
        final AceStepClient api;
        try{api=connect();}catch(Exception e){notice(e.getMessage());return;}
        setWorking(true);notice("Checking server health…");
        worker.execute(()->{
            try{
                org.json.JSONObject info=api.health();
                notice("GPU API responded: "+info.optString("status","response received")+
                    ". This checks connectivity only, not model load or paid GPU availability.");
            }catch(Exception e){notice("Connection failed: "+e.getMessage());}
            runOnUiThread(()->setWorking(false));
        });
    }
    private void startGeneration(){
        final AceStepClient api;
        final String p=description.getText().toString(),l=lyrics.getText().toString();
        final String choice=model.getSelectedItem().toString();
        final int length=(duration.getSelectedItemPosition()+3)*60;
        final int tempo;
        try{api=connect();tempo=Integer.parseInt(bpm.getText().toString().trim());}
        catch(Exception e){notice("Check HTTPS endpoint and BPM: "+e.getMessage());return;}
        try{AceStepClient.makeRequest(p,l,choice,tempo,length);}
        catch(Exception e){notice("Invalid generation settings: "+e.getMessage());return;}
        new AlertDialog.Builder(this)
            .setTitle("Start cloud generation?")
            .setMessage("This sends your production description and lyrics to the configured GPU server. Cloud processing may cost money. Your private training songs are NOT uploaded by this app.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Generate",(d,which)->generate(api,p,l,choice,tempo,length))
            .show();
    }
    private void generate(AceStepClient api,String p,String l,String choice,int tempo,int length){
        cancel=false;
        setWorking(true);notice("Submitting "+length/60+"-minute music request…");
        worker.execute(()->{
            try{
                String id=api.submit(p,l,choice,tempo,length);
                notice("ACE-Step task "+id+" queued. Checking progress…");
                for(int attempt=0;attempt<240&&!cancel;attempt++){
                    if(attempt>0)Thread.sleep(5000);
                    org.json.JSONObject task=api.poll(id);
                    int state=task.optInt("status",0);
                    if(state==2)throw new IOException("ACE-Step generation failed: "+task.optString("error","see GPU server logs"));
                    if(state==1){
                        String path=AceStepClient.successFile(task);
                        notice("Downloading generated vocal WAV…");
                        File output=new File(getFilesDir(),"latest_ace_step_cloud.wav");
                        api.download(path,output);
                        generated=output;
                        notice("Cloud song downloaded. Press PLAY or SAVE WAV. Model: "+choice);
                        runOnUiThread(()->setWorking(false));return;
                    }
                    if(attempt%3==0)notice("GPU generation queued/running. Task "+id+" (check "+(attempt+1)+")");
                }
                throw new IOException("Polling ended before completion; check GPU job queue");
            }catch(InterruptedException e){Thread.currentThread().interrupt();notice("Generation interrupted");}
            catch(Exception e){notice("Generation failed: "+e.getMessage());}
            runOnUiThread(()->setWorking(false));
        });
    }
    private void play(){
        if(generated==null)return;
        if(player!=null){try{player.stop();}catch(Exception ignored){}player.release();player=null;play.setText("PLAY CLOUD SONG");return;}
        try{
            player=new MediaPlayer();player.setDataSource(generated.getAbsolutePath());
            player.setOnCompletionListener(mp->{
                if(player!=null){player.release();player=null;}
                play.setText("PLAY CLOUD SONG");
            });
            player.prepare();player.start();play.setText("STOP PLAYBACK");
        }catch(Exception e){notice("Playback error: "+e.getMessage());}
    }
    private void save(){
        if(generated==null)return;
        Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        save.addCategory(Intent.CATEGORY_OPENABLE);save.setType("audio/wav");
        save.putExtra(Intent.EXTRA_TITLE,"Dennis_Cloud_ACE_Step.wav");
        startActivityForResult(save,SAVE);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=SAVE||result!=RESULT_OK||data==null||generated==null)return;
        Uri uri=data.getData();if(uri==null)return;
        setWorking(true);
        worker.execute(()->{
            try(InputStream in=new FileInputStream(generated);OutputStream out=getContentResolver().openOutputStream(uri,"w")){
                if(out==null)throw new IOException("Cannot write selected file");
                byte[] buffer=new byte[65536];int count;
                while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
                notice("Cloud WAV saved successfully.");
            }catch(Exception e){notice("Save failed: "+e.getMessage());}
            runOnUiThread(()->setWorking(false));
        });
    }
    @Override protected void onDestroy(){
        cancel=true;worker.shutdownNow();
        if(player!=null){player.release();player=null;}
        super.onDestroy();
    }
}
