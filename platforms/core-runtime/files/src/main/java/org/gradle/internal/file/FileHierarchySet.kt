/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.internal.file

import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Strings
import com.google.common.collect.ImmutableList
import java.io.File
import java.util.ArrayDeque
import java.util.Deque
import javax.annotation.CheckReturnValue
import kotlin.math.min

/**
 * An immutable set of directory trees. Intended to be use to efficiently determine whether a particular file is contained in a set of directories or not.
 */
// TODO Make this into an interface once we can migrate to Java 8+.
abstract class FileHierarchySet {
    /**
     * Checks if the given file is contained in the set.
     *
     * A file is contained in the set if it or one of its ancestors has
     * been added to the set.
     */
    abstract fun contains(file: File): Boolean

    /**
     * Checks if the given path is contained in the set.
     *
     * A path is contained in the set if it or one of its ancestors has
     * been added to the set.
     */
    abstract fun contains(path: String): Boolean

    /**
     * Whether this hierarchy is empty, i.e. contains no directories.
     */
    abstract val isEmpty: Boolean

    /**
     * Returns a set that contains the union of this set and the given path. If the given path is a directory, the set will contain the directory itself, plus all its descendants.
     */
    @CheckReturnValue
    abstract fun plus(path: File): FileHierarchySet?

    /**
     * Returns a set that contains the union of this set and the given absolute path. The set contains the path itself, plus all its descendants.
     */
    @CheckReturnValue
    abstract fun plus(absolutePath: String): FileHierarchySet?

    /**
     * Visit the root of each complete hierarchy contained in the set.
     */
    abstract fun visitRoots(visitor: RootVisitor)

    interface RootVisitor {
        fun visitRoot(absolutePath: String)
    }

    @VisibleForTesting
    internal class PrefixFileSet : FileHierarchySet {
        private val rootNode: Node

        constructor(rootDir: File) : this(toAbsolutePath(rootDir))

        constructor(rootPath: String) {
            val path: String = removeTrailingSeparator(rootPath)
            this.rootNode = Node(path)
        }

        private constructor(rootNode: Node) {
            this.rootNode = rootNode
        }

        @VisibleForTesting
        fun flatten(): MutableList<String> {
            val prefixes: MutableList<String> = ArrayList<String>()
            rootNode.visitHierarchy(0, object : NodeVisitor {
                override fun visitNode(depth: Int, node: Node) {
                    if (depth == 0) {
                        prefixes.add(node.prefix)
                    } else {
                        prefixes.add(depth.toString() + ":" + node.prefix.replace(File.separatorChar, '/'))
                    }
                }
            })
            return prefixes
        }

        override fun contains(path: String): Boolean {
            return rootNode.contains(path, 0)
        }

        override val isEmpty: Boolean
            get() {
                return false
            }

        override fun contains(file: File): Boolean {
            return rootNode.contains(file.getPath(), 0)
        }

        override fun plus(rootDir: File): FileHierarchySet {
            return plus(toAbsolutePath(rootDir))
        }

        override fun plus(absolutePath: String): FileHierarchySet {
            val newRoot = rootNode.plus(removeTrailingSeparator(absolutePath))
            if (newRoot === rootNode) {
                return this
            }
            return PrefixFileSet(newRoot)
        }

        override fun visitRoots(visitor: RootVisitor) {
            val prefixStack: Deque<String> = ArrayDeque<String>()
            rootNode.visitHierarchy(0, object : NodeVisitor {
                override fun visitNode(depth: Int, node: Node) {
                    while (prefixStack.size > depth) {
                        prefixStack.removeLast()
                    }
                    if (node.children.isEmpty()) {
                        val root: String
                        if (prefixStack.isEmpty()) {
                            root = node.prefix
                        } else {
                            val builder = StringBuilder()
                            for (prefix in prefixStack) {
                                builder.append(prefix)
                                if (!prefix.isEmpty()) {
                                    builder.append(File.separatorChar)
                                }
                            }
                            builder.append(node.prefix)
                            root = builder.toString()
                        }
                        visitor.visitRoot(root)
                    } else {
                        prefixStack.add(node.prefix)
                    }
                }
            })
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }

            val that = o as PrefixFileSet

            return rootNode == that.rootNode
        }

        override fun hashCode(): Int {
            return rootNode.hashCode()
        }

        override fun toString(): String {
            val builder = StringBuilder()
            rootNode.visitHierarchy(0, object : NodeVisitor {
                private var first = true

                override fun visitNode(depth: Int, node: Node) {
                    if (first) {
                        first = false
                    } else {
                        builder.append("\n")
                    }
                    builder.append(Strings.repeat(" ", depth * 2))
                    builder.append(node.prefix)
                }
            })
            return builder.toString()
        }

