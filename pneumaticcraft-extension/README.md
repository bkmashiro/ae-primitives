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

The ports are an escape hatch for external tube networks. AE Primitives machines that support PneumaticCraft will draw from compatible cells anywhere on their ME network without requiring a tube on every machine.

## Requirements

- [AE Primitives](../README.md)
- PneumaticCraft: Repressurized 8.2.x
- Applied Energistics 2 19.2.x
- Minecraft 1.21.1 and NeoForge 21.1

Both client and server need this extension.
