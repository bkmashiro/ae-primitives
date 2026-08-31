package dev.yuzhe.aeprimitives.diagnostics;

import appeng.helpers.patternprovider.PatternProviderLogicHost;
import dev.yuzhe.aeprimitives.network.ProcessAnalyzerPayload;
import dev.yuzhe.aeprimitives.sequence.SequenceRuntime;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.neoforge.network.PacketDistributor;

/** Opens a read-only, event-backed view of the AE network reached through a Pattern Provider. */
public final class ProcessAnalyzerItem extends Item {
    public ProcessAnalyzerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        var blockEntity = context.getLevel().getBlockEntity(context.getClickedPos());
        if (!(blockEntity instanceof PatternProviderLogicHost host) || host.getGrid() == null) {
            player.displayClientMessage(Component.translatable("message.aeprimitives.process_analyzer.target")
                    .withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.CONSUME;
        }
        PacketDistributor.sendToPlayer(player, new ProcessAnalyzerPayload(SequenceRuntime.snapshot(host.getGrid())));
        return InteractionResult.CONSUME;
    }
}
