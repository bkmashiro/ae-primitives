# Product ideas

This file records product directions that are worth retaining but are not the current execution pointer. `ROADMAP.md` remains authoritative for implementation order.

## Retained

### Selectable physical-quantity display

Provide a small ME display whose metric is chosen by the player. It must not hard-code a fixed dashboard or assume the current set of integrations is complete.

- Metric providers are registered by Core or optional extensions.
- A display presents one selected quantity clearly, with its own unit, range and warning thresholds.
- Initial candidates include pressure, stored air, volume, RPM, stress capacity, stress demand, FE throughput, mana, heat and lane state.
- New integrations can add metrics without changing the display block or adding optional dependencies to Core.
- The visual form may vary by metric, such as a needle, bar, waveform or discrete state, while retaining one readable information hierarchy.

### Crafting autopsy

Explain a failed or stalled crafting job as a causal chain that ends at the concrete blocked machine, resource, tool, output or external port. Prefer snapshots and event-driven state over continuous scanning.

### Spatial factory aquarium

Render installed machine-space components as readable miniature host-mod machines behind glass. Each lane should visibly stop at its real processing or blocked stage instead of becoming a generic red indicator.

### ME flight recorder

Keep a bounded event-driven history of lane reservations, resource transitions, completion, pending output and reload recovery. It should serve both player diagnosis and focused development evidence without becoming a database or permanent trace system.

### Factory orchestra easter egg

A restrained, opt-in or rare easter egg may synchronize subtle machine sounds across resource domains. It must never become the normal feedback path, must be easy to disable and must not make ordinary factories noisy.

## Not planned

### Production programs

Do not add repeat/until/fallback/budget control structures. They do not currently provide enough value over AE crafting and sequence patterns to justify a new programming surface.
