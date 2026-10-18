package io.papermc.paper;

import joptsimple.OptionSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bootstrap loader for UniversePurpur Titan Engine (+15,000 Players).
 * Completely rebrands and sanitizes bootstrap logs.
 *
 * Author: DevPapo (admin@scon.host)
 */
public final class PaperBootstrap {
    private static final Logger LOGGER = LoggerFactory.getLogger("bootstrap");

    private PaperBootstrap() {}

    public static void boot(final OptionSet options) {
        net.minecraft.SharedConstants.tryDetectVersion();
        
        final String javaVer = System.getProperty("java.version");
        final String vmName = System.getProperty("java.vm.name");
        final String osName = System.getProperty("os.name");
        final String osArch = System.getProperty("os.arch");

        LOGGER.info(String.format("Running Java %s (%s) on %s (%s)", javaVer, vmName, osName, osArch));
        LOGGER.info(String.format("Loading UniversePurpur Titan Engine (Build: 1.0.3-TITAN-RELEASE) for Minecraft %s",
            net.minecraft.SharedConstants.getCurrentVersion().getName()
        ));

        String startupLicense = System.getenv("LICENSE_KEY");
        String licenseSource = "environment variable (LICENSE_KEY)";
        if (startupLicense == null || startupLicense.isEmpty()) {
            startupLicense = System.getProperty("license.key");
            licenseSource = "JVM parameter (-Dlicense.key)";
        }
        if (startupLicense != null && !startupLicense.trim().isEmpty()) {
            LOGGER.info(String.format("[SCON Licensing] Detected startup license via %s: %s",
                licenseSource,
                org.purpurmc.purpur.license.UniverseLicenseManager.maskKey(startupLicense.trim())
            ));
        }

        // Early SCON License Validation: halts startup immediately if missing or invalid
        org.purpurmc.purpur.license.UniverseLicenseManager.initEarly();

        net.minecraft.server.Main.main(options);
    }
}
