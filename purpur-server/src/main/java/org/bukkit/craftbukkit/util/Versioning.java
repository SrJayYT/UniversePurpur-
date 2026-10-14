package org.bukkit.craftbukkit.util;

import java.io.InputStream;
import java.util.Properties;
import org.bukkit.Bukkit;

/**
 * Versioning helper for UniversePurpur Titan Engine.
 * Guarantees that Bukkit.getBukkitVersion() accurately returns 1.21.4-R0.1-SNAPSHOT
 * so that plugins like PlugManX, Citizens, WorldEdit, etc. correctly identify the server.
 *
 * Author: DevPapo (admin@scon.host)
 */
public final class Versioning {

    public static String getBukkitVersion() {
        String version = "1.21.4-R0.1-SNAPSHOT";
        try {
            InputStream stream = Bukkit.class.getClassLoader().getResourceAsStream("META-INF/maven/org.purpurmc.purpur/purpur-api/pom.properties");
            if (stream != null) {
                Properties props = new Properties();
                props.load(stream);
                String propVersion = props.getProperty("version");
                if (propVersion != null && !propVersion.trim().isEmpty()) {
                    version = propVersion.trim();
                }
            }
        } catch (Throwable ignored) {
        }
        return version;
    }
}
