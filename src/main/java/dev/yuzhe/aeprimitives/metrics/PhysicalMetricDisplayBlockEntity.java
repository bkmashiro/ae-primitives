package dev.yuzhe.aeprimitives.metrics;


import appeng.api.networking.GridFlags;
import appeng.api.networking.IStackWatcher;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

public final class PhysicalMetricDisplayBlockEntity extends AENetworkedBlockEntity
        implements IStorageWatcherNode {
    private ResourceLocation selectedMetric;
    private PhysicalMetricSample visibleSample;
    private IStackWatcher watcher;
    private boolean dirty = true;

    public PhysicalMetricDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(dev.yuzhe.aeprimitives.content.ModContent.PHYSICAL_METRIC_DISPLAY_ENTITY.get(), pos, state);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .setIdlePowerUsage(0.5)
                .addService(IStorageWatcherNode.class, this);
    }

    public ResourceLocation selectedMetric() {
        return selectedMetric;
    }

    public PhysicalMetricSample visibleSample() {
        return visibleSample;
    }

    public ResourceLocation cycleMetric() {
        selectedMetric = PhysicalMetricSelection.next(PhysicalMetricProviders.ids(), selectedMetric);
        configureWatcher();
        dirty = true;
        setChanged();
        return selectedMetric;
    }

    public void serverTick() {
        if (level == null || level.isClientSide) return;
        if (selectedMetric == null) {
            selectedMetric = PhysicalMetricSelection.resolve(PhysicalMetricProviders.ids(), null);
            configureWatcher();
            dirty = true;
            setChanged();
        }
        if (!dirty) return;
        dirty = false;

        PhysicalMetricSample next;
        var provider = selectedMetric == null ? null : PhysicalMetricProviders.get(selectedMetric);
        if (!getMainNode().isActive() || getMainNode().getGrid() == null || provider == null) {
            next = selectedMetric == null ? null : PhysicalMetricSample.unavailable(selectedMetric);
        } else {
            next = provider.sample(new PhysicalMetricContext(getMainNode().getGrid(),
                    IActionSource.ofMachine(this), worldPosition));
        }
        if (!java.util.Objects.equals(next, visibleSample)) {
            visibleSample = next;
            markForUpdate();
        }
    }

    @Override
    public void updateWatcher(IStackWatcher watcher) {
        this.watcher = watcher;
        configureWatcher();
        dirty = true;
    }

    @Override
    public void onStackChange(appeng.api.stacks.AEKey what, long amount) {
        dirty = true;
    }

    private void configureWatcher() {
        if (watcher == null) return;
        watcher.reset();
        var provider = selectedMetric == null ? null : PhysicalMetricProviders.get(selectedMetric);
        if (provider != null) provider.watchedStorageKeys().forEach(watcher::add);
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (selectedMetric != null) tag.putString("selectedMetric", selectedMetric.toString());
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        selectedMetric = tag.contains("selectedMetric")
                ? ResourceLocation.tryParse(tag.getString("selectedMetric")) : null;
        dirty = true;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return visualTag();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        readVisualTag(tag);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider registries) {
        readVisualTag(packet.getTag());
    }

    private CompoundTag visualTag() {
        var tag = new CompoundTag();
        if (selectedMetric != null) tag.putString("selectedMetric", selectedMetric.toString());
        if (visibleSample != null) {
            tag.putString("sampleId", visibleSample.id().toString());
            tag.putString("labelKey", visibleSample.labelKey());
            tag.putString("unit", visibleSample.unit());
            tag.putDouble("value", visibleSample.value());
            tag.putDouble("minimum", visibleSample.minimum());
            tag.putDouble("maximum", visibleSample.maximum());
            tag.putString("presentation", visibleSample.presentation().name());
            tag.putString("state", visibleSample.state().name());
        }
        return tag;
    }

    private void readVisualTag(CompoundTag tag) {
        selectedMetric = tag.contains("selectedMetric")
                ? ResourceLocation.tryParse(tag.getString("selectedMetric")) : null;
        if (!tag.contains("sampleId")) {
            visibleSample = null;
            return;
        }
        var id = ResourceLocation.tryParse(tag.getString("sampleId"));
        if (id == null) {
            visibleSample = null;
            return;
        }
        visibleSample = new PhysicalMetricSample(id, tag.getString("labelKey"), tag.getString("unit"),
                tag.getDouble("value"), tag.getDouble("minimum"), tag.getDouble("maximum"),
                enumValue(PhysicalMetricPresentation.class, tag.getString("presentation"),
                        PhysicalMetricPresentation.NUMERIC),
                enumValue(PhysicalMetricState.class, tag.getString("state"), PhysicalMetricState.UNAVAILABLE));
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String name, T fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
