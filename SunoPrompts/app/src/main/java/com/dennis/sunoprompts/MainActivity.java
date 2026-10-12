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
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int BG=0xff0C1120, PANEL=0xff1B2335, FIELD=0xff242E43;
    private static final int REQUEST_LIBRARY_EXPORT=9071,REQUEST_LIBRARY_IMPORT=9072;
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
        receiveSharedText(getIntent());
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        final Uri uri=data.getData();
        if(requestCode==REQUEST_LIBRARY_EXPORT){
            if(working){toast("Another operation is active.");return;}
            working=true;
            tasks.execute(()->{
                try{
                    String json=LibraryBackup.export(store).toString(2);
                    try(java.io.OutputStream out=getContentResolver().openOutputStream(uri,"w")){
                        if(out==null)throw new Exception("Could not open destination file");
                        out.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        out.flush();
                    }
                    runOnUiThread(()->{
                        working=false;
                        new AlertDialog.Builder(this).setTitle("Library backup saved")
                            .setMessage("Saved your production prompts and lyrics to the location you selected. Keep this JSON file before uninstalling or replacing the app. No ChatGPT login credentials were exported.")
                            .setPositiveButton("OK",null).show();
                    });
                }catch(Exception ex){
                    runOnUiThread(()->{
                        working=false;
                        new AlertDialog.Builder(this).setTitle("Backup not completed")
                            .setMessage(ex.getMessage()==null?"Could not write file.":ex.getMessage())
                            .setPositiveButton("OK",null).show();
                    });
                }
            });
        }else if(requestCode==REQUEST_LIBRARY_IMPORT){
            if(working){toast("Another operation is active.");return;}
            working=true;
            tasks.execute(()->{
                try{
                    byte[] bytes;
                    try(java.io.InputStream in=getContentResolver().openInputStream(uri);
                        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
                        if(in==null)throw new Exception("Could not read selected file");
                        byte[] buffer=new byte[8192];int n;
                        while((n=in.read(buffer))!=-1){
                            out.write(buffer,0,n);
                            if(out.size()>LibraryBackup.MAX_BYTES)throw new Exception("Backup file exceeds 12 MB limit");
                        }
                        bytes=out.toByteArray();
                    }
                    LibraryBackup.Preview preview=LibraryBackup.inspect(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
                    runOnUiThread(()->{
                        working=false;
                        new AlertDialog.Builder(this).setTitle("Restore Suno Prompts library?")
                            .setMessage(preview.text())
                            .setNegativeButton("Cancel",null)
                            .setPositiveButton("Import",(dialog,which)->{
                                if(working)return;
                                working=true;
                                tasks.execute(()->{
                                    try{
                                        String report=LibraryBackup.restore(store,preview);
                                        runOnUiThread(()->{
                                            working=false;showHome();
                                            new AlertDialog.Builder(this).setTitle("Restore complete")
                                                .setMessage(report).setPositiveButton("OK",null).show();
                                        });
                                    }catch(Exception e){runOnUiThread(()->{
                                        working=false;
                                        new AlertDialog.Builder(this).setTitle("Restore failed")
                                            .setMessage(e.getMessage()==null?"Import failed.":e.getMessage())
                                            .setPositiveButton("OK",null).show();
                                    });}
                                });
                            }).show();
                    });
                }catch(Exception ex){
                    runOnUiThread(()->{
                        working=false;
                        new AlertDialog.Builder(this).setTitle("Backup could not be opened")
                            .setMessage(ex.getMessage()==null?"Unsupported or damaged backup.":ex.getMessage())
                            .setPositiveButton("OK",null).show();
                    });
                }
            });
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        receiveSharedText(intent);
    }

    private void receiveSharedText(Intent intent) {
        if(intent==null || !Intent.ACTION_SEND.equals(intent.getAction()))return;
        if(intent.getType()==null || !intent.getType().startsWith("text/plain"))return;
        CharSequence text=intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        // Prevent import being repeated after rotation or home navigation.
        intent.removeExtra(Intent.EXTRA_TEXT);
        ShareImport imported=ShareImport.parse(text);
        if(imported.type==ShareImport.Type.UNKNOWN) {
            new AlertDialog.Builder(this).setTitle("Text cannot be imported")
                .setMessage("Use Share prompt text or Share lyrics in your OLD Suno Prompts app, then select Suno Prompts Studio in Android's Share menu.")
                .setPositiveButton("OK",null).show();
            return;
        }
        String name=imported.type==ShareImport.Type.STYLE?
            imported.artist+" / "+imported.title:imported.title;
        new AlertDialog.Builder(this)
            .setTitle("Import shared "+(imported.type==ShareImport.Type.STYLE?"production prompt":"lyrics"))
            .setMessage("Import \""+name+"\" into Suno Prompts Studio? The original app is not changed.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Import",(dialog,which)->commitSharedImport(imported))
            .show();
    }
    private void commitSharedImport(ShareImport imported) {
        if(imported.type==ShareImport.Type.LYRICS) {
            PromptStore.Lyrics draft=new PromptStore.Lyrics();
            draft.artist="Imported Lyrics";draft.song=imported.title;
            draft.body=imported.body;draft.mode="IMPORTED FROM OLD APP";
            LyricsClient.Brief brief=new LyricsClient.Brief();
            brief.artist=draft.artist;brief.title=draft.song;
            brief.story="User-authored lyrics transferred from a previous installation";
            draft.brief=brief.toJson("Imported from Android Share");
            long id=store.saveLyrics(draft);
            showLyricsDetail(id);
            return;
        }
        if(imported.type!=ShareImport.Type.STYLE)return;
        PromptStore.Prompt current=store.findPrompt(imported.artist,imported.title);
        if(current!=null){
            new AlertDialog.Builder(this).setTitle("Prompt already exists")
                .setMessage("Suno Prompts Studio already has a saved production prompt with this artist and song. Overwrite it with the shared text?")
                .setNegativeButton("Keep existing",null)
                .setPositiveButton("Replace",(dialog,which)->saveSharedStyle(imported,current.id))
                .show();
            return;
        }
        saveSharedStyle(imported,0);
    }
    private void saveSharedStyle(ShareImport imported,long existingId) {
        PromptStore.Prompt p=new PromptStore.Prompt();
        p.id=existingId;p.artist=imported.artist;p.song=imported.title;
        p.style=imported.body;p.exclude=imported.exclude;
        p.analysis="Imported verbatim by the user from an older Suno Prompts installation. The original app remains unchanged. No web or AI analysis was used during transfer.";
        p.sources="";p.mode="USER IMPORT";
        long id=store.save(p);
        showDetail(id);
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
        welcome.addView(text(total+" saved studio items",27,WHITE,true));
        spacing(welcome,6);
        welcome.addView(text("Research recordings, build original Suno v6 production prompts and keep every song under its artist.",14,SOFT,false));
        spacing(welcome,14);
        TextView create=action("+  New song prompt",true);
        welcome.addView(create);create.setOnClickListener(v->showCreate(""));
        spacing(welcome,12);
        TextView lyricsHome=action("✍  AI Lyrics Studio",false);
        welcome.addView(lyricsHome);
        lyricsHome.setOnClickListener(v->showLyricsForm("",""));
        spacing(body,7);
        LinearLayout controls=horizontal();
        TextView settings=action(chat.canUsePlan()?"✓ ChatGPT plan connected":"Connect ChatGPT plan",false);
        controls.addView(settings,new LinearLayout.LayoutParams(-1,-2));
        settings.setOnClickListener(v->showAccount());
        body.addView(controls);
        spacing(body,12);
        TextView backup=action("Back up my full library",false);
        body.addView(backup);
        backup.setOnClickListener(v->{
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.setType("application/json");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_TITLE,"SunoPromptsStudio-Library.json");
            startActivityForResult(intent,REQUEST_LIBRARY_EXPORT);
        });
        spacing(body,12);
        TextView restore=action("Restore a saved library backup",false);
        body.addView(restore);
        restore.setOnClickListener(v->{
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent,REQUEST_LIBRARY_IMPORT);
        });
        spacing(body,8);
        body.addView(text("Moving from the older app? Open its saved song, choose Share prompt text (or Share lyrics) and select Suno Prompts Studio. The original installation stays untouched.",12,SOFT,false));
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
            info.addView(text(entry.getValue()+" saved "+(entry.getValue()==1?"item":"items"),13,SOFT,false));
            row.addView(text("›",28,LILAC,true));
            card.setOnClickListener(v->showArtist(name));
            card.setClickable(true);
        }
        spacing(body,14);
        body.addView(text("Suno Prompts Studio v0.5.3 • Automatic Suno style length repair, studio DNA references and quality checks.",12,FAINT,false));
    }

    private void showArtist(String artist){
        location="artist";activeArtist=artist;activeId=0;
        base(artist,"ARTIST FOLDER","Library",this::showHome);
        TextView create=action("+  Research another song",true);
        body.addView(create);create.setOnClickListener(v->showCreate(artist));
        spacing(body,10);
        TextView lyricsNew=action("+  Write original lyrics",false);
        body.addView(lyricsNew);lyricsNew.setOnClickListener(v->showLyricsForm(artist,""));
        spacing(body,18);
        java.util.ArrayList<PromptStore.Prompt> songPrompts=store.byArtist(artist);
        if(!songPrompts.isEmpty()){body.addView(text("PRODUCTION PROMPTS",12,LILAC,true));spacing(body,10);}
        for(PromptStore.Prompt p:songPrompts){
            LinearLayout card=panel(body);
            card.addView(text(p.song,18,WHITE,true));spacing(card,4);
            card.addView(text(p.mode+"   •   Tap to open",12,SOFT,false));
            card.setOnClickListener(v->showDetail(p.id));
            card.setClickable(true);
        }
        java.util.ArrayList<PromptStore.Lyrics> drafts=store.lyricsByArtist(artist);
        if(!drafts.isEmpty()){
            spacing(body,14);
            body.addView(text("LYRICS DRAFTS",12,LILAC,true));spacing(body,10);
        }
        for(PromptStore.Lyrics draft:drafts){
            LinearLayout card=panel(body);
            card.addView(text(draft.song,18,WHITE,true));spacing(card,4);
            card.addView(text(draft.mode+"   •   Tap to edit",12,SOFT,false));
            card.setOnClickListener(v->showLyricsDetail(draft.id));
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
        form.addView(text("Describe the musical sound you want. You do not need to write a character limit here; Studio automatically fits the final AI style within Suno’s 1,000-character limit.",12,SOFT,false));
        spacing(form,12);
        TextView generateAi=action(chat.canUsePlan()?"Generate Studio DNA with ChatGPT (2 requests)":"Connect ChatGPT for detailed prompts",true);
        form.addView(generateAi);spacing(form,12);
        TextView generate=action("Use FREE offline/reference generator",false);
        form.addView(generate);
        TextView help=text("Studio DNA AI uses TWO requests from your eligible ChatGPT plan: (1) music DNA and (2) playable Suno arrangement. Reviewed song references are included automatically where available; other songs use broader artist/metadata guidance. Free song references cost nothing. No original recording audio is analysed.",13,SOFT,false);
        spacing(form,15);form.addView(help);
        generateAi.setOnClickListener(v->{
            if(!chat.canUsePlan()){showAccount();return;}
            String a=value(artist),s=value(song),n=value(notes);
            if(a.isEmpty()||s.isEmpty()){toast("Enter an artist and song.");return;}
            PromptStore.Prompt previous=store.findPrompt(a,s);
            if(previous!=null){
                new AlertDialog.Builder(this).setTitle("Replace this saved song?")
                    .setMessage(a+" — "+s+" already has a production prompt. Creating another with the same artist/song will replace its style and notes. Make a library backup if you need the previous version.")
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Generate and replace",(dialog,which)->
                        generateWithChatGpt(a,s,n,previous.id)).show();
            }else generateWithChatGpt(a,s,n,0);
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
                .setMessage("This uses TWO ChatGPT plan requests and replaces this saved song's style, Exclude and notes only if both succeed and local quality checks pass. Back up or copy edits you want to keep.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Generate",(dialog,which)->generateWithChatGpt(p.artist,p.song,"",p.id)).show();
        });
        spacing(body,12);
        TextView lyricsForSound=action("✍  Write original lyrics for this sound",true);
        body.addView(lyricsForSound);
        lyricsForSound.setOnClickListener(v->showLyricsForm(p.artist,value(style)));
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

    /** Creates a fresh lyrics draft. The artist name is organisational/style context only. */
    private void showLyricsForm(String folderPreset,String productionStyle){
        location="lyrics-form";activeArtist=folderPreset;
        base("Lyrics Studio","ORIGINAL SONGWRITING • SUNO-READY","Library",this::showHome);
        LinearLayout card=panel(body);
        card.addView(text("SONGWRITER BRIEF",12,LILAC,true));spacing(card,12);
        EditText folder=input("Optional — e.g. AC/DC, Country, Original Songs",folderPreset,false);
        EditText song=input("New original song title","",false);
        EditText reference=input("Optional — reference recording name, e.g. The Wild Boys","",false);
        EditText story=input("Tell the story: people, setting, conflict, emotion, key details…","",true);
        EditText hook=input("Optional title hook or memorable chorus phrase","",true);
        EditText genre=input("Genre and mood, e.g. 1990s Australian country-rock, uplifting","",false);
        EditText voice=input("Male/female/duet, vocal register, accent and delivery","",false);
        EditText structure=input("Verse 1, Pre-Chorus, Chorus, Verse 2, Chorus, Bridge, Final Chorus, Outro",
                                 "Verse 1, Chorus, Verse 2, Chorus, Bridge, Final Chorus, Outro",true);
        EditText rhyme=input("Natural, conversational, singable line lengths, gentle end rhymes",
                             "Natural conversational rhymes, varied line length, no forced rhymes",true);
        EditText rhythm=input("Specific rhythmic delivery and hook attack",
            LyricRhythmCoach.suggest(productionStyle+" "+folderPreset),true);
        EditText avoid=input("Words or ideas to avoid","neon; empty clichés; meaningless filler",false);
        EditText production=input("Existing Suno style prompt or music production notes",productionStyle,true);
        production.setMinLines(4);
        field(card,"Artist reference / library folder (optional)",folder);
        field(card,"New song title",song);
        field(card,"Musical reference song (optional, never copy its words)",reference);
        field(card,"Story / scenario / subject (required)",story);
        field(card,"Chorus hook (optional)",hook);
        field(card,"Genre and emotional tone",genre);
        field(card,"Vocal delivery",voice);
        field(card,"Song structure",structure);
        field(card,"Rhyme and syllable pacing",rhyme);
        field(card,"Vocal rhythm, breath gaps and chorus attack",rhythm);
        field(card,"Avoid these words or themes",avoid);
        field(card,"Production style the lyrics must fit",production);
        TextView chooseStyle=action("Choose saved song's production DNA",false);
        card.addView(chooseStyle);
        chooseStyle.setOnClickListener(v->{
            String folderName=value(folder);
            if(folderName.isEmpty()){toast("Enter an artist/library folder first.");return;}
            java.util.ArrayList<PromptStore.Prompt> available=store.byArtist(folderName);
            if(available.isEmpty()){toast("This artist folder has no production prompts saved yet.");return;}
            String[] items=new String[available.size()];
            for(int i=0;i<available.size();i++)items[i]=available.get(i).song;
            new AlertDialog.Builder(this).setTitle("Use production style from")
                .setItems(items,(dialog,which)->{
                    PromptStore.Prompt chosen=available.get(which);
                    production.setText(chosen.style);
                    reference.setText(chosen.song);
                    rhythm.setText(LyricRhythmCoach.suggest(chosen.style));
                }).setNegativeButton("Cancel",null).show();
        });
        spacing(card,15);
        TextView generate=action(chat.canUsePlan()?"Generate original lyrics with ChatGPT":"Connect ChatGPT to generate lyrics",true);
        card.addView(generate);
        spacing(card,12);
        TextView both=action(chat.canUsePlan()?"Generate lyrics + matching Suno sound (2 AI requests)":"Connect ChatGPT to generate lyrics + sound",false);
        card.addView(both);
        spacing(card,12);
        TextView manual=action("Start a blank lyrics draft (offline)",false);
        card.addView(manual);
        TextView explanation=text(
            "Generates a new, editable draft each time. Earlier drafts and production prompts are preserved. " +
            "The source artist is a style reference only; the lyrics will be original. ChatGPT generation uses eligible plan allowance.",
            13,SOFT,false);
        spacing(card,14);card.addView(explanation);
        final java.util.function.Supplier<LyricsClient.Brief> brief=()->{
            LyricsClient.Brief b=new LyricsClient.Brief();
            b.artist=value(folder);b.title=value(song);b.story=value(story);
            b.hook=value(hook);b.genre=value(genre);b.vocal=value(voice);
            b.structure=value(structure);b.rhyme=value(rhyme);
            b.referenceTrack=value(reference);b.rhythm=value(rhythm);
            b.avoid=value(avoid);b.styleGuide=value(production);
            if(b.styleGuide.isEmpty() && !b.artist.isEmpty()){
                PromptStore.Prompt linked=store.findPrompt(b.artist,b.title);
                if(linked!=null)b.styleGuide=linked.style;
            }
            return b;
        };
        both.setOnClickListener(v->{
            if(!chat.canUsePlan()){showAccount();return;}
            LyricsClient.Brief b=brief.get();
            if(b.title.isEmpty()||b.story.isEmpty()){
                toast("Enter an original title and song story first.");return;
            }
            new AlertDialog.Builder(this).setTitle("Create lyrics and production?")
                .setMessage("This will use two ChatGPT plan requests: first the original lyrics, then a Suno production arrangement around the generated words. Both are saved separately.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Generate both",(dialog,which)->generateLyrics(b,true))
                .show();
        });
        generate.setOnClickListener(v->{
            if(!chat.canUsePlan()){showAccount();return;}
            LyricsClient.Brief b=brief.get();
            if(b.title.isEmpty() || b.story.isEmpty()){
                toast("Enter a new song title and a specific story or subject.");return;
            }
            generateLyrics(b);
        });
        manual.setOnClickListener(v->{
            LyricsClient.Brief b=brief.get();
            if(b.title.isEmpty()){toast("Enter a song title.");return;}
            PromptStore.Lyrics l=new PromptStore.Lyrics();
            l.artist=b.artist.isEmpty()?"Original Songs":b.artist;
            l.song=b.title;l.brief=b.toJson("");l.body="[Verse 1]\n\n[Chorus]\n\n[Verse 2]\n\n[Chorus]\n\n[Bridge]\n\n[Final Chorus]\n";
            l.mode="MANUAL LYRICS DRAFT";
            long id=store.saveLyrics(l);
            showLyricsDetail(id);
        });
    }

    private void generateLyrics(LyricsClient.Brief brief){
        generateLyrics(brief,false);
    }
    private void generateLyrics(LyricsClient.Brief brief,boolean includeProduction){
        if(working){toast("An operation is already running.");return;}
        if(!chat.canUsePlan()){showAccount();return;}
        working=true;
        tasks.execute(()->{
            try{
                String token=chat.accessToken();
                java.util.ArrayList<ChatGptPrompts.Model> models=ChatGptPrompts.models(token);
                String preferred=getSharedPreferences("suno_settings",MODE_PRIVATE).getString("chat_model","");
                String model=models.get(0).id;
                for(ChatGptPrompts.Model m:models)if(m.id.equals(preferred)){model=m.id;break;}
                PromptStore.Lyrics lyrics=LyricsClient.generate(token,model,brief);
                long id=store.saveLyrics(lyrics);
                if(!includeProduction){
                    runOnUiThread(()->{working=false;showLyricsDetail(id);});
                    return;
                }
                // The first stage is already saved. If the second AI request fails,
                // always return the lyrics id and never discard or overwrite it.
                try{
                    PromptStore.LyricProduction production=LyricsProductionClient.generate(
                        token,model,id,lyrics.song,lyrics.body,brief,brief.styleGuide);
                    long productionId=store.saveLyricProduction(production);
                    runOnUiThread(()->{
                        working=false;
                        showLyricProductionDetail(productionId);
                        toast("Lyrics + matching Suno sound saved to your library");
                    });
                }catch(Exception followup){
                    String reason=followup.getMessage()==null?"No production prompt returned":followup.getMessage();
                    runOnUiThread(()->{
                        working=false;showLyricsDetail(id);
                        new AlertDialog.Builder(this)
                            .setTitle("Lyrics saved; production not generated")
                            .setMessage("Your original lyrics are saved under the artist folder. The second ChatGPT request failed: "+
                                reason+"\n\nYou can generate the matching sound later from the saved lyric draft.")
                            .setPositiveButton("OK",null).show();
                    });
                }
            }catch(Exception e){
                String message=e.getMessage()==null?"Unknown lyrics-generation error":e.getMessage();
                runOnUiThread(()->{
                    working=false;
                    new AlertDialog.Builder(this)
                        .setTitle("Lyrics were not generated")
                        .setMessage(message+"\n\nAny previously saved lyrics and song prompts remain unchanged.")
                        .setPositiveButton("OK",null).show();
                });
            }
        });
    }

    private void reviseLyrics(LyricsClient.Brief brief,String originalText,String instructions){
        if(working){toast("Another task is still running.");return;}
        working=true;
        tasks.execute(()->{
            try{
                String token=chat.accessToken();
                java.util.ArrayList<ChatGptPrompts.Model> models=ChatGptPrompts.models(token);
                String preferred=getSharedPreferences("suno_settings",MODE_PRIVATE).getString("chat_model","");
                String model=models.get(0).id;
                for(ChatGptPrompts.Model m:models)if(m.id.equals(preferred)){model=m.id;break;}
                PromptStore.Lyrics result=LyricsClient.revise(token,model,brief,originalText,instructions);
                // Always new row; no mutation of source lyrics.
                result.id=0;
                long id=store.saveLyrics(result);
                runOnUiThread(()->{
                    working=false;showLyricsDetail(id);
                    toast("New rhythm-matched lyrics draft saved; original remains in the folder.");
                });
            }catch(Exception ex){
                String reason=ex.getMessage()==null?"AI did not complete the lyric revision.":ex.getMessage();
                runOnUiThread(()->{
                    working=false;
                    new AlertDialog.Builder(this).setTitle("Revision not saved")
                        .setMessage(reason+"\n\nYour existing draft is still available.")
                        .setPositiveButton("OK",null).show();
                });
            }
        });
    }

    private void showLyricsDetail(long id){
        PromptStore.Lyrics l=store.getLyrics(id);
        if(l==null){showHome();return;}
        activeId=id;activeArtist=l.artist;location="lyrics-detail";
        base(l.song,l.artist+"  •  "+l.mode,l.artist,()->showArtist(l.artist));
        LinearLayout panel=panel(body);
        panel.addView(text("SUNO LYRICS",12,LILAC,true));spacing(panel,10);
        EditText editor=input("Original lyrics with Suno section tags",l.body,true);
        editor.setMinLines(15);panel.addView(editor);spacing(panel,13);
        TextView copy=action("Copy lyrics for Suno",true);panel.addView(copy);
        copy.setOnClickListener(v->copy(editor.getText().toString(),"Lyrics copied to clipboard"));
        spacing(panel,10);
        TextView save=action("Save edited lyrics",false);panel.addView(save);
        save.setOnClickListener(v->{
            if(value(editor).isEmpty()){toast("Lyrics cannot be blank.");return;}
            l.body=editor.getText().toString();
            if(!l.mode.contains("EDITED"))l.mode=l.mode+" / EDITED";
            store.saveLyrics(l);toast("Lyrics draft saved");
        });
        spacing(panel,10);
        TextView profile=action("Set musical reference for offline check",false);
        panel.addView(profile);
        profile.setOnClickListener(v->{
            if(!editor.getText().toString().equals(l.body)){
                toast("Save lyric edits first so the song's musical reference is retained.");return;
            }
            LyricsClient.Brief current=LyricsClient.Brief.fromJson(l.brief);
            LinearLayout content=vertical();content.setPadding(px(20),px(10),px(20),0);
            content.addView(text("Attach a genre and production profile to this lyric draft. This works entirely offline and does not alter the lyrics.",13,SOFT,false));
            spacing(content,12);
            EditText genre=input("e.g. 1984 British new-wave dance-rock",current.genre,false);
            field(content,"Musical genre / era",genre);
            EditText reference=input("e.g. The Wild Boys (musical influence only)",current.referenceTrack,false);
            field(content,"Reference recording (optional)",reference);
            EditText styleProfile=input("Describe drums, guitars, synths, groove, vocal dynamics",current.styleGuide,true);
            styleProfile.setMinLines(4);
            field(content,"Detailed musical production profile",styleProfile);
            TextView choose=action("Choose any saved production prompt",false);
            content.addView(choose);spacing(content,12);
            choose.setOnClickListener(w->{
                java.util.ArrayList<PromptStore.Prompt> options=new java.util.ArrayList<>();
                for(String artistFolder:store.artists().keySet())options.addAll(store.byArtist(artistFolder));
                if(options.isEmpty()){toast("No saved production prompts yet. Enter your sound manually.");return;}
                String[] labels=new String[options.size()];
                for(int i=0;i<options.size();i++){
                    PromptStore.Prompt source=options.get(i);
                    labels[i]=source.artist+" — "+source.song;
                }
                new AlertDialog.Builder(this).setTitle("Choose a musical reference")
                    .setItems(labels,(dialog,which)->{
                        PromptStore.Prompt source=options.get(which);
                        reference.setText(source.song);
                        styleProfile.setText(source.style);
                    }).setNegativeButton("Cancel",null).show();
            });
            ScrollView scroll=new ScrollView(this);scroll.addView(content);
            new AlertDialog.Builder(this).setTitle("Music reference for this draft")
                .setView(scroll).setNegativeButton("Cancel",null)
                .setPositiveButton("Save musical reference",(dialog,which)->{
                    current.artist=l.artist;
                    current.title=l.song;
                    current.genre=value(genre);
                    current.referenceTrack=value(reference);
                    current.styleGuide=value(styleProfile);
                    current.rhythm=LyricRhythmCoach.suggest(current.combinedProduction());
                    String notes="";
                    try{notes=new JSONObject(l.brief).optString("notes","");}catch(Exception ignored){}
                    l.brief=current.toJson(notes);
                    store.saveLyrics(l);
                    showLyricsDetail(l.id);
                    toast("Music reference saved — run the offline check again");
                }).show();
        });
        spacing(panel,10);
        TextView qc=action("Check phrasing and hook density (offline)",false);
        panel.addView(qc);
        qc.setOnClickListener(v->{
            LyricsClient.Brief qcBrief=LyricsClient.Brief.fromJson(l.brief);
            LyricRhythmCoach.Report report=LyricRhythmCoach.inspect(
                editor.getText().toString(),qcBrief.combinedProduction());
            new AlertDialog.Builder(this).setTitle("Songwriting rhythm check")
                .setMessage(report.summary).setPositiveButton("OK",null).show();
        });
        spacing(panel,10);
        TextView share=action("Share lyrics",false);panel.addView(share);
        share.setOnClickListener(v->{
            Intent intent=new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT,l.song+"\n\n"+editor.getText().toString());
            startActivity(Intent.createChooser(intent,"Share original lyrics"));
        });
        LinearLayout notes=panel(body);
        notes.addView(text("SONGWRITER BRIEF",12,LILAC,true));spacing(notes,9);
        LyricsClient.Brief b=LyricsClient.Brief.fromJson(l.brief);
        notes.addView(text(b.describe(),13,SOFT,false));
        try{
            String producerNotes=new JSONObject(l.brief).optString("notes","");
            if(!producerNotes.isEmpty()){
                spacing(notes,10);
                notes.addView(text("AI ARRANGEMENT NOTES\n"+producerNotes,13,SOFT,false));
            }
        }catch(Exception ignored){}
        spacing(body,12);
        LinearLayout productionPanel=panel(body);
        productionPanel.addView(text("PRODUCTION PROMPTS FOR THESE LYRICS",12,LILAC,true));
        spacing(productionPanel,9);
        productionPanel.addView(text("Design the band, groove and vocal arrangement around the actual words. Each version is linked to this lyric draft and can be edited independently.",13,SOFT,false));
        spacing(productionPanel,13);
        TextView music=action(chat.canUsePlan()?"AI • Create production from these lyrics":"Connect ChatGPT for lyrics-first production",true);
        productionPanel.addView(music);
        music.setOnClickListener(v->{
            if(!chat.canUsePlan()){showAccount();return;}
            if(!editor.getText().toString().equals(l.body)){
                toast("Save the lyrics first so the production matches the saved version.");
                return;
            }
            LyricsClient.Brief brief=LyricsClient.Brief.fromJson(l.brief);
            String originalDirection=brief.styleGuide.isEmpty()?
                (brief.genre+"; "+brief.vocal+"; "+brief.rhythm).trim():brief.styleGuide;
            LinearLayout layout=vertical();
            layout.setPadding(px(20),px(10),px(20),0);
            layout.addView(text("The AI will arrange an original record around this draft's verses, hook, bridge and vocal phrasing. It will not change any lyric words.",14,SOFT,false));
            spacing(layout,12);
            EditText direction=input("Genre, era, tempo feel, vocal tone, instrument parts and mix",
                originalDirection,true);
            field(layout,"How should this song sound?",direction);
            ScrollView scrolling=new ScrollView(this);scrolling.addView(layout);
            new AlertDialog.Builder(this)
                .setTitle("Lyrics → Studio Production")
                .setView(scrolling)
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Generate",(dialog,which)->generateLyricsProduction(l,value(direction)))
                .show();
        });
        java.util.ArrayList<PromptStore.LyricProduction> availableProductions=store.productionsForLyrics(l.id);
        if(!availableProductions.isEmpty())spacing(productionPanel,12);
        for(PromptStore.LyricProduction production:availableProductions){
            TextView item=action("♫  "+production.mode+"  ›",false);
            productionPanel.addView(item);spacing(productionPanel,8);
            item.setOnClickListener(v->showLyricProductionDetail(production.id));
        }
        spacing(body,12);
        TextView repair=action(chat.canUsePlan()?"AI Rhythm + Hook Repair (save a new draft)":"Connect ChatGPT for Rhythm + Hook Repair",true);
        body.addView(repair);
        repair.setOnClickListener(v->{
            if(!chat.canUsePlan()){showAccount();return;}
            if(!editor.getText().toString().equals(l.body)){
                toast("Save your edits first to preserve the draft before AI repair.");
                return;
            }
            LyricsClient.Brief repairBrief=LyricsClient.Brief.fromJson(l.brief);
            if(repairBrief.title.isEmpty())repairBrief.title=l.song;
            if(repairBrief.artist.isEmpty())repairBrief.artist=l.artist;
            LinearLayout repairLayout=vertical();
            repairLayout.setPadding(px(20),px(10),px(20),0);
            TextView explanation=text("Keep your characters and plot. Tighten verse attacks, chorus hooks, breaths and syllables to fit this production. A NEW lyrics version is saved; this one stays untouched.",13,SOFT,false);
            repairLayout.addView(explanation);spacing(repairLayout,12);
            EditText styleOverride=input("Production style for this revision",repairBrief.styleGuide,true);
            field(repairLayout,"Production DNA (can be pasted or changed)",styleOverride);
            EditText directions=input("What should improve?",LyricRhythmCoach.focus(repairBrief.combinedProduction())+
                " Keep the original storyline and emotional bridge; avoid copying the reference recording's hook.",true);
            field(repairLayout,"Additional musical direction",directions);
            ScrollView scroll=new ScrollView(this);scroll.addView(repairLayout);
            new AlertDialog.Builder(this).setTitle("Rhythm + Hook Repair")
                .setView(scroll).setNegativeButton("Cancel",null)
                .setPositiveButton("Create new version",(dialog,which)->{
                    repairBrief.styleGuide=value(styleOverride);
                    repairBrief.rhythm=LyricRhythmCoach.suggest(repairBrief.combinedProduction());
                    reviseLyrics(repairBrief,editor.getText().toString(),value(directions));
                }).show();
        });
        spacing(body,12);
        TextView another=action(chat.canUsePlan()?"Generate new lyrical variation":"Connect ChatGPT for another variation",true);
        body.addView(another);
        another.setOnClickListener(v->{
            if(!chat.canUsePlan()){showAccount();return;}
            if(!editor.getText().toString().equals(l.body)){toast("Save your lyric edits before creating another variation.");return;}
            new AlertDialog.Builder(this)
                .setTitle("New lyric variation?")
                .setMessage("Generates a fresh draft from the same songwriter brief. This existing version and its edits will remain saved.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Generate",(dialog,which)->{
                    LyricsClient.Brief same=LyricsClient.Brief.fromJson(l.brief);
                    if(same.title.isEmpty()) {toast("This draft has no saved songwriter brief.");return;}
                    generateLyrics(same);
                }).show();
        });
        spacing(body,12);
        TextView delete=action("Delete this lyrics draft",false);body.addView(delete);
        delete.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Delete lyric draft?")
            .setMessage("This deletes this lyric draft AND its linked lyrics-first production arrangements. Other lyric drafts and separate saved song production prompts remain unaffected.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Delete",(dialog,which)->{
                store.deleteLyrics(l.id);showArtist(l.artist);
            }).show());
    }

    private void generateLyricsProduction(PromptStore.Lyrics lyrics,String direction){
        if(working){toast("Another operation is active.");return;}
        if(!chat.canUsePlan()){showAccount();return;}
        working=true;
        tasks.execute(()->{
            try{
                String token=chat.accessToken();
                java.util.ArrayList<ChatGptPrompts.Model> catalog=ChatGptPrompts.models(token);
                String selected=getSharedPreferences("suno_settings",MODE_PRIVATE)
                    .getString("chat_model","");
                boolean chosen=false;
                for(ChatGptPrompts.Model m:catalog)if(m.id.equals(selected)){chosen=true;break;}
                if(!chosen)selected=catalog.get(0).id;
                LyricsClient.Brief brief=LyricsClient.Brief.fromJson(lyrics.brief);
                PromptStore.LyricProduction p=LyricsProductionClient.generate(
                    token,selected,lyrics.id,lyrics.song,lyrics.body,brief,direction);
                // Never overwrite an existing user-edited production or a song's old production profile.
                long id=store.saveLyricProduction(p);
                runOnUiThread(()->{
                    working=false;showLyricProductionDetail(id);
                });
            }catch(Exception ex){
                String reason=ex.getMessage()==null?"No production prompt was returned.":ex.getMessage();
                runOnUiThread(()->{
                    working=false;
                    new AlertDialog.Builder(this).setTitle("Production wasn't saved")
                        .setMessage(reason+"\n\nYour lyrics and existing productions remain unchanged.")
                        .setPositiveButton("OK",null).show();
                });
            }
        });
    }

    private void showLyricProductionDetail(long productionId){
        PromptStore.LyricProduction p=store.getLyricProduction(productionId);
        if(p==null){showHome();return;}
        PromptStore.Lyrics lyric=store.getLyrics(p.lyricId);
        if(lyric==null){showHome();return;}
        location="lyric-production";activeArtist=lyric.artist;activeId=lyric.id;
        base("Lyrics-first Sound",lyric.song+"  •  Studio production",
             lyric.song,()->showLyricsDetail(lyric.id));
        LinearLayout card=panel(body);
        card.addView(text("SUNO V6 STYLE",12,LILAC,true));spacing(card,10);
        EditText style=input("Up to 1000 characters",p.style,true);
        style.setMinLines(8);card.addView(style);spacing(card,7);
        TextView charCount=text(p.style.length()+" / 1000 characters",12,SOFT,false);
        card.addView(charCount);
        style.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){
                charCount.setText(s.length()+" / 1000 characters");
                charCount.setTextColor(s.length()>1000?0xffffab90:SOFT);
            }
            public void afterTextChanged(android.text.Editable e){}
        });
        spacing(card,12);
        TextView copy=action("Copy style for Suno",true);card.addView(copy);
        copy.setOnClickListener(v->copy(style.getText().toString(),"Production style copied"));
        spacing(card,15);
        card.addView(text("EXCLUDE",12,LILAC,true));spacing(card,8);
        EditText exclude=input("Sounds to exclude",p.exclude,true);
        card.addView(exclude);spacing(card,12);
        TextView copyExclude=action("Copy Exclude prompt",false);card.addView(copyExclude);
        copyExclude.setOnClickListener(v->copy(exclude.getText().toString(),"Exclude copied"));
        spacing(card,12);
        TextView save=action("Save edits to this production",true);card.addView(save);
        save.setOnClickListener(v->{
            String text=value(style);
            if(text.isEmpty() || text.length()>1000){
                toast("Style must be 1–1000 characters.");return;
            }
            p.style=text;p.exclude=value(exclude);
            if(!p.mode.contains("EDITED"))p.mode+=" / EDITED";
            store.saveLyricProduction(p);
            toast("Production prompt saved to this lyric draft");
        });
        LinearLayout notes=panel(body);
        notes.addView(text("ARRANGEMENT NOTES",12,LILAC,true));spacing(notes,7);
        notes.addView(text(p.notes.isEmpty()?"AI producer notes not supplied.":p.notes,14,SOFT,false));
        if(!LyricsProductionClient.fingerprint(lyric.body).equals(p.sourceHash)){
            spacing(notes,13);
            notes.addView(text("SOURCE LYRICS CHANGED: This sound was designed for an earlier version of the draft. Generate a new arrangement if the phrasing or structure changed.",13,0xffffbb88,true));
        }
        spacing(body,12);
        TextView share=action("Share style and Exclude",false);
        body.addView(share);
        share.setOnClickListener(v->{
            Intent send=new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT,lyric.song+
                "\n\nSTYLE\n"+style.getText().toString()+"\n\nEXCLUDE\n"+exclude.getText().toString());
            startActivity(Intent.createChooser(send,"Share Suno production"));
        });
        spacing(body,12);
        TextView delete=action("Delete this arrangement only",false);body.addView(delete);
        delete.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Delete production?")
            .setMessage("The original lyrics and other arrangements will stay saved.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Delete",(dialog,which)->{
                store.deleteLyricProduction(p.id);showLyricsDetail(lyric.id);
            }).show());
    }

    private void generateWithChatGpt(String artist,String song,String notes,long replacementId) {
        if(working){toast("Another operation is active.");return;}
        // NEVER choose the first listed AI model silently: plan availability and
        // model quality vary. A previous explicit selection is persisted locally.
        String chosen=getSharedPreferences("suno_settings",MODE_PRIVATE)
            .getString("chat_model","");
        if(chosen.isEmpty()){
            working=true;
            tasks.execute(()->{
                try{
                    java.util.ArrayList<ChatGptPrompts.Model> models=
                        ChatGptPrompts.models(chat.accessToken());
                    String[] labels=new String[models.size()];
                    for(int i=0;i<models.size();i++)
                        labels[i]=models.get(i).label+" ("+models.get(i).id+")";
                    runOnUiThread(()->{
                        working=false;
                        new AlertDialog.Builder(this).setTitle("Choose the production AI model")
                            .setMessage("Select which account-available model will write the Studio DNA. Your choice is saved. This is independent of which model runs this ChatGPT conversation.")
                            .setItems(labels,(dialog,which)->{
                                getSharedPreferences("suno_settings",MODE_PRIVATE).edit()
                                    .putString("chat_model",models.get(which).id).apply();
                                generateWithChatGpt(artist,song,notes,replacementId);
                            }).setNegativeButton("Cancel",null).show();
                    });
                }catch(Exception ex){
                    runOnUiThread(()->{
                        working=false;
                        new AlertDialog.Builder(this).setTitle("Could not choose model")
                            .setMessage(ex.getMessage()==null?"Model list unavailable.":ex.getMessage())
                            .setPositiveButton("OK",null).show();
                    });
                }
            });
            return;
        }
        working=true;
        String profileName=SongProfiles.find(artist,song)!=null?
            "Reviewed SONG-SPECIFIC musical reference found.":
            (PromptTemplates.artistGuide(artist)!=null?
                "Artist-level production guide; no reviewed reference for this exact song.":
                "No reviewed song-specific music profile. Research may be limited.");
        new AlertDialog.Builder(this)
            .setTitle("Studio DNA producer • 2 AI requests")
            .setMessage(profileName+"\n\nThe AI will first plan the musical DNA, then write and quality-check the Suno style. Selected model: "+
                chosen+". No original audio is analysed. A failed quality check will not replace your saved prompt.")
            .setPositiveButton("OK",null).show();
        tasks.execute(()->{
            try{
                String token=chat.accessToken();
                java.util.ArrayList<ChatGptPrompts.Model> models=ChatGptPrompts.models(token);
                String preferred=getSharedPreferences("suno_settings",MODE_PRIVATE)
                    .getString("chat_model","");
                boolean valid=false;
                for(ChatGptPrompts.Model model:models)
                    if(model.id.equals(preferred)){valid=true;break;}
                if(!valid){
                    getSharedPreferences("suno_settings",MODE_PRIVATE).edit()
                        .remove("chat_model").apply();
                    throw new Exception("Your previously selected model is no longer available to this account. Try Generate again to choose another.");
                }
                // When a reviewed song reference exists, do not wait on three
                // metadata websites just to pass non-musical information to the AI.
                ResearchClient.Research research;
                if(SongProfiles.find(artist,song)!=null){
                    research=new ResearchClient.Research();
                    research.warnings="Using reviewed musical interpretation; original audio has not been analysed. Online catalogue lookups skipped for this curated song.";
                }else{
                    research=ResearchClient.lookup(artist,song);
                }
                PromptStore.Prompt generated=ChatGptPrompts.generate(
                    token,preferred,artist,song,notes,research);
                if(replacementId!=0)generated.id=replacementId;
                long id=store.save(generated);
                runOnUiThread(()->{working=false;showDetail(id);});
            }catch(Exception ex){
                String error=ex.getMessage()==null?"Unknown production-generation error":ex.getMessage();
                runOnUiThread(()->{
                    working=false;
                    new AlertDialog.Builder(this).setTitle("Studio DNA did not save")
                        .setMessage(error+"\n\nAll existing song and lyric drafts remain unchanged.")
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
        if("lyric-production".equals(location)){showLyricsDetail(activeId);}
        else if("detail".equals(location)||"lyrics-detail".equals(location)){showArtist(activeArtist);}
        else if("lyrics-form".equals(location)){if(activeArtist.isEmpty())showHome();else showArtist(activeArtist);}
        else if("artist".equals(location)||"create".equals(location)||"account".equals(location)){showHome();}
        else super.onBackPressed();
    }
    @Override protected void onDestroy(){
        chat.stopListener();tasks.shutdownNow();store.close();super.onDestroy();
    }
}
