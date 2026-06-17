/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.plugins.ide.eclipse.model

import com.google.common.base.Objects
import com.google.common.base.Preconditions
import groovy.util.Node

/**
 * A wtp descriptor property entry.
 */
class WbProperty(name: String?, value: String?) : WbModuleEntry {
    var name: String
    var value: String?

    constructor(node: Node) : this(node.attribute("name") as String?, node.attribute("value") as String?)

    init {
        this.name = Preconditions.checkNotNull<String>(name)
        this.value = Preconditions.checkNotNull<String?>(value)
    }

    override fun appendNode(node: Node) {
        val attributes: MutableMap<String?, Any?> = LinkedHashMap<String?, Any?>()
        attributes.put("name", name)
        attributes.put("value", value)
        node.appendNode("property", attributes)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as WbProperty
        return Objects.equal(name, that.name) && Objects.equal(value, that.value)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(name, value)
    }

    override fun toString(): String {
        return "WbProperty{name='" + name + "', value='" + value + "'}"
    }
}
