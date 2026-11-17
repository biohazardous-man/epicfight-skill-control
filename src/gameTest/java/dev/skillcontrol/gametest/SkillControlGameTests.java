package dev.skillcontrol.gametest;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import dev.skillcontrol.BookCleanup;
import dev.skillcontrol.SkillPolicy;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import yesman.epicfight.api.event.types.player.SkillCastEvent;
import yesman.epicfight.data.loot.function.SetSkillFunction;
import yesman.epicfight.registry.EpicFightRegistries;
import yesman.epicfight.registry.entries.EpicFightItems;
import yesman.epicfight.registry.entries.EpicFightSkills;
import yesman.epicfight.skill.Skill;
import yesman.epicfight.skill.SkillSlots;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.item.SkillBookItem;

@Mod("ef_skill_control_tests")
@GameTestHolder("ef_skill_control_tests")
@PrefixGameTestTemplate(false)
public final class SkillControlGameTests {
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void selectiveRestrictions(GameTestHelper helper) throws Exception {
        var catalog = JsonParser.parseString(Files.readString(FMLPaths.CONFIGDIR.get()
                .resolve("epicfight-skill-control.json"))).getAsJsonObject().getAsJsonObject("skills");
        helper.assertTrue(catalog.size() == EpicFightRegistries.SKILL.size(), "Generated catalog must contain every registered skill");
        helper.assertTrue(catalog.getAsJsonObject("epicfight:phantom_ascent").get("description").getAsString().contains("air"),
                "Generated catalog must include English skill descriptions");
        Set<ResourceLocation> original = SkillPolicy.localDisabled();
        try {
            SkillPolicy.setLocalPolicy(Set.of());
            var player = new FakePlayer(helper.getLevel(),
                    new GameProfile(UUID.fromString("de238654-a28c-4b7c-a289-347d21c8ed11"), "SkillControlTest"));
            var patch = EpicFightCapabilities.getServerPlayerPatch(player);
            helper.assertTrue(patch != null, "Epic Fight player capability must be present");
            if (patch.uninitialized()) patch.onJoinWorld(player, helper.getLevel(), false);
            var skills = patch.getPlayerSkills();
            var slot = patch.getSkill(SkillSlots.GUARD);
            Skill forbidden = EpicFightSkills.IMPACT_GUARD.get();
            Skill allowed = EpicFightSkills.GUARD.get();
            helper.assertTrue(slot.setSkill(forbidden), "Enabled skill must be equipable");
            skills.addLearnedSkill(forbidden);
            skills.addLearnedSkill(allowed);
            CompoundTag saved = skills.write(new CompoundTag());

            SkillPolicy.setLocalPolicy(Set.of(forbidden.getRegistryName()));
            helper.assertTrue(!slot.canUse(patch, new SkillCastEvent(patch, slot, new CompoundTag())),
                    "Disabled active skill must not cast even before it is unloaded");
            skills.read(saved);
            helper.assertTrue(slot.isEmpty(), "Saved disabled skill must not be installed");
            helper.assertTrue(skills.hasLearned(forbidden), "Learned state must survive disabling");
            helper.assertTrue(skills.listAcquiredSkills().noneMatch(skill -> skill == forbidden), "Disabled learned skill must not be selectable");
            helper.assertTrue(skills.listAcquiredSkills().anyMatch(skill -> skill == allowed), "Allowed learned skill must remain selectable");
            slot.setSkill(forbidden);
            helper.assertTrue(slot.isEmpty(), "Direct installation must not bypass policy");
            helper.assertTrue(!slot.requestCasting(patch, new CompoundTag()), "Disabled slot must reject cast requests");
            helper.assertTrue(!slot.requestHold(patch, new CompoundTag()), "Disabled slot must reject hold requests");
            helper.assertTrue(slot.setSkill(allowed) && slot.getSkill() == allowed, "Unrelated guard skill must remain equipable");

            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            player.setItemInHand(InteractionHand.MAIN_HAND, sword);
            var capability = EpicFightCapabilities.getItemCapability(sword).orElseThrow();
            Skill innate = capability.getInnateSkill(patch, sword);
            helper.assertTrue(innate != null, "Test sword must have a weapon innate skill");
            Set<ResourceLocation> disabled = new HashSet<>(SkillPolicy.localDisabled());
            disabled.add(innate.getRegistryName());
            disabled.add(EpicFightSkills.BERSERKER.get().getRegistryName());
            SkillPolicy.setLocalPolicy(disabled);
            helper.assertTrue(capability.getInnateSkill(patch, sword) == null, "Disabled weapon innate skill must not be exposed");
            capability.changeWeaponInnateSkill(patch, sword);
            helper.assertTrue(patch.getSkill(SkillSlots.WEAPON_INNATE).isEmpty(), "Weapon change must not restore disabled innate skill");
            var passive = skills.getSkillContainersFor(EpicFightSkills.BERSERKER.get().getCategory()).iterator().next();
            passive.setSkill(EpicFightSkills.BERSERKER.get());
            helper.assertTrue(passive.isEmpty(), "Disabled passive skill must not initialize");

            ItemStack forbiddenBook = book(forbidden);
            ItemStack allowedBook = book(allowed);
            player.setItemInHand(InteractionHand.MAIN_HAND, forbiddenBook);
            helper.assertTrue(forbiddenBook.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult() == InteractionResult.FAIL,
                    "Existing disabled book must not open its skill screen");
            player.getInventory().setItem(0, forbiddenBook.copy());
            player.getInventory().setItem(1, allowedBook.copy());
            BookCleanup.onPlayerTick(new PlayerTickEvent.Post(player));
            helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "Disabled book must disappear from inventory");
            helper.assertTrue(!player.getInventory().getItem(1).isEmpty(), "Allowed book must remain in inventory");
            var chest = new SimpleContainer(27);
            chest.setItem(0, forbiddenBook.copy());
            chest.setItem(1, allowedBook.copy());
            BookCleanup.onContainerOpen(new PlayerContainerEvent.Open(player, ChestMenu.threeRows(0, player.getInventory(), chest)));
            helper.assertTrue(chest.getItem(0).isEmpty() && !chest.getItem(1).isEmpty(), "Opening a chest must remove only disabled books");
            var drop = new ItemEntity(helper.getLevel(), 0, 100, 0, forbiddenBook.copy());
            helper.assertTrue(!helper.getLevel().addFreshEntity(drop), "Disabled book must not enter the world as an item entity");
            var params = new LootParams.Builder(helper.getLevel()).create(LootContextParamSets.EMPTY);
            var forbiddenLoot = loot(forbidden);
            helper.assertTrue(forbiddenLoot.getRandomItems(params).isEmpty(), "Final loot must not emit forbidden books");
            var allowedLoot = loot(allowed);
            helper.assertTrue(!allowedLoot.getRandomItems(params).isEmpty(), "Allowed skill-book loot must remain");

            SkillPolicy.setServerPolicy(Set.of());
            helper.assertTrue(!SkillPolicy.isDisabled(forbidden), "An empty server policy must override local restrictions");
            SkillPolicy.clearServerPolicy();
            helper.assertTrue(SkillPolicy.isDisabled(forbidden), "Disconnect must restore local restrictions");
            SkillPolicy.setLocalPolicy(Set.of());
            helper.assertTrue(skills.listAcquiredSkills().anyMatch(skill -> skill == forbidden), "Re-enabled learned skills must return without relearning");
            helper.succeed();
        } finally {
            SkillPolicy.clearServerPolicy();
            SkillPolicy.setLocalPolicy(original);
        }
    }

    private static LootTable loot(Skill skill) {
        LootTable table = LootTable.lootTable().withPool(LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1))
                .add(LootItem.lootTableItem(EpicFightItems.SKILLBOOK.get()).apply(SetSkillFunction.builder(skill.holder())))).build();
        table.setLootTableId(ResourceLocation.fromNamespaceAndPath("ef_skill_control_tests", skill.getRegistryName().getPath()));
        return table;
    }

    private static ItemStack book(Skill skill) {
        ItemStack stack = new ItemStack(EpicFightItems.SKILLBOOK.get());
        SkillBookItem.setContainingSkill(skill.holder(), stack);
        return stack;
    }
}
