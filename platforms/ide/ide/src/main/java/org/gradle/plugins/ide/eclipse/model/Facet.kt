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
 * A project facet.
 */
class Facet {
    /**
     * An `installed` facet is really present on an Eclipse project whereas facet type `fixed` means that
     * this facet is locked and cannot be simply removed. See also
     * [here](https://eclipse.org/articles/Article-BuildingProjectFacets/tutorial.html#defining.presets).
     */
    enum class FacetType {
        installed, fixed
    }

    var type: FacetType
    var name: String? = null
    var version: String? = null

    constructor() {
        type = FacetType.installed
    }

    constructor(node: Node) : this(FacetType.valueOf((node.name() as kotlin.String?)!!), node.attribute("facet") as String?, node.attribute("version") as String?)

    constructor(name: String?, version: String?) : this(FacetType.installed, name, version)

    constructor(type: FacetType, name: String?, version: String?) {
        Preconditions.checkNotNull<FacetType?>(type)
        Preconditions.checkNotNull<String?>(name)
        if (type == FacetType.installed) {
            Preconditions.checkNotNull<String?>(version)
        } else {
            Preconditions.checkArgument(version == null)
        }
        this.type = type
        this.name = name
        this.version = version
    }

    fun appendNode(node: Node) {
        val attributes: MutableMap<String?, Any?> = LinkedHashMap<String?, Any?>()
        attributes.put("facet", name)
        if (type == FacetType.installed) {
            attributes.put("version", version)
        }
        node.appendNode(type.name, attributes)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val facet = o as Facet
        return type == facet.type && Objects.equal(name, facet.name) && Objects.equal(version, facet.version)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(type, name, version)
    }

    override fun toString(): String {
        return "Facet{type='" + type + "', name='" + name + "', version='" + version + "'}"
    }
}
