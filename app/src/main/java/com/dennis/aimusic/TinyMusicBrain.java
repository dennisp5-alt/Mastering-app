package com.dennis.aimusic;

import java.util.List;
import java.util.Random;

/** Small independently implemented neural network trained locally on symbolic dominant-chroma sequences.
 * Two one-hot previous pitch classes -> 32 ReLU hidden units -> 12 pitch-class probabilities.
 * This is NOT an audio-generation foundation model.
 */
public final class TinyMusicBrain {
    private static final int INPUT = 24, HIDDEN = 32, OUTPUT = 12;
    private final double[][] w1 = new double[HIDDEN][INPUT];
    private final double[] b1 = new double[HIDDEN];
    private final double[][] w2 = new double[OUTPUT][HIDDEN];
    private final double[] b2 = new double[OUTPUT];
    private final double[] prior = new double[12];
    private boolean trained;
    private int examples;

    public TinyMusicBrain() {
        Random r = new Random(37411);
        for (int j=0;j<HIDDEN;j++) for (int i=0;i<INPUT;i++) w1[j][i] = r.nextGaussian()*0.10;
        for (int k=0;k<OUTPUT;k++) for (int j=0;j<HIDDEN;j++) w2[k][j] = r.nextGaussian()*0.10;
        for (int i=0;i<12;i++) prior[i] = 1./12.;
    }
    public boolean isTrained() { return trained; }
    public int getExamples() { return examples; }

    /** Train the tiny network from the whole local library; deterministic, with clipped gradients. */
    public void fit(List<int[]> sequences) {
        int count=0;
        double[] hist = new double[12];
        for (int[] seq : sequences) if(seq != null) {
            for (int v : seq) if (v>=0 && v<12) hist[v]++;
            count += Math.max(0,seq.length-2);
        }
        examples=count;
        if(count<8) return;
        double sum=0; for(double d:hist) sum+=d;
        for(int i=0;i<12;i++) prior[i]=(hist[i]+0.5)/(sum+6);
        final double learningRate=0.020;
        double[] h=new double[HIDDEN], z=new double[HIDDEN], logits=new double[OUTPUT], dh=new double[HIDDEN];
        // Iterate without copying training data or allocating per-example objects.
        for(int epoch=0;epoch<9;epoch++) {
            for(int[] seq:sequences) {
                if(seq==null) continue;
                for(int t=2;t<seq.length;t++) {
                    int a=seq[t-2], b=seq[t-1], target=seq[t];
                    if(a<0||a>11||b<0||b>11||target<0||target>11) continue;
                    for(int j=0;j<HIDDEN;j++) {
                        z[j]=w1[j][a]+w1[j][12+b]+b1[j];
                        h[j]=Math.max(0,z[j]);
                    }
                    double maximum=-1e100;
                    for(int k=0;k<OUTPUT;k++) {
                        double v=b2[k]; for(int j=0;j<HIDDEN;j++) v+=w2[k][j]*h[j];
                        logits[k]=v; maximum=Math.max(maximum,v);
                    }
                    double denom=0;
                    for(int k=0;k<OUTPUT;k++){ logits[k]=Math.exp(logits[k]-maximum); denom+=logits[k]; }
                    for(int j=0;j<HIDDEN;j++) dh[j]=0;
                    for(int k=0;k<OUTPUT;k++) {
                        double grad=(logits[k]/denom - (k==target?1:0));
                        grad=Math.max(-1,Math.min(1,grad));
                        for(int j=0;j<HIDDEN;j++) {
                            dh[j]+=grad*w2[k][j];
                            w2[k][j]-=learningRate*(grad*h[j]+0.0001*w2[k][j]);
                        }
                        b2[k]-=learningRate*grad;
                    }
                    for(int j=0;j<HIDDEN;j++) {
                        double g=z[j]>0 ? Math.max(-1,Math.min(1,dh[j])) : 0;
                        w1[j][a]-=learningRate*g;
                        w1[j][12+b]-=learningRate*g;
                        b1[j]-=learningRate*g;
                    }
                }
            }
        }
        trained=true;
    }
    public int sample(int first, int second, Random random) {
        if(!trained) return random.nextInt(12);
        first=Math.floorMod(first,12); second=Math.floorMod(second,12);
        double[] h=new double[HIDDEN];
        for(int j=0;j<HIDDEN;j++) h[j]=Math.max(0,w1[j][first]+w1[j][12+second]+b1[j]);
        double[] p=new double[OUTPUT];double max=-Double.MAX_VALUE;
        for(int k=0;k<OUTPUT;k++) {
            double v=b2[k]; for(int j=0;j<HIDDEN;j++) v+=w2[k][j]*h[j];
            p[k]=v*0.75+Math.log(prior[k])*0.25;max=Math.max(max,p[k]);
        }
        double sum=0;for(int k=0;k<OUTPUT;k++){p[k]=Math.exp(p[k]-max);sum+=p[k];}
        double r=random.nextDouble()*sum;
        for(int k=0;k<OUTPUT;k++) {r-=p[k];if(r<=0)return k;}
        return 11;
    }
}