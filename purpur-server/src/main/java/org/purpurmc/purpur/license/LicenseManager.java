package org.purpurmc.purpur.license;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Universal SCON License Manager API for Bukkit plugins and server extensions.
 * Provides asynchronous verification directly against SCON Licensing Platform.
 * 
 * End-user plugins can invoke:
 *   LicenseManager.verifyLicenseAsync(this, licenseKey, "MyPlugin")
 *       .thenAccept(result -> { ... });
 */
public class LicenseManager {

    private static final String API_ENDPOINT = "https://licences.scon.host/api/v1/license/verify";

    public enum ValidationResult {
        VALID,
        INVALID_KEY,
        SUSPENDED,
        EXPIRED,
        IP_LIMIT_EXCEEDED,
        BLACKLISTED,
        NETWORK_ERROR
    }

    /**
     * Verifies a license asynchronously for a JavaPlugin instance.
     *
     * @param plugin The JavaPlugin instance running the check.
     * @param licenseKey The license key string provided by the user.
     * @param productName The registered product name in SCON Licensing.
     * @return CompletableFuture with ValidationResult.
     */
    public static CompletableFuture<ValidationResult> verifyLicenseAsync(
            JavaPlugin plugin, 
            String licenseKey, 
            String productName
    ) {
        String version = (plugin != null && plugin.getDescription() != null) 
                ? plugin.getDescription().getVersion() 
                : "1.0.0";
        Logger logger = (plugin != null) ? plugin.getLogger() : Logger.getLogger("LicenseManager");
        return verifyInternal(licenseKey, productName, version, logger);
    }

    /**
     * Verifies a license asynchronously without requiring a JavaPlugin instance.
     *
     * @param licenseKey The license key string provided by the user.
     * @param productName The registered product name in SCON Licensing.
     * @return CompletableFuture with ValidationResult.
     */
    public static CompletableFuture<ValidationResult> verifyLicenseAsync(
            String licenseKey, 
            String productName
    ) {
        return verifyInternal(licenseKey, productName, "1.0.3", Logger.getLogger("LicenseManager"));
    }

    private static CompletableFuture<ValidationResult> verifyInternal(
            String licenseKey, 
            String productName,
            String clientVersion,
            Logger logger
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = URI.create(API_ENDPOINT).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "SCON-Minecraft/" + clientVersion);
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.setDoOutput(true);

                int port = 25565;
                String serverVer = "1.21.4";
                try {
                    if (Bukkit.getServer() != null) {
                        port = Bukkit.getPort();
                        serverVer = Bukkit.getVersion();
                    }
                } catch (Throwable ignored) {}

                JsonObject json = new JsonObject();
                json.addProperty("license_key", licenseKey != null ? licenseKey.trim() : "");
                json.addProperty("product_name", productName != null ? productName : "UniversePurpur");
                json.addProperty("server_port", port);
                json.addProperty("plugin_version", clientVersion);
                json.addProperty("server_version", serverVer);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.toString().getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                InputStream is = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
                StringBuilder response = new StringBuilder();
                if (is != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                    }
                }

                JsonObject root = JsonParser.parseString(response.toString()).getAsJsonObject();
                if (!root.has("data")) return ValidationResult.INVALID_KEY;

                JsonObject data = root.getAsJsonObject("data");
                boolean isValid = data.has("valid") && data.get("valid").getAsBoolean();
                String status = data.has("status") ? data.get("status").getAsString() : "INVALID";

                if (isValid) return ValidationResult.VALID;

                switch (status) {
                    case "SUSPENDED": return ValidationResult.SUSPENDED;
                    case "EXPIRED": return ValidationResult.EXPIRED;
                    case "IP_LIMIT_EXCEEDED": return ValidationResult.IP_LIMIT_EXCEEDED;
                    case "BLACKLISTED": return ValidationResult.BLACKLISTED;
                    default: return ValidationResult.INVALID_KEY;
                }
            } catch (Exception e) {
                if (logger != null) {
                    logger.warning("[License] Error al conectar con SCON: " + e.getMessage());
                }
                return ValidationResult.NETWORK_ERROR;
            }
        });
    }
}
