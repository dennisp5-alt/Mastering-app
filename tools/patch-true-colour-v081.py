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

# v0.7.8: contextual, smoothly blended diagnostic map. Detection only.
start=s.index('    private Bitmap buildIssueMap(Bitmap source) {')
end=s.index('    private String comparisonStats(',start)
assert start>=0 and end>start
replacement=r'''    private Bitmap buildIssueMap(Bitmap source) {
        if(source==null)return null;
        final int w=source.getWidth(),h=source.getHeight();
        final int tile=Math.max(32,Math.min(w,h)/18);
        final int stride=Math.max(12,tile/2);
        final int cols=(w+stride-1)/stride,rows=(h+stride-1)/stride;
        Bitmap result=source.copy(Bitmap.Config.ARGB_8888,true);
        android.graphics.Canvas canvas=new android.graphics.Canvas(result);
        android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(android.graphics.Paint.Style.FILL);
        for(int gy=0;gy<rows;gy++)for(int gx=0;gx<cols;gx++){
            int x=gx*stride,y=gy*stride;
            int left=Math.max(0,x-tile/2),right=Math.min(w,x+tile/2);
            int top=Math.max(0,y-tile/2),bottom=Math.min(h,y+tile/2);
            long rs=0,gs=0,bs=0,ls=0,l2=0,nearBlack=0,nearWhite=0;
            long channelClip=0, n=0;
            int step=Math.max(1,tile/20);
            for(int py=top;py<bottom;py+=step)for(int px=left;px<right;px+=step){
                int c=source.getPixel(px,py);
                int r=Color.red(c),g=Color.green(c),b=Color.blue(c);
                int lum=(54*r+183*g+19*b)>>8;
                rs+=r;gs+=g;bs+=b;ls+=lum;l2+=(long)lum*lum;
                if(lum<=3)nearBlack++;
                if(lum>=252)nearWhite++;
                if(r>=253||g>=253||b>=253)channelClip++;
                n++;
            }
            if(n==0)continue;
            double mean=(double)ls/n, variance=Math.max(0,(double)l2/n-mean*mean);
            double r=(double)rs/n,g=(double)gs/n,b=(double)bs/n;
            boolean skyLike=b>r*1.17 && b>g*1.07 && mean>65 && variance<1300;
            boolean foliageLike=g>r*1.12 && g>b*1.08;
            int color=0;
            // Require sustained clipping, not just isolated bright branches or clouds.
            if((nearWhite*100/n>=17 || nearBlack*100/n>=24)
                 && (channelClip*100/n>=20 || nearBlack*100/n>=24))
                color=Color.argb(86,255,64,70);
            // Uniform skies and smooth natural backgrounds are not automatically defects.
            else if(!skyLike && !foliageLike && variance<32 && mean>32 && mean<220)
                color=Color.argb(45,255,183,55);
            // A colour cast requires near-neutral content; saturated natural subjects
            // cannot be reliably identified as casts without semantic context.
            // Therefore blue cast alerts are suppressed until confidence is available.
            if(color==0)continue;
            paint.setColor(color);
            paint.setShader(new android.graphics.RadialGradient(
                x,y,Math.max(12,tile*0.7f),color,
                color & 0x00ffffff,
                android.graphics.Shader.TileMode.CLAMP));
            canvas.drawCircle(x,y,Math.max(12,tile*0.7f),paint);
            paint.setShader(null);
        }
        return result;
    }

'''
s=s[:start]+replacement+s[end:]
s=s.replace('Issue map: RED possible clipping • AMBER low local contrast • BLUE colour imbalance. Diagnostic only.',
            'Issue map: RED likely clipping • AMBER very low contrast. Blue cast alerts suppressed. Diagnostic only.')
s=s.replace('Issue map ready • RED clipping • AMBER low contrast • BLUE colour imbalance • detection only',
            'Issue map ready • RED likely clipping • AMBER low contrast • natural sky protected • detection only')
