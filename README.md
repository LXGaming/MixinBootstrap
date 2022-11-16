# MixinBootstrap

[![License](https://img.shields.io/github/license/LXGaming/MixinBootstrap?label=License&cacheSeconds=86400)](https://github.com/LXGaming/MixinBootstrap/blob/master/LICENSE)

## This is an unofficial method of loading Mixin and as such do not expect any support.

## Mixin is available in Forge 1.15.2-31.2.44+ & 1.16.1-32.0.72+ (MixinBootstrap is no longer required).

**MixinBootstrap** is a **temporary** way of booting [Mixin](https://github.com/SpongePowered/Mixin) in a [MinecraftForge](https://github.com/MinecraftForge/MinecraftForge) production environment.

## Usage
Simply drop the `MixinBootstrap-<VERSION>.jar` into the [MinecraftForge](https://github.com/MinecraftForge/MinecraftForge) mods folder

### Development
Add the `org.spongepowered:mixin:0.8.5` dependency to your `build.gradle`, If you want to depend on MixinBootstrap then simply don't compile Mixin into your mod.

## Download
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/mixinbootstrap)
- [GitHub](https://github.com/LXGaming/MixinBootstrap/releases)
- [Modrinth](https://modrinth.com/mod/mixinbootstrap)

## Compatibility
| Version | Support | Reason |
| :-----: | :-----: | :----: |
| 1.12.2 | :heavy_check_mark: | - |
| 1.13.x | :x: | Not supported due to ModLauncher version |
| 1.14.x | :warning: | Only Forge 28.1.45 or later |
| 1.15.x | :heavy_check_mark: | - |
| 1.16.x | :heavy_check_mark: | - |
| 1.17.x | :heavy_check_mark: | - |

:heavy_check_mark: - Full Support | :warning: - Partial Support | :x: - No Support

## License
MixinBootstrap is licensed under the [Apache 2.0](https://github.com/LXGaming/MixinBootstrap/blob/master/LICENSE) license.
