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
print("Applied v0.7.6 comparison return loading-overlay fix")