s=s.replace('v0.7.7 STABLE TEST','v0.7.8 STABLE TEST')
p.write_text(s)
for rel in ("app/build.gradle","build-apk.sh","run-tests.sh"):
    q=root/rel
    t=q.read_text().replace("0.7.7","0.7.8").replace("versionCode 15","versionCode 16").replace("--version-code 15","--version-code 16")
    q.write_text(t)

# v0.7.9: mode-aware diagnostic mapping and conservative shadow-noise cues.
old='''        final Bitmap[] issueMap = new Bitmap[1];
        final boolean[] showingMap = {false};'''
new='''        final Bitmap[] issueMap = new Bitmap[4];
        final boolean[] showingMap = {false};
        final int[] mapMode = {0};'''
assert old in s
s=s.replace(old,new,1)
old='''            zoomImage.setImageBitmap(showingMap[0]?issueMap[0]:variants[0]);'''
new='''            zoomImage.setImageBitmap(showingMap[0]?issueMap[mapMode[0]]:variants[mapMode[0]]);'''
assert old in s
s=s.replace(old,new,1)
# Remove v0.7.7 single-source map callback, independent of v0.7.8 legend changes.
map_start=s.index('            final Bitmap map=buildIssueMap(originalPreview);')
map_end=s.index('            Bitmap[] result=new Bitmap[3];',map_start)
assert map_start>=0 and map_end>map_start
s=s[:map_start]+s[map_end:]
needle='''                    diagnostics.setText(comparisonStats(variants[0],variants[1],variants[2],variants[3]));'''
assert needle in s
s=s.replace(needle,needle+'''
                    worker.execute(()->{
                        Bitmap[] maps=new Bitmap[4];
                        for(int k=0;k<4;k++)maps[k]=buildIssueMap(variants[k]);
                        runOnUiThread(()->{
                            if(!comparisonOpen || generation!=workGeneration){
                                for(Bitmap m:maps)if(m!=null)m.recycle();
                                return;
                            }
                            System.arraycopy(maps,0,issueMap,0,4);
                            mapLegend.setText("Mode-aware issue map ready • RED clipping • AMBER low contrast • PURPLE possible shadow noise • diagnostic only");
                            if(showingMap[0] && issueMap[mapMode[0]]!=null)
                                zoomImage.setImageBitmap(issueMap[mapMode[0]]);
                        });
                    });''',1)
# Follow original tab selection without replacing its click listener.
needle='''        page.addView(tabs,matchWrap());'''
assert needle in s
s=s.replace(needle,'''        for(int k=0;k<4;k++){
            final int selected=k;
            tabs.getChildAt(k).setOnTouchListener((v,event)->{
                if(event.getAction()==android.view.MotionEvent.ACTION_UP){
                    v.post(()->{
                        mapMode[0]=selected;
                        if(showingMap[0] && issueMap[selected]!=null)
                            zoomImage.setImageBitmap(issueMap[selected]);
                        else if(showingMap[0]){
                            showingMap[0]=false;
                            mapButton.setText("SHOW ISSUE MAP");
                        }
                    });
                }
                return false;
            });
        }
''' + needle,1)
# Conservative noise cue: only near-dark textured areas, not bright sky.
needle='''            if(color==0)continue;'''
assert needle in s
s=s.replace(needle,'''            if(color==0 && mean>12 && mean<64 && variance>65 && variance<420
                && !skyLike && !foliageLike)
                color=Color.argb(38,170,92,220);
''' + needle,1)
s=s.replace('Issue map: RED likely clipping • AMBER very low contrast. Blue cast alerts suppressed. Diagnostic only.',
            'Issue map: RED clipping • AMBER low contrast • PURPLE possible shadow noise. Diagnostic only.')
s=s.replace('v0.7.8 STABLE TEST','v0.7.9 STABLE TEST')
p.write_text(s)
for rel in ("app/build.gradle","build-apk.sh","run-tests.sh"):
    q=root/rel
    t=q.read_text().replace("0.7.8","0.7.9").replace("versionCode 16","versionCode 17").replace("--version-code 16","--version-code 17")
    q.write_text(t)
print("Applied v0.7.9 mode-aware diagnostics and shadow noise cues; engines unchanged")






