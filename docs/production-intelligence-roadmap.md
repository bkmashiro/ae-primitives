# Production intelligence roadmap

## Goal

Turn AE Primitives' existing operation, sequence, machine-space and diagnostic state into a small set of tools that answer four questions:

1. What can this network do?
2. What will this job cost and where will it bottleneck?
3. Why is this job or machine blocked?
4. Where is the responsible machine, port or lane?

This roadmap begins after the current PneumaticCraft slice proves direct pressure consumption from ME storage. It does not replace the implementation order in `ROADMAP.md` until that pointer advances.

## Design rules

- Observation is read-only. Opening a screen, rendering an overlay or producing a forecast must never reserve, consume or create resources.
- Optional extensions own their metrics and machine knowledge. Core stores only small provider contracts and immutable snapshots.
- Stable systems are pull-based and revisioned. Providers mark changed state; displays and tools read a snapshot when opened, selected or invalidated. There is no global per-tick network scan.
- Reuse existing operation and sequence data. Do not build a second recipe graph beside `SequenceRuntime`, `SequenceCompiler` and `ProcessDiagnosticSnapshot`.
- Unknown facts remain unknown. Forecasts show bounded estimates and explicit external constraints rather than fabricated precision.
- Histories are bounded ring buffers of meaningful state transitions, not databases or tick traces.
- Commissioning is a pure deterministic virtual execution. It never runs a real machine, touches ME storage, emits an output item or invokes native world behavior.
- Player surfaces remain separate. One shared read model may feed a display, analyzer, lens and factory screen without merging them into one universal UI.

## Shared architecture

Add contracts only when their first consumer lands.

### Metric source

A Core registry exposes selectable physical quantities through immutable samples:

```text
metric id + source identity
label + unit
current value + optional range
normal/warning/critical state
revision
```

A metric provider resolves a nearby machine, factory lane, network aggregate or extension resource. Core handles selection, persistence, formatting hooks and display synchronization. PneumaticCraft, Create, Powah, Botania and Farmer's Delight register their own providers.

### Machine insight

A compact read model describes an operation provider or packaged machine:

```text
identity and supported operations
exact deterministic inputs/outputs when known
external resource contracts
throughput and parallel capacity
current blocked reason and responsible target
snapshot revision
```

Do not add these fields to `VirtualMachineLaneExecutor`. Execution and inspection have different safety boundaries. Use a separate provider registry keyed by a live block entity or `MachineSpaceEnvelope`, while reusing existing operation specifications and lane state.

### Diagnostic events

Later phases add a small event record only for state transitions already known by the owner:

```text
network time, job/lane identity, event kind, reason, target
```

Machine and lane owners emit transitions such as reserved, started, waiting resource, completed and blocked output. Consumers never infer history by polling.

## Phase 1: selectable physical-quantity display

**Status: complete.** Core now provides the watched metric contract and cable-attached display. PneumaticCraft registers pressure, stored-air, volume and maximum-tier metrics. The display persists one selection, samples only after selection or watched-storage changes, and synchronizes only the visible immutable sample.

Deliver the next PneumaticCraft slice as the first vertical use of the metric contract.

### Product

- One cable-attached display shows one player-selected metric.
- Initial extension metrics are pressure, stored air, volume and maximum supported pressure tier.
- The display supports numeric, bar, needle and discrete-state presentations without encoding extension-specific logic.
- Selection survives reload. A missing provider produces a stable unavailable state rather than silently switching metrics.

### Implementation shape

- Add a Core metric provider registry and immutable sample type.
- Register the display block, block entity, menu and renderer in Core.
- Register PneumaticCraft metric providers in `pneumaticcraft-extension`.
- Synchronize only the selected sample when its revision changes or the player changes selection.

### Acceptance

- A display can switch among pressure metrics and restore the selection after reload.
- Removing the source or extension leaves the display safely unavailable.
- A stable metric causes no repeated network scan or block update.
- Core release JAR contains no PneumaticCraft classes.

## Phase 2: capability inspector and pattern laboratory

Combine resource contract cards and the pattern laboratory into one inspection surface rather than two overlapping tools.

**Status: complete.** The ME Process Analyzer now inspects live machines, machine-space components, operation patterns and sequence patterns through immutable server snapshots. Core owns the compact insight contract and UI; Create registers its operation, distinct stress and minimum-speed requirements, catalyst contract and parallel capacity without exposing execution hooks to the inspector.

### Product

- Inspect a live machine, machine-space component, operation pattern or sequence pattern.
- Show supported operations, exact tools/catalysts/remainders, required physical resources, output bounds and available providers.
- Assemble or inspect a sequence using the existing operation graph.
- Compilation errors point to the exact step and missing capability.

### Implementation shape

- Extend the current diagnostic snapshot with immutable machine insight references.
- Reuse `SequenceRuntime`, `SequenceCompiler`, `OperationPatternSpec` and existing analyzer UI.
- Optional extensions describe resource contracts through the separate insight registry.
- Keep recipe import and graph compilation server-authoritative; the client receives a compact snapshot.

### Acceptance

- The inspector explains one Core operation, one Create sequence and one packaged extension machine without Core loading optional classes.
- Tools, catalysts, container remainders and external resource requirements remain distinguishable.
- Viewing or compiling the graph does not reserve inputs or mutate provider state.

## Phase 3: bounded Crafting Forecast

Status: implemented. Forecasts are immutable, revision-bound projections of the analyzer snapshot. They net exact intermediate outputs against later inputs, preserve alternative-input uncertainty, report the first structural bottleneck and a conservative parallel bound, and leave completion time explicitly unknown until a provider exposes measured throughput.

