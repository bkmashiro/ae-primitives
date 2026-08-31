# AE Primitives

Compact AE2 machines for common world-interaction contraptions.

AE Primitives adds compact machines for common AE2 processing setups. Single-block machines connect directly to the network, use one channel and keep blocked outputs inside the machine.

## Machines

### ME Fortune Chamber

Processes block items through their block loot table with a Fortune III diamond pickaxe. It replaces the usual Formation Plane and enchanted Annihilation Plane ore-fortuning setup.

### ME Transformation Chamber

Runs AE2 `transform` recipes directly. Place the recipe ingredients in the three input slots; completed output is returned to the connected network or held in the output buffer.

### ME Resource Generator

Generates cobblestone without placing water, lava or blocks in the world. Its transparent front shows the two media and the current product.

### ME Crystal Growth Chamber

Grows Certus Quartz or Fluix crystals from their dust and sand. The crystal rises through the chamber as the cycle progresses.

### ME Compost Chamber

Turns vanilla compostable items into bone meal without Export Buses, hoppers or a world composter. It uses each item's vanilla composting value, so compacting the setup does not improve its long-run yield.

### World-processing machines

- **ME Concrete Curing Chamber** turns any vanilla concrete powder into its matching concrete block.
- **ME Soil Processor** wets dirt into mud with a water bucket, or slowly dries mud into clay.
- **ME Dripstone Reservoir** keeps a water or lava source bucket and slowly fills empty buckets at a deliberately low rate.
- **ME Oxidation Chamber** advances unwaxed copper blocks through one vanilla weathering stage at a time.
- **ME Crop Cultivator** grows wheat, carrots, potatoes or beetroot from retained planting stock and bone meal.
- **ME Tree Nursery** converts bone meal into a conservative four-log harvest while retaining the sapling stock.
- **ME Growth Rack** grows sugar cane, cactus, bamboo, kelp and vines slowly from a retained mother plant.
- **ME Apiary Chamber** needs a flower plus bottles or shears to produce honey bottles or honeycomb.
- **ME Batch Gate** releases stackable input to the network only in batches of eight.
- **ME Cooling Plate** quenches lava and ice into obsidian, or combines lava, blue ice and soul soil into basalt.

These machines replace block placement, random ticking and small entity contraptions; they do not multiply the inputs of ordinary crafting or processing recipes.

### ME Resonance Foundry

A 3×2×3 multiblock for high-throughput AE2 transform recipes. Its four coil columns run up to four transformations per cycle through one controller and shared inventory.

Build it with the controller in the middle of the front edge. The four corners are two-block Resonance Coil columns, the two center blocks are Resonance Cores, and every remaining position is Resonance Casing. The foundry reacts immediately when a required part is added or removed.

## Using the machines

1. Connect the machine to a powered AE network. Each machine uses one channel.
2. Feed inputs from any side or with a Pattern Provider.
3. Right-click the machine to inspect its input, output and upgrade slots.
4. Processing machines accept AE2 Speed Cards. Most accept four; passive generators and the Resonance Foundry accept two. Each card doubles speed, while idle power rises with the square of that multiplier.
5. Outputs are inserted into the same AE network. If the network cannot accept them, they remain in the machine.

Both client and server need AE Primitives installed.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.x
- Applied Energistics 2 19.2.x
- GuideME
- Java 21

## Build

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
./gradlew verifyAll
```

## Visual gallery

Export the gallery and one PNG per machine without opening a browser window:

```bash
./gradlew exportVisualGallery
```

Images are written to `build/visual-gallery/images/`. The exporter runs its renderer headlessly and exits when the PNGs are ready.

For interactive inspection, generate `build/visual-gallery/index.html` with `./gradlew visualGallery` and open it manually. Use these previews for fast geometry, UV and texture review; Minecraft remains the final check for lighting, transparency and dynamic contents.

Machine models can also be generated from the repository's tree-shaped CSG and procedural texture format. See [Machine asset pipeline](docs/machine-assets.md).

During a visual-harness client run, regenerate changed assets and call `reload-resources`; block models, textures and declarative animation tracks update without restarting Minecraft. Java renderer changes still require a client restart.

## License

[MIT](LICENSE)
