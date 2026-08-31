package dev.yuzhe.aeprimitives.network;

import dev.yuzhe.aeprimitives.AePrimitives;
import dev.yuzhe.aeprimitives.compat.create.CreateSequenceImporter;
import dev.yuzhe.aeprimitives.content.ModContent;
import dev.yuzhe.aeprimitives.operation.OperationPatternData;
import dev.yuzhe.aeprimitives.operation.OperationPatternSpec;
import dev.yuzhe.aeprimitives.sequence.SequencePatternData;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-authoritative JEI import request. The client only sends recipe identities. */
public record PatternImportPayload(Kind kind, ResourceLocation operation, ResourceLocation recipe)
        implements CustomPacketPayload {
    public static final Type<PatternImportPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AePrimitives.MOD_ID, "pattern_import"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PatternImportPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeEnum(payload.kind);
                buffer.writeResourceLocation(payload.operation);
                buffer.writeResourceLocation(payload.recipe);
            },
            buffer -> new PatternImportPayload(
                    buffer.readEnum(Kind.class),
                    buffer.readResourceLocation(),
                    buffer.readResourceLocation()));

    private static final Set<ResourceLocation> CREATE_OPERATIONS = Set.of(
            id("pressing"), id("crushing"), id("milling"), id("mixing"), id("compacting"),
            id("cutting"), id("deploying"), id("filling"), id("haunting"), id("sandpaper_polishing"));

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, PatternImportPayload::handle);
    }

    private static void handle(PatternImportPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) payload.apply(player);
        });
    }

    private void apply(ServerPlayer player) {
        Item encodedItem;
        ItemStack encoded;
        if (kind == Kind.SEQUENCE) {
            if (!validSequence(player)) return;
            encodedItem = ModContent.SEQUENCE_PATTERN.get();
            encoded = SequencePatternData.encode(encodedItem, recipe);
        } else {
            if (!validOperation(player)) return;
            encodedItem = ModContent.OPERATION_PATTERN.get();
            var spec = recipe.equals(operation)
                    ? OperationPatternSpec.all(operation)
                    : new OperationPatternSpec(operation, Set.of(recipe), Set.of());
            encoded = OperationPatternData.encode(encodedItem, spec);
        }

        if (!replaceBlank(player, encodedItem, encoded)) {
            player.displayClientMessage(Component.translatable("message.aeprimitives.pattern_import.no_blank"), false);
            return;
        }
        player.displayClientMessage(Component.translatable("message.aeprimitives.pattern_import.success"), false);
    }

    private boolean validOperation(ServerPlayer player) {
        if (!CREATE_OPERATIONS.contains(operation)) return false;
        if (recipe.equals(operation)) return true; // whole operation family
        RecipeHolder<?> holder = player.serverLevel().getRecipeManager().byKey(recipe).orElse(null);
        if (holder == null) return false;
        var typeId = net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType());
        return operation.equals(typeId);
    }

    private boolean validSequence(ServerPlayer player) {
        if (!ModList.get().isLoaded("create")) return false;
        var holder = player.serverLevel().getRecipeManager().byKey(recipe).orElse(null);
        if (holder == null || !(holder.value() instanceof com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe sequence)) {
            return false;
        }
        return CreateSequenceImporter.compile(recipe, sequence).successful();
    }

    private static boolean replaceBlank(ServerPlayer player, Item blankItem, ItemStack encoded) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var candidate = inventory.getItem(slot);
            if (!candidate.is(blankItem) || candidate.has(DataComponents.CUSTOM_DATA)) continue;
            if (candidate.getCount() == 1) {
                inventory.setItem(slot, encoded);
            } else {
                candidate.shrink(1);
                if (!inventory.add(encoded)) player.drop(encoded, false);
            }
            inventory.setChanged();
            return true;
        }
        return false;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("create", path);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public enum Kind { OPERATION, SEQUENCE }
}
