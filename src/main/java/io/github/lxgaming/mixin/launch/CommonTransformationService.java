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

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

abstract class CommonTransformationService implements ITransformationService {
    static final String NAME = "mixinbootstrap";
    static final Logger LOGGER = LogManager.getLogger("MixinBootstrap Launch");
    final Map<String, ILaunchPluginService> launchPluginServices;
    final Set<ITransformationService> transformationServices;

    public CommonTransformationService() {
        if (Launcher.INSTANCE == null) {
            throw new IllegalStateException("Launcher has not been initialized!");
        }

        this.launchPluginServices = getLaunchPluginServices();
        this.transformationServices = new HashSet<>();
    }

    @Override
    public String name() {
        return NAME;
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
    public List<Map.Entry<String, Path>> runScan(IEnvironment environment) {
        List<Map.Entry<String, Path>> list = new ArrayList<>();
        this.transformationServices.forEach(transformationService -> list.addAll(transformationService.runScan(environment)));

        return list;
    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) throws IncompatibleEnvironmentException {
        for (ITransformationService transformationService : this.transformationServices) {
            transformationService.onLoad(env, otherServices);
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List<ITransformer> transformers() {
        List<ITransformer> list = new ArrayList<>();
        this.transformationServices.forEach(transformationService -> list.addAll(transformationService.transformers()));

        return list;
    }

    @SuppressWarnings("unchecked")
    private Map<String, ILaunchPluginService> getLaunchPluginServices() {
        try {
            // cpw.mods.modlauncher.Launcher.launchPlugins
            Field launchPluginsField = Launcher.class.getDeclaredField("launchPlugins");
            launchPluginsField.setAccessible(true);
            LaunchPluginHandler launchPluginHandler = (LaunchPluginHandler) launchPluginsField.get(Launcher.INSTANCE);

            // cpw.mods.modlauncher.LaunchPluginHandler.plugins
            Field pluginsField = LaunchPluginHandler.class.getDeclaredField("plugins");
            pluginsField.setAccessible(true);
            return (Map<String, ILaunchPluginService>) pluginsField.get(launchPluginHandler);
        } catch (Exception ex) {
            LOGGER.error("Encountered an error while getting LaunchPluginServices", ex);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    void registerLaunchPluginService(String className, ClassLoader classLoader) throws IncompatibleEnvironmentException {
        try {
            Class<? extends ILaunchPluginService> launchPluginServiceClass = (Class<? extends ILaunchPluginService>) Class.forName(className, true, classLoader);
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
    void registerTransformationService(String className, ClassLoader classLoader) throws IncompatibleEnvironmentException {
        try {
            Class<? extends ITransformationService> transformationServiceClass = (Class<? extends ITransformationService>) Class.forName(className, true, classLoader);
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
}
