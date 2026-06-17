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
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import java.io.File
import java.io.IOException

/**
 * Path Factory.
 */
class PathFactory {
    private val variables: MutableList<Variable> = ArrayList<Variable>()
    private val varsByName: MutableMap<String?, File?> = HashMap<String?, File?>()

    fun addPathVariable(name: String?, dir: File): PathFactory {
        variables.add(Variable('$'.toString() + name + '$', dir.getAbsolutePath() + File.separator, dir))
        varsByName.put(name, dir)
        return this
    }

    /**
     * Creates a path for the given file.
     *
     * @param file The file to generate a path for
     * @param useFileScheme Whether 'file://' prefixed URI should be used even for JAR files
     */
    /**
     * Creates a path for the given file.
     */
    @JvmOverloads
    fun path(file: File, useFileScheme: Boolean = false): FilePath {
        var match: Variable? = null
        for (variable in variables) {
            if (file.getAbsolutePath() == variable.dir!!.getAbsolutePath()) {
                match = variable
                break
            }
            if (file.getAbsolutePath().startsWith(variable.prefix!!)) {
                if (match == null || variable.prefix.startsWith(match.prefix!!)) {
                    match = variable
                }
            }
        }

        if (match != null) {
            return Companion.resolvePath(match.dir!!, match.name, file)
        }

        // IDEA doesn't like the result of file.toURI() so use the absolute path instead
        val relPath = file.getAbsolutePath().replace(File.separatorChar, '/')
        val url: String = relativePathToURI(relPath, useFileScheme)
        return FilePath(file, url, url, relPath)
    }

    /**
     * Creates a path relative to the given path variable.
     */
    fun relativePath(pathVar: String?, file: File): FilePath {
        return Companion.resolvePath(varsByName.get(pathVar)!!, "$" + pathVar + "$", file)
    }

    /**
     * Creates a path for the given URL.
     */
    /**
     * Creates a path for the given URL.
     */
    @JvmOverloads
    fun path(url: String, relPath: String? = null): Path {
        try {
            var expandedUrl = url
            for (variable in variables) {
                expandedUrl = expandedUrl.replace(variable.name!!, variable.prefix!!)
            }
            if (expandedUrl.lowercase().startsWith("file://")) {
                expandedUrl = toUrl("file", File(expandedUrl.substring(7)).getCanonicalFile())
            } else if (expandedUrl.lowercase().startsWith("jar://")) {
                val parts: Array<String?> = expandedUrl.substring(6).split("!".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (parts.size == 2) {
                    expandedUrl = toUrl("jar", File(parts[0]).getCanonicalFile()) + "!" + parts[1]
                }
            }
            return Path(url, expandedUrl, relPath)
        } catch (ex: IOException) {
            throw throwAsUncheckedException(ex)
        }
    }

    private class Variable(val name: String?, val prefix: String?, val dir: File?)
    companion object {
        private fun resolvePath(rootDir: File, rootDirName: String?, file: File): FilePath {
            val relPath: String = getRelativePath(rootDir, rootDirName, file)
            val url = relativePathToURI(relPath)
            val canonicalUrl = relativePathToURI(file.getAbsolutePath().replace(File.separatorChar, '/'))
            return FilePath(file, url, canonicalUrl, relPath)
        }

        private fun toUrl(scheme: String?, file: File): String {
            return scheme + "://" + file.getAbsolutePath().replace(File.separatorChar, '/')
        }

        private fun getRelativePath(rootDir: File, rootDirString: String?, file: File): String {
            val relpath: String? = matchPathLists(getPathList(rootDir), getPathList(file))
            return if (relpath != null) rootDirString + "/" + relpath else file.getAbsolutePath().replace(File.separatorChar, '/')
        }

        private fun relativePathToURI(relpath: String, useFileScheme: Boolean = false): String {
            if (relpath.endsWith(".jar") && !useFileScheme) {
                return "jar://" + relpath + "!/"
            } else {
                return "file://" + relpath
            }
        }

        private fun getPathList(f: File): MutableList<String?> {
            try {
                val list: MutableList<String?> = ArrayList<String?>()
                var r = f.getCanonicalFile()
                while (r != null) {
                    val parent = r.getParentFile()
                    list.add(if (parent != null) r.getName() else r.getAbsolutePath())
                    r = parent
                }
                return list
            } catch (ex: IOException) {
                throw throwAsUncheckedException(ex)
            }
        }

        private fun matchPathLists(r: MutableList<String?>, f: MutableList<String?>): String? {
            val s = StringBuilder()

            // eliminate the common root
            var i = r.size - 1
            var j = f.size - 1

            if (r.get(i) != f.get(j)) {
                // no common root
                return null
            }

            while (i >= 0 && j >= 0 && Objects.equal(r.get(i), f.get(j))) {
                i--
                j--
            }

            // for each remaining level in the relativeTo path, add a ..
            while (i >= 0) {
                s.append("../")
                i--
            }

            // for each level in the file path, add the path
            while (j >= 1) {
                s.append(f.get(j)).append("/")
                j--
            }

            // add the file name
            if (j == 0) {
                s.append(f.get(j))
            }

            return s.toString()
        }
    }
}
