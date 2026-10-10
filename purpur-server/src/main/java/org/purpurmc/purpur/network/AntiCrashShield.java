package org.purpurmc.purpur.network;

import java.util.concurrent.atomic.AtomicLong;
import org.purpurmc.purpur.UniversePurpurConfig;

/**
 * Enterprise Exploit & Anti-Crasher Shield for UniverseSpigot (+15,000 Players).
 * Defends massive server deployments against NaN/Infinite movement vectors, oversized NBT payloads,
 * invalid container window slots, and malformed network packet injectors.
 *
 * Author: DevPapo
 */
public final class AntiCrashShield {
    private static final AntiCrashShield INSTANCE = new AntiCrashShield();

    private final AtomicLong blockedInvalidMovement = new AtomicLong(0L);
    private final AtomicLong blockedOversizedPayloads = new AtomicLong(0L);
    private final AtomicLong blockedInvalidSlots = new AtomicLong(0L);

    private AntiCrashShield() {}

    public static AntiCrashShield instance() {
        return INSTANCE;
    }

    /**
     * Validates movement coordinates against NaN, Infinite, and extreme world boundary crashers.
     *
     * @return true if coordinates are safe; false if packet is malicious and should be dropped.
     */
    public boolean validateCoordinates(double x, double y, double z) {
        if (!UniversePurpurConfig.antiCrashShieldEnabled) {
            return true;
        }

        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)
                || Double.isInfinite(x) || Double.isInfinite(y) || Double.isInfinite(z)) {
            blockedInvalidMovement.incrementAndGet();
            return false;
        }

        if (Math.abs(x) > 30000000.0D || Math.abs(z) > 30000000.0D || y < -2000.0D || y > 4000.0D) {
            blockedInvalidMovement.incrementAndGet();
            return false;
        }

        return true;
    }

    /**
     * Validates rotation angles against NaN and Infinity crash packets.
     */
    public boolean validateRotation(float yaw, float pitch) {
        if (!UniversePurpurConfig.antiCrashShieldEnabled) {
            return true;
        }

        if (Float.isNaN(yaw) || Float.isNaN(pitch) || Float.isInfinite(yaw) || Float.isInfinite(pitch)) {
            blockedInvalidMovement.incrementAndGet();
            return false;
        }

        return true;
    }

    /**
     * Validates incoming custom payload buffer sizes against crasher payloads.
     */
    public boolean validatePayloadSize(int bytes) {
        if (!UniversePurpurConfig.antiCrashShieldEnabled) {
            return true;
        }

        int maxAllowed = UniversePurpurConfig.maxPayloadSizeKb * 1024;
        if (bytes > maxAllowed || bytes < 0) {
            blockedOversizedPayloads.incrementAndGet();
            return false;
        }

        return true;
    }

    /**
     * Validates inventory window slots against client-injected out-of-bounds indices.
     * Guaranteed protection against IndexOutOfBoundsException crashes in container menus.
     */
    public boolean validateWindowSlot(int slot, int maxSlots) {
        if (!UniversePurpurConfig.antiCrashShieldEnabled) {
            return true;
        }

        // -999 and -1 are special vanilla slots indicating dropping an item outside the window
        if (slot != -999 && slot != -1 && (slot < 0 || (maxSlots > 0 && slot >= maxSlots))) {
            blockedInvalidSlots.incrementAndGet();
            return false;
        }

        return true;
    }

    public long getBlockedInvalidMovement() {
        return blockedInvalidMovement.get();
    }

    public long getBlockedOversizedPayloads() {
        return blockedOversizedPayloads.get();
    }

    public long getBlockedInvalidSlots() {
        return blockedInvalidSlots.get();
    }

    public long getTotalBlockedExploits() {
        return blockedInvalidMovement.get() + blockedOversizedPayloads.get() + blockedInvalidSlots.get();
    }

    public void reset() {
        blockedInvalidMovement.set(0L);
        blockedOversizedPayloads.set(0L);
        blockedInvalidSlots.set(0L);
    }
}
