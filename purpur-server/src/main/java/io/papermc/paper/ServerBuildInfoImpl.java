package io.papermc.paper;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.jar.Manifest;
import net.kyori.adventure.key.Key;
import net.minecraft.SharedConstants;
import org.jetbrains.annotations.NotNull;

/**
 * Custom ServerBuildInfo implementation for UniversePurpur (+15,000 Players Titan Engine).
 *
 * Removes all references to upstream brands in telemetry, logging, F3 menus, and queries.
 * Author: DevPapo (admin@scon.host)
 */
public record ServerBuildInfoImpl(
    Key brandId,
    String brandName,
    String minecraftVersionId,
    String minecraftVersionName,
    OptionalInt buildNumber,
    Instant buildTime,
    Optional<String> gitBranch,
    Optional<String> gitCommit
) implements ServerBuildInfo {

    private static final String BRAND_UNIVERSE_NAME = "UniversePurpur";
    private static final Key BRAND_UNIVERSE_ID = Key.key("universepurpur", "universepurpur");
    private static final Key BRAND_PURPUR_ID = Key.key("purpurmc", "purpur");

    public ServerBuildInfoImpl() {
        this(
            BRAND_UNIVERSE_ID,
            BRAND_UNIVERSE_NAME,
            SharedConstants.getCurrentVersion().getId(),
            SharedConstants.getCurrentVersion().getName(),
            OptionalInt.of(1),
            Instant.now(),
            Optional.of("master"),
            Optional.of("titan-engine")
        );
    }

    public ServerBuildInfoImpl(final Manifest manifest) {
        this(
            BRAND_UNIVERSE_ID,
            BRAND_UNIVERSE_NAME,
            SharedConstants.getCurrentVersion().getId(),
            SharedConstants.getCurrentVersion().getName(),
            OptionalInt.of(1),
            Instant.now(),
            Optional.of("master"),
            Optional.of("titan-engine")
        );
    }

    @Override
    public boolean isBrandCompatible(final @NotNull Key brandId) {
        return brandId.equals(this.brandId)
            || brandId.equals(BRAND_PURPUR_ID)
            || brandId.equals(BRAND_PAPER_ID);
    }

    @Override
    public @NotNull String asString(final @NotNull StringRepresentation representation) {
        return "1.0.1-TITAN (MC: " + this.minecraftVersionId + ")";
    }
}
