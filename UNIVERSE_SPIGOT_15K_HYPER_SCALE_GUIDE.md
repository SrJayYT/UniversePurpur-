# Guía de Arquitectura e Infraestructura UniverseSpigot (+15,000 Players Online)
**Autor:** DevPapo  
**Objetivo:** Arquitectura Hyper-Scale para redes y servidores de Minecraft Java con más de 15,000 jugadores simultáneos.

---

## 1. Topología de Red para +15,000 Conexiones Concurrentes

Para sostener más de 15,000 jugadores en Minecraft Java con estabilidad absoluta (20.0 TPS y latencia < 30ms), se requiere una arquitectura desacoplada en 5 capas:

```
[ +15,000 Clientes Minecraft ]
             │
             ▼
┌──────────────────────────────────────────────┐
│ Capa 1: Borde Anycast & DDoS (TCP Syncookies) │
└──────────────────────┬───────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────┐
│ Capa 2: HAProxy Layer 4 (Proxy Protocol v2)  │
│  - Balanceo TCP round-robin / leastconn      │
└──────────────────────┬───────────────────────┘
                       │
         ┌─────────────┴─────────────┐
         ▼                           ▼
┌─────────────────┐         ┌─────────────────┐
│ Velocity Node 1 │   ...   │ Velocity Node 4 │  (Capa 3: Proxies Velocity)
│ (~4,000 players)│         │ (~4,000 players)│  (Netty epoll nativo)
└────────┬────────┘         └────────┬────────┘
         │                           │
         └─────────────┬─────────────┘
                       ▼
┌──────────────────────────────────────────────┐
│ Capa 4: Nodos Backend UniverseSpigot 15k     │  (Capa 4: Servidores de Juego)
│  - Presets: 15k-hyper-hub, 15k-mega-arena... │
│  - 5-Tier Dynamic MSPT Governor              │
│  - Culling de Jugadores (32 visibles/cliente)│
└──────────────────────┬───────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────┐
│ Capa 5: Redis / DragonFly Cache Cluster      │  (Capa 5: Datos en Memoria)
│  - Sincronización instantánea de red y chat │
└──────────────────────────────────────────────┘
```

---

## 2. Los 6 Pilares Críticos para Escala de +15,000 Jugadores

| Pilar de Optimización | Implementación en UniverseSpigot | Impacto Técnico |
|---|---|---|
| **Player Culling Dinámico** | `max-visible-players-per-client: 32` y `range-player: 28` | **El arma secreta de Hypixel:** En un lobby con 2,000 jugadores, evita enviar $2,000 \times 2,000 = 4,000,000$ paquetes por tick. Cada cliente solo renderiza los 32 jugadores más cercanos. |
| **5-Tier MSPT Governor** | Nivel 0 (Óptimo) al Nivel 4 (Lockdown de Emergencia) | Monitorea el tiempo por tick cada segundo y degrada tareas secundarias gradualmente para blindar el TPS a 20.0. |
| **Colisiones de Entidades** | `max-collisions-per-entity: 1` y `player-collisions: false` | Transforma la complejidad del cálculo de colisiones de $O(N^2)$ a $O(1)$. |
| **Adaptive Netty Rate Limiting** | Token Bucket continuo y adaptativo en `PacketRateLimiter` | Si el servidor entra en carga alta (Nivel 3/4), reduce automáticamente la ventana de ráfaga para proteger los canales TCP. |
| **Autosave Escalonado Asíncrono** | `max-chunks-per-tick: 6` y `dirty-save-only: true` | Elimina las caídas de FPS causadas por ráfagas de escritura masiva en disco al guardar miles de chunks. |
| **TabList y Chat Asíncronos** | `async-tablist-updates: true` y `chat-async-dispatch: true` | El formateo y despacho de miles de mensajes de chat y entradas de tablist se delegan a hilos de trabajo independientes. |

---

## 3. Presets Hyper-Scale en `universe-purpur.yml`

* **`15k-hyper-hub`**: Lobbies de bienvenida masivos (3,000 - 15,000 jugadores en red).
  - Ticks aleatorios: 0 | Simulación: 2 | Culling: 24 jugadores por cliente | Colisiones: 0.
* **`15k-mega-arena`**: Clústeres de minijuegos intensivos (Bedwars, Skywars, Duels).
  - Ticks de bloques: 6,000 | Simulación: 3 | Colisiones: 1 | Culling: 48 jugadores.
* **`15k-scale-smp`**: Modalidades de supervivencia y Skyblock masivas.
  - Spawning con DAB activado (cerebro a 1 tick cada 20 fuera de 12 bloques) | Simulación: 3.

---

## 4. Parámetros de Kernel de Linux (`/etc/sysctl.conf`) para +15,000 Conexiones

```ini
# Límite máximo de sockets TCP en cola de aceptación
net.core.somaxconn = 131072
net.ipv4.tcp_max_syn_backlog = 131072

# Reutilización agresiva de sockets en estado TIME_WAIT
net.ipv4.tcp_tw_reuse = 1
net.ipv4.tcp_fin_timeout = 10

# Ventana de buffers de red TCP (envío y recepción optimizados a 32MB)
net.core.rmem_max = 33554432
net.core.wmem_max = 33554432
net.ipv4.tcp_rmem = 4096 87380 33554432
net.ipv4.tcp_wmem = 4096 65536 33554432

# Descriptores de archivos máximos para el sistema (en /etc/security/limits.conf)
# * soft nofile 2000000
# * hard nofile 2000000
```

---

## 5. Flags de JVM Generational ZGC para Máquinas de 48GB - 64GB RAM

```bash
java -Xms48G -Xmx48G \
  -XX:+UseZGC \
  -XX:+ZGenerational \
  -XX:+AlwaysPreTouch \
  -XX:+UseNUMA \
  -XX:+UseStringDeduplication \
  -XX:+OptimizeStringConcat \
  -Dusing.aikars.flags=https://mcflags.emc.gs \
  -Daikars.new.flags=true \
  -Dio.netty.allocator.type=pooled \
  -Dio.netty.allocator.pageSize=8192 \
  -Dio.netty.recycler.maxCapacityPerThread=8192 \
  -Dio.netty.leakDetectionLevel=DISABLED \
  -Dfile.encoding=UTF-8 \
  -jar universepurpur.jar --nogui
```

---

## 6. Panel de Diagnóstico en Tiempo Real

Ejecuta en consola o en juego (con permiso `bukkit.command.universespigot`):
```
/universespigot status
```
Muestra en vivo: TPS, MSPT, Nivel del Governor (Level 0 al 4), límite de Player Culling, memoria física/asignada y estado del Rate Limiter Netty.
