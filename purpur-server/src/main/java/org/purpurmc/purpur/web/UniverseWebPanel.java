package org.purpurmc.purpur.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.purpurmc.purpur.UniversePurpurConfig;
import org.purpurmc.purpur.network.AntiCrashShield;
import org.purpurmc.purpur.task.UniverseGovernorTask;
import org.purpurmc.purpur.task.UniverseMemoryGovernor;
import org.purpurmc.purpur.util.MinecraftInternalPlugin;

/**
 * Enterprise Web Infrastructure for UniversePurpur (+15,000 Players).
 * Hosts:
 *  1. Public Web Portal: Server status, live player counter, 1-click IP copy, Discord Account Linking.
 *  2. Secured Admin Panel: Authentication, User & Role Manager, Branding Customizer, Full Console & Moderation.
 *  3. REST API for Discord Bot and Webhooks.
 *
 * Thread-safe with AsyncCatcher compliance and zero-dependency local asset serving.
 * Author: DevPapo
 */
public final class UniverseWebPanel {
    private static UniverseWebPanel instance;
    private static final MinecraftInternalPlugin PLUGIN = new MinecraftInternalPlugin();

    private HttpServer server;
    private final java.util.concurrent.atomic.AtomicInteger cachedLoadedChunks = new java.util.concurrent.atomic.AtomicInteger(0);
    private org.bukkit.event.Listener chunkListener;
    private boolean running = false;

    private UniverseWebPanel() {}

    public static synchronized UniverseWebPanel instance() {
        if (instance == null) {
            instance = new UniverseWebPanel();
        }
        return instance;
    }

