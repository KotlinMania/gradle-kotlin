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
package org.gradle.api.internal.artifacts.ivyservice

import org.gradle.internal.versionedcache.CacheVersionMapping
import org.gradle.internal.versionedcache.CacheVersionMapping.Companion.introducedIn
import java.io.File

/**
 * Versioned locations of global caches.
 *
 * The purpose of tracking previous versions is to help with cache cleanup.
 * E.g. when the layout was changed in `version1`, and it gets changed
 * in `version2` once again, we can delete the `version1` cache
 * when we detect that it is no longer used.
 *
 * Always use release candidate versions since we normally
 * don’t do code changes (and thus no cache layout changes) in final versions.
 */
enum class CacheLayout(parent: CacheLayout?, name: String, versionMappingBuilder: CacheVersionMapping.Builder) {
    MODULES(null, "modules", introducedIn("1.9-rc-1").incrementedIn("1.9-rc-2")),

    // If you update FILE_STORE, you may also need to update LocallyAvailableResourceFinderFactory
    FILE_STORE(CacheLayout.MODULES, "files", introducedIn("1.9-rc-1")),

    META_DATA(
        CacheLayout.MODULES, "metadata",  // skipped versions were not used in a release
        introducedIn("1.9-rc-2")
            .changedTo(2, "1.11-rc-1")
            .changedTo(6, "1.12-rc-1")
            .changedTo(12, "2.0-rc-1")
            .changedTo(13, "2.1-rc-3")
            .changedTo(14, "2.2-rc-1")
            .changedTo(15, "2.4-rc-1")
            .changedTo(16, "2.8-rc-1")
            .changedTo(17, "3.0-milestone-1")
            .changedTo(21, "3.1-rc-1")
            .changedTo(23, "3.2-rc-1")
            .changedTo(24, "4.2-rc-1")
            .changedTo(31, "4.3-rc-1")
            .changedTo(36, "4.4-rc-1")
            .changedTo(48, "4.5-rc-1")
            .changedTo(51, "4.5.1")
            .changedTo(53, "4.6-rc-1")
            .changedTo(56, "4.7-rc-1")
            .changedTo(58, "4.8-rc-1")
            .changedTo(63, "4.10-rc-1")
            .changedTo(68, "5.0-milestone-1")
            .changedTo(69, "5.0-rc-1")
            .changedTo(71, "5.3-rc-1")
            .changedTo(79, "6.0-rc-1")
            .changedTo(82, "6.0-rc-2")
            .changedTo(95, "6.1-rc-1")
            .changedTo(96, "6.4-rc-1")
            .changedTo(97, "6.8-rc-1")
            .changedTo(99, "7.5-rc-1")
            .changedTo(101, "7.6.2")
            .changedToWithConflict(100, "8.0-milestone-5")
            .changedTo(105, "8.1-rc-2")
            .changedTo(106, "8.2-milestone-1")
            .changedTo(107, "8.11-rc-1")
    ),

    RESOURCES(CacheLayout.MODULES, "resources", introducedIn("1.9-rc-1")),

    TRANSFORMS(
        null, "transforms", introducedIn("3.5-rc-1")
            .changedTo(2, "5.1")
            .changedTo(3, "6.8-rc-1") // Introduced move semantics
            .changedTo(4, "8.6-rc-1")
            .retiredIn("8.8-rc-1")
    );

    val name: String?
    val versionMapping: CacheVersionMapping

    init {
        this.name = name
        this.versionMapping = if (parent == null) versionMappingBuilder.build() else versionMappingBuilder.build(parent.version)
    }

    val version: CacheVersion
        get() = versionMapping.latestVersion

    val key: String
        get() = this.name + "-" + this.version

    fun getPath(parentDir: File?): File {
        return File(parentDir, this.key)
    }
}
