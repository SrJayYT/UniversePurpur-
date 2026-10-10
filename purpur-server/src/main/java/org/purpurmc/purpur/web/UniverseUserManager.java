package org.purpurmc.purpur.web;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;

/**
 * Authentication and Multi-User Management Engine for UniversePurpur Web Admin Panel.
 * Supports hashed passwords, session token lifecycles, and role-based permissions (ADMIN, MODERATOR).
 *
 * Author: DevPapo
 */
public final class UniverseUserManager {
    private static UniverseUserManager instance;

    public enum Role {
        SUPER_ADMIN, // Full server control, console, and user management
        MODERATOR    // Player moderation (kick, ban, mute) and telemetry viewing
    }

    public static class User {
        public String username;
        public String salt;
        public String passwordHash;
        public Role role;
        public long createdAt;

        public User(String username, String salt, String passwordHash, Role role, long createdAt) {
            this.username = username;
            this.salt = salt;
            this.passwordHash = passwordHash;
            this.role = role;
            this.createdAt = createdAt;
        }
    }

    public static class Session {
        public String token;
        public String username;
        public Role role;
        public long expiresAt;

        public Session(String token, String username, Role role, long expiresAt) {
            this.token = token;
            this.username = username;
            this.role = role;
            this.expiresAt = expiresAt;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final Map<String, Session> activeSessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private File storageFile;

    private UniverseUserManager() {
        storageFile = new File("universe-users.json");
        loadUsers();
        // Ensure default admin user exists if empty
        if (users.isEmpty()) {
            createUser("admin", "universe15k", Role.SUPER_ADMIN);
        }
    }

    public static synchronized UniverseUserManager instance() {
        if (instance == null) {
            instance = new UniverseUserManager();
        }
        return instance;
    }

    public boolean authenticate(String username, String password) {
        if (username == null || password == null) return false;
        User user = users.get(username.toLowerCase());
        if (user == null) return false;
        String computedHash = hashPassword(password, user.salt);
        return computedHash.equals(user.passwordHash);
    }

    public Session createSession(String username) {
        User user = users.get(username.toLowerCase());
        if (user == null) return null;
        String token = UUID.randomUUID().toString().replace("-", "") + Long.toHexString(System.currentTimeMillis());
        long expiry = System.currentTimeMillis() + (7L * 24L * 3600L * 1000L); // 7 days
        Session session = new Session(token, user.username, user.role, expiry);
        activeSessions.put(token, session);
        return session;
    }

    public Session validateSession(String token) {
        if (token == null || token.trim().isEmpty()) return null;
        Session session = activeSessions.get(token.trim());
        if (session == null) return null;
        if (session.isExpired()) {
            activeSessions.remove(token.trim());
            return null;
        }
        return session;
    }

    public void invalidateSession(String token) {
        if (token != null) {
            activeSessions.remove(token.trim());
        }
    }

    public boolean createUser(String username, String password, Role role) {
        if (username == null || password == null || username.trim().isEmpty() || password.length() < 4) {
            return false;
        }
        String lowerName = username.trim().toLowerCase();
        if (users.containsKey(lowerName)) {
            return false;
        }

        byte[] saltBytes = new byte[16];
        random.nextBytes(saltBytes);
        String salt = Base64.getEncoder().encodeToString(saltBytes);
        String hash = hashPassword(password, salt);

        User user = new User(username.trim(), salt, hash, role, System.currentTimeMillis());
        users.put(lowerName, user);
        saveUsers();
        return true;
    }

    public boolean deleteUser(String username) {
        if (username == null) return false;
        String lower = username.trim().toLowerCase();
        if (lower.equals("admin")) return false; // Protect root admin
        if (users.remove(lower) != null) {
            saveUsers();
            return true;
        }
        return false;
    }

    public Map<String, User> getUsers() {
        return users;
    }

    private String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hashed = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (Throwable t) {
            throw new RuntimeException("SHA-256 not supported", t);
        }
    }

    private void saveUsers() {
        try (FileWriter writer = new FileWriter(storageFile, StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            sb.append("[\n");
            boolean first = true;
            for (User u : users.values()) {
                if (!first) sb.append(",\n");
                first = false;
                sb.append(String.format("  {\"username\":\"%s\",\"salt\":\"%s\",\"hash\":\"%s\",\"role\":\"%s\",\"createdAt\":%d}",
                        u.username, u.salt, u.passwordHash, u.role.name(), u.createdAt));
            }
            sb.append("\n]");
            writer.write(sb.toString());
        } catch (Throwable ignore) {
        }
    }

    private void loadUsers() {
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
                    String uname = extractField(raw, "username");
                    String salt = extractField(raw, "salt");
                    String hash = extractField(raw, "hash");
                    String rStr = extractField(raw, "role");
                    long created = System.currentTimeMillis();
                    try {
                        String cStr = extractField(raw, "createdAt");
                        if (cStr != null) created = Long.parseLong(cStr);
                    } catch (Throwable ignore) {}

                    if (uname != null && salt != null && hash != null) {
                        Role role = Role.SUPER_ADMIN;
                        if (rStr != null && rStr.equalsIgnoreCase("MODERATOR")) role = Role.MODERATOR;
                        users.put(uname.toLowerCase(), new User(uname, salt, hash, role, created));
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
