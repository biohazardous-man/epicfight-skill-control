package dev.skillcontrol.mixin.client;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = CreativeModeTabs.class, remap = false)
public interface CreativeModeTabsAccessor {
    @Accessor("CACHED_PARAMETERS")
    static void skillcontrol$setCachedParameters(CreativeModeTab.ItemDisplayParameters parameters) {
        throw new AssertionError("Mixin accessor was not applied");
    }
}