Add a forecast on top of the same compiled graph and machine insights.

### Product

Before submission, report:

- whether every deterministic step has a provider;
- known input and external-resource requirements;
- safe parallel capacity;
- the first structural bottleneck;
- a bounded completion estimate when throughput is known;
- probability information only where the source recipe exposes it.

### Engineering boundary

The forecast is analytical, not an execution simulator. It evaluates immutable snapshots and labels estimates as exact, bounded or unknown. Volatile external supply, shared Create stress and native-world Botania stages may reduce confidence but must not be guessed.

### Acceptance

- A deterministic sequence produces repeatable cost and bottleneck results from the same snapshot.
- Changing provider capacity invalidates the forecast revision.
- Unknown external throughput is shown as unknown and never as zero or an invented duration.
- Forecasting leaves inventories, energy, pressure, mana and jobs unchanged.

## Phase 4: deterministic virtual commissioning

**Implemented.** Core now commissions supported deterministic primitive machines and packaged machine-space components from copied configuration. A bounded synthetic input ledger produces immutable resource descriptions only; probabilistic and world-native machines are rejected before planning. The Process Analyzer renders those descriptions without exposing production handles or collectible stacks.

Commissioning validates a machine configuration without executing a real production path.

### Product

- A player commissions a supported deterministic machine or machine-space component against synthetic in-memory inputs.
- The result reports recipe resolution, exact consumption, exact output shape, resource contract compatibility and configuration errors.
- Probabilistic recipes, native-world processes and machines with external side effects are explicitly unsupported by this mode.

### Safety model

Introduce a separate deterministic commissioning provider. It receives copied configuration and an isolated bounded virtual inventory. Its pure planning function returns a value object containing expected consumption, output and requirements.

It must not:

- call `VirtualMachineLaneExecutor.LanePlan.complete()`;
- access or mutate ME storage;
- use a world-backed inventory or block entity;
- create a collectible output stack;
- consume energy, pressure, stress, mana or heat;
- invoke random rolls, fake players or native structure behavior.

The UI renders the returned description only. A failed or closed commissioning session discards all temporary state.

### Acceptance

- Repeating a commissioning request with the same input produces the same result.
- Tests assert that source inventories and all resource stores are byte-for-byte unchanged.
- No output item enters a player, world, machine or ME inventory.
- Unsupported probabilistic or world-native machines are rejected before virtual execution.
- Reloading or disconnecting cannot recover temporary commissioning state as items.

## Phase 5: flight recorder and crafting autopsy

Build the recorder only when the autopsy is ready to consume it.

### Product

- Each relevant machine or factory keeps a small bounded history of state transitions.
- A stalled or failed job is explained as a causal chain ending at a concrete resource, tool, output slot, port or machine.
- The current process analyzer becomes the primary UI instead of creating another diagnostics application.

### Implementation shape

- Add owner-local ring buffers with strict entry and serialized-size limits.
- Emit events at existing reservation, start, wait, completion, pending-output and reload-recovery transitions.
- Link causes by stable job/lane identifiers; do not retain whole item stacks or recipe graphs in every event.
- Build the causal explanation on request from the current snapshot plus recent events.

### Acceptance

- Output blockage, missing external resource and reload recovery each produce a short correct causal chain.
- Stable operation emits no repeated events.
- Save data remains bounded after long operation.
- Pruning history may reduce detail but never changes machine behavior.

## Phase 6: ME Network Lens

Expose the same snapshot and causal target in the world.

### Product

- An active lens highlights the inspected machine, its bound spatial blocks, responsible resource port and current factory lane.
- A diagnostic result can focus the exact world target when one exists.
- Overlays are short-lived and requested by one player; there is no permanent server broadcast.

### Acceptance

- Spatial ownership, missing port and blocked machine targets resolve correctly.
- Unloaded or virtual-only targets degrade to textual identity.
- The lens does not scan unrelated chunks or reveal hidden network contents to unauthorized players.

## Phase 7: spatial factory visualization

Use the established insight state to make the heterogeneous factory legible without inventing a second runtime.

### Product

- Render machine-space components as host-mod-specific miniature machines behind glass.
- Miniatures reflect active, waiting and blocked stages from the authoritative lane snapshot.
- A component pedestal may inspect configuration and bounded lifetime counters using the same model.
- Blueprint ghosts may visualize required port direction and structure conflicts.

### Boundary

Rendering consumes snapshots only. It never derives process state from animation, and animation completion never drives production completion.

### Acceptance

- At least one Core and two optional-extension lanes are visually distinct and match their real status.
- Blocked output and missing resource are visually distinguishable.
- Rendering remains client-only and does not add optional classes to Core.

## Deferred polish

- Small non-persistent wear or residual-glow overlays may reflect coarse recent machine state.
- A quiet, default-off Factory Orchestra may exist as an easter egg after ordinary sound feedback is complete.
- These are polish, not execution pointers, and must not add maintenance mechanics or continuous simulation.

## Explicit non-goals

- No repeat/until/fallback/budget programming language.
- No universal fixed dashboard containing every physical quantity.
- No real-machine commissioning run disguised as a test.
- No probabilistic virtual commissioning.
- No global tick trace, permanent telemetry database or cross-chunk scanner.
- No conversion of pressure, stress, FE, mana or heat into a common hidden resource.
- No new CI workflows.

## Delivery discipline

Each phase is a complete vertical slice: provider contract, one real consumer, focused unit tests, the relevant disposable-world GameTests, build/JAR boundary checks and a representative visual or interaction check. Do not land registries or generalized models without the phase's concrete consumer. Advance only after the previous slice has a stable serialized form and no optional dependency leakage.
