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

import com.google.common.base.Joiner
import com.google.common.base.Objects
import com.google.common.collect.ImmutableList
import groovy.util.Node
import java.io.File
import java.util.Arrays

/**
 * SourceFolder.path contains only project relative path.
 */
class SourceFolder : AbstractClasspathEntry {
    var output: String?
    var includes: MutableList<String?>
    var excludes: MutableList<String?>

    //optional
    private var dir: File? = null
    private var name: String? = null

    constructor(node: Node) : super(node) {
        this.output = normalizePath(node.attribute("output") as String?)
        this.includes = parseNodeListAttribute(node, "including")
        this.excludes = parseNodeListAttribute(node, "excluding")
    }

    private fun parseNodeListAttribute(node: Node, attributeName: String?): MutableList<String?> {
        val attribute = node.attribute(attributeName)
        if (attribute == null) {
            return ImmutableList.of<String?>()
        } else {
            return Arrays.asList<String?>(*(attribute as String).split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
        }
    }

    constructor(projectRelativePath: String?, output: String?) : super(projectRelativePath) {
        this.output = normalizePath(output)
        this.includes = ImmutableList.of<String?>()
        this.excludes = ImmutableList.of<String?>()
    }

    fun getDir(): File {
        return dir!!
    }

    fun setDir(dir: File) {
        this.dir = dir
    }

    fun getName(): String {
        return name!!
    }

    fun setName(name: String) {
        this.name = name
    }

    override fun getKind(): String {
        return "src"
    }

    val absolutePath: String
        get() = dir!!.getAbsolutePath()

    @JvmOverloads
    fun trim(prefix: String? = null) {
        if (prefix != null) {
            name = prefix + "-" + name
        }
        path = name
    }

    override fun appendNode(node: Node?) {
        val attributes: MutableMap<String?, Any?> = LinkedHashMap<String?, Any?>()
        attributes.put("including", JOINER.join(includes))
        attributes.put("excluding", JOINER.join(excludes))
        attributes.put("output", output)
        addClasspathEntry(node, attributes)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        if (!super.equals(o)) {
            return false
        }
        val that = o as SourceFolder
        return exported == that.exported && Objects.equal(accessRules, that.accessRules)
                && Objects.equal(excludes, that.excludes)
                && Objects.equal(includes, that.includes)
                && Objects.equal(getNativeLibraryLocation(), that.getNativeLibraryLocation())
                && Objects.equal(output, that.output)
                && Objects.equal(path, that.path)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(exported, accessRules, excludes, includes, getNativeLibraryLocation(), output, path)
    }

    override fun toString(): String {
        return ("SourceFolder{path='" + path + "', dir='" + dir + "', nativeLibraryLocation='" + getNativeLibraryLocation() + "', exported=" + exported
                + ", accessRules=" + accessRules + ", output='" + output + "', excludes=" + excludes + ", includes=" + includes + "}")
    }

    companion object {
        private val JOINER = Joiner.on("|")
    }
}
