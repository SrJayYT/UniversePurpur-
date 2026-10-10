# TODO - UniversePurpur / UniverseSpigot (+15,000 Players Online Titan Hyper-Scale)

- [x] 1) Configurar repositorio Git local y resolver incompatibilidad de toolchain Java (Java 24 detectado localmente).
- [x] 2) Motor de configuración masiva en `UniversePurpurConfig.java`:
  - Dynamic MSPT Governor 5-Tier (target 38ms, hard limit 46ms, emergency lockdown).
  - Eliminación de cuello de botella O(N^2) en colisiones (`max-collisions-per-entity: 1`, `player-collisions-enabled: false`).
  - Culling dinámico de jugadores (`max-visible-players-per-client: 32` en mega-lobbies).
  - Optimización de IA/Mobs: DAB (Dynamic Activation of Brain), ticks inactivos a 1s (20 ticks), deshabilitación de ambient mobs.
  - Red Netty masiva (+15,000 conexiones concurrentes): `tcp-nodelay`, `flush-consolidation`, compresión asíncrona a 512 bytes, tablist y chat asíncronos.
  - Supresión de tormenta de paquetes y limitador adaptativo de tasa de paquetes.
  - Chunk I/O asíncrono y autosave distribuido (`max-chunks-per-tick: 6`, `dirty-save-only: true`).
- [x] 3) Conectar inicialización de `UniversePurpurConfig` en `PurpurConfig.java` y `/purpur reload`.
- [x] 4) Archivo de configuración `universe-purpur.yml` v4 con presets `15k-hyper-hub`, `15k-mega-arena` y `15k-scale-smp`.
- [x] 5) Guía completa de arquitectura de 5 capas en `UNIVERSE_SPIGOT_15K_HYPER_SCALE_GUIDE.md` (HAProxy, Velocity Cluster, Linux sysctl 131k sockets, Generational ZGC 48GB-64GB).
- [x] 6) Implementar `UniverseGovernorTask.java` con 5 niveles de mitigación dinámica de lag (Nivel 0 Óptimo a Nivel 4 Hyper-Scale Lockdown).
- [x] 7) Implementar `PacketRateLimiter.java` lock-free con Token Bucket adaptativo proporcional conectado al Governor.
- [x] 8) Implementar `/universespigot` (`UniverseSpigotCommand.java`) con Dashboard en vivo de 15k, métricas de Player Culling y cambio de perfiles en caliente.
- [x] 9) Crear scripts de arranque optimizados `start.bat` (Windows) y `start.sh` (Linux).
- [x] 10) Módulo `SpatialGrid.java`: particionado espacial 2D en cubos 32x32 para consultas O(1) de proximidad y culling de jugadores.
- [x] 11) Módulo `UniverseMemoryGovernor.java`: telemetría de saturación de Heap y pausas de GC con poda proactiva de cachés de chunks.
- [x] 12) Módulo `AntiCrashShield.java`: blindaje contra exploits Zero-Day (vectores NaN/Infinity, paquetes con NBT >16KB y slots inválidos).
- [x] 13) Módulo `UniverseBanner.java`: diagnóstico de telemetría ANSI en consola al arrancar el servidor.
- [x] 14) Subcomandos `/universespigot memory [trim]` y `/universespigot shield` integrados en el comando de diagnóstico.
- [x] 15) Corrección de ordenamiento por proximidad en `SpatialGrid.java`: resolución de culling errático garantizando que jugadores en radio inmediato nunca sean ocultados.
- [x] 16) Aislamiento multi-mundo en `SpatialGrid.java`: particionado independiente por UUID de mundo eliminando reseteos accidentales de grids entre Overworld, Nether y Arenas.
- [x] 17) Corrección de sobreescritura de ráfaga y deriva de micro-tokens en `PacketRateLimiter.java`: conservación estricta de `packetBurstLimit` y preservación del residuo temporal fraccional.
- [x] 18) Blindaje estricto de slots en `AntiCrashShield.java`: rechazo inmediato de slots fuera de `[0, maxSlots)` eliminando caídas del servidor por `IndexOutOfBoundsException`.
- [x] 19) Monotonía matemática en `UniverseGovernorTask.java`: umbrales escalonados entre target y hard-limit que previenen inversión o salto de niveles de mitigación.
- [x] 20) Persistencia de directorio raíz y recarga robusta en `UniversePurpurConfig.java` y `PurpurCommand.java`.
- [x] 21) Infraestructura de puertos duales (2 por defecto: 25565 Java + 25566 Web, 3 si Geyser: 19132) y Panel Administrador Web nativo (`UniverseWebPanel.java`) con gestión de jugadores (kick/ban/op), consola de comandos interactiva en vivo, telemetría y autenticación por token.
- [x] 22) Verificación y corrección de bugs en `UniverseWebPanel.java`: prevención de violaciones de AsyncCatcher mediante `CompletableFuture` seguro, métricas de chunks cacheadas periódicamente en el hilo principal, parser JSON con escape robusto de comillas, y manejo de rutas 404.
- [x] 23) Overhaul Integral de la Infraestructura Web: Portal Público (`/`), Vinculación de Cuentas de Discord (`/link` in-game + verificación web + API bot), Sistema de Usuarios y Autenticación con Roles (`SUPER_ADMIN`, `MODERATOR`), y Personalizador Dinámico de Marca (Logo, Icono, IP, Título, Discord) en vivo persistente.
- [x] 24) Auditoría de Seguridad & Resolución de Bugs: Blindaje contra Path Traversal en assets (`/assets/*`), despacho 100% en hilo principal de `trimCaches()` previniendo violaciones de AsyncCatcher, resolución de referencias cruzadas colgantes en `UniverseDiscordLinker`, aislamiento de ruta `SERVER_ROOT_DIR` para archivos JSON, eliminación de acumulación y fuga de memoria en `SpatialGrid.updateWorldEntities()`, protección contra timing attacks con `MessageDigest.isEqual()`, sanitización XSS en frontend web, botón OP en panel y subcomando in-game `/link unlink`.
- [x] 25) Egg Oficial de Pterodactyl Panel (`egg-universepurpur.json`): Manifiesto PTDL_v2 con soporte para Java 24 LTS (Generational ZGC), mapeo automático de puertos duales (Java 25565 + Web 25566, Geyser 19132), variables de perfil 15k, script de instalación automatizado con auto-aceptación de EULA y parser YAML/Properties dinámico.
- [x] 26) Ultra-Optimización de Consumo Mínimo de RAM y CPU: Arquitectura event-driven para telemetría de chunks en $O(1)$ (eliminación de escaneos de mundo con ChunkLoadEvent/ChunkUnloadEvent), preset 'ultra-low-ram-cpu' para nodos y VPS de bajo presupuesto, 'aggressive-memory-saver' con descarga proactiva de chunks sin jugadores (>64 bloques), purga automática de sesiones y códigos de vinculación expirados en el Heap, throttling extremo de DAB (40 ticks) y tolvas vacías (16 ticks), colisiones desactivadas, merging de ítems en radio 5.0, banderas JVM de deduplicación de cadenas (-XX:+UseStringDeduplication, -XX:+OptimizeStringConcat) e integración completa en start.bat, start.sh y Pterodactyl Egg.



