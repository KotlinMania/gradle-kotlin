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
package org.gradle.internal.locking

import com.google.common.collect.ImmutableList
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging.getLogger
import org.gradle.internal.resource.local.FileResourceListener
import org.gradle.util.internal.GFileUtils
import java.io.File
import java.io.IOException
import java.lang.String
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.TreeMap
import java.util.function.Predicate
import java.util.stream.Collectors
import kotlin.Boolean
import kotlin.RuntimeException
import kotlin.checkNotNull
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.dropLastWhile
import kotlin.collections.plus
import kotlin.collections.toTypedArray
import kotlin.plus
import kotlin.sequences.plus
import kotlin.text.isEmpty
import kotlin.text.plus
import kotlin.text.split
import kotlin.text.startsWith
import kotlin.text.substring
import kotlin.text.toByteArray
import kotlin.text.toRegex
import kotlin.text.trim
import kotlin.use

class LockFileReaderWriter(fileResolver: FileResolver, private val context: DomainObjectContext, private val lockFile: RegularFileProperty, private val listener: FileResourceListener) {
    private val lockFilesRoot: Path?

    init {
        var resolve: Path? = null
        if (fileResolver.canResolveRelativePath()) {
            resolve = fileResolver.resolve(DEPENDENCY_LOCKING_FOLDER).toPath()
            // TODO: Can I find a way to use a convention here instead?
            lockFile.set(fileResolver.resolve(decorate(UNIQUE_LOCKFILE_NAME)))
        }
        this.lockFilesRoot = resolve
        LOGGER.debug("Lockfiles root: {}", lockFilesRoot)
    }

    fun readLockFile(lockId: String): MutableList<String>? {
        checkValidRoot()

        val lockFile = lockFilesRoot!!.resolve(decorate(lockId) + FILE_SUFFIX)
        listener.fileObserved(lockFile.toFile())
        if (Files.exists(lockFile)) {
            val result: MutableList<String>
            try {
                result = Files.readAllLines(lockFile, CHARSET)
            } catch (e: IOException) {
                throw RuntimeException("Unable to load lock file", e)
            }
            val lines = result
            filterNonModuleLines(lines)
            return lines
        } else {
            return null
        }
    }

    private fun decorate(lockId: String): String {
        if (context.isScript()) {
            if (context.isRootScript()) {
                return SETTINGS_SCRIPT_PREFIX + lockId
            }
            return BUILD_SCRIPT_PREFIX + lockId
        } else {
            return lockId
        }
    }

    private fun checkValidRoot() {
        checkNotNull(lockFilesRoot) { "Dependency locking cannot be used for " + context.getDisplayName() + ". " + LIMITATIONS_DOC_LINK }
    }

    fun readUniqueLockFile(): MutableMap<String, MutableList<String>> {
        checkValidRoot()
        val empty = Predicate { obj: String -> obj.isEmpty() }
        val comment = Predicate { s: String -> s.startsWith("#") }
        val uniqueLockFile = this.uniqueLockfilePath
        val emptyLockIds: MutableList<String> = ArrayList<String>()
        val uniqueLockState: MutableMap<String, MutableList<String>> = HashMap<String, MutableList<String>>(10)
        listener.fileObserved(uniqueLockFile.toFile())
        if (Files.exists(uniqueLockFile)) {
            try {
                Files.lines(uniqueLockFile, CHARSET).use { lines ->
                    lines.filter(empty.or(comment).negate())
                        .filter { line: String? ->
                            if (line!!.startsWith(EMPTY_RESOLUTIONS_ENTRY)) {
                                // This is a perf hack for handling the last line in the file in a special way
                                Companion.collectEmptyLockIds(line, emptyLockIds)
                                return@filter false
                            } else {
                                return@filter true
                            }
                        }.forEach { line: String? -> parseLine(line!!, uniqueLockState) }
                }
            } catch (e: IOException) {
                throw RuntimeException("Unable to load unique lockfile", e)
            }
            for (emptyLockId in emptyLockIds) {
                uniqueLockState.computeIfAbsent(emptyLockId) { k: String? -> ArrayList<String?>() }
            }
            return uniqueLockState
        } else {
            return HashMap<String, MutableList<String>>()
        }
    }

    private val uniqueLockfilePath: Path
        get() = lockFile.get().getAsFile().toPath()

    private fun parseLine(line: String, result: MutableMap<String, MutableList<String>>) {
        val split = line.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (split.size != 2) {
            throw InvalidLockFileException("lock file specified in '" + this.uniqueLockfilePath.toString() + "'. Line: '" + line + "'", FORMATTING_DOC_LINK)
        }
        val lockIds = split[1].split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (lockId in lockIds) {
            result.compute(lockId) { k: String?, v: MutableList<String>? ->
                val mapping: MutableList<String>?
                if (v == null) {
                    mapping = ArrayList<String>()
                } else {
                    mapping = v
                }
                mapping.add(split[0])
                mapping
            }
        }
    }

    fun canWrite(): Boolean {
        return lockFilesRoot != null
    }

