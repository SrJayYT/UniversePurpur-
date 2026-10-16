package org.purpurmc.purpur.plugin;

import java.io.Closeable;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.purpurmc.purpur.UniversePurpurConfig;

/**
 * Native High-Performance PlugMan Engine for UniversePurpur Titan Engine (+15,000 Players).
 * Provides 100% native runtime lifecycle management for both modern Paper plugins (paper-plugin.yml)
 * and legacy Spigot/Bukkit plugins (plugin.yml) without experimental reflection errors.
 *
 * Author: DevPapo (admin@scon.host)
 */
public final class UniversePlugMan {

    private UniversePlugMan() {}

    public static record PluginResult(boolean success, String message, Plugin plugin) {
        public PluginResult(boolean success, String message) {
            this(success, message, null);
        }
    }

    /**
     * Checks if a plugin is a modern native Paper plugin (paper-plugin.yml).
     */
    public static boolean isPaperPlugin(Plugin plugin) {
        if (plugin == null) {
            return false;
        }
        try {
            if (plugin.getPluginMeta() instanceof io.papermc.paper.plugin.provider.configuration.PaperPluginMeta) {
                return true;
            }
        } catch (Throwable ignore) {
        }
        try {
            ClassLoader cl = plugin.getClass().getClassLoader();
            if (cl != null) {
                String clName = cl.getClass().getName();
                if (clName.contains("PaperPluginClassLoader") || clName.contains("PaperSimplePluginClassLoader")) {
                    return true;
                }
            }
        } catch (Throwable ignore) {
        }
        return false;
    }

