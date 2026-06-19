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
package org.gradle.internal.versionedcache

import org.apache.commons.io.filefilter.FileFilterUtils
import org.apache.commons.io.filefilter.RegexFileFilter
import org.gradle.cache.CleanableStore
import org.gradle.cache.CleanupProgressMonitor
import org.gradle.cache.internal.AbstractCacheCleanup
import org.gradle.cache.internal.CacheVersion
import org.gradle.cache.internal.FilesFinder
import org.gradle.cache.internal.NonReservedFileFilter
import org.gradle.util.GradleVersion
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileFilter
import java.util.Arrays
import java.util.SortedSet
import java.util.TreeSet
import java.util.function.Consumer
import java.util.regex.Pattern

class UnusedVersionsCacheCleanup private constructor(private val cacheNamePattern: Pattern, private val cacheVersionMapping: CacheVersionMapping, private val usedGradleVersions: UsedGradleVersions) :
    AbstractCacheCleanup(
        FilesFinder { baseDir: File?, filter: FileFilter? ->
            val combinedFilter: FileFilter = FileFilterUtils.and(
                FileFilterUtils.directoryFileFilter(), RegexFileFilter(
                    cacheNamePattern
                ), FileFilterUtils.asFileFilter(filter),
                FileFilterUtils.asFileFilter(NonReservedFileFilter(mutableSetOf<File>(baseDir!!)))
            )
            val result = baseDir.getParentFile().listFiles(combinedFilter)
            if (result == null) mutableSetOf<File>() else Arrays.asList<File>(*result)
        }) {
    private var usedVersions: MutableSet<CacheVersion>? = null

    override fun clean(cleanableStore: CleanableStore, progressMonitor: CleanupProgressMonitor) {
        determineUsedVersions()
        super.clean(cleanableStore, progressMonitor)
    }

    private fun determineUsedVersions() {
        usedVersions = TreeSet<CacheVersion>()
        for (gradleVersion in this.usedGradleVersionsSmallerThanCurrent) {
            cacheVersionMapping.getVersionUsedBy(gradleVersion).ifPresent(Consumer { e: CacheVersion -> usedVersions!!.add(e) })
        }
    }

    private val usedGradleVersionsSmallerThanCurrent: SortedSet<GradleVersion>
        get() = usedGradleVersions.usedGradleVersions.headSet(GradleVersion.current())

    override fun shouldDelete(cacheDir: File): Boolean {
        val matcher = cacheNamePattern.matcher(cacheDir.getName())
        if (matcher.matches()) {
            val version = CacheVersion.parse(matcher.group(1))
            return version.compareTo(cacheVersionMapping.latestVersion) < 0 && !usedVersions!!.contains(version)
        }
        return false
    }

    override fun doAfterDeletion(cacheDir: File) {
        LOGGER.debug("Deleting unused versioned cache directory at {}", cacheDir)
    }

    override fun deleteEmptyParentDirectories(baseDir: File, dir: File): Int {
        // do not delete parent dirs
        return 0
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(UnusedVersionsCacheCleanup::class.java)

        @JvmStatic
        fun create(cacheName: String, cacheVersionMapping: CacheVersionMapping, usedGradleVersions: UsedGradleVersions): UnusedVersionsCacheCleanup {
            val cacheNamePattern = Pattern.compile('^'.toString() + Pattern.quote(cacheName) + "-((?:\\d+" + Pattern.quote(CacheVersion.COMPONENT_SEPARATOR) + ")*\\d+)$")
            return UnusedVersionsCacheCleanup(cacheNamePattern, cacheVersionMapping, usedGradleVersions)
        }
    }
}
