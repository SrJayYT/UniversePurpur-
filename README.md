<p align="center">
  <img src="assets/universepurpur_banner.png" alt="UniversePurpur Banner" width="100%">
</p>

<div align="center">

# UniversePurpur

### *The Ultimate Hyper-Scale Minecraft Server Software (+15,000 Concurrent Players)*

[![Release](https://img.shields.io/badge/Release-v1.0.3-brightgreen.svg)](https://github.com/SrJayYT/UniversePurpur-/releases/tag/v1.0.3)
[![License: MIT](https://img.shields.io/badge/License-MIT-purple.svg)](LICENSE)
[![Java 24](https://img.shields.io/badge/Java-24%20LTS-orange.svg)](https://www.oracle.com/java/)
[![Architecture](https://img.shields.io/badge/Target-+15,000%20Players-brightgreen.svg)]()
[![Platform](https://img.shields.io/badge/Fork-Purpur%20%7C%20Paperweight-blue.svg)](https://purpurmc.org)
[![Author](https://img.shields.io/badge/Author-DevPapo-blueviolet.svg)](https://github.com/SrJayYT)

**UniversePurpur** es una bifurcación de nivel empresarial de [Purpur](https://purpurmc.org) y [Paper](https://papermc.io), re-arquitecturada desde el núcleo para soportar mega-redes, lobbies masivos, minijuegos de alta concurrencia y clústeres globales con **más de 15,000 jugadores en línea simultáneos**.

[Características](#-características-principales) •
[Licenciamiento SCON](#-9-sistema-de-seguridad-y-licencias-scon-universelicensemanager) •
[Presets](#-presets-de-alto-rendimiento) •
[Comandos](#-comandos-administrativos-y-de-jugadores) •
[Inicio Rápido](#-inicio-rápido) •
[Compilación](#-compilación) •
[Licencia](#-licencia)

</div>

---

## ⚡ Características Principales

### 🧠 1. Gobernador Dinámico de MSPT de 5 Niveles
Un motor de mitigación autónomo que monitorea continuamente el MSPT y TPS en tiempo real, aplicando micro-regulaciones progresivas sin intervención de administradores:
- **Nivel 0 (Óptimo, MSPT < 30ms):** Fidelidad completa a 20.0 TPS.
- **Nivel 1 (Soft Throttle, MSPT 30-38ms):** Reducción de ticks cerebrales (DAB) en entidades lejanas.
- **Nivel 2 (Moderate Throttle, MSPT 38-42ms):** Congelamiento de ítems inactivos en el suelo y limitación de física de bloques.
- **Nivel 3 (Aggressive Culling, MSPT 42-46ms):** Restricción de rango de visión y ajuste dinámico de ráfagas de paquetes.
- **Nivel 4 (Hyper-Scale Lockdown, MSPT >= 46ms):** Supresión de paquetes cosméticos y priorización absoluta de hilos de red de jugadores.

### 🌐 2. Particionado Espacial 2D O(1) (`SpatialGrid`)
Reemplaza los escaneos lineales $O(N)$ del servidor con particiones de cuadrícula hash de $32 \times 32$ bloques:
- **Culling Inteligente por Proximidad:** Cada cliente renderiza prioritariamente los 32 jugadores más cercanos alrededor suyo, garantizando que nunca se oculten amigos a 1 bloque de distancia.
- **Aislamiento Multi-Mundo:** Grids independientes indexados por UUID de mundo, eliminando colisiones de coordenadas entre el Overworld, Nether y Arenas.

### 🛡️ 3. Blindaje Anti-Crasher y Protección Zero-Day (`AntiCrashShield`)
Defensa activa en la capa de red contra exploits y paquetes maliciosos:
- Filtrado estricto de vectores de movimiento `NaN` y coordenadas infinitas.
- Bloqueo de rotaciones `yaw/pitch` desbordadas.
- Rechazo de paquetes de canal con payloads NBT mayores a 16 KB.
- Validación de slots de contenedores para evitar caídas por `IndexOutOfBoundsException`.

### 🚦 4. Limitador de Paquetes Lock-Free (`PacketRateLimiter`)
Algoritmo de *Token Bucket* atómico basado en bucles CAS (`compareAndSet`):
- Cero contención de sincronización entre hilos de Netty.
- Recarga proporcional sin deriva ni truncamiento fraccional de micro-tokens.
- Escalado adaptativo automático bajo carga extrema del servidor.

### 🧹 5. Gobernador de Memoria y Auto-Garbage Healer (`UniverseMemoryGovernor`)
- Telemetría en tiempo real del Heap de la JVM y pausas de Garbage Collection.
- Poda preventiva de cachés de mundos y chunks inactivos antes de que ocurran picos de parada de mundo (Stop-The-World).
- Despacho seguro en el hilo principal cumpliendo con las restricciones de `AsyncCatcher` de Paper.

### 💻 6. Portal Web Público y Centro de Control Admin (`UniverseWebPanel`)
UniversePurpur incluye de forma nativa un servidor HTTP embebido de alto rendimiento (puerto predeterminado `25566`) con interfaz moderna de estética dark cosmic/cyberpunk:
- **🌐 Portal Web Público (`/`):**
  - Vista para jugadores con IP copiable al portapapeles en 1 clic.
  - Telemetría en vivo: Conexiones activas, TPS, MSPT y estado del clúster.
  - Formulario interactivo de **Vinculación de Cuentas de Discord** para jugadores.
  - Enlace directo a la comunidad de Discord configurada.
- **🛡️ Centro de Control Admin (`/admin`):**
  - **Login Seguro & Roles:** Autenticación con contraseñas encriptadas con hash SHA-256 + salt y tokens de sesión de 7 días (`universe-users.json`). Roles de `SUPER_ADMIN` y `MODERATOR`.
  - **Gestor Multi-Usuario:** Creación y revocación de cuentas administrativas directamente desde la interfaz web.
  - **Personalizador Dinámico de Marca:** Edición en caliente del título del servidor, descripción, IP pública, logo URL, favicon URL y enlace de Discord con guardado automático en `universe-purpur.yml`.
  - **Gestión Total de Jugadores:** Búsqueda en vivo de jugadores conectados, con acciones instantáneas de expulsar (**Kick**), suspender (**Ban**) o alternar permisos (**OP**) en 1 clic.
  - **Terminal de Consola Remota:** Envía y ejecuta cualquier comando de Minecraft (`/stop`, `/say`, `/give`, `/whitelist`, etc.) directamente desde el navegador.
  - **Mitigación y Telemetría en Vivo:** Monitoreo de TPS, MSPT, memoria RAM Heap, pausas de GC, chunks cacheados, cubos espaciales 2D y paquetes bloqueados por AntiCrash.
  - **Servidor de Assets Locales (`/assets/*`):** Sirve banners y logos localmente sin depender de CDNs externas.

### 🔗 7. Sistema de Vinculación de Cuentas Discord (`UniverseDiscordLinker`)
- **Comando In-Game:** Los jugadores generan un código temporal de 6 dígitos mediante `/link` o `/vincular`.
- **Verificación Web:** Ingresan su código en el portal web público (`/`) junto a su Discord Tag para completar el enlace.
- **Persistencia Segura:** Las vinculaciones se almacenan permanentemente en `universe-discord-links.json`.
- **API REST para Bots de Discord:**
  - `GET /api/discord/lookup?discordId=<id>`: Busca el jugador de Minecraft vinculado a una ID de Discord.
  - `GET /api/discord/lookup?player=<name>`: Busca los datos de Discord vinculados a un jugador.
### 🍃 8. Ultra-Optimización de Consumo Mínimo de RAM y CPU
Diseñado especialmente para maximizar la densidad de instancias y permitir correr UniversePurpur en servidores VPS económicos o nodos de bajos recursos sin perder estabilidad:
- **Telemetría Event-Driven de Chunks $O(1)$:** Elimina los bucles periódicos que instanciaban arrays de chunks en el hilo primario, rastreando el recuento atómicamente mediante eventos de Bukkit (`ChunkLoadEvent` / `ChunkUnloadEvent`) con cero coste de CPU.
- **Aggressive Memory Saver & Chunk Pruning:** Si el consumo de Heap supera el umbral configurado, el gobernador purga de forma proactiva chunks sin jugadores cercanos (a más de 64 bloques de distancia), liberando megabytes críticos de memoria.
- **Purga de Sesiones y Códigos en Heap:** Limpieza periódica de sesiones administrativas caducadas y códigos temporales de Discord no utilizados, evitando acumulación en mapas en memoria.
- **Dynamic Activation of Brain (DAB) Extremo:** Ticks de IA cerebral reducidos a frecuencias de hasta 40 ticks para entidades fuera del rango de activación.
- **Hopper Empty Throttling (16 ticks):** Tolvas vacías entran en modo de espera de 16 ticks, reduciendo radicalmente el uso de CPU generado por complejos sistemas de redstone.
- **Cero Colisiones y Fusión de Ítems:** Colisiones desactivadas ($O(1)$ por tick) y radio de fusión de ítems en suelo ampliado a 5.0 bloques, minimizando el conteo de entidades activas.

### 🔐 9. Sistema de Seguridad y Licencias SCON (`UniverseLicenseManager`)
UniversePurpur cuenta con integración nativa con la plataforma de seguridad **[SCON Licensing](https://licences.scon.host)**:
- **Verificación Temprana (Early Bootstrap):** Valida la autenticidad criptográfica mediante firmas HMAC-SHA256 antes de que el servidor inicialice mundos o plugins.
- **Heartbeat Silencioso en Segundo Plano:** Chequeo periódico cada 10 segundos totalmente silencioso en producción (activable en consola con `-Dlicense.debug=true` o `LICENSE_DEBUG=true`).
- **Defensa Activa ante Revocación o Expiración:** En caso de que la licencia expire o sea suspendida en el panel web, los jugadores conectados son expulsados de forma sincronizada en el hilo principal (`[!] SERVIDOR CERRADO POR SEGURIDAD`) y el servidor ejecuta un apagado limpio sin generar bucles de crash en Pterodactyl.
- **Configuración Ultra-Limpia:** Solo la clave es requerida en `universe-purpur.yml`:
  ```yaml
  license:
    key: "SCON-XXXX-XXXX-XXXX-XXXX"
  ```
  O directamente en la pestaña **Startup** de Pterodactyl mediante la variable `LICENSE_KEY`.

---

## 🔌 Arquitectura de Puertos del Servidor

UniversePurpur requiere **2 puertos por defecto** (y un 3er puerto opcional si se utiliza Geyser):

| Puerto | Protocolo | Servicio | Descripción |
| :---: | :---: | :--- | :--- |
| **`25565`** | **TCP** | **Minecraft Java Server** | Puerto principal de conexión para clientes Java de Minecraft. |
| **`25566`** | **TCP / HTTP** | **Web Portal & Admin Panel** | Portal público (`/`) y panel admin (`/admin`) en `http://<ip>:25566`. |
| **`19132`** | **UDP** | **Geyser Bedrock (Opcional)** | Puerto para compatibilidad crossplay con Minecraft Bedrock (móviles, consolas). |

## 🎯 Presets de Alto Rendimiento

En [`universe-purpur.yml`](universe-purpur.yml), UniversePurpur incluye perfiles preconfigurados listos para producción:

| Preset | Caso de Uso | Distancia Simulación | Max Jugadores Visibles | Colisiones |
| :--- | :--- | :---: | :---: | :---: |
| `ultra-low-ram-cpu` | Servidores VPS económicos y Budget Nodes (Consumo mínimo RAM/CPU) | **2 chunks** (view 4) | **16** | **0** (Desactivadas) |
| `15k-hyper-hub` | Lobbies principales y Spawns masivos | **2 chunks** (view 6) | **24** | **0** (Desactivadas) |
| `15k-mega-arena` | Bedwars, Skywars, Duels y Práctica | **3 chunks** | **48** | **1** (Máx 1 empuje) |
| `15k-scale-smp` | Supervivencia masiva y Skyblock | **3 chunks** | **32** | **1** (Máx 1 empuje) |

---

## 🛠️ Comandos Administrativos y de Jugadores

### Comandos del Servidor y Jugadores:
```text
/link (alias: /vincular, /discordlink)
  Genera un código de un solo uso (UP-XXXXXX) válido por 5 minutos para vincular tu cuenta con Discord en el portal web.
```

### Comandos de Diagnóstico y Control Maestro:
El comando maestro `/universespigot` (alias: `/uspigot`, `/universe`) proporciona control total:

```text
/universespigot status
  Muestra el Dashboard en vivo con TPS, MSPT, memoria RAM, nivel del gobernador y métricas de culling.

/universespigot profile <ultra-low-ram-cpu | 15k-hyper-hub | 15k-mega-arena | 15k-scale-smp>
  Cambia el perfil de rendimiento en caliente sin reiniciar el servidor.

/universespigot lagprotection <on | off | auto>
  Controla el sistema de emergencia de mitigación de lag.

/universespigot purge <items | ambient | monsters>
  Limpia entidades acumuladas instantáneamente en todos los mundos.

/universespigot memory [trim]
  Muestra estadísticas de Garbage Collection y ejecuta poda proactiva de cachés de memoria.

/universespigot shield
  Muestra el total de paquetes maliciosos, vectores NaN y exploits bloqueados.

/universespigot spatial
  Muestra el estado de la cuadrícula de particionado 2D y cubos activos por mundo.

/universespigot reload
  Recarga la configuración de universe-purpur.yml en memoria de forma segura.
```


---

## 🚀 Inicio Rápido

### Banderas Recomendadas de la JVM (Java 24 LTS con Generational ZGC)

UniversePurpur soporta tanto entornos de recursos mínimos (VPS de 1GB - 4GB) como clústeres masivos (+15,000 jugadores):

#### 🍃 Perfil Ultra-Bajo Consumo (VPS / Nodos Económicos de 2GB a 4GB RAM):
```bash
java -Xms1G -Xmx3G \
  -XX:+UseZGC -XX:+ZGenerational \
  -XX:+UseStringDeduplication \
  -XX:+OptimizeStringConcat \
  -XX:CICompilerCount=2 \
  -Dio.netty.recycler.maxCapacityPerThread=1024 \
  -jar universepurpur.jar --nogui
```

#### ⚡ Perfil Hyper-Scale (+15,000 Jugadores Masivos):
- **Windows (`start.bat`):**
```bat
java -Xms32G -Xmx48G ^
  -XX:+UseZGC -XX:+ZGenerational ^
  -XX:+AlwaysPreTouch ^
  -XX:+UseNUMA ^
  -XX:+UseStringDeduplication ^
  -XX:+OptimizeStringConcat ^
  -Dio.netty.allocator.type=pooled ^
  -jar universepurpur.jar --nogui
```
- **Linux (`start.sh`):**
```bash
java -Xms32G -Xmx48G \
  -XX:+UseZGC -XX:+ZGenerational \
  -XX:+AlwaysPreTouch \
  -XX:+UseNUMA \
  -XX:+UseStringDeduplication \
  -XX:+OptimizeStringConcat \
  -Dio.netty.allocator.type=pooled \
  -jar universepurpur.jar --nogui
```

### 🦅 Despliegue en Panel Pterodactyl / Pelican

UniversePurpur incluye un **Egg oficial de Pterodactyl** preconfigurado y listo para importar: [`egg-universepurpur.json`](egg-universepurpur.json).

#### Pasos para Importar e Instalar en Pterodactyl:
1. Accede al panel administrativo de Pterodactyl o Pelican y ve a **Nests**.
2. Selecciona tu categoría (por ejemplo *Minecraft*) y haz clic en el botón **Import Egg**.
3. Sube el archivo [`egg-universepurpur.json`](egg-universepurpur.json) desde la raíz de este repositorio.
4. Asigna las variables de entorno deseadas:
   - `LICENSE_KEY`: Tu clave de activación de SCON Licensing (`SCON-XXXX-XXXX-XXXX-XXXX`).
   - `SERVER_JARFILE`: `universepurpur.jar`
   - `WEB_PORT`: `25566` (Recuerda añadir una asignación o *allocation* adicional a tu servidor en Pterodactyl).
   - `WEB_AUTH_TOKEN`: `admin15k`
   - `UNIVERSE_PROFILE`: `15k-hyper-hub` (o el preset de tu preferencia).
   - `JVM_FLAGS`: Flags ZGC Generacional con latencia ultrabaja.
5. Selecciona la imagen Docker oficial `ghcr.io/pterodactyl/yolks:java_24` (o `ghcr.io/pterodactyl/yolks:java_21`).
6. ¡Inicia el servidor! UniversePurpur levantará automáticamente el servidor Java en el puerto primario y el Panel Web en el puerto secundario.


---

## 🔨 Compilación

Para compilar UniversePurpur desde el código fuente se requiere **JDK 24** y Git:

```bash
# 1. Clonar el repositorio
git clone https://github.com/SrJayYT/UniversePurpur-.git
cd UniversePurpur-

# 2. Aplicar parches de Paper y Purpur
./gradlew applyAllPatches

# 3. Compilar el JAR ejecutable del servidor
./gradlew createMojmapBundlerJar
```

El archivo JAR generado se encontrará en `purpur-server/build/libs/`.

---

## 📄 Licencia

UniversePurpur está bajo la licencia **MIT License**. Consulta el archivo [LICENSE](LICENSE) para más detalles.

Agradecimientos especiales a los proyectos upstream:
- [PurpurMC/Purpur](https://github.com/PurpurMC/Purpur)
- [PaperMC/Paper](https://github.com/PaperMC/Paper)
- [PaperMC/paperweight](https://github.com/PaperMC/paperweight)

---

<p align="center">
  <b>Desarrollado y optimizado con ❤️ por DevPapo para redes de Minecraft masivas.</b>
</p>
