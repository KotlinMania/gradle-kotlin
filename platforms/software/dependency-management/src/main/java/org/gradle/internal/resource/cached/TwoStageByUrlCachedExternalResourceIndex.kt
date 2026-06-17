/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.internal.resource.cached

import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import java.io.File
import java.nio.file.Path

class TwoStageByUrlCachedExternalResourceIndex(
    private val readOnlyCachePath: Path,
    private val readOnlyCache: CachedExternalResourceIndex<String?>,
    private val writableCache: CachedExternalResourceIndex<String?>
) : CachedExternalResourceIndex<String?> {
    override fun store(key: String?, artifactFile: File, metaData: ExternalResourceMetaData?) {
        if (artifactFile.toPath().startsWith(readOnlyCachePath)) {
            // skip writing because the file comes from the RO cache
            return
        }
        writableCache.store(key, artifactFile, metaData)
    }

    override fun storeMissing(key: String?) {
        writableCache.storeMissing(key)
    }

    override fun lookup(key: String?): CachedExternalResource? {
        val lookup = writableCache.lookup(key)
        if (lookup != null) {
            return lookup
        }
        return readOnlyCache.lookup(key)
    }

    override fun clear(key: String?) {
        writableCache.clear(key)
    }
}
