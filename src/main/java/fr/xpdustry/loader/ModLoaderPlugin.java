package fr.xpdustry.loader;

import arc.files.Fi;
import arc.files.ZipFi;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.OrderedSet;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import arc.util.serialization.Json;
import arc.util.serialization.Jval;
import arc.util.serialization.Jval.Jformat;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.Locale;
import java.util.regex.Pattern;
import mindustry.Vars;
import mindustry.core.Version;
import mindustry.mod.Mod;
import mindustry.mod.Mods.LoadedMod;
import mindustry.mod.Mods.ModMeta;
import mindustry.mod.Plugin;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public final class ModLoaderPlugin extends Plugin {

  public static final Fi MOD_LOADER_DIRECTORY = new Fi("./mod-loader");
  private static final Pattern MOD_JSON_PATTERN = Pattern.compile("^(mod|plugin)\\.h?json$");

  private static final Json json = new Json();
  private static final ModClassLoader sharedClassLoader = new ModClassLoader(ModLoaderPlugin.class.getClassLoader());

  public ModLoaderPlugin() {
    MOD_LOADER_DIRECTORY.mkdirs();

    final var files = Seq.with(MOD_LOADER_DIRECTORY.list()).filter(f ->
      f.extension().matches("jar|zip") || (f.isDirectory() && (f.child("mod.json").exists() || f.child("mod.hjson").exists()))
    );

    for (final var file : resolveDependencies(files)) {
      try {
        Log.debug("MOD-LOADER: Loading mod @", file);
        final var mod = loadMod(file);
        Vars.mods.list().add(mod);
      } catch (final Exception e) {
        Log.err("MOD-LOADER: Failed to load " + file, e);
      }
    }
  }

  public static @NotNull ModClassLoader getSharedClassLoader() {
    return sharedClassLoader;
  }

  /**
   * Tries to find the config file of a mod/plugin.
   */
  private static @NotNull ModMeta findMeta(final @NotNull Fi directory) throws IOException {
    final var file = Seq.with(directory.list()).find(f -> MOD_JSON_PATTERN.matcher(f.name()).find());
    if (file != null) {
      final var meta = json.fromJson(ModMeta.class, Jval.read(file.readString()).toString(Jformat.plain));
      meta.cleanup();
      return meta;
    } else {
      throw new FileNotFoundException("Can't find [plugin|mod].[h]json in " + directory);
    }
  }

  private static @NotNull Fi getModRoot(final @NotNull Fi file) {
    var root = file.isDirectory() ? file : new ZipFi(file);
    return root.list().length == 1 && root.list()[0].isDirectory() ? root.list()[0] : root;
  }

  private static @NotNull LoadedMod loadMod(final @NotNull Fi file) throws Exception {
    Time.mark();

    final var root = getModRoot(file);
    final var meta = findMeta(root);

    final var camelized = meta.name.replace(" ", "");
    final var mainClass = meta.main == null ? camelized.toLowerCase(Locale.ROOT) + "." + camelized + "Mod" : meta.main;
    final var baseName = meta.name.toLowerCase(Locale.ROOT).replace(" ", "-");

    var mainFile = root;
    final var path = (mainClass.replace('.', '/') + ".class").split("/", -1);
    for (final var str : path) {
      if (!str.isEmpty()) mainFile = mainFile.child(str);
    }

    if ((mainFile.exists() || meta.java) && Version.isAtLeast(meta.minGameVersion) && (meta.getMinMajor() >= 105 || Vars.headless)) {
      final var loader = loadJar(file);
      sharedClassLoader.addChild(loader);
      final var main = Class.forName(mainClass, true, loader);

      //detect mods that incorrectly package mindustry in the jar
      if (main.getSuperclass().getName().matches("mindustry\\.mod\\.(Plugin|Mod)") && (main.getSuperclass().getClassLoader() != Mod.class.getClassLoader())) {
        throw new ModLoaderException("""
          This mod/plugin has loaded Mindustry dependencies from its own class loader.
          You are incorrectly including Mindustry dependencies in the mod/plugin JAR.
          Make sure Mindustry is declared as `compileOnly` in Gradle, and that the JAR is created with `runtimeClasspath`!
          """
        );
      }

      final var mainMod = (Mod) main.getDeclaredConstructor().newInstance();
      if (mainMod instanceof Plugin) meta.hidden = true;

      if (meta.version != null) {
        int line = meta.version.indexOf('\n');
        if (line != -1) meta.version = meta.version.substring(0, line);
      }

      Log.debug("MOD-LOADER: Loaded mod '@' in @ms", meta.name, Time.elapsed());
      return new LoadedMod(null, file, mainMod, sharedClassLoader, meta);
    } else {
      throw new ModLoaderException("Missing main class or outdated version.");
    }
  }

  private static @NotNull ClassLoader loadJar(final @NotNull Fi jar) throws IOException {
    return new URLClassLoader(new URL[] {jar.file().toURI().toURL()}, ModLoaderPlugin.sharedClassLoader) {
      @Override
      protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // Check for loaded state
        var loadedClass = findLoadedClass(name);
        if (loadedClass == null) {
          try {
            // try to load own class first
            loadedClass = findClass(name);
          } catch (final ClassNotFoundException e) {
            // use parent if not found
            return getParent().loadClass(name);
          }
        }

        if (resolve) resolveClass(loadedClass);
        return loadedClass;
      }
    };
  }

  /**
   * Resolves the loading order of a list of mods/plugins using their internal names. It also skips non-mods files or folders.
   */
  private static @NotNull Seq<Fi> resolveDependencies(final @NotNull Seq<Fi> files) {
    final ObjectMap<String, Fi> fileMapping = new ObjectMap<>();
    final ObjectMap<String, Seq<String>> dependencies = new ObjectMap<>();

    for (final var file : files) {
      final var zip = getModRoot(file);

      try {
        final var meta = findMeta(zip);
        dependencies.put(meta.name, meta.dependencies);
        fileMapping.put(meta.name, file);
      } catch (final Exception e) {
        Log.err("MOD-LOADER: Failed to load the mod metadata of " + zip, e);
      }
    }

    final ObjectSet<String> visited = new ObjectSet<>();
    final OrderedSet<String> ordered = new OrderedSet<>();

    for (String modName : dependencies.keys()) {
      if (!ordered.contains(modName)) {
        // Adds the loaded mods at the beginning of the list
        ordered.add(modName, 0);
        resolveDependencies(modName, dependencies, ordered, visited);
        visited.clear();
      }
    }

    Log.debug("MOD-LOADER: Finished dependency resolution");
    for (final var missingMod : dependencies.keys()) {
      if (!ordered.contains(missingMod)) {
        Log.debug("MOD-LOADER: @ has missing or circular dependencies.", missingMod);
      }
    }

    final var resolved = ordered.orderedItems().map(fileMapping::get);
    // Manual inversion cauz funny V7 bugs...
    final var tempList = resolved.list();
    Collections.reverse(tempList);
    return Seq.with(tempList);
  }

  /**
   * Recursive search of dependencies.
   */
  private static void resolveDependencies(
    final @NotNull String modName,
    final @NotNull ObjectMap<String, Seq<String>> dependencies,
    final @NotNull OrderedSet<String> ordered,
    final @NotNull ObjectSet<String> visited
  ) {
    visited.add(modName);

    for (String dependency : dependencies.get(modName)) {
      // Checks if the dependency tree isn't circular and that the dependency is not missing
      if (!visited.contains(dependency) && dependencies.containsKey(dependency)) {
        // Skips if the dependency was already explored in a separate tree
        if (ordered.contains(dependency)) continue;
        ordered.add(dependency);
        resolveDependencies(dependency, dependencies, ordered, visited);
      }
    }
  }
}
