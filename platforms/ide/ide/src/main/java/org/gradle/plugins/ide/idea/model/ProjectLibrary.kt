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
import groovy.util.Node
import java.io.File

/**
 * A project-level IDEA library.
 */
class ProjectLibrary {
    private var name: String? = null

    /**
     * The type of the library.
     */
    var type: String? = null

    /**
     * A set of Jar files containing compiler classes.
     */
    var compilerClasspath: MutableSet<File?> = LinkedHashSet<File?>()
    /**
     * A set of Jar files or directories containing compiled code.
     */
    /**
     * A set of Jar files or directories containing source code.
     */
    var classes: MutableSet<File?> = LinkedHashSet<File?>()

    /**
     * A set of Jar files or directories containing javadoc.
     */
    var javadoc: MutableSet<File?> = LinkedHashSet<File?>()

    /**
     * A set of directories containing sources.
     */
    var sources: MutableSet<File?> = LinkedHashSet<File?>()

    /**
     * The name of the library.
     */
    fun getName(): String {
        return name!!
    }

    fun setName(name: String) {
        this.name = name
    }

    fun addToNode(parentNode: Node, pathFactory: PathFactory) {
        val libraryAttributes: MutableMap<String?, Any?> = LinkedHashMap<String?, Any?>()
        libraryAttributes.put("name", name)
        if (type != null) {
            libraryAttributes.put("type", type)
        }
        val libraryNode = parentNode.appendNode("library", libraryAttributes)
        val classesNode = libraryNode.appendNode("CLASSES")
        for (file in classes) {
            val attributes: MutableMap<String?, Any?> = LinkedHashMap<String?, Any?>()
            attributes.put("url", pathFactory.path(file).getUrl())
            classesNode.appendNode("root", attributes)
        }
        val javadocNode = libraryNode.appendNode("JAVADOC")
        for (file in javadoc) {
            val attributes: MutableMap<String?, Any?> = LinkedHashMap<String?, Any?>()
            attributes.put("url", pathFactory.path(file).getUrl())
            javadocNode.appendNode("root", attributes)
        }
        val sourcesNode = libraryNode.appendNode("SOURCES")
        for (file in sources) {
            val attributes: MutableMap<String?, Any?> = LinkedHashMap<String?, Any?>()
            attributes.put("url", pathFactory.path(file).getUrl())
            sourcesNode.appendNode("root", attributes)
        }

        if (compilerClasspath.size > 0) {
            val properties = libraryNode.appendNode("properties")
            val compilerClasspathNode = properties.appendNode("compiler-classpath")
            for (file in compilerClasspath) {
                val attributes: MutableMap<String?, Any?> = LinkedHashMap<String?, Any?>()
                attributes.put("url", pathFactory.path(file, true).getUrl())
                compilerClasspathNode.appendNode("root", attributes)
            }
        }
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) {
            return true
        }
        if (obj !is ProjectLibrary) {
            return false
        }
        val that = obj
        return Objects.equal(name, that.name)
                && Objects.equal(type, that.type)
                && Objects.equal(compilerClasspath, that.compilerClasspath)
                && Objects.equal(classes, that.classes)
                && Objects.equal(javadoc, that.javadoc)
                && Objects.equal(sources, that.sources)
    }

    override fun hashCode(): Int {
        var result: Int
        result = name.hashCode()
        result = 31 * result + (if (type != null) type.hashCode() else 0)
        result = 31 * result + compilerClasspath.hashCode()
        result = 31 * result + classes.hashCode()
        result = 31 * result + javadoc.hashCode()
        result = 31 * result + sources.hashCode()
        return result
    }
}
