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
import org.gradle.util.GradleVersion
import java.io.File

class VersionSpecificCacheDirectory(dir: File, version: GradleVersion) : Comparable<VersionSpecificCacheDirectory> {
    @JvmField
    val dir: File
    @JvmField
    val version: GradleVersion

    init {
        this.dir = Preconditions.checkNotNull<File>(dir, "dir must not be null")
        this.version = Preconditions.checkNotNull<GradleVersion>(version, "version must not be null")
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as VersionSpecificCacheDirectory
        return this.dir == that.dir && this.version == that.version
    }

    override fun hashCode(): Int {
        var result = dir.hashCode()
        result = 31 * result + version.hashCode()
        return result
    }

    override fun compareTo(that: VersionSpecificCacheDirectory): Int {
        return this.version.compareTo(that.version)
    }
}