# v0.8.0: diagnostic-only measurements, sampled across each comparison mode.
# Noise metric measures residual variation in flat dark patches; it is not a
# definitive classifier. Edge metric counts strong adjacent luminance changes.
anchor='''        out.append("Preview-sampled measurements; not full-resolution export QC.");'''
assert anchor in s
s=s.replace(anchor,'''        out.append("PREVIEW QUALITY INDICATORS\\n");
        for(int k=0;k<all.length;k++){
            Bitmap b=all[k];
            if(b==null)continue;
            int w=b.getWidth(),h=b.getHeight();
            int step=Math.max(2,(int)Math.sqrt((double)w*h/14000d));
            long shadowCount=0,flatCount=0,noisyCount=0,edgeCount=0,clippedCount=0,samples=0;
            for(int y=step;y<h-step;y+=step)for(int x=step;x<w-step;x+=step){
                int c=b.getPixel(x,y),r=Color.red(c),g=Color.green(c),bl=Color.blue(c);
                int l=(54*r+183*g+19*bl)>>8;
                int lc=b.getPixel(x-step,y),rc=b.getPixel(x+step,y);
                int uc=b.getPixel(x,y-step),dc=b.getPixel(x,y+step);
                int ll=(54*Color.red(lc)+183*Color.green(lc)+19*Color.blue(lc))>>8;
                int lr=(54*Color.red(rc)+183*Color.green(rc)+19*Color.blue(rc))>>8;
                int lu=(54*Color.red(uc)+183*Color.green(uc)+19*Color.blue(uc))>>8;
                int ld=(54*Color.red(dc)+183*Color.green(dc)+19*Color.blue(dc))>>8;
                int localMin=Math.min(Math.min(ll,lr),Math.min(lu,ld));
                int localMax=Math.max(Math.max(ll,lr),Math.max(lu,ld));
                if(l<64)shadowCount++;
                if(l<=3||l>=252)clippedCount++;
                if(Math.abs(ll-lr)+Math.abs(lu-ld)>75)edgeCount++;
                if(l>10 && l<85 && localMax-localMin<26){
                    flatCount++;
                    if(Math.abs(4*l-ll-lr-lu-ld)>45)noisyCount++;
                }
                samples++;
            }
            if(samples>0){
                out.append(names[k]).append(" dark ").append(100*shadowCount/samples).append("%")
                   .append(" clip ").append(100*clippedCount/samples).append("%")
                   .append(" edges ").append(100*edgeCount/samples).append("%")
                   .append(" shadow texture ").append(flatCount>0?100*noisyCount/flatCount:0)
                   .append("% (").append(flatCount).append(" patches)\\n");
            }
        }
        out.append("Indicators are estimates, not proof of defects.\\n");
''' + anchor,1)
# Ensure original map has a helpful ready state even if no coloured regions.
s=s.replace('Mode-aware issue map ready • RED clipping • AMBER low contrast • PURPLE possible shadow noise • diagnostic only',
            'Map ready for selected mode • RED clipping • AMBER low contrast • PURPLE shadow texture • see quality indicators')
s=s.replace('v0.7.9 STABLE TEST','v0.8.0 STABLE TEST')
p.write_text(s)
for rel in ("app/build.gradle","build-apk.sh","run-tests.sh"):
    q=root/rel
    t=q.read_text().replace("0.7.9","0.8.0").replace("versionCode 17","versionCode 18").replace("--version-code 17","--version-code 18")
    q.write_text(t)
print("Applied v0.8.0 sampled quality diagnostics; processing engines unchanged")

