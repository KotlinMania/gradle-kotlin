/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.api.plugins.jvm.internal

import org.gradle.api.Action
import org.gradle.api.Transformer
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.jvm.JavaVersionParser
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.provider.MergeProvider
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.internal.provider.Providers
import org.gradle.api.internal.tasks.compile.HasCompileOptions
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.internal.JavaPluginExtensionInternal
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.instantiation.InstanceGenerator
import java.util.Collections
import java.util.stream.Collectors
import javax.inject.Inject

class DefaultJvmLanguageUtilities @Inject constructor(
    private val instanceGenerator: InstanceGenerator,
    private val project: ProjectInternal
) : JvmLanguageUtilities {
    private val configurationToCompileTasks: MutableMap<ConfigurationInternal, MutableSet<TaskProvider<*>>> // The generic wildcard (`?`) == AbstractCompile & HasCompileOptions

    init {
        this.configurationToCompileTasks = HashMap<ConfigurationInternal, MutableSet<TaskProvider<*>>>(5)
    }

    override fun <COMPILE> useDefaultTargetPlatformInference(configuration: Configuration, compileTask: TaskProvider<COMPILE?>) where COMPILE : AbstractCompile?, COMPILE : HasCompileOptions? {
        val configurationInternal = configuration as ConfigurationInternal

        val untypedTasks = configurationToCompileTasks.computeIfAbsent(configurationInternal) { key: ConfigurationInternal? -> HashSet<TaskProvider<*>?>() }
        val compileTasks: MutableSet<TaskProvider<COMPILE?>> = uncheckedCast<MutableSet<TaskProvider<COMPILE?>>?>(untypedTasks)!!
        compileTasks.add(compileTask)

        val java = project.getExtensions().getByType<JavaPluginExtension>(JavaPluginExtension::class.java)

        val targetJvmVersion = (java as JavaPluginExtensionInternal).getAutoTargetJvm().flatMap<Int>(Transformer { autoTargetJvm: Boolean? ->
            if (!autoTargetJvm!! && !configuration.isCanBeConsumed()) {
                return@flatMap Providers.of<Int>(Int.MAX_VALUE)
            }
            getMaxTargetJvmVersion<COMPILE?>(compileTasks)
        })

        configurationInternal.getAttributes().attributeProvider<Int>(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, targetJvmVersion)
    }

    override fun registerJvmLanguageSourceDirectory(sourceSet: SourceSet, name: String, configuration: Action<in JvmLanguageSourceDirectoryBuilder>) {
        val builder = instanceGenerator.newInstance<DefaultJvmLanguageSourceDirectoryBuilder>(
            DefaultJvmLanguageSourceDirectoryBuilder::class.java,
            name,
            project,
            sourceSet
        )
        configuration.execute(builder)
        builder.build()
    }

    companion object {
        private fun <COMPILE> getMaxTargetJvmVersion(compileTasks: MutableSet<TaskProvider<COMPILE?>>): ProviderInternal<Int> where COMPILE : AbstractCompile?, COMPILE : HasCompileOptions? {
            assert(!compileTasks.isEmpty())

            val allTargetJdkVersions = compileTasks.stream().map<Provider<Int>> { taskProvider: TaskProvider<COMPILE?>? ->
                taskProvider!!.flatMap<Int>(Transformer { compileTask: COMPILE? ->
                    if (compileTask!!.options.release.isPresent()) {
                        return@flatMap compileTask.options.release
                    }
                    val compilerArgs: MutableList<String> = compileTask.options.compilerArgs
                    val flagIndex = compilerArgs.indexOf("--release")
                    if (flagIndex != -1 && flagIndex + 1 < compilerArgs.size) {
                        // String.valueOf() is required here since compilerArgs.get can mysteriously not return a String
                        return@flatMap Providers.of<Int>(compilerArgs.get(flagIndex + 1).toString().toInt())
                    } else {
                        return@flatMap Providers.of<Int>(JavaVersionParser.parseMajorVersion(compileTask.targetCompatibility!!))
                    }
                })
            }.collect(Collectors.toList())

            return MergeProvider<Int?>(allTargetJdkVersions).map<Int>(Transformer { coll: MutableList<Int?>? -> Collections.max(coll) })
        }
    }
}
