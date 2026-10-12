package com.dennis.sunoprompts;

import org.json.JSONObject;

/**
 * Original songwriting engine: AI is optional and charged to eligible ChatGPT
 * shared plan allowance. No copyrighted lyrics or audio are retrieved.
 */
final class LyricsClient {
    static final class Brief {
        String artist="",title="",story="",hook="",genre="",structure="",
            rhyme="",vocal="",avoid="",styleGuide="",referenceTrack="",rhythm="";
        String toJson(String notes){
            JSONObject j=new JSONObject();
            try{
                j.put("artist",artist);j.put("title",title);j.put("story",story);
                j.put("hook",hook);j.put("genre",genre);j.put("structure",structure);
                j.put("rhyme",rhyme);j.put("vocal",vocal);j.put("avoid",avoid);
                j.put("styleGuide",styleGuide);j.put("referenceTrack",referenceTrack);
                j.put("rhythm",rhythm);j.put("notes",notes);
            }catch(Exception ignored){}
            return j.toString();
        }
        static Brief fromJson(String saved){
            Brief b=new Brief();
            try{
                JSONObject j=new JSONObject(saved);
                b.artist=j.optString("artist","");
                b.title=j.optString("title","");
                b.story=j.optString("story","");
                b.hook=j.optString("hook","");
                b.genre=j.optString("genre","");
                b.structure=j.optString("structure","");
                b.rhyme=j.optString("rhyme","");
                b.vocal=j.optString("vocal","");
                b.avoid=j.optString("avoid","");
                b.styleGuide=j.optString("styleGuide","");
                b.referenceTrack=j.optString("referenceTrack","");
                b.rhythm=j.optString("rhythm","");
            }catch(Exception ignored){}
            return b;
        }
        String describe(){
            return "ORIGINAL song title: "+title+
                "\nFolder / reference artist (not an instruction to copy): "+artist+
                "\nReference recording for musical energy only: "+referenceTrack+
                "\nStory and setting: "+story+
                "\nOriginal chorus hook idea: "+hook+
                "\nGenre and mood: "+genre+
                "\nSong structure: "+structure+
                "\nRhyme and line pacing: "+rhyme+
                "\nLead vocal: "+vocal+
                "\nAvoid: "+avoid+
                "\nPerformance/rhythmic attack: "+rhythm+
                "\nMusical production DNA: "+(styleGuide.isEmpty()?"Not supplied":styleGuide);
        }
        String combinedProduction(){
            return styleGuide+" "+genre+" "+rhythm+" "+artist+" "+referenceTrack;
        }
    }

    private static final String RULES =
        "You are a senior songwriter, topline specialist and music director. Write totally ORIGINAL, rhythmically performable lyrics for a professional Suno v6 session. "+
        "This is a songwriting commission, not a request to recreate any original recording's lyrics or melody. A reference artist or song can inform high-level groove, vocal energy, and instrument-response space but NOT existing words, title hook, rhymes, unique slogans or melodies. "+
        "Prioritise the supplied STORY: specific people, location, details, plot progression, point of view and emotional stakes. Do not replace it with vague genre imagery or hackneyed stock phrases. "+
        "Make the song actually FIT THE PRODUCTION: sync lyric attacks with likely kick/snare/backbeat and vocal phrase lengths inferred from described groove, leave breaths and openings for instrument answers, and differentiate verse/pre/chorus/bridge density. "+
        "For aggressive 1980s/new-wave dance-rock: verses may use clipped rhythmic calls with rests, but the CHORUS must open out dynamically. Alternate urgent short 3-6 syllable shouted lead calls with 7-12 syllable melodic answers, higher sustained vowels or a call-and-response choir. Not every line should be a 2-4 word chant. Leave space for pounding toms, bass and synth/guitar responses while preserving melodic movement and dramatic lift. Avoid country-ballad phrasing and monotonous talk-singing. "+
        "For country/bush-ballad: prefer spacious storytelling, natural inflected couplets, gentle internal rhyme, warm melodic chorus with controlled longer lines. For dance-pop: tight repeated rhythmic hooks. For intimate ballads: longer held vowels and fragile phrasing. Respect the actual supplied style above generic rules. "+
        "Avoid excessive syllables, hard-to-pronounce clusters, filler, awkward grammar, forced rhymes, unearned bridge sentiment, repetitive verse exposition, rhyming just to rhyme, and generic artificial-songwriter phrases. "+
        "Create one DISTINCT original chorus hook. Prefer a new title/hook instead of any memorable refrain from a referenced song; never reuse the reference song's title as a chanted hook unless it is independently user-authored and the result is clearly distinct. "+
        "Use only standard Suno section tags such as [Verse 1], [Pre-Chorus], [Chorus], [Verse 2], [Bridge], [Final Chorus], [Outro]. Keep singing lyrics outside tags and no explanation inside the lyrics. "+
        "Chorus must have a compelling melody-friendly contour, not simply fewer words than the verse: pair its strongest repeatable original hook with contrasting phrase lengths, rising intensity and one emotionally meaningful held line. Backing-vocal tags only if musically useful. Bridge should change perspective or emotional stakes rather than summarise the theme. "+
        "Before returning, do a SILENT PRODUCER REVIEW of every section: does it actually sound singable and emotionally strong; does each chorus soar or hit harder than the verse; does it alternate punch and melodic release; is any chant-only chorus monotonous; have original characters and key plot moments survived? If a check fails, revise your own draft once BEFORE replying. Do not output the internal review. "+
        "Return ONLY one valid JSON object with two string keys: lyrics (complete song with tags) and notes (brief explanation of beat placement, lyrical density and hook choice). No markdown fences.";

