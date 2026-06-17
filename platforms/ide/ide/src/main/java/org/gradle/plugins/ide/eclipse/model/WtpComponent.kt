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
package org.gradle.plugins.ide.eclipse.model

import com.google.common.base.Objects
import com.google.common.base.Preconditions
import com.google.common.base.Predicates
import com.google.common.base.Strings
import com.google.common.collect.Iterables
import com.google.common.collect.Lists
import com.google.common.collect.Sets
import groovy.util.Node
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.xml.XmlTransformer
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject

/**
 * Creates the .settings/org.eclipse.wst.common.component file for WTP projects.
 */
class WtpComponent(xmlTransformer: XmlTransformer?) : XmlPersistableConfigurationObject(xmlTransformer) {
    var deployName: String? = null
    var contextPath: String? = null

    // TODO Change to Set?
    var wbModuleEntries: MutableList<WbModuleEntry> = ArrayList<WbModuleEntry>()

    override fun getDefaultResourceName(): String {
        return "defaultWtpComponent.xml"
    }

    fun configure(deployName: String?, contextPath: String?, newEntries: MutableList<WbModuleEntry?>) {
        val toKeep = Iterables.filter<WbModuleEntry?>(wbModuleEntries, Predicates.not<WbModuleEntry?>(Predicates.instanceOf<WbModuleEntry?>(WbDependentModule::class.java)))
        this.wbModuleEntries = Lists.newArrayList<WbModuleEntry?>(Sets.newLinkedHashSet<WbModuleEntry?>(Iterables.concat<WbModuleEntry?>(toKeep, newEntries)))
        if (!Strings.isNullOrEmpty(deployName)) {
            this.deployName = deployName
        }
        if (!Strings.isNullOrEmpty(contextPath)) {
            this.contextPath = contextPath
        }
    }

    override fun load(xml: Node?) {
        val wbModuleNode: Node = getWbModuleNode(xml)
        deployName = wbModuleNode.attribute("deploy-name") as String?

        for (node in uncheckedCast<MutableList<Node>?>(wbModuleNode.children())!!) {
            if ("property" == node.name()) {
                if ("context-root" == node.attribute("name")) {
                    contextPath = node.attribute("value") as String?
                } else {
                    wbModuleEntries.add(WbProperty(node))
                }
            } else if ("wb-resource" == node.name()) {
                wbModuleEntries.add(WbResource(node))
            } else if ("dependent-module" == node.name()) {
                wbModuleEntries.add(WbDependentModule(node))
            }
        }
    }

    override fun store(xml: Node?) {
        removeConfigurableDataFromXml()
        val wbModuleNode: Node = getWbModuleNode(xml)
        setNodeAttribute(wbModuleNode, "deploy-name", deployName)
        if (!Strings.isNullOrEmpty(contextPath)) {
            WbProperty("context-root", contextPath).appendNode(wbModuleNode)
        }
        for (wbModuleEntry in wbModuleEntries) {
            wbModuleEntry.appendNode(wbModuleNode)
        }
    }

    private fun removeConfigurableDataFromXml() {
        val wbModuleNode: Node = getWbModuleNode(getXml())
        for (elementName in mutableListOf<String?>("property", "wb-resource", "dependent-module")) {
            for (elementNode in XmlPersistableConfigurationObject.Companion.getChildren(wbModuleNode, elementName)) {
                wbModuleNode.remove(elementNode)
            }
        }
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (javaClass != o.javaClass) {
            return false
        }
        val wtp = o as WtpComponent
        return Objects.equal(deployName, wtp.deployName)
                && Objects.equal(contextPath, wtp.contextPath)
                && Objects.equal(wbModuleEntries, wtp.wbModuleEntries)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(wbModuleEntries, deployName, contextPath)
    }

    override fun toString(): String {
        return ("WtpComponent{"
                + "wbModuleEntries=" + wbModuleEntries
                + ", deployName='" + deployName + "\'"
                + ", contextPath='" + contextPath + "\'"
                + "}")
    }

    companion object {
        private fun getWbModuleNode(xml: Node?): Node {
            val wbModule: Node = XmlPersistableConfigurationObject.Companion.findFirstChildNamed(xml, "wb-module")
            Preconditions.checkNotNull<Node?>(wbModule)
            return wbModule
        }

        private fun setNodeAttribute(node: Node, key: String?, value: String?) {
            val attributes = uncheckedCast<MutableMap<String?, String?>?>(node.attributes())
            attributes!!.put(key, value)
        }
    }
}
