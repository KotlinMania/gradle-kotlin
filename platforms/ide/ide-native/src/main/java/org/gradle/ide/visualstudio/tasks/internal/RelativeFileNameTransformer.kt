/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.ide.visualstudio.tasks.internal

import com.google.common.base.Joiner
import org.gradle.api.Transformer
import java.io.File
import java.io.IOException
import java.util.LinkedList

class RelativeFileNameTransformer private constructor(private val rootDir: File, private val currentDir: File) : Transformer<String?, File?> {
    override fun transform(file: File): String {
        val canonicalRoot: String?
        val canonicalFrom: String?
        val canonicalTo: String?
        try {
            canonicalRoot = rootDir.getCanonicalPath()
            canonicalFrom = currentDir.getCanonicalPath()
            canonicalTo = file.getCanonicalPath()
        } catch (e: IOException) {
            return file.getAbsolutePath()
        }

        return findRelativePathInRoot(canonicalRoot, canonicalFrom, canonicalTo)
    }

    private fun findRelativePathInRoot(root: String, from: String, to: String): String {
        if (!from.contains(root) || !to.contains(root)) {
            return to
        }

        val relativePath = findRelativePath(from, to)
        return if (relativePath.length == 0) "." else relativePath
    }

    private fun findRelativePath(from: String, to: String): String {
        val fromPath = splitPath(from)
        val toPath = splitPath(to)
        val relativePath: MutableList<String> = ArrayList<String>()

        while (!fromPath.isEmpty() && !toPath.isEmpty() && fromPath.get(0) == toPath.get(0)) {
            fromPath.removeFirst()
            toPath.removeFirst()
        }
        for (ignored in fromPath) {
            relativePath.add("..")
        }
        relativePath.addAll(toPath)
        return Joiner.on(File.separatorChar).join(relativePath)
    }

    private fun splitPath(path: String): LinkedList<String> {
        var pathFile: File? = File(path)
        val split = LinkedList<String>()
        while (pathFile != null) {
            split.add(0, pathFile.getName())
            pathFile = pathFile.getParentFile()
        }
        return split
    }

    companion object {
        fun forFile(rootDir: File, relativeFile: File): Transformer<String, File> {
            return RelativeFileNameTransformer(rootDir, relativeFile.getParentFile())
        }

        fun forDirectory(rootDir: File, currentDirectory: File): Transformer<String, File> {
            return RelativeFileNameTransformer(rootDir, currentDirectory)
        }

        fun from(file: File): Transformer<String, File> {
            if (file.isFile()) {
                val parentFile = file.getParentFile()
                return RelativeFileNameTransformer(parentFile, parentFile)
            }
            return RelativeFileNameTransformer(file, file)
        }
    }
}
