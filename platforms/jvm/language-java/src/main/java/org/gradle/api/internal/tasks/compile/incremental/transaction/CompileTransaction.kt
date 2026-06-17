/*
 * Copyright 2022 the original author or authors.
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
package org.gradle.api.internal.tasks.compile.incremental.transaction

import com.google.common.base.MoreObjects
import com.google.common.collect.ImmutableSet
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.tasks.compile.ApiCompilerResult
import org.gradle.api.internal.tasks.compile.CompilationFailedException
import org.gradle.api.internal.tasks.compile.JavaCompileSpec
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.GeneratedResource
import org.gradle.api.tasks.WorkResult
import org.gradle.api.tasks.WorkResults
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.file.Deleter
import org.gradle.language.base.internal.tasks.StaleOutputCleaner
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.EnumMap
import java.util.Objects
import java.util.function.Consumer
import java.util.function.Function
import java.util.stream.Collectors
import java.util.stream.Stream

/**
 * A helper class to handle incremental compilation after a failure: it makes moving files around easier and reverting state easier.
 */
class CompileTransaction(
    spec: JavaCompileSpec,
    classesToDelete: PatternSet?,
    resourcesToDelete: MutableMap<GeneratedResource.Location?, PatternSet?>,
    fileOperations: FileOperations,
    deleter: Deleter
) {
    private val deleter: Deleter
    private val fileOperations: FileOperations
    private val classesToDelete: PatternSet?
    private val spec: JavaCompileSpec?
    private val resourcesToDelete: MutableMap<GeneratedResource.Location?, PatternSet?>
    private val stashDirectory: File
    private val tempDir: File
    private val backupDirectory: File

    init {
        this.spec = spec
        this.tempDir = File(spec.tempDir, "compileTransaction")
        this.stashDirectory = File(tempDir, "stash-dir")
        this.backupDirectory = File(tempDir, "backup-dir")
        this.classesToDelete = classesToDelete
        this.resourcesToDelete = resourcesToDelete
        this.fileOperations = fileOperations
        this.deleter = deleter
    }

    /**
     * Executes the function that is wrapped in the transaction. Function accepts a work result,
     * that has a result of a stash operation. If some files were stashed, then work will be marked as "did work".
     *
     *
     * Execution steps: <br></br>
     * 1. At start create empty temporary directories or make sure they are empty <br></br>
     * 2. Stash all files that should be deleted from compiler destination directories to a temporary directories <br></br>
     * 3. a. In case of a success do nothing <br></br>
     * b. In case of a failure delete generated files and restore stashed files <br></br>
     */
    fun <T> execute(function: Function<WorkResult?, T?>): T? {
        ensureEmptyDirectoriesBeforeExecution()
        val stashedFiles = stashFilesThatShouldBeDeleted()
        try {
            if (supportsIncrementalCompilationAfterFailure()) {
                spec!!.classBackupDir = backupDirectory
            }
            val result = function.apply(WorkResults.didWork(!stashedFiles.isEmpty()))
            deleteEmptyDirectoriesAfterCompilation(stashedFiles)
            return result
        } catch (e: CompilationFailedException) {
            if (supportsIncrementalCompilationAfterFailure()) {
                rollback(stashedFiles, e.getCompilerPartialResult().orElse(null))
            }
            throw e
        }
    }

    private fun supportsIncrementalCompilationAfterFailure(): Boolean {
        return spec!!.compileOptions!!.supportsIncrementalCompilationAfterFailure()
    }

    private fun ensureEmptyDirectoriesBeforeExecution() {
        try {
            tempDir.mkdirs()

            // Create or clean stash and stage directories
            val ensureEmptyDirectories: MutableSet<File?> = HashSet<File?>()
            deleter.ensureEmptyDirectory(stashDirectory)
            ensureEmptyDirectories.add(stashDirectory)
            deleter.ensureEmptyDirectory(backupDirectory)
            ensureEmptyDirectories.add(backupDirectory)

            Files.list(tempDir.toPath()).use { dirStream ->
                dirStream.map<File?> { obj: Path? -> obj!!.toFile() }
                    .filter { file: File? -> !ensureEmptyDirectories.contains(file) }
                    .forEach { file: File? -> this.deleteRecursively(file!!) }
            }
        } catch (e: IOException) {
            throw throwAsUncheckedException(e)
        }
    }

    private fun deleteRecursively(file: File) {
        try {
            deleter.deleteRecursively(file)
        } catch (e: IOException) {
            throw throwAsUncheckedException(e)
        }
    }

    private fun stashFilesThatShouldBeDeleted(): MutableList<StashedFile?> {
        var uniqueId = 0
        val stashedFiles: MutableList<StashedFile?> = ArrayList<StashedFile?>()
        for (fileToDelete in collectFilesToDelete(classesToDelete, resourcesToDelete)) {
            val stashedFile = File(stashDirectory, fileToDelete.getName() + ".uniqueId" + uniqueId++)
            moveFile(fileToDelete, stashedFile)
            stashedFiles.add(StashedFile(fileToDelete, stashedFile))
        }
        return stashedFiles
    }

    //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    private fun deleteEmptyDirectoriesAfterCompilation(stashedFiles: MutableList<StashedFile?>) {
        val outputDirectories: ImmutableSet<File?> = this.outputDirectories
        val potentiallyEmptyFolders = stashedFiles.stream()
            .map<File?> { file: StashedFile? -> file.sourceFile.getParentFile() }
            .collect(Collectors.toSet())
        StaleOutputCleaner.cleanEmptyOutputDirectories(deleter, potentiallyEmptyFolders, outputDirectories)
    }

    private fun rollback(stashResult: MutableList<StashedFile?>, compilerResult: ApiCompilerResult?) {
        if (compilerResult != null) {
            deleteGeneratedFiles(compilerResult)
            rollbackOverwrittenFiles(compilerResult)
        }
        rollbackStashedFiles(stashResult)
    }

    //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    private fun deleteGeneratedFiles(compilerResult: ApiCompilerResult) {
        val classesToDelete = getNewGeneratedClasses(compilerResult)
        val resourcesToDelete = getNewGeneratedResources(compilerResult)
        val filesToDelete = collectFilesToDelete(classesToDelete, resourcesToDelete)
        StaleOutputCleaner.cleanOutputs(deleter, filesToDelete, this.outputDirectories)
        if (LOG.isDebugEnabled()) {
            LOG.debug("Deleting generated files: {}", filesToDelete.stream().sorted().collect(Collectors.toList()))
        }
    }

    private fun getNewGeneratedClasses(result: ApiCompilerResult): PatternSet {
        val filesToDelete = fileOperations.patternSet()
        result.sourceClassesMapping.values.stream()
            .flatMap<String?> { obj: MutableSet<String?>? -> obj!!.stream() }
            .forEach { className: String? ->
                filesToDelete.include(className!!.replace(".", "/") + ".class")
                filesToDelete.include(className.replace("[.$]".toRegex(), "_") + ".h")
            }
        val annotationProcessorTypes: MutableSet<String?> = HashSet<String?>(result.annotationProcessingResult.generatedAggregatingTypes)
        result.annotationProcessingResult.generatedTypesWithIsolatedOrigin.values.stream()
            .flatMap<String?> { obj: MutableSet<String?>? -> obj!!.stream() }
            .forEach { e: String? -> annotationProcessorTypes.add(e) }
        annotationProcessorTypes.forEach(Consumer { className: String? ->
            filesToDelete.include(className!!.replace(".", "/") + ".class")
            filesToDelete.include(className.replace("[.$]".toRegex(), "_") + ".h")
            filesToDelete.include(className.replace(".", "/") + ".java")
        })
        return filesToDelete
    }

    private fun getNewGeneratedResources(result: ApiCompilerResult): MutableMap<GeneratedResource.Location?, PatternSet?> {
        val resourcesByLocation: MutableMap<GeneratedResource.Location?, PatternSet?> = EnumMap<GeneratedResource.Location?, PatternSet?>(GeneratedResource.Location::class.java)
        Stream.of<GeneratedResource.Location>(*GeneratedResource.Location.values()).forEach { location: GeneratedResource.Location? -> resourcesByLocation.put(location, fileOperations.patternSet()) }
        result.annotationProcessingResult
            .generatedAggregatingResources
            .forEach(Consumer { resource: GeneratedResource? -> resourcesByLocation.get(resource!!.location)!!.include(resource.path) })
        result.annotationProcessingResult.generatedResourcesWithIsolatedOrigin.values.stream()
            .flatMap<GeneratedResource?> { obj: MutableSet<GeneratedResource?>? -> obj!!.stream() }
            .forEach { resource: GeneratedResource? -> resourcesByLocation.get(resource!!.location)!!.include(resource.path) }
        return resourcesByLocation
    }

    private fun collectFilesToDelete(classesToDelete: PatternSet?, resourcesToDelete: MutableMap<GeneratedResource.Location?, PatternSet?>): MutableSet<File> {
        val compileOutput = spec!!.getDestinationDir()
        val annotationProcessorOutput = spec.compileOptions!!.annotationProcessorGeneratedSourcesDirectory
        val headerOutput = spec.compileOptions!!.headerOutputDirectory
        val filesToDelete: MutableSet<File> = HashSet<File>()
        filesToDelete.addAll(collectFilesToDelete(classesToDelete, compileOutput))
        filesToDelete.addAll(collectFilesToDelete(classesToDelete, annotationProcessorOutput))
        filesToDelete.addAll(collectFilesToDelete(classesToDelete, headerOutput))
        filesToDelete.addAll(collectFilesToDelete(resourcesToDelete.get(GeneratedResource.Location.CLASS_OUTPUT), compileOutput))
        // If the client has not set a location for SOURCE_OUTPUT, javac outputs those files to the CLASS_OUTPUT directory, so delete that instead.
        filesToDelete.addAll(collectFilesToDelete(resourcesToDelete.get(GeneratedResource.Location.SOURCE_OUTPUT), MoreObjects.firstNonNull<File?>(annotationProcessorOutput, compileOutput)))
        // In the same situation with NATIVE_HEADER_OUTPUT, javac just NPEs.  Don't bother.
        filesToDelete.addAll(collectFilesToDelete(resourcesToDelete.get(GeneratedResource.Location.NATIVE_HEADER_OUTPUT), headerOutput))
        return filesToDelete
    }

    private fun collectFilesToDelete(patternSet: PatternSet?, sourceDirectory: File?): MutableSet<File?> {
        if (patternSet != null && !patternSet.isEmpty() && sourceDirectory != null && sourceDirectory.exists()) {
            return fileOperations.fileTree(sourceDirectory).matching(patternSet).getFiles()
        }
        return mutableSetOf<File?>()
    }

    private val outputDirectories: ImmutableSet<File?>
        get() = Stream.of<File?>(spec!!.getDestinationDir(), spec.compileOptions!!.annotationProcessorGeneratedSourcesDirectory, spec.compileOptions!!.headerOutputDirectory)
            .filter { obj: File? -> Objects.nonNull(obj) }
            .collect(ImmutableSet.toImmutableSet<File?>())

    private class StashedFile(private val sourceFile: File, private val stashFile: File) {
        fun unstash() {
            moveFile(stashFile, sourceFile)
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(CompileTransaction::class.java)

        private fun rollbackOverwrittenFiles(result: ApiCompilerResult) {
            result.backupClassFiles.forEach { (original: String?, backup: String?) -> moveFile(File(backup), File(original)) }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Restoring overwritten files: {}", result.backupClassFiles.keys.stream().sorted().collect(Collectors.toList()))
            }
        }

        private fun rollbackStashedFiles(stashedFiles: MutableList<StashedFile?>) {
            stashedFiles.forEach(Consumer { obj: StashedFile? -> obj!!.unstash() })
            if (LOG.isDebugEnabled()) {
                LOG.debug("Restoring stashed files: {}", stashedFiles.stream().map<String?> { f: StashedFile? -> f.sourceFile.getAbsolutePath() }.sorted().collect(Collectors.toList()))
            }
        }

        private fun moveFile(sourceFile: File, destinationFile: File) {
            try {
                destinationFile.getParentFile().mkdirs()
                Files.move(sourceFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } catch (e: IOException) {
                throw throwAsUncheckedException(e)
            }
        }
    }
}
