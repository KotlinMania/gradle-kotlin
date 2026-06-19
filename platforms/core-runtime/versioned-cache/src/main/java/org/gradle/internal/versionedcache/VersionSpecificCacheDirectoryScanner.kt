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

import com.google.common.collect.ImmutableSortedSet
import org.apache.commons.io.filefilter.FileFilterUtils
import org.apache.commons.io.filefilter.RegexFileFilter
import org.gradle.util.GradleVersion
import java.io.File
import java.io.FileFilter
import java.util.Arrays
import java.util.SortedSet

class VersionSpecificCacheDirectoryScanner(@JvmField val baseDir: File) {
    fun getBaseDir(): File {
        return baseDir
    }

    fun getDirectory(gradleVersion: GradleVersion): File {
        return File(baseDir, gradleVersion.getVersion())
    }

    val existingDirectories: SortedSet<VersionSpecificCacheDirectory>
        get() {
            val builder =
                ImmutableSortedSet.naturalOrder<VersionSpecificCacheDirectory>()
            for (subDir in listVersionSpecificCacheDirs()) {
                val version = tryParseGradleVersion(subDir)
                if (version != null) {
                    builder.add(VersionSpecificCacheDirectory(subDir, version))
                }
            }
            return builder.build()
        }

    private fun listVersionSpecificCacheDirs(): MutableCollection<File> {
        val combinedFilter: FileFilter = FileFilterUtils.and(FileFilterUtils.directoryFileFilter(), RegexFileFilter("^\\d.*"))
        val result = baseDir.listFiles(combinedFilter)
        return if (result == null) mutableSetOf<File>() else Arrays.asList<File>(*result)
    }

    private fun tryParseGradleVersion(dir: File): GradleVersion? {
        try {
            return GradleVersion.version(dir.getName())
        } catch (e: Exception) {
            return null
        }
    }
}
