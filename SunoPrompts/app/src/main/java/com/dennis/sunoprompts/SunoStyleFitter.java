package com.dennis.sunoprompts;

import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;

/**
 * Offline, zero-token, post-generation compression for Suno's <=1000-character
 * style field. Never blindly slice the AI's last sentence: retain playable
 * rhythm, musician roles, vocal delivery, arrangement changes and sonic depth.
 */
final class SunoStyleFitter {
    static final int MAX=1000;
    static final int TARGET=975;
    private SunoStyleFitter(){}

    static final class Result {
        final String style, method;
        final int originalCharacters;
        Result(String s,String m,int original){
            style=s;method=m;originalCharacters=original;
        }
        boolean condensed(){return !"unchanged".equals(method);}
    }
    private static final class Segment {
        final int order; final String text;
        final int score;
        Segment(int position,String phrase){
            order=position; text=phrase;
            score=score(phrase);
        }
    }
    static Result fit(String original,JSONObject dna,ProducerPipeline.Context context)throws Exception {
        if(original==null || original.trim().isEmpty())
            throw new Exception("AI did not supply a production style.");
        String clean=normalise(original);
        if(clean.length()<=MAX)return new Result(clean,"unchanged",clean.length());
        Result compressed=compressSentences(clean,context);
        if(compressed!=null && ProducerPipeline.quality(compressed.style,context).pass)
            return compressed;
        // Previous AI analysis is already available, so a controlled local
        // production summary can be assembled without a third billable request.
        Result reconstructed=fromMusicalDna(dna,context,clean.length());
        if(ProducerPipeline.quality(reconstructed.style,context).pass)
            return reconstructed;
        throw new Exception("The AI wrote an overlong prompt and offline condensation could not preserve its song-specific playing instructions. No existing prompt was changed. Try the free reviewed reference instead.");
    }
    private static String normalise(String src){
        return src.replaceAll("\\s+"," ").replaceAll("\\s+([,;:.!?])","$1").trim();
    }
    private static Result compressSentences(String original,ProducerPipeline.Context context){
        ArrayList<Segment> pieces=new ArrayList<>();
        String[] major=original.split("(?<=[.!?;])\\s+");
        int order=0;
        for(String majorPart:major){
            String value=majorPart.trim();
            if(value.isEmpty())continue;
            // Semicolon-free model paragraphs can still have very long sentences.
            String[] clauses=value.length()>240?value.split("(?<=,)\\s+"):new String[]{value};
            for(String c:clauses){
                String part=c.trim();
                if(!part.isEmpty())pieces.add(new Segment(order++,part));
            }
        }
        if(pieces.size()<2)return null;
        ArrayList<Segment> ranked=new ArrayList<>(pieces);
        // Prefer track-specific phrases and actionable musician directions over
        // generic polish buzzwords. Use deterministic original order for the result.
        ranked.sort((a,b)->{
            int delta=Integer.compare(b.score,a.score);
            return delta!=0?delta:Integer.compare(a.order,b.order);
        });
        HashSet<Integer> chosen=new HashSet<>();
        int used=0;
        String[] essential={
            "drum|snare|kick|brush|rhythm|beat|percussion",
            "acoustic|guitar|steel|fiddle|bass|piano|organ|synth|strum",
            "vocal|baritone|tenor|voice|singer|singing",
            "verse|refrain|chorus",
            "break|opening|intro|outro|transition"
        };
        for(String category:essential){
            Segment best=null;
            for(Segment item:ranked){
                if(chosen.contains(item.order) || !containsAny(item.text.toLowerCase(Locale.ROOT),category.split("\\|")))continue;
                if(best==null || item.score>best.score)best=item;
            }
            if(best!=null && used+best.text.length()+1<=TARGET){
                chosen.add(best.order);used+=best.text.length()+1;
            }
        }
        // Pack remaining informative clauses by value, not only their first
        // appearance (which tends to drop the mix and outro).
        for(Segment item:ranked){
            if(chosen.contains(item.order))continue;
            if(used+item.text.length()+1>TARGET)continue;
            chosen.add(item.order);used+=item.text.length()+1;
        }
        if(chosen.isEmpty())return null;
        StringBuilder out=new StringBuilder();
        for(Segment item:pieces){
            if(!chosen.contains(item.order))continue;
            if(out.length()>0)out.append(' ');
            out.append(item.text);
        }
        String style=normalise(out.toString());
        if(style.length()>MAX || style.length()<430)return null;
        return new Result(style,"prioritised complete musical phrases",original.length());
    }
    private static int score(String input){
        String s=input.toLowerCase(Locale.ROOT);
        int score=3;
        if(containsAny(s,"rolling","two-beat","road","trucking","bush","country","gallop","shuffle"))score+=4;
        if(containsAny(s,"drum","snare","kick","brush","rhythm","beat","percussion"))score+=3;
        if(containsAny(s,"acoustic","steel","twang","fiddle","guitar","piano","organ","synth","bass"))score+=3;
        if(containsAny(s,"vocal","baritone","tenor","singer","singing","voice"))score+=4;
        if(containsAny(s,"intro","opening","verse","refrain","chorus","break","outro"))score+=3;
        if(containsAny(s,"strum","pick","answer","counter","pulse","bend","tap","walk","swell","hold","trade"))score+=4;
        if(containsAny(s,"left","right","centre","center","room","dry","dynamic"))score+=1;
        if(containsAny(s,"polished","lush","professional","radio-ready","huge","modern","perfect","immersive"))score-=5;
        return score;
    }
    private static boolean containsAny(String source,String... terms){
        for(String term:terms)if(source.contains(term))return true;
        return false;
    }
    private static Result fromMusicalDna(JSONObject dna,ProducerPipeline.Context c,int originalLength) throws Exception {
        if(dna==null)throw new Exception("Musical analysis missing for local compression");
        // Source song's own curated opening ensures genre/era remains anchored.
        String reference="";
        if(c.reviewed!=null){
            String s=normalise(c.reviewed.style);
            int end=s.indexOf(". ");
            reference=end>=0?s.substring(0,end+1):take(s,125);
        }
        StringBuilder result=new StringBuilder();
        append(result,reference,105);
        append(result,dna.optString("groove",""),130);
        append(result,dna.optString("musicians",""),220);
        append(result,dna.optString("vocal",""),125);
        append(result,dna.optString("arrangement",""),175);
        append(result,dna.optString("mix",""),115);
        append(result,dna.optString("distinctive_details",""),85);
        String compact=normalise(result.toString());
        if(compact.length()<430 || compact.length()>MAX)
            throw new Exception("Local music-DNA compression could not generate a complete Suno-length prompt");
        return new Result(compact,"reconstructed from first-pass musician DNA",originalLength);
    }
    private static void append(StringBuilder dest,String text,int maximum){
        String part=take(normalise(text),maximum);
        if(part.isEmpty())return;
        if(dest.length()>0)dest.append(' ');
        dest.append(part);
    }
    private static String take(String raw,int max){
        if(raw.length()<=max)return raw;
        // Prefer a meaningful clause ending; do not cut into a word.
        int cut=Math.max(raw.lastIndexOf("; ",max),raw.lastIndexOf(". ",max));
        if(cut<max*0.55)cut=raw.lastIndexOf(", ",max);
        if(cut<max*0.55)cut=raw.lastIndexOf(' ',max);
        if(cut<max*0.45)return "";
        return raw.substring(0,cut).trim().replaceAll("[,;:]$","");
    }
}