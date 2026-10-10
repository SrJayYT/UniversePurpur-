# TODO - UniversePurpur / UniverseSpigot (+15,000 Players Online Hyper-Scale Optimization)

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
