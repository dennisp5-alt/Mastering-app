package com.dennis.aimasteringlab;

final class Biquad {
    private final double b0, b1, b2, a1, a2;
    private double x1, x2, y1, y2;

    private Biquad(double b0, double b1, double b2, double a1, double a2) {
        this.b0 = b0;
        this.b1 = b1;
        this.b2 = b2;
        this.a1 = a1;
        this.a2 = a2;
    }

    double process(double x) {
        double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
        x2 = x1;
        x1 = x;
        y2 = y1;
        y1 = y;
        return y;
    }

    static Biquad kWeightShelf(double sampleRate) {
        double gainDb = 3.999843853973347;
        double q = 0.7071752369554196;
        double fc = 1681.974450955533;
        double k = Math.tan(Math.PI * fc / sampleRate);
        double vh = Math.pow(10.0, gainDb / 20.0);
        double vb = Math.pow(vh, 0.4996667741545416);
        double den = 1.0 + k / q + k * k;
        return new Biquad(
                (vh + vb * k / q + k * k) / den,
                2.0 * (k * k - vh) / den,
                (vh - vb * k / q + k * k) / den,
                2.0 * (k * k - 1.0) / den,
                (1.0 - k / q + k * k) / den);
    }

    static Biquad kWeightHighPass(double sampleRate) {
        double q = 0.5003270373238773;
        double fc = 38.13547087602444;
        double k = Math.tan(Math.PI * fc / sampleRate);
        double den = 1.0 + k / q + k * k;
        return new Biquad(
                1.0 / den,
                -2.0 / den,
                1.0 / den,
                2.0 * (k * k - 1.0) / den,
                (1.0 - k / q + k * k) / den);
    }

    static Biquad lowPass(double sampleRate, double frequency) {
        double q = 1.0 / Math.sqrt(2.0);
        double w0 = 2.0 * Math.PI * frequency / sampleRate;
        double alpha = Math.sin(w0) / (2.0 * q);
        double cos = Math.cos(w0);
        double b0 = (1.0 - cos) / 2.0;
        double b1 = 1.0 - cos;
        double b2 = (1.0 - cos) / 2.0;
        double a0 = 1.0 + alpha;
        double a1 = -2.0 * cos;
        double a2 = 1.0 - alpha;
        return new Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0);
    }

    static Biquad highPass(double sampleRate, double frequency) {
        double q = 1.0 / Math.sqrt(2.0);
        double w0 = 2.0 * Math.PI * frequency / sampleRate;
        double alpha = Math.sin(w0) / (2.0 * q);
        double cos = Math.cos(w0);
        double b0 = (1.0 + cos) / 2.0;
        double b1 = -(1.0 + cos);
        double b2 = (1.0 + cos) / 2.0;
        double a0 = 1.0 + alpha;
        double a1 = -2.0 * cos;
        double a2 = 1.0 - alpha;
        return new Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0);
    }

    static Biquad peaking(double sampleRate, double frequency, double q, double gainDb) {
        double a = Math.pow(10.0, gainDb / 40.0);
        double w0 = 2.0 * Math.PI * frequency / sampleRate;
        double alpha = Math.sin(w0) / (2.0 * q);
        double cos = Math.cos(w0);
        double b0 = 1.0 + alpha * a;
        double b1 = -2.0 * cos;
        double b2 = 1.0 - alpha * a;
        double a0 = 1.0 + alpha / a;
        double a1 = -2.0 * cos;
        double a2 = 1.0 - alpha / a;
        return new Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0);
    }

    static Biquad bandPass(double sampleRate,double frequency,double q){
        double w=2*Math.PI*frequency/sampleRate,a=Math.sin(w)/(2*q),d=1+a;
        return new Biquad(a/d,0,-a/d,-2*Math.cos(w)/d,(1-a)/d);
    }

    static Biquad lowShelf(double sampleRate, double frequency, double gainDb) {
        return shelf(sampleRate, frequency, gainDb, false);
    }

    static Biquad highShelf(double sampleRate, double frequency, double gainDb) {
        return shelf(sampleRate, frequency, gainDb, true);
    }

    private static Biquad shelf(double sampleRate, double frequency,
                                double gainDb, boolean high) {
        double a = Math.pow(10.0, gainDb / 40.0);
        double w0 = 2.0 * Math.PI * frequency / sampleRate;
        double cos = Math.cos(w0);
        double sin = Math.sin(w0);
        double alpha = sin / Math.sqrt(2.0);
        double twoRootAAlpha = 2.0 * Math.sqrt(a) * alpha;
        double b0, b1, b2, a0, a1, a2;
        if (high) {
            b0 = a * ((a + 1) + (a - 1) * cos + twoRootAAlpha);
            b1 = -2 * a * ((a - 1) + (a + 1) * cos);
            b2 = a * ((a + 1) + (a - 1) * cos - twoRootAAlpha);
            a0 = (a + 1) - (a - 1) * cos + twoRootAAlpha;
            a1 = 2 * ((a - 1) - (a + 1) * cos);
            a2 = (a + 1) - (a - 1) * cos - twoRootAAlpha;
        } else {
            b0 = a * ((a + 1) - (a - 1) * cos + twoRootAAlpha);
            b1 = 2 * a * ((a - 1) - (a + 1) * cos);
            b2 = a * ((a + 1) - (a - 1) * cos - twoRootAAlpha);
            a0 = (a + 1) + (a - 1) * cos + twoRootAAlpha;
            a1 = -2 * ((a - 1) + (a + 1) * cos);
            a2 = (a + 1) + (a - 1) * cos - twoRootAAlpha;
        }
        return new Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0);
    }
}
