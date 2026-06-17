/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.buildinit.plugins.internal

import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework
import org.gradle.buildinit.plugins.internal.modifiers.ComponentType
import org.gradle.buildinit.plugins.internal.modifiers.Language
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption
import org.gradle.internal.Factory.create
import java.util.Optional

/**
 * Generator for some software and an associated Gradle build.
 */
class SourceGeneratingBuildGenerator(private val descriptor: ProjectGenerator, private val generators: MutableList<out BuildContentGenerator>) : AbstractBuildGenerator(
    descriptor,
    generators
), CompositeProjectInitDescriptor {
    override fun getId(): String {
        return descriptor.getId()
    }

    override fun getComponentType(): ComponentType {
        return descriptor.getComponentType()
    }

    override fun getLanguage(): Language {
        return descriptor.getLanguage()
    }

    override fun productionCodeUses(language: Language): Boolean {
        return descriptor.getLanguage() == language
    }

    override fun getDefaultProjectNames(): MutableList<String> {
        return getComponentType().getDefaultProjectNames()
    }

    override fun supportsJavaTargets(): Boolean {
        return descriptor.isJvmLanguage()
    }

    override fun getModularizationOptions(): MutableSet<ModularizationOption> {
        return descriptor.getModularizationOptions()
    }

    override fun supportsPackage(): Boolean {
        return descriptor.supportsPackage()
    }

    override fun getDefaultDsl(): BuildInitDsl {
        return descriptor.getDefaultDsl()
    }

    override fun getDefaultTestFramework(): BuildInitTestFramework {
        return getDefaultTestFramework(ModularizationOption.SINGLE_PROJECT)
    }

    override fun getDefaultTestFramework(modularizationOption: ModularizationOption): BuildInitTestFramework {
        return descriptor.getDefaultTestFramework(modularizationOption)
    }

    override fun getTestFrameworks(): MutableSet<BuildInitTestFramework> {
        return getTestFrameworks(ModularizationOption.SINGLE_PROJECT)
    }

    override fun getTestFrameworks(modularizationOption: ModularizationOption): MutableSet<BuildInitTestFramework> {
        return descriptor.getTestFrameworks(modularizationOption)
    }

    override fun getFurtherReading(settings: InitSettings): Optional<String> {
        return descriptor.getFurtherReading(settings)
    }

    override fun generateWithExternalComments(settings: InitSettings): MutableMap<String, MutableList<String>> {
        val buildContentGenerationContext = BuildContentGenerationContext(VersionCatalogDependencyRegistry(false))
        if (descriptor !is LanguageSpecificAdaptor) {
            throw UnsupportedOperationException()
        }
        for (generator in generators) {
            if (generator is SimpleGlobalFilesBuildSettingsDescriptor) {
                generator.generateWithoutComments(settings, buildContentGenerationContext)
            } else {
                generator.generate(settings, buildContentGenerationContext)
            }
        }
        val comments = descriptor.generateWithExternalComments(settings, buildContentGenerationContext)
        VersionCatalogGenerator.Companion.create(settings.getTarget()).generate(buildContentGenerationContext, settings.isWithComments())
        return comments
    }
}
