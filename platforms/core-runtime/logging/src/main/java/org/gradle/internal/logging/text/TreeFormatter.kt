/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.internal.logging.text

import org.gradle.api.internal.GeneratedSubclasses
import org.gradle.util.internal.TextUtil
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Constructs a tree of diagnostic messages.
 */
class TreeFormatter @JvmOverloads constructor(private val alwaysChildrenOnNewlines: Boolean = false) : DiagnosticsVisitor {
    private val buffer = StringBuilder()
    private val original: AbstractStyledTextOutput
    private var current: Node?
    private var prefixer: Prefixer = DefaultPrefixer()

    /**
     * By default, if a child node + the parent node have only a short amount of total text,
     * the formatter will merge them both onto the same line.
     *
     *
     * If this is set to `true`, this behavior will be disabled.
     *
     * @param alwaysChildrenOnNewlines `true` = never merge nodes; `false` (default) = merge nodes with short total text
     */
    init {
        this.original = object : AbstractStyledTextOutput() {
            override fun doAppend(text: String?) {
                buffer.append(text)
            }
        }
        this.current = TreeFormatter.Node()
    }

    override fun toString(): String {
        return buffer.toString()
    }

    /**
     * Starts a new node with the given text.
     */
    override fun node(text: String): TreeFormatter {
        if (current!!.state == State.TraverseChildren) {
            // First child node
            current = TreeFormatter.Node(current, text)
        } else {
            // A sibling node
            current!!.state = State.Done
            current = TreeFormatter.Node(current!!.parent, text)
        }
        if (current!!.isTopLevelNode) {
            // A new top level node, implicitly finish the previous node
            if (current !== current!!.parent!!.firstChild) {
                // Not the first top level node
                original.append(TextUtil.getPlatformLineSeparator())
            }
            original.append(text)
            current!!.valueWritten = true
        }
        return this
    }

    fun blankLine() {
        node("")
    }

    /**
     * Starts a new node with the given type name.
     */
    fun node(type: Class<*>): TreeFormatter {
        // Implementation is currently dumb, can be made smarter
        if (type.isInterface()) {
            node("Interface ")
        } else {
            node("Class ")
        }
        appendType(type)
        return this
    }

    /**
     * Appends text to the current node.
     */
    fun append(text: CharSequence?): TreeFormatter {
        if (current!!.state == State.CollectValue) {
            current!!.value.append(text)
            if (current!!.valueWritten) {
                original.append(text)
            }
        } else {
            throw IllegalStateException("Cannot append text as there is no current node.")
        }
        return this
    }

    /**
     * Appends a type name to the current node.
     */
    fun appendType(type: Type): TreeFormatter {
        // Implementation is currently dumb, can be made smarter
        if (type is Class<*>) {
            val classType = GeneratedSubclasses.unpack(type)
            appendOuter(classType)
            append(classType.getSimpleName())
        } else if (type is ParameterizedType) {
            val parameterizedType = type
            appendType(parameterizedType.getRawType())
            append("<")
            val typeArguments = parameterizedType.getActualTypeArguments()
            for (i in typeArguments.indices) {
                val typeArgument = typeArguments[i]
                if (i > 0) {
                    append(", ")
                }
                appendType(typeArgument)
            }
            append(">")
        } else {
            append(type.toString())
        }
        return this
    }

    private fun appendOuter(type: Class<*>) {
        val outer = type.getEnclosingClass()
        if (outer != null) {
            appendOuter(outer)
            append(outer.getSimpleName())
            append(".")
        }
    }

    /**
     * Appends an annotation name to the current node.
     */
    fun appendAnnotation(type: Class<out Annotation?>): TreeFormatter {
        append("@" + type.getSimpleName())
        return this
    }

    /**
     * Appends a method name to the current node.
     */
    fun appendMethod(method: Method): TreeFormatter {
        append(method.getDeclaringClass().getSimpleName())
        append(".")
        append(method.getName())
        append("(")
        val params = method.getParameterTypes()
        val numParams = params.size
        for (i in 0..<numParams) {
            val param: Class<*> = params[i]
            appendType(param)
            if (i < numParams - 1) {
                append(", ")
            }
        }
        append(")")
        if (method.getReturnType() != Void.TYPE) {
            append(": ")
            appendType(method.getGenericReturnType())
        }

        return this
    }

    /**
     * Appends some user provided value to the current node.
     */
    fun appendValue(value: Any?): TreeFormatter {
        // Implementation is currently dumb, can be made smarter
        if (value == null) {
            append("null")
        } else if (value.javaClass.isArray()) {
            val componentType = value.javaClass.getComponentType()
            if (componentType.isPrimitive()) {
                append(value.toString())
            } else {
                appendValues<Any?>(value as Array<Any?>)
            }
        } else if (value is String) {
            append("'")
            append(value.toString())
            append("'")
        } else {
            append(value.toString())
        }
        return this
    }

