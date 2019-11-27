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

import cpw.mods.modlauncher.TransformingClassLoader;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

public class MixinLaunchPluginService implements ILaunchPluginService {
    
    public static final String NAME = "mixinbootstrap";
    
    private static final List<String> SKIP_PACKAGES = Arrays.asList(
            "org.objectweb.asm.",
            "org.spongepowered.asm.launch.",
            "org.spongepowered.asm.lib.",
            "org.spongepowered.asm.mixin.",
            "org.spongepowered.asm.service.",
            "org.spongepowered.asm.util."
    );
    
    @Override
    public String name() {
        return MixinLaunchPluginService.NAME;
    }
    
    @Override
    public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty) {
        throw new UnsupportedOperationException("Outdated ModLauncher");
    }
    
    @Override
    public boolean processClass(Phase phase, ClassNode classNode, Type classType) {
        throw new UnsupportedOperationException("Outdated ModLauncher");
    }
    
    @Override
    public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty, String reason) {
        return EnumSet.noneOf(Phase.class);
    }
    
    @Override
    public boolean processClass(Phase phase, ClassNode classNode, Type classType, String reason) {
        return false;
    }
    
    @Override
    public void addResources(List<Map.Entry<String, Path>> resources) {
    }
    
    @Override
    public void initializeLaunch(ITransformerLoader transformerLoader, Path[] specialPaths) {
        TransformingClassLoader classLoader = (TransformingClassLoader) Thread.currentThread().getContextClassLoader();
        classLoader.addTargetPackageFilter(name -> SKIP_PACKAGES.stream().noneMatch(name::startsWith));
    }
    
    @Override
    public <T> T getExtension() {
        return null;
    }
}