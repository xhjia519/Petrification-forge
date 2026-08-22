# Petrification Mod

## Introduction
The Petrification Mod is a Minecraft Forge-based mod that allows players to transform mobs (as well as themselves) into exquisite statue blocks. The mod supports various materials (such as stone, iron, copper, etc.) and can fully preserve the visual state of the entity, including equipment, pose, variants, and health points. Players can also restore statues back to their original entities through various methods.

## Features
*   **Entity Petrification**: Use special potions to turn entities within range into static statues.
*   **Material System**: Supports 6 materials: Stone, Iron, Copper, Gold, Diamond, Netherite.
*   **Dynamic Oxidation**: Copper statues undergo oxidation color change over time (can be adjusted or disabled via configuration).
*   **Restoration Mechanism**: Provides restoration potions, restoration powder, and honeycomb to restore statues.
*   **Command Support**: Administrators can generate statue blocks or statue items via commands.
*   **High-Definition Rendering**: Custom rendering logic to precisely restore entity equipment, status, and animations.
*   **Data Saving**: Statues save almost all NBT data of the entity, including mob custom names, village information, etc.

## Installation Guide
1.  Confirm **Minecraft Forge 1.20.1** version is installed.
2.  Download the corresponding JAR file for the mod.
3.  Place the downloaded JAR file into the `mods` folder in the game directory.
4.  Launch the game; the mod will be visible in the menu.

## Configuration Instructions
After installing the mod, a `config/petrification-common.toml` configuration file will be generated automatically, mainly containing the following settings:
*   **potionRangeMultiplier**: The effect range multiplier for petrification potions.
*   **statueBlockHardness**: The destruction hardness of the statue block.
*   **enableOxidation**: Whether to enable the oxidation feature for copper statues.
*   **oxidationRate**: The oxidation speed for copper statues.
*   **allowCreativeMaterialization**: Whether to allow petrifying players in creative mode.
*   **allowOpMaterialization**: Whether to allow petrifying administrator (OP) players.
*   **statueRenderDistance**: The client-side render distance for statues.

## Commands & Gameplay
### Commands
*   `/statue give <material> <entity_type>`: Generates a statue item of a specified material (can be placed or held).
*   `/statue setblock <x> <y> <z> <material> <entity_type>`: Directly generates a statue block in the world.

### Items & Effects
*   **Materialization Potions**: There are 6 materials, each corresponding to Instant, Splash, and Lingering potions.
*   **Restoration Potion**: Instantly restores all statues within radius.
*   **Restoration Powder**: Hold and right-click a statue to restore it.
*   **Honeycomb**: Use on copper statues to wax them, preventing oxidation.

## Crafting Recipes
The mod adds custom crafting recipes:
*   **Statue Decompose**: Converts placed statue blocks back into more basic item forms (or decomposes into the constituent statue items).
*   **Statue Compose**: Used to craft or process specific statue items.

## License
This project follows the open-source license listed in `LICENSE.txt`.