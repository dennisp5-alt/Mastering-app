package com.dennis.sunoprompts;

import java.util.Locale;

final class PromptTemplates {
    private static final String EXCLUDE="Copied melodies or lyrics, celebrity voice clones, synthetic timing, robotic phrasing, metallic cymbals, over-compression, muddy low mids, harsh clipping.";
    static final class Guide {
        final String arrangement, vocal, mix, label;
        Guide(String name,String band,String singer,String production) {
            label=name;arrangement=band;vocal=singer;mix=production;
        }
    }

    static PromptStore.Prompt starter(String artist,String song,String notes,ResearchClient.Research research) {
        PromptStore.Prompt p=new PromptStore.Prompt();
        p.artist=artist.trim();p.song=song.trim();
        SongProfiles.Profile specific=SongProfiles.find(artist,song);
        if(specific!=null) {
            p.mode="CURATED SONG REFERENCE — FREE";
            p.style=addDirection(specific.style,notes);
            p.exclude=specific.exclude;
            p.sources=specific.sources+(research.sources.isEmpty()?"":"\nAdditional automatic research:\n"+research.sources);
            p.analysis=specific.evidence+"\n\nAutomatic database context:\n"+research.context()+
                "\n\nFREE MODE: curated original arrangement directions; no model inference or audio audition.";
            return p;
        }
        Guide g=artistGuide(artist);
        String context=(research.artistFacts+" "+research.songFacts).toLowerCase(Locale.ROOT);
        String label="";
        if(g==null) {
            g=genreGuide(context);
            label="GENRE-BASED RESEARCH GUIDE";
        } else label="ARTIST STYLE — SONG NOT PROFILED";
        String direction=notes==null?"":notes.trim();
        if(g!=null) {
            p.style=addDirection(g.arrangement+" "+g.vocal+" "+g.mix+
                " Create an independent composition with original melody and lyrics.",direction);
            p.mode=label+" — FREE";
            p.analysis="This song does not have a reviewed song-specific production profile. "+
                "The saved prompt follows the "+g.label+" production vocabulary and should NOT be read as a verified description of \""+song+"\". "+
                "Song-by-song musical differences require human review or licensed audio analysis. "+
                "No audio was analysed and no paid AI was used.\n\nAutomatic research:\n"+research.context();
        } else {
            p.style=addDirection("Production research draft for a new original song. Instrumentation, groove and vocal direction have not been independently identified for this artist or track. Specify genre, era, acoustic/electric instrument roles, vocal delivery, arrangement lift, and the sonic mix direction.",direction);
            p.mode="UNPROFILED — USER INPUT NEEDED";
            p.analysis="No reviewed artist/recording profile or confidently identifiable genre is available. "+
               "This is deliberately not presented as a complete Suno production prompt. "+
               "Add your own descriptors in the notes field, or select a supported artist; otherwise the result would be a misleading generic fabrication.\n\n"+
               research.context();
        }
        p.exclude=EXCLUDE;
        p.sources=research.sources;
        return p;
    }

