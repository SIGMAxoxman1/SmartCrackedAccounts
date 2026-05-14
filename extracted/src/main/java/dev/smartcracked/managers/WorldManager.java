package dev.smartcracked.managers;

import dev.smartcracked.SmartCrackedAccounts;
import org.bukkit.*;
import org.bukkit.WorldCreator;

/**
 * بيعمل عالم اللوبي الفارغ تلقائياً لو مش موجود
 */
public class WorldManager {

    private final SmartCrackedAccounts plugin;
    private World lobbyWorld;

    public WorldManager(SmartCrackedAccounts plugin) {
        this.plugin = plugin;
    }

    public void setupLobbyWorld() {
        String worldName = plugin.getConfigManager().getLobbyWorld();

        // شوف لو العالم محمّل بالفعل
        lobbyWorld = Bukkit.getWorld(worldName);

        if (lobbyWorld == null) {
            // العالم مش محمّل - نعمله أو نحمّله
            plugin.getLogger().info("بيعمل عالم اللوبي: " + worldName);

            WorldCreator creator = new WorldCreator(worldName);
            // عالم فارغ تماماً بدون أي generation
            creator.type(WorldType.FLAT);
            creator.generatorSettings("{\"layers\":[{\"block\":\"air\",\"height\":1}],\"biome\":\"the_void\"}");
            creator.environment(World.Environment.NORMAL);

            lobbyWorld = creator.createWorld();

            if (lobbyWorld != null) {
                configureWorld(lobbyWorld);
                plugin.getLogger().info("عالم اللوبي اتعمل بنجاح! ✔");
            } else {
                plugin.getLogger().severe("فشل في عمل عالم اللوبي! تأكد من الصلاحيات.");
            }
        } else {
            configureWorld(lobbyWorld);
            plugin.getLogger().info("عالم اللوبي موجود بالفعل وتم تحميله. ✔");
        }
    }

    private void configureWorld(World world) {
        // إعدادات العالم الفارغ
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);     // إيقاف دورة الليل/النهار
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);      // إيقاف الطقس
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);       // مفيش مونسترز
        world.setGameRule(GameRule.DO_FIRE_TICK, false);          // مفيش نار
        world.setGameRule(GameRule.FALL_DAMAGE, false);           // مفيش ضرر سقوط
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false); // مفيش achievements
        world.setTime(6000);                                       // وقت النهار دايماً
        world.setStorm(false);
    }

    /**
     * بيرجع موقع اللوبي من الـ config
     */
    public Location getLobbyLocation() {
        if (lobbyWorld == null) {
            setupLobbyWorld();
        }
        ConfigManager cfg = plugin.getConfigManager();
        return new Location(
                lobbyWorld,
                cfg.getLobbyX(),
                cfg.getLobbyY(),
                cfg.getLobbyZ(),
                cfg.getLobbyYaw(),
                cfg.getLobbyPitch()
        );
    }

    public World getLobbyWorld() {
        return lobbyWorld;
    }
}
