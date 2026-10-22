package com.dennis.aimasteringlab;
import java.io.IOException;
final class StudioAudio {static void cancel() throws IOException {if (Thread.currentThread().isInterrupted())throw new IOException("cancelled");}}