    public synchronized void start() {
        if (running) {
            stop();
        }

        if (!UniversePurpurConfig.webPanelEnabled) {
            return;
        }

        try {
            int port = UniversePurpurConfig.webPanelPort;
            String host = UniversePurpurConfig.webPanelBindAddress;
            InetSocketAddress address = (host == null || host.isEmpty() || host.equals("0.0.0.0"))
                    ? new InetSocketAddress(port)
                    : new InetSocketAddress(host, port);

            server = HttpServer.create(address, 64);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

            // Unified Web Routing
            server.createContext("/", new RouterHandler());

            server.start();
            running = true;

            // Zero-CPU Event-Driven Chunk Counter (Eliminates recurring Chunk[] polling allocations)
            Bukkit.getScheduler().runTask(PLUGIN, () -> {
                try {
                    int count = 0;
                    for (World w : Bukkit.getWorlds()) {
                        count += w.getLoadedChunks().length;
                    }
                    cachedLoadedChunks.set(count);
                } catch (Throwable ignore) {
                }
            });

            if (chunkListener == null) {
                chunkListener = new org.bukkit.event.Listener() {
                    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
                    public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
                        cachedLoadedChunks.incrementAndGet();
                    }

                    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
                    public void onChunkUnload(org.bukkit.event.world.ChunkUnloadEvent event) {
                        cachedLoadedChunks.updateAndGet(c -> Math.max(0, c - 1));
                    }
                };
                Bukkit.getPluginManager().registerEvents(chunkListener, PLUGIN);
            }

            String displayHost = UniversePurpurConfig.webServerIpAddress;
            if (displayHost == null || displayHost.isEmpty() || displayHost.contains("yournetwork") || displayHost.contains("universenetwork")) {
                String envHost = System.getenv("SERVER_IP");
                if (envHost != null && !envHost.isEmpty() && !envHost.equals("0.0.0.0")) {
                    displayHost = envHost;
                } else {
                    displayHost = "panel.scon.host";
                }
            }

            Bukkit.getLogger().info(String.format(
                    "\u001B[32m[UniversePurpur] Web Server online: Public Portal at http://%s:%d | Admin Panel at http://%s:%d/admin\u001B[0m",
                    displayHost, port,
                    displayHost, port
            ));
        } catch (Throwable ex) {
            Bukkit.getLogger().log(Level.WARNING, "[UniversePurpur] Could not start Web Panel on port " + UniversePurpurConfig.webPanelPort, ex);
            running = false;
        }
    }

    public synchronized void stop() {
        if (server != null) {
            try {
                server.stop(0);
            } catch (Throwable ignore) {
            }
            server = null;
        }
        if (chunkListener != null) {
            try {
                org.bukkit.event.HandlerList.unregisterAll(chunkListener);
            } catch (Throwable ignore) {
            }
            chunkListener = null;
        }
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    public int getCachedLoadedChunks() {
        return Math.max(0, cachedLoadedChunks.get());
    }



    private static UniverseUserManager.Session getSession(HttpExchange exchange) {
        String token = null;
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7).trim();
        }

        if (token == null) {
            String query = exchange.getRequestURI().getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] pair = param.split("=", 2);
                    if (pair.length == 2 && pair[0].equalsIgnoreCase("token")) {
                        token = pair[1];
                        break;
                    }
                }
            }
        }

        // Backward-compatibility: if token matches master webPanelAuthToken from config
        if (token != null && !token.isEmpty() && token.equals(UniversePurpurConfig.webPanelAuthToken)) {
            return new UniverseUserManager.Session(token, "MasterAdmin", UniverseUserManager.Role.SUPER_ADMIN, Long.MAX_VALUE);
        }

        return UniverseUserManager.instance().validateSession(token);
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendHtmlResponse(HttpExchange exchange, int statusCode, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private class RouterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            if ("OPTIONS".equalsIgnoreCase(method)) {
                sendJsonResponse(exchange, 200, "{}");
                return;
            }

            // 1. Static Asset Serving (/assets/*) with path-traversal hardening
            if (path.startsWith("/assets/")) {
                String sub = path.substring("/assets/".length()).replace('\\', '/');
                if (!sub.contains("..") && !sub.startsWith("/")) {
                    File baseDir = UniversePurpurConfig.SERVER_ROOT_DIR != null ? UniversePurpurConfig.SERVER_ROOT_DIR : new File(".");
                    File assetsDir = new File(baseDir, "assets").getCanonicalFile();
                    File file = new File(assetsDir, sub).getCanonicalFile();
                    if (file.getPath().startsWith(assetsDir.getPath()) && file.exists() && file.isFile()) {
                        byte[] bytes = Files.readAllBytes(file.toPath());
                        String mime = sub.endsWith(".png") ? "image/png"
                                : (sub.endsWith(".jpg") || sub.endsWith(".jpeg")) ? "image/jpeg"
                                : sub.endsWith(".ico") ? "image/x-icon"
                                : "application/octet-stream";
                        exchange.getResponseHeaders().set("Content-Type", mime);
                        exchange.sendResponseHeaders(200, bytes.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(bytes);
                        }
                        return;
                    }
                }
                sendJsonResponse(exchange, 404, "{\"error\":\"Asset Not Found\"}");
                return;
            }


            // 2. Public Web Portal (Root)
            if (path.equals("/") || path.equals("/index.html") || path.equals("/link")) {
                sendHtmlResponse(exchange, 200, PUBLIC_PORTAL_HTML);
                return;
            }

            // 3. Admin Web Panel (/admin)
            if (path.equals("/admin") || path.equals("/admin/")) {
                sendHtmlResponse(exchange, 200, ADMIN_PANEL_HTML);
                return;
            }

            // 4. REST APIs
            if (path.startsWith("/api/")) {
                handleApi(exchange, path, method);
                return;
            }

            // Not Found
            sendJsonResponse(exchange, 404, "{\"error\":\"Not Found\"}");
        }
    }

    private void handleApi(HttpExchange exchange, String path, String method) throws IOException {
        // --- Public Endpoints ---

        // Public Status for Web Portal & Server Lists
        if (path.equals("/api/status") && "GET".equalsIgnoreCase(method)) {
            handleStatusApi(exchange);
            return;
        }

        // Public Account Linking Verification
        if (path.equals("/api/link/verify") && "POST".equalsIgnoreCase(method)) {
            String body = readRequestBody(exchange);
            String code = extractJsonString(body, "code");
            String discordId = extractJsonString(body, "discordId");
            String discordTag = extractJsonString(body, "discordTag");
            if (code == null || discordId == null) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Código y Discord ID requeridos\"}");
                return;
            }
            boolean ok = UniverseDiscordLinker.instance().verifyAndLink(code, discordId, discordTag);
            if (ok) {
                sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"¡Cuenta vinculada con éxito a Discord!\"}");
            } else {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Código inválido o expirado. Genera uno nuevo con /link en el juego.\"}");
            }
            return;
        }

        // Discord Bot API: Lookup by Minecraft Player or Discord ID
        if (path.equals("/api/discord/lookup") && "GET".equalsIgnoreCase(method)) {
            String query = exchange.getRequestURI().getQuery();
            String player = null;
            String discordId = null;
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] pair = param.split("=", 2);
                    if (pair.length == 2) {
                        if (pair[0].equalsIgnoreCase("player")) player = pair[1];
                        if (pair[0].equalsIgnoreCase("discordId")) discordId = pair[1];
                    }
                }
            }
            UniverseDiscordLinker linker = UniverseDiscordLinker.instance();
            UniverseDiscordLinker.LinkedAccount acc = null;
            if (player != null) acc = linker.getByPlayerName(player);
            else if (discordId != null) acc = linker.getByDiscord(discordId);

            if (acc != null) {
                sendJsonResponse(exchange, 200, String.format(Locale.ROOT,
                        "{\"linked\":true,\"uuid\":\"%s\",\"player\":\"%s\",\"discordId\":\"%s\",\"discordTag\":\"%s\",\"linkedAt\":%d}",
                        acc.playerUuid, acc.playerName, acc.discordId, acc.discordTag, acc.linkedAt));
            } else {
                sendJsonResponse(exchange, 404, "{\"linked\":false,\"message\":\"Cuenta no encontrada\"}");
            }
            return;
        }

        // Auth Login
        if (path.equals("/api/auth/login") && "POST".equalsIgnoreCase(method)) {
            String body = readRequestBody(exchange);
            String u = extractJsonString(body, "username");
            String p = extractJsonString(body, "password");

            // Check master token direct login
            String masterToken = UniversePurpurConfig.webPanelAuthToken;
            if (p != null && masterToken != null && !masterToken.trim().isEmpty() && p.equals(masterToken.trim())) {
                sendJsonResponse(exchange, 200, String.format(
                        "{\"success\":true,\"token\":\"%s\",\"username\":\"MasterAdmin\",\"role\":\"SUPER_ADMIN\"}",
                        masterToken.trim()));
                return;
            }

            UniverseUserManager userMgr = UniverseUserManager.instance();
            if (userMgr.authenticate(u, p)) {
                UniverseUserManager.Session session = userMgr.createSession(u);
                sendJsonResponse(exchange, 200, String.format(
                        "{\"success\":true,\"token\":\"%s\",\"username\":\"%s\",\"role\":\"%s\"}",
                        session.token, session.username, session.role.name()));
            } else {
                sendJsonResponse(exchange, 401, "{\"success\":false,\"message\":\"Usuario o contraseña incorrectos\"}");
            }
            return;
        }

        // --- Authenticated Admin Endpoints ---
        UniverseUserManager.Session session = getSession(exchange);
        if (session == null) {
            sendJsonResponse(exchange, 401, "{\"error\":\"Unauthorized\",\"message\":\"Sesión inválida o expirada\"}");
            return;
        }

        // Auth Me
        if (path.equals("/api/auth/me")) {
            sendJsonResponse(exchange, 200, String.format("{\"username\":\"%s\",\"role\":\"%s\"}", session.username, session.role.name()));
            return;
        }

        // Players List
        if (path.equals("/api/players") && "GET".equalsIgnoreCase(method)) {
            handlePlayersApi(exchange);
            return;
        }

        // Player Actions (Kick, Ban, Op)
        if (path.equals("/api/player/action") && "POST".equalsIgnoreCase(method)) {
            handlePlayerActionApi(exchange);
            return;
        }

        // Remote Command Execution (Requires SUPER_ADMIN)
        if (path.equals("/api/command") && "POST".equalsIgnoreCase(method)) {
            if (session.role != UniverseUserManager.Role.SUPER_ADMIN) {
                sendJsonResponse(exchange, 403, "{\"error\":\"Permiso denegado: solo SUPER_ADMIN puede ejecutar comandos\"}");
                return;
            }
            handleCommandApi(exchange);
            return;
        }

        // System Actions (Profile, Trim, Purge, LagProtection)
        if (path.equals("/api/actions") && "POST".equalsIgnoreCase(method)) {
            handleSystemActionsApi(exchange);
            return;
        }

        // Branding Customization (Requires SUPER_ADMIN)
        if (path.equals("/api/settings/branding") && "POST".equalsIgnoreCase(method)) {
            if (session.role != UniverseUserManager.Role.SUPER_ADMIN) {
                sendJsonResponse(exchange, 403, "{\"error\":\"Permiso denegado\"}");
                return;
            }
            String body = readRequestBody(exchange);
            String title = extractJsonString(body, "title");
            String desc = extractJsonString(body, "description");
            String logo = extractJsonString(body, "logoUrl");
            String fav = extractJsonString(body, "faviconUrl");
            String ip = extractJsonString(body, "serverIp");
            String disc = extractJsonString(body, "discordUrl");
            UniversePurpurConfig.updateBranding(title, desc, logo, fav, ip, disc);
            sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Branding actualizado y guardado correctamente\"}");
            return;
        }

        // Users Management (List, Create, Delete, Password)
        if (path.equals("/api/users") && "GET".equalsIgnoreCase(method)) {
            if (session.role != UniverseUserManager.Role.SUPER_ADMIN) {
                sendJsonResponse(exchange, 403, "{\"error\":\"Permiso denegado\"}");
                return;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("{\"users\":[");
            boolean first = true;
            for (UniverseUserManager.User u : UniverseUserManager.instance().getUsers().values()) {
                if (!first) sb.append(",");
                first = false;
                sb.append(String.format("{\"username\":\"%s\",\"role\":\"%s\",\"createdAt\":%d}", escapeJson(u.username), u.role.name(), u.createdAt));
            }
            sb.append("]}");
            sendJsonResponse(exchange, 200, sb.toString());
            return;
        }

        if (path.equals("/api/users/create") && "POST".equalsIgnoreCase(method)) {
            if (session.role != UniverseUserManager.Role.SUPER_ADMIN) {
                sendJsonResponse(exchange, 403, "{\"error\":\"Permiso denegado\"}");
                return;
            }
            String body = readRequestBody(exchange);
            String u = extractJsonString(body, "username");
            String p = extractJsonString(body, "password");
            String r = extractJsonString(body, "role");
            UniverseUserManager.Role role = (r != null && r.equalsIgnoreCase("MODERATOR"))
                    ? UniverseUserManager.Role.MODERATOR : UniverseUserManager.Role.SUPER_ADMIN;
            boolean ok = UniverseUserManager.instance().createUser(u, p, role);
            if (ok) {
                sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Usuario creado exitosamente\"}");
            } else {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Nombre de usuario ya en uso o inválido\"}");
            }
            return;
        }

        if (path.equals("/api/users/delete") && "POST".equalsIgnoreCase(method)) {
            if (session.role != UniverseUserManager.Role.SUPER_ADMIN) {
                sendJsonResponse(exchange, 403, "{\"error\":\"Permiso denegado\"}");
                return;
            }
            String body = readRequestBody(exchange);
            String u = extractJsonString(body, "username");
            boolean ok = UniverseUserManager.instance().deleteUser(u);
            sendJsonResponse(exchange, 200, ok ? "{\"success\":true}" : "{\"success\":false,\"message\":\"No se puede eliminar este usuario\"}");
            return;
        }

        if (path.equals("/api/users/password") && "POST".equalsIgnoreCase(method)) {
            String body = readRequestBody(exchange);
            String u = extractJsonString(body, "username");
            String np = extractJsonString(body, "newPassword");
            if (u == null || np == null || np.length() < 4) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Contraseña inválida (mínimo 4 caracteres)\"}");
                return;
            }
            if (session.role != UniverseUserManager.Role.SUPER_ADMIN && !session.username.equalsIgnoreCase(u)) {
                sendJsonResponse(exchange, 403, "{\"error\":\"Permiso denegado\"}");
                return;
            }
            boolean ok = UniverseUserManager.instance().updatePassword(u, np);
            sendJsonResponse(exchange, 200, ok ? "{\"success\":true,\"message\":\"Contraseña actualizada correctamente\"}" : "{\"success\":false,\"message\":\"Usuario no encontrado\"}");
            return;
        }


        sendJsonResponse(exchange, 404, "{\"error\":\"Endpoint no encontrado\"}");
    }

    private void handleStatusApi(HttpExchange exchange) throws IOException {
        UniverseGovernorTask gov = UniverseGovernorTask.instance();
        UniverseMemoryGovernor mem = UniverseMemoryGovernor.instance();
        AntiCrashShield shield = AntiCrashShield.instance();

        Runtime rt = Runtime.getRuntime();
        long freeMem = rt.freeMemory();
        long totalMem = rt.totalMemory();
        long maxMem = rt.maxMemory();
        long usedMemMb = (totalMem - freeMem) / (1024L * 1024L);
        long maxMemMb = maxMem / (1024L * 1024L);

        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();
        int chunks = getCachedLoadedChunks();

        StringBuilder pList = new StringBuilder("[");
        int pCount = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (pCount > 0) pList.append(",");
            if (pCount >= 32) break;
            pList.append(String.format(Locale.ROOT, "{\"name\":\"%s\",\"ping\":%d}", escapeJson(p.getName()), p.getPing()));
            pCount++;
        }
        pList.append("]");

        String json = String.format(Locale.ROOT, """
                {
                  "server": "UniversePurpur",
                  "author": "DevPapo",
                  "title": "%s",
                  "description": "%s",
                  "logoUrl": "%s",
                  "faviconUrl": "%s",
                  "serverIp": "%s",
                  "discordInvite": "%s",
                  "tps": %.2f,
                  "mspt": %.2f,
                  "throttleLevel": %d,
                  "throttleName": "%s",
                  "profile": "%s",
                  "onlinePlayers": %d,
                  "maxPlayers": %d,
                  "loadedChunks": %d,
                  "usedMemoryMb": %d,
                  "maxMemoryMb": %d,
                  "memoryUsagePercent": %.1f,
                  "gcPauseMs": %d,
                  "autoTrimCount": %d,
                  "spatialBuckets": %d,
                  "blockedExploits": %d,
                  "blockedMovement": %d,
                  "blockedPayloads": %d,
                  "blockedSlots": %d,
                  "linkedAccountsCount": %d,
                  "players": %s,
                  "multiVersion": {
                    "enabled": %b,
                    "active": %b,
                    "range": "%s"
                  },
                  "ports": {
                    "minecraftJava": %d,
                    "webAdmin": %d,
                    "geyserBedrock": %d
                  }
                }
                """,
                UniversePurpurConfig.webServerTitle,
                UniversePurpurConfig.webServerDescription,
                UniversePurpurConfig.webServerLogoUrl,
                UniversePurpurConfig.webServerFaviconUrl,
                UniversePurpurConfig.webServerIpAddress,
                UniversePurpurConfig.webDiscordInviteUrl,
                gov.getCurrentTps(),
                gov.getCurrentMspt(),
                gov.getThrottleLevel(),
                gov.getThrottleLevelName(),
                UniversePurpurConfig.profile,
                onlinePlayers,
                maxPlayers,
                chunks,
                usedMemMb,
                maxMemMb,
                mem.getHeapUsagePercent(),
                mem.getTotalGcPauseTimeMs(),
                mem.getAutoTrimCount(),
                org.purpurmc.purpur.util.SpatialGrid.instance().getBucketCount(),
                shield.getTotalBlockedExploits(),
                shield.getBlockedInvalidMovement(),
                shield.getBlockedOversizedPayloads(),
                shield.getBlockedInvalidSlots(),
                UniverseDiscordLinker.instance().getAllLinks().size(),
                pList.toString(),
                UniversePurpurConfig.multiVersionEnabled,
                org.purpurmc.purpur.multiversion.UniverseMultiVersionManager.isMultiVersionActive(),
                org.purpurmc.purpur.multiversion.UniverseMultiVersionManager.getSupportedRange(),
                Bukkit.getPort() > 0 ? Bukkit.getPort() : 25565,
                UniversePurpurConfig.webPanelPort,
                UniversePurpurConfig.geyserPort
        );

        sendJsonResponse(exchange, 200, json);
    }

    private void handlePlayersApi(HttpExchange exchange) throws IOException {
        CompletableFuture<String> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(PLUGIN, () -> {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append("{\"players\":[");
                boolean first = true;
                UniverseDiscordLinker linker = UniverseDiscordLinker.instance();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!first) sb.append(",");
                    first = false;
                    Location loc = p.getLocation();
                    String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "world";
                    int ping = p.getPing();
                    UniverseDiscordLinker.LinkedAccount linked = linker.getByPlayer(p.getUniqueId());
                    String dTag = linked != null ? linked.discordTag : "";
                    sb.append(String.format(Locale.ROOT,
                            "{\"name\":\"%s\",\"uuid\":\"%s\",\"world\":\"%s\",\"x\":%.1f,\"y\":%.1f,\"z\":%.1f,\"ping\":%d,\"health\":%.1f,\"op\":%b,\"discord\":\"%s\"}",
                            p.getName(), p.getUniqueId(), worldName, loc.getX(), loc.getY(), loc.getZ(), ping, p.getHealth(), p.isOp(), dTag));
                }
                sb.append("]}");
                future.complete(sb.toString());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });

        String res;
        try {
            res = future.get(2, TimeUnit.SECONDS);
        } catch (Throwable t) {
            res = "{\"players\":[],\"error\":\"Failed to snapshot players safely\"}";
        }
        sendJsonResponse(exchange, 200, res);
    }

    private void handlePlayerActionApi(HttpExchange exchange) throws IOException {
        String body = readRequestBody(exchange);
        String target = extractJsonString(body, "target");
        String action = extractJsonString(body, "action");
        String reason = extractJsonString(body, "reason");
        if (reason == null || reason.isEmpty()) reason = "Expulsado por Administrador Web (UniversePurpur)";

        if (target == null || target.isEmpty() || action == null) {
            sendJsonResponse(exchange, 400, "{\"error\":\"Missing target or action\"}");
            return;
        }

        final String fTarget = target;
        final String fAction = action;
        final String fReason = reason;

        CompletableFuture<String> actionFuture = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(PLUGIN, () -> {
            try {
                Player player = Bukkit.getPlayerExact(fTarget);
                if (player == null) {
                    try {
                        UUID uuid = UUID.fromString(fTarget);
                        player = Bukkit.getPlayer(uuid);
                    } catch (Throwable ignore) {
                    }
                }

                if (fAction.equalsIgnoreCase("kick")) {
                    if (player != null && player.isOnline()) {
                        player.kick(Component.text(fReason));
                        actionFuture.complete("{\"success\":true,\"message\":\"Jugador " + fTarget + " expulsado exitosamente\"}");
                    } else {
                        actionFuture.complete("{\"success\":false,\"message\":\"Jugador " + fTarget + " no encontrado o desconectado\"}");
                    }
                } else if (fAction.equalsIgnoreCase("ban")) {
                    try {
                        Bukkit.getBanList(BanList.Type.NAME).addBan(fTarget, fReason, null, "UniverseWebAdmin");
                    } catch (Throwable ignore) {
                    }
                    if (player != null && player.isOnline()) {
                        player.kick(Component.text("Baneado: " + fReason));
                    }
                    actionFuture.complete("{\"success\":true,\"message\":\"Jugador " + fTarget + " baneado exitosamente\"}");
                } else if (fAction.equalsIgnoreCase("op")) {
                    if (player != null) {
                        boolean newState = !player.isOp();
                        player.setOp(newState);
                        actionFuture.complete("{\"success\":true,\"message\":\"Rango OP de " + fTarget + " cambiado a " + newState + "\"}");
                    } else {
                        actionFuture.complete("{\"success\":false,\"message\":\"Jugador " + fTarget + " no encontrado\"}");
                    }
                } else {
                    actionFuture.complete("{\"success\":false,\"message\":\"Acción desconocida: " + fAction + "\"}");
                }
            } catch (Throwable t) {
                actionFuture.complete("{\"success\":false,\"error\":\"" + t.getMessage() + "\"}");
            }
        });

        String resJson;
        try {
            resJson = actionFuture.get(3, TimeUnit.SECONDS);
        } catch (Throwable t) {
            resJson = "{\"success\":false,\"message\":\"Tiempo de espera agotado al ejecutar acción\"}";
        }
        sendJsonResponse(exchange, 200, resJson);
    }

    private void handleCommandApi(HttpExchange exchange) throws IOException {
        String body = readRequestBody(exchange);
        String command = extractJsonString(body, "command");
        if (command == null || command.trim().isEmpty()) {
            sendJsonResponse(exchange, 400, "{\"error\":\"Empty command\"}");
            return;
        }

        final String cmd = command.startsWith("/") ? command.substring(1) : command;
        CompletableFuture<String> cmdFuture = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(PLUGIN, () -> {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                cmdFuture.complete("{\"success\":true,\"dispatched\":\"" + cmd.replace("\"", "\\\"") + "\"}");
            } catch (Throwable t) {
                Bukkit.getLogger().log(Level.WARNING, "Error executing web console command: " + cmd, t);
                cmdFuture.complete("{\"success\":false,\"error\":\"" + (t.getMessage() != null ? t.getMessage().replace("\"", "\\\"") : "Error al ejecutar comando") + "\"}");
            }
        });

        String resJson;
        try {
            resJson = cmdFuture.get(3, TimeUnit.SECONDS);
        } catch (Throwable t) {
            resJson = "{\"success\":false,\"message\":\"Tiempo de espera agotado al despachar comando\"}";
        }
        sendJsonResponse(exchange, 200, resJson);
    }

    private void handleSystemActionsApi(HttpExchange exchange) throws IOException {
        String body = readRequestBody(exchange);
        String action = extractJsonString(body, "action");
        String value = extractJsonString(body, "value");

        if ("profile".equalsIgnoreCase(action) && value != null) {
            UniversePurpurConfig.setProfile(value.toLowerCase());
            sendJsonResponse(exchange, 200, "{\"success\":true,\"profile\":\"" + value + "\"}");
            return;
        }

        if ("trim".equalsIgnoreCase(action)) {
            int freed = UniverseMemoryGovernor.instance().trimCaches();
            sendJsonResponse(exchange, 200, "{\"success\":true,\"freed\":\"" + freed + "\"}");
            return;
        }

        if ("purge".equalsIgnoreCase(action) && value != null) {
            final String target = value.toLowerCase();
            Bukkit.getScheduler().runTask(PLUGIN, () -> {
                for (World world : Bukkit.getWorlds()) {
                    if (target.equals("items")) {
                        for (Item i : world.getEntitiesByClass(Item.class)) i.remove();
                    } else if (target.equals("ambient")) {
                        for (Ambient a : world.getEntitiesByClass(Ambient.class)) a.remove();
                    } else if (target.equals("monsters")) {
                        for (Monster m : world.getEntitiesByClass(Monster.class)) m.remove();
                    }
                }
            });
            sendJsonResponse(exchange, 200, "{\"success\":true,\"purged\":\"" + target + "\"}");
            return;
        }

        if ("lagprotection".equalsIgnoreCase(action) && value != null) {
            if (value.equalsIgnoreCase("on")) {
                UniverseGovernorTask.instance().setManualEmergencyOverride(true);
            } else if (value.equalsIgnoreCase("off")) {
                UniverseGovernorTask.instance().setManualEmergencyOverride(false);
            } else {
                UniverseGovernorTask.instance().setManualEmergencyOverride(null);
            }
            sendJsonResponse(exchange, 200, "{\"success\":true,\"mode\":\"" + value + "\"}");
            return;
        }

        sendJsonResponse(exchange, 400, "{\"error\":\"Unknown action\"}");
    }

    private static String extractJsonString(String json, String key) {
        if (json == null || key == null) return null;
        String searchKey = "\"" + key + "\"";
        int keyIdx = json.indexOf(searchKey);
        if (keyIdx == -1) return null;
        int colonIdx = json.indexOf(':', keyIdx + searchKey.length());
        if (colonIdx == -1) return null;
        int startQuote = json.indexOf('"', colonIdx + 1);
        if (startQuote == -1) return null;
        int start = startQuote + 1;
        int end = start;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == '\\') {
                end += 2;
                continue;
            }
            if (c == '"') {
                break;
            }
            end++;
        }
        if (end > json.length()) return null;
        String val = json.substring(start, end);
        return val.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n").replace("\\r", "\r");
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }


    private static final String PUBLIC_PORTAL_HTML = """
<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>UniversePurpur | Servidor Oficial</title>
  <link rel="icon" type="image/x-icon" href="https://scon.host/favicon.ico">
  <style>
    :root {
      --bg: #060813;
      --card-bg: rgba(14, 17, 34, 0.75);
      --card-border: rgba(157, 78, 221, 0.28);
      --accent-purple: #9d4edd;
      --accent-cyan: #00f0ff;
      --accent-green: #00e676;
      --accent-red: #ff1744;
      --accent-pink: #f72585;
      --text-main: #f0f2f5;
      --text-muted: #9aa0bc;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Segoe UI', system-ui, -apple-system, sans-serif; }
    body {
      background: radial-gradient(circle at 50% 10%, #15102a, var(--bg) 75%);
      color: var(--text-main);
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      overflow-x: hidden;
    }
    header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 20px 45px;
      backdrop-filter: blur(16px);
      background: rgba(6, 8, 19, 0.6);
      border-bottom: 1px solid rgba(255,255,255,0.06);
      position: sticky;
      top: 0;
      z-index: 100;
    }
    .brand { display: flex; align-items: center; gap: 14px; text-decoration: none; }
    .brand img { height: 42px; border-radius: 10px; box-shadow: 0 0 20px rgba(157,78,221,0.5); }
    .brand-text h1 { font-size: 20px; font-weight: 900; letter-spacing: 0.5px; background: linear-gradient(90deg, #fff, var(--accent-cyan)); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
    .brand-text small { font-size: 10px; font-weight: 700; color: var(--accent-purple); letter-spacing: 1.5px; text-transform: uppercase; }
    .nav-links { display: flex; align-items: center; gap: 20px; }
    .nav-links a { color: var(--text-muted); text-decoration: none; font-weight: 600; font-size: 14px; transition: 0.25s; }
    .nav-links a:hover, .nav-links a.active { color: var(--accent-cyan); }
    .btn-admin { background: linear-gradient(135deg, rgba(157,78,221,0.3), rgba(0,240,255,0.2)); border: 1px solid var(--accent-purple); color: #fff !important; padding: 8px 18px; border-radius: 10px; font-weight: 700 !important; transition: 0.3s; }
    .btn-admin:hover { filter: brightness(1.2); box-shadow: 0 0 15px rgba(157,78,221,0.6); transform: translateY(-1px); }

    .hero { text-align: center; padding: 60px 20px 30px; max-width: 960px; margin: 0 auto; }
    .hero-badge {
      display: inline-flex;
      align-items: center;
      gap: 10px;
      background: rgba(0, 240, 255, 0.08);
      border: 1px solid rgba(0, 240, 255, 0.35);
      color: var(--accent-cyan);
      padding: 7px 18px;
      border-radius: 30px;
      font-size: 12px;
      font-weight: 800;
      letter-spacing: 1px;
      margin-bottom: 22px;
      box-shadow: 0 0 20px rgba(0,240,255,0.15);
    }
    .pulse-dot { width: 9px; height: 9px; background: var(--accent-green); border-radius: 50%; box-shadow: 0 0 10px var(--accent-green); animation: pulseDot 1.6s infinite ease-in-out; }
    @keyframes pulseDot { 0%, 100% { opacity: 1; transform: scale(1); } 50% { opacity: 0.35; transform: scale(0.8); } }

    .hero h2 { font-size: 48px; font-weight: 900; line-height: 1.15; margin-bottom: 16px; background: linear-gradient(135deg, #ffffff 30%, var(--accent-cyan)); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
    .hero p { font-size: 17px; color: var(--text-muted); line-height: 1.6; margin-bottom: 35px; max-width: 760px; margin-left: auto; margin-right: auto; }

    /* Interactive IP Box */
    .ip-card {
      display: inline-flex;
      flex-direction: column;
      align-items: center;
      background: rgba(14, 17, 34, 0.85);
      border: 1px solid var(--card-border);
      padding: 16px 32px;
      border-radius: 18px;
      gap: 10px;
      cursor: pointer;
      transition: 0.3s;
      box-shadow: 0 10px 40px rgba(0,0,0,0.6);
      position: relative;
    }
    .ip-card:hover {
      border-color: var(--accent-cyan);
      transform: translateY(-3px);
      box-shadow: 0 15px 45px rgba(0,240,255,0.25);
    }
    .ip-row { display: flex; align-items: center; gap: 16px; }
    .ip-text { font-family: 'Consolas', 'Courier New', monospace; font-size: 22px; font-weight: 900; color: var(--accent-cyan); letter-spacing: 0.5px; }
    .ip-copy-btn { background: var(--accent-purple); color: #fff; padding: 6px 14px; border-radius: 8px; font-size: 12px; font-weight: 700; border: none; cursor: pointer; transition: 0.2s; }
    .ip-copy-btn:hover { filter: brightness(1.2); }
    .ip-ports { font-size: 12px; color: var(--text-muted); display: flex; gap: 16px; font-weight: 600; }
    .ip-ports span { color: #fff; }

    /* Stats Grid */
    .stats-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 20px; max-width: 960px; margin: 45px auto 50px; padding: 0 20px; }
    .stat-card {
      background: var(--card-bg);
      border: 1px solid var(--card-border);
      padding: 22px 20px;
      border-radius: 16px;
      text-align: center;
      backdrop-filter: blur(14px);
      transition: 0.3s;
      box-shadow: 0 4px 20px rgba(0,0,0,0.3);
    }
    .stat-card:hover { transform: translateY(-3px); border-color: rgba(0,240,255,0.4); box-shadow: 0 8px 30px rgba(0,240,255,0.15); }
    .stat-card .num { font-size: 30px; font-weight: 900; color: #fff; margin-bottom: 4px; }
    .stat-card .lbl { font-size: 11px; color: var(--text-muted); text-transform: uppercase; font-weight: 700; letter-spacing: 1px; }
    .stat-bar { height: 4px; background: rgba(255,255,255,0.08); border-radius: 4px; margin-top: 12px; overflow: hidden; }
    .stat-bar-fill { height: 100%; background: linear-gradient(90deg, var(--accent-purple), var(--accent-cyan)); border-radius: 4px; width: 0%; transition: width 0.5s ease; }

    /* Online Players Showcase */
    .section-wrap { max-width: 960px; margin: 0 auto 60px; padding: 0 20px; }
    .section-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
    .section-head h3 { font-size: 20px; font-weight: 800; color: #fff; display: flex; align-items: center; gap: 10px; }
    .section-head small { color: var(--text-muted); font-size: 13px; }
    .players-container {
      background: var(--card-bg);
      border: 1px solid var(--card-border);
      border-radius: 18px;
      padding: 25px;
      backdrop-filter: blur(14px);
      box-shadow: 0 8px 30px rgba(0,0,0,0.4);
    }
    .players-grid { display: flex; flex-wrap: wrap; gap: 14px; min-height: 80px; align-items: center; }
    .player-chip {
      display: flex;
      align-items: center;
      gap: 10px;
      background: rgba(20, 24, 46, 0.85);
      border: 1px solid rgba(255,255,255,0.08);
      padding: 8px 14px;
      border-radius: 12px;
      transition: 0.25s;
    }
    .player-chip:hover { border-color: var(--accent-cyan); transform: translateY(-2px); box-shadow: 0 4px 15px rgba(0,240,255,0.2); }
    .player-chip img { width: 32px; height: 32px; border-radius: 6px; }
    .player-info { text-align: left; }
    .player-info .p-name { font-size: 13px; font-weight: 800; color: #fff; display: block; }
    .player-info .p-ping { font-size: 11px; color: var(--accent-green); font-weight: 700; }
    .sleep-banner {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 20px;
      background: rgba(157, 78, 221, 0.08);
      border: 1px dashed rgba(157, 78, 221, 0.35);
      border-radius: 14px;
      width: 100%;
    }
    .sleep-icon { font-size: 32px; }
    .sleep-text h4 { font-size: 15px; font-weight: 800; color: #fff; margin-bottom: 4px; }
    .sleep-text p { font-size: 13px; color: var(--text-muted); }

    /* Features Grid */
    .features-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 20px; margin-bottom: 60px; }
    .feature-card {
      background: var(--card-bg);
      border: 1px solid var(--card-border);
      border-radius: 16px;
      padding: 25px 20px;
      text-align: left;
      backdrop-filter: blur(12px);
      transition: 0.3s;
    }
    .feature-card:hover { border-color: var(--accent-purple); transform: translateY(-3px); }
    .feature-icon { font-size: 28px; margin-bottom: 12px; }
    .feature-card h4 { font-size: 16px; font-weight: 800; color: #fff; margin-bottom: 8px; }
    .feature-card p { font-size: 13px; color: var(--text-muted); line-height: 1.5; }

    /* Discord Linking Section */
    .link-section {
      background: var(--card-bg);
      border: 1px solid var(--card-border);
      border-radius: 20px;
      padding: 35px;
      backdrop-filter: blur(16px);
      box-shadow: 0 20px 50px rgba(0,0,0,0.6);
      margin-bottom: 70px;
    }
    .link-steps { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 15px; margin: 25px 0; }
    .step-box { background: rgba(9, 11, 23, 0.8); border: 1px solid rgba(255,255,255,0.06); padding: 16px; border-radius: 12px; }
    .step-box span { font-size: 11px; font-weight: 900; color: var(--accent-cyan); text-transform: uppercase; }
    .step-box p { font-size: 13px; color: #fff; font-weight: 700; margin-top: 4px; }
    .form-group { margin-bottom: 16px; }
    .form-group label { display: block; font-size: 11px; text-transform: uppercase; font-weight: 800; color: var(--text-muted); margin-bottom: 6px; letter-spacing: 0.5px; }
    .form-group input { width: 100%; background: #0c0e1a; border: 1px solid rgba(255,255,255,0.12); padding: 13px 18px; border-radius: 10px; color: #fff; font-size: 15px; outline: none; transition: 0.25s; }
    .form-group input:focus { border-color: var(--accent-cyan); box-shadow: 0 0 15px rgba(0,240,255,0.3); }
    .btn-submit { width: 100%; background: linear-gradient(135deg, var(--accent-purple), #7b2cbf); border: none; padding: 15px; border-radius: 12px; color: #fff; font-size: 15px; font-weight: 800; cursor: pointer; transition: 0.25s; box-shadow: 0 6px 25px rgba(157,78,221,0.4); }
    .btn-submit:hover { filter: brightness(1.2); transform: translateY(-2px); }

    footer {
      text-align: center;
      padding: 35px 20px;
      font-size: 13px;
      color: var(--text-muted);
      border-top: 1px solid rgba(255,255,255,0.06);
      margin-top: auto;
      background: rgba(6, 8, 19, 0.8);
    }
    footer a { color: var(--accent-cyan); text-decoration: none; font-weight: 700; }
  </style>
</head>
<body>
  <header>
    <a href="/" class="brand">
      <img src="/assets/universepurpur_banner.png" alt="Logo" id="headerLogo" onerror="this.style.display='none'">
      <div class="brand-text">
        <h1 id="headerTitle">UniversePurpur</h1>
        <small>+15,000 PLAYERS TITAN ENGINE</small>
      </div>
    </a>
    <div class="nav-links">
      <a href="/" class="active">Inicio</a>
      <a href="#playersSection">Jugadores</a>
      <a href="#linkSection">Vincular Discord</a>
      <a href="https://discord.gg/universepurpur" target="_blank" id="discordLinkBtn">Discord</a>
      <a href="/admin" class="btn-admin">Acceso Admin</a>
    </div>
  </header>

  <main>
    <section class="hero">
      <div class="hero-badge">
        <div class="pulse-dot"></div>
        <span id="heroBadgeText">EN LÍNEA • MOTOR TITAN +15K</span>
      </div>
      <h2 id="heroTitle">Bienvenido a UniversePurpur</h2>
      <p id="heroDesc">La experiencia de Minecraft más fluida y optimizada del mundo. Juega sin lag con tasa de 20.0 TPS continua y compatibilidad crossplay.</p>

      <div class="ip-card" onclick="copyIp()">
        <div class="ip-row">
          <span class="ip-text" id="ipText">panel.scon.host</span>
          <button class="ip-copy-btn" id="copyNotice">📋 Copiar IP</button>
        </div>
        <div class="ip-ports">
          <div>Java: <span id="pJava">25565</span></div>
          <div>Bedrock: <span id="pBedrock">19132</span></div>
          <div>Web: <span id="pWeb">25566</span></div>
        </div>
      </div>
    </section>

    <!-- Stats Grid -->
    <div class="stats-row">
      <div class="stat-card">
        <div class="num" id="statTps" style="color:var(--accent-green);">20.00</div>
        <div class="lbl">TPS del Servidor</div>
        <div class="stat-bar"><div class="stat-bar-fill" id="tpsBar" style="width:100%;"></div></div>
      </div>
      <div class="stat-card">
        <div class="num" id="statMspt" style="color:var(--accent-cyan);">0.0 ms</div>
        <div class="lbl">Tiempo de Tick (MSPT)</div>
        <div class="stat-bar"><div class="stat-bar-fill" id="msptBar" style="width:10%;"></div></div>
      </div>
      <div class="stat-card">
        <div class="num" id="statPlayers">0</div>
        <div class="lbl">Jugadores en Línea</div>
        <div class="stat-bar"><div class="stat-bar-fill" id="playersBar" style="width:0%;"></div></div>
      </div>
      <div class="stat-card">
        <div class="num" id="statLinked" style="color:var(--accent-purple);">0</div>
        <div class="lbl">Cuentas Vinculadas</div>
        <div class="stat-bar"><div class="stat-bar-fill" style="width:100%; background:var(--accent-purple);"></div></div>
      </div>
    </div>

    <!-- Players Showcase -->
    <div class="section-wrap" id="playersSection">
      <div class="section-head">
        <h3>👥 Jugadores en el Servidor</h3>
        <small id="playersCountSubtitle">0 conectados</small>
      </div>
      <div class="players-container">
        <div class="players-grid" id="playersGrid">
          <div class="sleep-banner">
            <div class="sleep-icon">🌙</div>
            <div class="sleep-text">
              <h4>Servidor en Reposo Inteligente (Hibernación Profunda)</h4>
              <p>El motor descargó automáticamente los chunks y liberó memoria RAM al sistema operativo. ¡Únete para iniciar al instante!</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Features Section -->
    <div class="section-wrap">
      <div class="section-head">
        <h3>⚡ Arquitectura Empresarial Titan</h3>
        <small>Optimización de clase mundial</small>
      </div>
      <div class="features-grid">
        <div class="feature-card">
          <div class="feature-icon">🚀</div>
          <h4>Particionado Espacial O(1)</h4>
          <p>Elimina el cuello de botella $O(N^2)$ en colisiones y búsquedas con proximidad continua para soportar +15k jugadores simultáneos.</p>
        </div>
        <div class="feature-card">
          <div class="feature-icon">🌐</div>
          <h4>Crossplay Universal MultiVersión</h4>
          <p>Conexión fluida desde Minecraft Java 1.21.x hasta 1.26.x, además de soporte para móviles y consolas mediante Geyser Bedrock.</p>
        </div>
        <div class="feature-card">
          <div class="feature-icon">🛡️</div>
          <h4>Blindaje Anti-Crasher & Exploit Shield</h4>
          <p>Protección contra paquetes malformados, libros corruptos, payloads gigantes y ataques Zero-Day integrada en el pipeline Netty.</p>
        </div>
        <div class="feature-card">
          <div class="feature-icon">💾</div>
          <h4>Zero-Player Deep Hibernation</h4>
          <p>Libera páginas de RAM directamente al sistema operativo cuando no hay jugadores, evitando Stop-the-World pauses innecesarias.</p>
        </div>
      </div>
    </div>

    <!-- Discord Link Section -->
    <div class="section-wrap" id="linkSection">
      <div class="link-section">
        <h3 style="font-size:24px; font-weight:800; color:#fff; margin-bottom:8px;">🔗 Vincula tu Cuenta con Discord</h3>
        <p style="font-size:14px; color:var(--text-muted); margin-bottom:20px;">Sincroniza tus rangos, obtén recompensas exclusivas y activa la seguridad en dos pasos para tu cuenta de juego.</p>

        <div class="link-steps">
          <div class="step-box">
            <span>Paso 1</span>
            <p>Escribe <b>/link</b> dentro de Minecraft</p>
          </div>
          <div class="step-box">
            <span>Paso 2</span>
            <p>Copia el código temporal generado</p>
          </div>
          <div class="step-box">
            <span>Paso 3</span>
            <p>Pégalo aquí y confirma tu Discord</p>
          </div>
        </div>

        <div class="form-group">
          <label>1. Código de Vinculación:</label>
          <input type="text" id="linkCodeInput" placeholder="Ejemplo: UP-839201" maxlength="9" oninput="this.value = this.value.toUpperCase()">
        </div>
        <div class="form-group">
          <label>2. Tu Usuario o ID de Discord:</label>
          <input type="text" id="discordIdInput" placeholder="Ejemplo: MiUsuario#1234 o ID numérico">
        </div>
        <button class="btn-submit" onclick="submitDiscordLink()">Vincular Mi Cuenta Ahora</button>
        <div id="linkResultMsg" style="margin-top:16px; font-size:14px; text-align:center; font-weight:bold;"></div>
      </div>
    </div>
  </main>

  <footer>
    <p>Impulsado por <b>UniversePurpur Enterprise Titan Engine</b> | Host: <b>scon.host</b> | Autor: <b>DevPapo</b> | <a href="/admin">Panel de Administración</a></p>
  </footer>

  <script>
    function escapeHtml(str) {
      if (!str && str !== 0) return '';
      return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    async function loadData() {
      try {
        const res = await fetch('/api/status');
        const data = await res.json();
        if (data) {
          if (data.title) {
            document.getElementById('headerTitle').innerText = data.title;
            document.getElementById('heroTitle').innerText = data.title;
          }
          if (data.description) document.getElementById('heroDesc').innerText = data.description;
          if (data.serverIp) document.getElementById('ipText').innerText = data.serverIp;

          // Ports
          if (data.ports) {
            document.getElementById('pJava').innerText = data.ports.minecraftJava || 25565;
            document.getElementById('pBedrock').innerText = data.ports.geyserBedrock || 19132;
            document.getElementById('pWeb').innerText = data.ports.webAdmin || 25566;
          }

          // Stats
          const tpsVal = data.tps ? data.tps.toFixed(2) : '20.00';
          const tpsNum = parseFloat(tpsVal);
          const tpsEl = document.getElementById('statTps');
          tpsEl.innerText = tpsVal;
          tpsEl.style.color = tpsNum >= 19.0 ? 'var(--accent-green)' : tpsNum >= 15.0 ? '#ffd600' : 'var(--accent-red)';
          document.getElementById('tpsBar').style.width = Math.min(100, (tpsNum / 20.0) * 100) + '%';

          const msptVal = data.mspt ? data.mspt.toFixed(1) : '0.0';
          document.getElementById('statMspt').innerText = msptVal + ' ms';
          document.getElementById('msptBar').style.width = Math.min(100, (parseFloat(msptVal) / 50.0) * 100) + '%';

          document.getElementById('statPlayers').innerText = (data.onlinePlayers || 0) + ' / ' + (data.maxPlayers || 15000);
          const playerPercent = Math.min(100, ((data.onlinePlayers || 0) / Math.max(1, data.maxPlayers || 100)) * 100);
          document.getElementById('playersBar').style.width = playerPercent + '%';

          document.getElementById('statLinked').innerText = data.linkedAccountsCount || 0;

          if (data.discordInvite) document.getElementById('discordLinkBtn').href = data.discordInvite;
          if (data.logoUrl) document.getElementById('headerLogo').src = data.logoUrl;

          // Render Players Grid
          renderPlayers(data.players || [], data.onlinePlayers || 0);
        }
      } catch (err) {}
    }

    function renderPlayers(players, onlineCount) {
      const grid = document.getElementById('playersGrid');
      const countSubtitle = document.getElementById('playersCountSubtitle');
      countSubtitle.innerText = onlineCount + ' jugador' + (onlineCount === 1 ? '' : 'es') + ' conectados';

      if (!players || players.length === 0) {
        grid.innerHTML = `
          <div class="sleep-banner">
            <div class="sleep-icon">🌙</div>
            <div class="sleep-text">
              <h4>Servidor en Reposo Inteligente (Hibernación Profunda)</h4>
              <p>El motor descargó automáticamente los chunks y liberó memoria RAM al sistema operativo. ¡Únete para iniciar al instante!</p>
            </div>
          </div>
        `;
        return;
      }

      grid.innerHTML = players.map(p => `
        <div class="player-chip" title="Ping: ${p.ping || 0}ms">
          <img src="https://mc-heads.net/avatar/${escapeHtml(p.name)}/32" alt="${escapeHtml(p.name)}" onerror="this.src='https://mc-heads.net/avatar/Steve/32'">
          <div class="player-info">
            <span class="p-name">${escapeHtml(p.name)}</span>
            <span class="p-ping">📶 ${p.ping || 0}ms</span>
          </div>
        </div>
      `).join('');
    }

    function copyIp() {
      const ip = document.getElementById('ipText').innerText;
      navigator.clipboard.writeText(ip);
      const notice = document.getElementById('copyNotice');
      notice.innerText = '✔ ¡IP Copiada!';
      notice.style.background = 'var(--accent-green)';
      setTimeout(() => {
        notice.innerText = '📋 Copiar IP';
        notice.style.background = 'var(--accent-purple)';
      }, 2500);
    }

    async function submitDiscordLink() {
      const code = document.getElementById('linkCodeInput').value.trim();
      const discord = document.getElementById('discordIdInput').value.trim();
      const msg = document.getElementById('linkResultMsg');
      if (!code || !discord) {
        msg.innerText = 'Por favor completa todos los campos.';
        msg.style.color = '#ff1744';
        return;
      }

      msg.innerText = 'Verificando código...';
      msg.style.color = '#00f0ff';

      try {
        const res = await fetch('/api/link/verify', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ code: code, discordId: discord, discordTag: discord })
        });
        const data = await res.json();
        if (data && data.success) {
          msg.innerText = '✔ ' + data.message;
          msg.style.color = '#00e676';
          document.getElementById('linkCodeInput').value = '';
          document.getElementById('discordIdInput').value = '';
          loadData();
        } else {
          msg.innerText = '❌ ' + (data ? data.message : 'Error al verificar');
          msg.style.color = '#ff1744';
        }
      } catch (err) {
        msg.innerText = '❌ Error de red con el servidor';
        msg.style.color = '#ff1744';
      }
    }

    loadData();
    setInterval(loadData, 2500);
  </script>
</body>
</html>
""";

    // --- Ultra-Modern Admin Control Panel SPA ---
    private static final String ADMIN_PANEL_HTML = """
<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>UniversePurpur | Admin Control Center</title>
  <link rel="icon" type="image/x-icon" href="https://scon.host/favicon.ico">
  <style>
    :root {
      --bg: #070914;
      --card: #101426;
      --border: #1e243d;
      --purple: #9d4edd;
      --cyan: #00f0ff;
      --green: #00e676;
      --red: #ff1744;
      --yellow: #ffd600;
      --text: #f0f2f5;
      --muted: #8b92b0;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Segoe UI', system-ui, -apple-system, sans-serif; }
    body { background: var(--bg); color: var(--text); min-height: 100vh; display: flex; flex-direction: column; overflow-x: hidden; }

    /* Header */
    header { background: rgba(11, 14, 28, 0.85); backdrop-filter: blur(14px); border-bottom: 1px solid var(--border); padding: 14px 28px; display: flex; justify-content: space-between; align-items: center; position: sticky; top: 0; z-index: 90; }
    .brand { display: flex; align-items: center; gap: 14px; text-decoration: none; color: inherit; }
    .logo { width: 38px; height: 38px; background: linear-gradient(135deg, var(--purple), var(--cyan)); border-radius: 10px; display: flex; align-items: center; justify-content: center; font-weight: 900; font-size: 17px; color: #fff; box-shadow: 0 0 16px rgba(157,78,221,0.5); }
    .title h1 { font-size: 17px; font-weight: 900; letter-spacing: 0.5px; background: linear-gradient(90deg, #fff, var(--cyan)); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
    .title p { font-size: 10px; color: var(--purple); font-weight: 800; letter-spacing: 1px; }

    .header-right { display: flex; align-items: center; gap: 18px; }
    .status-pill { display: flex; align-items: center; gap: 8px; background: rgba(0,230,118,0.1); border: 1px solid rgba(0,230,118,0.25); color: var(--green); padding: 5px 12px; border-radius: 20px; font-size: 12px; font-weight: 700; }
    .pulse-dot { width: 8px; height: 8px; background: var(--green); border-radius: 50%; box-shadow: 0 0 8px var(--green); }
    .user-pill { display: flex; align-items: center; gap: 12px; background: rgba(255,255,255,0.03); padding: 5px 12px; border-radius: 20px; border: 1px solid var(--border); }
    .user-pill span { font-size: 12px; color: var(--muted); }
    .user-pill b { color: #fff; }

    /* Layout */
    .layout { display: flex; flex: 1; min-height: calc(100vh - 67px); }
    nav { width: 230px; background: #0a0d1c; border-right: 1px solid var(--border); padding: 20px 12px; display: flex; flex-direction: column; gap: 6px; }
    .nav-btn { background: transparent; border: none; color: var(--muted); text-align: left; padding: 12px 16px; border-radius: 10px; cursor: pointer; font-size: 13px; font-weight: 700; transition: 0.2s; display: flex; align-items: center; gap: 12px; }
    .nav-btn:hover { background: rgba(255,255,255,0.04); color: #fff; }
    .nav-btn.active { background: linear-gradient(90deg, rgba(157,78,221,0.25), rgba(0,240,255,0.08)); color: var(--cyan); border-left: 3px solid var(--cyan); }
    .nav-icon { font-size: 16px; }

    .content { flex: 1; padding: 25px 30px; overflow-y: auto; background: radial-gradient(circle at 50% 0%, #11152d, var(--bg) 60%); }

    /* Cards & Grids */
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 18px; margin-bottom: 25px; }
    .card { background: var(--card); border: 1px solid var(--border); border-radius: 14px; padding: 20px; box-shadow: 0 6px 25px rgba(0,0,0,0.35); position: relative; }
    .card h3 { font-size: 11px; text-transform: uppercase; letter-spacing: 1px; color: var(--muted); margin-bottom: 8px; font-weight: 800; display: flex; justify-content: space-between; align-items: center; }
    .card .val { font-size: 28px; font-weight: 900; color: #fff; margin-bottom: 4px; }
    .card .sub { font-size: 12px; color: var(--muted); font-weight: 600; }

    .pill { display: inline-block; padding: 3px 8px; border-radius: 8px; font-size: 11px; font-weight: 800; }
    .pill-green { background: rgba(0,230,118,0.15); color: var(--green); border: 1px solid rgba(0,230,118,0.3); }
    .pill-yellow { background: rgba(255,214,0,0.15); color: var(--yellow); border: 1px solid rgba(255,214,0,0.3); }
    .pill-red { background: rgba(255,23,68,0.15); color: var(--red); border: 1px solid rgba(255,23,68,0.3); }

    /* Charts Container */
    .charts-grid { display: grid; grid-template-columns: 2fr 1fr; gap: 20px; margin-bottom: 25px; }
    @media (max-width: 1024px) { .charts-grid { grid-template-columns: 1fr; } }
    .chart-box { background: var(--card); border: 1px solid var(--border); border-radius: 14px; padding: 20px; }
    .chart-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
    .chart-header h4 { font-size: 13px; font-weight: 800; text-transform: uppercase; letter-spacing: 0.5px; color: #fff; }
    .chart-header span { font-size: 12px; color: var(--cyan); font-weight: 700; font-family: monospace; }
    canvas { width: 100% !important; height: 160px !important; display: block; }

    /* Quick Action Toolbar */
    .quick-toolbar { display: flex; gap: 10px; margin-bottom: 25px; flex-wrap: wrap; }
    .action-chip { background: rgba(20, 24, 46, 0.9); border: 1px solid var(--border); padding: 10px 16px; border-radius: 10px; color: #fff; font-size: 13px; font-weight: 700; cursor: pointer; transition: 0.2s; display: flex; align-items: center; gap: 8px; }
    .action-chip:hover { border-color: var(--cyan); transform: translateY(-2px); box-shadow: 0 4px 15px rgba(0,240,255,0.2); }

    /* Tables */
    .table-container { overflow-x: auto; margin-top: 15px; }
    table { width: 100%; border-collapse: collapse; text-align: left; }
    th { padding: 12px 14px; font-size: 11px; text-transform: uppercase; color: var(--muted); border-bottom: 1px solid var(--border); font-weight: 800; letter-spacing: 0.5px; }
    td { padding: 12px 14px; font-size: 13px; border-bottom: 1px solid #161a2e; }
    tr:hover td { background: rgba(255,255,255,0.02); }
    .player-row { display: flex; align-items: center; gap: 10px; }
    .player-avatar { width: 28px; height: 28px; border-radius: 6px; }

    /* Buttons */
    .btn { background: linear-gradient(135deg, var(--purple), #7b2cbf); border: none; color: #fff; padding: 9px 18px; border-radius: 8px; cursor: pointer; font-weight: 800; font-size: 13px; transition: 0.2s; display: inline-flex; align-items: center; gap: 8px; }
    .btn:hover { filter: brightness(1.2); transform: translateY(-1px); }
    .btn-red { background: var(--red); }
    .btn-act { padding: 5px 11px; font-size: 11px; border-radius: 6px; border: none; cursor: pointer; font-weight: 800; margin-right: 5px; transition: 0.2s; }
    .btn-act:hover { filter: brightness(1.2); }
    .btn-kick { background: #ff9100; color: #000; }
    .btn-ban { background: var(--red); color: #fff; }
    .btn-op { background: var(--purple); color: #fff; }

    /* Terminal Console */
    .console-box { background: #06070d; border: 1px solid var(--border); border-radius: 12px; height: 380px; overflow-y: auto; padding: 16px; font-family: 'Consolas', 'Courier New', monospace; font-size: 13px; color: #a6e22e; line-height: 1.5; margin-bottom: 12px; }
    .console-row { display: flex; gap: 10px; }
    .console-row input { flex: 1; background: #0d1020; border: 1px solid var(--border); padding: 12px 16px; border-radius: 8px; color: #fff; font-size: 14px; font-family: monospace; outline: none; transition: 0.2s; }
    .console-row input:focus { border-color: var(--cyan); box-shadow: 0 0 12px rgba(0,240,255,0.25); }
    .console-chips { display: flex; gap: 8px; margin-bottom: 10px; flex-wrap: wrap; }
    .cmd-chip { background: rgba(255,255,255,0.05); border: 1px solid var(--border); color: var(--cyan); padding: 4px 10px; border-radius: 6px; font-size: 11px; font-family: monospace; font-weight: bold; cursor: pointer; transition: 0.2s; }
    .cmd-chip:hover { background: rgba(0,240,255,0.15); border-color: var(--cyan); }

    /* Forms */
    .tab { display: none; }
    .tab.active { display: block; animation: fadeIn 0.2s ease; }
    @keyframes fadeIn { from { opacity: 0; transform: translateY(4px); } to { opacity: 1; transform: translateY(0); } }
    .form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }
    .form-group label { display: block; font-size: 11px; font-weight: 800; color: var(--muted); margin-bottom: 6px; text-transform: uppercase; letter-spacing: 0.5px; }
    .form-group input, .form-group select { width: 100%; background: #0e1224; border: 1px solid var(--border); padding: 11px 15px; border-radius: 8px; color: #fff; font-size: 14px; outline: none; transition: 0.2s; }
    .form-group input:focus, .form-group select:focus { border-color: var(--purple); box-shadow: 0 0 10px rgba(157,78,221,0.3); }

    /* Toasts */
    #toastContainer { position: fixed; top: 20px; right: 20px; z-index: 10000; display: flex; flex-direction: column; gap: 10px; }
    .toast { background: rgba(16, 20, 38, 0.95); backdrop-filter: blur(10px); border: 1px solid var(--border); padding: 12px 20px; border-radius: 10px; color: #fff; font-size: 13px; font-weight: 700; box-shadow: 0 10px 30px rgba(0,0,0,0.6); display: flex; align-items: center; gap: 10px; animation: slideIn 0.3s ease; }
    .toast-success { border-color: var(--green); }
    .toast-error { border-color: var(--red); }
    @keyframes slideIn { from { transform: translateX(100%); opacity: 0; } to { transform: translateX(0); opacity: 1; } }

    /* Auth Modal */
    #loginModal { position: fixed; inset: 0; background: rgba(5,6,14,0.94); backdrop-filter: blur(12px); display: flex; align-items: center; justify-content: center; z-index: 1000; }
    .login-box { background: #101426; border: 1px solid var(--border); border-radius: 18px; padding: 40px; width: 400px; box-shadow: 0 15px 50px rgba(0,0,0,0.8); }
    .login-box h2 { font-size: 24px; font-weight: 900; margin-bottom: 6px; color: #fff; text-align: center; }
    .login-box p { font-size: 12px; color: var(--muted); text-align: center; margin-bottom: 25px; }
  </style>
</head>
<body>
  <div id="toastContainer"></div>

  <!-- Login Modal -->
  <div id="loginModal">
    <div class="login-box">
      <div style="display:flex; justify-content:center; margin-bottom:15px;">
        <div class="logo" style="width:48px; height:48px; font-size:22px;">UP</div>
      </div>
      <h2>Iniciar Sesión</h2>
      <p>UniversePurpur Titan Enterprise Control Center</p>
      <div class="form-group" style="margin-bottom:16px;">
        <label>Usuario o Master Token:</label>
        <input type="text" id="loginUsername" placeholder="admin">
      </div>
      <div class="form-group" style="margin-bottom:24px;">
        <label>Contraseña:</label>
        <input type="password" id="loginPassword" placeholder="••••••••" onkeydown="if(event.key==='Enter') executeLogin()">
      </div>
      <button class="btn" style="width:100%; padding:14px;" onclick="executeLogin()">Acceder al Panel</button>
      <div id="loginErr" style="color:var(--red); font-size:12px; text-align:center; margin-top:14px; font-weight:bold;"></div>
    </div>
  </div>

  <header>
    <a href="/" class="brand">
      <div class="logo">UP</div>
      <div class="title">
        <h1>UniversePurpur Control Center</h1>
        <p>ENTERPRISE MULTI-PORT CONTROL CENTER • SCON.HOST</p>
      </div>
    </a>
    <div class="header-right">
      <div class="status-pill">
        <div class="pulse-dot"></div>
        <span id="headerThrottle">Óptimo (20.0 TPS)</span>
      </div>
      <div class="user-pill">
        <span>Conectado: <b id="currentUserName">admin</b> (<b id="currentUserRole" style="color:var(--purple);">ADMIN</b>)</span>
      </div>
      <button class="btn btn-red" style="padding:6px 14px;" onclick="logout()">Salir</button>
    </div>
  </header>

  <div class="layout">
    <nav>
      <button class="nav-btn active" id="nav-dashboard" onclick="showTab('dashboard')"><span class="nav-icon">📊</span> Dashboard</button>
      <button class="nav-btn" id="nav-players" onclick="showTab('players')"><span class="nav-icon">👥</span> Jugadores</button>
      <button class="nav-btn" id="nav-console" onclick="showTab('console')"><span class="nav-icon">💻</span> Consola Titan</button>
      <button class="nav-btn" id="nav-tuning" onclick="showTab('tuning')"><span class="nav-icon">⚡</span> Motor & Tuner</button>
      <button class="nav-btn" id="nav-branding" onclick="showTab('branding')"><span class="nav-icon">🎨</span> Branding & Web</button>
      <button class="nav-btn" id="nav-users" onclick="showTab('users')"><span class="nav-icon">🔑</span> Usuarios & Roles</button>
    </nav>

    <main class="content">
      <!-- TAB: DASHBOARD -->
      <section id="tab-dashboard" class="tab active">
        <!-- Quick Action Toolbar -->
        <div class="quick-toolbar">
          <div class="action-chip" onclick="triggerTrim()">🧹 Poda Suave de RAM</div>
          <div class="action-chip" onclick="triggerPurge('items')">🗑️ Purgar Ítems Sueltos</div>
          <div class="action-chip" onclick="triggerPurge('monsters')">🧟 Purgar Monstruos</div>
          <div class="action-chip" onclick="setLag('auto')">🛡️ Modo Escudo AUTO</div>
          <div class="action-chip" onclick="setProfile('ultra-low-ram-cpu')">🍃 Perfil Ultra-Low</div>
          <div class="action-chip" onclick="setProfile('15k-hyper-hub')">🏛️ Perfil Hyper-Hub</div>
        </div>

        <div class="grid">
          <div class="card">
            <h3>Rendimiento del Motor <span class="pill pill-green" id="throttlePill">Óptimo</span></h3>
            <div class="val" id="tpsVal">20.00</div>
            <div class="sub">Tick MSPT: <span id="msptVal" style="color:var(--cyan);">0.0 ms</span> | Chunks: <span id="chunksVal">0</span></div>
          </div>
          <div class="card">
            <h3>Jugadores Online <span class="pill pill-green" id="profileBadge">Profile</span></h3>
            <div class="val" id="playersVal">0 / 15,000</div>
            <div class="sub">Spatial 2D: <span id="spatialVal" style="color:var(--cyan);">0</span> buckets | Linked: <span id="linkedVal">0</span></div>
          </div>
          <div class="card">
            <h3>Memoria Heap JVM <span class="pill" id="ramPercentPill">0%</span></h3>
            <div class="val" id="ramVal">0 MB</div>
            <div class="sub">Pausa GC: <span id="gcVal">0</span> ms | Podas Auto: <span id="trimCountVal">0</span></div>
          </div>
          <div class="card">
            <h3>Shield Anti-Crash & Zero-Day <span class="pill pill-green">Activo</span></h3>
            <div class="val" id="shieldVal">0</div>
            <div class="sub">Payloads: <span id="payloadsVal">0</span> | Movement: <span id="movVal">0</span></div>
          </div>
        </div>

        <!-- Real-time Canvas Charts -->
        <div class="charts-grid">
          <div class="chart-box">
            <div class="chart-header">
              <h4>Monitor de Rendimiento en Tiempo Real (TPS - 60s)</h4>
              <span id="chartTpsLabel">20.00 TPS</span>
            </div>
            <canvas id="tpsChart"></canvas>
          </div>
          <div class="chart-box">
            <div class="chart-header">
              <h4>Tiempo de Tick (MSPT - 60s)</h4>
              <span id="chartMsptLabel">0.0 ms</span>
            </div>
            <canvas id="msptChart"></canvas>
          </div>
        </div>

        <!-- Ports and Environment Details -->
        <div class="card">
          <h3>Puertos del Servidor y Estado Multi-Versión</h3>
          <div style="display:grid; grid-template-columns:repeat(auto-fit, minmax(200px, 1fr)); gap:16px; margin-top:10px;">
            <div>
              <span style="font-size:11px; text-transform:uppercase; color:var(--muted); font-weight:700;">☕ Minecraft Java</span>
              <p style="font-size:18px; font-weight:800; color:#fff;" id="pJava">25565</p>
            </div>
            <div>
              <span style="font-size:11px; text-transform:uppercase; color:var(--muted); font-weight:700;">🌐 Web Admin & Portal</span>
              <p style="font-size:18px; font-weight:800; color:var(--cyan);" id="pWeb">25566</p>
            </div>
            <div>
              <span style="font-size:11px; text-transform:uppercase; color:var(--muted); font-weight:700;">📱 Geyser Bedrock UDP</span>
              <p style="font-size:18px; font-weight:800; color:var(--green);" id="pBedrock">19132</p>
            </div>
            <div>
              <span style="font-size:11px; text-transform:uppercase; color:var(--muted); font-weight:700;">⚡ MultiVersión Soporte</span>
              <p style="font-size:18px; font-weight:800; color:var(--purple);" id="multiVerRange">1.21.x -> 1.26.x</p>
            </div>
          </div>
        </div>
      </section>

      <!-- TAB: PLAYERS -->
      <section id="tab-players" class="tab">
        <div class="card">
          <div style="display:flex; justify-content:space-between; align-items:center;">
            <h3>Jugadores Conectados en Tiempo Real</h3>
            <button class="btn" style="padding:6px 14px;" onclick="loadPlayers()">🔄 Refrescar Lista</button>
          </div>
          <div class="table-container">
            <table>
              <thead>
                <tr>
                  <th>Avatar & Jugador</th>
                  <th>Mundo</th>
                  <th>Coordenadas (X, Y, Z)</th>
                  <th>Ping</th>
                  <th>Salud</th>
                  <th>Discord</th>
                  <th>Rango</th>
                  <th>Acciones de Moderación</th>
                </tr>
              </thead>
              <tbody id="playersBody">
                <tr><td colspan="8" style="text-align:center; color:var(--muted);">Cargando jugadores...</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <!-- TAB: CONSOLE -->
      <section id="tab-console" class="tab">
        <div class="card">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:10px;">
            <h3>Consola Remota Titan</h3>
            <button class="btn" style="padding:4px 10px; font-size:11px;" onclick="clearConsole()">Limpiar</button>
          </div>
          <div class="console-chips">
            <span class="cmd-chip" onclick="quickCommand('tps')">tps</span>
            <span class="cmd-chip" onclick="quickCommand('version')">version</span>
            <span class="cmd-chip" onclick="quickCommand('memory')">memory</span>
            <span class="cmd-chip" onclick="quickCommand('lag')">lag</span>
            <span class="cmd-chip" onclick="quickCommand('save-all')">save-all</span>
            <span class="cmd-chip" onclick="quickCommand('whitelist list')">whitelist list</span>
          </div>
          <div class="console-box" id="consoleBox">
            [UniverseTitan] Consola remota conectada y lista.<br>
            [UniverseTitan] Presiona las teclas Flecha Arriba y Abajo para navegar el historial de comandos.
          </div>
          <div class="console-row">
            <input type="text" id="consoleInput" placeholder="Escribe un comando (ej: tps, gamerule, kick)..." onkeydown="handleConsoleKey(event)">
            <button class="btn" onclick="executeCommand()">Enviar Comando</button>
          </div>
        </div>
      </section>

      <!-- TAB: TUNING -->
      <section id="tab-tuning" class="tab">
        <div class="card" style="margin-bottom:20px;">
          <h3>Ajustes de Perfiles del Motor</h3>
          <p style="font-size:13px; color:var(--muted); margin:8px 0 16px;">Cambia el comportamiento interno del servidor en caliente sin necesidad de reiniciar.</p>
          <div style="display:grid; grid-template-columns:repeat(auto-fit, minmax(220px, 1fr)); gap:14px;">
            <div class="action-chip" style="flex-direction:column; align-items:flex-start; padding:16px;" onclick="setProfile('ultra-low-ram-cpu')">
              <b style="color:var(--green);">🍃 ultra-low-ram-cpu</b>
              <span style="font-size:12px; color:var(--muted); margin-top:4px;">Para VPS y hostings económicos. View-dist 4, sim-dist 2, 0 colisiones.</span>
            </div>
            <div class="action-chip" style="flex-direction:column; align-items:flex-start; padding:16px;" onclick="setProfile('15k-hyper-hub')">
              <b style="color:var(--cyan);">🏛️ 15k-hyper-hub</b>
              <span style="font-size:12px; color:var(--muted); margin-top:4px;">Lobbies gigantes de 3k a 15k usuarios. Player culling dinámico a 24.</span>
            </div>
            <div class="action-chip" style="flex-direction:column; align-items:flex-start; padding:16px;" onclick="setProfile('15k-mega-arena')">
              <b style="color:var(--purple);">⚔️ 15k-mega-arena</b>
              <span style="font-size:12px; color:var(--muted); margin-top:4px;">Arenas de Bedwars, Skywars, Duels con tick rate optimizado.</span>
            </div>
            <div class="action-chip" style="flex-direction:column; align-items:flex-start; padding:16px;" onclick="setProfile('15k-scale-smp')">
              <b style="color:var(--yellow);">🌲 15k-scale-smp</b>
              <span style="font-size:12px; color:var(--muted); margin-top:4px;">Supervivencia masiva y Skyblock con IA mob adaptativa.</span>
            </div>
          </div>
        </div>

        <div class="card">
          <h3>Operaciones Manuales de Emergencia</h3>
          <div style="display:flex; gap:12px; margin-top:14px; flex-wrap:wrap;">
            <button class="btn" style="background:#00b4d8;" onclick="triggerTrim()">🧹 Poda Suave de Cachés</button>
            <button class="btn" style="background:#e63946;" onclick="triggerPurge('items')">🗑️ Purgar Todos los Ítems</button>
            <button class="btn" style="background:#f77f00;" onclick="triggerPurge('monsters')">🧟 Purgar Mobs Hostiles</button>
            <button class="btn" style="background:#7209b7;" onclick="setLag('auto')">🛡️ Escudo AUTO</button>
            <button class="btn" style="background:#d90429;" onclick="setLag('on')">⚠️ Lockdown de Emergencia</button>
          </div>
        </div>
      </section>

      <!-- TAB: BRANDING -->
      <section id="tab-branding" class="tab">
        <div class="card">
          <h3>Personalización Visual de la Web Pública</h3>
          <p style="font-size:13px; color:var(--muted); margin:6px 0 18px;">Modifica los textos, IP e imágenes del portal público al instante.</p>
          <div class="form-row">
            <div class="form-group">
              <label>Título del Servidor:</label>
              <input type="text" id="bTitle">
            </div>
            <div class="form-group">
              <label>IP del Servidor (a mostrar y copiar):</label>
              <input type="text" id="bIp">
            </div>
          </div>
          <div class="form-group" style="margin-bottom:16px;">
            <label>Descripción / Mensaje de Bienvenida:</label>
            <input type="text" id="bDesc">
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>URL del Logo:</label>
              <input type="text" id="bLogo">
            </div>
            <div class="form-group">
              <label>URL del Favicon:</label>
              <input type="text" id="bFavicon">
            </div>
          </div>
          <div class="form-group" style="margin-bottom:20px;">
            <label>Enlace de Invitación de Discord:</label>
            <input type="text" id="bDiscord">
          </div>
          <button class="btn" onclick="saveBranding()">Guardar Configuración de Branding</button>
        </div>
      </section>

      <!-- TAB: USERS -->
      <section id="tab-users" class="tab">
        <div class="card" style="margin-bottom:20px;">
          <h3>Crear Nuevo Usuario del Panel</h3>
          <div class="form-row" style="margin-top:14px;">
            <div class="form-group">
              <label>Nombre de Usuario:</label>
              <input type="text" id="newUsername" placeholder="moderador1">
            </div>
            <div class="form-group">
              <label>Contraseña:</label>
              <input type="password" id="newPassword" placeholder="••••••••">
            </div>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>Rol y Privilegios:</label>
              <select id="newRole">
                <option value="MODERATOR">Moderador (Solo Moderación de Jugadores)</option>
                <option value="SUPER_ADMIN">Super Administrador (Control Total & Consola)</option>
              </select>
            </div>
            <div style="display:flex; align-items:flex-end;">
              <button class="btn" style="width:100%; height:44px;" onclick="createUser()">+ Crear Usuario</button>
            </div>
          </div>
        </div>

        <div class="card">
          <h3>Usuarios Registrados</h3>
          <div class="table-container">
            <table>
              <thead>
                <tr>
                  <th>Usuario</th>
                  <th>Rol</th>
                  <th>Fecha de Creación</th>
                  <th>Acciones</th>
                </tr>
              </thead>
              <tbody id="usersBody"></tbody>
            </table>
          </div>
        </div>
      </section>
    </main>
  </div>

  <script>
    let token = localStorage.getItem('up_session_token') || '';
    const tpsHistory = new Array(60).fill(20.0);
    const msptHistory = new Array(60).fill(0.0);
    const commandHistory = [];
    let historyIndex = -1;

    function showToast(message, type = 'info') {
      const container = document.getElementById('toastContainer');
      const toast = document.createElement('div');
      toast.className = 'toast ' + (type === 'success' ? 'toast-success' : type === 'error' ? 'toast-error' : '');
      toast.innerHTML = (type === 'success' ? '✔ ' : type === 'error' ? '❌ ' : 'ℹ ') + escapeHtml(message);
      container.appendChild(toast);
      setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateX(100%)';
        setTimeout(() => toast.remove(), 300);
      }, 3200);
    }

    function checkAuth() {
      if (!token) {
        document.getElementById('loginModal').style.display = 'flex';
      } else {
        document.getElementById('loginModal').style.display = 'none';
        loadMe();
        refreshStatus();
      }
    }

    async function executeLogin() {
      const u = document.getElementById('loginUsername').value.trim();
      const p = document.getElementById('loginPassword').value.trim();
      const err = document.getElementById('loginErr');
      err.innerText = '';
      try {
        const res = await fetch('/api/auth/login', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username: u, password: p })
        });
        const data = await res.json();
        if (data && data.success) {
          token = data.token;
          localStorage.setItem('up_session_token', token);
          checkAuth();
          showToast('Bienvenido, ' + data.username, 'success');
        } else {
          err.innerText = data.message || 'Credenciales incorrectas';
        }
      } catch (e) {
        err.innerText = 'Error de conexión con el servidor';
      }
    }

    function logout() {
      token = '';
      localStorage.removeItem('up_session_token');
      checkAuth();
    }

    async function api(path, method = 'GET', body = null) {
      const opts = { method, headers: { 'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json' } };
      if (body) opts.body = JSON.stringify(body);
      try {
        const res = await fetch(path, opts);
        if (res.status === 401) { logout(); return null; }
        return await res.json();
      } catch (e) { return null; }
    }

    function escapeHtml(str) {
      if (!str && str !== 0) return '';
      return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    function showTab(id) {
      document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
      document.querySelectorAll('.nav-btn').forEach(b => b.classList.remove('active'));
      const targetTab = document.getElementById('tab-' + id);
      if (targetTab) targetTab.classList.add('active');
      const targetNav = document.getElementById('nav-' + id);
      if (targetNav) targetNav.classList.add('active');
      if (id === 'players') loadPlayers();
      if (id === 'users') loadUsers();
    }

    async function loadMe() {
      const data = await api('/api/auth/me');
      if (data) {
        document.getElementById('currentUserName').innerText = data.username;
        document.getElementById('currentUserRole').innerText = data.role;
      }
    }

    async function refreshStatus() {
      const data = await api('/api/status');
      if (!data) return;

      const tps = data.tps || 20.0;
      const mspt = data.mspt || 0.0;

      // Update history queues
      tpsHistory.push(tps);
      tpsHistory.shift();
      msptHistory.push(mspt);
      msptHistory.shift();

      // Render Charts
      drawChart('tpsChart', tpsHistory, 20.0, '#00e676', 'TPS');
      drawChart('msptChart', msptHistory, 50.0, '#00f0ff', 'ms', true);

      document.getElementById('chartTpsLabel').innerText = tps.toFixed(2) + ' TPS';
      document.getElementById('chartMsptLabel').innerText = mspt.toFixed(1) + ' ms';

      document.getElementById('tpsVal').innerText = tps.toFixed(2);
      document.getElementById('msptVal').innerText = mspt.toFixed(1) + ' ms';
      document.getElementById('chunksVal').innerText = data.loadedChunks || 0;
      document.getElementById('playersVal').innerText = (data.onlinePlayers || 0) + ' / ' + (data.maxPlayers || 15000);
      document.getElementById('profileBadge').innerText = data.profile || 'default';
      document.getElementById('spatialVal').innerText = data.spatialBuckets || 0;
      document.getElementById('linkedVal').innerText = data.linkedAccountsCount || 0;

      document.getElementById('ramVal').innerText = (data.usedMemoryMb || 0) + ' MB / ' + (data.maxMemoryMb || 0) + ' MB';
      const ramPct = (data.memoryUsagePercent || 0).toFixed(1);
      const ramPill = document.getElementById('ramPercentPill');
      ramPill.innerText = ramPct + '%';
      ramPill.className = 'pill ' + (ramPct < 70 ? 'pill-green' : ramPct < 85 ? 'pill-yellow' : 'pill-red');

      document.getElementById('gcVal').innerText = data.gcPauseMs || 0;
      document.getElementById('trimCountVal').innerText = data.autoTrimCount || 0;

      document.getElementById('shieldVal').innerText = data.blockedExploits || 0;
      document.getElementById('payloadsVal').innerText = data.blockedPayloads || 0;
      document.getElementById('movVal').innerText = data.blockedMovement || 0;

      if (data.ports) {
        document.getElementById('pJava').innerText = data.ports.minecraftJava || 25565;
        document.getElementById('pWeb').innerText = data.ports.webAdmin || 25566;
        document.getElementById('pBedrock').innerText = data.ports.geyserBedrock || 19132;
      }
      if (data.multiVersion) {
        document.getElementById('multiVerRange').innerText = data.multiVersion.range || '1.21.x -> 1.26.x';
      }

      const pill = document.getElementById('throttlePill');
      pill.innerText = data.throttleName || 'Óptimo';
      pill.className = 'pill ' + (data.throttleLevel === 0 ? 'pill-green' : data.throttleLevel <= 2 ? 'pill-yellow' : 'pill-red');
      document.getElementById('headerThrottle').innerText = (data.throttleName || 'Óptimo') + ' (' + tps.toFixed(2) + ' TPS)';

      // Auto-populate branding form if empty
      if (!document.getElementById('bTitle').value && data.title) {
        document.getElementById('bTitle').value = data.title || '';
        document.getElementById('bDesc').value = data.description || '';
        document.getElementById('bLogo').value = data.logoUrl || '';
        document.getElementById('bFavicon').value = data.faviconUrl || '';
        document.getElementById('bIp').value = data.serverIp || '';
        document.getElementById('bDiscord').value = data.discordInvite || '';
      }
    }

    function drawChart(canvasId, data, targetMax, strokeColor, labelUnit, isMspt = false) {
      const cvs = document.getElementById(canvasId);
      if (!cvs) return;
      const ctx = cvs.getContext('2d');
      const w = cvs.clientWidth || 300;
      const h = cvs.clientHeight || 150;
      cvs.width = w;
      cvs.height = h;

      ctx.clearRect(0, 0, w, h);

      // Grid Lines
      ctx.strokeStyle = 'rgba(255,255,255,0.06)';
      ctx.lineWidth = 1;
      for (let y = 0; y < 4; y++) {
        const lineY = h - (y / 3) * (h - 20) - 10;
        ctx.beginPath();
        ctx.moveTo(0, lineY);
        ctx.lineTo(w, lineY);
        ctx.stroke();
      }

      // If MSPT, draw 50ms red line
      if (isMspt) {
        const lineY = Math.max(10, h - (50.0 / Math.max(60.0, Math.max(...data))) * (h - 20) - 10);
        ctx.strokeStyle = 'rgba(255, 23, 68, 0.4)';
        ctx.setLineDash([4, 4]);
        ctx.beginPath();
        ctx.moveTo(0, lineY);
        ctx.lineTo(w, lineY);
        ctx.stroke();
        ctx.setLineDash([]);
      }

      const maxVal = Math.max(targetMax, Math.max(...data) * 1.15);
      const stepX = w / (data.length - 1);

      // Draw Filled Gradient Area
      ctx.beginPath();
      for (let i = 0; i < data.length; i++) {
        const x = i * stepX;
        const y = h - (data[i] / maxVal) * (h - 20) - 10;
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      }
      ctx.lineTo(w, h);
      ctx.lineTo(0, h);
      ctx.closePath();
      const grad = ctx.createLinearGradient(0, 0, 0, h);
      grad.addColorStop(0, strokeColor + '44');
      grad.addColorStop(1, strokeColor + '00');
      ctx.fillStyle = grad;
      ctx.fill();

      // Draw Main Stroke
      ctx.beginPath();
      for (let i = 0; i < data.length; i++) {
        const x = i * stepX;
        const y = h - (data[i] / maxVal) * (h - 20) - 10;
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      }
      ctx.strokeStyle = strokeColor;
      ctx.lineWidth = 2.5;
      ctx.stroke();
    }

    async function loadPlayers() {
      const data = await api('/api/players');
      const tbody = document.getElementById('playersBody');
      if (!data || !data.players || data.players.length === 0) {
        tbody.innerHTML = '<tr><td colspan="8" style="text-align:center; color:var(--muted); padding:25px;">Sin jugadores en línea en este momento.</td></tr>';
        return;
      }
      tbody.innerHTML = data.players.map(p => `
        <tr>
          <td>
            <div class="player-row">
              <img src="https://mc-heads.net/avatar/${escapeHtml(p.name)}/32" class="player-avatar" onerror="this.src='https://mc-heads.net/avatar/Steve/32'">
              <b>${escapeHtml(p.name)}</b>
            </div>
          </td>
          <td>${escapeHtml(p.world)}</td>
          <td><code>${p.x.toFixed(0)}, ${p.y.toFixed(0)}, ${p.z.toFixed(0)}</code></td>
          <td><span class="pill ${p.ping < 60 ? 'pill-green' : p.ping < 140 ? 'pill-yellow' : 'pill-red'}">${p.ping}ms</span></td>
          <td>❤️ ${p.health.toFixed(0)}</td>
          <td>${p.discord ? '<b style="color:var(--cyan);">' + escapeHtml(p.discord) + '</b>' : '<span style="color:var(--muted);">Sin vincular</span>'}</td>
          <td>${p.op ? '<span class="pill pill-green">OP</span>' : 'Usuario'}</td>
          <td>
            <button class="btn-act btn-kick" onclick="playerAction('${escapeHtml(p.name)}', 'kick')">Kick</button>
            <button class="btn-act btn-ban" onclick="playerAction('${escapeHtml(p.name)}', 'ban')">Ban</button>
            <button class="btn-act btn-op" onclick="playerAction('${escapeHtml(p.name)}', 'op')">${p.op ? 'De-OP' : 'OP'}</button>
          </td>
        </tr>
      `).join('');
    }

    async function playerAction(name, action) {
      const reason = prompt('Razón para ' + action + ' a ' + name + ':', action === 'ban' ? 'Suspendido por administrador' : 'Expulsado por administrador');
      if (reason === null) return;
      const res = await api('/api/player/action', 'POST', { target: name, action, reason });
      if (res && res.message) showToast(res.message, res.success ? 'success' : 'error');
      loadPlayers();
      refreshStatus();
    }

    function quickCommand(cmd) {
      document.getElementById('consoleInput').value = cmd;
      executeCommand();
    }

    function clearConsole() {
      document.getElementById('consoleBox').innerHTML = '[UniverseTitan] Consola limpiada.';
    }

    function handleConsoleKey(event) {
      if (event.key === 'Enter') {
        executeCommand();
      } else if (event.key === 'ArrowUp') {
        if (commandHistory.length > 0 && historyIndex < commandHistory.length - 1) {
          historyIndex++;
          document.getElementById('consoleInput').value = commandHistory[commandHistory.length - 1 - historyIndex];
        }
      } else if (event.key === 'ArrowDown') {
        if (historyIndex > 0) {
          historyIndex--;
          document.getElementById('consoleInput').value = commandHistory[commandHistory.length - 1 - historyIndex];
        } else if (historyIndex === 0) {
          historyIndex = -1;
          document.getElementById('consoleInput').value = '';
        }
      }
    }

    async function executeCommand() {
      const input = document.getElementById('consoleInput');
      const cmd = input.value.trim();
      if (!cmd) return;
      commandHistory.push(cmd);
      historyIndex = -1;
      input.value = '';

      const log = document.getElementById('consoleBox');
      log.innerHTML += `<br><span style="color:var(--cyan); font-weight:bold;">&gt; ${escapeHtml(cmd)}</span>`;
      log.scrollTop = log.scrollHeight;

      const res = await api('/api/command', 'POST', { command: cmd });
      if (res && res.success) {
        log.innerHTML += `<br><span style="color:var(--muted);">[Consola] Ejecutado: ${escapeHtml(res.dispatched)}</span>`;
      } else {
        log.innerHTML += `<br><span style="color:var(--red);">[Error] ${res ? escapeHtml(res.error || res.message) : 'Fallo de ejecución'}</span>`;
      }
      log.scrollTop = log.scrollHeight;
      refreshStatus();
    }

    async function loadUsers() {
      const data = await api('/api/users');
      const tbody = document.getElementById('usersBody');
      if (!data || !data.users) return;
      tbody.innerHTML = data.users.map(u => `
        <tr>
          <td><b>${escapeHtml(u.username)}</b></td>
          <td><span class="pill ${u.role === 'SUPER_ADMIN' ? 'pill-green' : 'pill-yellow'}">${escapeHtml(u.role)}</span></td>
          <td>${new Date(u.createdAt).toLocaleDateString()}</td>
          <td>
            <button class="btn-act" style="background:#00b4d8; color:#fff;" onclick="changeUserPassword('${escapeHtml(u.username)}')">Clave</button>
            ${u.username !== 'admin' ? `<button class="btn-act btn-ban" onclick="deleteUser('${escapeHtml(u.username)}')">Eliminar</button>` : '<i>Protegido</i>'}
          </td>
        </tr>
      `).join('');
    }

    async function changeUserPassword(username) {
      const p = prompt('Nueva contraseña para ' + username + ' (mínimo 4 caracteres):');
      if (!p) return;
      const res = await api('/api/users/password', 'POST', { username, newPassword: p });
      showToast(res ? res.message : 'Error', res && res.success ? 'success' : 'error');
    }

    async function createUser() {
      const u = document.getElementById('newUsername').value.trim();
      const p = document.getElementById('newPassword').value.trim();
      const r = document.getElementById('newRole').value;
      if (!u || !p) { showToast('Completa todos los campos', 'error'); return; }
      const res = await api('/api/users/create', 'POST', { username: u, password: p, role: r });
      showToast(res ? res.message : 'Error', res && res.success ? 'success' : 'error');
      document.getElementById('newUsername').value = '';
      document.getElementById('newPassword').value = '';
      loadUsers();
    }

    async function deleteUser(username) {
      if (!confirm('¿Eliminar usuario ' + username + '?')) return;
      const res = await api('/api/users/delete', 'POST', { username });
      showToast('Usuario eliminado', 'success');
      loadUsers();
    }

    async function saveBranding() {
      const body = {
        title: document.getElementById('bTitle').value,
        description: document.getElementById('bDesc').value,
        serverIp: document.getElementById('bIp').value,
        logoUrl: document.getElementById('bLogo').value,
        faviconUrl: document.getElementById('bFavicon').value,
        discordUrl: document.getElementById('bDiscord').value
      };
      const res = await api('/api/settings/branding', 'POST', body);
      showToast(res ? res.message : 'Error al guardar', res && res.success ? 'success' : 'error');
      refreshStatus();
    }

    async function setProfile(name) {
      await api('/api/actions', 'POST', { action: 'profile', value: name });
      showToast('Perfil cambiado a: ' + name, 'success');
      refreshStatus();
    }

    async function triggerTrim() {
      const res = await api('/api/actions', 'POST', { action: 'trim' });
      showToast('Poda de memoria ejecutada. Particiones: ' + (res ? res.freed : 0), 'success');
      refreshStatus();
    }

    async function triggerPurge(val) {
      await api('/api/actions', 'POST', { action: 'purge', value: val });
      showToast('Purga de ' + val + ' completada', 'success');
      refreshStatus();
    }

    async function setLag(mode) {
      await api('/api/actions', 'POST', { action: 'lagprotection', value: mode });
      showToast('Modo Lag Shield establecido a: ' + mode, 'success');
      refreshStatus();
    }

    checkAuth();
    setInterval(refreshStatus, 2500);
  </script>
</body>
</html>
""";
}
