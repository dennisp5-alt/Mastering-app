package com.dennis.aimusic;

import java.util.Random;

/** Builds a simple instrumental arrangement; section lengths are anchored to full-track duration. */
public final class SongComposer {
    public static final class Plan {
        public final int bpm, seconds, root, style;
        public final int[] noteClasses, chordRoots, sections;
        public final long seed;
        public Plan(int bpm,int seconds,int root,int style,long seed,int[] notes,int[] chords,int[] sections) {
            this.bpm=bpm;this.seconds=seconds;this.root=root;this.style=style;this.seed=seed;
            this.noteClasses=notes;this.chordRoots=chords;this.sections=sections;
        }
    }
    /** styles: country=0, rock=1, dance=2, r&b=3 */
    public static Plan compose(TinyMusicBrain brain,int bpm,int seconds,int root,int style,long seed) {
        if(bpm<65||bpm>170||seconds<10||seconds>420)throw new IllegalArgumentException("Invalid song length/tempo");
        Random r=new Random(seed);
        int bars=(int)Math.ceil((seconds*bpm)/(60.*4));
        int[] chords=new int[bars], sections=new int[bars];
        // 8-part architecture: intro, verse, chorus, verse, chorus, bridge, final chorus, outro.
        final int[] cycle={0,1,2,1,2,3,2,4};
        final double[] proportions={.08,.17,.17,.16,.17,.09,.11,.05};
        double cumulative=0;
        int[] thresholds=new int[8];
        for(int j=0;j<8;j++){cumulative+=proportions[j];thresholds[j]=(int)Math.round(bars*cumulative);}
        int[][] movements={{0,7,9,5},{0,5,7,9},{0,9,5,7},{0,7,5,7}};
        for(int bar=0;bar<bars;bar++) {
            int block=0;while(block<7&&bar>=thresholds[block])block++;
            sections[bar]=cycle[block];
            int[] movement=movements[style];
            chords[bar]=(root+movement[(bar/2+(block==5?1:0))%4])%12;
        }
        int steps=bars*8;
        int[] notes=new int[steps];int prev=root,prevPrev=(root+7)%12;
        for(int i=0;i<steps;i++){
            int note=brain.sample(prevPrev,prev,r);
            int chord=chords[Math.min(bars-1,i/8)];
            // Conservative music theory guard on top of the learned symbolic note transition.
            if(i%2==0 && r.nextDouble()<0.58){
                int[] triad={chord,(chord+4)%12,(chord+7)%12};
                int best=triad[0],dist=12;
                for(int n:triad){int d=Math.min((note-n+12)%12,(n-note+12)%12);if(d<dist){best=n;dist=d;}}
                note=best;
            }
            if(i%8==0 && r.nextDouble()<.55)note=chord;
            notes[i]=note;
            prevPrev=prev;prev=note;
        }
        return new Plan(bpm,seconds,root,style,seed,notes,chords,sections);
    }
}