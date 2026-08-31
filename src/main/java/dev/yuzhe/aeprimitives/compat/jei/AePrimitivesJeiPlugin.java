package dev.yuzhe.aeprimitives.compat.jei;

import dev.yuzhe.aeprimitives.AePrimitives;
import dev.yuzhe.aeprimitives.network.PatternImportPayload;
import java.util.Set;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.registration.IAdvancedRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;

/** Adds one small import action directly to supported JEI recipe pages. */
@JeiPlugin
public final class AePrimitivesJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = id("jei");
    private static final ResourceLocation SEQUENCED_ASSEMBLY = create("sequenced_assembly");
    private static final Set<ResourceLocation> OPERATIONS = Set.of(
            create("pressing"), create("crushing"), create("milling"), create("mixing"),
            create("compacting"), create("cutting"), create("deploying"), create("filling"),
            create("haunting"), create("sandpaper_polishing"));

    @Override public ResourceLocation getPluginUid() { return UID; }

    @Override
    public void registerAdvanced(IAdvancedRegistration registration) {
        registration.addRecipeButtonFactory(ImportButton::new);
    }

    private static final class ImportButton implements IIconButtonController {
        private final IRecipeLayoutDrawable<?> layout;
        private final ResourceLocation category;
        private final boolean supported;

        private ImportButton(IRecipeLayoutDrawable<?> layout) {
            this.layout = layout;
            this.category = layout.getRecipeCategory().getRecipeType().getUid();
            this.supported = SEQUENCED_ASSEMBLY.equals(category) || OPERATIONS.contains(category);
        }

        @Override
        public boolean onPress(IJeiUserInput input) {
            if (!supported) return false;
            var recipeId = recipeId(layout);
            if (recipeId == null) return false;
            if (!input.isSimulate()) {
                if (SEQUENCED_ASSEMBLY.equals(category)) {
                    PacketDistributor.sendToServer(new PatternImportPayload(
                            PatternImportPayload.Kind.SEQUENCE, category, recipeId));
                } else {
                    // Normal click imports this recipe. Shift-click imports the whole operation family.
                    boolean family = (input.getModifiers() & 1) != 0;
                    PacketDistributor.sendToServer(new PatternImportPayload(
                            PatternImportPayload.Kind.OPERATION, category, family ? category : recipeId));
                }
            }
            return true;
        }

        @Override
        public void initState(IButtonState state) {
            state.setVisible(supported);
            state.setActive(supported);
            state.setIcon(layout.getRecipeCategory().getIcon());
        }

        @Override
        public void getTooltips(ITooltipBuilder tooltip) {
            if (SEQUENCED_ASSEMBLY.equals(category)) {
                tooltip.add(Component.translatable("jei.aeprimitives.import_sequence"));
            } else {
                tooltip.add(Component.translatable("jei.aeprimitives.import_operation"));
                tooltip.add(Component.translatable("jei.aeprimitives.import_operation_family"));
            }
        }
    }

    private static ResourceLocation recipeId(IRecipeLayoutDrawable<?> layout) {
        Object recipe = layout.getRecipe();
        @SuppressWarnings({"rawtypes", "unchecked"})
        var category = (mezz.jei.api.recipe.category.IRecipeCategory) layout.getRecipeCategory();
        ResourceLocation id = category.getRegistryName(recipe);
        if (id == null && recipe instanceof RecipeHolder<?> holder) id = holder.id();
        return id;
    }

    private static ResourceLocation create(String path) {
        return ResourceLocation.fromNamespaceAndPath("create", path);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(AePrimitives.MOD_ID, path);
    }
}
