@echo off
title UniverseSpigot 10k Server [Author: DevPapo]
color 0b

echo ===============================================================================
echo  Starting UniverseSpigot / UniversePurpur (Extreme Scale +10k Players Edition)
echo  Author: DevPapo
echo  Engine: Dynamic MSPT Governor ^| Generational ZGC ^| Netty Packet Throttle
echo ===============================================================================

:: Java Heap Settings (Adjust -Xms / -Xmx based on your available host RAM)
set MIN_RAM=16G
set MAX_RAM=32G

:: Jar File Name
set JAR_FILE=universepurpur.jar
if not exist "%JAR_FILE%" (
    for %%f in (*.jar) do (
        set JAR_FILE=%%f
        goto found_jar
    )
)
:found_jar

echo Target JAR: %JAR_FILE%
echo Initializing JVM with Generational ZGC (Low-Latency Ultra-Throughput)...
echo.

:run_loop
java -Xms%MIN_RAM% -Xmx%MAX_RAM% ^
  -XX:+UseZGC ^
  -XX:+ZGenerational ^
  -XX:+AlwaysPreTouch ^
  -XX:+UseNUMA ^
  -XX:+UseStringDeduplication ^
  -XX:+OptimizeStringConcat ^
  -Dusing.aikars.flags=https://mcflags.emc.gs ^
  -Daikars.new.flags=true ^
  -Dio.netty.leakDetectionLevel=DISABLED ^
  -Dio.netty.recycler.maxCapacityPerThread=4096 ^
  -Dfile.encoding=UTF-8 ^
  -jar "%JAR_FILE%" --nogui

echo.
echo ===============================================================================
echo  Server stopped. Press Ctrl+C to terminate or wait 5 seconds to auto-restart.
echo ===============================================================================
timeout /t 5
goto run_loop