    fun writeUniqueLockfile(lockState: MutableMap<String, MutableList<String>>) {
        checkValidRoot()
        val lockfilePath = this.uniqueLockfilePath

        if (lockState.isEmpty()) {
            // Remove the file when no lock state
            GFileUtils.deleteQuietly(lockfilePath.toFile())
            return
        }

        // Revert mapping
        val dependencyToLockIds: MutableMap<String, MutableList<String>> = TreeMap<String, MutableList<String>>()
        val emptyLockIds: MutableList<String> = ArrayList<String>()
        mapLockStateFromDependencyToLockId(lockState, dependencyToLockIds, emptyLockIds)

        writeUniqueLockfile(lockfilePath, dependencyToLockIds, emptyLockIds)

        cleanupLegacyLockFiles(lockState.keys)
    }

    /**
     * In prior versions of Gradle, each lock ID had its own lock file.
     * This method removes those lock files if they are present.
     */
    private fun cleanupLegacyLockFiles(lockIdsToDelete: MutableSet<String>) {
        lockIdsToDelete.stream()
            .map<Path> { f: String? -> lockFilesRoot!!.resolve(decorate(f!!) + FILE_SUFFIX) }
            .map<File> { obj: Path? -> obj!!.toFile() }
            .forEach { file: File? -> GFileUtils.deleteQuietly(file) }
    }

    private fun buildRegenerationComment(): String {
        return "# To regenerate this file, run: ./gradlew " + context.projectPath("dependencies") + " --write-locks"
    }

    private fun writeUniqueLockfile(lockfilePath: Path, dependencyToLockId: MutableMap<String, MutableList<String>>, emptyLockIds: MutableList<String>) {
        try {
            Files.createDirectories(lockfilePath.getParent())
            val content: MutableList<String> = ArrayList<String>(50)
            content.addAll(LOCKFILE_HEADER_LIST)
            content.add(buildRegenerationComment())
            for (entry in dependencyToLockId.entries) {
                val builder = entry.key + "=" + entry.value.stream().sorted().collect(Collectors.joining(","))
                content.add(builder)
            }
            content.add("empty=" + emptyLockIds.stream().sorted().collect(Collectors.joining(",")))
            val bytes = (String.join("\n", content) + "\n").toByteArray(CHARSET)
            Files.write(lockfilePath, bytes)
        } catch (e: IOException) {
            throw RuntimeException("Unable to write unique lockfile", e)
        }
    }

    companion object {
        private val LOGGER: Logger = getLogger(LockFileReaderWriter::class.java)!!
        private val LIMITATIONS_DOC_LINK = DocumentationRegistry().getDocumentationRecommendationFor("information on limitations", "dependency_locking", "locking_limitations")
        val FORMATTING_DOC_LINK: kotlin.String =
            "Verify the lockfile content. " + DocumentationRegistry().getDocumentationRecommendationFor("information on lock file format", "dependency_locking", "lock_state_location_and_format")

        const val UNIQUE_LOCKFILE_NAME: kotlin.String = "gradle.lockfile"
        const val FILE_SUFFIX: kotlin.String = ".lockfile"
        const val DEPENDENCY_LOCKING_FOLDER: kotlin.String = "gradle/dependency-locks"
        val CHARSET: Charset = StandardCharsets.UTF_8
        val LOCKFILE_HEADER_LIST: MutableList<kotlin.String> = ImmutableList.of<kotlin.String>(
            "# This is a Gradle generated file for dependency locking.",
            "# Manual edits can break the build and are not advised.",
            "# This file is expected to be part of source control."
        )
        const val EMPTY_RESOLUTIONS_ENTRY: kotlin.String = "empty="
        const val BUILD_SCRIPT_PREFIX: kotlin.String = "buildscript-"
        const val SETTINGS_SCRIPT_PREFIX: kotlin.String = "settings-"

        private fun filterNonModuleLines(lines: MutableList<kotlin.String>) {
            val iterator = lines.iterator()
            while (iterator.hasNext()) {
                val value = iterator.next().trim { it <= ' ' }
                if (value.startsWith("#") || value.isEmpty()) {
                    iterator.remove()
                }
            }
        }

        private fun collectEmptyLockIds(line: kotlin.String, emptyLockIds: MutableList<kotlin.String>) {
            if (line.length > EMPTY_RESOLUTIONS_ENTRY.length) {
                val lockIds = line.substring(EMPTY_RESOLUTIONS_ENTRY.length).split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                Collections.addAll<kotlin.String>(emptyLockIds, *lockIds)
            }
        }

        private fun mapLockStateFromDependencyToLockId(
            lockState: MutableMap<kotlin.String, MutableList<kotlin.String>>,
            dependencyToLockIds: MutableMap<kotlin.String, MutableList<kotlin.String>>,
            emptyLockIds: MutableList<kotlin.String>
        ) {
            for (entry in lockState.entries) {
                val dependencies = entry.value
                if (dependencies.isEmpty()) {
                    emptyLockIds.add(entry.key)
                } else {
                    for (dependency in dependencies) {
                        dependencyToLockIds.compute(dependency) { k: kotlin.String?, v: MutableList<kotlin.String>? ->
                            var confs = v
                            if (v == null) {
                                confs = ArrayList<kotlin.String>()
                            }
                            confs.add(entry.key)
                            confs
                        }
                    }
                }
            }
        }
    }
}
