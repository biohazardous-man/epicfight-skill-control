package dev.skillcontrol;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public final class BookCleanup {
    private BookCleanup() {}

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ItemEntity item && SkillPolicy.isDisabled(item.getItem())) {
            event.setCanceled(true);
        }
    }

    public static boolean discardDisabled(ItemStack stack) {
        if (!SkillPolicy.isDisabled(stack)) return false;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread()) return false;
        // Vanilla may already hold this stack in a local variable or copy supplier.
        stack.setCount(0);
        return true;
    }
}
