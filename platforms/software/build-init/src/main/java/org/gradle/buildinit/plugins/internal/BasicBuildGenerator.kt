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

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework
import org.gradle.buildinit.plugins.internal.modifiers.ComponentType
import org.gradle.buildinit.plugins.internal.modifiers.Language
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption
import java.util.Optional

/**
 * Generator for a "basic" Gradle build.
 */
class BasicBuildGenerator(scriptBuilderFactory: BuildScriptBuilderFactory, private val documentationRegistry: DocumentationRegistry, generators: MutableList<out BuildContentGenerator>) :
    AbstractBuildGenerator(
        BasicProjectGenerator(
            scriptBuilderFactory,
            documentationRegistry
        ), generators
    ) {
    override fun getId(): String {
        return "basic"
    }

    override fun getComponentType(): ComponentType {
        return ComponentType.BASIC
    }

    override fun productionCodeUses(language: Language): Boolean {
        return false
    }

    override fun getDefaultProjectNames(): MutableList<String> {
        return getComponentType().getDefaultProjectNames()
    }

    override fun supportsJavaTargets(): Boolean {
        return false
    }

    override fun getModularizationOptions(): MutableSet<ModularizationOption> {
        return mutableSetOf<ModularizationOption>(ModularizationOption.SINGLE_PROJECT)
    }

    override fun supportsPackage(): Boolean {
        return false
    }

    override fun getDefaultDsl(): BuildInitDsl {
        return BuildInitDsl.KOTLIN
    }

    override fun getDefaultTestFramework(modularizationOption: ModularizationOption): BuildInitTestFramework {
        return BuildInitTestFramework.NONE
    }

    override fun getTestFrameworks(modularizationOption: ModularizationOption): MutableSet<BuildInitTestFramework> {
        return mutableSetOf<BuildInitTestFramework>(BuildInitTestFramework.NONE)
    }

    override fun getFurtherReading(settings: InitSettings): Optional<String> {
        return Optional.of<String>(documentationRegistry.sampleForMessage)
    }
}
