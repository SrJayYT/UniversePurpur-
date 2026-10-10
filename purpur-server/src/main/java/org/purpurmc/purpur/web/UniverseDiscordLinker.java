package org.purpurmc.purpur.web;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Account Linking Engine between Minecraft and Discord for UniversePurpur.
 * Manages 6-digit verification codes, persistent JSON links, and REST endpoints for Discord Bots.
 *
 * Author: DevPapo
 */
public final class UniverseDiscordLinker {
    private static UniverseDiscordLinker instance;

    public static class LinkCode {
        public String code;
        public UUID playerUuid;
        public String playerName;
        public long expiresAt;

        public LinkCode(String code, UUID playerUuid, String playerName, long expiresAt) {
            this.code = code;
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.expiresAt = expiresAt;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    public static class LinkedAccount {
        public UUID playerUuid;
        public String playerName;
        public String discordId;
        public String discordTag;
        public long linkedAt;

        public LinkedAccount(UUID playerUuid, String playerName, String discordId, String discordTag, long linkedAt) {
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.discordId = discordId;
            this.discordTag = discordTag;
            this.linkedAt = linkedAt;
        }
    }

    // Active pending verification codes: Code -> LinkCode
    private final Map<String, LinkCode> pendingCodes = new ConcurrentHashMap<>();
    // Player UUID -> Active Code
    private final Map<UUID, String> playerToCode = new ConcurrentHashMap<>();
    // Permanent links: Player UUID -> LinkedAccount
    private final Map<UUID, LinkedAccount> linkedByPlayer = new ConcurrentHashMap<>();
    // Discord ID -> LinkedAccount
    private final Map<String, LinkedAccount> linkedByDiscord = new ConcurrentHashMap<>();

    private final SecureRandom random = new SecureRandom();
    private final File storageFile;

    private UniverseDiscordLinker() {
        File baseDir = org.purpurmc.purpur.UniversePurpurConfig.SERVER_ROOT_DIR != null
                ? org.purpurmc.purpur.UniversePurpurConfig.SERVER_ROOT_DIR : new File(".");
        this.storageFile = new File(baseDir, "universe-discord-links.json");
        loadLinks();
    }

    public static synchronized UniverseDiscordLinker instance() {
        if (instance == null) {
            instance = new UniverseDiscordLinker();
        }
        return instance;
    }

    /**
     * Generates a 6-digit verification code for an in-game player (valid for 5 minutes).
     */
    public String generateCode(Player player) {
        if (player == null) return null;
        UUID uuid = player.getUniqueId();

        // Invalidate old code if present
        String oldCode = playerToCode.remove(uuid);
        if (oldCode != null) {
            pendingCodes.remove(oldCode);
        }

        int num = 100000 + random.nextInt(900000);
        String code = "UP-" + num;
        long expires = System.currentTimeMillis() + (5L * 60L * 1000L); // 5 minutes

        LinkCode lc = new LinkCode(code, uuid, player.getName(), expires);
        pendingCodes.put(code, lc);
        playerToCode.put(uuid, code);

        return code;
    }

    /**
     * Verifies and completes account linking via public web or bot.
     */
    public boolean verifyAndLink(String code, String discordId, String discordTag) {
        if (code == null || discordId == null) return false;
        String cleanCode = code.trim().toUpperCase();
        if (!cleanCode.startsWith("UP-") && cleanCode.length() == 6) {
            cleanCode = "UP-" + cleanCode;
        }

        LinkCode lc = pendingCodes.get(cleanCode);
        if (lc == null || lc.isExpired()) {
            pendingCodes.remove(cleanCode);
            return false;
        }

        String cleanDiscordId = discordId.trim();
        String cleanDiscordTag = discordTag != null ? discordTag.trim() : cleanDiscordId;

        // Clean up any existing reverse mappings to avoid dangling references
        LinkedAccount prevByPlayer = linkedByPlayer.remove(lc.playerUuid);
        if (prevByPlayer != null) {
            linkedByDiscord.remove(prevByPlayer.discordId);
        }
        LinkedAccount prevByDiscord = linkedByDiscord.remove(cleanDiscordId);
        if (prevByDiscord != null) {
            linkedByPlayer.remove(prevByDiscord.playerUuid);
        }

        // Link account
        LinkedAccount account = new LinkedAccount(
                lc.playerUuid,
                lc.playerName,
                cleanDiscordId,
                cleanDiscordTag,
                System.currentTimeMillis()
        );

        linkedByPlayer.put(lc.playerUuid, account);
        linkedByDiscord.put(cleanDiscordId, account);

        // Remove used code
        pendingCodes.remove(cleanCode);
        playerToCode.remove(lc.playerUuid);

        saveLinks();
        return true;
    }

    public boolean unlink(UUID uuid) {
        if (uuid == null) return false;
        LinkedAccount removed = linkedByPlayer.remove(uuid);
        if (removed != null) {
            linkedByDiscord.remove(removed.discordId);
            saveLinks();
            return true;
        }
        return false;
    }

    public boolean unlinkByDiscord(String discordId) {
        if (discordId == null) return false;
        LinkedAccount removed = linkedByDiscord.remove(discordId.trim());
        if (removed != null) {
            linkedByPlayer.remove(removed.playerUuid);
            saveLinks();
            return true;
        }
        return false;
    }

    public LinkedAccount getByPlayer(UUID uuid) {
        return linkedByPlayer.get(uuid);
    }

    public LinkedAccount getByDiscord(String discordId) {
        if (discordId == null) return null;
        return linkedByDiscord.get(discordId.trim());
    }

    public LinkedAccount getByPlayerName(String name) {
        if (name == null) return null;
        for (LinkedAccount acc : linkedByPlayer.values()) {
            if (acc.playerName.equalsIgnoreCase(name.trim())) {
                return acc;
            }
        }
        return null;
    }

    public Map<UUID, LinkedAccount> getAllLinks() {
        return linkedByPlayer;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private void saveLinks() {
        try (FileWriter writer = new FileWriter(storageFile, StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            sb.append("[\n");
            boolean first = true;
            for (LinkedAccount acc : linkedByPlayer.values()) {
                if (!first) sb.append(",\n");
                first = false;
                sb.append(String.format("  {\"uuid\":\"%s\",\"name\":\"%s\",\"discordId\":\"%s\",\"discordTag\":\"%s\",\"linkedAt\":%d}",
                        escapeJson(acc.playerUuid.toString()), escapeJson(acc.playerName), escapeJson(acc.discordId), escapeJson(acc.discordTag), acc.linkedAt));
            }
            sb.append("\n]");
            writer.write(sb.toString());
        } catch (Throwable ignore) {
        }
    }


    private void loadLinks() {
        if (!storageFile.exists()) return;
        try (FileReader reader = new FileReader(storageFile, StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[1024];
            int read;
            while ((read = reader.read(buf)) != -1) {
                sb.append(buf, 0, read);
            }
            String content = sb.toString().trim();
            if (content.startsWith("[") && content.endsWith("]")) {
                content = content.substring(1, content.length() - 1).trim();
                String[] entries = content.split("},\\s*\\{");
                for (String raw : entries) {
                    raw = raw.replace("{", "").replace("}", "").trim();
                    if (raw.isEmpty()) continue;
                    String uStr = extractField(raw, "uuid");
                    String name = extractField(raw, "name");
                    String did = extractField(raw, "discordId");
                    String dtag = extractField(raw, "discordTag");
                    long linkedAt = System.currentTimeMillis();
                    try {
                        String lStr = extractField(raw, "linkedAt");
                        if (lStr != null) linkedAt = Long.parseLong(lStr);
                    } catch (Throwable ignore) {}

                    if (uStr != null && name != null && did != null) {
                        UUID uuid = UUID.fromString(uStr);
                        LinkedAccount acc = new LinkedAccount(uuid, name, did, dtag != null ? dtag : did, linkedAt);
                        linkedByPlayer.put(uuid, acc);
                        linkedByDiscord.put(did, acc);
                    }
                }
            }
        } catch (Throwable ignore) {
        }
    }

    private String extractField(String raw, String key) {
        String pattern = "\"" + key + "\":\"";
        int idx = raw.indexOf(pattern);
        if (idx == -1) {
            pattern = "\"" + key + "\":";
            idx = raw.indexOf(pattern);
            if (idx == -1) return null;
            int start = idx + pattern.length();
            int end = raw.indexOf(",", start);
            if (end == -1) end = raw.length();
            return raw.substring(start, end).replace("\"", "").trim();
        }
        int start = idx + pattern.length();
        int end = raw.indexOf("\"", start);
        if (end == -1) return null;
        return raw.substring(start, end);
    }
}
