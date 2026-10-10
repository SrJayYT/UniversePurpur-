package org.purpurmc.purpur.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
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
import org.purpurmc.purpur.UniversePurpurConfig;
import org.purpurmc.purpur.network.AntiCrashShield;
import org.purpurmc.purpur.task.UniverseGovernorTask;
import org.purpurmc.purpur.task.UniverseMemoryGovernor;
import org.purpurmc.purpur.util.MinecraftInternalPlugin;

/**
 * Built-in Lightweight Web Admin Panel & Control Server for UniversePurpur (+15,000 Players).
 * Hosts an embedded HTTP server directly inside the Minecraft server (default port 25566).
 *
 * Provides real-time dashboard telemetry, full player management (kick, ban, teleport),
 * remote server console command execution, profile switching, and lag mitigation triggers.
 *
 * Author: DevPapo
 */
public final class UniverseWebPanel {
    private static UniverseWebPanel instance;
    private HttpServer server;
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

            server.createContext("/", new StaticGuiHandler());
            server.createContext("/api/status", new StatusHandler());
            server.createContext("/api/players", new PlayersHandler());
            server.createContext("/api/player/action", new PlayerActionHandler());
            server.createContext("/api/command", new CommandHandler());
            server.createContext("/api/actions", new ActionsHandler());

