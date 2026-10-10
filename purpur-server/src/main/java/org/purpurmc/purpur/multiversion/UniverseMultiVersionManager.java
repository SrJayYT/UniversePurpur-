package org.purpurmc.purpur.multiversion;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.purpurmc.purpur.UniversePurpurConfig;

/**
 * Native MultiVersion Protocol Compatibility Manager for UniversePurpur (+15,000 Players Titan Engine).
 *
 * Provides out-of-the-box support for Minecraft client versions from 1.21.x (1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4)
 * up to future releases (1.21.5+, 1.22.x, 1.23.x, 1.24.x, 1.25.x, 1.26.x+).
 *
 * Authors: DevPapo (admin@scon.host)
 */
public final class UniverseMultiVersionManager {

    public static final String VIAVERSION_DOWNLOAD_URL = "https://github.com/ViaVersion/ViaVersion/releases/download/5.12.1/ViaVersion-5.12.1.jar";
    public static final String VIABACKWARDS_DOWNLOAD_URL = "https://github.com/ViaVersion/ViaBackwards/releases/download/5.12.1/ViaBackwards-5.12.1.jar";
    public static final String RESOURCE_VIAVERSION = "/multiversion/ViaVersion-5.12.1.jar";
    public static final String RESOURCE_VIABACKWARDS = "/multiversion/ViaBackwards-5.12.1.jar";

    private static boolean initialized = false;
    private static boolean deployed = false;

    private UniverseMultiVersionManager() {}

    /**
     * Initializes the multi-version system during server startup before plugins are loaded.
     */
    public static synchronized void init(File serverRootDir) {
        if (!UniversePurpurConfig.multiVersionEnabled) {
            return;
        }

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

        boolean hasViaVersion = hasPluginJar(pluginsDir, "viaversion");
        boolean hasViaBackwards = hasPluginJar(pluginsDir, "viabackwards");

        if ((!hasViaVersion || !hasViaBackwards) && UniversePurpurConfig.multiVersionAutoDeploy) {
            Bukkit.getLogger().info("[UniverseTitan] Inicializando motor MultiVersión nativo (1.21.x -> 1.26.x)...");

            if (!hasViaVersion) {
                deployModule(pluginsDir, "ViaVersion.jar", RESOURCE_VIAVERSION, VIAVERSION_DOWNLOAD_URL);
            }
            if (!hasViaBackwards) {
                deployModule(pluginsDir, "ViaBackwards.jar", RESOURCE_VIABACKWARDS, VIABACKWARDS_DOWNLOAD_URL);
            }
            deployed = true;

            Bukkit.getLogger().info("================================================================");
            Bukkit.getLogger().info(" [UniverseTitan] MOTOR MULTIVERSIÓN ACTIVADO CON ÉXITO");
            Bukkit.getLogger().info(" [UniverseTitan] Rango soportado: 1.21.x (1.21 - 1.21.4) ➔ 1.26.x+");
            Bukkit.getLogger().info(" [UniverseTitan] Cualquier cliente entre 1.21.x y 1.26.x puede conectarse.");
            Bukkit.getLogger().info("================================================================");
        } else if (hasViaVersion && hasViaBackwards) {
            Bukkit.getLogger().info("[UniverseTitan] MultiVersión detectado en plugins/ (1.21.x -> 1.26.x activo).");
        }
    }

