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
import groovy.util.Node
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.plugins.ide.eclipse.model.internal.FileReferenceFactory

/**
 * Common superclass for the library elements.
 */
abstract class AbstractLibrary : AbstractClasspathEntry {
    var sourcePath: FileReference? = null
    private var javadocPath: FileReference? = null
    private var library: FileReference? = null
    var moduleVersion: ModuleVersionIdentifier? = null

    constructor(node: Node, fileReferenceFactory: FileReferenceFactory) : super(node) {
        val javadocLocation = getEntryAttributes().get(ATTRIBUTE_JAVADOC_LOCATION) as String?
        javadocPath = fileReferenceFactory.fromJarURI(javadocLocation)
    }

    constructor(library: FileReference) : super(library.getPath()) {
        this.library = library
    }

    fun getJavadocPath(): FileReference? {
        return javadocPath
    }

    fun setJavadocPath(path: FileReference?) {
        this.javadocPath = path
        if (path != null) {
            val location = path.getJarURL()
            getEntryAttributes().put(ATTRIBUTE_JAVADOC_LOCATION, location)
        } else {
            getEntryAttributes().remove(ATTRIBUTE_JAVADOC_LOCATION)
        }
    }

    fun getLibrary(): FileReference? {
        return library
    }

    fun setLibrary(library: FileReference) {
        this.library = library
        setPath(library.getPath())
    }

    override fun appendNode(node: Node?) {
        val attributes: MutableMap<String?, Any?> = LinkedHashMap<String?, Any?>()
        attributes.put("sourcepath", if (sourcePath == null) null else sourcePath!!.getPath())
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
        val that = o as AbstractLibrary
        return isExported() == that.isExported() && Objects.equal(getAccessRules(), that.getAccessRules())
                && Objects.equal(getJavadocPath(), that.getJavadocPath())
                && Objects.equal(getNativeLibraryLocation(), that.getNativeLibraryLocation())
                && Objects.equal(getPath(), that.getPath())
                && Objects.equal(this.sourcePath, that.sourcePath)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(getPath(), getNativeLibraryLocation(), isExported(), getAccessRules(), this.sourcePath, getJavadocPath())
    }

    override fun toString(): String {
        return ("{path='" + getPath() + "', nativeLibraryLocation='" + getNativeLibraryLocation() + "', exported=" + isExported()
                + ", accessRules=" + getAccessRules() + ", sourcePath='" + sourcePath + "', javadocPath='" + javadocPath + "', id='" + moduleVersion + "'}")
    }

    companion object {
        private const val ATTRIBUTE_JAVADOC_LOCATION = "javadoc_location"
    }
}