            server.start();
            running = true;
            Bukkit.getLogger().info(String.format(
                    "\u001B[32m[UniversePurpur] Web Admin Panel online at http://%s:%d (Auth Token: %s)\u001B[0m",
                    (host == null || host.equals("0.0.0.0")) ? "127.0.0.1" : host,
                    port,
                    UniversePurpurConfig.webPanelAuthToken.isEmpty() ? "None (Open)" : UniversePurpurConfig.webPanelAuthToken
            ));
        } catch (Throwable ex) {
            Bukkit.getLogger().log(Level.WARNING, "[UniversePurpur] Could not start Web Admin Panel on port " + UniversePurpurConfig.webPanelPort, ex);
            running = false;
        }
    }

    public synchronized void stop() {
        if (server != null) {
            try {
                server.stop(1);
            } catch (Throwable ignore) {
            }
            server = null;
        }
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    private static boolean isAuthorized(HttpExchange exchange) {
        String token = UniversePurpurConfig.webPanelAuthToken;
        if (token == null || token.trim().isEmpty()) {
            return true; // No auth required if token is empty
        }

        // Check Authorization header: Bearer <token>
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String passed = authHeader.substring(7).trim();
            if (token.equals(passed)) {
                return true;
            }
        }

        // Check query parameter ?token=<token>
        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=", 2);
                if (pair.length == 2 && pair[0].equalsIgnoreCase("token") && token.equals(pair[1])) {
                    return true;
                }
            }
        }

        return false;
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

    // --- Handlers ---

    private static class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 200, "{}");
                return;
            }

            if (!isAuthorized(exchange)) {
                sendJsonResponse(exchange, 401, "{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing token\"}");
                return;
            }

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
            int chunks = 0;
            for (World w : Bukkit.getWorlds()) {
                chunks += w.getLoadedChunks().length;
            }

            String json = String.format(java.util.Locale.ROOT, """
                    {
                      "server": "UniversePurpur",
                      "author": "DevPapo",
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
                      "ports": {
                        "minecraftJava": %d,
                        "webAdmin": %d,
                        "geyserBedrock": %d
                      }
                    }
                    """,
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
                    Bukkit.getPort(),
                    UniversePurpurConfig.webPanelPort,
                    UniversePurpurConfig.geyserPort
            );

            sendJsonResponse(exchange, 200, json);
        }
    }

    private static class PlayersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 200, "{}");
                return;
            }

            if (!isAuthorized(exchange)) {
                sendJsonResponse(exchange, 401, "{\"error\":\"Unauthorized\"}");
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("{\"players\":[");
            boolean first = true;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!first) sb.append(",");
                first = false;
                Location loc = p.getLocation();
                String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "world";
                int ping = p.getPing();
                sb.append(String.format(java.util.Locale.ROOT,
                        "{\"name\":\"%s\",\"uuid\":\"%s\",\"world\":\"%s\",\"x\":%.1f,\"y\":%.1f,\"z\":%.1f,\"ping\":%d,\"health\":%.1f,\"op\":%b}",
                        p.getName(), p.getUniqueId(), worldName, loc.getX(), loc.getY(), loc.getZ(), ping, p.getHealth(), p.isOp()));
            }
            sb.append("]}");

            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    private static class PlayerActionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 200, "{}");
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            if (!isAuthorized(exchange)) {
                sendJsonResponse(exchange, 401, "{\"error\":\"Unauthorized\"}");
                return;
            }

            String body = readRequestBody(exchange);
            // Parse simple json fields: target, action, reason
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

            Bukkit.getScheduler().runTask(new MinecraftInternalPlugin(), () -> {
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
                    }
                } else if (fAction.equalsIgnoreCase("ban")) {
                    try {
                        Bukkit.getBanList(BanList.Type.NAME).addBan(fTarget, fReason, null, "UniverseWebAdmin");
                    } catch (Throwable ignore) {
                    }
                    if (player != null && player.isOnline()) {
                        player.kick(Component.text("Baneado: " + fReason));
                    }
                } else if (fAction.equalsIgnoreCase("op")) {
                    if (player != null) {
                        player.setOp(!player.isOp());
                    }
                }
            });

            sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Action executed\"}");
        }
    }

    private static class CommandHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 200, "{}");
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            if (!isAuthorized(exchange)) {
                sendJsonResponse(exchange, 401, "{\"error\":\"Unauthorized\"}");
                return;
            }

            if (!UniversePurpurConfig.webPanelAllowCommands) {
                sendJsonResponse(exchange, 403, "{\"error\":\"Remote console execution is disabled in config\"}");
                return;
            }

            String body = readRequestBody(exchange);
            String command = extractJsonString(body, "command");
            if (command == null || command.trim().isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"error\":\"Empty command\"}");
                return;
            }

            final String cmd = command.startsWith("/") ? command.substring(1) : command;
            Bukkit.getScheduler().runTask(new MinecraftInternalPlugin(), () -> {
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                } catch (Throwable t) {
                    Bukkit.getLogger().log(Level.WARNING, "Error executing web console command: " + cmd, t);
                }
            });

            sendJsonResponse(exchange, 200, "{\"success\":true,\"dispatched\":\"" + cmd.replace("\"", "\\\"") + "\"}");
        }
    }

    private static class ActionsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 200, "{}");
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            if (!isAuthorized(exchange)) {
                sendJsonResponse(exchange, 401, "{\"error\":\"Unauthorized\"}");
                return;
            }

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
                Bukkit.getScheduler().runTask(new MinecraftInternalPlugin(), () -> {
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
    }

    private static class StaticGuiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] htmlBytes = DASHBOARD_HTML.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, htmlBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(htmlBytes);
            }
        }
    }

    private static String extractJsonString(String json, String key) {
        if (json == null) return null;
        String pattern = "\"" + key + "\":\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) {
            pattern = "\"" + key + "\": \"";
            idx = json.indexOf(pattern);
        }
        if (idx == -1) return null;
        int start = idx + pattern.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return null;
        return json.substring(start, end);
    }

    // --- Embedded HTML / CSS / JS Single Page Application ---
    private static final String DASHBOARD_HTML = """
<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>UniversePurpur | Web Admin Panel (+15,000 Players)</title>
  <style>
    :root {
      --bg: #090a10;
      --card-bg: #12131f;
      --card-border: #1e2038;
      --accent-purple: #9d4edd;
      --accent-cyan: #00f0ff;
      --accent-green: #00e676;
      --accent-yellow: #ffd600;
      --accent-red: #ff1744;
      --text-main: #f0f2f5;
      --text-muted: #8c90aa;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Segoe UI', Roboto, Helvetica, sans-serif; }
    body { background-color: var(--bg); color: var(--text-main); min-height: 100vh; display: flex; flex-direction: column; }
    header { background: #0e0f1a; border-bottom: 1px solid var(--card-border); padding: 15px 30px; display: flex; justify-content: space-between; align-items: center; }
    .logo-area { display: flex; align-items: center; gap: 15px; }
    .logo-box { width: 40px; height: 40px; background: linear-gradient(135deg, var(--accent-purple), var(--accent-cyan)); border-radius: 8px; display: flex; align-items: center; justify-content: center; font-weight: bold; font-size: 20px; color: #fff; box-shadow: 0 0 15px rgba(157,78,221,0.5); }
    .logo-title h1 { font-size: 20px; font-weight: 800; letter-spacing: 1px; background: linear-gradient(90deg, #fff, var(--accent-cyan)); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
    .logo-title p { font-size: 11px; color: var(--accent-purple); font-weight: 600; letter-spacing: 0.5px; }
    .auth-bar { display: flex; align-items: center; gap: 10px; }
    .auth-bar input { background: #161828; border: 1px solid var(--card-border); color: #fff; padding: 7px 12px; border-radius: 6px; font-size: 13px; outline: none; }
    .auth-bar button, .btn { background: linear-gradient(135deg, var(--accent-purple), #7b2cbf); border: none; color: #fff; padding: 7px 16px; border-radius: 6px; cursor: pointer; font-weight: 600; font-size: 13px; transition: all 0.2s; }
    .auth-bar button:hover, .btn:hover { filter: brightness(1.2); transform: translateY(-1px); }
    .main-layout { display: flex; flex: 1; }
    nav { width: 220px; background: #0c0d16; border-right: 1px solid var(--card-border); padding: 20px 10px; display: flex; flex-direction: column; gap: 8px; }
    .nav-btn { background: transparent; border: none; color: var(--text-muted); text-align: left; padding: 12px 16px; border-radius: 8px; cursor: pointer; font-size: 14px; font-weight: 600; display: flex; align-items: center; gap: 10px; transition: 0.2s; }
    .nav-btn.active, .nav-btn:hover { background: #18192a; color: var(--accent-cyan); }
    .content { flex: 1; padding: 25px 30px; overflow-y: auto; }
    .grid-stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 20px; margin-bottom: 25px; }
    .card { background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 12px; padding: 20px; box-shadow: 0 4px 20px rgba(0,0,0,0.3); }
    .card h3 { font-size: 12px; text-transform: uppercase; letter-spacing: 1px; color: var(--text-muted); margin-bottom: 8px; }
    .card .val { font-size: 26px; font-weight: 800; color: #fff; }
    .card .sub { font-size: 12px; color: var(--text-muted); margin-top: 4px; }
    .pill { display: inline-block; padding: 3px 8px; border-radius: 12px; font-size: 11px; font-weight: bold; }
    .pill-green { background: rgba(0,230,118,0.15); color: var(--accent-green); }
    .pill-yellow { background: rgba(255,214,0,0.15); color: var(--accent-yellow); }
    .pill-red { background: rgba(255,23,68,0.15); color: var(--accent-red); }
    .pill-cyan { background: rgba(0,240,255,0.15); color: var(--accent-cyan); }
    .ports-banner { background: linear-gradient(90deg, #181930, #111322); border: 1px solid #2b2e52; border-radius: 10px; padding: 12px 20px; display: flex; gap: 20px; align-items: center; margin-bottom: 25px; }
    .ports-banner span { font-size: 13px; font-weight: 600; color: var(--text-muted); }
    .ports-banner b { color: var(--accent-cyan); }
    .table-container { overflow-x: auto; margin-top: 15px; }
    table { width: 100%; border-collapse: collapse; text-align: left; }
    th { padding: 12px; font-size: 12px; text-transform: uppercase; color: var(--text-muted); border-bottom: 1px solid var(--card-border); }
    td { padding: 12px; font-size: 13px; border-bottom: 1px solid #161726; }
    .btn-action { padding: 4px 10px; font-size: 11px; border-radius: 4px; border: none; cursor: pointer; font-weight: bold; margin-right: 5px; }
    .btn-kick { background: #ff9100; color: #000; }
    .btn-ban { background: var(--accent-red); color: #fff; }
    .console-box { background: #08090f; border: 1px solid var(--card-border); border-radius: 8px; height: 380px; overflow-y: auto; padding: 15px; font-family: monospace; font-size: 13px; color: #00e676; margin-bottom: 12px; }
    .console-input-row { display: flex; gap: 10px; }
    .console-input-row input { flex: 1; background: #131422; border: 1px solid var(--card-border); padding: 10px 14px; border-radius: 6px; color: #fff; font-size: 14px; outline: none; }
    .tab-content { display: none; }
    .tab-content.active { display: block; }
  </style>
</head>
<body>
  <header>
    <div class="logo-area">
      <div class="logo-box">UP</div>
      <div class="logo-title">
        <h1>UniversePurpur Admin Panel</h1>
        <p>HYPER-SCALE ENGINE (+15,000 PLAYERS) BY DEVPAPO</p>
      </div>
    </div>
    <div class="auth-bar">
      <input type="password" id="authTokenInput" placeholder="Auth Token">
      <button onclick="saveToken()">Conectar</button>
    </div>
  </header>

  <div class="main-layout">
    <nav>
      <button class="nav-btn active" onclick="switchTab('dashboard')">📊 Dashboard</button>
      <button class="nav-btn" onclick="switchTab('players')">👥 Jugadores Online</button>
      <button class="nav-btn" onclick="switchTab('console')">💻 Consola en Vivo</button>
      <button class="nav-btn" onclick="switchTab('tuning')">⚙️ Controles 15k</button>
    </nav>

    <main class="content">
      <div class="ports-banner">
        <span>PUERTOS ACTIVOS:</span>
        <span>Minecraft Java: <b id="portJava">25565</b></span>
        <span>Web Admin: <b id="portWeb">25566</b></span>
        <span>Geyser Bedrock: <b id="portBedrock">19132</b> (Opcional)</span>
      </div>

      <!-- TAB: DASHBOARD -->
      <section id="tab-dashboard" class="tab-content active">
        <div class="grid-stats">
          <div class="card">
            <h3>TPS / MSPT</h3>
            <div class="val" id="tpsVal">20.00</div>
            <div class="sub"><span id="msptVal">0.00 ms</span> | <span class="pill pill-green" id="throttlePill">ÓPTIMO</span></div>
          </div>
          <div class="card">
            <h3>Jugadores Online</h3>
            <div class="val" id="playersVal">0 / 15,000</div>
            <div class="sub">Culling Activo (32/cliente)</div>
          </div>
          <div class="card">
            <h3>Memoria Heap</h3>
            <div class="val" id="ramVal">0 MB</div>
            <div class="sub"><span id="ramPercent">0%</span> de asignación total</div>
          </div>
          <div class="card">
            <h3>Shield Anti-Crash</h3>
            <div class="val" id="shieldVal">0</div>
            <div class="sub">Exploits & Paquetes NaN Bloqueados</div>
          </div>
        </div>

        <div class="card" style="margin-bottom: 20px;">
          <h3>Telemetría del Motor UniversePurpur</h3>
          <p style="font-size: 13px; color: var(--text-muted); margin-top: 5px;">
            Perfil Activo: <b style="color:var(--accent-cyan);" id="profileText">15k-hyper-scale</b> | 
            Particionado Espacial 2D: <b style="color:var(--accent-green);"><span id="spatialBuckets">0</span> Cubos 32x32</b> | 
            Pausa de GC Acumulada: <b style="color:#fff;"><span id="gcPauseVal">0</span> ms</b>
          </p>
        </div>
      </section>

      <!-- TAB: PLAYERS -->
      <section id="tab-players" class="tab-content">
        <div class="card">
          <div style="display:flex; justify-content:space-between; align-items:center;">
            <h3>Jugadores Conectados en Tiempo Real</h3>
            <button class="btn" onclick="fetchPlayers()">🔄 Refrescar</button>
          </div>
          <div class="table-container">
            <table>
              <thead>
                <tr>
                  <th>Jugador</th>
                  <th>Mundo</th>
                  <th>Coordenadas</th>
                  <th>Ping</th>
                  <th>Salud</th>
                  <th>Rango</th>
                  <th>Acciones</th>
                </tr>
              </thead>
              <tbody id="playersTableBody">
                <tr><td colspan="7" style="color:var(--text-muted); text-align:center;">Cargando jugadores...</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <!-- TAB: CONSOLE -->
      <section id="tab-console" class="tab-content">
        <div class="card">
          <h3>Terminal de Consola del Servidor</h3>
          <div class="console-box" id="consoleOutput">
            [UniversePurpur] Conexión establecida con la consola del servidor.<br>
            [UniversePurpur] Escribe cualquier comando abajo (ejemplo: say Hola Servidor, tps, kick, etc.)
          </div>
          <div class="console-input-row">
            <input type="text" id="cmdInput" placeholder="Escribe un comando sin '/'..." onkeydown="if(event.key==='Enter') sendConsoleCommand()">
            <button class="btn" onclick="sendConsoleCommand()">Ejecutar</button>
          </div>
        </div>
      </section>

      <!-- TAB: TUNING -->
      <section id="tab-tuning" class="tab-content">
        <div class="card" style="margin-bottom: 20px;">
          <h3>Cambio Rápido de Perfil 15,000 Jugadores</h3>
          <div style="display:flex; gap:10px; margin-top: 15px;">
            <button class="btn" onclick="setProfile('15k-hyper-hub')">🏛️ 15k-hyper-hub (Lobbies)</button>
            <button class="btn" onclick="setProfile('15k-mega-arena')">⚔️ 15k-mega-arena (Minijuegos)</button>
            <button class="btn" onclick="setProfile('15k-scale-smp')">🌲 15k-scale-smp (Survival/SMP)</button>
          </div>
        </div>

        <div class="card">
          <h3>Operaciones Proactivas de Descongestión</h3>
          <div style="display:flex; gap:10px; margin-top: 15px; flex-wrap: wrap;">
            <button class="btn" style="background:#00b4d8;" onclick="triggerTrim()">🧹 Poda Proactiva de Chunks & GC</button>
            <button class="btn" style="background:#e63946;" onclick="triggerPurge('items')">🗑️ Purgar Ítems en el Suelo</button>
            <button class="btn" style="background:#f77f00;" onclick="triggerPurge('monsters')">🧟 Purgar Monstruos</button>
            <button class="btn" style="background:#7209b7;" onclick="setLagProtection('auto')">🛡️ Lag Protection: AUTO</button>
            <button class="btn" style="background:#d90429;" onclick="setLagProtection('on')">⚠️ Forzar Lockdown (ON)</button>
          </div>
        </div>
      </section>
    </main>
  </div>

  <script>
    let token = localStorage.getItem('up_auth_token') || '';
    document.getElementById('authTokenInput').value = token;

    function saveToken() {
      token = document.getElementById('authTokenInput').value.trim();
      localStorage.setItem('up_auth_token', token);
      alert('Token guardado.');
      refreshAll();
    }

    function switchTab(tabId) {
      document.querySelectorAll('.tab-content').forEach(el => el.classList.remove('active'));
      document.querySelectorAll('.nav-btn').forEach(el => el.classList.remove('active'));
      document.getElementById('tab-' + tabId).classList.add('active');
      event.target.classList.add('active');
      if (tabId === 'players') fetchPlayers();
    }

    async function apiRequest(path, method = 'GET', body = null) {
      const headers = { 'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json' };
      const opts = { method, headers };
      if (body) opts.body = JSON.stringify(body);
      try {
        const res = await fetch(path, opts);
        if (res.status === 401) {
          console.warn('Unauthorized');
          return null;
        }
        return await res.json();
      } catch (err) {
        console.error(err);
        return null;
      }
    }

    async function refreshStatus() {
      const data = await apiRequest('/api/status');
      if (!data) return;

      document.getElementById('tpsVal').innerText = data.tps.toFixed(2);
      document.getElementById('msptVal').innerText = data.mspt.toFixed(2) + ' ms';
      document.getElementById('playersVal').innerText = data.onlinePlayers + ' / ' + data.maxPlayers;
      document.getElementById('ramVal').innerText = data.usedMemoryMb + ' MB';
      document.getElementById('ramPercent').innerText = data.memoryUsagePercent.toFixed(1) + '%';
      document.getElementById('shieldVal').innerText = data.blockedExploits;
      document.getElementById('profileText').innerText = data.profile;
      document.getElementById('spatialBuckets').innerText = data.spatialBuckets;
      document.getElementById('gcPauseVal').innerText = data.gcPauseMs;

      if (data.ports) {
        document.getElementById('portJava').innerText = data.ports.minecraftJava;
        document.getElementById('portWeb').innerText = data.ports.webAdmin;
        document.getElementById('portBedrock').innerText = data.ports.geyserBedrock;
      }

      const pill = document.getElementById('throttlePill');
      pill.innerText = data.throttleName;
      pill.className = 'pill ' + (data.throttleLevel === 0 ? 'pill-green' : data.throttleLevel <= 2 ? 'pill-yellow' : 'pill-red');
    }

    async function fetchPlayers() {
      const data = await apiRequest('/api/players');
      const tbody = document.getElementById('playersTableBody');
      if (!data || !data.players || data.players.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" style="text-align:center; color:var(--text-muted);">No hay jugadores conectados actualmente.</td></tr>';
        return;
      }

      tbody.innerHTML = data.players.map(p => `
        <tr>
          <td><b>${p.name}</b></td>
          <td>${p.world}</td>
          <td>${p.x.toFixed(0)}, ${p.y.toFixed(0)}, ${p.z.toFixed(0)}</td>
          <td>${p.ping} ms</td>
          <td>❤️ ${p.health.toFixed(0)}</td>
          <td>${p.op ? '<span class="pill pill-cyan">OP</span>' : 'Player'}</td>
          <td>
            <button class="btn-action btn-kick" onclick="playerAction('${p.name}', 'kick')">Kick</button>
            <button class="btn-action btn-ban" onclick="playerAction('${p.name}', 'ban')">Ban</button>
          </td>
        </tr>
      `).join('');
    }

    async function playerAction(name, action) {
      const reason = prompt('Razón para ' + action + ' a ' + name + ':', 'Expulsado por Administrador');
      if (reason === null) return;
      await apiRequest('/api/player/action', 'POST', { target: name, action, reason });
      fetchPlayers();
      refreshStatus();
    }

    async function sendConsoleCommand() {
      const input = document.getElementById('cmdInput');
      const cmd = input.value.trim();
      if (!cmd) return;
      input.value = '';

      const log = document.getElementById('consoleOutput');
      log.innerHTML += `<br><span style="color:#00f0ff;">&gt; ${cmd}</span>`;
      log.scrollTop = log.scrollHeight;

      const res = await apiRequest('/api/command', 'POST', { command: cmd });
      if (res && res.success) {
        log.innerHTML += `<br><span style="color:#8c90aa;">[Consola] Comando '${res.dispatched}' despachado al servidor.</span>`;
      } else {
        log.innerHTML += `<br><span style="color:#ff1744;">[Error] No se pudo ejecutar el comando. Verifica permisos o token.</span>`;
      }
      log.scrollTop = log.scrollHeight;
      refreshStatus();
    }

    async function setProfile(name) {
      await apiRequest('/api/actions', 'POST', { action: 'profile', value: name });
      alert('Perfil cambiado a: ' + name);
      refreshStatus();
    }

    async function triggerTrim() {
      const res = await apiRequest('/api/actions', 'POST', { action: 'trim' });
      alert('Poda completada. Particiones tocadas: ' + (res ? res.freed : 0));
      refreshStatus();
    }

    async function triggerPurge(type) {
      await apiRequest('/api/actions', 'POST', { action: 'purge', value: type });
      alert('Purga de ' + type + ' completada.');
      refreshStatus();
    }

    async function setLagProtection(mode) {
      await apiRequest('/api/actions', 'POST', { action: 'lagprotection', value: mode });
      alert('Lag protection establecido en: ' + mode);
      refreshStatus();
    }

    function refreshAll() {
      refreshStatus();
    }

    setInterval(refreshStatus, 2000);
    refreshStatus();
  </script>
</body>
</html>
""";
}
