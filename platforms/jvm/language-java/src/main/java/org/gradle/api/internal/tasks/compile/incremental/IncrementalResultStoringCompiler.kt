/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.tasks.compile.incremental

import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Iterables
import org.gradle.api.internal.tasks.compile.ApiCompilerResult
import org.gradle.api.internal.tasks.compile.JavaCompileSpec
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.CompilerApiData
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.constants.ConstantToDependentsMapping
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.constants.ConstantToDependentsMappingMerger
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.GeneratedResource
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingData
import org.gradle.api.internal.tasks.compile.incremental.recomp.CurrentCompilationAccess
import org.gradle.api.internal.tasks.compile.incremental.recomp.IncrementalCompilationResult
import org.gradle.api.internal.tasks.compile.incremental.recomp.PreviousCompilationAccess
import org.gradle.api.internal.tasks.compile.incremental.recomp.PreviousCompilationData
import org.gradle.api.internal.tasks.compile.incremental.recomp.RecompilationSpec
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDeclaration
import org.gradle.api.tasks.WorkResult
import org.gradle.language.base.internal.compile.Compiler
import java.io.File
import java.util.Objects
import java.util.function.Consumer

/**
 * Stores the incremental class dependency analysis after compilation has finished.
 */
internal class IncrementalResultStoringCompiler<T : JavaCompileSpec?>(
    private val delegate: Compiler<T?>,
    private val classpathSnapshotter: CurrentCompilationAccess,
    private val previousCompilationAccess: PreviousCompilationAccess
) : Compiler<T?> {
    override fun execute(spec: T?): WorkResult? {
        val result = delegate.execute(spec)
        if (result is RecompilationNotNecessary) {
            return result
        }
        storeResult(spec!!, result)
        return result
    }

    private fun storeResult(spec: JavaCompileSpec, result: WorkResult?) {
        val outputSnapshot = classpathSnapshotter.analyzeOutputFolder(spec.getDestinationDir())
        val classpathSnapshot = classpathSnapshotter.getClasspathSnapshot(Iterables.< File > concat < java . io . File ? > (spec.compileClasspath, spec.modulePath))
        val annotationProcessingData = getAnnotationProcessingData(spec, result)
        val compilerApiData = getCompilerApiData(spec, result)
        val minimizedClasspathSnapshot = classpathSnapshot.reduceToTypesAffecting(outputSnapshot, compilerApiData)
        val data = PreviousCompilationData(outputSnapshot, annotationProcessingData, minimizedClasspathSnapshot, compilerApiData)
        val previousCompilationDataFile = Objects.requireNonNull<File?>(spec.compileOptions!!.previousCompilationDataFile)
        previousCompilationAccess.writePreviousCompilationData(data, previousCompilationDataFile)
    }

    private fun getCompilerApiData(spec: JavaCompileSpec, result: WorkResult?): CompilerApiData {
        var result = result
        if (spec.compileOptions!!.supportsCompilerApi()) {
            var previousCompilerApiData: CompilerApiData? = null
            var recompilationSpec: RecompilationSpec? = null
            if (result is IncrementalCompilationResult) {
                previousCompilerApiData = result.getPreviousCompilationData().getCompilerApiData()
                recompilationSpec = result.getRecompilationSpec()
                result = result.getCompilerResult()
            }

            val changedClasses = if (recompilationSpec == null) mutableSetOf<String?>() else recompilationSpec.getClassesToCompile()
            val previousConstantToDependentsMapping = if (previousCompilerApiData == null) null else previousCompilerApiData.getConstantToClassMapping()
            val previousSourceClassesMapping = if (previousCompilerApiData == null) null else previousCompilerApiData.getSourceToClassMapping()
            if (result is ApiCompilerResult) {
                val jdkJavaResult = result
                val newConstantsToDependentsMapping: ConstantToDependentsMapping = jdkJavaResult.constantsAnalysisResult
                    .constantToDependentsMapping
                    .orElseThrow<org.gradle.api.GradleException?>(java.util.function.Supplier { org.gradle.api.GradleException("Constants to dependents mapping not present, but it should be") })!!
                val newSourceClassesMapping = jdkJavaResult.sourceClassesMapping
                val mergedSourceClassesMapping: MutableMap<String?, MutableSet<String?>?>?
                if (previousSourceClassesMapping == null) {
                    mergedSourceClassesMapping = newSourceClassesMapping
                } else {
                    mergedSourceClassesMapping = mergeSourceClassesMappings(previousSourceClassesMapping, newSourceClassesMapping, changedClasses)
                }
                val mergedConstants = ConstantToDependentsMappingMerger().merge(newConstantsToDependentsMapping, previousConstantToDependentsMapping, changedClasses)
                if (spec.compileOptions!!.supportsConstantAnalysis()) {
                    return CompilerApiData.Companion.withConstantsMapping(mergedSourceClassesMapping, mergedConstants)
                } else {
                    return CompilerApiData.Companion.withoutConstantsMapping(mergedSourceClassesMapping)
                }
            }
        }
        return CompilerApiData.Companion.unavailable()
    }

    private fun mergeSourceClassesMappings(
        previousSourceClassesMapping: MutableMap<String?, MutableSet<String?>?>,
        newSourceClassesMapping: MutableMap<String?, MutableSet<String?>?>,
        changedClasses: MutableSet<String?>
    ): MutableMap<String?, MutableSet<String?>?> {
        val merged: MutableMap<String?, MutableSet<String?>?> = HashMap<String?, MutableSet<String?>?>(previousSourceClassesMapping)
        merged.keys.removeAll(changedClasses)
        for (entry in newSourceClassesMapping.entries) {
            merged.computeIfAbsent(entry.key) { key: kotlin.String? -> java.util.HashSet<kotlin.String?>() }!!.addAll(entry.value!!)
        }
        return merged
    }

    private fun getAnnotationProcessingData(spec: JavaCompileSpec, result: WorkResult?): AnnotationProcessingData {
        var result = result
        val processors: MutableSet<AnnotationProcessorDeclaration?> = spec.effectiveAnnotationProcessors
        if (processors.isEmpty()) {
            return AnnotationProcessingData()
        }
        var previousAnnotationProcessingData: AnnotationProcessingData? = null
        var recompilationSpec: RecompilationSpec? = null
        if (result is IncrementalCompilationResult) {
            previousAnnotationProcessingData = result.getPreviousCompilationData().getAnnotationProcessingData()
            recompilationSpec = result.getRecompilationSpec()
            result = result.getCompilerResult()
        }
        val changedClasses = if (recompilationSpec == null) mutableSetOf<String?>() else recompilationSpec.getClassesToCompile()

        if (result is ApiCompilerResult) {
            val processingResult = result.annotationProcessingResult
            val newAnnotationProcessingData = AnnotationProcessingData(
                processingResult.generatedTypesWithIsolatedOrigin,
                processingResult.aggregatedTypes,
                processingResult.generatedAggregatingTypes,
                processingResult.generatedResourcesWithIsolatedOrigin,
                processingResult.generatedAggregatingResources,
                processingResult.fullRebuildCause
            )
            if (previousAnnotationProcessingData == null) {
                return newAnnotationProcessingData
            }
            return mergeAnnotationProcessingData(previousAnnotationProcessingData, newAnnotationProcessingData, changedClasses)
        }
        return AnnotationProcessingData(
            ImmutableMap.of<String?, MutableSet<String?>?>(),
            ImmutableSet.of<String?>(),
            ImmutableSet.of<String?>(),
            ImmutableMap.of<String?, MutableSet<GeneratedResource?>?>(),
            ImmutableSet.of<GeneratedResource?>(),
            "the chosen compiler did not support incremental annotation processing"
        )
    }

    private fun mergeAnnotationProcessingData(oldData: AnnotationProcessingData, newData: AnnotationProcessingData, changedClasses: MutableSet<String?>): AnnotationProcessingData {
        val generatedTypesByOrigin: MutableMap<String?, MutableSet<String?>?> = HashMap<String?, MutableSet<String?>?>(oldData.getGeneratedTypesByOrigin())
        changedClasses.forEach(Consumer { key: String? -> generatedTypesByOrigin.remove(key) })
        generatedTypesByOrigin.putAll(newData.getGeneratedTypesByOrigin())
        val generatedResourcesByOrigin: MutableMap<String?, MutableSet<GeneratedResource?>?> = HashMap<String?, MutableSet<GeneratedResource?>?>(oldData.getGeneratedResourcesByOrigin())
        changedClasses.forEach(Consumer { key: String? -> generatedResourcesByOrigin.remove(key) })
        generatedResourcesByOrigin.putAll(newData.getGeneratedResourcesByOrigin())

        return AnnotationProcessingData(
            generatedTypesByOrigin,
            newData.getAggregatedTypes(),
            newData.getGeneratedTypesDependingOnAllOthers(),
            generatedResourcesByOrigin,
            newData.getGeneratedResourcesDependingOnAllOthers(),
            newData.getFullRebuildCause()
        )
    }
}
