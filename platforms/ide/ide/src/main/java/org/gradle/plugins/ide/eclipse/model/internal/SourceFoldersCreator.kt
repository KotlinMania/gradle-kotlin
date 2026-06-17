/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.plugins.ide.eclipse.model.internal

import com.google.common.base.Function
import com.google.common.base.Joiner
import com.google.common.collect.Collections2
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.collect.LinkedHashMultimap
import com.google.common.collect.Lists
import com.google.common.collect.Multimap
import com.google.common.collect.Sets
import org.gradle.api.file.DirectoryTree
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.Pair
import org.gradle.internal.metaobject.DynamicObjectUtil
import org.gradle.plugins.ide.eclipse.internal.EclipsePluginConstants
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath
import org.gradle.plugins.ide.eclipse.model.SourceFolder
import org.gradle.util.internal.CollectionUtils
import org.gradle.util.internal.CollectionUtils.partition
import org.gradle.util.internal.CollectionUtils.sort
import java.io.File
import java.util.function.BiFunction
import java.util.stream.Collectors

class SourceFoldersCreator {
    fun createSourceFolders(classpath: EclipseClasspath): MutableList<SourceFolder?>? {
        val sourceFolders = configureProjectRelativeFolders(
            classpath.getSourceSets(),
            classpath.getTestSourceSets().getOrElse(mutableSetOf<SourceSet?>()),
            Function { input: File? -> PathUtil.normalizePath(classpath.getProject().relativePath(input!!)) },
            classpath.getDefaultOutputDir(),
            classpath.getBaseSourceOutputDir().get().getAsFile()
        )

        return collectRegularAndExternalSourceFolders<ImmutableList<SourceFolder?>?>(
            sourceFolders,
            BiFunction { sourceFoldersLeft: MutableCollection<SourceFolder?>?, sourceFoldersRight: MutableCollection<SourceFolder?>? ->
                ImmutableList.builder<SourceFolder?>()
                    .addAll(sourceFoldersLeft!!)
                    .addAll(sourceFoldersRight!!)
                    .build()
            })
    }

    /**
     * Returns the list of external source folders defining only the name and path attributes.
     *
     * @return source folders that live outside of the project
     */
    fun getBasicExternalSourceFolders(sourceSets: Iterable<SourceSet>?, provideRelativePath: Function<File?, String?>, defaultOutputDir: File?): MutableList<SourceFolder?>? {
        val basicSourceFolders = basicProjectRelativeFolders(sourceSets, provideRelativePath)
        return collectRegularAndExternalSourceFolders<ImmutableList<SourceFolder?>?>(
            basicSourceFolders,
            BiFunction { sourceFoldersLeft: MutableCollection<SourceFolder?>?, sourceFoldersRight: MutableCollection<SourceFolder?>? -> ImmutableList.copyOf<SourceFolder?>(sourceFoldersRight) })
    }

    private fun <T> collectRegularAndExternalSourceFolders(
        sourceFolder: MutableList<SourceFolder?>,
        collector: BiFunction<MutableCollection<SourceFolder?>?, MutableCollection<SourceFolder?>?, T?>
    ): T? {
        val partitionedFolders: Pair<MutableCollection<SourceFolder>?, MutableCollection<SourceFolder?>?> =
            partition<SourceFolder?>(sourceFolder, org.gradle.api.specs.Spec { sf: SourceFolder? -> sf!!.getPath().contains("..") })

        val externalSourceFolders = partitionedFolders.left
        val regularSourceFolders = partitionedFolders.right

        val sources: MutableList<String?> = Lists.newArrayList<String?>(Collections2.transform<SourceFolder?, String?>(regularSourceFolders!!, Function { obj: SourceFolder? -> obj!!.getName() }))
        val dedupedExternalSourceFolders: MutableCollection<SourceFolder?> = trimAndDedup(externalSourceFolders!!, sources)

        return collector.apply(regularSourceFolders, dedupedExternalSourceFolders)
    }

