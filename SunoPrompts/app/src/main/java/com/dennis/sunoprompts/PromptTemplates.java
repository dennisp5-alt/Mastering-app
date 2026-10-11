package com.dennis.sunoprompts;

import java.util.Locale;

final class PromptTemplates {
    static PromptStore.Prompt starter(String artist,String song,String notes,ResearchClient.Research research) {
        PromptStore.Prompt p=new PromptStore.Prompt();
        p.artist=artist;p.song=song;p.mode="RESEARCH STARTER — NO AI";
        String a=artist.toLowerCase(Locale.ROOT),s=song.toLowerCase(Locale.ROOT);
        String musical="Organically performed ensemble, believable timing, deliberate musical interaction, contrasting verse and chorus.";
        String tone="Natural full-range studio mix, stable centre image, distinct registers, clear vocal space, controlled low mids, no artificial sheen.";
        String voice="Expressive and human lead vocal with conversational phrasing and natural breaths.";
        if(a.contains("ac/dc") || a.equals("acdc")) {
            musical="Classic guitar-driven hard rock: two complementary distorted electric guitars, incisive blues-rooted riffing, sturdy electric bass and firm live-kit backbeat. Open chord voicings, spacious transitions and a strong chorus lift.";
            voice="Raw chest-voice rock singing, sharply enunciated phrasing and assertive call-and-response energy.";
            tone="Dry, punchy late-1970s/early-1980s analogue-rock feel; tightly anchored kick, bass and lead vocal; guitar interplay spread left and right; room ambience and natural transients.";
            if(s.equals("thunderstruck"))musical="Build from a bright repeated high-register guitar figure; introduce staggered instrumental layers, then release into a weighty four-on-the-floor rock pulse and broad guitar responses. No melody copying.";
            else if(s.equals("back in black"))musical="Start with a stripped, syncopated electric-guitar riff and purposeful rests; lock dry drums and bass into the gaps, add complementary guitar stabs and a bigger chorus without crowding. No melody copying.";
            else if(s.equals("highway to hell"))musical="Driving mid-tempo electric-guitar chord accents, strong snare backbeat, relaxed bass support and a broad communal rock-chorus lift. No melody copying.";
        } else if(a.contains("slim dusty")) {
            musical="Australian bush-country story song with relaxed train-beat drums, upright-feeling electric bass, acoustic strumming, measured twang electric fills, gentle pedal steel and fiddle between lines.";
            voice="Plain-spoken weathered Australian baritone, conversational storytelling and warm restrained expression.";
            tone="Intimate vintage band-in-a-room recording, gentle plate echo, modest compression, natural stereo balance.";
        } else if(a.contains("dire straits")) {
            musical="Understated roots-rock: articulate fingerpicked electric guitar, spare drums, warm bass and melodic keyboard shading, with instrumental replies occupying different registers.";
            voice="Close, understated narrative male lead with spacious phrasing.";
            tone="Transparent spacious analogue studio sound; guitar detail and musical dynamics take priority over loudness.";
        } else if(a.contains("duran duran")) {
            musical="Polished early-1980s dance-rock: syncopated bass guitar, tightly gated drums, chiming synth hooks, clean rhythm guitar and evolving keyboard counterlines.";
            voice="Confident melodic new-wave lead with carefully layered chorus harmonies.";
            tone="Wide and clear 1980s-inspired stereo imaging, snappy percussion, firm centre bass and separated synth registers.";
        }
        String style="Song-inspired original production brief for \""+song+"\". "+musical+" "+voice+" "+tone+
            (notes.isBlank()?"":" Direction: "+notes.trim())+" Original melody and lyrics; focus on arrangement and production character.";
        p.style=shorten(style,1000);
        p.exclude="Copied melody or lyrics, soundalike vocal cloning, robotic timing, metallic cymbals, over-compression, smeared low mids, harsh digital distortion, synthetic AI vocal artifacts.";
        p.analysis="Starter prompt: no audio has been analysed. General stylistic choices are educated directions, not verified track-by-track acoustic facts.\n\n"+
                research.context();
        p.sources=research.sources;
        return p;
    }
    static String shorten(String s,int limit) {
        if(s.length()<=limit)return s;
        int cut=s.lastIndexOf(' ',limit);
        return s.substring(0,cut>limit/2?cut:limit).trim();
    }
}
