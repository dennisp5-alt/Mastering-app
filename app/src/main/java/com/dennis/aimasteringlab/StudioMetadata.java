package com.dennis.aimasteringlab;
import java.io.*;
import java.nio.charset.StandardCharsets;

final class StudioMetadata {
    static void wav(File f,StudioRecipe r)throws IOException{
        ByteArrayOutputStream info=new ByteArrayOutputStream();LittleEndian.writeAscii(info,"INFO");
        item(info,"INAM",r.title);item(info,"IART",r.artist);item(info,"IPRD",r.album);item(info,"ITRK",Integer.toString(r.track));
        item(info,"ISRC",r.isrc);item(info,"ISFT","Dennis AI Mastering Lab 0.14.5");
        byte[] payload=info.toByteArray();
        try(RandomAccessFile out=new RandomAccessFile(f,"rw")){
            long size=out.length()+8+payload.length;
            if(size-8>0xffffffffL)throw new IOException("WAV metadata would exceed RIFF size limit");
            out.seek(out.length());out.writeBytes("LIST");u32(out,payload.length);out.write(payload);
            out.seek(4);u32(out,size-8);out.getFD().sync();
        }
    }
    private static void item(OutputStream out,String key,String value)throws IOException{
        if(value==null||value.isEmpty())return;byte[] b=(value+"\0").getBytes(StandardCharsets.UTF_8);
        LittleEndian.writeAscii(out,key);LittleEndian.writeU32(out,b.length);out.write(b);if((b.length&1)!=0)out.write(0);
    }
    private static void u32(RandomAccessFile out,long v)throws IOException{for(int i=0;i<4;i++)out.write((int)(v>>>(8*i))&255);}
}