    private fun configureProjectRelativeFolders(
        sourceSets: Iterable<SourceSet>?,
        testSourceSets: MutableCollection<SourceSet?>,
        provideRelativePath: Function<File?, String?>,
        defaultOutputDir: File?,
        baseSourceOutputDir: File?
    ): MutableList<SourceFolder?> {
        val defaultOutputPath = provideRelativePath.apply(defaultOutputDir)
        val entries = ImmutableList.builder<SourceFolder?>()
        val sortedSourceSets = sortSourceSetsAsPerUsualConvention(sourceSets)
        val sourceSetOutputPaths = collectSourceSetOutputPaths(sortedSourceSets, defaultOutputPath!!, provideRelativePath.apply(baseSourceOutputDir))
        val sourceSetUsages = getSourceSetUsages(sortedSourceSets)
        for (sourceSet in sortedSourceSets) {
            val sortedSourceDirs = sortSourceDirsAsPerUsualConvention(sourceSet.allSource.getSrcDirTrees())
            for (tree in sortedSourceDirs) {
                val dir = tree.getDir()
                if (dir.isDirectory()) {
                    val folder = createSourceFolder(testSourceSets, provideRelativePath, sourceSetOutputPaths, sourceSetUsages, sourceSet, tree, dir)
                    entries.add(folder)
                }
            }
        }
        return entries.build()
    }

    private fun createSourceFolder(
        testSourceSets: MutableCollection<SourceSet?>,
        provideRelativePath: Function<File?, String?>,
        sourceSetOutputPaths: MutableMap<SourceSet?, String?>,
        sourceSetUsages: Multimap<SourceSet?, SourceSet?>,
        sourceSet: SourceSet,
        tree: DirectoryTree,
        dir: File
    ): SourceFolder {
        val relativePath = provideRelativePath.apply(dir)
        val folder = SourceFolder(relativePath, null)
        folder.setDir(dir)
        folder.setName(dir.getName())
        folder.setIncludes(getIncludesForTree(sourceSet, tree))
        folder.setExcludes(getExcludesForTree(sourceSet, tree))
        folder.setOutput(sourceSetOutputPaths.get(sourceSet))
        addScopeAttributes(folder, sourceSet, sourceSetUsages)
        addSourceSetAttributeIfNeeded(sourceSet, folder, testSourceSets)
        return folder
    }

    private fun basicProjectRelativeFolders(sourceSets: Iterable<SourceSet>?, provideRelativePath: Function<File?, String?>): MutableList<SourceFolder?> {
        val entries = ImmutableList.builder<SourceFolder?>()
        val sortedSourceSets = sortSourceSetsAsPerUsualConvention(sourceSets)
        for (sourceSet in sortedSourceSets) {
            val sortedSourceDirs = sortSourceDirsAsPerUsualConvention(sourceSet.allSource.getSrcDirTrees())
            for (tree in sortedSourceDirs) {
                val dir = tree.getDir()
                if (dir.isDirectory()) {
                    entries.add(createSourceFolder(provideRelativePath, dir))
                }
            }
        }
        return entries.build()
    }

    private fun trimAndDedup(externalSourceFolders: MutableCollection<SourceFolder>, givenSources: MutableList<String?>): MutableList<SourceFolder?> {
        // externals are mapped to linked resources so we just need a name of the resource, without full path
        // non-unique folder names are naively deduped by adding parent filename as a prefix till unique
        // since this seems like a rare edge case this simple approach should be enough
        val trimmedSourceFolders: MutableList<SourceFolder?> = ArrayList<SourceFolder?>()
        for (folder in externalSourceFolders) {
            folder.trim()
            var parentFile = folder.getDir().getParentFile()
            while (givenSources.contains(folder.getName()) && parentFile != null) {
                folder.trim(parentFile.getName())
                parentFile = parentFile.getParentFile()
            }
            givenSources.add(folder.getName())
            trimmedSourceFolders.add(folder)
        }
        return trimmedSourceFolders
    }

