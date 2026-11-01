package com.dennis.aimasteringlab;

public final class V0145PassageEnvelopeHarness {
    static void req(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    static PassagePreservation.Window window(double peak) {
        PassagePreservation.Window w = new PassagePreservation.Window();
        w.frames = 48000;
        w.energy = 48.0; // -30 dB average power
        w.midPresence = 24.0;
        w.midEnergy = 24.0;
        w.sideEnergy = 2.4;
        w.peak = peak;
        w.start = 0.0;
        w.end = 1.0;
        return w;
    }
    static PassagePreservation.Scan scan(double peak) {
        PassagePreservation.Scan s = new PassagePreservation.Scan();
        s.frames = 48000;
        s.rate = 48000;
        s.lufs = -14.0;
        s.duration = 1.0;
        s.windows.add(window(peak));
        return s;
    }
    public static void main(String[] args) {
        PassagePreservation.Scan a = scan(0.10);
        PassagePreservation.Scan b = scan(0.0912); // about -0.80 dB crest change
        PassagePreservation.Result standard = PassagePreservation.compare(a, b);
        req(!standard.passed(), "standard passage gate should reject -0.8 dB transient crest loss");
        PassagePreservation.Result adaptive = PassagePreservation.compareDynamicsLed(a, b, -0.85);
        req(adaptive.passed(), "dynamics-led envelope should allow bounded -0.8 dB crest move");
        PassagePreservation.Scan c = scan(0.0860); // about -1.31 dB
        PassagePreservation.Result excessive = PassagePreservation.compareDynamicsLed(a, c, -0.85);
        req(!excessive.passed(), "strong transient shaving must still fail");
        System.out.println("V0145_PASSAGE_ENVELOPE_PASS");
    }
}
