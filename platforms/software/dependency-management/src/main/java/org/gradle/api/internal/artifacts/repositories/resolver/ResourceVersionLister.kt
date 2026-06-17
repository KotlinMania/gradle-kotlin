/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories.resolver

import com.google.common.collect.Lists
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.repositories.PatternHelper
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.ExternalResourceRepository
import org.gradle.internal.resource.ResourceExceptions
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.function.Function
import java.util.regex.Pattern
import java.util.stream.Collectors

class ResourceVersionLister(private val repository: ExternalResourceRepository) : VersionLister {
    private val fileSeparator = "/"
    private val directoriesToList: MutableMap<ExternalResourceName, MutableList<String>> = HashMap<ExternalResourceName, MutableList<String>>()

    override fun listVersions(module: ModuleIdentifier, artifact: IvyArtifactName, patterns: MutableList<ResourcePattern>, result: BuildableModuleVersionListingResolveResult) {
        val collector: MutableList<String> = ArrayList<String>()
        val filteredPatterns = filterDuplicates(patterns)
        val versionListPatterns: MutableMap<ResourcePattern, ExternalResourceName> = filteredPatterns.stream()
            .collect(Collectors.toMap(Function { pattern: ResourcePattern? -> pattern }, Function { pattern: ResourcePattern? -> pattern!!.toVersionListPattern(module, artifact) }))
        for (pattern in filteredPatterns) {
            visit(pattern, versionListPatterns, collector, result)
        }
        if (!collector.isEmpty()) {
            result.listed(collector)
        }
    }

    private fun filterDuplicates(patterns: MutableList<ResourcePattern>): MutableList<ResourcePattern> {
        if (patterns.size <= 1) {
            return patterns
        }
        val toRemove: MutableList<ResourcePattern> = ArrayList<ResourcePattern>(patterns.size)
        for (i in 0..<patterns.size - 1) {
            val first = patterns.get(i)
            if (toRemove.contains(first)) {
                continue
            }
            for (j in i + 1..<patterns.size) {
                val second = patterns.get(j)
                if (first.getPattern().startsWith(second.getPattern())) {
                    toRemove.add(second)
                } else if (second.getPattern().startsWith(first.getPattern())) {
                    toRemove.add(first)
                    break
                }
            }
        }
        if (toRemove.isEmpty()) {
            return patterns
        } else {
            val result = ArrayList<ResourcePattern>(patterns)
            result.removeAll(toRemove)
            return result
        }
    }

    private fun visit(
        pattern: ResourcePattern,
        versionListPatterns: MutableMap<ResourcePattern, ExternalResourceName>,
        collector: MutableList<String>,
        result: BuildableModuleVersionListingResolveResult
    ) {
        val versionListPattern: ExternalResourceName = versionListPatterns.get(pattern)!!
        LOGGER.debug("Listing all in {}", versionListPattern)
        try {
            collector.addAll(listRevisionToken(versionListPattern, result, versionListPatterns))
        } catch (e: Exception) {
            throw ResourceExceptions.failure(versionListPattern.getUri(), String.format("Could not list versions using %s.", pattern), e)
        }
    }

    // lists all the values a revision token listed by a given url lister
    private fun listRevisionToken(
        versionListPattern: ExternalResourceName,
        result: BuildableModuleVersionListingResolveResult,
        versionListPatterns: MutableMap<ResourcePattern, ExternalResourceName>
    ): MutableList<String> {
        val pattern = versionListPattern.getPath()
        if (!pattern.contains(REVISION_TOKEN)) {
            LOGGER.debug("revision token not defined in pattern {}.", pattern)
            return mutableListOf<String>()
        }
        val prefix = pattern.substring(0, pattern.indexOf(REVISION_TOKEN))
        val listedVersions: MutableList<String>
        if (revisionMatchesDirectoryName(pattern)) {
            val parent = versionListPattern.getRoot().resolve(prefix)
            listedVersions = listAll(parent, result)
        } else {
            val parentFolderSlashIndex = prefix.lastIndexOf(fileSeparator)
            val revisionParentFolder = if (parentFolderSlashIndex == -1) "" else prefix.substring(0, parentFolderSlashIndex + 1)
            val parent = versionListPattern.getRoot().resolve(revisionParentFolder)
            LOGGER.debug("using {} to list all in {} ", repository, revisionParentFolder)
            result.attempted(parent)
            val all = listWithCache(parent)
            if (all == null) {
                return mutableListOf<String>()
            }
            LOGGER.debug("found {} urls", all.size)
            val regexPattern = createRegexPattern(pattern, parentFolderSlashIndex)
            listedVersions = filterMatchedValues(all, regexPattern)
            LOGGER.debug("{} matched {}", listedVersions.size, pattern)
        }
        if (versionListPatterns.size > 1) {
            // Verify that none of the listed "versions" do match another pattern
            return filterOutMatchesWithOverlappingPatterns(listedVersions, versionListPattern, versionListPatterns.values)
        }
        return listedVersions
    }

