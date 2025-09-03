package dev.replenish;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

final class AuraSkillsCompat {
    private static volatile boolean checked = false;
    private static volatile boolean available = false;
    private static volatile long lastCheckNanos = 0L;
    private static final long RECHECK_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(30);

    private AuraSkillsCompat() {}

    static boolean isAvailable() {
        long now = System.nanoTime();
        if (!checked || !available || (now - lastCheckNanos) >= RECHECK_INTERVAL_NANOS) {
            available = Bukkit.getPluginManager().isPluginEnabled("AuraSkills");
            checked = true;
            lastCheckNanos = now;
        }
        return available;
    }

    static void grantFarmingXp(Player player, double xp) {
        if (!isAvailable() || xp <= 0 || player == null) return;

        try {
            Class<?> apiClass = Class.forName("dev.aurelium.auraskills.api.AuraSkillsApi");
            Method getMethod = apiClass.getMethod("get");
            Object api = getMethod.invoke(null);

            Method getUser = api.getClass().getMethod("getUser", UUID.class);
            Object user = getUser.invoke(api, player.getUniqueId());
            if (user == null) return;

            Method isLoaded = user.getClass().getMethod("isLoaded");
            if (!Boolean.TRUE.equals(isLoaded.invoke(user))) return;

            Class<?> skillsEnum = Class.forName("dev.aurelium.auraskills.api.skill.Skills");
            Field farmingField = skillsEnum.getField("FARMING");
            Object FARMING = farmingField.get(null);

            Class<?> skillInterface = Class.forName("dev.aurelium.auraskills.api.skill.Skill");
            Method addSkillXp = user.getClass().getMethod("addSkillXp", skillInterface, double.class);
            addSkillXp.invoke(user, FARMING, xp);
        } catch (Throwable t) {
            Bukkit.getLogger().log(Level.FINE, "[Replenish] AuraSkills XP grant failed", t);
        }
    }
}
