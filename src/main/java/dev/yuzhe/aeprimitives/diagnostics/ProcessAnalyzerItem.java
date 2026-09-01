package dev.yuzhe.aeprimitives.diagnostics;

import appeng.api.stacks.AEItemKey;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import dev.yuzhe.aeprimitives.content.ModContent;
import dev.yuzhe.aeprimitives.network.ProcessAnalyzerPayload;
import dev.yuzhe.aeprimitives.operation.OperationPatternData;
import dev.yuzhe.aeprimitives.sequence.SequencePatternData;
import dev.yuzhe.aeprimitives.sequence.SequenceRuntime;
import dev.yuzhe.aeprimitives.space.MachineSpaceComponentItem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
        var insight = blockEntity == null ? null : MachineInsightProviders.inspect(blockEntity);
        if (insight != null) {
            send(player, new ProcessDiagnosticSnapshot((int) insight.revision(), List.of(), List.of(insight)));
            return InteractionResult.CONSUME;
        }
        if (!(blockEntity instanceof PatternProviderLogicHost host) || host.getGrid() == null) {
            player.displayClientMessage(Component.translatable("message.aeprimitives.process_analyzer.target")
                    .withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.CONSUME;
        }
        send(player, SequenceRuntime.snapshot(host.getGrid()));
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player,
                                                   InteractionHand hand) {
        ItemStack analyzer = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResultHolder.success(analyzer);
        ItemStack subject = player.getItemInHand(hand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        var snapshot = inspectItem(subject, level);
        if (snapshot == null) {
            player.displayClientMessage(Component.translatable("message.aeprimitives.process_analyzer.subject")
                    .withStyle(ChatFormatting.YELLOW), true);
        } else {
            send(serverPlayer, snapshot);
        }
        return InteractionResultHolder.consume(analyzer);
    }

    private static ProcessDiagnosticSnapshot inspectItem(ItemStack subject, Level level) {
        if (subject.isEmpty()) return null;
        if (subject.is(ModContent.MACHINE_SPACE_COMPONENT.get())) {
            var envelope = MachineSpaceComponentItem.read(subject);
            var insight = envelope == null ? null : MachineInsightProviders.inspect(envelope);
            return insight == null ? null
                    : new ProcessDiagnosticSnapshot((int) insight.revision(), List.of(), List.of(insight));
        }
        var key = AEItemKey.of(subject);
        if (key == null) return null;
        if (subject.is(ModContent.OPERATION_PATTERN.get())) {
            var details = OperationPatternData.decode(key);
            if (details == null) return null;
            var insight = new MachineInsight(details.spec().operation(), List.of(details.spec()), List.of(),
                    1, "", 0);
            return new ProcessDiagnosticSnapshot(0, List.of(), List.of(insight));
        }
        if (subject.is(ModContent.SEQUENCE_PATTERN.get())) {
            var details = SequencePatternData.decode(key, level);
            return details == null ? null : ProcessDiagnosticModel.build(0, List.of(details.sequence()), List.of());
        }
        return null;
    }

    private static void send(ServerPlayer player, ProcessDiagnosticSnapshot snapshot) {
        PacketDistributor.sendToPlayer(player, new ProcessAnalyzerPayload(snapshot));
    }
}
