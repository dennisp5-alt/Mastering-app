package com.dennis.aimasteringlab;
import java.io.*;
import java.util.Random;

/** PCM derivative writer. High-pass triangular dither is applied once at 16 bits. */
final class PcmExportWriter implements AutoCloseable {
    private final FileOutputStream file;private final OutputStream out;
    private final int bits;private final long expected;
    private final Random random=new Random(0x44414d4c31324cL);
    private final double[] previous={0,0};long frames,clips;
    PcmExportWriter(File path,int rate,int bits,long frames)throws IOException{
        this.bits=bits;this.expected=frames;long bytes=frames*2*(bits/8);
        if(frames<0||bytes>0xffffffffL-36||!(bits==16||bits==32))throw new IOException("Invalid export size/format");
        file=new FileOutputStream(path);out=new BufferedOutputStream(file,262144);
        LittleEndian.writeAscii(out,"RIFF");LittleEndian.writeU32(out,36+bytes);LittleEndian.writeAscii(out,"WAVEfmt ");
        LittleEndian.writeU32(out,16);LittleEndian.writeU16(out,bits==32?3:1);LittleEndian.writeU16(out,2);
        LittleEndian.writeU32(out,rate);LittleEndian.writeU32(out,rate*2L*(bits/8));LittleEndian.writeU16(out,2*(bits/8));
        LittleEndian.writeU16(out,bits);LittleEndian.writeAscii(out,"data");LittleEndian.writeU32(out,bytes);
    }
    void frame(double l,double r)throws IOException{sample(l,0);sample(r,1);frames++;}
    private void sample(double x,int c)throws IOException{
        if(!Double.isFinite(x))throw new IOException("Non-finite export sample");
        if(bits==32){LittleEndian.writeU32(out,Float.floatToRawIntBits((float)x)&0xffffffffL);return;}
        double u=random.nextDouble()-.5,d=u-previous[c];previous[c]=u;
        long q=Math.round(x*32768+d);if(q<-32768||q>32767)clips++;
        LittleEndian.writeU16(out,(int)Math.max(-32768,Math.min(32767,q))&65535);
    }
    public void close()throws IOException{
        try{out.flush();file.getFD().sync();}finally{out.close();}
        if(frames!=expected)throw new IOException("Export frame count mismatch: "+frames+" / "+expected);
    }
}
