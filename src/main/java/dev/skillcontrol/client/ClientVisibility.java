package dev.skillcontrol.client;

import dev.skillcontrol.SkillPolicy;
import dev.skillcontrol.mixin.client.CreativeModeTabsAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import yesman.epicfight.client.gui.screen.SkillEditScreen;
import yesman.epicfight.client.world.capabilites.entitypatch.player.LocalPlayerPatch;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;

public final class ClientVisibility {
    private static Runnable recipeViewerRefresh;

    private ClientVisibility() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(EventPriority.LOWEST, ClientVisibility::buildCreativeTab);
        NeoForge.EVENT_BUS.addListener(ClientVisibility::loggingOut);
    }

    public static void policyChanged() {
        CreativeModeTabsAccessor.skillcontrol$setCachedParameters(null);
        if (recipeViewerRefresh != null) {
            recipeViewerRefresh.run();
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof SkillEditScreen && minecraft.player != null) {
            LocalPlayerPatch playerPatch = EpicFightCapabilities.getLocalPlayerPatch(minecraft.player);
            if (playerPatch != null) {
                minecraft.setScreen(new SkillEditScreen(minecraft.player, playerPatch.getPlayerSkills()));
            }
        }
    }

    static void setRecipeViewerRefresh(Runnable refresh) {
        recipeViewerRefresh = refresh;
    }

    private static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SkillPolicy.clearServerPolicy();
    }

    private static void buildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!SkillPolicy.hasDisabledSkills()) return;
        List<ItemStack> hidden = new ArrayList<>();
        for (ItemStack stack : event.getParentEntries()) {
            if (SkillPolicy.isDisabled(stack)) {
                hidden.add(stack);
            }
        }
        for (ItemStack stack : event.getSearchEntries()) {
            if (!event.getParentEntries().contains(stack) && SkillPolicy.isDisabled(stack)) {
                hidden.add(stack);
            }
        }
        for (ItemStack stack : hidden) {
            event.remove(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }
}
