/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.ide.xcode.internal.xcodeproj

import com.google.common.base.Preconditions
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import java.util.Collections

/**
 * A collection of files in Xcode's virtual filesystem hierarchy.
 */
class PBXGroup(name: String?, path: String?, sourceTree: SourceTree?) : PBXReference(name, path, sourceTree) {
    val children: MutableList<PBXReference?>
    private val childGroupsByName: LoadingCache<String?, PBXGroup>

    // Unfortunately, we can't determine this at constructor time, because CacheBuilder
    // calls our constructor and it's not easy to pass arguments to it.
    private var sortPolicy: SortPolicy?

    init {
        sortPolicy = SortPolicy.BY_NAME
        children = ArrayList<PBXReference?>()

        childGroupsByName = CacheBuilder.newBuilder().build<String?, PBXGroup?>(
            object : CacheLoader<String?, PBXGroup?>() {
                @Throws(Exception::class)
                override fun load(key: String?): PBXGroup {
                    val group = PBXGroup(key, null, SourceTree.GROUP)
                    children.add(group)
                    return group
                }
            })
    }

    fun getOrCreateChildGroupByName(name: String): PBXGroup {
        return childGroupsByName.getUnchecked(name)
    }

    fun getSortPolicy(): SortPolicy? {
        return sortPolicy
    }

    fun setSortPolicy(sortPolicy: SortPolicy?) {
        this.sortPolicy = Preconditions.checkNotNull<SortPolicy?>(sortPolicy)
    }

    override fun isa(): String {
        return "PBXGroup"
    }

    override fun serializeInto(s: XcodeprojSerializer) {
        super.serializeInto(s)

        if (sortPolicy == SortPolicy.BY_NAME) {
            Collections.sort<PBXReference?>(children, object : Comparator<PBXReference?> {
                override fun compare(o1: PBXReference, o2: PBXReference): Int {
                    return o1.getName().compareTo(o2.getName())
                }
            })
        }

        s.addField("children", children)
    }

    /**
     * Method by which group contents will be sorted.
     */
    enum class SortPolicy {
        /**
         * By name, in default Java sort order.
         */
        BY_NAME,

        /**
         * Group contents will not be sorted, and will remain in the
         * order they were added.
         */
        UNSORTED
    }
}
