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
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableMap
import groovy.util.Node
import groovy.util.NodeList
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.plugins.ide.eclipse.model.internal.PathUtil
import java.util.Map
import java.util.function.BinaryOperator
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collectors

// TODO: consider entryAttributes in equals, hashCode, and toString
/**
 * Common superclass for all [ClasspathEntry] instances.
 */
abstract class AbstractClasspathEntry : ClasspathEntry {
    var path: String
    var isExported: Boolean
    var accessRules: MutableSet<AccessRule>?
    val entryAttributes: MutableMap<String?, Any?>

    constructor(node: Node) {
        path = normalizePath(node.attribute("path") as String?)
        this.isExported = isNodeExported(node)
        accessRules = readAccessRules(node)
        entryAttributes = readEntryAttributes(node)
        Preconditions.checkNotNull<String?>(path)
        Preconditions.checkNotNull<MutableSet<AccessRule?>?>(accessRules)
    }

    private fun isNodeExported(node: Node): Boolean {
        val value = node.attribute("exported")
        if (value == null) {
            return false
        } else if (value is Boolean) {
            return value
        } else {
            return (value as String).toBoolean()
        }
    }

    constructor(path: String?) {
        Preconditions.checkNotNull<String?>(path)
        this.path = normalizePath(path)
        this.isExported = false
        this.accessRules = LinkedHashSet<AccessRule>()
        this.entryAttributes = java.util.LinkedHashMap<String?, Any?>()
    }

    var nativeLibraryLocation: String?
        get() = entryAttributes.get(NATIVE_LIBRARY_ATTRIBUTE) as String?
        set(location) {
            entryAttributes.put(NATIVE_LIBRARY_ATTRIBUTE, location)
        }

    override fun appendNode(node: Node) {
        addClasspathEntry(node, ImmutableMap.of<String?, Any?>())
    }

    protected fun addClasspathEntry(node: Node, attributes: MutableMap<String?, *>): Node {
        val allAttributes = ImmutableMap.builder<String?, Any?>()
        attributes.forEach { (key: String?, value: Any?) ->
            if (value != null && !value.toString().isEmpty()) {
                allAttributes.put(key, value)
            }
        }
        allAttributes.put("kind", getKind())
        allAttributes.put("path", path)


        if (this.isExported && this !is SourceFolder) {
            allAttributes.put("exported", true)
        }

        val entryNode = node.appendNode("classpathentry", allAttributes.build())
        writeAccessRules(entryNode)
        writeEntryAttributes(entryNode)
        return entryNode
    }

    protected fun normalizePath(path: String?): String {
        return PathUtil.normalizePath(path)
    }

    private fun readAccessRules(node: Node): MutableSet<AccessRule> {
        val accessRules: MutableSet<AccessRule> = LinkedHashSet<AccessRule>()
        val accessRulesNodes = node.get("accessrules") as NodeList
        for (accessRulesNode in accessRulesNodes) {
            val accessRuleNodes = (accessRulesNode as Node).get("accessrule") as NodeList
            for (accessRuleNode in accessRuleNodes) {
                val ruleNode = accessRuleNode as Node
                accessRules.add(AccessRule(ruleNode.attribute("kind") as String?, ruleNode.attribute("pattern") as String?))
            }
        }
        return accessRules
    }

    private fun writeAccessRules(node: Node) {
        if (accessRules == null || accessRules!!.isEmpty()) {
            return
        }
        val accessRulesNode: Node = getAttributesNode(node, "accessrules")
        for (rule in accessRules) {
            accessRulesNode.appendNode(
                "accessrule",
                ImmutableMap.of<String?, String?>(
                    "kind", rule.getKind(),
                    "pattern", rule.getPattern()
                )
            )
        }
    }

    private fun readEntryAttributes(node: Node): MutableMap<String?, Any?> {
        val attributes: MutableMap<String?, Any?> = java.util.LinkedHashMap<String?, Any?>()
        val attributesNodes = node.get("attributes") as NodeList
        for (attributesEntry in attributesNodes) {
            val attributeNodes = (attributesEntry as Node).get("attribute") as NodeList
            for (attributeEntry in attributeNodes) {
                val attributeNode = attributeEntry as Node
                attributes.put(attributeNode.attribute("name") as String?, attributeNode.attribute("value"))
            }
        }
        return attributes
    }

    fun writeEntryAttributes(node: Node) {
        val effectiveEntryAttrs = this.effectiveEntryAttrs

        if (effectiveEntryAttrs.isEmpty()) {
            return
        }

        val attributesNode: Node = getAttributesNode(node, "attributes")

        effectiveEntryAttrs.forEach { (key: String?, value: Any?) ->
            // If the attribute value is an Iterable, an <attribute> node is produced for each element in the Iterable.
            // This is something that is supported by the classpath entry format and it allows users to define multi-value
            // entries by putting a list as value into the 'entryAttributes' Map.
            // For exmaple: entryAttributes['add-exports'] = ['java.base/jdk.internal.access=ALL-UNNAMED', 'java.base/jdk.internal.loader=ALL-UNNAMED']
            if (value is Iterable<*>) {
                uncheckedCast<Iterable<*>?>(value)!!.forEach { valueElement: Any? ->
                    attributesNode.appendNode(
                        "attribute", ImmutableMap.of<String?, Any?>(
                            "name", key,
                            "value", valueElement
                        )
                    )
                }
            } else {
                attributesNode.appendNode(
                    "attribute", ImmutableMap.of<String?, Any?>(
                        "name", key,
                        "value", value
                    )
                )
            }
        }
    }

    private val effectiveEntryAttrs: MutableMap<String?, Any?>
        get() = entryAttributes.entries.stream()
            .filter { entry: MutableMap.MutableEntry<String?, Any?>? -> entry!!.value != null }
            .collect(
                Collectors.toMap(
                    Function { Map.Entry.key },
                    Function { Map.Entry.value },
                    BinaryOperator { existing: Any?, replacement: Any? -> existing },
                    Supplier { LinkedHashMap() })
            )

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as AbstractClasspathEntry
        return this.isExported == that.isExported && Objects.equal(path, that.path)
                && Objects.equal(accessRules, that.accessRules)
                && Objects.equal(this.nativeLibraryLocation, that.nativeLibraryLocation)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(path, this.isExported, accessRules, this.nativeLibraryLocation)
    }

    override fun toString(): String {
        return "{path='" + path + "', nativeLibraryLocation='" + this.nativeLibraryLocation + "', exported=" + this.isExported + ", accessRules=" + accessRules + "}"
    }

    companion object {
        private const val NATIVE_LIBRARY_ATTRIBUTE = "org.eclipse.jdt.launching.CLASSPATH_ATTR_LIBRARY_PATH_ENTRY"
        const val COMPONENT_NON_DEPENDENCY_ATTRIBUTE: String = "org.eclipse.jst.component.nondependency"
        const val COMPONENT_DEPENDENCY_ATTRIBUTE: String = "org.eclipse.jst.component.dependency"

        private fun getAttributesNode(node: Node, attributes: String?): Node {
            val attributesNodes = node.get(attributes) as NodeList
            if (attributesNodes.isEmpty()) {
                return node.appendNode(attributes)
            }
            return attributesNodes.get(0) as Node
        }
    }
}
