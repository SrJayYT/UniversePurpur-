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

            Bukkit.getLogger().info(String.format(
                    "\u001B[32m[UniversePurpur] Web Server online: Public Portal at http://%s:%d | Admin Panel at http://%s:%d/admin\u001B[0m",
                    (host == null || host.equals("0.0.0.0")) ? "127.0.0.1" : host, port,
                    (host == null || host.equals("0.0.0.0")) ? "127.0.0.1" : host, port
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


    // --- Modern Public Web Portal SPA ---
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
      --bg: #07080f;
      --card-bg: rgba(16, 18, 33, 0.75);
      --card-border: rgba(157, 78, 221, 0.25);
      --accent-purple: #9d4edd;
      --accent-cyan: #00f0ff;
      --accent-green: #00e676;
      --text-main: #f0f2f5;
      --text-muted: #9aa0bc;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Segoe UI', system-ui, -apple-system, sans-serif; }
    body { background: radial-gradient(circle at 50% 20%, #15102a, var(--bg) 80%); color: var(--text-main); min-height: 100vh; display: flex; flex-direction: column; overflow-x: hidden; }
    header { display: flex; justify-content: space-between; align-items: center; padding: 25px 50px; backdrop-filter: blur(12px); border-bottom: 1px solid rgba(255,255,255,0.05); }
    .brand { display: flex; align-items: center; gap: 15px; }
    .brand img { height: 42px; border-radius: 8px; box-shadow: 0 0 15px rgba(157,78,221,0.5); }
    .brand h1 { font-size: 22px; font-weight: 800; background: linear-gradient(90deg, #fff, var(--accent-cyan)); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
    .nav-links a { color: var(--text-muted); text-decoration: none; margin-left: 25px; font-weight: 600; font-size: 14px; transition: 0.2s; }
    .nav-links a:hover, .nav-links a.active { color: var(--accent-cyan); }
    .btn-admin { background: rgba(157,78,221,0.15); border: 1px solid var(--accent-purple); color: #fff !important; padding: 8px 18px; border-radius: 8px; }
    .hero { text-align: center; padding: 70px 20px 40px; max-width: 900px; margin: 0 auto; }
    .hero-badge { display: inline-flex; align-items: center; gap: 8px; background: rgba(0,240,255,0.1); border: 1px solid rgba(0,240,255,0.3); color: var(--accent-cyan); padding: 6px 16px; border-radius: 30px; font-size: 12px; font-weight: 700; margin-bottom: 20px; }
    .hero h2 { font-size: 46px; font-weight: 900; line-height: 1.15; margin-bottom: 15px; background: linear-gradient(135deg, #ffffff 40%, var(--accent-cyan)); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
    .hero p { font-size: 17px; color: var(--text-muted); line-height: 1.6; margin-bottom: 30px; }
    .ip-box { display: inline-flex; align-items: center; background: rgba(14, 16, 29, 0.9); border: 1px solid var(--card-border); padding: 12px 24px; border-radius: 12px; gap: 15px; cursor: pointer; transition: 0.3s; box-shadow: 0 8px 30px rgba(0,0,0,0.5); }
    .ip-box:hover { border-color: var(--accent-cyan); transform: translateY(-2px); box-shadow: 0 12px 35px rgba(0,240,255,0.2); }
    .ip-box span { font-family: monospace; font-size: 18px; font-weight: bold; color: var(--accent-cyan); }
    .ip-box small { color: var(--accent-green); font-weight: 700; font-size: 12px; }
    .stats-row { display: flex; justify-content: center; gap: 30px; margin: 40px 0 60px; flex-wrap: wrap; }
    .stat-pill { background: var(--card-bg); border: 1px solid var(--card-border); padding: 18px 32px; border-radius: 14px; text-align: center; min-width: 170px; backdrop-filter: blur(10px); }
    .stat-pill .num { font-size: 28px; font-weight: 900; color: #fff; }
    .stat-pill .lbl { font-size: 12px; color: var(--text-muted); text-transform: uppercase; margin-top: 4px; font-weight: 600; }
    .link-section { max-width: 600px; margin: 0 auto 80px; background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 20px; padding: 35px; backdrop-filter: blur(15px); box-shadow: 0 20px 40px rgba(0,0,0,0.6); }
    .link-section h3 { font-size: 22px; font-weight: 800; color: #fff; margin-bottom: 8px; display: flex; align-items: center; gap: 10px; }
    .link-section p { font-size: 14px; color: var(--text-muted); margin-bottom: 25px; line-height: 1.5; }
    .form-group { margin-bottom: 16px; }
    .form-group label { display: block; font-size: 12px; text-transform: uppercase; font-weight: 700; color: var(--text-muted); margin-bottom: 6px; }
    .form-group input { width: 100%; background: #0c0e18; border: 1px solid rgba(255,255,255,0.1); padding: 12px 16px; border-radius: 8px; color: #fff; font-size: 15px; outline: none; transition: 0.2s; }
    .form-group input:focus { border-color: var(--accent-purple); box-shadow: 0 0 10px rgba(157,78,221,0.3); }
    .btn-submit { width: 100%; background: linear-gradient(135deg, var(--accent-purple), #7b2cbf); border: none; padding: 14px; border-radius: 10px; color: #fff; font-size: 15px; font-weight: 800; cursor: pointer; transition: 0.2s; box-shadow: 0 4px 20px rgba(157,78,221,0.4); }
    .btn-submit:hover { filter: brightness(1.2); transform: translateY(-2px); }
    footer { text-align: center; padding: 30px; font-size: 13px; color: var(--text-muted); border-top: 1px solid rgba(255,255,255,0.05); margin-top: auto; }
    footer a { color: var(--accent-cyan); text-decoration: none; }
  </style>
</head>
<body>
  <header>
    <div class="brand">
      <img src="/assets/universepurpur_banner.png" alt="Logo" id="headerLogo" onerror="this.style.display='none'">
      <h1 id="headerTitle">UniversePurpur</h1>
    </div>
    <div class="nav-links">
      <a href="/" class="active">Inicio</a>
      <a href="#linkSection">Vincular Discord</a>
      <a href="https://discord.gg/universepurpur" target="_blank" id="discordLinkBtn">Discord Oficial</a>
      <a href="/admin" class="btn-admin">Panel Admin</a>
    </div>
  </header>

  <main>
    <section class="hero">
      <div class="hero-badge">⚡ MOTOR TITAN +15,000 JUGADORES ONLINE</div>
      <h2 id="heroTitle">Bienvenido a UniversePurpur</h2>
      <p id="heroDesc">La experiencia de Minecraft más fluida y optimizada del mundo. Juega sin lag con tasa de 20.0 TPS continua y compatibilidad crossplay.</p>

      <div class="ip-box" onclick="copyIp()">
        <span id="ipText">play.universenetwork.net</span>
        <small id="copyNotice">📋 Haz clic para copiar</small>
      </div>

      <div class="stats-row">
        <div class="stat-pill">
          <div class="num" id="statPlayers">0</div>
          <div class="lbl">Jugadores en Línea</div>
        </div>
        <div class="stat-pill">
          <div class="num" id="statTps">20.00</div>
          <div class="lbl">TPS del Servidor</div>
        </div>
        <div class="stat-pill">
          <div class="num" id="statMspt">0.0 ms</div>
          <div class="lbl">Tiempo de Tick (MSPT)</div>
        </div>
        <div class="stat-pill">
          <div class="num" id="statLinked">0</div>
          <div class="lbl">Cuentas Vinculadas</div>
        </div>
      </div>
    </section>

    <section class="link-section" id="linkSection">
      <h3>🔗 Vinculación con Discord</h3>
      <p>Vincula tu cuenta de Minecraft con nuestro servidor de Discord para obtener recompensas exclusivas, rangos y sincronización automática.</p>

      <div class="form-group">
        <label>1. Código de Vinculación (Escribe <b>/link</b> en el juego):</label>
        <input type="text" id="linkCodeInput" placeholder="Ejemplo: UP-839201" maxlength="9">
      </div>
      <div class="form-group">
        <label>2. Tu Usuario o ID de Discord:</label>
        <input type="text" id="discordIdInput" placeholder="Ejemplo: MiUsuario#1234 o 8392019482910">
      </div>
      <button class="btn-submit" onclick="submitDiscordLink()">Vincular Mi Cuenta Ahora</button>
      <div id="linkResultMsg" style="margin-top:15px; font-size:14px; text-align:center; font-weight:bold;"></div>
    </section>
  </main>

  <footer>
    <p>Impulsado por <b>UniversePurpur</b> | Autor: <b>DevPapo</b> | <a href="/admin">Acceso Administrativo</a></p>
  </footer>

  <script>
    async function loadData() {
      try {
        const res = await fetch('/api/status');
        const data = await res.json();
        if (data) {
          document.getElementById('headerTitle').innerText = data.title;
          document.getElementById('heroTitle').innerText = data.title;
          document.getElementById('heroDesc').innerText = data.description;
          document.getElementById('ipText').innerText = data.serverIp;
          document.getElementById('statPlayers').innerText = data.onlinePlayers + ' / ' + data.maxPlayers;
          document.getElementById('statTps').innerText = data.tps.toFixed(2);
          document.getElementById('statMspt').innerText = data.mspt.toFixed(1) + ' ms';
          document.getElementById('statLinked').innerText = data.linkedAccountsCount;
          if (data.discordInvite) document.getElementById('discordLinkBtn').href = data.discordInvite;
          if (data.logoUrl) document.getElementById('headerLogo').src = data.logoUrl;
        }
      } catch (err) {}
    }

    function copyIp() {
      const ip = document.getElementById('ipText').innerText;
      navigator.clipboard.writeText(ip);
      const notice = document.getElementById('copyNotice');
      notice.innerText = '✔ ¡IP Copiada!';
      notice.style.color = '#00e676';
      setTimeout(() => {
        notice.innerText = '📋 Haz clic para copiar';
        notice.style.color = '#00e676';
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
        msg.innerText = '❌ Error de red';
        msg.style.color = '#ff1744';
      }
    }

    loadData();
    setInterval(loadData, 3000);
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
      --bg: #090a12;
      --card: #121422;
      --border: #1f233a;
      --purple: #9d4edd;
      --cyan: #00f0ff;
      --green: #00e676;
      --red: #ff1744;
      --yellow: #ffd600;
      --text: #f0f2f5;
      --muted: #8a8ea8;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Segoe UI', system-ui, sans-serif; }
    body { background: var(--bg); color: var(--text); min-height: 100vh; display: flex; flex-direction: column; }
    header { background: #0e101c; border-bottom: 1px solid var(--border); padding: 15px 30px; display: flex; justify-content: space-between; align-items: center; }
    .brand { display: flex; align-items: center; gap: 15px; }
    .logo { width: 38px; height: 38px; background: linear-gradient(135deg, var(--purple), var(--cyan)); border-radius: 8px; display: flex; align-items: center; justify-content: center; font-weight: 900; font-size: 18px; color: #fff; box-shadow: 0 0 15px rgba(157,78,221,0.5); }
    .title h1 { font-size: 18px; font-weight: 800; background: linear-gradient(90deg, #fff, var(--cyan)); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
    .title p { font-size: 10px; color: var(--purple); font-weight: 700; letter-spacing: 0.5px; }
    .user-pill { display: flex; align-items: center; gap: 12px; }
    .user-pill span { font-size: 13px; color: var(--muted); }
    .btn { background: linear-gradient(135deg, var(--purple), #7b2cbf); border: none; color: #fff; padding: 8px 16px; border-radius: 6px; cursor: pointer; font-weight: 700; font-size: 13px; transition: 0.2s; }
    .btn:hover { filter: brightness(1.2); transform: translateY(-1px); }
    .btn-red { background: var(--red); }
    .layout { display: flex; flex: 1; }
    nav { width: 220px; background: #0b0c16; border-right: 1px solid var(--border); padding: 20px 10px; display: flex; flex-direction: column; gap: 6px; }
    .nav-btn { background: transparent; border: none; color: var(--muted); text-align: left; padding: 12px 16px; border-radius: 8px; cursor: pointer; font-size: 14px; font-weight: 600; transition: 0.2s; display: flex; align-items: center; gap: 10px; }
    .nav-btn.active, .nav-btn:hover { background: #161828; color: var(--cyan); }
    .content { flex: 1; padding: 25px 30px; overflow-y: auto; }
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(210px, 1fr)); gap: 18px; margin-bottom: 25px; }
    .card { background: var(--card); border: 1px solid var(--border); border-radius: 12px; padding: 20px; box-shadow: 0 4px 20px rgba(0,0,0,0.3); }
    .card h3 { font-size: 11px; text-transform: uppercase; letter-spacing: 1px; color: var(--muted); margin-bottom: 8px; }
    .card .val { font-size: 26px; font-weight: 800; color: #fff; }
    .card .sub { font-size: 12px; color: var(--muted); margin-top: 4px; }
    .pill { display: inline-block; padding: 3px 8px; border-radius: 10px; font-size: 11px; font-weight: bold; }
    .pill-green { background: rgba(0,230,118,0.15); color: var(--green); }
    .pill-yellow { background: rgba(255,214,0,0.15); color: var(--yellow); }
    .pill-red { background: rgba(255,23,68,0.15); color: var(--red); }
    .table-container { overflow-x: auto; margin-top: 15px; }
    table { width: 100%; border-collapse: collapse; text-align: left; }
    th { padding: 12px; font-size: 11px; text-transform: uppercase; color: var(--muted); border-bottom: 1px solid var(--border); }
    td { padding: 12px; font-size: 13px; border-bottom: 1px solid #161829; }
    .btn-act { padding: 4px 10px; font-size: 11px; border-radius: 4px; border: none; cursor: pointer; font-weight: bold; margin-right: 5px; }
    .btn-kick { background: #ff9100; color: #000; }
    .btn-ban { background: var(--red); color: #fff; }
    .console-box { background: #08090f; border: 1px solid var(--border); border-radius: 8px; height: 380px; overflow-y: auto; padding: 15px; font-family: monospace; font-size: 13px; color: var(--green); margin-bottom: 12px; }
    .console-row { display: flex; gap: 10px; }
    .console-row input { flex: 1; background: #131524; border: 1px solid var(--border); padding: 10px 14px; border-radius: 6px; color: #fff; font-size: 14px; outline: none; }
    .tab { display: none; }
    .tab.active { display: block; }
    .form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 15px; margin-bottom: 15px; }
    .form-group label { display: block; font-size: 12px; font-weight: 700; color: var(--muted); margin-bottom: 6px; text-transform: uppercase; }
    .form-group input, .form-group select { width: 100%; background: #141628; border: 1px solid var(--border); padding: 10px 14px; border-radius: 6px; color: #fff; font-size: 14px; outline: none; }
    /* Auth Modal */
    #loginModal { position: fixed; inset: 0; background: rgba(5,6,12,0.92); backdrop-filter: blur(10px); display: flex; align-items: center; justify-content: center; z-index: 1000; }
    .login-box { background: #111322; border: 1px solid var(--border); border-radius: 16px; padding: 35px; width: 380px; box-shadow: 0 10px 40px rgba(0,0,0,0.8); }
    .login-box h2 { font-size: 22px; font-weight: 800; margin-bottom: 6px; color: #fff; text-align: center; }
    .login-box p { font-size: 12px; color: var(--muted); text-align: center; margin-bottom: 25px; }
  </style>
</head>
<body>
  <!-- Login Screen -->
  <div id="loginModal">
    <div class="login-box">
      <h2>Iniciar Sesión</h2>
      <p>UniversePurpur Control Center</p>
      <div class="form-group" style="margin-bottom:15px;">
        <label>Usuario o Master Token:</label>
        <input type="text" id="loginUsername" placeholder="admin">
      </div>
      <div class="form-group" style="margin-bottom:25px;">
        <label>Contraseña:</label>
        <input type="password" id="loginPassword" placeholder="••••••••" onkeydown="if(event.key==='Enter') executeLogin()">
      </div>
      <button class="btn" style="width:100%; padding:12px;" onclick="executeLogin()">Acceder al Panel</button>
      <div id="loginErr" style="color:var(--red); font-size:12px; text-align:center; margin-top:12px; font-weight:bold;"></div>
    </div>
  </div>

  <header>
    <div class="brand">
      <div class="logo">UP</div>
      <div class="title">
        <h1>UniversePurpur Admin Panel</h1>
        <p>ENTERPRISE MULTI-PORT CONTROL CENTER</p>
      </div>
    </div>
    <div class="user-pill">
      <span>Conectado: <b id="currentUserName" style="color:var(--cyan);">admin</b> (<b id="currentUserRole" style="color:var(--purple);">ADMIN</b>)</span>
      <button class="btn btn-red" onclick="logout()">Salir</button>
    </div>
  </header>

  <div class="layout">
    <nav>
      <button class="nav-btn active" id="nav-dashboard" onclick="showTab('dashboard')">📊 Dashboard</button>
      <button class="nav-btn" id="nav-players" onclick="showTab('players')">👥 Jugadores</button>
      <button class="nav-btn" id="nav-console" onclick="showTab('console')">💻 Consola</button>
      <button class="nav-btn" id="nav-users" onclick="showTab('users')">🔑 Usuarios</button>
      <button class="nav-btn" id="nav-branding" onclick="showTab('branding')">🎨 Branding & Web</button>
      <button class="nav-btn" id="nav-tuning" onclick="showTab('tuning')">⚡ Motor 15k</button>
    </nav>


    <main class="content">
      <!-- TAB: DASHBOARD -->
      <section id="tab-dashboard" class="tab active">
        <div class="grid">
          <div class="card">
            <h3>Rendimiento (TPS / MSPT)</h3>
            <div class="val" id="tpsVal">20.00</div>
            <div class="sub"><span id="msptVal">0.0 ms</span> | <span class="pill pill-green" id="throttlePill">Óptimo</span></div>
          </div>
          <div class="card">
            <h3>Jugadores Online</h3>
            <div class="val" id="playersVal">0 / 15,000</div>
            <div class="sub">Particionado 2D: <span id="spatialVal" style="color:var(--cyan);">0</span> buckets</div>
          </div>
          <div class="card">
            <h3>Memoria Heap</h3>
            <div class="val" id="ramVal">0 MB</div>
            <div class="sub"><span id="ramPercent">0%</span> usado | GC: <span id="gcVal">0</span> ms</div>
          </div>
          <div class="card">
            <h3>Shield Anti-Crash</h3>
            <div class="val" id="shieldVal">0</div>
            <div class="sub">Ataques y exploits bloqueados</div>
          </div>
        </div>

        <div class="card" style="margin-bottom:20px;">
          <h3>Puertos Activos en el Host</h3>
          <p style="font-size:13px; color:var(--muted); margin-top:8px;">
            • Minecraft Java: <b style="color:#fff;" id="pJava">25565</b><br>
            • Panel Web Admin: <b style="color:var(--cyan);" id="pWeb">25566</b><br>
            • Geyser Bedrock UDP: <b style="color:var(--green);" id="pBedrock">19132</b> (Opcional)
          </p>
        </div>
      </section>

      <!-- TAB: PLAYERS -->
      <section id="tab-players" class="tab">
        <div class="card">
          <div style="display:flex; justify-content:space-between; align-items:center;">
            <h3>Jugadores Conectados en Tiempo Real</h3>
            <button class="btn" onclick="loadPlayers()">🔄 Refrescar</button>
          </div>
          <div class="table-container">
            <table>
              <thead>
                <tr>
                  <th>Jugador</th>
                  <th>Mundo</th>
                  <th>Coords</th>
                  <th>Ping</th>
                  <th>Salud</th>
                  <th>Discord</th>
                  <th>Rango</th>
                  <th>Acciones</th>
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
          <h3>Consola del Servidor</h3>
          <div class="console-box" id="consoleBox">
            [UniversePurpur] Consola remota conectada y lista.
          </div>
          <div class="console-row">
            <input type="text" id="consoleInput" placeholder="Escribe un comando..." onkeydown="if(event.key==='Enter') executeCommand()">
            <button class="btn" onclick="executeCommand()">Enviar</button>
          </div>
        </div>
      </section>

      <!-- TAB: USERS -->
      <section id="tab-users" class="tab">
        <div class="card" style="margin-bottom:20px;">
          <h3>Crear Nuevo Usuario del Panel</h3>
          <div class="form-row" style="margin-top:15px;">
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
              <label>Rol / Permisos:</label>
              <select id="newRole">
                <option value="MODERATOR">Moderador (Solo Moderación de Jugadores)</option>
                <option value="SUPER_ADMIN">Super Administrador (Control Total & Consola)</option>
              </select>
            </div>
            <div style="display:flex; align-items:flex-end;">
              <button class="btn" style="width:100%; height:42px;" onclick="createUser()">+ Crear Usuario</button>
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
                  <th>Creado</th>
                  <th>Acción</th>
                </tr>
              </thead>
              <tbody id="usersBody"></tbody>
            </table>
          </div>
        </div>
      </section>

      <!-- TAB: BRANDING -->
      <section id="tab-branding" class="tab">
        <div class="card">
          <h3>Personalización de la Web Pública & Servidor</h3>
          <div class="form-row" style="margin-top:15px;">
            <div class="form-group">
              <label>Título del Servidor:</label>
              <input type="text" id="bTitle">
            </div>
            <div class="form-group">
              <label>IP a Copiar:</label>
              <input type="text" id="bIp">
            </div>
          </div>
          <div class="form-group" style="margin-bottom:15px;">
            <label>Descripción / Mensaje de Bienvenida:</label>
            <input type="text" id="bDesc">
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>URL del Logo (o /assets/universepurpur_banner.png):</label>
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
          <button class="btn" onclick="saveBranding()">Guardar Cambios de Branding</button>
        </div>
      </section>

      <!-- TAB: TUNING -->
      <section id="tab-tuning" class="tab">
        <div class="card" style="margin-bottom:20px;">
          <h3>Perfiles de Rendimiento 15k</h3>
          <div style="display:flex; gap:10px; margin-top:15px; flex-wrap:wrap;">
            <button class="btn" onclick="setProfile('15k-hyper-hub')">🏛️ 15k-hyper-hub</button>
            <button class="btn" onclick="setProfile('15k-mega-arena')">⚔️ 15k-mega-arena</button>
            <button class="btn" onclick="setProfile('15k-scale-smp')">🌲 15k-scale-smp</button>
          </div>
        </div>
        <div class="card">
          <h3>Mitigación y Mantenimiento</h3>
          <div style="display:flex; gap:10px; margin-top:15px; flex-wrap:wrap;">
            <button class="btn" style="background:#00b4d8;" onclick="triggerTrim()">🧹 Poda de Memoria</button>
            <button class="btn" style="background:#e63946;" onclick="triggerPurge('items')">🗑️ Purgar Ítems</button>
            <button class="btn" style="background:#f77f00;" onclick="triggerPurge('monsters')">🧟 Purgar Monstruos</button>
            <button class="btn" style="background:#7209b7;" onclick="setLag('auto')">🛡️ Lag AUTO</button>
            <button class="btn" style="background:#d90429;" onclick="setLag('on')">⚠️ Lockdown ON</button>
          </div>
        </div>
      </section>
    </main>
  </div>

  <script>
    let token = localStorage.getItem('up_session_token') || '';

    function checkAuth() {
      if (!token) {
        document.getElementById('loginModal').style.display = 'flex';
      } else {
        document.getElementById('loginModal').style.display = 'none';
        loadMe();
        refreshAll();
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
        } else {
          err.innerText = data.message || 'Error de autenticación';
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
      document.getElementById('tpsVal').innerText = data.tps.toFixed(2);
      document.getElementById('msptVal').innerText = data.mspt.toFixed(1) + ' ms';
      document.getElementById('playersVal').innerText = data.onlinePlayers + ' / ' + data.maxPlayers;
      document.getElementById('ramVal').innerText = data.usedMemoryMb + ' MB';
      document.getElementById('ramPercent').innerText = data.memoryUsagePercent.toFixed(1) + '%';
      document.getElementById('shieldVal').innerText = data.blockedExploits;
      document.getElementById('spatialVal').innerText = data.spatialBuckets;
      document.getElementById('gcVal').innerText = data.gcPauseMs;

      if (data.ports) {
        document.getElementById('pJava').innerText = data.ports.minecraftJava;
        document.getElementById('pWeb').innerText = data.ports.webAdmin;
        document.getElementById('pBedrock').innerText = data.ports.geyserBedrock;
      }

      const pill = document.getElementById('throttlePill');
      pill.innerText = data.throttleName;
      pill.className = 'pill ' + (data.throttleLevel === 0 ? 'pill-green' : data.throttleLevel <= 2 ? 'pill-yellow' : 'pill-red');

      // Populate branding inputs if empty
      if (!document.getElementById('bTitle').value) {
        document.getElementById('bTitle').value = data.title || '';
        document.getElementById('bDesc').value = data.description || '';
        document.getElementById('bLogo').value = data.logoUrl || '';
        document.getElementById('bFavicon').value = data.faviconUrl || '';
        document.getElementById('bIp').value = data.serverIp || '';
        document.getElementById('bDiscord').value = data.discordInvite || '';
      }
    }

    async function loadPlayers() {
      const data = await api('/api/players');
      const tbody = document.getElementById('playersBody');
      if (!data || !data.players || data.players.length === 0) {
        tbody.innerHTML = '<tr><td colspan="8" style="text-align:center; color:var(--muted);">Sin jugadores online.</td></tr>';
        return;
      }
      tbody.innerHTML = data.players.map(p => `
        <tr>
          <td><b>${escapeHtml(p.name)}</b></td>
          <td>${escapeHtml(p.world)}</td>
          <td>${p.x.toFixed(0)}, ${p.y.toFixed(0)}, ${p.z.toFixed(0)}</td>
          <td>${p.ping}ms</td>
          <td>❤️ ${p.health.toFixed(0)}</td>
          <td>${p.discord ? '<b style="color:var(--cyan);">' + escapeHtml(p.discord) + '</b>' : '<span style="color:var(--muted);">No vinculado</span>'}</td>
          <td>${p.op ? '<span class="pill pill-green">OP</span>' : 'Usuario'}</td>
          <td>
            <button class="btn-act btn-kick" onclick="playerAction('${escapeHtml(p.name)}', 'kick')">Kick</button>
            <button class="btn-act btn-ban" onclick="playerAction('${escapeHtml(p.name)}', 'ban')">Ban</button>
            <button class="btn-act" style="background:var(--purple); color:#fff;" onclick="playerAction('${escapeHtml(p.name)}', 'op')">${p.op ? 'De-OP' : 'OP'}</button>
          </td>
        </tr>
      `).join('');
    }

    async function playerAction(name, action) {
      const reason = prompt('Razón para ' + action + ' a ' + name + ':', action === 'ban' ? 'Suspendido por administrador' : 'Expulsado por administrador');
      if (reason === null) return;
      const res = await api('/api/player/action', 'POST', { target: name, action, reason });
      if (res && res.message) alert(res.message);
      loadPlayers();
      refreshStatus();
    }

    async function executeCommand() {
      const input = document.getElementById('consoleInput');
      const cmd = input.value.trim();
      if (!cmd) return;
      input.value = '';

      const log = document.getElementById('consoleBox');
      log.innerHTML += `<br><span style="color:var(--cyan);">&gt; ${escapeHtml(cmd)}</span>`;
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
      alert(res ? res.message : 'Error');
    }

    async function createUser() {
      const u = document.getElementById('newUsername').value.trim();
      const p = document.getElementById('newPassword').value.trim();
      const r = document.getElementById('newRole').value;
      if (!u || !p) { alert('Completa todos los campos'); return; }
      const res = await api('/api/users/create', 'POST', { username: u, password: p, role: r });
      alert(res ? res.message : 'Error');
      document.getElementById('newUsername').value = '';
      document.getElementById('newPassword').value = '';
      loadUsers();
    }

    async function deleteUser(username) {
      if (!confirm('¿Eliminar usuario ' + username + '?')) return;
      await api('/api/users/delete', 'POST', { username });
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
      alert(res ? res.message : 'Error al guardar');
      refreshStatus();
    }

    async function setProfile(name) {
      await api('/api/actions', 'POST', { action: 'profile', value: name });
      alert('Perfil cambiado a: ' + name);
      refreshStatus();
    }
    async function triggerTrim() {
      const res = await api('/api/actions', 'POST', { action: 'trim' });
      alert('Poda completada. Particiones: ' + (res ? res.freed : 0));
      refreshStatus();
    }
    async function triggerPurge(val) {
      await api('/api/actions', 'POST', { action: 'purge', value: val });
      alert('Purga completada');
      refreshStatus();
    }
    async function setLag(mode) {
      await api('/api/actions', 'POST', { action: 'lagprotection', value: mode });
      alert('Lag mode: ' + mode);
      refreshStatus();
    }

    function refreshAll() {
      refreshStatus();
    }

    checkAuth();
    setInterval(refreshStatus, 2500);
  </script>
</body>
</html>
""";
}