    //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    private fun filterOutMatchesWithOverlappingPatterns(
        listedVersions: MutableList<String>,
        currentVersionListPattern: ExternalResourceName,
        versionListPatterns: MutableCollection<ExternalResourceName>
    ): MutableList<String> {
        val remaining: MutableList<String> = Lists.newArrayList<String>(listedVersions)
        for (otherVersionListPattern in versionListPatterns) {
            if (otherVersionListPattern !== currentVersionListPattern) {
                val patternPath = otherVersionListPattern.getPath()
                val regexPattern = toControlRegexPattern(patternPath)
                val matching = listedVersions.stream()
                    .filter { version: String? -> regexPattern.matcher(currentVersionListPattern.getPath().replace(REVISION_TOKEN, version!!)).matches() }
                    .collect(Collectors.toList())
                if (!matching.isEmpty()) {
                    LOGGER.debug("Filtered out {} from results for overlapping match with {}", matching, otherVersionListPattern)
                    remaining.removeAll(matching)
                }
            }
        }
        return remaining
    }

    private fun filterMatchedValues(all: MutableList<String>, p: Pattern): MutableList<String> {
        val ret: MutableList<String> = ArrayList<String>(all.size)
        for (path in all) {
            val m = p.matcher(path)
            if (m.matches()) {
                val value = m.group(1)
                ret.add(value!!)
            }
        }
        return ret
    }

    private fun createRegexPattern(pattern: String, prefixLastSlashIndex: Int): Pattern {
        val endNameIndex = pattern.indexOf(fileSeparator, prefixLastSlashIndex + 1)
        var namePattern: String?
        if (endNameIndex != -1) {
            namePattern = pattern.substring(prefixLastSlashIndex + 1, endNameIndex)
        } else {
            namePattern = pattern.substring(prefixLastSlashIndex + 1)
        }
        namePattern = namePattern.replace("\\.".toRegex(), "\\\\.")
        val acceptNamePattern = namePattern.replace("\\[revision]".toRegex(), "(.+)")
        return Pattern.compile(acceptNamePattern)
    }

    private fun toControlRegexPattern(pattern: String): Pattern {
        var pattern = pattern
        pattern = pattern.replace("\\.".toRegex(), "\\\\.")

        // Creates a control regexp pattern where extra revision tokens _must_ have the same value as the original one
        val acceptNamePattern = pattern.replaceFirst("\\[revision]".toRegex(), "(.+)")
            .replace("\\[revision]".toRegex(), "\u0001")
        return Pattern.compile(acceptNamePattern)
    }

    private fun revisionMatchesDirectoryName(pattern: String): Boolean {
        val startToken: Int = pattern.indexOf(REVISION_TOKEN)
        if (startToken > 0 && !pattern.startsWith(fileSeparator, startToken - 1)) {
            // previous character is not a separator
            return false
        }
        val endToken: Int = startToken + REV_TOKEN_LENGTH
        // next character is not a separator
        return endToken >= pattern.length || pattern.startsWith(fileSeparator, endToken)
    }

    private fun listAll(parent: ExternalResourceName, result: BuildableModuleVersionListingResolveResult): MutableList<String> {
        LOGGER.debug("using {} to list all in {}", repository, parent)
        result.attempted(parent.toString())
        val paths = listWithCache(parent)
        if (paths == null) {
            return mutableListOf<String>()
        }
        LOGGER.debug("found {} resources", paths.size)
        return paths
    }

    private fun listWithCache(parent: ExternalResourceName): MutableList<String>? {
        if (directoriesToList.containsKey(parent)) {
            return directoriesToList.get(parent)
        } else {
            val result = repository.resource(parent).list()
            directoriesToList.put(parent, result!!)
            return result
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(ResourceVersionLister::class.java)
        private val REVISION_TOKEN = PatternHelper.getTokenString(PatternHelper.REVISION_KEY)
        val REV_TOKEN_LENGTH: Int = REVISION_TOKEN.length
    }
}
