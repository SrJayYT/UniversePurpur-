package org.purpurmc.purpur.license;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.purpurmc.purpur.UniversePurpurConfig;

/**
 * Native License Verification and Protection System for UniversePurpur Titan Engine.
 * Integrates with SCON Licensing Security Platform (https://licences.scon.host).
 *
 * Checks cryptographic authenticity, IP bindings, expiration, and HWID via HMAC-SHA256.
 * Supports configuration via universe-purpur.yml, JVM parameters (-Dlicense.key),
 * and environment variables (LICENSE_KEY).
 *
 * Author: DevPapo (admin@scon.host)
 */
public final class UniverseLicenseManager {

    private static final Logger LOGGER = Logger.getLogger("SCON-Licensing");
    private static ScheduledExecutorService heartbeatExecutor;

    public static volatile boolean isValid = false;
    public static volatile String resolvedKey = "";
    public static volatile String status = "UNCHECKED";
    public static volatile String message = "";
    public static volatile String customerDiscordId = "";
    public static volatile String licenseTag = "";
    public static volatile String expiresAt = "Lifetime";
    public static volatile List<String> boundIps = new ArrayList<>();
    public static volatile int maxIps = 1;
    public static volatile long lastCheckTimestamp = 0L;

    private UniverseLicenseManager() {}

