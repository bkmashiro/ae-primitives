# AE Primitives: PneumaticCraft

Network-wide compressed-air storage and pressure I/O for PneumaticCraft: Repressurized.

## Compressed-air cells

- **Basic Compressed-Air Cell** stores one 5 bar pressure domain.
- **Reinforced Compressed-Air Cell** stores one 20 bar pressure domain.
- Both cells fit ordinary AE2 ME Drives and ME Chests, alongside item and fluid cells.
- Basic and reinforced air are separate AE key domains. A basic cell cannot accept reinforced air, so high-pressure machines cannot bypass their pressure tier.

Stored air and available cell volume determine bank pressure. More cells add both air capacity and virtual volume rather than creating free pressure.

## Pressure ports

- **Pressure Import Port** accepts air from a PneumaticCraft pressure network and stores it in matching cells on its ME network.
- **Pressure Export Port** withdraws matching air from ME storage and exposes a native PneumaticCraft air handler to adjacent tubes and machines.
- Basic ports are rated for 5 bar. Reinforced ports are rated for 20 bar.
- Transfers equalize toward the lower-pressure side at a bounded rate and consume AE power.

Ports use PneumaticCraft's native air handler and keep its pressure state across reloads. At their rating they safety-vent upward instead of damaging the ME Drive or its storage cells. Machines connected beyond an export port retain their own native pressure and failure behavior.

The ports are an escape hatch for external tube networks. AE Primitives machines that support PneumaticCraft draw from compatible cells anywhere on their ME network without requiring a tube on every machine.

## Physical Metric Display

The Core display attaches to an ME network and shows one selected metric. With this extension installed it can display basic or reinforced pressure, stored air, available volume and the maximum pressure tier. Storage watchers invalidate only the selected metric, so a stable display does not rescan the network every tick. The selection persists across reloads and remains visibly unavailable if its provider disappears.

## ME Pneumatic Assembly Chamber

- Install a Basic or Reinforced Pneumatic Assembly Head by using it on the chamber. Shift-use the empty hand to remove it.
- The chamber executes PneumaticCraft pressure-chamber recipes whose required positive pressure maps exactly to the installed head's 5 bar or 20 bar domain.
- Before committing, it crafts against an isolated inventory copy, proves output capacity, verifies that the selected air bank remains above recipe pressure after transfer costs, and simulates the powered extraction.
- Input and output crossings each cost PneumaticCraft's native pressure-chamber interface charge of 1000 mL per item. Inputs and outputs are committed only after the matching ME air extraction succeeds.
- Vacuum recipes, recipes above 20 bar and native world-dependent behavior are intentionally left to PneumaticCraft machinery.

## Requirements

- [AE Primitives](../README.md)
- PneumaticCraft: Repressurized 8.2.x
- Applied Energistics 2 19.2.x
- Minecraft 1.21.1 and NeoForge 21.1

Both client and server need this extension.
