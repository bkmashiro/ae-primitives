package dev.yuzhe.aeprimitives.space;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class VirtualMachineLaneExecutors {
    private static final List<VirtualMachineLaneExecutor> EXECUTORS = new CopyOnWriteArrayList<>();

    public static void register(VirtualMachineLaneExecutor executor) {
        if (!EXECUTORS.contains(executor)) EXECUTORS.add(executor);
    }

    public static VirtualMachineLaneExecutor find(MachineSpaceEnvelope envelope) {
        for (var executor : EXECUTORS) if (executor.supports(envelope)) return executor;
        return null;
    }

    private VirtualMachineLaneExecutors() {}
}
