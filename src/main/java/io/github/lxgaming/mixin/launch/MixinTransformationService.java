/*
 * Copyright 2019 Alex Thomson
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

import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import joptsimple.OptionSpecBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class MixinTransformationService implements ITransformationService {
    
    public static final String NAME = "mixinbootstrap";
    
    private static final Logger LOGGER = LogManager.getLogger("MixinBootstrap Launch");
    private final Map<String, ILaunchPluginService> launchPluginServices;
    private final Set<ITransformationService> transformationServices;
    
    public MixinTransformationService() {
        if (Launcher.INSTANCE == null) {
            throw new IllegalStateException("Launcher has not been initialized!");
        }
        
        this.launchPluginServices = getLaunchPluginServices();
        this.transformationServices = new HashSet<>();
    }
    
    @Nonnull
    @Override
    public String name() {
        return MixinTransformationService.NAME;
    }
    
    @Override
    public void arguments(BiFunction<String, String, OptionSpecBuilder> argumentBuilder) {
        this.transformationServices.forEach(transformationService -> transformationService.arguments(argumentBuilder));
    }
    
    @Override
    public void argumentValues(OptionResult option) {
        this.transformationServices.forEach(transformationService -> transformationService.argumentValues(option));
    }
    
    @Override
    public void initialize(IEnvironment environment) {
        this.transformationServices.forEach(transformationService -> transformationService.initialize(environment));
    }
    
    @Override
    public void beginScanning(IEnvironment environment) {
        this.transformationServices.forEach(transformationService -> transformationService.beginScanning(environment));
    }
    
    @Override
    public List<Map.Entry<String, Path>> runScan(IEnvironment environment) {
        List<Map.Entry<String, Path>> list = new ArrayList<>();
        this.transformationServices.forEach(transformationService -> {
            list.addAll(transformationService.runScan(environment));
        });
        
        return list;
    }
    
    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) throws IncompatibleEnvironmentException {
        if (this.launchPluginServices == null) {
            throw new IncompatibleEnvironmentException("LaunchPluginServices is unavailable");
        }
        
        // Running in a dev environment
        if (getClass().getClassLoader() == Launcher.class.getClassLoader()) {
            return;
        }
        
        try {
            URLClassLoader classLoader = (URLClassLoader) Launcher.class.getClassLoader();
            Method addURLMethod = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addURLMethod.setAccessible(true);
            addURLMethod.invoke(classLoader, getClass().getProtectionDomain().getCodeSource().getLocation().toURI().toURL());
        } catch (Throwable ex) {
            throw new IncompatibleEnvironmentException("Failed to invoke URLClassLoader::addURL");
        }
        
        // Mixin
        // - Plugin Service
        registerLaunchPluginService("org.spongepowered.asm.launch.MixinLaunchPlugin");
        
        // - Transformation Service
        // This cannot be loaded by the ServiceLoader as it will load classes under the wrong classloader
        registerTransformationService("org.spongepowered.asm.launch.MixinTransformationService");
        
        // MixinBootstrap
        // - Plugin Service
        registerLaunchPluginService("io.github.lxgaming.mixin.launch.MixinLaunchPluginService");
        
        for (ITransformationService transformationService : this.transformationServices) {
            transformationService.onLoad(env, otherServices);
        }
    }
    
    @Nonnull
    @Override
    public List<ITransformer> transformers() {
        List<ITransformer> list = new ArrayList<>();
        this.transformationServices.forEach(transformationService -> {
            list.addAll(transformationService.transformers());
        });
        
        return list;
    }
    
    @Override
    public Map.Entry<Set<String>, Supplier<Function<String, Optional<URL>>>> additionalClassesLocator() {
        return null;
    }
    
    @Override
    public Map.Entry<Set<String>, Supplier<Function<String, Optional<URL>>>> additionalResourcesLocator() {
        return null;
    }
    
    @SuppressWarnings("unchecked")
    private void registerLaunchPluginService(String className) throws IncompatibleEnvironmentException {
        try {
            Class<? extends ILaunchPluginService> launchPluginServiceClass = (Class<? extends ILaunchPluginService>) Class.forName(className, true, Launcher.class.getClassLoader());
            if (isLaunchPluginServicePresent(launchPluginServiceClass)) {
                LOGGER.warn("{} is already registered", launchPluginServiceClass.getSimpleName());
                return;
            }
            
            ILaunchPluginService launchPluginService = launchPluginServiceClass.newInstance();
            String pluginName = launchPluginService.name();
            this.launchPluginServices.put(pluginName, launchPluginService);
            
            List<Map<String, String>> mods = Launcher.INSTANCE.environment().getProperty(IEnvironment.Keys.MODLIST.get()).orElse(null);
            if (mods != null) {
                Map<String, String> mod = new HashMap<>();
                mod.put("name", pluginName);
                mod.put("type", "PLUGINSERVICE");
                String fileName = launchPluginServiceClass.getProtectionDomain().getCodeSource().getLocation().getFile();
                mod.put("file", fileName.substring(fileName.lastIndexOf('/')));
                mods.add(mod);
            }
            
            LOGGER.debug("Registered {} ({})", launchPluginServiceClass.getSimpleName(), pluginName);
        } catch (Throwable ex) {
            LOGGER.error("Encountered an error while registering {}", className, ex);
            throw new IncompatibleEnvironmentException(String.format("Failed to register %s", className));
        }
    }
    
    @SuppressWarnings("unchecked")
    private void registerTransformationService(String className) throws IncompatibleEnvironmentException {
        try {
            Class<? extends ITransformationService> transformationServiceClass = (Class<? extends ITransformationService>) Class.forName(className, true, Thread.currentThread().getContextClassLoader());
            if (isTransformationServicePresent(transformationServiceClass)) {
                LOGGER.warn("{} is already registered", transformationServiceClass.getSimpleName());
                return;
            }
            
            ITransformationService transformationService = transformationServiceClass.newInstance();
            String name = transformationService.name();
            this.transformationServices.add(transformationService);
            LOGGER.debug("Registered {} ({})", transformationServiceClass.getSimpleName(), name);
        } catch (Exception ex) {
            LOGGER.error("Encountered an error while registering {}", className, ex);
            throw new IncompatibleEnvironmentException(String.format("Failed to register %s", className));
        }
    }
    
    private boolean isLaunchPluginServicePresent(Class<? extends ILaunchPluginService> launchPluginServiceClass) {
        for (ILaunchPluginService launchPluginService : this.launchPluginServices.values()) {
            if (launchPluginServiceClass.isInstance(launchPluginService)) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean isTransformationServicePresent(Class<? extends ITransformationService> transformationServiceClass) {
        for (ITransformationService transformationService : this.transformationServices) {
            if (transformationServiceClass.isInstance(transformationService)) {
                return true;
            }
        }
        
        return false;
    }
    
    private Map<String, ILaunchPluginService> getLaunchPluginServices() {
        try {
            // cpw.mods.modlauncher.Launcher.launchPlugins
            Field launchPluginsField = Launcher.class.getDeclaredField("launchPlugins");
            launchPluginsField.setAccessible(true);
            LaunchPluginHandler launchPluginHandler = (LaunchPluginHandler) launchPluginsField.get(Launcher.INSTANCE);
            
            // cpw.mods.modlauncher.LaunchPluginHandler.plugins
            Field pluginsField = LaunchPluginHandler.class.getDeclaredField("plugins");
            pluginsField.setAccessible(true);
            
            // noinspection unchecked
            return (Map<String, ILaunchPluginService>) pluginsField.get(launchPluginHandler);
        } catch (Exception ex) {
            LOGGER.error("Encountered an error while getting LaunchPluginServices", ex);
            return null;
        }
    }
}