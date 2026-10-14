package org.purpurmc.purpur.multiversion;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.purpurmc.purpur.UniversePurpurConfig;

/**
 * Gestor Autónomo de PlugMan / PlugManX para UniversePurpur Titan Engine (+15,000 Players).
 * Garantiza que el servidor cuente siempre con la versión más actualizada de PlugMan instalada en plugins/.
 *
 * Autor: DevPapo (admin@scon.host)
 */
public final class UniversePlugManManager {

    private static final String MODRINTH_PLUGMANX_API = "https://api.modrinth.com/v2/project/plugmanx/version";
    private static final String FALLBACK_PLUGMANX_URL = "https://cdn.modrinth.com/data/yro4niHu/versions/j22EUw2Y/PlugManX-3.2.1.jar";

    private static boolean initialized = false;

    private UniversePlugManManager() {}

    /**
     * Inicializa la comprobación y auto-instalación de PlugMan antes de que carguen los plugins.
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
            ensurePlugMan(pluginsDir);
        } catch (Throwable ex) {
            Bukkit.getLogger().log(Level.WARNING, "[UniverseTitan] PlugMan Auto-Install: " + ex.getMessage());
        }
    }

    private static void ensurePlugMan(File pluginsDir) {
        File[] files = pluginsDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().toLowerCase().endsWith(".jar")) {
                    String name = file.getName().toLowerCase();
                    if (name.startsWith("plugman")) {
                        Bukkit.getLogger().info("[UniverseTitan] PlugMan detectado en plugins/ (" + file.getName() + ").");
                        return;
                    }
                }
            }
        }

        Bukkit.getLogger().info("[UniverseTitan] PlugMan no encontrado -> Descargando e instalando PlugManX más actualizado...");
        File targetJar = new File(pluginsDir, "PlugManX.jar");
        String downloadUrl = fetchLatestDownloadUrl();
        if (downloadUrl == null || downloadUrl.isEmpty()) {
            downloadUrl = FALLBACK_PLUGMANX_URL;
        }

        boolean success = downloadFile(downloadUrl, targetJar);
        if (success && targetJar.exists() && targetJar.length() > 0) {
            Bukkit.getLogger().info("[UniverseTitan] PlugManX instalado exitosamente en plugins/ (" + targetJar.length() + " bytes).");
        } else {
            Bukkit.getLogger().warning("[UniverseTitan] No se pudo descargar PlugManX automáticamente.");
        }
    }

    private static String fetchLatestDownloadUrl() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(MODRINTH_PLUGMANX_API);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "UniversePurpur-TitanEngine");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);

            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder json = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        json.append(line);
                    }
                    String text = json.toString();
                    int urlIdx = text.indexOf("\"url\":\"");
                    if (urlIdx != -1) {
                        int start = urlIdx + 7;
                        int end = text.indexOf("\"", start);
                        if (end != -1) {
                            String foundUrl = text.substring(start, end).replace("\\/", "/");
                            if (foundUrl.endsWith(".jar")) {
                                return foundUrl;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignore) {
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Throwable ignore) {}
            }
        }
        return FALLBACK_PLUGMANX_URL;
    }

    private static boolean downloadFile(String urlStr, File destination) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "UniversePurpur-TitanEngine");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);
            conn.setInstanceFollowRedirects(true);

            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP || code == 307 || code == 308) {
                String newUrl = conn.getHeaderField("Location");
                if (newUrl != null) {
                    conn.disconnect();
                    return downloadFile(newUrl, destination);
                }
            }

            if (code != 200) {
                return false;
            }

            File tempFile = new File(destination.getParentFile(), destination.getName() + ".tmp");
            try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }

            if (destination.exists()) {
                destination.delete();
            }
            return tempFile.renameTo(destination);
        } catch (Throwable ex) {
            return false;
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Throwable ignore) {}
            }
        }
    }
}
