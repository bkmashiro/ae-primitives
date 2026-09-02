# AE Primitives roadmap

## Direction

AE Primitives turns common automation structures into AE-managed processing capabilities without erasing the source mod's costs or resource model. Machines may reduce BlockEntity count and routing work, but must preserve recipe inputs, probabilistic outputs, tools, catalysts, energy and throughput limits.

The repository remains a lightweight monorepo:

- `ae-primitives`: shared AE machines, patterns, diagnostics and spatial parallelism.
- `create-extension`: Create recipes, stress-driven machines and sequenced assembly support.
- Optional extensions currently cover Farmer's Delight, Botania, Powah and PneumaticCraft.
- The core mod must not acquire optional mod dependencies.

## Current execution pointer

The original spatial-factory roadmap is complete. PneumaticCraft now has tiered compressed-air cells for normal AE2 drives, bounded pressure import/export ports and an ME Pneumatic Assembly Chamber that consumes the matching air domain directly from its grid. The selectable physical-quantity display, capability inspector and bounded crafting forecast are complete: optional extensions register watched metrics and immutable machine insights while Core owns selection, snapshots, analytical forecasts and rendering. The current pointer is Phase 4 of the [production intelligence roadmap](docs/production-intelligence-roadmap.md): deterministic virtual commissioning with isolated copied state and no access to real storage, resources, world state or collectible outputs.

The production intelligence roadmap incrementally reuses the current operation, sequence, machine-space and diagnostic models for selectable metrics, capability inspection, bounded forecasts, deterministic virtual commissioning, causal diagnostics and world overlays.

Pressure rules for this extension:

- Basic and reinforced air remain separate 5 bar and 20 bar storage domains.
- Stored air and cell volume determine pressure; adding cells never creates free pressure.
- Compatible AE machines draw from matching cells anywhere on their ME network.
- Import/export ports are optional tube-network escape hatches, not mandatory per-machine plumbing.
- Ports safety-vent before critical pressure. ME Drives and storage cells never explode; downstream PneumaticCraft machines retain native behavior.

## 1. Spatial parallel blocks

A spatial parallel block attaches to exactly one machine. Its facing side identifies the owner, so a block touching two machines can only serve the machine it points at.

### Behavior

- Basic, advanced and ultimate variants serve matching machine tiers.
- Multiple valid blocks may contribute capacity to one machine, subject to the tier limit.
- The main machine owns configuration, inventory, progress, scheduling and error state.
- Parallel blocks do not tick. Placement, removal and rotation invalidate the owner's cached capacity through neighbor updates.
- A missing, incompatible or wrongly oriented owner leaves the parallel block idle.
- The player-facing multiplier is implemented as independent bounded lanes, not by multiplying one rolled result.
- Each lane reserves its own inputs, checks aggregate output capacity and rolls probabilistic outputs independently.
- Energy, Create stress, tool damage and consumable use scale with active lanes.
- Catalysts and environmental configuration are inherited from the main machine.

Initial limits and recipe costs should be conservative and easy to tune. Corresponding machine frames and AE2 spatial components pay for the virtual copies.

### Shared contract

Define a small core-side host contract so optional extensions can participate without the core importing them. A host declares:

- machine tier;
- maximum accepted parallel capacity;
- current cached capacity;
- a topology invalidation hook.

The spatial block only resolves the faced neighbor and notifies this contract. Each machine remains responsible for execution semantics.

### First hosts

- ME Kinetic Press
- ME Crushing Chamber
- ME Catalyst Processing Chamber

The machine UI and exterior status lights should show total and active lanes. The existing transparent window remains dedicated to the machine's material or catalyst.

### Acceptance

- A correctly oriented matching block increases throughput.
- A block cannot contribute to both adjacent machines.
- Wrong tier, wrong facing and detached blocks contribute nothing.
- Removing a block updates the owner without polling.
- Limited inputs and blocked outputs start only the number of safe lanes.
- Probabilistic recipes are rolled once per completed lane.
- Stress and energy use scale with active work.
- Core and Kinetics GameTests remain green and release JAR boundaries remain intact.

## 2. Complete the Create atomic-operation set

Build new machines on `KineticProcessBehavior` rather than adding kind-specific branches to the shared block entity.

### ME Basin Processor

- Execute Create Mixing and Compacting recipes.
- Render the real item and fluid contents in a transparent basin.
- Read Create heat from below.
- Permit data-defined third-party heat blocks where no common heat capability exists.
- An optional resistance card substitutes ME energy for heating only; the machine still requires rotational stress.
- Distinguish unheated, heated and superheated recipes.

### ME Filling Station

