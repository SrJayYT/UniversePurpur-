package org.purpurmc.purpur;

import com.destroystokyo.paper.util.VersionFetcher;
import io.papermc.paper.ServerBuildInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;

import static net.kyori.adventure.text.Component.text;

/**
 * Version Fetcher for UniversePurpur (+15,000 Players Titan Engine).
 * Fully independent branding - no external purpurmc calls.
 * Author: DevPapo (admin@scon.host)
 */
@DefaultQualifier(NonNull.class)
public class PurpurVersionFetcher implements VersionFetcher {
    private static final String DOWNLOAD_PAGE = "https://github.com/SrJayYT/UniversePurpur-/releases";

    @Override
    public int distance() {
        return 0;
    }

    @Override
    public long getCacheTime() {
        return 3600000;
    }

    @Override
    public Component getVersionMessage() {
        return text("-----------------------------------------------------", NamedTextColor.DARK_GRAY)
                .append(Component.newline())
                .append(text("  UNIVERSE", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                .append(text("PURPUR ", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
                .append(text("» ", NamedTextColor.GRAY))
                .append(text(UniversePurpurConfig.getEngineName(), NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.newline())
                .append(text("-----------------------------------------------------", NamedTextColor.DARK_GRAY))
                .append(Component.newline())
                .append(text(" • Software: ", NamedTextColor.GRAY))
                .append(text("UniversePurpur Enterprise Edition", NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.newline())
                .append(text(" • Versión del Núcleo: ", NamedTextColor.GRAY))
                .append(text("1.0.3-TITAN-RELEASE", NamedTextColor.GREEN))
                .append(Component.newline())
                .append(text(" • Autor & Desarrollador: ", NamedTextColor.GRAY))
                .append(text("DevPapo ", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(text("(admin@scon.host)", NamedTextColor.YELLOW))
                .append(Component.newline())
                .append(text(" • Motor en Uso (Engine): ", NamedTextColor.GRAY))
                .append(text(UniversePurpurConfig.getEngineName(), NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                .append(text(" [" + UniversePurpurConfig.engine + "]", NamedTextColor.DARK_AQUA))
                .append(Component.newline())
                .append(text(" • Tuner de Rendimiento: ", NamedTextColor.GRAY))
                .append(text(UniversePurpurConfig.getTunerName(), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(text(" [" + UniversePurpurConfig.profile + "]", NamedTextColor.YELLOW))
                .append(Component.newline())
                .append(text(" • Versiones Nativas: ", NamedTextColor.GRAY))
                .append(text("1.21.0, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11", NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.newline())
                .append(text(" • Estado: ", NamedTextColor.GRAY))
                .append(text("Estás ejecutando la versión más reciente y optimizada", NamedTextColor.GREEN))
                .append(Component.newline())
                .append(text(" • Repositorio: ", NamedTextColor.GRAY))
                .append(text(DOWNLOAD_PAGE, NamedTextColor.BLUE)
                        .clickEvent(ClickEvent.openUrl(DOWNLOAD_PAGE)))
                .append(Component.newline())
                .append(text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));
    }
}
