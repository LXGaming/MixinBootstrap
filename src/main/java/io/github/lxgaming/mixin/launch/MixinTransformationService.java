/*
 * Copyright 2019-2021 Alex Thomson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.lxgaming.mixin.launch;

import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;
import io.github.lxgaming.classloader.ClassLoaderUtils;

import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MixinTransformationService extends CommonTransformationService {
    
    @Override
    public void initialize(IEnvironment environment) {
        ensureTransformerExclusion();
        
        super.initialize(environment);
    }

    @Override
    public void beginScanning(IEnvironment environment) {
        this.transformationServices.forEach(transformationService -> transformationService.beginScanning(environment));
    }
    
    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) throws IncompatibleEnvironmentException {
        if (env.findLaunchPlugin("mixin").isPresent()) {
            LOGGER.debug("MixinLauncherPlugin detected");
            return;
        }
        
        if (this.launchPluginServices == null) {
            throw new IncompatibleEnvironmentException("LaunchPluginServices is unavailable");
        }
        
        if (!ITransformationService.class.getPackage().isCompatibleWith("4.0") || ITransformationService.class.getPackage().isCompatibleWith("8.0")) {
            LOGGER.error("-------------------------[ ERROR ]-------------------------");
            LOGGER.error("Mixin is not compatible with ModLauncher v" + ITransformationService.class.getPackage().getImplementationVersion());
            LOGGER.error("Ensure you are running Forge v28.1.45 ~ v37.0.0");
            LOGGER.error("-------------------------[ ERROR ]-------------------------");
            throw new IncompatibleEnvironmentException("Incompatibility with ModLauncher");
        }

        ClassLoader contextCL = Thread.currentThread().getContextClassLoader();
        try {
            Map<String, String> map = new HashMap<>();
            map.put("create", "true");
            FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" + this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI()), map);
            Files.list(fs.getPath("META-INF", "libraries"))
                .filter(p -> !Files.isDirectory(p) && p.getFileName().toString().endsWith(".jar"))
                .forEach(p -> {
                    try {
                        ClassLoaderUtils.appendToClassPath(contextCL, Files.copy(p, Files.createTempFile("", ".jar"), StandardCopyOption.REPLACE_EXISTING).toUri().toURL());
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                });

            ClassLoaderUtils.appendToClassPath(contextCL, getClass().getProtectionDomain().getCodeSource().getLocation().toURI().toURL());
        } catch (Throwable ex) {
            LOGGER.error("Encountered an error while attempting to append to the class path", ex);
            throw new IncompatibleEnvironmentException("Failed to append to the class path");
        }
        
        // Mixin
        // - Plugin Service
        registerLaunchPluginService("org.spongepowered.asm.launch.MixinLaunchPluginLegacy", contextCL);
        
        // - Transformation Service
        // This cannot be loaded by the ServiceLoader as it will load classes under the wrong classloader
        registerTransformationService("org.spongepowered.asm.launch.MixinTransformationServiceLegacy", contextCL);
        
        // MixinBootstrap
        // - Plugin Service
        registerLaunchPluginService("io.github.lxgaming.mixin.launch.MixinLaunchPluginService", contextCL);

        super.onLoad(env, otherServices);
    }
    
    /**
     * Fixes https://github.com/MinecraftForge/MinecraftForge/pull/6600
     */
    private void ensureTransformerExclusion() {
        try {
            Path path = Launcher.INSTANCE.environment().getProperty(IEnvironment.Keys.GAMEDIR.get())
                    .map(parentPath -> parentPath.resolve("mods"))
                    .map(Path::toAbsolutePath)
                    .map(Path::normalize)
                    .map(parentPath -> {
                        // Extract the file name
                        String file = getClass().getProtectionDomain().getCodeSource().getLocation().getFile();
                        return parentPath.resolve(file.substring(file.lastIndexOf('/') + 1));
                    })
                    .filter(Files::exists)
                    .orElse(null);
            if (path == null) {
                return;
            }
            
            // Check if the path is behind a symbolic link
            if (path.equals(path.toRealPath())) {
                return;
            }
            
            List<Path> transformers = getTransformers();
            if (transformers != null && !transformers.contains(path)) {
                transformers.add(path);
            }
        } catch (Throwable ex) {
            // no-op
        }
    }


    @SuppressWarnings("unchecked")
    private List<Path> getTransformers() {
        try {
            Class<?> modDirTransformerDiscovererClass = Class.forName("net.minecraftforge.fml.loading.ModDirTransformerDiscoverer", true, Launcher.class.getClassLoader());

            // net.minecraftforge.fml.loading.ModDirTransformerDiscoverer.transformers
            Field transformersField = modDirTransformerDiscovererClass.getDeclaredField("transformers");
            transformersField.setAccessible(true);
            return (List<Path>) transformersField.get(null);
        } catch (Exception ex) {
            LOGGER.error("Encountered an error while getting Transformers", ex);
            return null;
        }
    }
}
