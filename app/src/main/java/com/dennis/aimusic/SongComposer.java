package com.dennis.aimusic;

import java.util.Random;

/** Phrase-based symbolic arranger. Training supplies note tendencies; arrangement remains explicitly rule based. */
public final class SongComposer {
    public static final class Plan {
        public final int bpm, seconds, root, style;
        public final int[] noteClasses, chordRoots, sections, noteOnset, velocity;
        public final long seed;
        public Plan(int bpm,int seconds,int root,int style,long seed,int[] notes,int[] chords,int[] sections,int[] onsets,int[] velocities) {
            this.bpm=bpm;this.seconds=seconds;this.root=root;this.style=style;this.seed=seed;
            this.noteClasses=notes;this.chordRoots=chords;this.sections=sections;this.noteOnset=onsets;this.velocity=velocities;
        }
    }
    private static final int PHRASE=32; // four bars of eight eighth notes
    /** Only 0=country,1=rock,2=dance,3=R&B are supported. */
    public static Plan compose(TinyMusicBrain brain,int bpm,int seconds,int root,int style,long seed) {
        if(bpm<65||bpm>170||seconds<10||seconds>420||style<0||style>3)
            throw new IllegalArgumentException("Invalid song length, tempo or style");
        Random r=new Random(seed);
        int bars=(int)Math.ceil((seconds*bpm)/(60.*4));
        int[] chords=new int[bars], sections=new int[bars], phraseStart=new int[bars];
        final int[] cycle={0,1,2,1,2,3,2,4};
        final double[] proportions={.08,.17,.17,.16,.17,.09,.11,.05};
        double cumulative=0;
        int[] thresholds=new int[8];
        for(int j=0;j<8;j++){cumulative+=proportions[j];thresholds[j]=(int)Math.round(bars*cumulative);}
        int[][] movements={{0,7,9,5},{0,5,7,9},{0,9,5,7},{0,7,5,7}};
        int oldBlock=-1, start=0;
        for(int bar=0;bar<bars;bar++) {
            int block=0;while(block<7&&bar>=thresholds[block])block++;
            if(block!=oldBlock){start=bar;oldBlock=block;}
            phraseStart[bar]=start;
            sections[bar]=cycle[block];
            int[] movement=movements[style];
            chords[bar]=(root+movement[((bar-start)/2+(block==5?1:0))%4])%12;
        }
        // Chorus and verse each get a recognisable theme. Later repeats preserve the theme
        // while changing occasional phrases, rather than continuously sampling unrelated notes.
        int[][] patterns=new int[5][PHRASE];
        int[][] strengths=new int[5][PHRASE];
        for(int type=0;type<5;type++)createPhrase(patterns[type],strengths[type],brain,root,style,type,new Random(seed ^ (0x9e3779b97f4a7c15L*(type+1))));
        int steps=bars*8;
        int[] notes=new int[steps], onsets=new int[steps],velocity=new int[steps];
        java.util.Arrays.fill(notes,-1);
        java.util.Arrays.fill(onsets,-1);
        for(int bar=0;bar<bars;bar++){
            int type=sections[bar];
            int sectionStart=phraseStart[bar];
            int repeat=(bar-sectionStart)/4;
            int currentChord=chords[bar];
            int baseChord=chords[Math.min(bars-1,sectionStart+(bar-sectionStart)%4)];
            for(int inBar=0;inBar<8;inBar++){
                int s=bar*8+inBar,p=((bar-sectionStart)%4)*8+inBar;
                int pc=patterns[type][p],v=strengths[type][p];
                if(pc<0)continue;
                // Distinct arrangement and intensity between sections; lower-density outro.
                if((type==0||type==4)&&inBar%2!=0)continue;
                // Repeat chorus hook faithfully, with a controlled occasional response.
                if(type!=2 && repeat>0 && r.nextDouble()<0.09)pc=(pc+(r.nextBoolean()?2:10))%12;
                int transposed=Math.floorMod(pc+(currentChord-baseChord),12);
                notes[s]=transposed;
                onsets[s]=s;
                velocity[s]=Math.min(127,Math.max(35,v+(repeat>0&&type==2?5:0)));
            }
        }
        // Extend note lengths into empty grid cells, but retain intentional phrase gaps.
        for(int s=0;s<steps;s++)if(onsets[s]==s){
            int type=sections[Math.min(bars-1,s/8)];
            int span=(type==2?2:3);
            for(int k=1;k<span&&s+k<steps&&(s+k)/8==s/8&&notes[s+k]<0;k++){
                if((s+k)%8==7)break;
                notes[s+k]=notes[s];onsets[s+k]=s;velocity[s+k]=velocity[s];
            }
        }
        return new Plan(bpm,seconds,root,style,seed,notes,chords,sections,onsets,velocity);
    }
    private static void createPhrase(int[] notes,int[] velocities,TinyMusicBrain brain,int root,int style,int type,Random rng) {
        java.util.Arrays.fill(notes,-1);
        int a=root,b=(root+7)%12;
        int position=0;
        while(position<notes.length) {
            int barPosition=position%8;
            boolean sparse=(type==0||type==4);
            boolean isRest=barPosition==7 || rng.nextDouble()<(sparse?.44:type==1?.31:type==2?.17:.28);
            if(isRest){position++;continue;}
            int pc=brain.sample(a,b,rng);
            // Degree-based selection safeguards harmonic continuity; not claimed as learned harmony.
            int[] allowed={root,(root+2)%12,(root+4)%12,(root+5)%12,(root+7)%12,(root+9)%12,(root+11)%12};
            if(rng.nextDouble()<.72){
                int closest=allowed[0],dist=13;
                for(int candidate:allowed){
                    int d=Math.min((pc-candidate+12)%12,(candidate-pc+12)%12);
                    if(d<dist){dist=d;closest=candidate;}
                }
                pc=closest;
            }
            if(type==2&&barPosition==0)pc=(root+7)%12;
            if(type==3&&barPosition==0)pc=(root+9)%12;
            notes[position]=pc;
            velocities[position]=66+rng.nextInt(37);
            a=b;b=pc;
            int gap=1+rng.nextInt(type==2?3:4);
            position+=gap;
        }
    }
}