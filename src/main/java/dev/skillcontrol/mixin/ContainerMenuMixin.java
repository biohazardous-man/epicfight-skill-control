package dev.skillcontrol.mixin;

import dev.skillcontrol.BookCleanup;
import java.util.function.Supplier;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AbstractContainerMenu.class, remap = false)
public abstract class ContainerMenuMixin {
    @Shadow @Final public NonNullList<Slot> slots;
    @Shadow public abstract ItemStack getCarried();
    @Shadow public abstract void setCarried(ItemStack stack);

    @Inject(method = {"triggerSlotListeners", "synchronizeSlotToRemote"},
            at = @At(value = "INVOKE", target = "Ljava/util/function/Supplier;get()Ljava/lang/Object;"), require = 2)
    private void skillcontrol$filterChangedSlot(int slotIndex, ItemStack stack, Supplier<ItemStack> copy, CallbackInfo callback) {
        if (BookCleanup.discardDisabled(stack)) slots.get(slotIndex).set(ItemStack.EMPTY);
    }

    @Inject(method = "synchronizeCarriedToRemote",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;copy()Lnet/minecraft/world/item/ItemStack;"))
    private void skillcontrol$filterChangedCarried(CallbackInfo callback) {
        if (BookCleanup.discardDisabled(getCarried())) setCarried(ItemStack.EMPTY);
    }

    @Redirect(method = "sendAllDataToRemote",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack skillcontrol$filterInitialSlot(Slot slot) {
        ItemStack stack = slot.getItem();
        if (BookCleanup.discardDisabled(stack)) {
            slot.set(ItemStack.EMPTY);
            return ItemStack.EMPTY;
        }
        return stack;
    }

    @Redirect(method = "sendAllDataToRemote",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;getCarried()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack skillcontrol$filterInitialCarried(AbstractContainerMenu menu) {
        ItemStack stack = menu.getCarried();
        if (BookCleanup.discardDisabled(stack)) {
            menu.setCarried(ItemStack.EMPTY);
            return ItemStack.EMPTY;
        }
        return stack;
    }
}
