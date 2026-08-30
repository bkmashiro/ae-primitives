# AE Primitives

Compact AE2 machines for common world-interaction contraptions.

AE Primitives adds three single-block machines that connect directly to an AE network, use one channel, draw AE power and keep blocked outputs safely inside the machine.

## Machines

### ME Fortune Chamber

Processes block items through their block loot table with a Fortune III diamond pickaxe. It replaces the usual Formation Plane and enchanted Annihilation Plane ore-fortuning setup.

### ME Transformation Chamber

Runs AE2 `transform` recipes directly. Place the recipe ingredients in the three input slots; completed output is returned to the connected network or held in the output buffer.

### ME Resource Generator

Generates cobblestone without placing water, lava or blocks in the world. Its transparent front shows the two media and the current product.

## Using the machines

1. Connect the machine to a powered AE network. Each machine uses one channel.
2. Feed inputs from any side or with a Pattern Provider.
3. Right-click the machine to inspect its input and output buffers.
4. Outputs are inserted into the same AE network. If the network cannot accept them, they remain in the machine.

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

## License

[MIT](LICENSE)
