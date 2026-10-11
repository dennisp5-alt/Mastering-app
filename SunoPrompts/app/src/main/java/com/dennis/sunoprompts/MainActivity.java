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
    private ChatGptConnection chat;
    private final ExecutorService tasks=Executors.newSingleThreadExecutor();
    private String location="home", activeArtist="";
    private long activeId=0;
    private boolean working=false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        store=new PromptStore(this);
        chat=new ChatGptConnection(this);
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
        TextView settings=action(chat.canUsePlan()?"✓ ChatGPT plan connected":"Connect ChatGPT plan",false);
        controls.addView(settings,new LinearLayout.LayoutParams(-1,-2));
        settings.setOnClickListener(v->showAccount());
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
        body.addView(text("v0.3 • Free offline reference prompts plus optional ChatGPT plan generation. No separate API key or token purchase required.",12,FAINT,false));
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
        TextView generateAi=action(chat.canUsePlan()?"Generate with ChatGPT plan":"Connect ChatGPT for detailed prompts",true);
        form.addView(generateAi);spacing(form,12);
        TextView generate=action("Use FREE offline/reference generator",false);
        form.addView(generate);
        TextView help=text("ChatGPT generation uses your existing eligible plan allowance; no separate OpenAI API credits. Sign in and approve plan usage first. Free local reference profiles remain available when offline or at your usage limit. Both modes save to the same artist folders.",13,SOFT,false);
        spacing(form,15);form.addView(help);
        generateAi.setOnClickListener(v->{
            if(!chat.canUsePlan()){showAccount();return;}
            String a=value(artist),s=value(song),n=value(notes);
            if(a.isEmpty()||s.isEmpty()){toast("Enter an artist and song.");return;}
            generateWithChatGpt(a,s,n,0);
        });
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
        body.addView(text("The app researches song metadata before asking ChatGPT to write a distinct production brief. No source recording is analysed, so the output is a reasoned interpretation rather than a verified transcription.",13,SOFT,false));
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
        TextView improve=action(chat.canUsePlan()?"Regenerate with ChatGPT plan":"Connect ChatGPT for a stronger prompt",true);
        body.addView(improve);
        improve.setOnClickListener(v->{
            if(!chat.canUsePlan()){showAccount();return;}
            new AlertDialog.Builder(this).setTitle("Replace with ChatGPT prompt?")
                .setMessage("A new researched prompt will replace the saved style, Exclude and notes for this song. Copy any edits you want to keep.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Generate",(dialog,which)->generateWithChatGpt(p.artist,p.song,"",p.id)).show();
        });
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

    private void generateWithChatGpt(String artist,String song,String notes,long replacementId) {
        if(working){toast("Another operation is active.");return;}
        working=true;
        new AlertDialog.Builder(this)
            .setTitle("ChatGPT plan generation")
            .setMessage("Researching "+song+" by "+artist+". Only artist, song, creative notes and public research will be sent to OpenAI. If the request fails, your current prompt remains unchanged.")
            .setPositiveButton("OK",null).show();
        tasks.execute(()->{
            try{
                // Authenticate and select from the account's model catalog.
                String token=chat.accessToken();
                java.util.ArrayList<ChatGptPrompts.Model> models=ChatGptPrompts.models(token);
                String preferred=getSharedPreferences("suno_settings",MODE_PRIVATE).getString("chat_model","");
                String selected=models.get(0).id;
                for(ChatGptPrompts.Model m:models)if(m.id.equals(preferred)){selected=m.id;break;}
                ResearchClient.Research research=ResearchClient.lookup(artist,song);
                PromptStore.Prompt generated=ChatGptPrompts.generate(token,selected,artist,song,notes,research);
                if(replacementId!=0)generated.id=replacementId;
                long id=store.save(generated);
                runOnUiThread(()->{working=false;showDetail(id);});
            }catch(Exception e){
                String message=e.getMessage()==null?"Unknown ChatGPT request error":e.getMessage();
                runOnUiThread(()->{
                    working=false;
                    new AlertDialog.Builder(this).setTitle("Prompt was not changed")
                        .setMessage(message)
                        .setPositiveButton("OK",null).show();
                });
            }
        });
    }

    private void showAccount() {
        location="account";
        base("ChatGPT Plan","OFFICIAL SIGN IN • NO API KEY","Library",this::showHome);
        LinearLayout card=panel(body);
        card.addView(text(chat.connected()?"ACCOUNT CONNECTED":"USE YOUR CHATGPT PLAN",16,WHITE,true));
        spacing(card,10);
        card.addView(text(chat.canUsePlan()?
            "ChatGPT plan usage authorised for "+chat.account()+
            ". Requests count towards your existing ChatGPT plan limits.":
            "Connect through OpenAI's official browser sign-in. Grant ChatGPT plan usage to create specific production prompts without purchasing API credits.",14,SOFT,false));
        spacing(card,16);
        TextView login=action(chat.canUsePlan()?"Reconnect ChatGPT account":"Continue with ChatGPT",true);
        card.addView(login);
        login.setOnClickListener(v->{
            if(working)return;
            working=true;
            login.setText("Complete sign-in in your browser…");login.setEnabled(false);
            tasks.execute(()->chat.signIn(this,new ChatGptConnection.Callback(){
                public void done(String account){
                    runOnUiThread(()->{
                        working=false;
                        showAccount();
                        new AlertDialog.Builder(MainActivity.this)
                            .setTitle("ChatGPT plan enabled")
                            .setMessage("Connected: "+account+". Eligible generation will use your ChatGPT plan allowance. You can manage limits in ChatGPT Settings.")
                            .setPositiveButton("OK",null).show();
                    });
                }
                public void fail(String message){
                    runOnUiThread(()->{
                        working=false;
                        showAccount();
                        new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Connection unsuccessful")
                            .setMessage(message)
                            .setPositiveButton("OK",null).show();
                    });
                }
            }));
        });
        spacing(body,10);
        TextView usage=action("View ChatGPT usage settings",false);
        body.addView(usage);
        usage.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW,
            Uri.parse("https://chatgpt.com/#settings/Usage"))));
        spacing(body,12);
        TextView diagnose=action("Test OpenAI connection",false);
        body.addView(diagnose);
        diagnose.setOnClickListener(v->{
            if(working)return;
            working=true;diagnose.setText("Checking network and secure connection…");diagnose.setEnabled(false);
            tasks.execute(()->{
                String findings=NetworkHealth.test(getApplicationContext());
                runOnUiThread(()->{
                    working=false;
                    diagnose.setEnabled(true);diagnose.setText("Test OpenAI connection");
                    new AlertDialog.Builder(this).setTitle("Network diagnostic")
                        .setMessage(findings).setPositiveButton("OK",null).show();
                });
            });
        });
        if(chat.canUsePlan()){
            spacing(body,12);
            TextView model=action("Choose available AI model",false);
            body.addView(model);
            model.setOnClickListener(v->{
                if(working)return;
                working=true;model.setText("Loading your model catalog…");
                tasks.execute(()->{
                    try{
                        java.util.ArrayList<ChatGptPrompts.Model> models=ChatGptPrompts.models(chat.accessToken());
                        String[] labels=new String[models.size()];
                        for(int i=0;i<models.size();i++)labels[i]=models.get(i).label;
                        runOnUiThread(()->{
                            working=false;model.setText("Choose available AI model");
                            new AlertDialog.Builder(this).setTitle("ChatGPT models")
                                .setItems(labels,(dialog,which)->{
                                    getSharedPreferences("suno_settings",MODE_PRIVATE).edit()
                                        .putString("chat_model",models.get(which).id).apply();
                                    toast("Selected "+models.get(which).label);
                                }).setNegativeButton("Cancel",null).show();
                        });
                    }catch(Exception e){runOnUiThread(()->{
                        working=false;model.setText("Choose available AI model");
                        toast("Model catalog unavailable: "+e.getMessage());
                    });}
                });
            });
            spacing(body,12);
            TextView signOut=action("Disconnect on this device",false);
            body.addView(signOut);
            signOut.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle("Disconnect ChatGPT?")
                .setMessage("This clears credentials stored on your phone. To also revoke the connection remotely, open ChatGPT Settings. Your saved prompts are not deleted.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Disconnect",(dialog,which)->{chat.disconnect();showAccount();}).show());
        }
        spacing(body,13);
        body.addView(text("This connection shares no ChatGPT chat history. Requests use the official Responses API, not unofficial website endpoints. If sign-in or plan permission fails, use the free generator instead.",13,SOFT,false));
    }

    private void copy(String txt,String message){
        ((ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Suno Prompt",txt));
        toast(message);
    }

    @Override public void onBackPressed() {
        if("detail".equals(location)){showArtist(activeArtist);}
        else if("artist".equals(location)||"create".equals(location)||"account".equals(location)){showHome();}
        else super.onBackPressed();
    }
    @Override protected void onDestroy(){
        chat.stopListener();tasks.shutdownNow();store.close();super.onDestroy();
    }
}
