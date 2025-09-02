package dev.replenish;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

final class AuraSkillsCompat {
    private static volatile boolean checked = false;
    private static volatile boolean available = false;

    private AuraSkillsCompat() {}

    static boolean isAvailable() {
        if (!checked) {
            available = Bukkit.getPluginManager().isPluginEnabled("AuraSkills");
            checked = true;
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
        } catch (Throwable ignored) {
        }
    }
}
