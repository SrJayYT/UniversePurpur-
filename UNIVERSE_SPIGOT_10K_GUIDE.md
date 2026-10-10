# Guía de Optimización UniverseSpigot / UniversePurpur (+10,000 Players Online)
**Autor:** DevPapo  
**Objetivo:** Arquitectura e infraestructura para soportar más de 10,000 jugadores simultáneos en red/servidores Minecraft Java.

---

## 1. Principales Cuellos de Botella en Servidores con +10,000 Jugadores y Cómo se Solucionan

| Cuello de Botella | Comportamiento Vanilla/Estándar | Solución en UniverseSpigot / UniversePurpur |
|---|---|---|
| **Colisiones de Entidades** | Cálculo cuadrático $O(N^2)$ al agruparse jugadores | `max-collisions-per-entity: 1` y `player-collisions-enabled: false`. Reduce la complejidad de $O(N^2)$ a $O(1)$. |
| **Tormenta de Paquetes (Packet Storm)** | Si 500 jugadores se mueven cerca, se emiten $500 \times 500 = 250,000$ paquetes por tick | `max-tracked-entities-per-player: 48` y tracking ranges compactos (32 bloques). Corta en un 90% el tráfico de red. |
| **IA y Pathfinding de Mobs** | Cada mob calcula rutas e intenciones cada tick (50ms) | **DAB (Dynamic Activation of Brain)**: Mobs a más de 12 bloques solo procesan cerebro cada 20 ticks (1 vez/seg). Ambient mobs deshabilitados. |
| **Picos de Autosave (Lag Spikes)** | Guarda cientos de chunks de golpe bloqueando disco e I/O | Autosave distribuido suave: `max-chunks-per-tick: 6` y `dirtySaveOnly: true`. El guardado no produce caídas de TPS. |
| **Throughput de Red Netty** | Sobrecarga de syscalls y compresión innecesaria de paquetes pequeños | `tcp-nodelay: true`, `flush-consolidation: true`, compresión asíncrona con umbral elevado a 512 bytes y rate limiter anti-crasher. |
| **Picos de Caída de TPS** | El servidor se congela cuando el MSPT supera 50ms | **Dynamic MSPT Governor**: Detecta cuando el MSPT pasa de 40ms y frena tareas secundarias antes de que baje de 20 TPS. |

---

## 2. Presets Incluidos en `universe-purpur.yml`

* **`10k-hub`**: Ideal para lobbies masivos y salas de espera centrales (2,000 - 10,000 jugadores por lobby o pool de lobbies).
  - Ticks aleatorios: 0
  - Distancia de simulación: 2
  - Cero mobs, colisiones desactivadas.
* **`10k-minigames`**: Para modalidades competitivas intensas (Bedwars, Skywars, Duels, Practice, Arenas).
  - Simulación: 3, tracking optimizado, colisiones limitadas a 1.
* **`10k-survival`**: Para mundos de supervivencia / SMP masivos.
  - Spawning activo pero controlado con DAB, colisiones a 1, autosave asíncrono y disperso.

---

## 3. Flags de JVM Recomendados para +10,000 Conexiones (Java 21 / 24)

Para servidores con grandes volúmenes de jugadores y memoria RAM (32GB - 64GB), se recomienda **Generational ZGC** para evitar pausas "Stop The World" de Garbage Collector:

```bash
java -Xms32G -Xmx32G \
  -XX:+UseZGC \
  -XX:+ZGenerational \
  -XX:+AlwaysPreTouch \
  -XX:+UseNUMA \
  -XX:+UseStringDeduplication \
  -XX:+OptimizeStringConcat \
  -Dusing.aikars.flags=https://mcflags.emc.gs \
  -Daikars.new.flags=true \
  -Dio.netty.leakDetectionLevel=DISABLED \
  -Dio.netty.recycler.maxCapacityPerThread=4096 \
  -jar universepurpur.jar --nogui
```

---

## 4. Ajustes del Sistema Operativo (Linux / Red) para +10k Sockets Concurrentes

En `/etc/sysctl.conf`:
```ini
# Aumentar límite de sockets y conexiones pendientes
net.core.somaxconn = 65535
net.ipv4.tcp_max_syn_backlog = 65535

# Reutilización rápida de puertos TIME_WAIT
net.ipv4.tcp_tw_reuse = 1
net.ipv4.tcp_fin_timeout = 15

# Buffer de red
net.core.rmem_max = 16777216
net.core.wmem_max = 16777216

# Descriptores de archivo por proceso (en /etc/security/limits.conf)
# * soft nofile 1000000
# * hard nofile 1000000
```

---

## 5. Panel de Control y Comandos en Vivo (`/universespigot` | `/uspigot` | `/universe`)

UniverseSpigot incluye un comando de administración y diagnóstico en tiempo real con MiniMessage y telemetría:

* `/universespigot status`: Abre el Dashboard en vivo mostrando:
  - TPS real y MSPT con código de colores (Verde <35ms, Amarillo 35-45ms, Rojo >45ms).
  - Estado del Dynamic Governor (Level 0 a Level 3 Emergency).
  - Perfil activo actual, jugadores en línea y chunks cargados.
  - Consumo de memoria RAM (Usada / Asignada / Máxima).
  - Estado del Netty Packet Rate Limiter y límites de colisiones.

* `/universespigot reload`: Recarga en caliente [`universe-purpur.yml`](file:///C:/Users/Jacob/Desktop/DevPapo/SoftwareMC/UniversePurpur/universe-purpur.yml) sin reiniciar.
* `/universespigot profile <10k-hub | 10k-minigames | 10k-survival>`: Cambia el perfil de rendimiento global en caliente.
* `/universespigot lagprotection <on | off | auto>`: Fuerza o automatiza la protección de lag de emergencia.
* `/universespigot purge <items | ambient>`: Limpia instantáneamente ítems en el suelo o entidades ambientales para aliviar picos de lag en eventos masivos.

También se puede recargar todo mediante `/purpur reload`.

---

## 6. Scripts de Arranque Automatizados

Se incluyen scripts de ejecución con Generational ZGC y optimizaciones de red:
* **Windows:** [`start.bat`](file:///C:/Users/Jacob/Desktop/DevPapo/SoftwareMC/UniversePurpur/start.bat) (con reinicio automático tras 5 segundos).
* **Linux:** [`start.sh`](file:///C:/Users/Jacob/Desktop/DevPapo/SoftwareMC/UniversePurpur/start.sh) (ideal para contenedores o servicios systemd).

