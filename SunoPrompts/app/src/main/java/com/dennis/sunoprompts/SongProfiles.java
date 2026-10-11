package com.dennis.sunoprompts;

import java.util.Locale;

/** Reviewed creative production interpretations, not audio analysis or track transcription. */
final class SongProfiles {
    static final class Profile {
        final String style, exclude, evidence, sources;
        Profile(String s,String x,String e,String urls) {
            style=s; exclude=x; evidence=e; sources=urls;
        }
    }
    static String norm(String s) {
        return s==null?"":s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim().replaceAll(" +"," ");
    }
    private static final String GENERAL_EXCLUDE =
        "Copied melody or lyrics, voice cloning, harsh autotune, robotic delivery, plastic drums, over-quantisation, crowded low mids, brittle cymbals, heavy limiting.";
    static Profile find(String artist,String song) {
        String a=norm(artist), s=norm(song);
        if(a.equals("lee kernaghan") && s.equals("boys from the bush")) {
            return new Profile(
                "Early-1990s Australian country-rock bush anthem, lively and earthy with a driving, galloping country groove. Tight live drums, cracking snare backbeat, rolling electric bass and bright strummed acoustic guitar form the backbone. Crunchy, twang-edged electric rhythm guitars add energetic chord accents; short melodic electric guitar fills, fiddle and mandolin provide rustic colour without crowding the vocal. Verses ride a lean, bouncing band arrangement; choruses open into a confident, communal singalong with backing harmonies and stronger guitar accents. Lead vocal: rugged Australian male baritone, nasal country twang, conversational storytelling, lively clipped phrasing, natural grit and enthusiasm. Early-90s Nashville-meets-Australian-pub-country production: real musicians, distinct left/right guitar parts, firm centred bass and vocal, open drums, warm mids, lively transients, minimal modern processing. Original melody and lyrics.",
                GENERAL_EXCLUDE+", smooth pop-country vocals, trap hats, synthetic bass, glossy synth-pop arrangements.",
                "Verified: Boys from the Bush appears on Lee Kernaghan's 1992 album The Outback Club. Kernaghan described Garth Porter's involvement and the album's live band with drums, bass, guitars, keyboards, fiddle and mandolin. These are album-level details, not a song-specific stem or instrument-by-instrument verification. The galloping groove, arrangement and mix suggestions are an original creative interpretation. No audio was analysed.",
                "Lee Kernaghan official album: https://leekernaghan.com/album/the-outback-club/\n"+
                "Lee Kernaghan on the album musicians: https://music.apple.com/us/album/boys-from-the-bush-remastered-2017/1583356708?i=1583356709\n");
        }
        if(a.equals("ac dc") && s.equals("back in black")) {
            return new Profile(
                "Early-1980s hard rock with a relaxed, deep-pocket midtempo stomp. Start on a low, syncopated overdriven guitar figure punctuated by confident rests; anchor it with deliberate bass guitar and roomy acoustic drums. Second guitar answers in a separate upper-mid register, making the gaps as important as the attacks. Verses stay riff-led and spacious; choruses widen into blocky open chords and punchy band accents. High-energy, raspy male rock lead with clipped phrasing and dynamic call-and-response. Natural drum attack, thick but not fuzzy guitar amps, dry upfront vocal, wide complementary rhythm guitars and strong mono-compatible centre bass and kick. Retain dynamics, room air and real band timing. New original riffs, melody and lyrics.",
                GENERAL_EXCLUDE+", metal shredding, keyboard pads, double kick, modern metal compression.",
                "Curated musical reference: this is an original early-1980s hard-rock arrangement inspired by the recorded song's general era and instrumental vocabulary, not an audio measurement or note-for-note recreation.","");
        }
        if(a.equals("ac dc") && s.equals("thunderstruck")) {
            return new Profile(
                "Early-1990s stadium hard rock, electric and propulsive. Open with an exposed, bright high-register repeating guitar figure and purposeful silence around it. Introduce a complementary lower-register guitar response, electric bass and firm kick/snare rock pulse in layers; build into broad rhythm-guitar chords and crowd-sized vocal sections. Thick low-string guitar accents and sharp staccato stops energise transitions. Forceful high, gritty male lead, tight shouted backing responses and a big live-band lift. Split guitars left and right, keep bass, kick and lead vocal firmly centred; clear cymbal transients, real amp edge, little modern polish. Create new guitar motif, melody and lyrics rather than reproducing the reference.",
                GENERAL_EXCLUDE+", metal blast beats, synth drops, EDM bass, overproduced drum samples.",
                "Curated creative direction based on broad hard-rock production traits. No source audio or precise track transcription analysed.","");
        }
        if(a.equals("ac dc") && s.equals("highway to hell")) {
            return new Profile(
                "Late-1970s Australian hard rock with a confident, loose-limbed midtempo drive. Open with accented open-chord electric rhythm guitar and a firm backbeat, then bring in rounded electric bass and a second crunchy rhythm guitar that fills gaps rather than doubling every strum. Natural acoustic-kit drums, forward snare and occasional cymbal pushes. Verses are punchy and spacious; the chorus opens to an anthemic group response and sustained power-chord accents. Gritty shouted chest-voice rock vocal, swaggering timing, audible breaths and live-band conviction. Vintage amp saturation, dry room sound, complementary L/R guitars, centred bass, kick and lead vocal. Preserve groove and natural dynamics; write original melodic material.",
                GENERAL_EXCLUDE+", rapid metal drums, huge synth pads, sterile drum samples.",
                "Curated creative direction. This is not a direct transcription of the released song.","");
        }
        if(a.equals("slim dusty") && s.equals("lights on the hill")) {
            return new Profile(
                "Classic Australian truck-driving country ballad in an intimate 1970s bush-country vein. A rolling, unhurried road-beat from simple bass notes, lightly brushed or tapped snare and steady acoustic guitar strumming; clean twang electric guitar and gently weeping pedal steel answer the narrative lines. Verses move like a long journey with small fiddle or steel fills, while the refrain opens slightly without turning into a stadium chorus. Mature weathered Australian baritone, unforced conversational diction, grounded storytelling with subtle held notes and natural breaths. Small live ensemble, gentle tape-era warmth, dry clear words, sympathetic plate ambience, wide acoustic/steel support and a firm unexaggerated centre. New original story, tune and lyric.",
                GENERAL_EXCLUDE+", modern country-rock bombast, electronic drums, dense vocal stacks.",
                "Curated production direction reflecting Australian trucking-country tradition; no actual recording analysed.","");
        }
        if(a.equals("slim dusty") && s.equals("the home run")) {
            return new Profile(
                "Unhurried Australian bush-country trucking story, warm and worn-in, with a steady rolling two-beat pulse. Bright but soft acoustic strum under an uncluttered bass line; restrained brushed snare, country electric guitar fills, gentle steel-guitar swells and intermittent fiddle replies. Story-led verses remain quiet and plainly delivered; lift the refrain through chord voicing and instrumental responses rather than heavy drumming. Mature plain-spoken Australian baritone with gentle nasal twang and precise storytelling consonants, loose natural timing and warm emotion. Record as a small live band with modest analogue colour, clean centred voice and bass, acoustic guitar to one side, steel and fiddle balancing the other. Original melody and lyrics.",
                GENERAL_EXCLUDE+", stadium drums, slick pop-country tuning, synthetic strings.",
                "Curated original bush-ballad arrangement; no isolated recording analysis or confirmed session details.","");
        }
        if(a.equals("dire straits") && s.equals("sultans of swing")) {
            return new Profile(
                "Late-1970s articulate roots-rock with a relaxed swinging backbeat and lively guitar interplay. Fingerpicked clean electric lead plays nimble, syncopated answer lines high above restrained chordal rhythm guitar. Rounded melodic bass follows the groove, crisp low-key live drums keep a lightly shuffling pulse, and understated keyboard shading fills only the spaces. Verses carry a half-spoken narrative baritone with dry wit and patient phrasing; let the instrumental breaks gradually become more expressive without getting heavier. Clean valve-amp attack, audible pick/finger dynamics, moderate room ambience, natural stereo spacing, centred vocal/kick/bass, guitar details spread across the sides. Fresh original guitar lines and song melody.",
                GENERAL_EXCLUDE+", distorted metal guitars, big arena drums, synth bass, modern vocal tuning.",
                "Curated era/style reference for an original recording; no exact transcription or source waveform analysis.","");
        }
        if(a.equals("duran duran") && s.equals("rio")) {
            return new Profile(
                "Bright, elastic early-1980s new-wave dance-rock with propulsive syncopated electric bass driving the whole arrangement. Tight, crisp live-drum groove accented by bright hi-hats and snare; glassy analogue synth chords, high decorative synth counters, clean percussive funk guitar and brief sax-flavoured colours trade space instead of piling up. Energetic verses carry a poised male pop lead with rhythmic phrasing; choruses soar into stacked harmonies and broader synth voicings. Precise but still human timing, vivid left/right interplay, central bass-kick-lead anchor and spacious midrange for the voice. Early-80s glossy studio character without brickwall compression. Original hooks, lyrics and melody.",
                GENERAL_EXCLUDE+", EDM drops, trap hats, excessive pitch correction, squashed drums.",
                "Curated early-80s dance-rock production concept, not a transcript of the song recording.","");
        }
        return null;
    }
}
