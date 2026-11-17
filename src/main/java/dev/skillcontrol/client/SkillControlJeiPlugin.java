package dev.skillcontrol.client;

import dev.skillcontrol.SkillPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.vanilla.IJeiIngredientInfoRecipe;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import yesman.epicfight.api.data.reloader.SkillReloadListener;
import yesman.epicfight.registry.entries.EpicFightDataComponentTypes;
import yesman.epicfight.registry.entries.EpicFightItems;
import yesman.epicfight.skill.Skill;
import yesman.epicfight.world.item.SkillBookItem;

@JeiPlugin
public final class SkillControlJeiPlugin implements IModPlugin {
    private final List<ItemStack> hiddenBooks = new ArrayList<>();
    private final Set<IJeiIngredientInfoRecipe> hiddenDescriptions =
        Collections.newSetFromMap(new IdentityHashMap<>());
    private IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath("ef_skill_control", "skill_visibility");
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        ISubtypeInterpreter<ItemStack> interpreter = new SkillBookSubtypeInterpreter();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof SkillBookItem) {
                registration.registerSubtypeInterpreter(item, interpreter);
            }
        }
    }

    @Override
    public void registerExtraIngredients(IExtraIngredientRegistration registration) {
        // Keep all subtypes available for restoration after a server policy change.
        List<ItemStack> books = new ArrayList<>();
        for (Skill skill : SkillReloadListener.getSkills(skill -> skill.getCategory().learnable())) {
            ItemStack stack = new ItemStack(EpicFightItems.SKILLBOOK.get());
            SkillBookItem.setContainingSkill(skill.holder(), stack);
            books.add(stack);
        }
        if (!books.isEmpty()) {
            registration.addExtraItemStacks(books);
        }
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        hiddenBooks.clear();
        hiddenDescriptions.clear();
        ClientVisibility.setRecipeViewerRefresh(this::refresh);
        refresh();
    }

    @Override
    public void onRuntimeUnavailable() {
        ClientVisibility.setRecipeViewerRefresh(null);
        runtime = null;
        hiddenBooks.clear();
        hiddenDescriptions.clear();
    }

    private void refresh() {
        if (runtime == null) {
            return;
        }
        refreshBooks(runtime.getIngredientManager());
        refreshDescriptions(runtime.getRecipeManager());
    }

    private void refreshBooks(IIngredientManager ingredients) {
        List<ItemStack> reveal = new ArrayList<>();
        Iterator<ItemStack> hidden = hiddenBooks.iterator();
        while (hidden.hasNext()) {
            ItemStack stack = hidden.next();
            if (!SkillPolicy.isDisabled(stack)) {
                reveal.add(stack);
                hidden.remove();
            }
        }
        if (!reveal.isEmpty()) {
            ingredients.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, reveal);
        }

        List<ItemStack> remove = new ArrayList<>();
        for (ItemStack stack : ingredients.getAllItemStacks()) {
            if (SkillPolicy.isDisabled(stack)) {
                remove.add(stack);
            }
        }
        if (!remove.isEmpty()) {
            hiddenBooks.addAll(remove);
            ingredients.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, remove);
        }
    }

    private void refreshDescriptions(IRecipeManager recipes) {
        List<IJeiIngredientInfoRecipe> reveal = new ArrayList<>();
        Iterator<IJeiIngredientInfoRecipe> hidden = hiddenDescriptions.iterator();
        while (hidden.hasNext()) {
            IJeiIngredientInfoRecipe recipe = hidden.next();
            if (!describesDisabledSkill(recipe)) {
                reveal.add(recipe);
                hidden.remove();
            }
        }
        if (!reveal.isEmpty()) {
            recipes.unhideRecipes(RecipeTypes.INFORMATION, reveal);
        }

        List<IJeiIngredientInfoRecipe> remove = recipes.createRecipeLookup(RecipeTypes.INFORMATION)
            .get()
            .filter(SkillControlJeiPlugin::describesDisabledSkill)
            .toList();
        if (!remove.isEmpty()) {
            hiddenDescriptions.addAll(remove);
            recipes.hideRecipes(RecipeTypes.INFORMATION, remove);
        }
    }

    private static boolean describesDisabledSkill(IJeiIngredientInfoRecipe recipe) {
        for (ITypedIngredient<?> ingredient : recipe.getIngredients()) {
            ItemStack stack = ingredient.getCastIngredient(VanillaTypes.ITEM_STACK);
            if (stack != null && SkillPolicy.isDisabled(stack)) {
                return true;
            }
        }
        return false;
    }

    private static final class SkillBookSubtypeInterpreter implements ISubtypeInterpreter<ItemStack> {
        @Override
        public Object getSubtypeData(ItemStack stack, UidContext context) {
            Holder<Skill> skill = stack.get(EpicFightDataComponentTypes.SKILL.get());
            return skill == null ? null : skill.getKey().location();
        }

        @Override
        public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) {
            Object subtype = getSubtypeData(stack, context);
            return subtype == null ? "" : subtype.toString();
        }
    }
}
