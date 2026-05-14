package dev.smartcracked.listeners;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * بيكشف لاعبين Bedrock بدون ما نحتاج Floodgate كـ compile dependency
 * بيستخدم Reflection عشان cumulus jar مش متاح في Maven repos العامة
 */
public class FloodgateHelper {

    private static Method isFloodgatePlayerMethod = null;
    private static Object floodgateApiInstance = null;
    private static boolean initialized = false;

    private static void init() {
        if (initialized) return;
        initialized = true;
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Method getInstance = apiClass.getMethod("getInstance");
            floodgateApiInstance = getInstance.invoke(null);
            isFloodgatePlayerMethod = apiClass.getMethod("isFloodgatePlayer", java.util.UUID.class);
        } catch (Exception ignored) {
            // Floodgate مش موجود - مش مشكلة
        }
    }

    /**
     * بيرجع true لو اللاعب بيدروك (Floodgate)
     * بيرجع false لو Floodgate مش موجود أو اللاعب مش بيدروك
     */
    public static boolean isBedrockPlayer(Player player) {
        init();
        if (floodgateApiInstance == null || isFloodgatePlayerMethod == null) return false;
        try {
            return (boolean) isFloodgatePlayerMethod.invoke(floodgateApiInstance, player.getUniqueId());
        } catch (Exception ignored) {
            return false;
        }
    }
}
