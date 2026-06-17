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
package org.gradle.plugins.ide.idea.model

import com.google.common.base.Objects
import com.google.common.base.Strings
import com.google.common.collect.ImmutableMap
import com.google.common.collect.Sets
import groovy.util.Node

/**
 * Represents an orderEntry of type module-library in the iml XML.
 */
open class ModuleLibrary(
    classes: MutableCollection<out Path?>,
    javadoc: MutableCollection<out Path?>,
    sources: MutableCollection<out Path?>,
    jarDirectories: MutableCollection<JarDirectory?>,
    private var scope: String?
) : Dependency {
    /**
     * A set of Jar files or directories containing compiled code.
     */
    var classes: MutableSet<Path>

    /**
     * A set of directories containing Jar files.
     */
    var jarDirectories: MutableSet<JarDirectory>

    /**
     * A set of Jar files or directories containing Javadoc.
     */
    var javadoc: MutableSet<Path>

    /**
     * A set of Jar files or directories containing source code.
     */
    var sources: MutableSet<Path>

    /**
     * Whether the library is exported to dependent modules.
     */
    var isExported: Boolean = false

    init {
        this.classes = Sets.newLinkedHashSet<Path?>(classes)
        this.jarDirectories = Sets.newLinkedHashSet<JarDirectory?>(jarDirectories)
        this.javadoc = Sets.newLinkedHashSet<Path?>(javadoc)
        this.sources = Sets.newLinkedHashSet<Path?>(sources)
    }

    /**
     * The scope of this library. If `null`, the scope attribute is not added.
     */
    override fun getScope(): String? {
        return scope
    }

    override fun setScope(scope: String?) {
        this.scope = scope
    }

    override fun addToNode(parentNode: Node) {
        val orderEntryAttributes: MutableMap<String?, Any?> = LinkedHashMap<String?, Any?>()
        orderEntryAttributes.put("type", "module-library")
        orderEntryAttributes.putAll(this.attributeMapForScopeAndExported)
        val libraryNode = parentNode.appendNode("orderEntry", orderEntryAttributes).appendNode("library")
        val classesNode = libraryNode.appendNode("CLASSES")
        val javadocNode = libraryNode.appendNode("JAVADOC")
        val sourcesNode = libraryNode.appendNode("SOURCES")
        for (path in classes) {
            val attributes: MutableMap<String?, Any?> = LinkedHashMap<String?, Any?>()
            attributes.put("url", path.getUrl())
            classesNode.appendNode("root", attributes)
        }
        for (path in javadoc) {
            val attributes: MutableMap<String?, Any?> = LinkedHashMap<String?, Any?>()
            attributes.put("url", path.getUrl())
            javadocNode.appendNode("root", attributes)
        }
        for (path in sources) {
            val attributes: MutableMap<String?, Any?> = LinkedHashMap<String?, Any?>()
            attributes.put("url", path.getUrl())
            sourcesNode.appendNode("root", attributes)
        }
        for (jarDirectory in jarDirectories) {
            val attributes: MutableMap<String?, Any?> = LinkedHashMap<String?, Any?>()
            attributes.put("url", jarDirectory.getPath().getUrl())
            attributes.put("recursive", jarDirectory.isRecursive().toString())
            libraryNode.appendNode("jarDirectory", attributes)
        }
    }

    private val attributeMapForScopeAndExported: MutableMap<String?, Any?>
        get() {
            val builder = ImmutableMap.builder<String?, Any?>()
            if (this.isExported) {
                builder.put("exported", "")
            }
            if (scope != null && "COMPILE" != scope) {
                builder.put("scope", scope)
            }
            return builder.build()
        }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (javaClass != o.javaClass) {
            return false
        }
        val that = o as ModuleLibrary
        return Objects.equal(classes, that.classes)
                && Objects.equal(jarDirectories, that.jarDirectories)
                && Objects.equal(javadoc, that.javadoc)
                && scopeEquals(scope, that.scope)
                && Objects.equal(sources, that.sources)
    }

    override fun hashCode(): Int {
        var result: Int
        result = classes.hashCode()
        result = 31 * result + jarDirectories.hashCode()
        result = 31 * result + javadoc.hashCode()
        result = 31 * result + sources.hashCode()
        result = 31 * result + this.scopeHash
        return result
    }

    private val scopeHash: Int
        get() = if (!Strings.isNullOrEmpty(scope) && scope != "COMPILE") scope.hashCode() else 0

    override fun toString(): String {
        return ("ModuleLibrary{"
                + "classes=" + classes
                + ", jarDirectories=" + jarDirectories
                + ", javadoc=" + javadoc
                + ", sources=" + sources
                + ", scope='" + scope
                + "\'" + "}")
    }

    companion object {
        private fun scopeEquals(lhs: String?, rhs: String?): Boolean {
            if ("COMPILE" == lhs) {
                return Strings.isNullOrEmpty(rhs) || "COMPILE" == rhs
            } else if ("COMPILE" == rhs) {
                return Strings.isNullOrEmpty(lhs)
            } else {
                return Objects.equal(lhs, rhs)
            }
        }
    }
}