    /**
     * Appends some user provided values to the current node.
     */
    fun <T> appendValues(values: Array<T?>): TreeFormatter {
        // Implementation is currently dumb, can be made smarter
        append("[")
        for (i in values.indices) {
            val value = values[i]
            if (i > 0) {
                append(", ")
            }
            appendValue(value)
        }
        append("]")
        return this
    }

    /**
     * Appends some user provided values to the current node.
     */
    fun appendTypes(vararg types: Type): TreeFormatter {
        // Implementation is currently dumb, can be made smarter
        append("(")
        for (i in types.indices) {
            val type: Type = types[i]
            checkNotNull(type) { "type cannot be null" }
            if (i > 0) {
                append(", ")
            }
            appendType(type)
        }
        append(")")
        return this
    }

    fun startNumberedChildren(): TreeFormatter {
        startChildren()
        prefixer = NumberedPrefixer()
        return this
    }

    override fun startChildren(): TreeFormatter {
        if (current!!.state == State.CollectValue) {
            current!!.state = State.TraverseChildren
        } else {
            throw IllegalStateException("Cannot start children again")
        }
        return this
    }

    override fun endChildren(): TreeFormatter {
        checkNotNull(current!!.parent) { "Not visiting any node." }
        if (current!!.state == State.CollectValue) {
            current!!.state = State.Done
            current = current!!.parent
        }
        check(current!!.state == State.TraverseChildren) { "Cannot end children." }
        if (current!!.isTopLevelNode) {
            writeNode(current!!)
        }
        current!!.state = State.Done
        current = current!!.parent
        prefixer = DefaultPrefixer()
        return this
    }

    private fun writeNode(node: Node) {
        if (node.prefix == null) {
            node.prefix = if (node.isTopLevelNode) "" else node.parent!!.prefix + "    "
        }

        val output: StyledTextOutput = LinePrefixingStyledTextOutput(original, node.prefix, false)
        if (!node.valueWritten) {
            output.append(node.parent!!.prefix)
            output.append(prefixer.nextPrefix())
            output.append(node.value)
        }

        val separator = node.getFirstChildSeparator(alwaysChildrenOnNewlines)

        if (!separator.newLine) {
            output.append(separator.text)
            val firstChild = node.firstChild
            output.append(firstChild!!.value)
            firstChild.valueWritten = true
            firstChild.prefix = node.prefix
            writeNode(firstChild)
        } else if (node.firstChild != null) {
            original.append(separator.text)
            writeNode(node.firstChild!!)
        }
        if (node.nextSibling != null) {
            original.append(TextUtil.getPlatformLineSeparator())
            writeNode(node.nextSibling!!)
        }
    }

    private enum class State {
        CollectValue, TraverseChildren, Done
    }

    private enum class Separator(val newLine: Boolean, text: String) {
        NewLine(true, TextUtil.getPlatformLineSeparator()),
        Empty(false, " "),
        Colon(false, ": "),
        ColonNewLine(true, ":" + TextUtil.getPlatformLineSeparator());

        val text: String?

        init {
            this.text = text
        }
    }

    private class Node {
        val parent: Node?
        val value: StringBuilder
        var firstChild: Node? = null
        var lastChild: Node? = null
        var nextSibling: Node? = null
        var prefix: String? = null
        var state: State?
        var valueWritten: Boolean = false

        private constructor() {
            this.parent = null
            this.value = StringBuilder()
            prefix = ""
            state = State.TraverseChildren
        }

        private constructor(parent: Node, value: String) {
            this.parent = parent
            this.value = StringBuilder(value)
            state = State.CollectValue
            if (parent.firstChild == null) {
                parent.firstChild = this
                parent.lastChild = this
            } else {
                parent.lastChild!!.nextSibling = this
                parent.lastChild = this
            }
        }

        fun getFirstChildSeparator(alwaysChildrenOnNewlines: Boolean): Separator {
            if (firstChild == null) {
                return Separator.NewLine
            }
            if (value.length == 0) {
                // Always expand empty node
                return Separator.NewLine
            }
            val trailing = value.get(value.length - 1)
            if (trailing == '.') {
                // Always expand with trailing .
                return Separator.NewLine
            }
            if (firstChild!!.nextSibling == null && firstChild!!.firstChild == null && value.length + firstChild!!.value.length < 60 && !alwaysChildrenOnNewlines
            ) {
                // A single leaf node as child and total text is not too long, collapse
                if (trailing == ':') {
                    return Separator.Empty
                }
                return Separator.Colon
            }
            // Otherwise, expand
            if (trailing == ':') {
                return Separator.NewLine
            }
            return Separator.ColonNewLine
        }

        val isTopLevelNode: Boolean
            get() = parent!!.parent == null
    }

    private interface Prefixer {
        fun nextPrefix(): String?
    }

    private class DefaultPrefixer : Prefixer {
        override fun nextPrefix(): String {
            return "  - "
        }
    }

    private class NumberedPrefixer : Prefixer {
        private var cur = 0

        override fun nextPrefix(): String {
            return "  " + ++cur + ". "
        }
    }
}