    private fun addScopeAttributes(folder: SourceFolder, sourceSet: SourceSet, sourceSetUsages: Multimap<SourceSet?, SourceSet?>) {
        folder.getEntryAttributes().put(EclipsePluginConstants.GRADLE_SCOPE_ATTRIBUTE_NAME, sanitizeNameForAttribute(sourceSet))
        folder.getEntryAttributes().put(EclipsePluginConstants.GRADLE_USED_BY_SCOPE_ATTRIBUTE_NAME, COMMA_JOINER.join(getUsingSourceSetNames(sourceSet, sourceSetUsages)))
    }

    private fun getUsingSourceSetNames(sourceSet: SourceSet?, sourceSetUsages: Multimap<SourceSet?, SourceSet?>): MutableList<String?> {
        return sourceSetUsages.get(sourceSet).stream()
            .map<String?> { sourceSet: SourceSet? -> this.sanitizeNameForAttribute(sourceSet!!) }
            .collect(Collectors.toList())
    }

    private fun sanitizeNameForAttribute(sourceSet: SourceSet): String {
        return sourceSet.name.replace(",".toRegex(), "")
    }

    private fun getSourceSetUsages(sourceSets: Iterable<SourceSet>): Multimap<SourceSet?, SourceSet?> {
        val usages: Multimap<SourceSet?, SourceSet?> = LinkedHashMultimap.create<SourceSet?, SourceSet?>()
        for (sourceSet in sourceSets) {
            for (otherSourceSet in sourceSets) {
                if (containsOutputOf(sourceSet, otherSourceSet)) {
                    usages.put(otherSourceSet, sourceSet)
                }
            }
        }
        return usages
    }

    private fun containsOutputOf(sourceSet: SourceSet, otherSourceSet: SourceSet): Boolean {
        try {
            return containsAll(sourceSet.runtimeClasspath, otherSourceSet.output)
        } catch (e: Exception) {
            return false
        }
    }

    private fun containsAll(first: FileCollection, second: FileCollection): Boolean {
        for (file in second) {
            if (!first.contains(file)) {
                return false
            }
        }
        return true
    }

    private fun addSourceSetAttributeIfNeeded(sourceSet: SourceSet?, folder: SourceFolder, testSourceSets: MutableCollection<SourceSet?>) {
        if (testSourceSets.contains(sourceSet)) {
            folder.getEntryAttributes().put(EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_KEY, EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_VALUE)
        }
    }

    private fun collectSourceSetOutputPaths(sourceSets: Iterable<SourceSet>, defaultOutputPath: String, baseSourceOutputDir: String?): MutableMap<SourceSet?, String?> {
        val existingPaths: MutableSet<String?> = Sets.newHashSet<String?>(defaultOutputPath)
        val result: MutableMap<SourceSet?, String?> = HashMap<SourceSet?, String?>()
        for (sourceSet in sourceSets) {
            val path = collectSourceSetOutputPath(sourceSet.name, existingPaths, "", baseSourceOutputDir)
            existingPaths.add(path)
            result.put(sourceSet, path)
        }

        return result
    }

    private fun collectSourceSetOutputPath(sourceSetName: String?, existingPaths: MutableSet<String?>, suffix: String?, baseSourceOutputDir: String?): String {
        val path = baseSourceOutputDir + "/" + sourceSetName + suffix
        return if (existingPaths.contains(path)) collectSourceSetOutputPath(sourceSetName, existingPaths, suffix + "_", baseSourceOutputDir) else path
    }

    private fun getExcludesForTree(sourceSet: SourceSet, directoryTree: DirectoryTree): MutableList<String?> {
        val excludesByType = getFiltersForTreeGroupedByType(sourceSet, directoryTree, "excludes")
        return CollectionUtils.intersection<String?>(excludesByType)
    }

