package com.dennis.aimusic;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/** On-device symbolic training data, no uploaded recordings retained. */
public final class LibraryStore {
    private static final int MAGIC=0x444D5331; // DMS1
    private static final int MAX_SONGS=75;
    private final File file;
    private final List<WavAnalyzer.SongProfile> songs = new ArrayList<>();
    public LibraryStore(File privateDir) throws IOException {
        file = new File(privateDir,"music_dna_v1.bin");load();
    }
    public int size() {return songs.size();}
    public List<WavAnalyzer.SongProfile> tracks() {return new ArrayList<>(songs);}
    public void add(WavAnalyzer.SongProfile profile) throws IOException {
        if(songs.size()>=MAX_SONGS)throw new IOException("Library limit of 75 tracks reached in v0.1");
        songs.add(profile);save();
    }
    public void clear() throws IOException {songs.clear();save();}
    public void save() throws IOException {
        File tmp=new File(file.getParentFile(),file.getName()+".tmp");
        try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))){
            out.writeInt(MAGIC);out.writeInt(songs.size());
            for(WavAnalyzer.SongProfile p:songs) {
                out.writeUTF(p.name.length()>200?p.name.substring(0,200):p.name);
                for(int i=0;i<12;i++)out.writeFloat(p.chroma[i]);
                out.writeInt(p.notes.length);
                for(int n:p.notes)out.writeByte(n);
            }
        }
        if(file.exists()&&!file.delete())throw new IOException("Could not replace training library");
        if(!tmp.renameTo(file))throw new IOException("Could not finish saving training library");
    }
    /** Explicit user-controlled backup so experimental APK signing changes cannot destroy music DNA. */
    public void backup(OutputStream target) throws IOException {
        save();
        try(InputStream in=new BufferedInputStream(new FileInputStream(file))) {
            byte[] buf=new byte[16384];int count;
            while((count=in.read(buf))!=-1)target.write(buf,0,count);
            target.flush();
        }
    }
    /** Restore only after validating a whole backup, then atomically write private library. */
    public void restore(InputStream backup) throws IOException {
        ArrayList<WavAnalyzer.SongProfile> restored=new ArrayList<>();
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(backup))) {
            if(in.readInt()!=MAGIC)throw new IOException("Not a Dennis Music DNA backup");
            int count=in.readInt();
            if(count<0||count>MAX_SONGS)throw new IOException("Invalid backup track count");
            for(int i=0;i<count;i++){
                String name=in.readUTF();float[] chroma=new float[12];
                for(int c=0;c<12;c++){
                    float v=in.readFloat();
                    if(!Float.isFinite(v)||v<0||v>1)throw new IOException("Invalid backup chroma");
                    chroma[c]=v;
                }
                int length=in.readInt();
                if(length<10||length>30000)throw new IOException("Invalid backup sequence length");
                int[] notes=new int[length];
                for(int j=0;j<length;j++){
                    notes[j]=in.readUnsignedByte();
                    if(notes[j]>11)throw new IOException("Invalid backup note value");
                }
                restored.add(new WavAnalyzer.SongProfile(name,notes,chroma));
            }
            if(in.read()!=-1)throw new IOException("Unexpected backup trailing data");
        }
        songs.clear();songs.addAll(restored);save();
    }
    private void load() throws IOException {
        if(!file.exists())return;
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            if(in.readInt()!=MAGIC)throw new IOException("Invalid training-library version");
            int count=in.readInt();if(count<0||count>MAX_SONGS)throw new IOException("Corrupt library count");
            for(int j=0;j<count;j++){
                String name=in.readUTF();float[] dist=new float[12];
                for(int i=0;i<12;i++)dist[i]=in.readFloat();
                int n=in.readInt();if(n<0||n>30000)throw new IOException("Corrupt training sequence");
                int[] notes=new int[n];for(int i=0;i<n;i++)notes[i]=in.readUnsignedByte();
                songs.add(new WavAnalyzer.SongProfile(name,notes,dist));
            }
        }
    }
}