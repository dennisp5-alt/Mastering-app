package com.dennis.sunoprompts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

/**
 * Zero-cost text heuristics; not a beat tracker, melody transcription, or
 * automated music critic. Used to expose obvious density/structure mismatches
 * before a user spends ChatGPT plan allowance on a voluntary rewrite.
 */
final class LyricRhythmCoach {
    private LyricRhythmCoach(){}
    static final class Report {
        String mode="",summary="",promptAdvice="";
        int verseLines,chorusLines,bridgeLines,estimatedVerseSyllables,estimatedChorusSyllables;
    }

    static String suggest(String production) {
        String s=norm(production);
        if(danceRock(s))return "Tight syncopated verses; brief stressed phrases, breath gaps and percussive consonants. Chorus: short bold calls with answering group shouts, repeatable original hook and open vowel endings. Keep the story moving.";
        if(s.contains("disco")||s.contains("eurodance")||s.contains("four-on-the-floor"))
            return "Club-locked rhythm with clear stressed words, short melodic chorus hook, compact repeated responses, plenty of breathing room between rhythmic phrases.";
        if(s.contains("country")||s.contains("bush-ballad")||s.contains("nashville"))
            return "Narrative conversational verses that sit naturally on the two-beat/rolling groove. Short singable chorus, concrete images and a memorable warm melodic hook.";
        if(s.contains("rap")||s.contains("hip-hop")||s.contains("808"))
            return "Rhythm-forward conversational phrasing, intentional bar-length variations, internal rhyme used sparingly and short hook repeats. Leave room around the strongest words.";
        if(s.contains("hard rock")||s.contains("stadium"))
            return "Forceful clipped verse phrasing, breath gaps before the backbeat, memorable shouted response lines, wider held vowels in the chorus.";
        if(s.contains("ballad")||s.contains("slow-burn"))
            return "Patient emotional phrasing, conversational verse timing, restrained rhyme, long open chorus vowels, strategic silences and an earned final lift.";
        return "Prioritise believable vocal breath lengths, strong accents on the musical groove, distinct verse and chorus rhythm, a compact original hook and human phrasing.";
    }
    static boolean danceRock(String lower){
        return lower.contains("dance-rock")||lower.contains("dance rock")||
            lower.contains("new wave")||lower.contains("new-wave")||
            lower.contains("tribal tom")||lower.contains("pounding tom")||
            lower.contains("duran duran")||lower.contains("wild boys");
    }
    private static String norm(String t){return t==null?"":t.toLowerCase(Locale.ROOT);}
    static String focus(String production){
        String s=norm(production);
        if(danceRock(s))return "PERCUSSIVE DANCE-ROCK: chord/drum-accent synchronisation, clipped declarative verses, tension-building rests, short shouted call/response chorus and a hook that can be chanted in one breath. Do not turn it into a long country-style narrative refrain.";
        if(s.contains("country")||s.contains("bush-ballad"))
            return "STORY-FIRST COUNTRY: naturally stressed concrete language, couplets that sing without rushing, chorus lift through open vowels and a straightforward melodic hook; avoid shout-heavy stadium chant unless asked.";
        if(s.contains("disco")||s.contains("dance")||s.contains("euro"))
            return "DANCE-POP: strong pulse accents, compact verse phrases and spaced hooks, rhythmic repetition, singable vowel endings and chorus-backbeat interplay.";
        if(s.contains("rock")||s.contains("metal"))
            return "ROCK: tight breath-aware verse lines, aggressive stress placement, gaps for guitar replies, bold concise chorus hook and energetic bridge contrast.";
        if(s.contains("rap")||s.contains("hip-hop"))
            return "RAP-SUNG: clear 4-bar/8-bar phrases, internal accents, contrasting short melodic hook and natural spoken breath placement.";
        return "GENERAL SONGWRITING: revise syllabic density against the chosen groove, differentiate chorus rhythms and retain narrative development.";
    }
    static Report inspect(String lyrics,String production){
        Report r=new Report();
        r.mode=focus(production);
        String section="";
        ArrayList<Integer> verses=new ArrayList<>(),choruses=new ArrayList<>();
        ArrayList<String> concerns=new ArrayList<>();
        int longChorus=0,longVerse=0;
        for(String raw:(lyrics==null?"":lyrics).split("\\r?\\n")){
            String line=raw.trim();
            if(line.isEmpty())continue;
            if(line.startsWith("[")&&line.endsWith("]")){
                String tag=line.toLowerCase(Locale.ROOT);
                if(tag.contains("chorus")||tag.contains("hook"))section="chorus";
                else if(tag.contains("verse"))section="verse";
                else if(tag.contains("bridge"))section="bridge";
                else section="other";
                continue;
            }
            if(line.startsWith("(")&&line.endsWith(")"))continue;
            int syllables=syllables(line);
            if(section.equals("verse")){verses.add(syllables);r.verseLines++;if(syllables>15)longVerse++;}
            if(section.equals("chorus")){choruses.add(syllables);r.chorusLines++;if(syllables>12)longChorus++;}
            if(section.equals("bridge"))r.bridgeLines++;
        }
        r.estimatedVerseSyllables=median(verses);
        r.estimatedChorusSyllables=median(choruses);
        if(verses.isEmpty())concerns.add("No [Verse] lines detected");
        if(choruses.isEmpty())concerns.add("No [Chorus] lines detected");
        if(longVerse>0)concerns.add(longVerse+" verse line(s) may need extra breathing space (>15 estimated syllables)");
        if(longChorus>0)concerns.add(longChorus+" chorus line(s) are dense (>12 estimated syllables)");
        if(!choruses.isEmpty()&&!verses.isEmpty() && danceRock(norm(production)) &&
            r.estimatedChorusSyllables>=r.estimatedVerseSyllables)
            concerns.add("Dance-rock chorus is not shorter than the verse on median; consider stronger, simpler chant phrasing");
        if(choruses.size()>0 && choruses.size()<4)concerns.add("Very short chorus; check that the title hook is clear");
        if(concerns.isEmpty())concerns.add("No obvious text-density warning; listen for actual stress, melody and groove after Suno renders it");
        r.summary="ADVISORY TEXT CHECK — NOT AUDIO MEASUREMENT\n\n"+
            "Production target: "+r.mode+"\n\n"+
            "Verse lines: "+r.verseLines+"; median ~"+r.estimatedVerseSyllables+" estimated syllables\n"+
            "Chorus lines: "+r.chorusLines+"; median ~"+r.estimatedChorusSyllables+" estimated syllables\n"+
            "Bridge lines: "+r.bridgeLines+"\n\n"+
            "Things to review:\n• "+String.join("\n• ",concerns)+
            "\n\nSyllables are only approximate (English pronunciation, sung pickups and melisma vary). "+
            "A text check cannot determine pitch, note placement, exact bar alignment or whether a Suno generation sings naturally.";
        r.promptAdvice="Estimated syllable medians: verse "+r.estimatedVerseSyllables+
            ", chorus "+r.estimatedChorusSyllables+". "+String.join("; ",concerns)+
            ". These are advisory text estimates, NOT true audio or melody measurements.";
        return r;
    }
    private static int median(ArrayList<Integer> list){
        if(list.isEmpty())return 0;
        ArrayList<Integer> ordered=new ArrayList<>(list);
        Collections.sort(ordered);return ordered.get(ordered.size()/2);
    }
    private static int syllables(String line){
        int sum=0;
        for(String token:line.split("\\s+")){
            String word=token.toLowerCase(Locale.ROOT).replaceAll("[^a-z]","");
            if(word.isEmpty())continue;
            if(word.length()<4){sum++;continue;}
            String normalized=word.replaceAll("e$", "");
            if(normalized.isEmpty())normalized=word;
            String groups=normalized.replaceAll("[^aeiouy]+"," ").trim();
            int n=groups.isEmpty()?1:groups.split(" +").length;
            if(word.endsWith("le") && word.length()>3 && !"aeiou".contains(String.valueOf(word.charAt(word.length()-3))))n++;
            sum+=Math.max(1,n);
        }
        return sum;
    }
}
