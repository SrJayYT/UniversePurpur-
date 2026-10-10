#!/usr/bin/env bash
# ===============================================================================
#  UniverseSpigot / UniversePurpur Production Launch Script (+10,000 Players)
#  Author: DevPapo
# ===============================================================================

MIN_RAM="16G"
MAX_RAM="32G"

# Locate executable JAR
JAR_FILE="universepurpur.jar"
if [ ! -f "$JAR_FILE" ]; then
    for f in *.jar; do
        if [ -f "$f" ]; then
            JAR_FILE="$f"
            break
        fi
    done
fi

if [ ! -f "$JAR_FILE" ]; then
    echo "[ERROR] No server JAR found in this directory! (Target: $JAR_FILE)"
    echo "Please compile or place the server JAR in this folder before launching."
    exit 1
fi

echo "==============================================================================="
echo " Starting UniverseSpigot / UniversePurpur (10k Extreme Scale Edition)"
echo " Author: DevPapo"
echo " Target JAR: $JAR_FILE"
echo " RAM Heap: ${MIN_RAM} - ${MAX_RAM} (Generational ZGC)"
echo "==============================================================================="

while true; do
    java -Xms${MIN_RAM} -Xmx${MAX_RAM} \
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
      -Dfile.encoding=UTF-8 \
      -jar "$JAR_FILE" --nogui

    echo "==============================================================================="
    echo " Server stopped. Restarting in 5 seconds... (Press Ctrl+C to stop)"
    echo "==============================================================================="
    sleep 5
done
