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
package org.gradle.plugins.ide.eclipse.model.internal

import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.plugins.ide.eclipse.model.FileReference
import java.io.File
import java.net.URI
import java.net.URISyntaxException

class FileReferenceFactory {
    private val variables: MutableMap<String?, File?> = HashMap<String?, File?>()

    /**
     * Adds a path variable
     */
    fun addPathVariable(name: String?, dir: File?) {
        variables.put(name, dir)
    }

    /**
     * Creates a reference to the given file. Returns null for a null file.
     */
    fun fromFile(file: File?): FileReference? {
        if (file == null) {
            return null
        }
        var path: String? = null
        var usedVar = false
        for (entry in variables.entries) {
            val rootDirPath = entry.value!!.getAbsolutePath()
            val filePath = file.getAbsolutePath()
            if (filePath == rootDirPath) {
                path = entry.key
                usedVar = true
                break
            }
            if (filePath.startsWith(rootDirPath + File.separator)) {
                val len = rootDirPath.length
                path = entry.key + filePath.substring(len)
                usedVar = true
                break
            }
        }
        path = PathUtil.normalizePath(if (path != null) path else file.getAbsolutePath())
        return FileReferenceImpl(file, path, usedVar)
    }

    /**
     * Creates a reference to the given path. Returns null for null path
     */
    fun fromPath(path: String?): FileReference? {
        if (path == null) {
            return null
        }
        return FileReferenceImpl(File(path), path, false)
    }

    /**
     * Creates a reference to the given path. Returns null for null path
     */
    fun fromJarURI(jarURI: String?): FileReference? {
        if (jarURI == null) {
            return null
        }
        //cut the pre and postfix of this url
        var fileURI: URI? = null
        try {
            fileURI = URI(jarURI.replace("jar:", "").replace("!/", ""))
        } catch (e: URISyntaxException) {
            throwAsUncheckedException(e)
        }
        val file = File(fileURI)
        val path = PathUtil.normalizePath(file.getAbsolutePath())
        return FileReferenceImpl(file, path, false)
    }

    /**
     * Creates a reference to the given path containing a variable reference. Returns null for null variable path
     */
    fun fromVariablePath(path: String?): FileReference? {
        if (path == null) {
            return null
        }
        for (entry in variables.entries) {
            val prefix = entry.key + "/"
            if (path.startsWith(prefix)) {
                val file = File(entry.value, path.substring(prefix.length))
                return FileReferenceImpl(file, path, true)
            }
        }
        return fromPath(path)
    }

    private class FileReferenceImpl(val file: File, val path: String?, val relativeToPathVariable: Boolean) : FileReference {
        override fun equals(obj: Any?): Boolean {
            if (obj === this) {
                return true
            }
            if (obj == null || obj.javaClass != javaClass) {
                return false
            }
            val ref = obj as FileReference
            return file == ref.getFile()
        }

        override fun getFile(): File {
            return file
        }

        override fun getPath(): String? {
            return path
        }

        override fun getJarURL(): String {
            //windows needs an additional backslash in jar urls
            return "jar:" + file.toURI() + "!/"
        }

        override fun isRelativeToPathVariable(): Boolean {
            return relativeToPathVariable
        }

        override fun toString(): String {
            return "{file='" + file + "', path='" + path + "', jarUrl='" + getJarURL() + "'}"
        }

        override fun hashCode(): Int {
            return file.hashCode()
        }
    }
}