        companion object {
            private fun toAbsolutePath(rootDir: File): String {
                assert(rootDir.isAbsolute())
                return rootDir.getAbsolutePath()
            }

            private fun removeTrailingSeparator(absolutePath: String): String {
                var absolutePath = absolutePath
                if (absolutePath == "/") {
                    absolutePath = ""
                } else if (absolutePath.endsWith(File.separator)) {
                    absolutePath = absolutePath.substring(0, absolutePath.length - 1)
                }
                return absolutePath
            }
        }
    }

    private class Node {
        internal val prefix: String
        internal val children: MutableList<Node>

        internal constructor(prefix: String) {
            this.prefix = prefix
            this.children = ImmutableList.of<Node>()
        }

        constructor(prefix: String, children: MutableList<Node>) {
            this.prefix = prefix
            this.children = children
        }

        fun plus(path: String): Node {
            return plusWithCommonPrefixLength(path, sizeOfCommonPrefix(path, 0))
        }

        fun plusWithCommonPrefixLength(path: String, prefixLen: Int): Node {
            val maxPos = min(prefix.length, path.length)
            if (prefixLen == maxPos) {
                if (prefix.length == path.length) {
                    // Path == prefix
                    if (children.isEmpty()) {
                        return this
                    }
                    return Node(path)
                }
                if (prefix.length < path.length) {
                    // Path is a descendant of this
                    if (children.isEmpty()) {
                        return this
                    }
                    val startNextSegment = if (prefixLen == 0) 0 else prefixLen + 1
                    val nextSegment = path.substring(startNextSegment)
                    val merged: MutableList<Node> = ArrayList<Node>(children.size + 1)
                    var matched = false
                    for (child in children) {
                        if (!matched) {
                            val childCommonPrefix = child.sizeOfCommonPrefix(path, startNextSegment)
                            if (childCommonPrefix > 0) {
                                merged.add(child.plusWithCommonPrefixLength(nextSegment, childCommonPrefix))
                                matched = true
                                continue
                            }
                        }
                        merged.add(child)
                    }
                    if (!matched) {
                        merged.add(Node(nextSegment))
                    }
                    return Node(prefix, merged)
                } else {
                    // Path is an ancestor of this
                    return Node(path)
                }
            }
            val commonPrefix = prefix.substring(0, prefixLen)

            val newChildrenStartIndex = if (prefixLen == 0) 0 else prefixLen + 1

            val newThis = Node(prefix.substring(newChildrenStartIndex), children)
            val sibling = Node(path.substring(newChildrenStartIndex))
            return Node(commonPrefix, ImmutableList.of<Node>(newThis, sibling))
        }

        fun sizeOfCommonPrefix(path: String, offset: Int): Int {
            return FilePathUtil.sizeOfCommonPrefix(prefix, path, offset)
        }

        /**
         * This uses an optimized version of [regionMatches]
         * which does not check for negative indices or integer overflow.
         */
        fun isChildOfOrThis(filePath: String, offset: Int): Boolean {
            if (prefix.isEmpty()) {
                return true
            }

            val pathLength = filePath.length
            val prefixLength = prefix.length
            val endOfThisSegment = prefixLength + offset
            if (pathLength < endOfThisSegment) {
                return false
            }
            var i = prefixLength - 1
            var j = endOfThisSegment - 1
            while (i >= 0) {
                if (prefix.get(i) != filePath.get(j)) {
                    return false
                }
                i--
                j--
            }
            return endOfThisSegment == pathLength || filePath.get(endOfThisSegment) == File.separatorChar
        }

        fun contains(filePath: String, offset: Int): Boolean {
            if (!isChildOfOrThis(filePath, offset)) {
                return false
            }
            if (children.isEmpty()) {
                return true
            }

            val startNextSegment = if (prefix.isEmpty()) offset else offset + prefix.length + 1
            for (child in children) {
                if (child.contains(filePath, startNextSegment)) {
                    return true
                }
            }
            return false
        }

        fun visitHierarchy(depth: Int, visitor: NodeVisitor) {
            visitor.visitNode(depth, this)
            for (child in children) {
                child.visitHierarchy(depth + 1, visitor)
            }
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }

            val node = o as Node

            if (prefix != node.prefix) {
                return false
            }
            return children == node.children
        }

        override fun hashCode(): Int {
            var result = prefix.hashCode()
            result = 31 * result + children.hashCode()
            return result
        }

        override fun toString(): String {
            return prefix
        }
    }

    private interface NodeVisitor {
        fun visitNode(depth: Int, node: Node)
    }

    companion object {
        /**
         * The empty set.
         */
        @JvmStatic
        fun empty(): FileHierarchySet {
            return EMPTY
        }

        private val EMPTY: FileHierarchySet = object : FileHierarchySet() {
            override fun contains(file: File): Boolean {
                return false
            }

            override fun contains(path: String): Boolean {
                return false
            }

            override val isEmpty: Boolean
                get() {
                    return true
                }

            override fun plus(rootDir: File): FileHierarchySet {
                return PrefixFileSet(rootDir)
            }

            override fun plus(absolutePath: String): FileHierarchySet {
                return PrefixFileSet(absolutePath)
            }

            override fun visitRoots(visitor: RootVisitor) {
            }

            override fun toString(): String {
                return "EMPTY"
            }
        }
    }
}