    static Guide artistGuide(String artist) {
        String a=SongProfiles.norm(artist);
        if(a.equals("lee kernaghan"))return new Guide("Australian 1990s country-rock",
          "1990s Australian rural country-rock band, driving snare-led road rhythm, alternating acoustic strum and twangy overdriven electric guitar, nimble country bass, bright fiddle or mandolin punctuating the vocal gaps, verse-to-chorus lift through live ensemble dynamics.",
          "Rugged but articulate Australian male baritone, nasal bush-country twang, lively forward storytelling with believable loose timing and shouted communal chorus answers.",
          "Period-appropriate Australian/Nashville crossover: centred warm bass, kick and lead voice; acoustic and electric guitar complementary across the stereo field, natural drums and clear transient punch; not overly slick.");
        if(a.equals("slim dusty"))return new Guide("Australian bush-country",
          "Australian bush-ballad country with plain acoustic guitar down-up strumming, gently rolling two-beat country bass, light brushed drums or train beat, short electric twang fills and measured steel/fiddle responses between narrative lines.",
          "A weathered older Australian baritone, conversational storytelling and relaxed behind-the-beat phrasing, warm natural consonants.",
          "Small-room ensemble feel, restrained vintage tape saturation, mostly dry centred lead vocal, soft plate on steel, dynamic rather than loud.");
        if(a.equals("ac dc")||a.equals("acdc"))return new Guide("classic Australian hard rock",
          "Classic Australian hard rock with two electric guitars playing distinct blues-rooted rhythm and riff registers, driving real bass guitar, big but simple live-kit backbeat, powerful chorus chord accents and sparse instrumental breaks.",
          "Raw high, raspy male chest-voice shouting, natural grit and assertive short phrases.",
          "Crunchy analogue amps, dry centre vocal, firmly centred bass and kick, tight snare, guitars complementary left and right, full room dynamics.");
        if(a.equals("dire straits"))return new Guide("roots-rock",
          "Late-1970s/1980s roots-rock with agile fingerpicked clean lead guitar, restrained rhythm guitar, supple electric bass, a light live-kit groove, sparse organ or piano and high-register instrumental responses.",
          "Conversational, understated story-telling male baritone and unhurried naturally accented delivery.",
          "Spacious restrained analog mix, strong stereo separation, preserved pick dynamics, clear midrange vocal and organic ambience.");
        if(a.equals("duran duran"))return new Guide("new-wave dance rock",
          "Early-1980s glossy dance-rock: syncopated melodic electric bass, bright gated snare and hi-hat, precise clean funk guitar, shimmering analogue synth pads and separate high-register synth hook fragments.",
          "Confident melodic new-wave male vocal with expressive chorus harmonies and clean diction.",
          "Broad stereo synth architecture, anchored kick/bass/lead, firm groove, bright but non-brittle vintage pop mix.");
        if(a.equals("icehouse"))return new Guide("Australian new-wave art rock",
          "Australian early-1980s art-rock/new wave, spacious analogue synth motifs, melodic fretless-style bass movement, clean chorus guitar and firm acoustic drum kit with selective gated ambience.",
          "Clear expressive male baritone-tenor, sustained emotional vowel lines and subtle layered chorus harmony.",
          "Wide delayed guitar/synth contrasts, crisp centre snare and bass, deep front-back spatial layering without exaggerated modern effects.");
        if(a.equals("alan jackson"))return new Guide("1990s Nashville country",
          "Warm early-1990s Nashville honky-tonk country with strummed acoustic guitar, clean Telecaster fills, swinging bass, light snare and rimshots, fiddle and pedal steel trading short answers.",
          "Smooth natural American male baritone, relaxed vowel phrasing, conversational country storytelling and carefully measured harmonies.",
          "Organic studio band, open midrange, gentle plate reverb, no slick modern compression.");
        if(a.equals("joe diffie"))return new Guide("1990s honky tonk",
          "Punchy mid-1990s honky-tonk country with bright electric Telecaster, acoustic backbeat, walking or two-beat bass, swinging live snare, fiddle, honky-tonk piano and steel-guitar accent replies.",
          "Rich expressive American country baritone, occasional high tenor lift, playful conversational attack and warm chorus harmonies.",
          "Real Nashville session-band presence, robust low mids without boom, warm transient punch.");
        if(a.equals("garth brooks"))return new Guide("1990s country arena",
          "Early-1990s commercial Nashville country with tight acoustic guitar, ringing Telecaster, pedal steel, piano, full live drums and dynamic chorus growth toward arena-country uplift.",
          "Expressive masculine tenor/baritone, close emotional verses and powerful natural belts, harmony-backed refrains.",
          "Large but organic room, airy stereo acoustic layers, grounded electric bass and natural live drum impact.");
        if(a.equals("charley pride"))return new Guide("classic Nashville country",
          "Vintage Nashville country ensemble with simple strummed acoustic guitar, gently ticking rimshot kit, electric bass, restrained piano, clean lead guitar and softly singing pedal steel or fiddle responses.",
          "Deep smooth country baritone, uncluttered melody delivery with intimate sustained notes and poised diction.",
          "Warm small-room analogue tone, modest stereo breadth and gentle plate, clear voice at front.");
        if(a.equals("johnny cash"))return new Guide("classic country train beat",
          "Sparse classic country with percussive muted acoustic strum, ticking train-beat snare, compact bass notes, occasional twang electric single-note fills and little decorative arrangement.",
          "Deep spoken-sung masculine bass-baritone, crisp consonants, purposeful pauses and stoic storytelling.",
          "Dry up-front live sound, stable centred voice and bass, almost no synthetic processing.");
        if(a.equals("terri clark"))return new Guide("1990s country rock",
          "1990s mainstream country rock with punchy live drums, rolling bass guitar, acoustic strums, crisp electric Telecaster leads, steel-fiddle accents and guitar-driven hook lift.",
          "Confident natural female country alto, strong clear diction, slightly husky edge, easy soaring chorus harmonies.",
          "Spacious Nashville band room with warm vocal upfront and bright separation across guitars.");
        if(a.equals("kenny rogers"))return new Guide("late-1970s country pop",
          "Late-1970s/early-1980s country-pop ballad with measured acoustic guitar, soft drum kit, gentle electric bass, warm electric piano, restrained strings and occasional steel fills.",
          "Warm mature male baritone, velvety yet grainy story phrasing, emotionally soft dynamic chorus rise.",
          "Soft analogue tape texture, controlled smooth top end and vocal-front studio balance.");
        if(a.equals("bryan adams"))return new Guide("1980s melodic rock",
          "1980s melodic guitar rock with bright palm-muted and open electric-guitar riffs, bass driving eighths, huge real-kit snare backbeat, acoustic undercurrent and keyboard support.",
          "Raspy melodic male rock tenor, focused close verses and full-throated anthemic choruses.",
          "Warm compressed-room snare, overdriven guitars arranged left/right, anchored bass and lead, energetic natural dynamics.");
        if(a.equals("m tley cr e")||a.equals("motley crue")||a.equals("m tley crue"))return new Guide("1980s glam metal",
          "1980s Los Angeles glam metal with saturated layered hard-rock guitars, pounding backbeat, pumping bass, flashy short lead breaks and explosive chorus guitar harmonies.",
          "High raspy male rock delivery, energetic high notes, aggressive attack and gang-backed refrain.",
          "Wide Marshall-style rhythm guitars, close hard-hitting drums and stage-sized ambience without modern brickwall distortion.");
        if(a.equals("the bee gees")||a.equals("bee gees"))return new Guide("1970s disco pop",
          "Late-1970s disco-pop with elastic syncopated bass, crisp steady kick and snare, subtle guitar chops, electric piano, silky string/synth arrangements and layered falsetto vocal hooks.",
          "Airy high male falsetto, tightly stacked harmony chords, breathy human phrasing and confident melodic lift.",
          "Lush warm 1970s studio depth, bright rhythmic percussion, wide background textures and solid centred kick and bass.");
        if(a.equals("2 unlimited"))return new Guide("early-1990s Eurodance",
          "Early-1990s high-energy Eurodance with solid four-on-the-floor kick, bright rave stabs, syncopated bass synth, snare/clap accents, short sharp synth riff exchanges and 8/16-bar dance-build structure.",
          "Urgent punchy male spoken rap sections contrasted with strong rhythmic female sung hooks.",
          "Big club punch, clean low bass and kick in centre, stereo rave synths, strong verse-hook contrast.");
        if(a.equals("alan walker"))return new Guide("melodic electronic pop",
          "Contemporary melodic electronic dance-pop with concise minor-key piano motif, airy layered synth pads, pulsing bass, crisp sampled drums, sidechained synth chords and a euphoric chorus swell.",
          "Breathy restrained lead with wide doubled refrains and layered atmospheric harmonies.",
          "Polished transparent dance production, deep centred sub and kick, distinct stereo arpeggios and evolving width.");
        return null;
    }

