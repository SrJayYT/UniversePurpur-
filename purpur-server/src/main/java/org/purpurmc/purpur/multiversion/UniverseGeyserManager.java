package org.purpurmc.purpur.multiversion;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.purpurmc.purpur.UniversePurpurConfig;

/**
 * Gestor Autónomo de Geyser y Bedrock para UniversePurpur Titan Engine (+15,000 Players).
 * Garantiza que el servidor siempre ejecute la release oficial más reciente de Geyser y Floodgate
 * y sincroniza automáticamente los puertos asignados por Pterodactyl / Cloud.
 *
 * Autor: DevPapo (admin@scon.host)
 */
public final class UniverseGeyserManager {

    private static final String GEYSER_API_URL = "https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest";
    private static final String GEYSER_DOWNLOAD_URL = "https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/spigot";

    private static final String FLOODGATE_API_URL = "https://download.geysermc.org/v2/projects/floodgate/versions/latest/builds/latest";
    private static final String FLOODGATE_DOWNLOAD_URL = "https://download.geysermc.org/v2/projects/floodgate/versions/latest/builds/latest/downloads/spigot";

    private static boolean initialized = false;

    private UniverseGeyserManager() {}

    /**
     * Inicializa la sincronización de Geyser y Floodgate antes de la carga de plugins.
     */
    public static synchronized void init(File serverRootDir) {
        if (initialized) {
            return;
        }
        initialized = true;

        if (serverRootDir == null) {
            serverRootDir = (UniversePurpurConfig.SERVER_ROOT_DIR != null) ? UniversePurpurConfig.SERVER_ROOT_DIR : new File(".");
        }

        File pluginsDir = new File(serverRootDir, "plugins");
        if (!pluginsDir.exists()) {
            pluginsDir.mkdirs();
        }

        try {
            ensureLatestGeyser(pluginsDir);
            ensureLatestFloodgate(pluginsDir);
            syncGeyserConfigPort(pluginsDir, UniversePurpurConfig.geyserPort);
        } catch (Throwable ex) {
            Bukkit.getLogger().log(Level.WARNING, "[UniverseTitan] Geyser Auto-Update: " + ex.getMessage());
        }
    }

