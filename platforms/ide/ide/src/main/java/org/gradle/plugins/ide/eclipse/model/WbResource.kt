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
 * A wtp descriptor resource entry.
 */
class WbResource(deployPath: String?, sourcePath: String?) : WbModuleEntry {
    var deployPath: String
    var sourcePath: String?

    constructor(node: Node) : this(node.attribute("deploy-path") as String?, node.attribute("source-path") as String?)

    init {
        Preconditions.checkNotNull<String?>(deployPath)
        Preconditions.checkNotNull<String?>(sourcePath)
        this.deployPath = PathUtil.normalizePath(deployPath)
        this.sourcePath = PathUtil.normalizePath(sourcePath)
    }

    override fun appendNode(node: Node) {
        val attributes: MutableMap<String?, Any?> = LinkedHashMap<String?, Any?>()
        attributes.put("deploy-path", deployPath)
        attributes.put("source-path", sourcePath)
        node.appendNode("wb-resource", attributes)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as WbResource
        return Objects.equal(deployPath, that.deployPath) && Objects.equal(sourcePath, that.sourcePath)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(deployPath, sourcePath)
    }

    override fun toString(): String {
        return "WbResource{deployPath='" + deployPath + "', sourcePath='" + sourcePath + "'}"
    }
}
