/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.ear.descriptor.internal

import com.google.common.base.Objects
import groovy.namespace.QName
import groovy.util.Node
import org.apache.commons.lang3.StringUtils
import org.gradle.plugins.ear.descriptor.EarModule

open class DefaultEarModule : EarModule {
    private var path: String? = null
    private var altDeployDescriptor: String? = null

    constructor()

    constructor(path: String?) {
        this.path = path
    }

    override fun getPath(): String? {
        return path
    }

    override fun setPath(path: String?) {
        this.path = path
    }

    override fun getAltDeployDescriptor(): String? {
        return altDeployDescriptor
    }

    override fun setAltDeployDescriptor(altDeployDescriptor: String?) {
        this.altDeployDescriptor = altDeployDescriptor
    }

    override fun toXmlNode(parentModule: Node?, name: Any?): Node {
        val node = Node(parentModule, name, path)
        if (StringUtils.isNotEmpty(altDeployDescriptor)) {
            Node(parentModule, nodeNameFor("alt-dd", name), altDeployDescriptor)
        }
        return node
    }

    protected fun nodeNameFor(name: String, sampleName: Any?): Any? {
        if (sampleName is QName) {
            return QName(sampleName.getNamespaceURI(), name)
        }
        return name
    }

    override fun hashCode(): Int {
        var result: Int
        result = if (path != null) path.hashCode() else 0
        result = 31 * result + (if (altDeployDescriptor != null) altDeployDescriptor.hashCode() else 0)
        return result
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o !is DefaultEarModule) {
            return false
        }
        val that = o
        return Objects.equal(path, that.path) && Objects.equal(altDeployDescriptor, that.altDeployDescriptor)
    }
}
