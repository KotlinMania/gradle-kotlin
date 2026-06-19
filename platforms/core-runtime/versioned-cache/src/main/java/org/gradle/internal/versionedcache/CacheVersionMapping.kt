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

import com.google.common.base.Preconditions
import org.gradle.cache.internal.CacheVersion
import org.gradle.util.GradleVersion
import java.util.Map
import java.util.NavigableMap
import java.util.Optional
import java.util.TreeMap
import java.util.function.Function

class CacheVersionMapping private constructor(versions: NavigableMap<GradleVersion, CacheVersion>) {
    private val versions: NavigableMap<GradleVersion, CacheVersion>

    init {
        Preconditions.checkArgument(!versions.isEmpty(), "versions must not be empty")
        this.versions = TreeMap<GradleVersion, CacheVersion>(versions)
    }

    val latestVersion: CacheVersion
        get() = versions[versions.lastKey()]!!

    fun getVersionUsedBy(gradleVersion: GradleVersion): Optional<CacheVersion> {
        val versionToFind = if (gradleVersion.isSnapshot()) gradleVersion.getBaseVersion() else gradleVersion
        return Optional.ofNullable<MutableMap.MutableEntry<GradleVersion, CacheVersion>>(versions.floorEntry(versionToFind)).map<CacheVersion>(Function { it.value })
    }

    class Builder {
        private val versions: NavigableMap<GradleVersion, Int> = TreeMap<GradleVersion, Int>()

        fun incrementedIn(minGradleVersion: String): Builder {
            return changedTo(versions.get(versions.lastKey())!! + 1, minGradleVersion)
        }

        /**
         * Specify the Gradle version where this cache directory was retired.
         * For this and any newer Gradle version, the cache directory is unused.
         * This is indicated by setting the current cache version to Integer.MAX_VALUE (so it cannot be further incremented).
         */
        fun retiredIn(gradleVersion: String): Builder {
            return changedTo(Int.MAX_VALUE, gradleVersion)
        }

        fun changedTo(cacheVersion: Int, minGradleVersion: String): Builder {
            val parsedGradleVersion = GradleVersion.version(minGradleVersion)
            if (!versions.isEmpty()) {
                Preconditions.checkArgument(
                    parsedGradleVersion.compareTo(versions.lastKey()) > 0,
                    "Gradle version (%s) must be greater than all previous versions: %s", parsedGradleVersion.getVersion(), versions.keys
                )
                val currentBaseVersion = GradleVersion.current().getBaseVersion()
                Preconditions.checkArgument(
                    parsedGradleVersion.getBaseVersion().compareTo(currentBaseVersion) <= 0,
                    "Base version of Gradle version (%s) must not be greater than base version of current Gradle version: %s", parsedGradleVersion.getVersion(), currentBaseVersion
                )
                Preconditions.checkArgument(
                    cacheVersion > versions.get(versions.lastKey())!!,
                    "cache version (%s) must be greater than all previous versions: %s", cacheVersion, versions.values
                )
            }
            versions.put(parsedGradleVersion, cacheVersion)
            return this
        }

        /**
         * You should not use this method. But Gradle 7.6.2 backport made it necessary.
         * Only one set of argument is currently accepted
         */
        fun changedToWithConflict(cacheVersion: Int, minGradleVersion: String): Builder {
            val parsedGradleVersion = GradleVersion.version(minGradleVersion)
            Preconditions.checkArgument(cacheVersion == 100 && minGradleVersion == "8.0-milestone-5")
            versions.put(parsedGradleVersion, cacheVersion)
            return this
        }

        @JvmOverloads
        fun build(parentVersion: CacheVersion = CacheVersion.empty()): CacheVersionMapping {
            val convertedVersions: NavigableMap<GradleVersion, CacheVersion> = TreeMap<GradleVersion, CacheVersion>()
            for (entry in versions.entries) {
                convertedVersions.put(entry.key, parentVersion.append(entry.value))
            }
            return CacheVersionMapping(convertedVersions)
        }
    }

    companion object {
        @JvmStatic
        fun introducedIn(gradleVersion: String): Builder {
            return CacheVersionMapping.Builder().changedTo(1, gradleVersion)
        }
    }
}
