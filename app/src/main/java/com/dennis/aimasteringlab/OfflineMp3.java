package com.dennis.aimasteringlab;
import java.io.*;
import de.sciss.jump3r.mp3.*;
import de.sciss.jump3r.mpg.*;

/** Bundled LGPL Jump3r/LAME core. No desktop APIs, subprocess or network. */
final class OfflineMp3 {
    static final class Encoded {int delay,padding;long frames;}
    private static final class Core {
        final Lame lame=new Lame();final MPGLib mpg=new MPGLib();final VBRTag tag=new VBRTag();final ID3Tag id3=new ID3Tag();
        Core(){
            GainAnalysis ga=new GainAnalysis();BitStream bs=new BitStream();Presets p=new Presets();
            QuantizePVT qp=new QuantizePVT();Quantize q=new Quantize();Version ver=new Version();
            Reservoir rv=new Reservoir();Takehiro tk=new Takehiro();Common common=new Common();Interface intf=new Interface();
            lame.setModules(ga,bs,p,qp,q,tag,ver,id3,mpg);bs.setModules(ga,mpg,ver,tag);id3.setModules(bs,ver);
            p.setModules(lame);q.setModules(bs,rv,qp,tk);qp.setModules(tk,rv,lame.enc.psy);rv.setModules(bs);
            tk.setModules(qp);tag.setModules(lame,bs,ver);mpg.setModules(intf,common);intf.setModules(tag,common);
        }
    }
    static Encoded encode(File wav,File mp3,String title,double gainDb,AudioAnalyzer.Progress progress)throws IOException{
        return encode(wav,mp3,title,gainDb,progress,null);
    }
    static Encoded encode(File wav,File mp3,String title,double gainDb,AudioAnalyzer.Progress progress,StudioRecipe metadata)throws IOException{
        Core c=new Core();LameGlobalFlags f=c.lame.lame_init();Encoded result=new Encoded();
        try(WavFile.Reader reader=WavFile.open(new FileInputStream(wav));RandomAccessFile out=new RandomAccessFile(mp3,"rw")){
            if(reader.info.sampleRate!=44100||reader.info.channels!=2||reader.info.bitsPerSample!=16)throw new IOException("MP3 requires verified 16-bit/44.1 kHz stereo");
            out.setLength(0);f.num_channels=2;f.in_samplerate=44100;f.out_samplerate=44100;f.brate=320;
            f.VBR=VbrMode.vbr_off;f.mode=MPEGMode.JOINT_STEREO;f.quality=2;f.bWriteVbrTag=true;
            f.findReplayGain=false;f.write_id3tag_automatic=true;f.num_samples=(int)Math.min(Integer.MAX_VALUE,reader.info.frames);
            c.id3.id3tag_init(f);c.id3.id3tag_add_v2(f);c.id3.id3tag_set_title(f,title);
            if(metadata!=null){
                c.id3.id3tag_set_artist(f,metadata.artist);c.id3.id3tag_set_album(f,metadata.album);
                c.id3.id3tag_set_track(f,Integer.toString(metadata.track));
                if(!metadata.isrc.isEmpty())c.id3.id3tag_set_fieldvalue(f,"TSRC="+metadata.isrc.toUpperCase(java.util.Locale.US));
            }
            if(c.lame.lame_init_params(f)<0)throw new IOException("MP3 encoder initialization failed");
            float[][] pcm=new float[2][1152];int[] l=new int[1152],r=new int[1152];byte[] encoded=new byte[32768];
            double gain=Math.pow(10,gainDb/20);int n;long frames=0;
            while((n=reader.readFrames(pcm,1152))>0){
                for(int i=0;i<n;i++){l[i]=(int)Math.round(pcm[0][i]*gain*2147483648.0);r[i]=(int)Math.round(pcm[1][i]*gain*2147483648.0);}
                int size=c.lame.lame_encode_buffer_int(f,l,r,n,encoded,0,encoded.length);
                if(size<0)throw new IOException("MP3 encoding failed: "+size);out.write(encoded,0,size);frames+=n;
                progress.update("Encoding offline MP3 320 kbps",(int)(frames*100/reader.info.frames));cancel();
            }
            int size=c.lame.lame_encode_flush(f,encoded,0,encoded.length);
            if(size<0)throw new IOException("MP3 encoder flush failed");out.write(encoded,0,size);
            writeGaplessTag(c,f,out);
            result.delay=f.encoder_delay;result.padding=f.encoder_padding;result.frames=frames;out.getFD().sync();
        }finally{c.lame.lame_close(f);}
        verifyFrames(mp3);return result;
    }
    // Upstream 1.0.5 putVbrTag has an inverted ID3-present check. Keep vendor
    // sources unchanged and write its generated frame at the parsed audio offset.
    private static void writeGaplessTag(Core c,LameGlobalFlags f,RandomAccessFile out)throws IOException{
        out.seek(0);byte[] header=new byte[10];out.readFully(header);long offset=0;
        if(header[0]=='I'&&header[1]=='D'&&header[2]=='3')
            offset=10+((header[6]&127)<<21)+((header[7]&127)<<14)+((header[8]&127)<<7)+(header[9]&127);
        byte[] tag=new byte[2880];int n=c.tag.getLameTagFrame(f,tag);
        if(n<1||n>tag.length||offset+n>out.length())throw new IOException("MP3 gapless header size invalid");
        out.seek(offset);out.write(tag,0,n);
    }
    static void decode(File mp3,Encoded info,File pcm,AudioAnalyzer.Progress progress)throws IOException{
        Core c=new Core();MPGLib.mpstr_tag state=c.mpg.hip_decode_init();long skip=info.delay+529L,seen=0,written=0;
        try(InputStream in=new BufferedInputStream(new FileInputStream(mp3));PcmExportWriter out=new PcmExportWriter(pcm,44100,32,info.frames)){
            byte[] bytes=new byte[4096];float[] l=new float[1152],r=new float[1152];int length;long read=0;
            while((length=in.read(bytes))>=0){
                if(length==0)continue;read+=length;int n=c.mpg.hip_decode1_unclipped(state,bytes,0,length,l,r);
                do{
                    if(n<0)throw new IOException("MP3 decoder rejected encoded data");
                    for(int i=0;i<n;i++,seen++)if(seen>=skip&&written<info.frames){out.frame(l[i]/32768.0,r[i]/32768.0);written++;}
                    if(n==0)break;n=c.mpg.hip_decode1_unclipped(state,bytes,0,0,l,r);
                }while(true);
                progress.update("Verifying decoded MP3",(int)Math.min(100,read*100/mp3.length()));cancel();
            }
            int n;
            while((n=c.mpg.hip_decode1_unclipped(state,bytes,0,0,l,r))>0){
                for(int i=0;i<n;i++,seen++)if(seen>=skip&&written<info.frames){out.frame(l[i]/32768.0,r[i]/32768.0);written++;}cancel();
            }
            if(n<0||written!=info.frames||seen<skip+info.frames)throw new IOException("MP3 decoded duration mismatch");
        }finally{c.mpg.hip_decode_exit(state);}
    }
    static void verifyFrames(File mp3)throws IOException{
        try(RandomAccessFile f=new RandomAccessFile(mp3,"r")){
            long p=0;if(f.length()<10)throw new IOException("Empty MP3");byte[] h=new byte[10];f.readFully(h);
            if(h[0]=='I'&&h[1]=='D'&&h[2]=='3')p=10+((h[6]&127)<<21)+((h[7]&127)<<14)+((h[8]&127)<<7)+(h[9]&127);
            int count=0;
            while(p+4<=f.length()){
                if(f.length()-p==128){f.seek(p);if(f.read()=='T'&&f.read()=='A'&&f.read()=='G'){p+=128;break;}}
                f.seek(p);int h32=f.readInt();
                if((h32&0xffe00000)!=0xffe00000||((h32>>>19)&3)!=3||((h32>>>17)&3)!=1||((h32>>>12)&15)!=14||((h32>>>10)&3)!=0||((h32>>>6)&3)==3)
                    throw new IOException("MP3 is not complete 320 kbps CBR/44.1 kHz stereo at byte "+p+" frame "+count+" header "+Integer.toHexString(h32));
                p+=144000*320/44100+((h32>>>9)&1);count++;
            }
            if(p!=f.length()||count<2)throw new IOException("MP3 frame listing is truncated");
        }
    }
    private static void cancel()throws IOException{if(Thread.currentThread().isInterrupted())throw new IOException("MP3 export cancelled");}
}
