/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.internal.resource.transfer

import org.gradle.api.resources.ResourceException
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.ReadableContent
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import java.io.IOException
import java.net.URI
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class DefaultExternalResourceConnector(private val accessor: ExternalResourceAccessor, private val lister: ExternalResourceLister, private val uploader: ExternalResourceUploader) :
    ExternalResourceConnector {
    @Throws(ResourceException::class)
    override fun <T> withContent(location: ExternalResourceName, revalidate: Boolean, action: ExternalResource.ContentAndMetadataAction<T?>): T? {
        statistics.resource(location.uri)
        return accessor.withContent<T?>(location, revalidate, action)
    }

    override fun getMetaData(location: ExternalResourceName, revalidate: Boolean): ExternalResourceMetaData? {
        statistics.metadata(location.uri)
        return accessor.getMetaData(location, revalidate)
    }

    override fun list(parent: ExternalResourceName): MutableList<String?>? {
        statistics.list(parent.uri)
        return lister.list(parent)
    }

    @Throws(IOException::class)
    override fun upload(resource: ReadableContent, destination: ExternalResourceName) {
        statistics.upload(destination.uri)
        uploader.upload(resource, destination)
    }

    interface ExternalResourceAccessStats {
        enum class Mode {
            none,
            count,
            trace;

            fun create(): ExternalResourceAccessStats {
                when (this) {
                    Mode.none -> return NoOpStats.Companion.INSTANCE
                    Mode.count -> return CountingStats()
                    Mode.trace -> return MemoizingStats()
                }
                throw UnsupportedOperationException()
            }
        }

        fun resource(location: URI?)

        fun metadata(location: URI?)

        fun list(parent: URI?)

        fun upload(destination: URI?)

        fun reset()
    }

    private class NoOpStats : ExternalResourceAccessStats {
        override fun resource(location: URI?) {
        }

        override fun metadata(location: URI?) {
        }

        override fun list(parent: URI?) {
        }

        override fun upload(destination: URI?) {
        }

        override fun reset() {
        }

        override fun toString(): String {
            return "External resources access stats are not recorded. Run Gradle with -D" + SYSPROP_KEY + "=(count|trace) to record statistics"
        }

        companion object {
            val INSTANCE: NoOpStats = NoOpStats()
        }
    }

    private open class CountingStats : ExternalResourceAccessStats {
        private val resourceCount = AtomicInteger()
        private val metadataCount = AtomicInteger()
        private val listCount = AtomicInteger()
        private val uploadCount = AtomicInteger()

        override fun resource(location: URI?) {
            resourceCount.incrementAndGet()
        }

        override fun metadata(location: URI?) {
            metadataCount.incrementAndGet()
        }

        override fun list(parent: URI?) {
            listCount.incrementAndGet()
        }

        override fun upload(destination: URI?) {
            uploadCount.incrementAndGet()
        }

        @Synchronized
        override fun reset() {
            resourceCount.set(0)
            metadataCount.set(0)
            listCount.set(0)
            uploadCount.set(0)
        }

        override fun toString(): String {
            val sb = StringBuilder("External resources connector statistics: \n")
            sb.append("   - Resources fetched : ").append(resourceCount.get()).append("\n")
            sb.append("   - Metadata fetched  : ").append(metadataCount.get()).append("\n")
            sb.append("   - Lists             : ").append(listCount.get()).append("\n")
            sb.append("   - Uploads           : ").append(uploadCount.get()).append("\n")
            return sb.toString()
        }
    }

    private class MemoizingStats : CountingStats() {
        private val resources: MutableMap<URI?, Int?> = HashMap<URI?, Int?>()
        private val metadata: MutableMap<URI?, Int?> = HashMap<URI?, Int?>()
        private val lists: MutableMap<URI?, Int?> = HashMap<URI?, Int?>()
        private val uploads: MutableMap<URI?, Int?> = HashMap<URI?, Int?>()

        @Synchronized
        fun record(container: MutableMap<URI?, Int?>, uri: URI?) {
            val count = container.get(uri)
            if (count == null) {
                container.put(uri, 1)
            } else {
                container.put(uri, count + 1)
            }
        }

        override fun list(parent: URI?) {
            record(lists, parent)
            super.list(parent)
        }

        override fun metadata(location: URI?) {
            record(metadata, location)
            super.metadata(location)
        }

        override fun resource(location: URI?) {
            record(resources, location)
            super.resource(location)
        }

        override fun upload(destination: URI?) {
            record(uploads, destination)
            super.upload(destination)
        }

        @Synchronized
        override fun reset() {
            super.reset()
            resources.clear()
            metadata.clear()
            lists.clear()
            uploads.clear()
        }

        override fun toString(): String {
            val sb = StringBuilder(super.toString())
            statsFor("fetched resources", resources, sb, 10)
            statsFor("fetched metadata", metadata, sb, 10)
            statsFor("lists queries", lists, sb, 10)
            statsFor("uploaded URIs", uploads, sb, 10)
            return sb.toString()
        }

        fun statsFor(label: String?, stats: MutableMap<URI?, Int?>, sb: StringBuilder, max: Int) {
            if (stats.isEmpty()) {
                return
            }
            val entries: MutableList<MutableMap.MutableEntry<URI?, Int?>> = ArrayList<MutableMap.MutableEntry<URI?, Int?>>(stats.entries)
            Collections.sort(entries, object : Comparator<MutableMap.MutableEntry<URI?, Int?>> {
                override fun compare(o1: MutableMap.MutableEntry<URI?, Int?>, o2: MutableMap.MutableEntry<URI?, Int?>): Int {
                    return o2.value!! - o1.value!!
                }
            })
            sb.append("Top ").append(max).append(" most ").append(label).append("\n")
            var cpt = 0
            for (entry in entries) {
                sb.append("   ").append(entry.key).append(" (").append(entry.value).append(" times)\n")
                if (++cpt == max) {
                    break
                }
            }
        }
    }

    companion object {
        private const val SYSPROP_KEY = "gradle.externalresources.recordstats"
        private val STATS_MODE = ExternalResourceAccessStats.Mode.valueOf(System.getProperty(SYSPROP_KEY, "none"))
        val statistics: ExternalResourceAccessStats = STATS_MODE.create()
    }
}
