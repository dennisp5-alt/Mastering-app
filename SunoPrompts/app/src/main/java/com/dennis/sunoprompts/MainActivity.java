package com.dennis.sunoprompts;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int BG=0xff0C1120, PANEL=0xff1B2335, FIELD=0xff242E43;
    private static final int WHITE=0xffF2F3F8, SOFT=0xffAEB7CB, FAINT=0xff7F8DA9;
    private static final int LILAC=0xffBAA2FF, MINT=0xff54DFD0, OUTLINE=0xff39415A;
    private LinearLayout root, body;
    private PromptStore store;
    private final ExecutorService tasks=Executors.newSingleThreadExecutor();
    private String location="home", activeArtist="";
    private long activeId=0;
    private boolean working=false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        store=new PromptStore(this);
        // v0.2 no-credit mode: discard any legacy API token; no paid AI requests are made.
        getSharedPreferences("suno_settings",MODE_PRIVATE).edit().remove("api_key").apply();
        showHome();
    }

    private int px(float dp) {return Math.round(dp*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color,int radius,int border) {
        GradientDrawable g=new GradientDrawable();
        g.setColor(color);g.setCornerRadius(px(radius));
        if(border!=0)g.setStroke(px(1),border);
        return g;
    }
    private LinearLayout vertical() {
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;
    }
    private LinearLayout horizontal() {
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;
    }
    private void spacing(LinearLayout parent,int amount) {
        View v=new View(this);parent.addView(v,new LinearLayout.LayoutParams(1,px(amount)));
    }
    private TextView text(String value,int sp,int color,boolean bold) {
        TextView t=new TextView(this);t.setText(value);t.setTextSize(sp);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setIncludeFontPadding(true);return t;
    }
    private LinearLayout panel(LinearLayout into) {
        LinearLayout c=vertical();
        c.setPadding(px(16),px(16),px(16),px(16));
        c.setBackground(shape(PANEL,18,OUTLINE));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=px(12);
        into.addView(c,p);return c;
    }
    private TextView action(String label,boolean primary) {
        TextView b=text(label,15,primary?BG:WHITE,true);
        b.setGravity(Gravity.CENTER);b.setMinHeight(px(50));
        b.setPadding(px(14),px(10),px(14),px(10));
        b.setBackground(shape(primary?MINT:FIELD,13,primary?0:OUTLINE));
        b.setClickable(true);b.setFocusable(true);
        return b;
    }
    private EditText input(String hint,String initial,boolean multiline) {
        EditText e=new EditText(this);
        e.setTextColor(WHITE);e.setHintTextColor(FAINT);e.setTextSize(16);
        e.setHint(hint);e.setText(initial==null?"":initial);
        e.setBackground(shape(FIELD,13,OUTLINE));
        e.setPadding(px(14),px(13),px(14),px(13));
        if(multiline) {
            e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            e.setMinLines(3);e.setGravity(Gravity.TOP|Gravity.START);
        } else e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        return e;
    }
    private void field(LinearLayout parent,String label,EditText input) {
        TextView lbl=text(label,13,SOFT,true);parent.addView(lbl);spacing(parent,7);
        parent.addView(input,new LinearLayout.LayoutParams(-1,-2));spacing(parent,16);
    }
    private void base(String title,String subtitle,String returnLabel,Runnable back) {
        root=vertical();root.setBackgroundColor(BG);
        setContentView(root);
        LinearLayout head=vertical();head.setPadding(px(20),px(18),px(20),px(14));
        root.addView(head);
        if(back!=null) {
            TextView backView=text("‹  "+returnLabel,15,LILAC,true);
            backView.setPadding(0,px(3),0,px(12));backView.setOnClickListener(v->back.run());
            head.addView(backView);
        }
        LinearLayout titleRow=horizontal();
        TextView icon=text("♫",30,LILAC,true);titleRow.addView(icon);
        TextView heading=text("  "+title,24,WHITE,true);titleRow.addView(heading);
        head.addView(titleRow);
        if(subtitle!=null&&!subtitle.isEmpty()) {
            TextView sub=text(subtitle,13,SOFT,false);
            sub.setPadding(0,px(6),0,0);head.addView(sub);
        }
        View line=new View(this);line.setBackgroundColor(OUTLINE);
        root.addView(line,new LinearLayout.LayoutParams(-1,px(1)));
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);scroll.setClipToPadding(false);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        body=vertical();body.setPadding(px(18),px(20),px(18),px(24));
        scroll.addView(body);
    }
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private String value(EditText e) {return e.getText().toString().trim();}

    private void showHome() {
        location="home";activeArtist="";activeId=0;
        Map<String,Integer> artists=store.artists();
        int total=0;for(Integer n:artists.values())total+=n;
        base("Suno Prompts","RESEARCH  •  CREATE  •  ORGANISE",null,null);
        LinearLayout welcome=panel(body);
        welcome.addView(text("YOUR PRODUCTION LIBRARY",12,LILAC,true));
        spacing(welcome,8);
        welcome.addView(text(total+" saved song prompts",27,WHITE,true));
        spacing(welcome,6);
        welcome.addView(text("Research recordings, build original Suno v6 production prompts and keep every song under its artist.",14,SOFT,false));
        spacing(welcome,14);
        TextView create=action("+  New song prompt",true);
        welcome.addView(create);create.setOnClickListener(v->showCreate(""));
        spacing(body,7);
        LinearLayout controls=horizontal();
        TextView settings=action("Free mode • No API credits",false);controls.addView(settings,new LinearLayout.LayoutParams(-1,-2));
        settings.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("No paid AI needed").setMessage("The app generates prompts from curated recording and artist production profiles. MusicBrainz and Wikipedia provide optional free metadata research. Songs without a reviewed production profile are marked clearly; the app never claims to have heard the recording.").setPositiveButton("OK",null).show());
        body.addView(controls);
        spacing(body,23);
        body.addView(text("ARTIST FOLDERS",13,SOFT,true));spacing(body,12);
        if(artists.isEmpty()){
            LinearLayout empty=panel(body);
            empty.addView(text("No folders yet",18,WHITE,true));spacing(empty,6);
            empty.addView(text("Start with AC/DC and Back in Black, or choose any artist and song. Folders are created automatically.",14,SOFT,false));
        }
        for(Map.Entry<String,Integer> entry:artists.entrySet()){
            String name=entry.getKey();
            LinearLayout card=panel(body);
            LinearLayout row=horizontal();card.addView(row);
            TextView folder=text("▣",24,LILAC,true);row.addView(folder);
            LinearLayout info=vertical();info.setPadding(px(12),0,0,0);
            row.addView(info,new LinearLayout.LayoutParams(0,-2,1));
            info.addView(text(name,17,WHITE,true));
            info.addView(text(entry.getValue()+" song "+(entry.getValue()==1?"prompt":"prompts"),13,SOFT,false));
            row.addView(text("›",28,LILAC,true));
            card.setOnClickListener(v->showArtist(name));
            card.setClickable(true);
        }
        spacing(body,14);
        body.addView(text("v0.2 • Free to generate. No key or credits. Curated song and artist guides work offline; optional metadata research uses the internet.",12,FAINT,false));
    }

    private void showArtist(String artist){
        location="artist";activeArtist=artist;activeId=0;
        base(artist,"ARTIST FOLDER","Library",this::showHome);
        TextView create=action("+  Research another song",true);
        body.addView(create);create.setOnClickListener(v->showCreate(artist));
        spacing(body,18);
        for(PromptStore.Prompt p:store.byArtist(artist)){
            LinearLayout card=panel(body);
            card.addView(text(p.song,18,WHITE,true));spacing(card,4);
            card.addView(text(p.mode+"   •   Tap to open",12,SOFT,false));
            card.setOnClickListener(v->showDetail(p.id));
            card.setClickable(true);
        }
    }

    private void showCreate(String preset){
        location="create";activeArtist=preset;
        base("New prompt","SONG-SPECIFIC PRODUCTION RESEARCH","Library",this::showHome);
        LinearLayout form=panel(body);
        form.addView(text("RESEARCH A SONG",12,LILAC,true));spacing(form,15);
        EditText artist=input("e.g. AC/DC",preset,false);
        EditText song=input("e.g. Back in Black","",false);
        EditText notes=input("Optional: warmer mix, detailed stereo, era, vocal direction…","",true);
        field(form,"Artist",artist);field(form,"Song title",song);
        field(form,"Your production direction (optional)",notes);
        TextView generate=action("Create FREE production prompt",true);
        form.addView(generate);
        TextView help=text("No API key, credits or subscription. Curated song profiles and artist-specific production directions are generated locally. Other artists may use free MusicBrainz/Wikipedia research, which cannot analyse the audio itself. Unprofiled songs are clearly labelled.",13,SOFT,false);
        spacing(form,15);form.addView(help);
        generate.setOnClickListener(v->{
            String a=value(artist),s=value(song),n=value(notes);
            if(a.isEmpty()||s.isEmpty()){toast("Enter both an artist and a song title.");return;}
            if(working)return;
            working=true;generate.setText("Researching sources…");
            generate.setEnabled(false);
            tasks.execute(()->{
                ResearchClient.Research research;
                boolean locallyKnown=SongProfiles.find(a,s)!=null || PromptTemplates.artistGuide(a)!=null;
                if(locallyKnown)research=new ResearchClient.Research();
                else research=ResearchClient.lookup(a,s);
                PromptStore.Prompt generated=PromptTemplates.starter(a,s,n,research);
                long id=store.save(generated);
                runOnUiThread(()->{
                    working=false;
                    showDetail(id);
                });
            });
        });
        spacing(body,12);
        body.addView(text("Curated songs are ready instantly, even without internet. Unrecognised artists need internet access and may require your own production notes.",13,SOFT,false));
    }

    private void showDetail(long id){
        PromptStore.Prompt p=store.get(id);
        if(p==null){showHome();return;}
        location="detail";activeId=id;activeArtist=p.artist;
        base(p.song,p.artist+"   •   "+p.mode,p.artist,()->showArtist(p.artist));
        LinearLayout main=panel(body);
        main.addView(text("STYLE PROMPT",12,LILAC,true));spacing(main,8);
        EditText style=input("Production style prompt",p.style,true);
        style.setMinLines(7);main.addView(style);spacing(main,7);
        TextView counter=text(p.style.length()+" / 1000 characters",12,SOFT,false);
        main.addView(counter);
        style.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){counter.setText(s.length()+" / 1000 characters");counter.setTextColor(s.length()>1000?0xffffab90:SOFT);}
            public void afterTextChanged(android.text.Editable e){}
        });
        spacing(main,13);
        TextView copy=action("Copy style prompt",true);main.addView(copy);
        copy.setOnClickListener(v->copy(value(style),"Style prompt copied"));
        spacing(main,19);
        main.addView(text("EXCLUDE PROMPT",12,LILAC,true));spacing(main,8);
        EditText exclude=input("Exclude",p.exclude,true);main.addView(exclude);
        spacing(main,11);
        TextView copyExclude=action("Copy exclusions",false);main.addView(copyExclude);
        copyExclude.setOnClickListener(v->copy(value(exclude),"Exclude prompt copied"));
        spacing(main,17);
        TextView save=action("Save edits",true);main.addView(save);
        save.setOnClickListener(v->{
            if(value(style).isEmpty()){toast("Style prompt cannot be empty.");return;}
            if(value(style).length()>1000){toast("Style prompt must be 1000 characters or fewer.");return;}
            p.style=value(style);p.exclude=value(exclude);
            p.mode=p.mode.contains("EDITED")?p.mode:p.mode+" / EDITED";
            store.save(p);toast("Saved to "+p.artist+" folder");
        });
        LinearLayout research=panel(body);
        research.addView(text("RESEARCH & PRODUCTION NOTES",12,LILAC,true));spacing(research,9);
        research.addView(text(p.analysis.isEmpty()?"No notes.":p.analysis,14,SOFT,false));
        if(!p.sources.trim().isEmpty()){
            spacing(research,15);research.addView(text("SOURCE LINKS",12,LILAC,true));spacing(research,6);
            for(String line:p.sources.split("\n")){
                if(line.trim().isEmpty())continue;
                TextView link=text(line,13,MINT,false);
                link.setPadding(0,px(5),0,px(5));
                research.addView(link);
                String url=line.substring(Math.max(0,line.indexOf("https://")));
                if(url.startsWith("https://"))link.setOnClickListener(v->{
                    try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){toast("Cannot open source.");}
                });
            }
        }
        spacing(body,12);
        TextView refresh=action("Replace with latest FREE production prompt",false);
        body.addView(refresh);
        refresh.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle("Replace saved production prompt?")
            .setMessage("Rebuild using the free song and artist reference library. Your current edits to this song will be replaced.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Replace",(dialog,which)->{
                if(working)return;
                working=true;refresh.setEnabled(false);refresh.setText("Rebuilding…");
                tasks.execute(()->{
                    ResearchClient.Research rr=(SongProfiles.find(p.artist,p.song)!=null ||
                        PromptTemplates.artistGuide(p.artist)!=null)?
                        new ResearchClient.Research():ResearchClient.lookup(p.artist,p.song);
                    PromptStore.Prompt newPrompt=PromptTemplates.starter(p.artist,p.song,"",rr);
                    newPrompt.id=p.id;
                    store.save(newPrompt);
                    runOnUiThread(()->{working=false;showDetail(p.id);});
                });
            }).show());
        spacing(body,12);
        TextView share=action("Share prompt text",false);body.addView(share);
        share.setOnClickListener(v->{
            Intent intent=new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT,p.artist+" — "+p.song+"\n\nSTYLE\n"+value(style)+"\n\nEXCLUDE\n"+value(exclude));
            startActivity(Intent.createChooser(intent,"Share Suno prompt"));
        });
        spacing(body,13);
        TextView delete=action("Delete this song prompt",false);body.addView(delete);
        delete.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Delete prompt?")
                .setMessage("Remove "+p.song+" from "+p.artist+"? This cannot be undone.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Delete",(dialog,which)->{store.remove(p.id);showArtist(p.artist);}).show());
    }

    private void copy(String txt,String message){
        ((ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Suno Prompt",txt));
        toast(message);
    }

    @Override public void onBackPressed() {
        if("detail".equals(location)){showArtist(activeArtist);}
        else if("artist".equals(location)||"create".equals(location)){showHome();}
        else super.onBackPressed();
    }
    @Override protected void onDestroy(){
        tasks.shutdownNow();store.close();super.onDestroy();
    }
}
