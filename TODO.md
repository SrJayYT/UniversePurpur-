# TODO - UniversePurpur / UniverseSpigot (+10,000 Players Online Optimization)

- [x] 1) Configurar repositorio Git local y resolver incompatibilidad de toolchain Java (Java 24 detectado localmente).
- [x] 2) Crear e integrar el motor de configuración de rendimiento masivo en `UniversePurpurConfig.java`:
  - Dynamic MSPT Governor (target 40ms, hard limit 48ms, emergency lag protection).
  - Eliminación de cuello de botella O(N^2) en colisiones (`max-collisions-per-entity: 1`, `player-collisions-enabled: false`).
  - Optimización de IA/Mobs: DAB (Dynamic Activation of Brain), ticks inactivos a 1s (20 ticks), deshabilitación de ambient mobs.
  - Red Netty masiva (+10,000 conexiones concurrentes): `tcp-nodelay`, `flush-consolidation`, umbral de compresión optimizado a 512 bytes, anti-flood packet rate limiter (1200 pps, burst 250).
  - Supresión de tormenta de paquetes (Packet Storm Suppression): límite de entidades visibles por tick por jugador (`max-tracked-entities-per-player: 48`) y rangos de tracking ajustados.
  - Chunk I/O asíncrono y autosave incremental distribuido (`max-chunks-per-tick: 6`, `dirty-save-only: true`).
- [x] 3) Conectar la inicialización de `UniversePurpurConfig` dentro del ciclo de inicio de `PurpurConfig.java` y en `/purpur reload`.
- [x] 4) Crear archivo de configuración exhaustivo y documentado `universe-purpur.yml` con presets `10k-hub`, `10k-minigames` y `10k-survival`.
- [x] 5) Documentar recomendaciones completas de despliegue, flags JVM (Generational ZGC) y proxy para +10,000 jugadores.
- [x] 6) Implementar `UniverseGovernorTask.java` con telemetría en tiempo real de MSPT/TPS y 4 niveles de mitigación dinámica de lag (Nivel 0 Óptimo a Nivel 3 Emergencia).
- [x] 7) Implementar `PacketRateLimiter.java` atómico y lock-free con algoritmo token bucket para supresión de flooding en el pipeline Netty.
- [x] 8) Implementar `/universespigot` (`UniverseSpigotCommand.java`) con Dashboard en vivo, cambio de perfiles en caliente, lagprotection y purga de entidades.
- [x] 9) Crear scripts de arranque optimizados listos para producción: `start.bat` (Windows) y `start.sh` (Linux).
