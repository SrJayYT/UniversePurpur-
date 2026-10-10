package org.purpurmc.purpur.network;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.purpurmc.purpur.UniversePurpurConfig;

/**
 * Ultra-high-performance lock-free Packet Rate Limiter for massive server connections (+10,000 players).
 * Employs an atomic token bucket algorithm to suppress crasher floods and packet storms before tick loops.
 *
 * Author: DevPapo
 */
public final class PacketRateLimiter {
    private final AtomicInteger tokens;
    private final AtomicLong lastRefillTimestamp;
    private final AtomicLong totalDroppedPackets = new AtomicLong(0);

    public PacketRateLimiter() {
        int initialBurst = Math.max(10, UniversePurpurConfig.packetBurstLimit);
        this.tokens = new AtomicInteger(initialBurst);
        this.lastRefillTimestamp = new AtomicLong(System.currentTimeMillis());
    }

    /**
     * Checks if an incoming network packet is allowed through the pipeline.
     * @return true if packet is accepted; false if it exceeded packet rate threshold.
     */
    public boolean checkPacket() {
        if (!UniversePurpurConfig.packetRateLimitEnabled) {
            return true;
        }

        refillTokens();

        if (tokens.decrementAndGet() >= 0) {
            return true;
        } else {
            tokens.incrementAndGet();
            totalDroppedPackets.incrementAndGet();
            return false;
        }
    }

    private void refillTokens() {
        long now = System.currentTimeMillis();
        long last = lastRefillTimestamp.get();
        long elapsed = now - last;

        if (elapsed >= 1000L && lastRefillTimestamp.compareAndSet(last, now)) {
            int maxRate = Math.max(10, UniversePurpurConfig.maxPacketsPerSecond);
            tokens.set(maxRate);
        }
    }

    public long getDroppedPackets() {
        return totalDroppedPackets.get();
    }

    public void reset() {
        tokens.set(UniversePurpurConfig.packetBurstLimit);
        lastRefillTimestamp.set(System.currentTimeMillis());
        totalDroppedPackets.set(0);
    }
}