- Execute Create Filling and Emptying recipes against AE fluid storage.
- Preserve container capacity, components and remainders.
- Show the active fluid and item in the chamber.

### ME Deployer

- Execute Deploying recipes without a fake player.
- Preserve installed tool identity, durability, enchantments and recipe remainders.
- Expose a reusable tool-slot contract for later integrations.

### Secondary operations

Add sawing, milling and sandpaper polishing only after Basin, Filling and Deploying form a verified sequenced-assembly path. Milling and crushing remain separate capabilities because their costs and result pools differ.

### Acceptance

A real sequence such as press, deploy, fill and press can be imported, diagnosed and completed through AE-managed operation providers without manually routing intermediate items through world structures.

## 3. Homogeneous scaling before heterogeneous factories

Spatial parallel blocks are the normal scaling mechanism. They keep configuration local and visible while collapsing repeated identical machines into one ticking host.

Do not build a universal heterogeneous factory until the machine families and resource contracts are stable.

## 4. Optional integrations

### Farmer's Delight

Ship as a separate extension.

- ME Cooking Pot reuses heat, container remainder and basin rendering contracts.
- ME Cutting Board reuses the tool-slot contract and applies real knife durability.
- Recipes, heat sources and byproducts come from Farmer's Delight rather than duplicated tables.

### Botania

Ship stateful AE interfaces for real Botania structures rather than FE-powered replacements.

- Petal Apothecary interface
- Runic Altar interface
- Mana Pool and catalyst operation interface

AE manages material dispatch, stage transitions and result recovery. Mana and the original Botania structure remain authoritative.

### Powah

Provide an ME Energizing Table in a separate extension.

- Execute Powah Energizing recipes with original total FE costs.
- Accept FE from external power connections; do not silently convert AE power.
- Emitter modules crafted from real Energizing Rod tiers retain the corresponding transfer limits.
- Render the active item and energy-beam state.
- Spatial lanes require proportional emitter capacity and FE throughput.

### Excluded

Do not add Mekanism integration. Its existing Pattern Provider processing workflow is already sufficient for the intended use.

## 5. Machine space components and heterogeneous factory

This phase became active after the Create, Farmer's Delight and Powah resource contracts were implemented. The first controller now persists four distinct lanes and schedules their operations without materializing world block entities. Its tabbed four-lane menu installs components directly, exposes sixteen isolated inputs and seven outputs per lane, and reports lane identity, progress and blocked state. A core-side executor registry lets optional extensions own virtual execution without leaking their classes into the Core JAR. Kinetics packages idle presses and crushers plus configured catalyst chambers and runs them through a dedicated kinetic factory port. Farmer's Delight packages idle ME Cooking Pots and executes real recipes through an explicit heat port while preserving containers and ingredient remainders. Powah packages idle ME Energizing Chambers with their emitter configuration and charges each lane through an explicit FE port with persisted paid energy. Botania packages idle Mana Pool, Runic Altar and Petal Apothecary Interfaces. Mana Pool lanes preserve native catalysts and exact mana cost; Runic lanes reserve concrete ingredients and livingrock into one exclusive real altar, persist opaque native stage state, respect the altar's native post-craft cooldown, and recover only their own output. Petal lanes reserve concrete petals and the seed reagent separately, stage petals in one exclusive water-filled real apothecary, persist the opaque native recipe across reload and feed the reserved reagent only after output capacity is available. Components remain locked while a lane owns inputs, progress, external stage state or pending output.

### Packaging model

A configured, idle and empty machine may be packed into a machine space component. Configuration such as catalysts, tools and upgrades is preserved; process inputs, outputs and active jobs prevent packaging. Unpacking restores the machine and configuration.

The original machine UI remains the configuration surface. The assembly table validates and packages machines but does not create a second dynamic editor.

### Factory model

A late-game multiblock hosts machine space components and schedules them as independent machine instances. Components retain their original resource contracts. The structure must expose the required ports instead of converting resources:

- ME energy
- Create kinetic stress
- FE
- Botania mana
- heat/environment

The factory UI installs components and reports status; configuration changes require unpacking the component. Spatially parallelized machines may be packaged only if their consumed parallel hardware is included atomically.

Proceed only when at least the Create, Farmer's Delight and Powah machine contracts demonstrate that this common representation is actually useful.

## Development rules

- Use disposable superflat development worlds for client and visual checks.
- Never use a real player save as a test fixture.
- Prefer focused GameTests and Minecraft Visual Harness evidence for changed behavior.
- Preserve independent output rolls and transactional input/output handling.
- Keep optional dependencies in their own Gradle modules and release JARs.
- Avoid polling when placement, neighbor, inventory or network events can invalidate cached state.