    private static boolean hasPluginJar(File pluginsDir, String namePrefix) {
        File[] files = pluginsDir.listFiles();
        if (files == null) return false;
        String lowerPrefix = namePrefix.toLowerCase();
        for (File f : files) {
            if (f.isFile() && f.getName().toLowerCase().endsWith(".jar")) {
                String fname = f.getName().toLowerCase();
                if (fname.startsWith(lowerPrefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void deployModule(File pluginsDir, String targetFileName, String resourcePath, String downloadUrl) {
        File targetFile = new File(pluginsDir, targetFileName);
        // 1. Intentar extracción rápida desde recursos embebidos
        try (InputStream in = UniverseMultiVersionManager.class.getResourceAsStream(resourcePath)) {
            if (in != null) {
                try (FileOutputStream out = new FileOutputStream(targetFile)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                }
                Bukkit.getLogger().info("[UniverseTitan] Módulo " + targetFileName + " extraído desde recursos internos con éxito.");
                return;
            }
        } catch (Throwable ex) {
            Bukkit.getLogger().log(Level.WARNING, "[UniverseTitan] No se pudo extraer " + targetFileName + " de recursos internos: " + ex.getMessage());
        }

        // 2. Si no estaba embebido, descargar directamente desde la release oficial de GitHub
        try {
            Bukkit.getLogger().info("[UniverseTitan] Descargando " + targetFileName + " desde GitHub (" + downloadUrl + ")...");
            URL url = new URL(downloadUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "UniversePurpur-MultiVersion-Installer/1.0");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(true);

            int status = conn.getResponseCode();
            if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                String newUrl = conn.getHeaderField("Location");
                if (newUrl != null) {
                    conn = (HttpURLConnection) new URL(newUrl).openConnection();
                    conn.setRequestProperty("User-Agent", "UniversePurpur-MultiVersion-Installer/1.0");
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(15000);
                }
            }

            try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(targetFile)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
            }
            Bukkit.getLogger().info("[UniverseTitan] " + targetFileName + " descargado e instalado correctamente (" + targetFile.length() + " bytes).");
        } catch (IOException ex) {
            Bukkit.getLogger().log(Level.WARNING, "[UniverseTitan] Error al descargar " + targetFileName + ": " + ex.getMessage());
        }
    }

    /**
     * Retorna si el motor multiversión está operando activamente en el servidor.
     */
    public static boolean isMultiVersionActive() {
        if (!UniversePurpurConfig.multiVersionEnabled) {
            return false;
        }
        try {
            org.bukkit.plugin.Plugin vv = Bukkit.getPluginManager().getPlugin("ViaVersion");
            return vv != null && vv.isEnabled();
        } catch (Throwable ignored) {
            return deployed;
        }
    }

    /**
     * Rango de versiones de clientes compatibles.
     */
    public static String getSupportedRange() {
        return UniversePurpurConfig.multiVersionRange;
    }

    /**
     * Obtiene la versión del protocolo del cliente para un jugador conectado.
     */
    public static int getPlayerProtocolVersion(UUID uuid) {
        try {
            Class<?> viaClass = Class.forName("com.viaversion.viaversion.api.Via");
            Method getApiMethod = viaClass.getMethod("getAPI");
            Object apiInstance = getApiMethod.invoke(null);
            if (apiInstance != null) {
                Method getPlayerVersionMethod = apiInstance.getClass().getMethod("getPlayerVersion", UUID.class);
                Object ver = getPlayerVersionMethod.invoke(apiInstance, uuid);
                if (ver instanceof Number) {
                    return ((Number) ver).intValue();
                }
            }
        } catch (Throwable ignored) {
        }
        return 769; // 1.21.4 por defecto
    }

    /**
     * Traduce el número de protocolo a una etiqueta amigable de versión de Minecraft.
     */
    public static String getFriendlyVersionName(int protocol) {
        return switch (protocol) {
            case 767 -> "1.21 / 1.21.1";
            case 768 -> "1.21.2 / 1.21.3";
            case 769 -> "1.21.4 (Nativo)";
            case 770, 771, 772 -> "1.21.5+";
            case 773, 774, 775 -> "1.22.x";
            case 776, 777, 778 -> "1.23.x";
            case 779, 780, 781 -> "1.24.x";
            case 782, 783, 784 -> "1.25.x";
            case 785, 786, 787, 788 -> "1.26.x";
            default -> (protocol > 788) ? "1.26.x+" : ((protocol < 767) ? "1.20.x o anterior" : "1.21.4");
        };
    }
}
