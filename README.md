# mod-loader

[![Build status](https://github.com/xpdustry/mod-loader/actions/workflows/build.yml/badge.svg?branch=master&event=push)](https://github.com/xpdustry/mod-loader/actions/workflows/build.yml)
[![Mindustry 6.0 | 7.0 ](https://img.shields.io/badge/Mindustry-6.0%20%7C%207.0-ffd37f)](https://github.com/Anuken/Mindustry/releases)
[![Xpdustry latest](https://maven.xpdustry.com/api/badge/latest/releases/fr/xpdustry/mod-loader?color=00FFFF&name=mod-loader&prefix=v)](https://maven.xpdustry.com/#/releases/fr/xpdustry/mod-loader/)

## Description

> **Warning:** This repository is no longer maintained since modern versions of Mindustry have proper support for mod dependencies.

A simple Mindustry plugin to enable jvm mod/plugin dependencies for V6 and V7 below v136.

To use it, put your mods/plugins in a directory named `./mod-loader` instead of `./config/mods` (except `mod-loader`) and enjoy.

## Building

- `./gradlew jar` for a simple jar that contains only the plugin code.

- `./gradlew shadowJar` for a fatJar that contains the plugin and its dependencies (use this for your server).

## Testing 

- `./gradlew runMindustryClient`: Run Mindustry in desktop with the plugin.

- `./gradlew runMindustryServer`: Run Mindustry in a server with the plugin.

### Nice tips

- Your file tree should look like this
  
  ```
  - config/
    - mods/
      - ModLoaderPlugin.jar
      - ModThatDoesNotHaveDependencies.jar
      - ...
    - ...
  - mod-loader/
    - ModThatHasDependencies.jar
    - DependencyOfTheAboveMod.jar
    - ...
  - server.jar
  - ...
  ```

- It's perfecly fine to put regular mods in the `mod-loader` directory.