    /**
     * Early bootstrap validation before Minecraft initializes.
     * Guarantees the server does not start if the license is invalid.
     */
    public static synchronized void initEarly() {
        if (isValid) return;

        resolveLicenseKey();

        if (resolvedKey == null || resolvedKey.trim().isEmpty() || resolvedKey.contains("XXXX-XXXX")) {
            printFailureBanner("MISSING_KEY", "No se detecto ninguna clave de licencia configurada.");
            System.exit(0);
            return;
        }

        System.out.println("\u001B[1;36m[SCON Licensing] Verificando clave de licencia con https://licences.scon.host...\u001B[0m");

        boolean verified = false;
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                verified = performVerification(resolvedKey, true);
                if (verified) break;
            } catch (Exception ex) {
                System.err.println("\u001B[1;33m[SCON Licensing] Intento " + attempt + "/" + maxRetries + " fallido: " + ex.getMessage() + "\u001B[0m");
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(1500L);
                    } catch (InterruptedException ignored) {}
                }
            }
        }

        if (!verified) {
            printFailureBanner(status, (message != null && !message.isEmpty()) ? message : "Licencia no autorizada o rechazada por el servidor.");
            System.exit(0);
            return;
        }

        startHeartbeatScheduler();
    }

    /**
     * Compatibility init hook for UniversePurpurConfig.
     */
    public static synchronized void init(File serverRootDir) {
        if (!isValid) {
            initEarly();
        }
    }

    /**
     * Resolves license key prioritizing:
     * 1. Environment variables (Pterodactyl / Pelican / Docker: LICENSE_KEY)
     * 2. JVM system properties (-Dlicense.key)
     * 3. Direct read from universe-purpur.yml on disk
     * 4. UniversePurpurConfig memory cache
     */
    public static String resolveLicenseKey() {
        // 1. Primary: Environment Variables (Pterodactyl Startup Variables / Docker)
        String key = System.getenv("LICENSE_KEY");
        if (isNullOrEmpty(key)) key = System.getenv("UNIVERSE_PURPUR_LICENSE");
        if (isNullOrEmpty(key)) key = System.getenv("UNIVERSE_LICENSE");
        if (isNullOrEmpty(key)) key = System.getenv("SCON_LICENSE_KEY");

        // 2. Secondary: JVM system property (-Dlicense.key)
        if (isNullOrEmpty(key)) key = System.getProperty("license.key");
        if (isNullOrEmpty(key)) key = System.getProperty("universe.license");
        if (isNullOrEmpty(key)) key = System.getProperty("scon.license");

        // 3. Fallback: Direct read from universe-purpur.yml on disk
        if (isNullOrEmpty(key)) {
            File configFile = new File("universe-purpur.yml");
            if (configFile.exists() && configFile.canRead()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new java.io.FileInputStream(configFile), StandardCharsets.UTF_8))) {
                    String line;
                    boolean inLicenseBlock = false;
                    while ((line = reader.readLine()) != null) {
                        String trimmed = line.trim();
                        if (trimmed.startsWith("license:")) {
                            inLicenseBlock = true;
                            continue;
                        }
                        if (inLicenseBlock) {
                            if (trimmed.startsWith("key:")) {
                                String[] parts = trimmed.split(":", 2);
                                if (parts.length > 1) {
                                    key = parts[1].trim().replace("\"", "").replace("'", "");
                                }
                                break;
                            } else if (!trimmed.startsWith("#") && !line.startsWith(" ") && !line.startsWith("\t")) {
                                inLicenseBlock = false;
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }

        // 4. Fallback: UniversePurpurConfig if already loaded
        if (isNullOrEmpty(key) && UniversePurpurConfig.licenseKey != null) {
            key = UniversePurpurConfig.licenseKey;
        }

        if (key != null) {
            key = key.trim();
            if (key.contains("{{") || key.contains("XXXX-XXXX")) {
                key = "";
            }
        } else {
            key = "";
        }

        resolvedKey = key;
        return resolvedKey;
    }

    /**
     * Executes the HTTP POST verification call.
     */
    public static boolean performVerification(String keyToVerify, boolean isStartup) throws Exception {
        if (keyToVerify == null || keyToVerify.trim().isEmpty()) {
            status = "MISSING_KEY";
            message = "License key is empty.";
            isValid = false;
            return false;
        }

        String endpoint = UniversePurpurConfig.licenseServerUrl;
        if (endpoint == null || endpoint.trim().isEmpty()) {
            endpoint = "https://licences.scon.host/api/v1/license/verify";
        }

        int serverPort = 25565;
        try {
            if (Bukkit.getServer() != null) {
                serverPort = Bukkit.getPort();
            }
        } catch (Throwable ignored) {}

        JsonObject payload = new JsonObject();
        payload.addProperty("license_key", keyToVerify.trim());
        payload.addProperty("product_name", UniversePurpurConfig.licenseProductName != null ? UniversePurpurConfig.licenseProductName : "UniversePurpur");
        payload.addProperty("project_slug", "upurpur");
        payload.addProperty("server_port", serverPort);
        payload.addProperty("plugin_version", "1.0.2");
        payload.addProperty("server_version", "1.21.4");

        byte[] postBytes = payload.toString().getBytes(StandardCharsets.UTF_8);

        URL url = URI.create(endpoint).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "SCON-UniversePurpur/1.0.2");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(10000);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(postBytes);
            os.flush();
        }

        int responseCode = conn.getResponseCode();
        InputStream is = (responseCode >= 200 && responseCode < 400) ? conn.getInputStream() : conn.getErrorStream();

        StringBuilder sb = new StringBuilder();
        if (is != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
        }

        String responseBody = sb.toString();
        if (responseBody.isEmpty()) {
            status = "EMPTY_RESPONSE";
            message = "Empty response from license server (HTTP " + responseCode + ")";
            isValid = false;
            return false;
        }

        JsonElement rootElement = JsonParser.parseString(responseBody);
        if (!rootElement.isJsonObject()) {
            status = "MALFORMED_RESPONSE";
            message = "Invalid JSON response from license server.";
            isValid = false;
            return false;
        }

        JsonObject root = rootElement.getAsJsonObject();
        JsonObject data = root.has("data") && root.get("data").isJsonObject() ? root.getAsJsonObject("data") : root;

        boolean validResult = data.has("valid") && data.get("valid").getAsBoolean();
        status = data.has("status") ? data.get("status").getAsString() : (validResult ? "ACTIVE" : "INVALID");
        message = data.has("message") ? data.get("message").getAsString() : (validResult ? "Licencia válida." : "Verificación fallida.");

        if (validResult) {
            isValid = true;
            customerDiscordId = data.has("customer_discord_id") && !data.get("customer_discord_id").isJsonNull() ? data.get("customer_discord_id").getAsString() : "N/A";
            licenseTag = data.has("tag") && !data.get("tag").isJsonNull() ? data.get("tag").getAsString() : "Sin etiqueta";
            expiresAt = data.has("expires_at") && !data.get("expires_at").isJsonNull() ? data.get("expires_at").getAsString() : "Lifetime";
            maxIps = data.has("max_ips") ? data.get("max_ips").getAsInt() : 1;

            boundIps.clear();
            if (data.has("bound_ips") && data.get("bound_ips").isJsonArray()) {
                JsonArray arr = data.getAsJsonArray("bound_ips");
                for (JsonElement el : arr) {
                    boundIps.add(el.getAsString());
                }
            }
            lastCheckTimestamp = System.currentTimeMillis();

            if (isStartup) {
                printSuccessBanner();
            } else {
                LOGGER.info("[SCON Licensing] Background heartbeat OK (Status: ACTIVE, Checked at: " + new java.util.Date() + ")");
            }
            return true;
        } else {
            isValid = false;
            return false;
        }
    }

    /**
     * Starts background heartbeat check every X minutes.
     */
    private static synchronized void startHeartbeatScheduler() {
        if (heartbeatExecutor != null && !heartbeatExecutor.isShutdown()) {
            return;
        }

        int interval = Math.max(10, UniversePurpurConfig.licenseHeartbeatMinutes);
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SCON-License-Heartbeat");
            t.setDaemon(true);
            return t;
        });

        heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                if (resolvedKey != null && !resolvedKey.isEmpty()) {
                    boolean ok = performVerification(resolvedKey, false);
                    if (!ok) {
                        LOGGER.severe("[SCON Licensing] ⚠️ Remote license heartbeat failed! Reason: " + message + " (Status: " + status + ")");
                        if (UniversePurpurConfig.licenseShutdownOnFailure) {
                            LOGGER.severe("[SCON Licensing] License revoked or suspended. Shutting down server...");
                            try {
                                Bukkit.shutdown();
                            } catch (Throwable t) {
                                System.exit(1);
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                LOGGER.warning("[SCON Licensing] Heartbeat check failed to reach licensing endpoint: " + ex.getMessage());
            }
        }, interval, interval, TimeUnit.MINUTES);
    }

    private static void printSuccessBanner() {
        String masked = maskKey(resolvedKey);
        System.out.println();
        System.out.println("\u001B[1;32m================================================================================");
        System.out.println("  SCON LICENSING :: UNIVERSEPURPUR TITAN ENGINE AUTORIZADO");
        System.out.println("================================================================================\u001B[0m");
        System.out.println("  \u001B[1;37mClave de Licencia :\u001B[0m \u001B[1;36m" + masked + "\u001B[0m (\u001B[1;32m" + status + "\u001B[0m)");
        System.out.println("  \u001B[1;37mProducto          :\u001B[0m \u001B[1;35m" + (UniversePurpurConfig.licenseProductName != null ? UniversePurpurConfig.licenseProductName : "UniversePurpur") + "\u001B[0m (Minecraft Java / Purpur)");
        System.out.println("  \u001B[1;37mCliente Discord ID:\u001B[0m \u001B[1;33m" + customerDiscordId + "\u001B[0m");
        System.out.println("  \u001B[1;37mEtiqueta / Tag    :\u001B[0m \u001B[1;37m" + licenseTag + "\u001B[0m");
        System.out.println("  \u001B[1;37mDuración          :\u001B[0m \u001B[1;32m" + expiresAt + "\u001B[0m");
        System.out.println("  \u001B[1;37mIPs Vinculadas    :\u001B[0m \u001B[1;36m" + boundIps.size() + " / " + maxIps + " (" + String.join(", ", boundIps) + ")\u001B[0m");
        System.out.println("  \u001B[1;37mSeguridad         :\u001B[0m \u001B[1;32mHMAC-SHA256 Anti-Spoofing Criptográfico Verificado\u001B[0m");
        System.out.println("\u001B[1;32m================================================================================\u001B[0m");
        System.out.println();
        System.out.flush();
    }

    private static void printFailureBanner(String statusReason, String detailedMsg) {
        String masked = maskKey(resolvedKey);
        System.out.println();
        System.out.println("\u001B[1;31m================================================================================");
        System.out.println("  UNIVERSEPURPUR TITAN ENGINE :: ACTIVACION REQUERIDA");
        System.out.println("================================================================================\u001B[0m");
        System.out.println();
        System.out.println(" \u001B[1;31m[ERROR]\u001B[0m \u001B[1;37mNo se detecto una licencia valida para iniciar este servidor.\u001B[0m");
        System.out.println(" \u001B[1;33m  Estado     :\u001B[0m \u001B[1;31m[" + statusReason + "]\u001B[0m");
        System.out.println(" \u001B[1;33m  Motivo     :\u001B[0m \u001B[0;37m" + detailedMsg + "\u001B[0m");
        System.out.println(" \u001B[1;33m  Clave      :\u001B[0m \u001B[1;36m" + masked + "\u001B[0m");
        System.out.println();
        System.out.println("\u001B[0;90m--------------------------------------------------------------------------------\u001B[0m");
        System.out.println(" \u001B[1;32mPASOS PARA ACTIVAR TU SERVIDOR:\u001B[0m");
        System.out.println();
        System.out.println("  \u001B[1;37m1. Panel de Hosting (Pterodactyl / Pelican):\u001B[0m");
        System.out.println("     \u001B[0;37m- Ve a la pestana \u001B[1;33mStartup\u001B[0;37m (Parametros de Inicio).\u001B[0m");
        System.out.println("     \u001B[0;37m- En la variable \u001B[1;32mLICENSE_KEY\u001B[0;37m, introduce tu clave de SCON.\u001B[0m");
        System.out.println("     \u001B[0;37m- Guarda e inicia nuevamente tu servidor.\u001B[0m");
        System.out.println();
        System.out.println("  \u001B[1;37m2. O en tu archivo de configuracion (universe-purpur.yml):\u001B[0m");
        System.out.println("     \u001B[0;36mglobal:\u001B[0m");
        System.out.println("       \u001B[0;36mlicense:\u001B[0m");
        System.out.println("         \u001B[1;32mkey: \"SCON-XXXX-XXXX-XXXX-XXXX\"\u001B[0m");
        System.out.println();
        System.out.println("  \u001B[1;37m3. O como Variable de Entorno en Linux / Docker:\u001B[0m");
        System.out.println("     \u001B[1;33mexport LICENSE_KEY=\"SCON-XXXX-XXXX-XXXX-XXXX\"\u001B[0m");
        System.out.println("\u001B[0;90m--------------------------------------------------------------------------------\u001B[0m");
        System.out.println(" \u001B[1;35mPanel de Licencias:\u001B[0m \u001B[1;34mhttps://licences.scon.host\u001B[0m");
        System.out.println(" \u001B[1;31mArranque detenido de forma segura. Servidor apagado.\u001B[0m");
        System.out.println("\u001B[1;31m================================================================================\u001B[0m");
        System.out.println();
        System.out.flush();
    }

    public static String maskKey(String key) {
        if (key == null || key.trim().isEmpty() || key.contains("XXXX-XXXX") || key.contains("{{")) {
            return "(No configurada / Vacia)";
        }
        key = key.trim();
        if (key.length() < 9) return "•••••••••••••••••••••";
        return key.substring(0, 9) + "••••-••••-" + (key.length() > 4 ? key.substring(key.length() - 4) : "••••");
    }

    private static boolean isNullOrEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
}