    private static void ensureLatestGeyser(File pluginsDir) {
        File targetJar = new File(pluginsDir, "Geyser-Spigot.jar");

        // Buscar si existe algún JAR alternativo de Geyser en plugins/
        File existingGeyser = findPluginJar(pluginsDir, "geyser");
        if (existingGeyser != null) {
            targetJar = existingGeyser;
        }

        try {
            String json = fetchJson(GEYSER_API_URL, 5000);
            if (json == null || json.isEmpty()) {
                if (targetJar.exists() && targetJar.length() > 1000000) {
                    Bukkit.getLogger().info("[UniverseTitan] Geyser local detectado (" + targetJar.getName() + "). No se pudo verificar API remota, manteniendo archivo actual.");
                }
                return;
            }

            String version = extractPattern(json, "\"version\"\\s*:\\s*\"([^\"]+)\"");
            String build = extractPattern(json, "\"build\"\\s*:\\s*(\\d+)");
            String sha256 = extractPattern(json, "\"spigot\"\\s*:\\s*\\{[^}]*\"sha256\"\\s*:\\s*\"([a-fA-F0-9]+)\"");

            if (targetJar.exists() && targetJar.length() > 1000000 && sha256 != null && !sha256.isEmpty()) {
                String localHash = calculateSha256(targetJar);
                if (sha256.equalsIgnoreCase(localHash)) {
                    Bukkit.getLogger().info("[UniverseTitan] Geyser-Spigot está en su versión Release más actualizada (v" + version + " Build #" + build + ").");
                    return;
                }
            }

            Bukkit.getLogger().info("================================================================");
            Bukkit.getLogger().info("[UniverseTitan] Nueva Release de Geyser detectada (v" + version + " Build #" + build + "). Descargando actualización...");
            Bukkit.getLogger().info("================================================================");

            File tempFile = new File(pluginsDir, "Geyser-Spigot.jar.download");
            if (downloadFile(GEYSER_DOWNLOAD_URL, tempFile, 15000)) {
                if (tempFile.length() > 5000000) {
                    if (targetJar.exists()) {
                        targetJar.delete();
                    }
                    File finalDest = new File(pluginsDir, "Geyser-Spigot.jar");
                    if (tempFile.renameTo(finalDest)) {
                        Bukkit.getLogger().info("[UniverseTitan] Geyser-Spigot Release instalado con éxito (" + finalDest.length() + " bytes).");
                    } else {
                        Files.copy(tempFile.toPath(), finalDest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        tempFile.delete();
                        Bukkit.getLogger().info("[UniverseTitan] Geyser-Spigot Release instalado con éxito vía stream copy.");
                    }
                } else {
                    tempFile.delete();
                }
            }
        } catch (Throwable ex) {
            Bukkit.getLogger().log(Level.WARNING, "[UniverseTitan] No se pudo actualizar Geyser automáticamente: " + ex.getMessage());
        }
    }

    private static void ensureLatestFloodgate(File pluginsDir) {
        File targetJar = new File(pluginsDir, "floodgate-spigot.jar");
        File existingFloodgate = findPluginJar(pluginsDir, "floodgate");
        if (existingFloodgate != null) {
            targetJar = existingFloodgate;
        }

        try {
            String json = fetchJson(FLOODGATE_API_URL, 5000);
            if (json == null || json.isEmpty()) {
                return;
            }

            String version = extractPattern(json, "\"version\"\\s*:\\s*\"([^\"]+)\"");
            String build = extractPattern(json, "\"build\"\\s*:\\s*(\\d+)");
            String sha256 = extractPattern(json, "\"spigot\"\\s*:\\s*\\{[^}]*\"sha256\"\\s*:\\s*\"([a-fA-F0-9]+)\"");

            if (targetJar.exists() && targetJar.length() > 500000 && sha256 != null && !sha256.isEmpty()) {
                String localHash = calculateSha256(targetJar);
                if (sha256.equalsIgnoreCase(localHash)) {
                    Bukkit.getLogger().info("[UniverseTitan] floodgate-spigot está en su versión Release más actualizada (v" + version + " Build #" + build + ").");
                    return;
                }
            }

            File tempFile = new File(pluginsDir, "floodgate-spigot.jar.download");
            if (downloadFile(FLOODGATE_DOWNLOAD_URL, tempFile, 15000)) {
                if (tempFile.length() > 500000) {
                    if (targetJar.exists()) {
                        targetJar.delete();
                    }
                    File finalDest = new File(pluginsDir, "floodgate-spigot.jar");
                    if (!tempFile.renameTo(finalDest)) {
                        Files.copy(tempFile.toPath(), finalDest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        tempFile.delete();
                    }
                    Bukkit.getLogger().info("[UniverseTitan] floodgate-spigot Release instalado con éxito.");
                } else {
                    tempFile.delete();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void syncGeyserConfigPort(File pluginsDir, int targetPort) {
        if (targetPort <= 0) return;
        File geyserDir = new File(pluginsDir, "Geyser-Spigot");
        File configFile = new File(geyserDir, "config.yml");
        if (!configFile.exists()) {
            return;
        }

        try {
            String content = Files.readString(configFile.toPath(), StandardCharsets.UTF_8);
            // Replace port: XXXX under bedrock section
            Pattern pattern = Pattern.compile("(?m)(^\\s*port:\\s*)\\d+");
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                String replaced = matcher.replaceFirst("$1" + targetPort);
                if (!replaced.equals(content)) {
                    Files.writeString(configFile.toPath(), replaced, StandardCharsets.UTF_8);
                    Bukkit.getLogger().info("[UniverseTitan] Sincronizado puerto Bedrock en Geyser config.yml a " + targetPort + ".");
                }
            }
        } catch (Throwable ex) {
            Bukkit.getLogger().log(Level.WARNING, "[UniverseTitan] Error sincronizando puerto en Geyser config.yml: " + ex.getMessage());
        }
    }

    private static File findPluginJar(File pluginsDir, String prefix) {
        File[] files = pluginsDir.listFiles();
        if (files == null) return null;
        String lower = prefix.toLowerCase();
        for (File f : files) {
            if (f.isFile() && f.getName().toLowerCase().endsWith(".jar") && f.getName().toLowerCase().startsWith(lower)) {
                return f;
            }
        }
        return null;
    }

    private static String fetchJson(String urlString, int timeoutMs) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "UniversePurpur-TitanEngine/1.0");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setInstanceFollowRedirects(true);

            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                try (InputStream in = conn.getInputStream();
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                    return out.toString(StandardCharsets.UTF_8);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean downloadFile(String downloadUrl, File targetFile, int timeoutMs) {
        try {
            URL url = new URL(downloadUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "UniversePurpur-TitanEngine/1.0");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setInstanceFollowRedirects(true);

            int status = conn.getResponseCode();
            if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                String newUrl = conn.getHeaderField("Location");
                if (newUrl != null) {
                    conn = (HttpURLConnection) new URL(newUrl).openConnection();
                    conn.setRequestProperty("User-Agent", "UniversePurpur-TitanEngine/1.0");
                    conn.setConnectTimeout(timeoutMs);
                    conn.setReadTimeout(timeoutMs);
                }
            }

            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(targetFile)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
            }
            return targetFile.exists() && targetFile.length() > 0;
        } catch (Throwable ex) {
            Bukkit.getLogger().log(Level.WARNING, "[UniverseTitan] Error descargando desde " + downloadUrl + ": " + ex.getMessage());
            return false;
        }
    }

    private static String calculateSha256(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, len);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String extractPattern(String text, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
