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
import org.gradle.plugins.ide.eclipse.model.internal.PathUtil

/**
 * A classpath entry representing an output folder.
 */
class Output(path: String?) : ClasspathEntry {
    var path: String

    constructor(node: Node) : this(node.attribute("path") as String?)

    init {
        Preconditions.checkNotNull<String?>(path)
        this.path = PathUtil.normalizePath(path)
    }

    override fun getKind(): String {
        return "output"
    }

    override fun appendNode(node: Node) {
        val attributes: MutableMap<String?, Any?> = LinkedHashMap<String?, Any?>()
        attributes.put("kind", getKind())
        attributes.put("path", path)
        node.appendNode("classpathentry", attributes)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val output = o as Output
        return Objects.equal(path, output.path)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(path)
    }

    override fun toString(): String {
        return "Output{path='" + path + "'}"
    }
}