    static PromptStore.Lyrics generate(String token,String model,Brief brief)throws Exception {
        String coaching=LyricRhythmCoach.focus(brief.combinedProduction());
        String request="WRITE NEW LYRICS from this brief:\n"+brief.describe()+
            "\nPERFORMANCE TARGET:\n"+coaching+
            "\nRemember: the title and story are for a NEW song, not a cover. "+
            "Do not borrow any published hook. Make the response a finished song rather than a generic outline.";
        JSONObject result=ChatGptPrompts.completeJson(token,model,RULES,request);
        return parseResult(brief,result,model,"CHATGPT LYRICS");
    }

    /**
     * Rework ONLY the provided original draft as its author requests.
     * A revised version is always a new row and never overwrites the source.
     */
    static PromptStore.Lyrics revise(String token,String model,Brief brief,String existing,
                                    String additionalDirections)throws Exception {
        if(existing==null||existing.trim().length()<70)
            throw new Exception("Paste or write an existing draft before requesting a rhythmic rewrite.");
        if(existing.length()>25000)
            throw new Exception("Draft is too long for this revision tool.");
        LyricRhythmCoach.Report check=LyricRhythmCoach.inspect(existing,brief.combinedProduction());
        String instruction=RULES+
            " REVISION MODE: Preserve the existing draft's original characters, names, narrative events, relationship dynamics, point of view and its emotional core. "+
            "Do not invent a new story. Tighten mouthfuls, overlong lines, weak rhythm and generic filler. "+
            "Rebuild verses and chorus to fit the musical production and vocalist. "+
            "Retain strong imagery and emotional turning points. When the original hook matches a reference recording's recognisable hook, choose a fresh user-appropriate original hook without borrowing it. "+
            "Do NOT simply reduce every line to two-to-five syllables; the prior attempt made a flat almost-spoken chorus. Retain some strong short calls but create rising melodic answers and controlled held vowels, alternating brief percussive phrases with expressive medium lines. Preserve bridge meaning and narrative specificity. Critique your candidate silently against the source and musical genre, then edit before returning. This must be a meaningful performance-focused revision, not mere spelling edits. Return a complete revised song with all section tags.";
        String request="REVISE THIS USER-SUPPLIED ORIGINAL LYRIC DRAFT for real singing:\n"+
            existing+
            "\n\nPRODUCTION/SONGWRITER BRIEF:\n"+brief.describe()+
            "\n\nADVISORY LOCAL TEXT QC (not musical measurements):\n"+check.promptAdvice+
            "\n\nYOUR EDIT PRIORITIES:\n"+(additionalDirections==null?"":additionalDirections)+
            "\n\nPRODUCTION RHYTHM TARGET:\n"+LyricRhythmCoach.focus(brief.combinedProduction())+
            "\nReturn JSON object with lyrics and notes. Do not copy any reference song lyrics.";
        JSONObject result=ChatGptPrompts.completeJson(token,model,instruction,request);
        return parseResult(brief,result,model,"RHYTHM + HOOK REWORK");
    }

    private static PromptStore.Lyrics parseResult(Brief brief,JSONObject result,String model,String mode)throws Exception{
        String text=result.optString("lyrics","").trim();
        if(text.length()<70||text.length()>26000)
            throw new Exception("Lyrics are incomplete or unexpectedly large. No existing draft was changed.");
        String lower=text.toLowerCase(java.util.Locale.ROOT);
        if(!lower.contains("[verse")||!lower.contains("[chorus"))
            throw new Exception("Output has no standard [Verse] and [Chorus] tags. Existing draft is safe.");
        PromptStore.Lyrics out=new PromptStore.Lyrics();
        out.artist=brief.artist.trim().isEmpty()?"Original Songs":brief.artist.trim();
        out.song=brief.title.trim().isEmpty()?"Untitled":brief.title.trim();
        out.body=text;
        String feedback=result.optString("notes","").trim();
        out.brief=brief.toJson(feedback);
        out.mode=mode+" — "+model;
        return out;
    }
}
