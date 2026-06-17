/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.language.base.internal.model

import com.google.common.collect.Lists
import com.google.common.primitives.Booleans
import org.apache.commons.lang3.StringUtils
import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer
import org.gradle.internal.service.ServiceRegistry
import org.gradle.language.base.internal.JointCompileTaskConfig
import org.gradle.language.base.internal.LanguageSourceSetInternal
import org.gradle.language.base.internal.SourceTransformTaskConfig
import org.gradle.language.base.internal.registry.LanguageTransform
import org.gradle.language.base.internal.registry.LanguageTransformContainer
import org.gradle.platform.base.internal.BinarySpecInternal
import java.util.Collections

/**
 * Creates source 'transformation' tasks based on the available [LanguageTransform]s.
 *
 * This class is a basic and somewhat hacky placeholder for true dependency-aware source handling:
 * - Source sets should be able to depend on other source sets, resulting in the correct task dependencies and inputs
 * - Joint-compilation should only be used in the case where sources are co-dependent.
 *
 * Currently we use joint-compilation when:
 * - We have a language transform that supports joint-compilation
 * - Binary is flagged with [BinarySpecInternal.hasCodependentSources].
 */
class BinarySourceTransformations(private val tasks: TaskContainer, transforms: LanguageTransformContainer, serviceRegistry: ServiceRegistry?) {
    private val prioritizedTransforms: Iterable<LanguageTransform<*, *>>
    private val serviceRegistry: ServiceRegistry?

    init {
        this.prioritizedTransforms = prioritize(transforms)
        this.serviceRegistry = serviceRegistry
    }

    fun createTasksFor(binary: BinarySpecInternal) {
        val sourceSetsToCompile: MutableSet<LanguageSourceSetInternal?> = getSourcesToCompile(binary)
        for (languageTransform in prioritizedTransforms) {
            if (!languageTransform.applyToBinary(binary)) {
                continue
            }

            var sourceSetToCompile: LanguageSourceSetInternal?
            while ((findSourceFor(languageTransform, sourceSetsToCompile).also { sourceSetToCompile = it }) != null) {
                sourceSetsToCompile.remove(sourceSetToCompile)

                val taskConfig = languageTransform.getTransformTask()
                val taskName = getTransformTaskName(languageTransform, taskConfig, binary, sourceSetToCompile!!)
                @Suppress("deprecation") val task: Task = tasks.create(taskName, taskConfig.getTaskType())
                taskConfig.configureTask(task, binary, sourceSetToCompile, serviceRegistry)

                task.dependsOn(sourceSetToCompile)
                binary.tasks.add(task)

                if (binary.hasCodependentSources() && taskConfig is JointCompileTaskConfig) {
                    val jointCompileTaskConfig = taskConfig

                    val candidateSourceSets = sourceSetsToCompile.iterator()
                    while (candidateSourceSets.hasNext()) {
                        val candidate = candidateSourceSets.next()
                        if (jointCompileTaskConfig.canTransform(candidate)) {
                            jointCompileTaskConfig.configureAdditionalTransform(task, candidate)
                            candidateSourceSets.remove()
                        }
                    }
                }
            }
        }
        // Should really fail here if sourcesToCompile is not empty: no transform for this source set in this binary
    }

    private fun prioritize(languageTransforms: LanguageTransformContainer): Iterable<LanguageTransform<*, *>> {
        val prioritized: MutableList<LanguageTransform<*, *>> = Lists.newArrayList<LanguageTransform<*, *>?>(languageTransforms)
        Collections.sort<LanguageTransform<*, *>?>(prioritized, object : Comparator<LanguageTransform<*, *>?> {
            override fun compare(o1: LanguageTransform<*, *>, o2: LanguageTransform<*, *>): Int {
                val joint1 = o1.getTransformTask() is JointCompileTaskConfig
                val joint2 = o2.getTransformTask() is JointCompileTaskConfig
                return Booleans.trueFirst().compare(joint1, joint2)
            }
        })
        return prioritized
    }

    private fun getSourcesToCompile(binary: BinarySpecInternal): MutableSet<LanguageSourceSetInternal?> {
        val sourceSets = LinkedHashSet<LanguageSourceSetInternal?>()
        for (languageSourceSet in binary.inputs) {
            val languageSourceSetInternal = languageSourceSet as LanguageSourceSetInternal
            if (languageSourceSetInternal.getMayHaveSources()) {
                sourceSets.add(languageSourceSetInternal)
            }
        }
        return sourceSets
    }

    private fun getTransformTaskName(transform: LanguageTransform<*, *>, taskConfig: SourceTransformTaskConfig, binary: BinarySpecInternal, sourceSetToCompile: LanguageSourceSetInternal): String {
        if (binary.hasCodependentSources() && taskConfig is JointCompileTaskConfig) {
            return taskConfig.getTaskPrefix() + StringUtils.capitalize(binary.projectScopedName) + StringUtils.capitalize(transform.javaClass.getSimpleName())
        }
        return taskConfig.getTaskPrefix() + StringUtils.capitalize(binary.projectScopedName) + StringUtils.capitalize(sourceSetToCompile.getProjectScopedName())
    }

    private fun findSourceFor(languageTransform: LanguageTransform<*, *>, sourceSetsToCompile: MutableSet<LanguageSourceSetInternal?>): LanguageSourceSetInternal? {
        for (candidate in sourceSetsToCompile) {
            if (languageTransform.getSourceSetType().isInstance(candidate)) {
                return candidate
            }
        }
        return null
    }
}
