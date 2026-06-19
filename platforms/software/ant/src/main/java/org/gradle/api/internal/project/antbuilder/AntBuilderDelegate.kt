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
package org.gradle.api.internal.project.antbuilder

import org.gradle.api.file.DirectoryTree
import org.gradle.api.tasks.util.PatternSet
import org.gradle.api.tasks.util.internal.IntersectionPatternSet
import org.gradle.internal.metaobject.DynamicObject
import org.gradle.internal.metaobject.DynamicObjectUtil
import java.io.File
import java.util.Collections

class AntBuilderDelegate(builder: Any?, private val antlibClassLoader: ClassLoader) {
    private val builder: DynamicObject

    private var current: Any? = null

    init {
        this.builder = DynamicObjectUtil.asDynamicObject(builder!!)
    }

    fun addFiles(childNodeName: String, params: Iterable<File>) {
        createNode(childNodeName, mutableMapOf<String?, Any?>(), Runnable {
            for (file in params) {
                val filename: String = maskFilename(file.getAbsolutePath())
                createNode("file", Collections.singletonMap<String?, Any?>("file", filename))
            }
        })
    }

    fun addDirectoryTrees(childNodeName: String, directoryTrees: MutableCollection<DirectoryTree>) {
        for (tree in directoryTrees) {
            if (!tree.getDir().exists()) {
                continue
            }

            val directory: String = maskFilename(tree.getDir().getAbsolutePath())
            createNode(childNodeName, Collections.singletonMap<String?, Any?>("dir", directory), Runnable {
                addPatternSet(tree.getPatterns())
            })
        }
    }

    private fun addPatternSet(patterns: PatternSet) {
        if (!patterns.getIncludeSpecsView().isEmpty() || !patterns.getExcludeSpecsView().isEmpty()) {
            throw UnsupportedOperationException("Cannot add include/exclude specs to Ant node. Only include/exclude patterns are currently supported.")
        }

        addPatternToAntBuilder(patterns)
    }

    private fun addPatternToAntBuilder(patterns: PatternSet) {
        if (patterns is IntersectionPatternSet) {
            createNode("and", mutableMapOf<String?, Any?>(), Runnable {
                addIncludesAndExcludes(patterns)
                addPatternToAntBuilder(patterns.getOther())
            })
        } else {
            addIncludesAndExcludes(patterns)
        }
    }

    private fun addIncludesAndExcludes(patterns: PatternSet) {
        createNode("and", mutableMapOf<String?, Any?>(), Runnable {
            val caseSensitive = patterns.isCaseSensitive()
            val includes = patterns.getIncludesView()
            if (!includes.isEmpty()) {
                createNode("or", mutableMapOf<String?, Any?>(), Runnable { addFilenames(includes, caseSensitive) }
                )
            }

            val excludes = patterns.getExcludesView()
            if (!excludes.isEmpty()) {
                createNode("not", mutableMapOf<String?, Any?>(), Runnable {
                    createNode("or", mutableMapOf<String?, Any?>(), Runnable { addFilenames(excludes, caseSensitive) }
                    )
                })
            }
        })
    }

    private fun addFilenames(filenames: Iterable<String>, caseSensitive: Boolean) {
        val props: MutableMap<String?, Any?> = HashMap<String?, Any?>(2)
        props.put("casesensitive", caseSensitive)
        for (filename in filenames) {
            props.put("name", maskFilename(filename))
            createNode("filename", props)
        }
    }

    fun taskdef(name: String, classname: String?) {
        try {
            this.project.invokeMethod("addTaskDefinition", name, antlibClassLoader.loadClass(classname))
        } catch (ex: ClassNotFoundException) {
            throw RuntimeException(ex)
        }
    }

    fun createNode(methodName: String, content: String?) {
        val node = builder.invokeMethod("createNode", methodName, content)
        nodeCompleted(current!!, node)
    }

    fun createNode(methodName: String, parameters: MutableMap<String?, Any?>?) {
        val node = builder.invokeMethod("createNode", methodName, parameters)
        nodeCompleted(current!!, node)
    }

    fun createNode(methodName: String, parameters: MutableMap<String?, Any?>?, closure: Runnable?) {
        val node = builder.invokeMethod("createNode", methodName, parameters)

        if (closure != null) {
            // push new node on stack
            val oldCurrent = current
            this.current = node
            closure.run()
            this.current = oldCurrent
        }

        nodeCompleted(current!!, node)
    }

    /**
     * A hook to allow nodes to be processed once they have had all of their
     * children applied.
     *
     * @param node   the current node being processed
     * @param parent the parent of the node being processed
     */
    private fun nodeCompleted(parent: Any, node: Any?) {
        builder.invokeMethod("nodeCompleted", parent, node)
    }

    val projectProperties: MutableMap<String?, Any?>?
        get() = this.project.invokeMethod("getProperties") as MutableMap<String?, Any?>?

    val project: DynamicObject
        get() = DynamicObjectUtil.asDynamicObject(builder.invokeMethod("getProject")!!)

    fun setSaveStreams(value: Boolean) {
        builder.invokeMethod("setSaveStreams", value)
    }

    companion object {
        /**
         * Masks a string against Ant property expansion.
         * This needs to be used when adding a File as a String property.
         *
         * @param string to mask
         *
         * @return The masked String
         */
        fun maskFilename(string: String): String {
            return string.replace("\\$".toRegex(), "\\$\\$")
        }
    }
}
