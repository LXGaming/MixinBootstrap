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

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MixinTransformationServiceV2 extends CommonTransformationService {

    @Override
    public void beginScanning(IEnvironment environment) {

    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) throws IncompatibleEnvironmentException {
        if (!ITransformationService.class.getPackage().isCompatibleWith("8.0")) {
            throw new IncompatibleEnvironmentException("Incompatibility with ModLauncher");
        }

        try {
            resetBootstrapFallBackClassLoader();
        } catch (Throwable ex) {
            LOGGER.error("Encountered an error while attempting to append to the class path", ex);
            throw new IncompatibleEnvironmentException("Failed to append to the class path");
        }

        // Mixin
        // - Plugin Service
        registerLaunchPluginService("org.spongepowered.asm.launch.MixinLaunchPlugin", this.getClass().getClassLoader());

        // - Transformation Service
        // This cannot be loaded by the ServiceLoader as it will load classes under the wrong classloader
        registerTransformationService("org.spongepowered.asm.launch.MixinTransformationService", this.getClass().getClassLoader());

        super.onLoad(env, otherServices);
    }

    // Mixin requires it can be loaded in context class loader.
    private static void resetBootstrapFallBackClassLoader() throws Throwable {
        Class<?> moduleClassLoaderClass = Class.forName("cpw.mods.cl.ModuleClassLoader");
        ClassLoader contextCL = Thread.currentThread().getContextClassLoader();
        ClassLoader currentCL = MixinTransformationServiceV2.class.getClassLoader();
        if (moduleClassLoaderClass.isInstance(contextCL) && !currentCL.equals(contextCL)) {
            Unsafe unsafe = getUnsafe();
            Field fallbackClassLoaderField = moduleClassLoaderClass.getDeclaredField("fallbackClassLoader");
            ClassLoader fallbackClassLoader = (ClassLoader) unsafe.getObject(contextCL, unsafe.objectFieldOffset(fallbackClassLoaderField));
            unsafe.putObject(contextCL, unsafe.objectFieldOffset(fallbackClassLoaderField), new ClassLoader(fallbackClassLoader) {
                private final Set<String> invalidClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());
                @Override
                protected Class<?> findClass(final String name) throws ClassNotFoundException {
                    try {
                        return fallbackClassLoader.loadClass(name);
                    } catch (ClassNotFoundException cnfe) {
                        if (invalidClasses.contains(name)) throw cnfe;
                        invalidClasses.add(name);
                        Class<?> c = currentCL.loadClass(name);
                        invalidClasses.remove(name);
                        return c;
                    }
                }
            });
        }
    }

    private static Unsafe getUnsafe() throws Throwable {
        Field unsafeFiled = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeFiled.setAccessible(true);
        return (Unsafe) unsafeFiled.get(null);
    }
}
