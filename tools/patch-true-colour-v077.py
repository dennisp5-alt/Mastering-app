from pathlib import Path
import sys
root=Path(sys.argv[1])
p=root/"app/src/main/java/com/dennis/autofixcolourlab/MainActivity.java"
s=p.read_text()
s=s.replace("private static final int AMBER =", "private static final int INK = Color.rgb(236, 242, 249);\n    private static final int GOLD_INK = Color.rgb(24, 27, 34);\n    private static final int AMBER =")
for size in ("14","13","12"):
    s=s.replace(size+", NAVY, true",size+", INK, true")
s=s.replace("primary ? WHITE : NAVY","primary ? GOLD_INK : INK")
s=s.replace("button.setTextColor(selected ? WHITE : (enabled ? NAVY : MUTED));","button.setTextColor(selected ? GOLD_INK : (enabled ? INK : MUTED));")
s=s.replace("centeredText(names[i],11,Color.WHITE,true)","centeredText(names[i],11,i==0?GOLD_INK:INK,true)")
s=s.replace("for(int j=0;j<4;j++)buttons[j].setBackground(rounded(j==index?BLUE:WHITE,10,1,BORDER));","for(int j=0;j<4;j++){\n                    buttons[j].setBackground(rounded(j==index?BLUE:WHITE,10,1,BORDER));\n                    buttons[j].setTextColor(j==index?GOLD_INK:INK);\n                }")
s=s.replace("v0.7.3 STABLE TEST","v0.7.4 STABLE TEST")
p.write_text(s)
for rel in ("app/build.gradle","build-apk.sh","run-tests.sh"):
    q=root/rel
    t=q.read_text().replace("0.7.3","0.7.4").replace("versionCode 11","versionCode 12").replace("--version-code 11","--version-code 12")
    q.write_text(t)

# v0.7.5: comparison inspection controls and sampled RGB diagnostics.
anchor='        TextView fitButton=actionButton("FIT / RESET ZOOM",false);'
assert anchor in s, "Comparison controls changed: patch aborted"
s=s.replace(anchor, '''        TextView actualButton=actionButton("100% PIXEL VIEW",false);
        actualButton.setOnClickListener(v->{
            Bitmap active=variants[0];
            if(zoomImage.getDrawable() instanceof android.graphics.drawable.BitmapDrawable)
                active=((android.graphics.drawable.BitmapDrawable)zoomImage.getDrawable()).getBitmap();
            if(active==null)return;
            transform.reset();
            transform.postTranslate((zoomImage.getWidth()-active.getWidth())/2f,
                    (zoomImage.getHeight()-active.getHeight())/2f);
            zoomImage.setImageMatrix(transform);
        });
        page.addView(actualButton,matchWithBottom(6));
''' + anchor)
anchor2='        setContentView(page);'
assert anchor2 in s
s=s.replace(anchor2,'''        TextView diagnostics=centeredText("RGB diagnostics will appear when previews are ready",11,INK,false);
        diagnostics.setPadding(dp(8),dp(4),dp(8),dp(10));
        page.addView(diagnostics,matchWrap());
''' + anchor2,1)
s=s.replace('status.setText("All four previews ready • Zoom and pan stay locked");','''status.setText("All four previews ready • Shared zoom and pan");
                    diagnostics.setText(comparisonStats(variants[0],variants[1],variants[2],variants[3]));''')
# Standalone diagnostic method. Samples at most ~16k pixels per image; no edits to processing.
method='''    private String comparisonStats(Bitmap before, Bitmap tc, Bitmap lab, Bitmap nat) {
        Bitmap[] all={before,tc,lab,nat};
        String[] names={"BEFORE","TRUE COLOUR","LAB","NAT GEO"};
        StringBuilder out=new StringBuilder("SAMPLED RGB DIAGNOSTICS\\n");
        for(int n=0;n<all.length;n++){
            Bitmap b=all[n];
            if(b==null)continue;
            int step=Math.max(1,(int)Math.sqrt((double)b.getWidth()*b.getHeight()/16000d));
            long red=0,green=0,blue=0,shadows=0,highlights=0,count=0;
            for(int y=0;y<b.getHeight();y+=step)
                for(int x=0;x<b.getWidth();x+=step){
                    int c=b.getPixel(x,y),r=Color.red(c),g=Color.green(c),bl=Color.blue(c);
                    red+=r;green+=g;blue+=bl;
                    int lum=(r*54+g*183+bl*19)>>8;
                    if(lum<=5)shadows++;
                    if(lum>=250)highlights++;
                    count++;
                }
            if(count==0)continue;
            out.append(names[n]).append("  RGB ")
                .append(red/count).append("/").append(green/count).append("/").append(blue/count)
                .append("  shadows ").append(100*shadows/count).append("%")
                .append("  highlights ").append(100*highlights/count).append("%\\n");
        }
        out.append("Preview-sampled measurements; not full-resolution export QC.");
        return out.toString();
    }

'''
marker='    private void selectLook(boolean labLook) {'
assert marker in s
s=s.replace(marker,method+marker,1)
s=s.replace("v0.7.4 STABLE TEST","v0.7.5 STABLE TEST")
p.write_text(s)
for rel in ("app/build.gradle","build-apk.sh","run-tests.sh"):
    q=root/rel
    t=q.read_text().replace("0.7.4","0.7.5").replace("versionCode 12","versionCode 13").replace("--version-code 12","--version-code 13")
    q.write_text(t)
