package dev.skillcontrol.gametest;

import com.mojang.authlib.GameProfile;
import dev.skillcontrol.SkillPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import yesman.epicfight.registry.entries.EpicFightItems;
import yesman.epicfight.registry.entries.EpicFightSkills;
import yesman.epicfight.skill.Skill;
import yesman.epicfight.world.item.SkillBookItem;

@GameTestHolder("ef_skill_control_tests")
@PrefixGameTestTemplate(false)
public final class BookCleanupSyncGameTests {
    @GameTest(template = "empty")
    public static void filtersInitialSynchronization(GameTestHelper helper) {
        Set<ResourceLocation> original = SkillPolicy.localDisabled();
        try {
            Skill forbidden = EpicFightSkills.PHANTOM_ASCENT.get();
            Skill allowed = EpicFightSkills.GUARD.get();
            SkillPolicy.setLocalPolicy(Set.of(forbidden.getRegistryName()));
            FakePlayer player = player(helper, 1);
            SimpleContainer chest = new SimpleContainer(27);
            chest.setItem(0, book(forbidden));
            chest.setItem(1, book(allowed));
            ChestMenu menu = ChestMenu.threeRows(0, player.getInventory(), chest);
            menu.setCarried(book(forbidden));
            RecordingSynchronizer receiver = new RecordingSynchronizer();

            menu.setSynchronizer(receiver);

            helper.assertTrue(chest.getItem(0).isEmpty(), "Initial sync must delete the forbidden server-side book");
            helper.assertTrue(!chest.getItem(1).isEmpty(), "Initial sync must preserve an allowed server-side book");
            helper.assertTrue(menu.getCarried().isEmpty(), "Initial sync must delete a forbidden cursor stack");
            helper.assertTrue(receiver.initial.get(0).isEmpty() && !receiver.initial.get(1).isEmpty(),
                    "Initial client snapshot must omit only forbidden books");
            helper.assertTrue(receiver.carried.isEmpty(), "Initial client cursor must not receive a forbidden book");
            helper.succeed();
        } finally {
            SkillPolicy.setLocalPolicy(original);
        }
    }

    @GameTest(template = "empty")
    public static void filtersDirtySlotsAndCursor(GameTestHelper helper) {
        Set<ResourceLocation> original = SkillPolicy.localDisabled();
        try {
            Skill forbidden = EpicFightSkills.PHANTOM_ASCENT.get();
            Skill allowed = EpicFightSkills.GUARD.get();
            SkillPolicy.setLocalPolicy(Set.of(forbidden.getRegistryName()));
            FakePlayer player = player(helper, 2);
            SimpleContainer chest = new SimpleContainer(27);
            ChestMenu menu = ChestMenu.threeRows(0, player.getInventory(), chest);
            RecordingSynchronizer receiver = new RecordingSynchronizer();
            menu.setSynchronizer(receiver);
            menu.broadcastChanges();

            chest.setItem(0, book(forbidden));
            chest.setItem(1, book(allowed));
            menu.setCarried(book(forbidden));
            menu.broadcastChanges();

            helper.assertTrue(chest.getItem(0).isEmpty() && !chest.getItem(1).isEmpty(), "Dirty sync must remove only forbidden books");
            helper.assertTrue(menu.getCarried().isEmpty(), "Dirty sync must delete a forbidden cursor stack");
            helper.assertTrue(receiver.slotUpdates.stream().anyMatch(update -> update.slot() == 1 && !update.stack().isEmpty()),
                    "Client must still receive the allowed book update");
            assertNoForbiddenUpdates(helper, receiver);

            SkillBookItem.setContainingSkill(forbidden.holder(), chest.getItem(1));
            menu.broadcastChanges();
            helper.assertTrue(chest.getItem(1).isEmpty(), "In-place skill component changes must not bypass cleanup");
            helper.assertTrue(receiver.slotUpdates.getLast().slot() == 1 && receiver.slotUpdates.getLast().stack().isEmpty(),
                    "Client must receive removal of a formerly allowed book");

            menu.suppressRemoteUpdates();
            chest.setItem(2, book(forbidden));
            menu.setCarried(book(forbidden));
            menu.broadcastChanges();
            menu.resumeRemoteUpdates();
            menu.broadcastChanges();
            helper.assertTrue(chest.getItem(2).isEmpty() && menu.getCarried().isEmpty(),
                    "Suppressed updates must not allow forbidden books to leak when synchronization resumes");
            assertNoForbiddenUpdates(helper, receiver);

            chest.setItem(3, book(forbidden));
            menu.setCarried(book(forbidden));
            menu.broadcastFullState();
            helper.assertTrue(chest.getItem(3).isEmpty() && receiver.initial.get(3).isEmpty() && receiver.carried.isEmpty(),
                    "Full resynchronization must remove forbidden slot and cursor contents before sending them");
            helper.succeed();
        } finally {
            SkillPolicy.setLocalPolicy(original);
        }
    }

    private static void assertNoForbiddenUpdates(GameTestHelper helper, RecordingSynchronizer receiver) {
        helper.assertTrue(receiver.slotUpdates.stream().noneMatch(update -> SkillPolicy.isDisabled(update.stack())),
                "Forbidden books must never be sent in incremental slot updates");
        helper.assertTrue(receiver.cursorUpdates.stream().noneMatch(SkillPolicy::isDisabled),
                "Forbidden books must never be sent in incremental cursor updates");
    }

    private static FakePlayer player(GameTestHelper helper, int id) {
        return new FakePlayer(helper.getLevel(), new GameProfile(new UUID(0x71e1c105L, id), "BookSyncTest" + id));
    }

    private static ItemStack book(Skill skill) {
        ItemStack stack = new ItemStack(EpicFightItems.SKILLBOOK.get());
        SkillBookItem.setContainingSkill(skill.holder(), stack);
        return stack;
    }

    private record SlotUpdate(int slot, ItemStack stack) {}

    private static final class RecordingSynchronizer implements ContainerSynchronizer {
        private List<ItemStack> initial = List.of();
        private ItemStack carried = ItemStack.EMPTY;
        private final List<SlotUpdate> slotUpdates = new ArrayList<>();
        private final List<ItemStack> cursorUpdates = new ArrayList<>();
        private final List<Integer> dataUpdates = new ArrayList<>();

        @Override
        public void sendInitialData(AbstractContainerMenu menu, NonNullList<ItemStack> items, ItemStack cursor, int[] data) {
            initial = items.stream().map(ItemStack::copy).toList();
            carried = cursor.copy();
            for (int value : data) dataUpdates.add(value);
        }

        @Override
        public void sendSlotChange(AbstractContainerMenu menu, int slot, ItemStack stack) {
            slotUpdates.add(new SlotUpdate(slot, stack.copy()));
        }

        @Override
        public void sendCarriedChange(AbstractContainerMenu menu, ItemStack stack) {
            carried = stack.copy();
            cursorUpdates.add(carried);
        }

        @Override
        public void sendDataChange(AbstractContainerMenu menu, int id, int value) {
            dataUpdates.add(value);
        }
    }
}
