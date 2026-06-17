/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.internal.generator

import com.google.common.base.Predicate
import com.google.common.collect.Iterables
import groovy.lang.Closure
import groovy.lang.DelegatesTo
import groovy.util.Node
import groovy.xml.XmlParser
import org.gradle.api.Action
import org.gradle.api.XmlProvider
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.xml.XmlTransformer
import org.gradle.util.internal.ConfigureUtil
import java.io.InputStream
import java.io.OutputStream

/**
 * A [org.gradle.plugins.ide.internal.generator.generator.PersistableConfigurationObject]
 * which is stored in an XML file.
 */
abstract class XmlPersistableConfigurationObject protected constructor(private val xmlTransformer: XmlTransformer) : AbstractPersistableConfigurationObject() {
    open var xml: Node? = null
        private set

    @Throws(Exception::class)
    override fun load(inputStream: InputStream?) {
        xml = XmlParser().parse(inputStream)
        load(xml)
    }

    override fun store(outputStream: OutputStream?) {
        store(xml)
        xmlTransformer.transform(xml, outputStream)
    }

    /**
     * Called immediately after the XML file has been read.
     */
    protected open fun load(xml: Node?) {
        // no-op
    }

    /**
     * Called immediately before the XML file is to be written.
     */
    protected open fun store(xml: Node?) {
        // no-op
    }

    open fun transformAction(@DelegatesTo(XmlProvider::class) action: Closure<*>?) {
        transformAction(ConfigureUtil.configureUsing<XmlProvider?>(action))
    }

    /**
     * @param action transform action
     * @since 3.5
     */
    open fun transformAction(action: Action<in XmlProvider?>?) {
        xmlTransformer.addAction(action)
    }

    companion object {
        protected fun getChildren(root: Node?, name: String?): MutableList<Node?>? {
            return if (root == null) mutableListOf<Node?>() else uncheckedCast<MutableList<Node?>?>(root.get(name))
        }

        fun findFirstChildNamed(root: Node?, name: String?): Node? {
            return if (root == null) null else Iterables.getFirst<Node?>(getChildren(root, name)!!, null)
        }

        fun findFirstChildWithAttributeValue(root: Node?, childName: String?, attribute: String?, value: String): Node? {
            return if (root == null) null else findFirstWithAttributeValue(getChildren(root, childName), attribute, value)
        }

        protected fun findFirstWithAttributeValue(nodes: MutableList<Node?>?, attribute: String?, value: String): Node? {
            return if (nodes == null) null else Iterables.getFirst<Node?>(Iterables.filter<Node?>(nodes, object : Predicate<Node?> {
                override fun apply(node: Node): Boolean {
                    return value == node.attribute(attribute)
                }
            }), null)
        }

        fun findOrCreateFirstChildNamed(root: Node, name: String?): Node? {
            var child: Node? = findFirstChildNamed(root, name)
            if (child == null) {
                child = root.appendNode(name)
            }
            return child
        }

        fun findOrCreateFirstChildWithAttributeValue(root: Node?, childName: String?, attribute: String?, value: String): Node? {
            var child: Node? = findFirstChildWithAttributeValue(root, childName, attribute, value)
            if (child == null) {
                val attributes: MutableMap<String?, Any?> = LinkedHashMap<String?, Any?>()
                attributes.put(attribute, value)
                child = root!!.appendNode(childName, attributes)
            }
            return child
        }
    }
}