# v0.7.6: returning to the editor must not leave its loading scrim active.
# showEditorShell() sets busy=true while creating the editor, but comparison
# navigation has no subsequent loadPhoto() to clear it.
old='comparisonOpen = false; showEditorShell(); previewView.setImageBitmap(currentDisplayBitmap()); updateLookControls(); updateAnalysisUi();'
new='comparisonOpen = false; showEditorShell(); previewView.setImageBitmap(currentDisplayBitmap()); setBusy(false, null); updateLookControls(); updateAnalysisUi();'
assert old in s, "Comparison return handler not found"
s=s.replace(old,new)
s=s.replace('v0.7.5 STABLE TEST','v0.7.6 STABLE TEST')
p.write_text(s)
for rel in ("app/build.gradle","build-apk.sh","run-tests.sh"):
    q=root/rel
    t=q.read_text().replace("0.7.5","0.7.6").replace("versionCode 13","versionCode 14").replace("--version-code 13","--version-code 14")
    q.write_text(t)

# v0.7.7 detection-only issue map: no modifications to photo processing/export.
needle='        final Bitmap[] variants = new Bitmap[4];'
assert needle in s
s=s.replace(needle,'''        final Bitmap[] issueMap = new Bitmap[1];
        final boolean[] showingMap = {false};
''' + needle,1)
needle='        page.addView(tabs,matchWrap());'
assert needle in s
s=s.replace(needle,'''        TextView mapButton=actionButton("SHOW ISSUE MAP",false);
        mapButton.setOnClickListener(v->{
            if(issueMap[0]==null){Toast.makeText(this,"Mapping image regions…",Toast.LENGTH_SHORT).show();return;}
            showingMap[0]=!showingMap[0];
            zoomImage.setImageBitmap(showingMap[0]?issueMap[0]:variants[0]);
            zoomImage.setImageMatrix(transform);
            mapButton.setText(showingMap[0]?"HIDE ISSUE MAP":"SHOW ISSUE MAP");
        });
        page.addView(mapButton,matchWithBottom(6));
        TextView mapLegend=centeredText("Issue map: RED possible clipping • AMBER low local contrast • BLUE colour imbalance. Diagnostic only.",11,INK,false);
        page.addView(mapLegend,matchWithBottom(6));
''' + needle,1)
needle='        worker.execute(()->{\n            Bitmap[] result=new Bitmap[3];'
assert needle in s
s=s.replace(needle,'''        worker.execute(()->{
            final Bitmap map=buildIssueMap(originalPreview);
            runOnUiThread(()->{
                if(comparisonOpen && generation==workGeneration){issueMap[0]=map;mapLegend.setText("Issue map ready • RED clipping • AMBER low contrast • BLUE colour imbalance • detection only");}
                else if(map!=null)map.recycle();
            });
            Bitmap[] result=new Bitmap[3];''',1)
marker='    private String comparisonStats(Bitmap before, Bitmap tc, Bitmap lab, Bitmap nat) {'
assert marker in s
method='''    // Overlapping diagnostic tiles: detect potential anomalies, never repair or alter exports.
    private Bitmap buildIssueMap(Bitmap source) {
        if(source==null)return null;
        final int w=source.getWidth(),h=source.getHeight();
        final int tile=Math.max(24,Math.min(w,h)/14);
        Bitmap result=source.copy(Bitmap.Config.ARGB_8888,true);
        android.graphics.Canvas canvas=new android.graphics.Canvas(result);
        android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        for(int y=0;y<h;y+=tile/2)for(int x=0;x<w;x+=tile/2){
            int endX=Math.min(w,x+tile),endY=Math.min(h,y+tile);
            long rSum=0,gSum=0,bSum=0,lSum=0,l2Sum=0,clipped=0,n=0;
            int step=Math.max(1,tile/16);
            for(int py=y;py<endY;py+=step)for(int px=x;px<endX;px+=step){
                int c=source.getPixel(px,py),r=Color.red(c),g=Color.green(c),b=Color.blue(c);
                int l=(54*r+183*g+19*b)>>8;
                rSum+=r;gSum+=g;bSum+=b;lSum+=l;l2Sum+=(long)l*l;
                if(l<=3||l>=252)clipped++;
                n++;
            }
            if(n==0)continue;
            double mean=(double)lSum/n;
            double variance=(double)l2Sum/n-mean*mean;
            int rr=(int)(rSum/n),gg=(int)(gSum/n),bb=(int)(bSum/n);
            int color=0;
            if(clipped*100/n>=9)color=Color.argb(88,255,63,67);
            else if(variance<95 && mean>25 && mean<230)color=Color.argb(72,255,184,44);
            else if(Math.max(rr,Math.max(gg,bb))-Math.min(rr,Math.min(gg,bb))>58)
                color=Color.argb(54,65,155,255);
            if(color!=0){
                paint.setColor(color);
                canvas.drawRect(x,y,endX,endY,paint);
            }
        }
        return result;
    }

'''
s=s.replace(marker,method+marker,1)
s=s.replace('v0.7.6 STABLE TEST','v0.7.7 STABLE TEST')
p.write_text(s)
for rel in ("app/build.gradle","build-apk.sh","run-tests.sh"):
    q=root/rel
    t=q.read_text().replace("0.7.6","0.7.7").replace("versionCode 14","versionCode 15").replace("--version-code 14","--version-code 15")
    q.write_text(t)
print("Applied v0.7.7 diagnostic issue map; processing unchanged")



