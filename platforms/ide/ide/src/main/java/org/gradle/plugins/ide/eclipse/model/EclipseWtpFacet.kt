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
package org.gradle.plugins.ide.eclipse.model

import com.google.common.collect.Iterables
import com.google.common.collect.Lists
import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.plugins.ide.api.XmlFileContentMerger
import org.gradle.plugins.ide.internal.IdeDeprecations
import org.gradle.util.internal.ConfigureUtil
import java.util.Arrays
import java.util.stream.Collectors
import javax.inject.Inject

/**
 * Enables fine-tuning wtp facet details of the Eclipse plugin
 *
 *
 * Advanced configuration closures beforeMerged and whenMerged receive [WtpFacet] object as parameter.
 *
 * <pre class='autoTestedWithDeprecations'>
 * plugins {
 * id 'war' // or 'ear' or 'java'
 * id 'eclipse-wtp'
 * }
 *
 * eclipse {
 * wtp {
 * facet {
 * //you can add some extra wtp facets or update existing ones; mandatory keys: 'name', 'version':
 * facet name: 'someCoolFacet', version: '1.3'
 *
 * file {
 * //if you want to mess with the resulting XML in whatever way you fancy
 * withXml {
 * def node = it.asNode()
 * node.appendNode('xml', 'is what I love')
 * }
 *
 * //beforeMerged and whenMerged closures are the highest voodoo for the tricky edge cases.
 * //the type passed to the closures is [WtpFacet]
 *
 * //closure executed after wtp facet file content is loaded from existing file
 * //but before gradle build information is merged
 * beforeMerged { wtpFacet -&gt;
 * //tinker with [WtpFacet] here
 * }
 *
 * //closure executed after wtp facet file content is loaded from existing file
 * //and after gradle build information is merged
 * whenMerged { wtpFacet -&gt;
 * //you can tinker with the [WtpFacet] here
 * }
 * }
 * }
 * }
 * }
 *
</pre> *
 *
 */
@Deprecated("Will be removed in Gradle 10.")
abstract class EclipseWtpFacet @Inject constructor(file: XmlFileContentMerger) {
    /**
     * See [.file]
     */
    val file: XmlFileContentMerger

    /**
     * The facets to be added as elements.
     *
     *
     * For examples see docs for [EclipseWtpFacet]
     */
    var facets: MutableList<Facet?>? = ArrayList<Facet?>()

    init {
        IdeDeprecations.nagDeprecatedType(EclipseWtpFacet::class.java)
        this.file = file
    }

    /**
     * Enables advanced configuration like tinkering with the output XML
     * or affecting the way existing wtp facet file content is merged with gradle build information
     *
     *
     * The object passed to whenMerged{} and beforeMerged{} closures is of type [WtpFacet]
     *
     *
     *
     * For example see docs for [EclipseWtpFacet]
     */
    fun file(@DelegatesTo(XmlFileContentMerger::class) closure: Closure<*>?) {
        ConfigureUtil.configure<XmlFileContentMerger?>(closure, file)
    }

    /**
     * Enables advanced configuration like tinkering with the output XML
     * or affecting the way existing wtp facet file content is merged with gradle build information.
     *
     *
     *
     * For example see docs for [EclipseWtpFacet]
     *
     * @since 3.5
     */
    fun file(action: Action<in XmlFileContentMerger?>) {
        action.execute(file)
    }

    /**
     * Adds a facet.
     *
     *
     * If a facet already exists with the given name then its version will be updated.
     *
     *
     * In the case of a "jst.ejb" facet, it will also be added as a fixed facet.
     *
     *
     * For examples see docs for [EclipseWtpFacet]
     *
     * @param args A map that must contain a 'name' and 'version' key with corresponding values.
     */
    fun facet(args: MutableMap<String?, *>) {
        val newFacet = ConfigureUtil.configureByMap<Facet>(args, Facet())
        val newFacets: MutableList<Facet?>?
        if ("jst.ejb" == newFacet.getName()) {
            newFacets = Arrays.asList<Facet?>(Facet(Facet.FacetType.fixed, "jst.ejb", null), newFacet)
        } else {
            newFacets = mutableListOf<Facet?>(newFacet)
        }
        facets = Lists.newArrayList<Facet?>(
            Iterables.concat<Facet?>(
                this.facets!!.stream()
                    .filter { f: Facet? -> f!!.getType() != newFacet.getType() || f.getName() != newFacet.getName() }
                    .collect(Collectors.toList()),
                newFacets))
    }

    /**
     * Removes incompatible facets from a list of facets.
     *
     *
     * Currently removes the facet "jst.utility" when the facet "jst.ejb" is present.
     *
     * @param facets, a list of facets
     * @return the modified facet list
     */
    fun replaceInconsistentFacets(facets: MutableList<Facet?>): MutableList<Facet?> {
        if (facets.stream().anyMatch { f: Facet? -> "jst.ejb" == f!!.getName() }) {
            return facets.stream().filter { f: Facet? -> "jst.utility" != f!!.getName() }.collect(Collectors.toList())
        }
        return facets
    }

    fun mergeXmlFacet(xmlFacet: WtpFacet) {
        file.getBeforeMerged().execute(xmlFacet)
        xmlFacet.configure(replaceInconsistentFacets(this.facets!!))
        file.getWhenMerged().execute(xmlFacet)
    }
}
