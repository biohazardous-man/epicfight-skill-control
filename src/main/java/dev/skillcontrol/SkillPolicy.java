package dev.skillcontrol;

import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import yesman.epicfight.registry.entries.EpicFightDataComponentTypes;
import yesman.epicfight.skill.Skill;
import yesman.epicfight.world.item.SkillBookItem;

public final class SkillPolicy {
    private static volatile Set<ResourceLocation> localDisabled = Set.of();
    private static volatile Set<ResourceLocation> serverDisabled;
    private static Runnable refreshListener;

    private SkillPolicy() {}

    public static boolean isDisabled(ResourceLocation id) {
        Set<ResourceLocation> remote = serverDisabled;
        return (remote == null ? localDisabled : remote).contains(id);
    }

    public static boolean hasDisabledSkills() {
        Set<ResourceLocation> remote = serverDisabled;
        return !(remote == null ? localDisabled : remote).isEmpty();
    }

    public static boolean isDisabled(Skill skill) {
        return skill != null && isDisabled(skill.getRegistryName());
    }

    public static boolean isDisabled(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof SkillBookItem)) return false;
        Holder<Skill> skill = stack.get(EpicFightDataComponentTypes.SKILL.get());
        return skill != null && isDisabled(skill.value());
    }

    public static Skill allowed(Skill skill) {
        return isDisabled(skill) ? null : skill;
    }

    public static void setLocalPolicy(Set<ResourceLocation> disabled) {
        localDisabled = Set.copyOf(disabled);
    }

    public static Set<ResourceLocation> localDisabled() {
        return localDisabled;
    }

    public static void setServerPolicy(Set<ResourceLocation> disabled) {
        serverDisabled = Set.copyOf(disabled);
        refreshClient();
    }

    public static void clearServerPolicy() {
        serverDisabled = null;
        refreshClient();
    }

    public static void setRefreshListener(Runnable listener) {
        refreshListener = listener;
    }

    private static void refreshClient() {
        if (refreshListener != null) refreshListener.run();
    }
}