    private fun getIncludesForTree(sourceSet: SourceSet, directoryTree: DirectoryTree): MutableList<String?> {
        val includesByType = getFiltersForTreeGroupedByType(sourceSet, directoryTree, "includes")
        if (includesByType.stream().anyMatch { obj: MutableSet<String?>? -> obj!!.isEmpty() }) {
            return mutableListOf<String?>()
        }

        val allIncludes = CollectionUtils.flattenCollections<String?>(String::class.java, includesByType)
        return ImmutableSet.copyOf<String?>(allIncludes).asList()
    }

    private fun getFiltersForTreeGroupedByType(sourceSet: SourceSet, directoryTree: DirectoryTree, filterOperation: String): MutableList<MutableSet<String?>?> {
        // check for duplicate entries in java and resources
        val javaSrcDirs: MutableSet<File?> = sourceSet.allJava.getSrcDirs()
        val resSrcDirs: MutableSet<File?> = sourceSet.resources.getSrcDirs()
        val srcDirs = CollectionUtils.intersection<File?>(ImmutableList.of<MutableSet<File?>?>(javaSrcDirs, resSrcDirs))
        if (!srcDirs.contains(directoryTree.getDir())) {
            return ImmutableList.of<MutableSet<String?>?>(collectFilters(directoryTree.getPatterns(), filterOperation))
        } else {
            val resourcesFilter = collectFilters(sourceSet.resources.getSrcDirTrees(), directoryTree.getDir(), filterOperation)
            val sourceFilter = collectFilters(sourceSet.allJava.getSrcDirTrees(), directoryTree.getDir(), filterOperation)
            return ImmutableList.of<MutableSet<String?>?>(resourcesFilter, sourceFilter)
        }
    }

    private fun collectFilters(directoryTrees: MutableSet<DirectoryTree>, targetDir: File?, filterOperation: String): MutableSet<String?> {
        for (directoryTree in directoryTrees) {
            if (directoryTree.getDir() == targetDir) {
                val patterns = directoryTree.getPatterns()
                return collectFilters(patterns, filterOperation)
            }
        }
        return mutableSetOf<String?>()
    }

    private fun collectFilters(patterns: PatternSet?, filterOperation: String): MutableSet<String?> {
        return uncheckedCast<MutableSet<String?>?>(DynamicObjectUtil.asDynamicObject(patterns!!).getProperty(filterOperation))!!
    }

    private fun sortSourceSetsAsPerUsualConvention(sourceSets: Iterable<SourceSet>?): MutableList<SourceSet> {
        return sort<SourceSet?>(sourceSets, Comparator.comparing<SourceSet?, Int?>(java.util.function.Function { sourceSet: SourceSet? -> Companion.toComparable(sourceSet!!) }))
    }

    private fun sortSourceDirsAsPerUsualConvention(sourceDirs: Iterable<DirectoryTree>?): MutableList<DirectoryTree> {
        return sort<DirectoryTree?>(sourceDirs, Comparator.comparing<DirectoryTree?, Int?>(java.util.function.Function { tree: DirectoryTree? -> Companion.toComparable(tree!!) }))
    }

    companion object {
        private fun createSourceFolder(provideRelativePath: Function<File?, String?>, dir: File): SourceFolder {
            val folder = SourceFolder(provideRelativePath.apply(dir), null)
            folder.setDir(dir)
            folder.setName(dir.getName())
            return folder
        }

        private val COMMA_JOINER = Joiner.on(',')
        private fun toComparable(sourceSet: SourceSet): Int {
            val name: String? = sourceSet.name
            if (SourceSet.MAIN_SOURCE_SET_NAME == name) {
                return 0
            } else if (SourceSet.TEST_SOURCE_SET_NAME == name) {
                return 1
            } else {
                return 2
            }
        }

        private fun toComparable(tree: DirectoryTree): Int {
            val path = tree.getDir().getPath()
            if (path.endsWith("java")) {
                return 0
            } else if (path.endsWith("resources")) {
                return 2
            } else {
                return 1
            }
        }
    }
}
