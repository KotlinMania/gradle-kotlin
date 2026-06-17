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

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.internal.xml.XmlTransformer
import org.gradle.plugins.ide.api.XmlFileContentMerger
import org.gradle.plugins.ide.internal.IdeDeprecations
import org.gradle.util.internal.ConfigureUtil
import javax.inject.Inject

/**
 * Enables fine-tuning wtp/wst details of the Eclipse plugin
 *
 *
 * For projects applying the eclipse plugin and either one of the ear or war plugins, this plugin is auto-applied.
 *
 *
 * More interesting examples you will find in docs for [EclipseWtpComponent] and [EclipseWtpFacet]
 *
 * <pre class='autoTestedWithDeprecations'>
 * plugins {
 * id 'war' // or 'ear' or 'java'
 * id 'eclipse-wtp'
 * }
 *
 * eclipse {
 *
 * //if you want parts of paths in resulting file(s) to be replaced by variables (files):
 * pathVariables 'GRADLE_HOME': file('/best/software/gradle'), 'TOMCAT_HOME': file('../tomcat')
 *
 * wtp {
 * component {
 * //for examples see docs for [EclipseWtpComponent]
 * }
 *
 * facet {
 * //for examples see docs for [EclipseWtpFacet]
 * }
 * }
 * }
 *
</pre> *
 *
 */
@Deprecated("Will be removed in Gradle 10.")
abstract class EclipseWtp @Inject constructor() {
    /**
     * Configures wtp component.
     *
     *
     * For examples see docs for [EclipseWtpComponent]
     */
    var component: EclipseWtpComponent? = null
    var facet: EclipseWtpFacet? = null
        /**
         * Configures wtp facet.
         *
         *
         * For examples see docs for [EclipseWtpFacet]
         */
        get() {
            if (field == null) {
                val xmlTransformer = XmlTransformer()
                xmlTransformer.setIndentation("\t")
                field = this.objectFactory.newInstance<EclipseWtpFacet?>(
                    EclipseWtpFacet::class.java,
                    XmlFileContentMerger(xmlTransformer)
                )
            }
            return field
        }

    init {
        IdeDeprecations.nagDeprecatedType(EclipseWtp::class.java)
    }

    @get:Inject
    protected abstract val objectFactory: ObjectFactory?

    /**
     * Configures wtp component.
     *
     *
     * For examples see docs for [EclipseWtpComponent]
     */
    fun component(@DelegatesTo(EclipseWtpComponent::class) action: Closure<*>?) {
        ConfigureUtil.configure<EclipseWtpComponent?>(action, component)
    }

    /**
     * Configures wtp component.
     *
     *
     * For examples see docs for [EclipseWtpComponent]
     *
     * @since 3.5
     */
    fun component(action: Action<in EclipseWtpComponent?>) {
        action.execute(component)
    }

    /**
     * Configures wtp facet.
     *
     *
     * For examples see docs for [EclipseWtpFacet]
     */
    fun facet(@DelegatesTo(EclipseWtpFacet::class) action: Closure<*>?) {
        ConfigureUtil.configure<EclipseWtpFacet?>(action, this.facet)
    }

    /**
     * Configures wtp facet.
     *
     *
     * For examples see docs for [EclipseWtpFacet]
     *
     * @since 3.5
     */
    fun facet(action: Action<in EclipseWtpFacet?>) {
        action.execute(this.facet)
    }
}