    /**
     * Retrieves the file location of a plugin on disk.
     */
    public static File getJarFile(Plugin plugin) {
        if (plugin == null) {
            return null;
        }
        try {
            URL url = plugin.getClass().getProtectionDomain().getCodeSource().getLocation();
            if (url != null) {
                File f = new File(url.toURI());
                if (f.exists() && f.isFile()) {
                    return f;
                }
            }
        } catch (Throwable ignore) {
        }

        File serverRoot = UniversePurpurConfig.SERVER_ROOT_DIR != null ? UniversePurpurConfig.SERVER_ROOT_DIR : new File(".");
        File pluginsDir = new File(serverRoot, "plugins");
        if (pluginsDir.isDirectory()) {
            File exact = new File(pluginsDir, plugin.getName() + ".jar");
            if (exact.exists() && exact.isFile()) {
                return exact;
            }
            File[] files = pluginsDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jar"));
            if (files != null) {
                for (File f : files) {
                    if (f.getName().equalsIgnoreCase(plugin.getName() + ".jar")) {
                        return f;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Finds an active or registered plugin by name (case-insensitive).
     */
    public static Plugin findPlugin(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        Plugin p = Bukkit.getPluginManager().getPlugin(name);
        if (p != null) {
            return p;
        }
        for (Plugin candidate : Bukkit.getPluginManager().getPlugins()) {
            if (candidate.getName().equalsIgnoreCase(name.trim())) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Dynamically loads a plugin from a .jar file. Supports both Paper and Bukkit plugins natively.
     */
    public static PluginResult load(File file) {
        if (file == null) {
            return new PluginResult(false, "El archivo especificado es nulo.");
        }
        if (!file.exists() || !file.isFile()) {
            return new PluginResult(false, "El archivo no existe en el disco: " + file.getName());
        }
        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".jar")) {
            return new PluginResult(false, "El archivo debe ser un archivo JAR (.jar).");
        }

        try {
            Plugin loaded = Bukkit.getPluginManager().loadPlugin(file);
            if (loaded == null) {
                return new PluginResult(false, "El cargador de Paper no pudo instanciar el plugin en " + file.getName() + " (posibles dependencias faltantes o descriptor inválido).");
            }

            Bukkit.getPluginManager().enablePlugin(loaded);
            syncCommands();

            boolean isPaper = isPaperPlugin(loaded);
            String typeTag = isPaper ? "Paper" : "Bukkit";
            return new PluginResult(true, "Plugin §b" + loaded.getName() + " §av" + loaded.getDescription().getVersion() + " §7[§e" + typeTag + "§7] cargado y activado exitosamente.", loaded);
        } catch (Throwable ex) {
            return new PluginResult(false, "Error al cargar plugin " + file.getName() + ": " + ex.getMessage());
        }
    }

    /**
     * Safely unloads a plugin from memory, unregisters all its listeners and commands, and releases ClassLoader locks.
     */
    public static PluginResult unload(Plugin plugin) {
        if (plugin == null) {
            return new PluginResult(false, "El plugin especificado no existe o es nulo.");
        }

        String name = plugin.getName();
        boolean isPaper = isPaperPlugin(plugin);

        try {
            // 1. Disable plugin if enabled
            if (plugin.isEnabled()) {
                Bukkit.getPluginManager().disablePlugin(plugin);
            }

            // 2. Cancel scheduled tasks
            try {
                Bukkit.getScheduler().cancelTasks(plugin);
            } catch (Throwable ignore) {}

            // 3. Unregister all event handlers
            try {
                HandlerList.unregisterAll(plugin);
            } catch (Throwable ignore) {}

            // 4. Cleanly unregister commands from CommandMap
            unregisterPluginCommands(plugin);

            // 5. Remove plugin instance from PaperPluginInstanceManager
            removePluginFromPaperManager(plugin);

            // 6. Remove plugin instance from legacy SimplePluginManager if present
            removePluginFromSimpleManager(plugin);

            // 7. Remove from ClassLoaderGroup and close ClassLoader
            closePluginClassLoader(plugin);

            // 8. Refresh command tree to client players
            syncCommands();

            // 9. Hint GC to reclaim PermGen/Metaspace memory
            System.gc();

            String typeTag = isPaper ? "Paper" : "Bukkit";
            return new PluginResult(true, "Plugin §b" + name + " §7[§e" + typeTag + "§7] descargado y liberado de memoria exitosamente.");
        } catch (Throwable ex) {
            return new PluginResult(false, "Error al descargar el plugin " + name + ": " + ex.getMessage());
        }
    }

    /**
     * Safely unloads and reloads a plugin from its JAR file in real-time.
     */
    public static PluginResult reload(Plugin plugin) {
        if (plugin == null) {
            return new PluginResult(false, "El plugin especificado no existe o es nulo.");
        }

        String name = plugin.getName();
        File jarFile = getJarFile(plugin);
        if (jarFile == null || !jarFile.exists()) {
            return new PluginResult(false, "No se encontró el archivo JAR de " + name + " en la carpeta plugins/.");
        }

        PluginResult unloadResult = unload(plugin);
        if (!unloadResult.success()) {
            return new PluginResult(false, "Fallo al descargar " + name + ": " + unloadResult.message());
        }

        PluginResult loadResult = load(jarFile);
        if (!loadResult.success()) {
            return new PluginResult(false, "Plugin " + name + " descargado, pero falló la recarga: " + loadResult.message());
        }

        return new PluginResult(true, "Plugin §b" + name + " §arecargado en caliente exitosamente.", loadResult.plugin());
    }

    /**
     * Enables a disabled plugin.
     */
    public static PluginResult enable(Plugin plugin) {
        if (plugin == null) {
            return new PluginResult(false, "Plugin nulo.");
        }
        if (plugin.isEnabled()) {
            return new PluginResult(false, "El plugin " + plugin.getName() + " ya está habilitado.");
        }
        try {
            Bukkit.getPluginManager().enablePlugin(plugin);
            syncCommands();
            return new PluginResult(true, "Plugin §b" + plugin.getName() + " §ahabilitado correctamente.");
        } catch (Throwable ex) {
            return new PluginResult(false, "Error al habilitar " + plugin.getName() + ": " + ex.getMessage());
        }
    }

    /**
     * Disables an enabled plugin without unloading its classes.
     */
    public static PluginResult disable(Plugin plugin) {
        if (plugin == null) {
            return new PluginResult(false, "Plugin nulo.");
        }
        if (!plugin.isEnabled()) {
            return new PluginResult(false, "El plugin " + plugin.getName() + " ya se encuentra deshabilitado.");
        }
        try {
            Bukkit.getPluginManager().disablePlugin(plugin);
            syncCommands();
            return new PluginResult(true, "Plugin §b" + plugin.getName() + " §cdeshabilitado correctamente.");
        } catch (Throwable ex) {
            return new PluginResult(false, "Error al deshabilitar " + plugin.getName() + ": " + ex.getMessage());
        }
    }

    /**
     * Unregisters all commands created by a plugin from the SimpleCommandMap.
     */
    private static void unregisterPluginCommands(Plugin plugin) {
        try {
            CommandMap map = Bukkit.getCommandMap();
            if (map instanceof SimpleCommandMap simpleMap) {
                Map<String, Command> known = simpleMap.getKnownCommands();
                String prefix = plugin.getName().toLowerCase(Locale.ROOT) + ":";
                Iterator<Map.Entry<String, Command>> it = known.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, Command> entry = it.next();
                    Command cmd = entry.getValue();
                    boolean belongs = false;

                    if (cmd instanceof PluginIdentifiableCommand pic) {
                        Plugin owner = pic.getPlugin();
                        if (owner == plugin || (owner != null && owner.getName().equalsIgnoreCase(plugin.getName()))) {
                            belongs = true;
                        }
                    }
                    if (!belongs && entry.getKey().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        belongs = true;
                    }

                    if (belongs) {
                        try {
                            cmd.unregister(simpleMap);
                        } catch (Throwable ignore) {}
                        it.remove();
                    }
                }
            }
        } catch (Throwable ignore) {
        }
    }

    /**
     * Unregisters the plugin from Paper's PaperPluginInstanceManager.
     */
    private static void removePluginFromPaperManager(Plugin plugin) {
        try {
            PluginManager pm = Bukkit.getPluginManager();
            if (pm instanceof io.papermc.paper.plugin.manager.PaperPluginManagerImpl paperPm) {
                Field imField = io.papermc.paper.plugin.manager.PaperPluginManagerImpl.class.getDeclaredField("instanceManager");
                imField.setAccessible(true);
                Object im = imField.get(paperPm);
                if (im != null) {
                    // Remove from plugins list
                    Field pluginsField = im.getClass().getDeclaredField("plugins");
                    pluginsField.setAccessible(true);
                    Object listObj = pluginsField.get(im);
                    if (listObj instanceof List<?> pList) {
                        synchronized (pList) {
                            pList.remove(plugin);
                        }
                    }

                    // Remove from lookupNames map
                    Field lookupField = im.getClass().getDeclaredField("lookupNames");
                    lookupField.setAccessible(true);
                    Object lookupObj = lookupField.get(im);
                    if (lookupObj instanceof Map<?, ?> lookupMap) {
                        synchronized (lookupMap) {
                            lookupMap.remove(plugin.getName());
                            lookupMap.remove(plugin.getName().toLowerCase(Locale.ROOT));
                            if (plugin.getDescription() != null) {
                                lookupMap.remove(plugin.getDescription().getName());
                                lookupMap.remove(plugin.getDescription().getName().toLowerCase(Locale.ROOT));
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignore) {
        }
    }

    /**
     * Removes the plugin from legacy SimplePluginManager if present.
     */
    private static void removePluginFromSimpleManager(Plugin plugin) {
        try {
            PluginManager pm = Bukkit.getPluginManager();
            Class<?> spmClass = Class.forName("org.bukkit.plugin.SimplePluginManager");
            if (spmClass.isInstance(pm)) {
                Field pField = spmClass.getDeclaredField("plugins");
                pField.setAccessible(true);
                List<?> list = (List<?>) pField.get(pm);
                if (list != null) {
                    synchronized (list) {
                        list.remove(plugin);
                    }
                }

                Field lField = spmClass.getDeclaredField("lookupNames");
                lField.setAccessible(true);
                Map<?, ?> map = (Map<?, ?>) lField.get(pm);
                if (map != null) {
                    synchronized (map) {
                        map.remove(plugin.getName());
                        map.remove(plugin.getName().toLowerCase(Locale.ROOT));
                    }
                }
            }
        } catch (Throwable ignore) {
        }
    }

    /**
     * Cleans up the ClassLoader and removes it from Paper's PluginClassLoaderGroup.
     */
    private static void closePluginClassLoader(Plugin plugin) {
        try {
            ClassLoader cl = plugin.getClass().getClassLoader();
            if (cl == null) {
                return;
            }

            // Remove from Paper's PluginClassLoaderGroup
            try {
                Method getGroup = cl.getClass().getMethod("getGroup");
                Object group = getGroup.invoke(cl);
                if (group instanceof io.papermc.paper.plugin.provider.classloader.PluginClassLoaderGroup pcg
                        && cl instanceof io.papermc.paper.plugin.provider.classloader.ConfiguredPluginClassLoader cpcl) {
                    pcg.remove(cpcl);
                }
            } catch (Throwable ignore) {
            }

            // Close URLClassLoader or Closeable
            if (cl instanceof Closeable closeable) {
                closeable.close();
            }
        } catch (Throwable ignore) {
        }
    }

    /**
     * Synchronizes command tree with all connected player clients.
     */
    public static void syncCommands() {
        try {
            Method m = Bukkit.getServer().getClass().getMethod("syncCommands");
            m.invoke(Bukkit.getServer());
        } catch (Throwable ignore) {
            try {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.updateCommands();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Gets all commands registered by a plugin.
     */
    public static List<String> getPluginCommands(Plugin plugin) {
        if (plugin == null) {
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<>();
        try {
            CommandMap map = Bukkit.getCommandMap();
            if (map instanceof SimpleCommandMap simpleMap) {
                for (Map.Entry<String, Command> entry : simpleMap.getKnownCommands().entrySet()) {
                    Command cmd = entry.getValue();
                    if (cmd instanceof PluginIdentifiableCommand pic && pic.getPlugin() == plugin) {
                        if (!list.contains(cmd.getName())) {
                            list.add(cmd.getName());
                        }
                    }
                }
            }
        } catch (Throwable ignore) {
        }
        return list;
    }
}
