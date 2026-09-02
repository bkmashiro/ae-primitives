package dev.yuzhe.aeprimitives.commissioning;

import dev.yuzhe.aeprimitives.space.MachineSpaceEnvelope;
import java.util.List;

/** Pure description provider. Implementations receive a defensive envelope copy and no world or storage handle. */
public interface DeterministicCommissioningProvider {
    boolean supports(MachineSpaceEnvelope envelope);

    List<CommissioningReport> commission(MachineSpaceEnvelope envelope);
}
