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
import com.google.common.collect.Lists
import com.google.common.collect.Sets
import groovy.util.Node
import groovy.util.NodeList
import org.gradle.internal.xml.XmlTransformer
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject


/**
 * Creates the .settings/org.eclipse.wst.common.project.facet.core.xml file for WTP projects.
 */
class WtpFacet(xmlTransformer: XmlTransformer?) : XmlPersistableConfigurationObject(xmlTransformer) {
    var facets: MutableList<Facet> = ArrayList<Facet>() // TODO: turn into Set?

    override fun load(xml: Node) {
        val fixed = xml.get("fixed") as NodeList
        val installed = xml.get("installed") as NodeList
        for (n in fixed) {
            facets.add(Facet(n as Node?))
        }
        for (n in installed) {
            facets.add(Facet(n as Node?))
        }
    }

    override fun store(xml: Node?) {
        removeConfigurableDataFromXml()
        for (facet in facets) {
            facet.appendNode(xml)
        }
    }

    override fun getDefaultResourceName(): String {
        return "defaultWtpFacet.xml"
    }

    fun configure(facets: MutableList<Facet?>) {
        this.facets.addAll(facets)
        removeDuplicates()
    }

    private fun removeDuplicates() {
        this.facets = Lists.newArrayList<Facet?>(Sets.newLinkedHashSet<Facet?>(facets))
    }

    private fun removeConfigurableDataFromXml() {
        val xml = getXml()
        val fixed = xml.get("fixed") as NodeList
        val installed = xml.get("installed") as NodeList
        for (n in fixed) {
            xml.remove(n as Node?)
        }
        for (n in installed) {
            xml.remove(n as Node?)
        }
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val wtpFacet = o as WtpFacet
        return Objects.equal(facets, wtpFacet.facets)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(facets)
    }

    override fun toString(): String {
        return "WtpFacet{facets=" + facets + "}"
    }
}
