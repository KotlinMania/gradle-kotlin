/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.tasks.compile.incremental.recomp

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileType
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.tasks.compile.DefaultJvmLanguageCompileSpec.setCompileClasspath
import org.gradle.api.internal.tasks.compile.JavaCompileSpec
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.GeneratedResource
import org.gradle.api.internal.tasks.compile.incremental.transaction.CompileTransaction
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.file.Deleter
import org.gradle.work.ChangeType
import org.gradle.work.FileChange
import java.io.File
import java.util.EnumMap

internal abstract class AbstractRecompilationSpecProvider(
    private val deleter: Deleter?,
    private val fileOperations: FileOperations,
    private val sourceTree: FileTree,
    private val sourceChanges: Iterable<FileChange>,
    private val incremental: Boolean
) : RecompilationSpecProvider {
    override fun provideRecompilationSpec(spec: JavaCompileSpec?, current: CurrentCompilation, previous: PreviousCompilation): RecompilationSpec {
        val recompilationSpec = RecompilationSpec()
        val sourceFileClassNameConverter: SourceFileClassNameConverter = FileNameDerivingClassNameConverter(
            previous.getSourceToClassConverter(),
            this.fileExtensions
        )

        processClasspathChanges(current, previous, recompilationSpec)

        val sourceFileChangeProcessor = SourceFileChangeProcessor(previous)
        processSourceChanges(current, sourceFileChangeProcessor, recompilationSpec, sourceFileClassNameConverter)
        processCompilerSpecificDependencies(spec, recompilationSpec, sourceFileChangeProcessor, sourceFileClassNameConverter)
        collectAllSourcePathsAndIndependentClasses(sourceFileChangeProcessor, recompilationSpec, sourceFileClassNameConverter)

        val typesToReprocess = previous.getTypesToReprocess(recompilationSpec.getClassesToCompile())
        processTypesToReprocess(typesToReprocess, recompilationSpec, sourceFileClassNameConverter)
        addModuleInfoToCompile(recompilationSpec, sourceFileClassNameConverter)

        return recompilationSpec
    }

    protected abstract val fileExtensions: MutableSet<String?>?

    private fun processSourceChanges(
        current: CurrentCompilation?,
        sourceFileChangeProcessor: SourceFileChangeProcessor,
        spec: RecompilationSpec,
        sourceFileClassNameConverter: SourceFileClassNameConverter
    ) {
        if (spec.isFullRebuildNeeded()) {
            return
        }
        for (fileChange in sourceChanges) {
            if (spec.isFullRebuildNeeded()) {
                return
            }
            if (fileChange.getFileType() != FileType.FILE) {
                continue
            }

            val relativeFilePath = fileChange.getNormalizedPath()
            val changedClasses = sourceFileClassNameConverter.getClassNames(relativeFilePath)
            if (changedClasses.isEmpty() && !isIncrementalOnResourceChanges(current)) {
                spec.setFullRebuildCause(rebuildClauseForChangedNonSourceFile(fileChange))
            }
            sourceFileChangeProcessor.processChange(changedClasses, spec)
            // For added/modified files, record the source path directly so it's included
            // in compilation even if the class-to-source reverse mapping is stale (see #28916).
            if (fileChange.getChangeType() != ChangeType.REMOVED && !changedClasses.isEmpty()) {
                spec.addSourcePath(relativeFilePath)
            }
        }
    }

    protected abstract fun processCompilerSpecificDependencies(
        spec: JavaCompileSpec?,
        recompilationSpec: RecompilationSpec?,
        sourceFileChangeProcessor: SourceFileChangeProcessor?,
        sourceFileClassNameConverter: SourceFileClassNameConverter?
    )

    protected abstract fun isIncrementalOnResourceChanges(currentCompilation: CurrentCompilation?): Boolean

    override fun initCompilationSpecAndTransaction(spec: JavaCompileSpec, recompilationSpec: RecompilationSpec): CompileTransaction {
        if (!recompilationSpec.isBuildNeeded()) {
            spec.setSourceFiles(ImmutableSet.of<File?>())
            spec.setClassesToProcess(mutableSetOf<T?>())
            return CompileTransaction(spec, fileOperations.patternSet(), ImmutableMap.of<GeneratedResource.Location?, PatternSet?>(), fileOperations, deleter)
        }

        val classesToDelete = fileOperations.patternSet()
        val sourceToCompile = fileOperations.patternSet()

        prepareFilePatterns(recompilationSpec.getClassesToCompile(), recompilationSpec.getSourcePaths(), classesToDelete, sourceToCompile)
        spec.setSourceFiles(narrowDownSourcesToCompile(sourceTree, sourceToCompile))
        includePreviousCompilationOutputOnClasspath(spec)
        addClassesToProcess(spec, recompilationSpec)
        spec.classesToCompile = recompilationSpec.getClassesToCompile()
        val resourcesToDelete: MutableMap<GeneratedResource.Location?, PatternSet?> = prepareResourcePatterns(recompilationSpec.getResourcesToGenerate(), fileOperations)
        return CompileTransaction(spec, classesToDelete, resourcesToDelete, fileOperations, deleter)
    }

    override fun isIncremental(): Boolean {
        return incremental
    }

    companion object {
        private const val MODULE_INFO_CLASS_NAME = "module-info"
        private const val PACKAGE_INFO_CLASS_NAME = "package-info"

        private fun processClasspathChanges(current: CurrentCompilation, previous: PreviousCompilation, spec: RecompilationSpec) {
            val dependents = current.findDependentsOfClasspathChanges(previous)
            if (dependents.isDependencyToAll) {
                spec.setFullRebuildCause(dependents.description)
                return
            }
            spec.addClassesToCompile(dependents.privateDependentClasses)
            spec.addClassesToCompile(dependents.accessibleDependentClasses)
            spec.addResourcesToGenerate(dependents.dependentResources)
        }

        private fun rebuildClauseForChangedNonSourceFile(fileChange: FileChange): String {
            return String.format("%s '%s' has been %s", "resource", fileChange.getFile().getName(), fileChange.getChangeType().name.lowercase())
        }

        /**
         * This method collects all source paths that will be passed to a compiler. While collecting paths it additionally also
         * collects all classes that are inside these sources, but were not detected as a dependency of changed classes.
         * This is important so all .class files that will be re-created are removed before compilation, otherwise
         * it confuse a compiler: for example Groovy compiler could generate incorrect classes for Spock.
         *
         *
         * We will use name "independent classes" for classes that are in the sources that are passed to a compiler but are not a dependency to a changed class.
         *
         *
         * Check also: [#21644](https://github.com/gradle/gradle/issues/21644)
         */
        private fun collectAllSourcePathsAndIndependentClasses(
            sourceFileChangeProcessor: SourceFileChangeProcessor,
            spec: RecompilationSpec,
            sourceFileClassNameConverter: SourceFileClassNameConverter
        ) {
            var classesToCompile: MutableSet<String?> = LinkedHashSet<String?>(spec.getClassesToCompile())
            while (!classesToCompile.isEmpty() && !spec.isFullRebuildNeeded()) {
                val independentClasses: MutableSet<String?> = collectSourcePathsAndIndependentClasses(classesToCompile, spec, sourceFileClassNameConverter)
                // Since these independent classes didn't actually change, they will be just recreated without any change, so we don't need to collect all transitive dependencies.
                // But we have to collect annotation processor dependencies, so these classes are correctly deleted, since annotation processor is able to output classes from these independent classes.
                classesToCompile = if (independentClasses.isEmpty()) mutableSetOf<String?>() else
                    sourceFileChangeProcessor.processAnnotationDependenciesOfIndependentClasses(independentClasses, spec)
            }
        }

        /**
         * Collect source paths and independent classes.
         *
         *
         * The source paths corresponding to the `classesToCompile` are added to the `spec`.
         * It will also add the independent classes to the `spec`'s `classesToCompile`.
         *
         * @param classesToCompile the source paths that will be added to the `spec`.
         * @param spec the spec that will receive the source paths.
         *
         * @return independent classes for the detected source paths.
         */
        private fun collectSourcePathsAndIndependentClasses(
            classesToCompile: MutableSet<String?>,
            spec: RecompilationSpec,
            sourceFileClassNameConverter: SourceFileClassNameConverter
        ): MutableSet<String?> {
            val independentClasses: MutableSet<String?> = LinkedHashSet<String?>()
            for (classToCompile in classesToCompile) {
                for (sourcePath in sourceFileClassNameConverter.getRelativeSourcePaths(classToCompile)) {
                    independentClasses.addAll(collectIndependentClassesForSourcePath(sourcePath, spec, sourceFileClassNameConverter))
                    spec.addSourcePath(sourcePath)
                }
            }
            return independentClasses
        }

        private fun collectIndependentClassesForSourcePath(sourcePath: String?, spec: RecompilationSpec, sourceFileClassNameConverter: SourceFileClassNameConverter): MutableSet<String?> {
            val classNames = sourceFileClassNameConverter.getClassNames(sourcePath)
            if (classNames.size <= 1) {
                // If source has just 1 class, it doesn't have any independent class
                return mutableSetOf<String?>()
            }
            val newClasses: MutableSet<String?> = LinkedHashSet<String?>()
            for (className in classNames) {
                if (spec.addClassToCompile(className)) {
                    newClasses.add(className)
                }
            }
            return newClasses
        }

        private fun processTypesToReprocess(typesToReprocess: MutableSet<String>, spec: RecompilationSpec, sourceFileClassNameConverter: SourceFileClassNameConverter) {
            for (typeToReprocess in typesToReprocess) {
                if (typeToReprocess.endsWith(PACKAGE_INFO_CLASS_NAME) || typeToReprocess == MODULE_INFO_CLASS_NAME) {
                    // Fixes: https://github.com/gradle/gradle/issues/17572
                    // package-info classes cannot be passed as classes to reprocess to the Java compiler.
                    // Therefore, we need to recompile them every time anything changes if they are processed by an aggregating annotation processor.
                    spec.addClassToCompile(typeToReprocess)
                    spec.addSourcePaths(sourceFileClassNameConverter.getRelativeSourcePaths(typeToReprocess))
                } else {
                    spec.addClassToReprocess(typeToReprocess)
                }
            }
        }

        private fun addModuleInfoToCompile(spec: RecompilationSpec, sourceFileClassNameConverter: SourceFileClassNameConverter) {
            val moduleInfoSources = sourceFileClassNameConverter.getRelativeSourcePathsThatExist(MODULE_INFO_CLASS_NAME)
            if (!moduleInfoSources.isEmpty()) {
                // Always recompile module-info.java if present.
                // This solves case for incremental compilation where some package was deleted and exported in module-info, but compilation doesn't fail.
                spec.addClassToCompile(MODULE_INFO_CLASS_NAME)
                spec.addSourcePaths(moduleInfoSources)
            }
        }

        private fun narrowDownSourcesToCompile(sourceTree: FileTree, sourceToCompile: PatternSet): Iterable<File?> {
            return sourceTree.matching(sourceToCompile)
        }

        private fun prepareResourcePatterns(staleResources: MutableCollection<GeneratedResource>, fileOperations: FileOperations): MutableMap<GeneratedResource.Location?, PatternSet?> {
            val resourcesByLocation: MutableMap<GeneratedResource.Location?, PatternSet?> = EnumMap<GeneratedResource.Location?, PatternSet?>(GeneratedResource.Location::class.java)
            for (location in GeneratedResource.Location.values()) {
                resourcesByLocation.put(location, fileOperations.patternSet())
            }
            for (resource in staleResources) {
                resourcesByLocation.get(resource.location)!!.include(resource.path)
            }
            return resourcesByLocation
        }

        private fun prepareFilePatterns(staleClasses: MutableCollection<String>, sourcePaths: MutableCollection<String>, filesToDelete: PatternSet, sourceToCompile: PatternSet) {
            for (sourcePath in sourcePaths) {
                filesToDelete.include(sourcePath)
                sourceToCompile.include(sourcePath)
            }
            for (staleClass in staleClasses) {
                filesToDelete.include(staleClass.replace("\\.".toRegex(), "/") + ".class")
                filesToDelete.include(staleClass.replace("[.$]".toRegex(), "_") + ".h")
            }
        }

        private fun addClassesToProcess(spec: JavaCompileSpec, recompilationSpec: RecompilationSpec) {
            val classesToProcess: MutableSet<String?> = HashSet<String?>(recompilationSpec.getClassesToProcess())
            classesToProcess.removeAll(recompilationSpec.getClassesToCompile())
            spec.setClassesToProcess(classesToProcess)
        }

        private fun includePreviousCompilationOutputOnClasspath(spec: JavaCompileSpec) {
            val originalClasspath: MutableList<File?> = spec.compileClasspath!!
            val destinationDir = spec.getDestinationDir()
            spec.setCompileClasspath(
                ImmutableList.builderWithExpectedSize<File?>(originalClasspath.size + 1)
                    .add(destinationDir)
                    .addAll(originalClasspath)
                    .build()
            )
        }
    }
}
