package com.dennis.aimasteringlab;

import java.io.*;
import java.nio.file.*;

/** Reproduces Android's /data/user/0 -> /data/data storage alias. */
public final class StudioImportPathHarness {
    public static void main(String[] args)throws Exception {
        Path work=Paths.get(args[0]);Files.createDirectories(work);
        Path actual=work.resolve("data/data/app/files/studio-projects");Files.createDirectories(actual);
        Path user=work.resolve("data/user");Files.createDirectories(user);
        Files.createSymbolicLink(user.resolve("0"),Paths.get("../data"));
        File root=user.resolve("0/app/files/studio-projects").toFile();
        StudioProject p=StudioProject.create(root,"Android import path");
        File source=p.child("source.wav");Files.write(source.toPath(),new byte[]{1,2,3});
        p.file("source",source);p.data.put("source_hash",Hashing.sha256(source));p.save();
        if(!p.data.getString("source").equals("source.wav"))throw new AssertionError("Saved source path is not project-relative");
        p=StudioProject.load(new File(root,p.dir.getName()));
        if(!p.validFile("source","source_hash"))throw new AssertionError("Source was lost after aliased-path reload");
        System.out.println("STUDIO_ANDROID_PATH_ALIAS_PASS");
    }
}
