/*
 * Copyright 2014 the original author or authors.
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

import com.google.common.base.Joiner
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.ivy.IvyExtraInfo
import org.gradle.util.internal.CollectionUtils.collect
import java.util.Collections
import java.util.function.Function
import javax.xml.namespace.QName

open class DefaultIvyExtraInfo : IvyExtraInfo {
    protected val extraInfo: MutableMap<NamespaceId?, String?>

    constructor() {
        this.extraInfo = LinkedHashMap<NamespaceId?, String?>()
    }

    constructor(extraInfo: MutableMap<NamespaceId?, String?>) {
        this.extraInfo = extraInfo
    }

    override fun get(name: String): String? {
        val foundEntries: MutableList<MutableMap.MutableEntry<NamespaceId?, String?>?> = ArrayList<MutableMap.MutableEntry<NamespaceId?, String?>?>()
        for (entry in extraInfo.entries) {
            if (entry.key!!.name == name) {
                foundEntries.add(entry)
            }
        }
        if (foundEntries.size > 1) {
            val allNamespaces = Joiner.on(", ").join(
                collect<String?, MutableMap.MutableEntry<NamespaceId?, String?>?>(
                    foundEntries,
                    Function { original: MutableMap.MutableEntry<NamespaceId?, String?>? -> original!!.key!!.namespace })
            )
            throw InvalidUserDataException(
                String.format(
                    "Cannot get extra info element named '%s' by name since elements with this name were found from multiple namespaces (%s).  Use get(String namespace, String name) instead.",
                    name,
                    allNamespaces
                )
            )
        }
        return if (foundEntries.size == 0) null else foundEntries.get(0)!!.value
    }

    override fun get(namespace: String, name: String): String? {
        return extraInfo.get(NamespaceId(namespace, name))
    }

    override fun asMap(): MutableMap<QName?, String?> {
        val map: MutableMap<QName?, String?> = LinkedHashMap<QName?, String?>()
        for (entry in extraInfo.entries) {
            map.put(QName(entry.key!!.namespace, entry.key!!.name), entry.value)
        }
        return Collections.unmodifiableMap<QName?, String?>(map)
    }
}