    static Guide genreGuide(String context) {
        if(context==null)return null;
        String text=context.toLowerCase(Locale.ROOT);
        if(text.contains("country") || text.contains("bluegrass")) return new Guide("country-music references",
            "Live acoustic guitar strums supported by electric bass, restrained acoustic drums, expressive slide or steel guitar and fiddle replies, with a chorus growing through playable band dynamics.",
            "Natural narrative country lead vocal with clear diction and emotional restraint.",
            "Organic ensemble mix with acoustic clarity, distinct registers, centred lead and bass and subtle reverb.");
        if(text.contains("heavy metal")||text.contains("hard rock"))return new Guide("hard-rock genre",
            "Real overdriven rhythm guitars in complementary registers, electric bass, energetic snare-backbeat drums and dynamic choruses with short instrumental replies.",
            "Direct raspy rock lead with powerful naturally delivered chorus lines.",
            "Warm guitar amps, anchored bass and drum centre, wide rhythm guitars and live room ambience.");
        if(text.contains("new wave")||text.contains("synthpop")||text.contains("synth-pop"))return new Guide("new-wave/synth-pop genre",
            "Syncopated bass and crisp percussion with melodic synth arpeggios, analogue pads, contrasting mid-register guitar and carefully staged chorus harmonies.",
            "Articulate melodic pop lead with a light expressive phrasing.",
            "Bright layered stereo pop mix, uncluttered bass frequencies and natural macro-dynamics.");
        if(text.contains("hip hop")||text.contains("hip-hop")||text.contains("rap music"))return new Guide("hip-hop genre",
            "Solid groove-led programmed drums, a short repeating instrumental motif, distinct bass weight and sparse counterline accents, with space for vocal phrasing and breaks.",
            "Human conversational lead delivery with musical emphasis and controlled breathing.",
            "Strong grounded kick and bass, clean centred lead and carefully restrained upper-register layers.");
        if(text.contains("disco")||text.contains("dance-pop")||text.contains("electronic dance"))return new Guide("dance-pop genre",
            "Repetitive groove-led four-on-the-floor kick, syncopated bass, crisp claps and bright melodic keyboard textures; verses strip back, hooks expand in short repeating phrases.",
            "Natural melodic lead with layered supporting chorus doubles.",
            "Crisp modern club balance and strong centre kick/bass, with wide bright synth harmonies.");
        if(text.contains("folk music")||text.contains("folk rock"))return new Guide("folk genre",
            "Natural acoustic guitar fingerpicking and strum, soft bass, sparse percussion and modest fiddle/mandolin interjections, gradually swelling for refrains.",
            "Intimate narrative human lead with natural breaths and variable intensity.",
            "Warm acoustic room with clear foreground vocal and light stereo spread.");
        if(text.contains("rock music")||text.contains("pop rock"))return new Guide("rock genre",
            "Live acoustic-kit backbeat, electric bass and complementary clean/overdriven electric guitars; verses leave space for vocal and choruses add stronger chordal accents.",
            "Energetic natural rock lead with dynamic contrast and distinct phrasing.",
            "Wide guitars, centred vocal/kick/bass, resonant drums and moderate compression.");
        return null;
    }

    static String addDirection(String style,String notes) {
        String suffix=notes==null||notes.trim().isEmpty()?"": " Personal direction: "+notes.trim().replaceAll("\\s+"," ");
        if(suffix.isEmpty())return shorten(style,1000);
        int budget=1000-suffix.length();
        if(budget<100)return shorten(style,1000);
        return shorten(style,budget)+suffix;
    }
    static String shorten(String value,int limit) {
        if(value.length()<=limit)return value;
        int cut=value.lastIndexOf(' ',limit);
        return value.substring(0,cut>limit/2?cut:limit).trim();
    }
}
