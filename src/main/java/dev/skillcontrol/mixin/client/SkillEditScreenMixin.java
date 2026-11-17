package dev.skillcontrol.mixin.client;

import dev.skillcontrol.SkillPolicy;
import java.util.function.Predicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import yesman.epicfight.client.gui.screen.SkillEditScreen;
import yesman.epicfight.skill.Skill;

@Mixin(value = SkillEditScreen.class, remap = false)
public abstract class SkillEditScreenMixin {
    @ModifyArg(
        method = {"lambda$init$8", "populatePassiveEquipList"},
        at = @At(
            value = "INVOKE",
            target = "Lyesman/epicfight/api/data/reloader/SkillReloadListener;getSkills(Ljava/util/function/Predicate;)Ljava/util/Collection;"
        ),
        index = 0,
        require = 2
    )
    private Predicate<Skill> efSkillControl$filterCreativeSkills(Predicate<Skill> original) {
        return original.and(skill -> !SkillPolicy.isDisabled(skill));
    }
}
