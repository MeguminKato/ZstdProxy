package cn.tohsaka.factory.zstdpiping;

import java.util.concurrent.atomic.LongAdder;

public class DualTrafficStats {

    public final LongAdder rawBytes = new LongAdder();
    public final LongAdder compressedBytes = new LongAdder();

    private long lastRaw = 0;
    private long lastCompressed = 0;
    private long lastTime = System.nanoTime();

    public void addRaw(long n) {
        rawBytes.add(n);
    }

    public void addCompressed(long n) {
        compressedBytes.add(n);
    }

    public void reset(){
        rawBytes.reset();
        compressedBytes.reset();
    }

    public void print(String name) {
        long now = System.nanoTime();

        long raw = rawBytes.sum();
        long comp = compressedBytes.sum();

        double seconds = (now - lastTime) / 1_000_000_000.0;
        if (seconds <= 0) return;

        long dRaw = raw - lastRaw;
        long dComp = comp - lastCompressed;

        double bw = (dComp / 1024.0 / 1024.0) / seconds;
        double ratio = dComp == 0 ? 0 : (double) dRaw / dComp;

        System.out.printf(
                "[%s] Raw: %.2f MB | Compressed: %.2f MB | Ratio: %.2fx | BW: %.2f MB/s%n",
                name,
                raw / 1024.0 / 1024.0,
                comp / 1024.0 / 1024.0,
                ratio,
                bw
        );

        lastRaw = raw;
        lastCompressed = comp;
        lastTime = now;
    }
}
