package me.collective.util;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

public final class ViaProtocolUtil {

    public static final int PROTOCOL_1_9 = 107;
    public static final int PROTOCOL_1_18_2 = 758;
    public static final int PROTOCOL_1_21_2 = 768;
    public static final int UNKNOWN = -1;

    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("viafabricplus");

    private static Method getTargetVersion;
    private static Method getVersion;
    private static boolean resolved;

    private ViaProtocolUtil() {
    }

    public static boolean isPresent() {
        return LOADED;
    }

    public static int targetProtocol() {
        Object version = targetVersion();
        if (version == null) return UNKNOWN;

        try {
            if (getVersion == null) getVersion = version.getClass().getMethod("getVersion", new Class[0]);

            Object value = getVersion.invoke(version, new Object[0]);
            return value instanceof Integer number && number >= 0 ? number : UNKNOWN;
        } catch (Throwable ignored) {
            return UNKNOWN;
        }
    }

    public static String targetName() {
        Object version = targetVersion();
        return version == null ? "" : String.valueOf(version);
    }

    public static boolean isLegacyBand(int protocol) {
        return protocol >= PROTOCOL_1_9 && protocol < PROTOCOL_1_18_2;
    }

    private static Object targetVersion() {
        if (!LOADED) return null;

        try {
            if (!resolved) {
                resolved = true;

                getTargetVersion = Class
                    .forName("com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslator")
                    .getMethod("getTargetVersion", new Class[0]);
            }

            return getTargetVersion == null ? null : getTargetVersion.invoke(null, new Object[0]);
        } catch (Throwable ignored) {
            return null;
        }
    }
}