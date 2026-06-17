/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.api.Incubating
import org.gradle.plugins.ide.eclipse.model.internal.PathUtil

/**
 * A wtp descriptor dependent module entry.
 */
class WbDependentModule @Incubating constructor(archiveName: String, deployPath: String?, handle: String?) : WbModuleEntry {
    /**
     * Get the archiveName property.
     *
     * @return the archiveName for this module.
     * @since 8.1
     */
    /**
     * Set the archiveName for this module.
     *
     * @param archiveName the archiveName value to set.
     * @since 8.1
     */
    @get:Incubating
    @set:Incubating
    var archiveName: String
    var deployPath: String
    var handle: String

    constructor(node: Node) : this((node.attribute("archiveName") as kotlin.String?)!!, node.attribute("deploy-path") as String?, node.attribute("handle") as String?)

    constructor(deployPath: String?, handle: String?) : this("", deployPath, handle)

    /**
     * Constructor for WbDependentModule
     *
     * @since 8.1
     */
    init {
        Preconditions.checkNotNull<String?>(archiveName)
        Preconditions.checkNotNull<String?>(deployPath)
        this.archiveName = archiveName
        this.deployPath = PathUtil.normalizePath(deployPath)
        this.handle = Preconditions.checkNotNull<String>(handle)
    }

    override fun appendNode(parentNode: Node) {
        val attributes: MutableMap<String?, Any?> = LinkedHashMap<String?, Any?>()
        attributes.put("archiveName", archiveName)
        attributes.put("deploy-path", deployPath)
        attributes.put("handle", handle)
        val node = parentNode.appendNode("dependent-module", attributes)
        node.appendNode("dependency-type").setValue("uses")
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (javaClass != o.javaClass) {
            return false
        }
        val that = o as WbDependentModule
        return Objects.equal(archiveName, that.archiveName) && Objects.equal(deployPath, that.deployPath) && Objects.equal(handle, that.handle)
    }

    override fun hashCode(): Int {
        var result: Int
        result = archiveName.hashCode()
        result = 31 * result + deployPath.hashCode()
        result = 31 * result + handle.hashCode()
        return result
    }

    override fun toString(): String {
        return ("WbDependentModule{"
                + "archiveName='" + archiveName + "\'"
                + "deployPath='" + deployPath + "\'"
                + ", handle='" + handle + "\'"
                + "}")
    }
}
