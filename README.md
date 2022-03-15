# ModLoaderPlugin

[![Build status](https://github.com/Xpdustry/ModLoaderPlugin/actions/workflows/build.yml/badge.svg?branch=master&event=push)](https://github.com/Xpdustry/ModLoaderPlugin/actions/workflows/build.yml)
[![Mindustry 6.0 | 7.0 ](https://img.shields.io/badge/Mindustry-6.0%20%7C%207.0-ffd37f)](https://github.com/Anuken/Mindustry/releases)
[![Xpdustry latest](https://repo.xpdustry.fr/api/badge/latest/releases/fr/xpdustry/mod-loader-plugin?color=00FFFF&name=ModLoaderPlugin&prefix=v)](https://github.com/Xpdustry/ModLoaderPlugin/releases)

## Description

A simple Mindustry plugin to enable jvm mod/plugin dependencies for V6 and V7 up to v135.

To use it, put your mods/plugins in a directory named `./mod-loader`instead of `./config/mods` (except `mod-loader`) and enjoy.

## Building

- `./gradlew jar` for a simple jar that contains only the plugin code.

- `./gradlew shadowJar` for a fatJar that contains the plugin and its dependencies (use this for your server).

## Testing 

- `./gradlew runMindustryClient`: Run Mindustry in desktop with the plugin.

- `./gradlew runMindustryServer`: Run Mindustry in a server with the plugin.