# v0.8.1: more discriminating preview diagnostics and readable aligned report.
# Diagnostic-only: no alterations to photo processing or exported images.
start=s.index('        out.append("PREVIEW QUALITY INDICATORS\\n");')
end=s.index('        out.append("Indicators are estimates, not proof of defects.\\n");',start)
end+=len('        out.append("Indicators are estimates, not proof of defects.\\n");')
replacement=r'''        out.append("QUALITY ANALYSIS (PREVIEW ONLY)\n");
        out.append("Mode       Dark  Black  White  Edge  Noise  Halo\n");
        for(int k=0;k<all.length;k++){
            Bitmap b=all[k];
            if(b==null)continue;
            int w=b.getWidth(),h=b.getHeight();
            int step=Math.max(2,(int)Math.sqrt((double)w*h/18000d));
            long dark=0,black=0,white=0,edges=0,noise=0,flat=0,halos=0,edgeCandidates=0,n=0;
            for(int y=2*step;y<h-2*step;y+=step)for(int x=2*step;x<w-2*step;x+=step){
                int c=b.getPixel(x,y);
                int l=(54*Color.red(c)+183*Color.green(c)+19*Color.blue(c))>>8;
                int[] v=new int[8];
                int[][] xy={{x-step,y},{x+step,y},{x,y-step},{x,y+step},
                            {x-2*step,y},{x+2*step,y},{x,y-2*step},{x,y+2*step}};
                for(int j=0;j<8;j++){
                    int p=b.getPixel(xy[j][0],xy[j][1]);
                    v[j]=(54*Color.red(p)+183*Color.green(p)+19*Color.blue(p))>>8;
                }
                int range=Math.max(Math.max(v[0],v[1]),Math.max(v[2],v[3]))
                         -Math.min(Math.min(v[0],v[1]),Math.min(v[2],v[3]));
                int gradient=Math.max(Math.abs(v[0]-v[1]),Math.abs(v[2]-v[3]));
                if(l<64)dark++;
                if(l<=3)black++;
                if(l>=252)white++;
                if(gradient>38)edges++;
                // Residual only within low-gradient dark regions, excluding structural edges.
                if(l>8 && l<90 && range<22 && gradient<14){
                    flat++;
                    int residual=Math.abs(4*l-v[0]-v[1]-v[2]-v[3]);
                    if(residual>32)noise++;
                }
                // Overshoot cue near strong transitions, with outer samples as context.
                if(gradient>50){
                    edgeCandidates++;
                    int localMax=Math.max(Math.max(v[0],v[1]),Math.max(v[2],v[3]));
                    int localMin=Math.min(Math.min(v[0],v[1]),Math.min(v[2],v[3]));
                    int outerMax=Math.max(Math.max(v[4],v[5]),Math.max(v[6],v[7]));
                    int outerMin=Math.min(Math.min(v[4],v[5]),Math.min(v[6],v[7]));
                    if((l>localMax+18 && l>outerMax+12)
                      ||(l<localMin-18 && l<outerMin-12))halos++;
                }
                n++;
            }
            if(n>0){
                String shortName=k==0?"BEFORE":k==1?"TRUE":k==2?"LAB":"NAT GEO";
                out.append(String.format(java.util.Locale.US,
                    "%-7s %3d%%  %3d%%   %3d%%   %3d%%   %3d%%   %3d%%\n",
                    shortName,100*dark/n,100*black/n,100*white/n,
                    100*edges/n,flat>0?100*noise/flat:0,
                    edgeCandidates>0?100*halos/edgeCandidates:0));
                out.append("    Noise candidates ").append(noise).append("/")
                   .append(flat).append(" flat shadow samples; halo candidates ")
                   .append(halos).append("/").append(edgeCandidates).append(" edges\n");
            }
        }
        out.append("Black/White = separate clipping estimates. Noise/Halo = heuristic cues, not confirmed defects.\n");'''
s=s[:start]+replacement+s[end:]
# Preserve existing TextView and avoid monospaced width assumption in compact display.
s=s.replace('v0.8.0 STABLE TEST','v0.8.1 STABLE TEST')
p.write_text(s)
for rel in ("app/build.gradle","build-apk.sh","run-tests.sh"):
    q=root/rel
    t=q.read_text().replace("0.8.0","0.8.1").replace("versionCode 18","versionCode 19").replace("--version-code 18","--version-code 19")
    q.write_text(t)
print("Applied v0.8.1 separated clipping, edge-aware shadow noise, halo estimates and compact comparison report; engines unchanged")
