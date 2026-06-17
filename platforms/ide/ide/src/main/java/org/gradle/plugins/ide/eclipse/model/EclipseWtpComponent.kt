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
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.plugins.ide.api.XmlFileContentMerger
import org.gradle.plugins.ide.eclipse.model.internal.FileReferenceFactory
import org.gradle.plugins.ide.eclipse.model.internal.WtpComponentFactory
import org.gradle.plugins.ide.internal.IdeArtifactRegistry
import org.gradle.plugins.ide.internal.IdeDeprecations
import org.gradle.util.internal.ConfigureUtil
import java.io.File
import javax.inject.Inject

/**
 * Enables fine-tuning wtp component details of the Eclipse plugin
 *
 *
 * Example of use with a blend of all possible properties.
 * Bear in mind that usually you don't have to configure them directly because Gradle configures it for free!
 *
 * <pre class='autoTestedWithDeprecations'>
 * plugins {
 * id 'war' // or 'ear' or 'java'
 * id 'eclipse-wtp'
 * }
 *
 * configurations {
 * someInterestingConfiguration
 * anotherConfiguration
 * }
 *
 * eclipse {
 *
 * //if you want parts of paths in resulting file(s) to be replaced by variables (files):
 * pathVariables 'GRADLE_HOME': file('/best/software/gradle'), 'TOMCAT_HOME': file('../tomcat')
 *
 * wtp {
 * component {
 * //you can configure the context path:
 * contextPath = 'someContextPath'
 *
 * //you can configure the deployName:
 * deployName = 'killerApp'
 *
 * //you can alter the wb-resource elements.
 * //non-existing source dirs won't be added to the component file.
 * sourceDirs += file('someExtraFolder')
 *
 * // dependencies to mark as deployable with lib folder deploy path
 * libConfigurations += [ configurations.someInterestingConfiguration ]
 *
 * // dependencies to mark as deployable with root folder deploy path
 * rootConfigurations += [ configurations.someInterestingConfiguration ]
 *
 * // dependencies to exclude from wtp deployment
 * minusConfigurations &lt;&lt; configurations.anotherConfiguration
 *
 * //you can add a wb-resource elements; mandatory keys: 'sourcePath', 'deployPath':
 * //if sourcePath points to non-existing folder it will *not* be added.
 * resource sourcePath: 'extra/resource', deployPath: 'deployment/resource'
 *
 * //you can add a wb-property elements; mandatory keys: 'name', 'value':
 * property name: 'moodOfTheDay', value: ':-D'
 * }
 * }
 * }
</pre> *
 *
 * For tackling edge cases users can perform advanced configuration on resulting XML file.
 * It is also possible to affect the way eclipse plugin merges the existing configuration
 * via beforeMerged and whenMerged closures.
 *
 *
 * beforeMerged and whenMerged closures receive [WtpComponent] object
 *
 *
 * Examples of advanced configuration:
 *
 * <pre class='autoTestedWithDeprecations'>
 * plugins {
 * id 'war'
 * id 'eclipse-wtp'
 * }
 *
 * eclipse {
 *
 * wtp {
 * component {
 * file {
 * //if you want to mess with the resulting XML in whatever way you fancy
 * withXml {
 * def node = it.asNode()
 * node.appendNode('xml', 'is what I love')
 * }
 *
 * //closure executed after wtp component file content is loaded from existing file
 * //but before gradle build information is merged
 * beforeMerged { wtpComponent -&gt;
 * //tinker with [WtpComponent] here
 * }
 *
 * //closure executed after wtp component file content is loaded from existing file
 * //and after gradle build information is merged
 * whenMerged { wtpComponent -&gt;
 * //you can tinker with the [WtpComponent] here
 * }
 * }
 * }
 * }
 * }
</pre> *
 *
 */
@Deprecated("Will be removed in Gradle 10.")
abstract class EclipseWtpComponent @Inject constructor(project: Project?, file: XmlFileContentMerger) {
    val project: Project?

    /**
     * See [.file]
     */
    val file: XmlFileContentMerger

    /**
     * Source directories to be transformed into wb-resource elements.
     *
     *
     * For examples see docs for [EclipseWtp]
     *
     *
     * Only source dirs that exist will be added to the wtp component file.
     * Non-existing resource directory declarations lead to errors when project is imported into Eclipse.
     */
    var sourceDirs: MutableSet<File?>? = null

    /**
     * The configurations whose files are to be marked to be deployed with a deploy path of '/'.
     *
     *
     * For examples see docs for [EclipseWtp]
     */
    var rootConfigurations: MutableSet<Configuration?>? = LinkedHashSet<Configuration?>()

    /**
     * The configurations whose files are to be marked to be deployed with a deploy path of [.getLibDeployPath].
     *
     *
     * For examples see docs for [EclipseWtp]
     */
    var libConfigurations: MutableSet<Configuration?>? = LinkedHashSet<Configuration?>()

    /**
     * The configurations whose files are to be excluded from wtp deployment.
     *
     *
     * For examples see docs for [EclipseWtp]
     */
    var minusConfigurations: MutableSet<Configuration?>? = LinkedHashSet<Configuration?>()

    /**
     * The deploy name to be used.
     *
     *
     * For examples see docs for [EclipseWtp]
     */
    var deployName: String? = null

    /**
     * Additional wb-resource elements.
     *
     *
     * For examples see docs for [EclipseWtp]
     *
     *
     * Only resources that link to an existing directory ([WbResource.getSourcePath])
     * will be added to the wtp component file.
     * The reason is that non-existing resource directory declarations
     * lead to errors when project is imported into Eclipse.
     */
    var resources: MutableList<WbResource?>? = ArrayList<WbResource?>()

    /**
     * Additional property elements.
     *
     *
     * For examples see docs for [EclipseWtp]
     */
    var properties: MutableList<WbProperty?>? = ArrayList<WbProperty?>()

    /**
     * The context path for the web application
     *
     *
     * For examples see docs for [EclipseWtp]
     */
    var contextPath: String? = null

    /**
     * The deploy path for classes.
     *
     *
     * For examples see docs for [EclipseWtp]
     */
    var classesDeployPath: String? = "/WEB-INF/classes"

    /**
     * The deploy path for libraries.
     *
     *
     * For examples see docs for [EclipseWtp]
     */
    var libDeployPath: String? = null

    /**
     * The variables to be used for replacing absolute path in dependent-module elements.
     *
     *
     * For examples see docs for [EclipseModel]
     */
    var pathVariables: MutableMap<String?, File?> = HashMap<String?, File?>()

    init {
        IdeDeprecations.nagDeprecatedType(EclipseWtpComponent::class.java)
        this.project = project
        this.file = file
    }

    /**
     * Enables advanced configuration like tinkering with the output XML
     * or affecting the way existing wtp component file content is merged with gradle build information
     *
     *
     * The object passed to whenMerged{} and beforeMerged{} closures is of type [WtpComponent]
     *
     *
     * For example see docs for [EclipseWtpComponent]
     */
    fun file(@DelegatesTo(XmlFileContentMerger::class) closure: Closure<*>?) {
        ConfigureUtil.configure<XmlFileContentMerger?>(closure, file)
    }

    /**
     * Enables advanced configuration like tinkering with the output XML
     * or affecting the way existing wtp component file content is merged with gradle build information.
     *
     *
     * For example see docs for [EclipseWtpComponent]
     *
     * @since 3.5
     */
    fun file(action: Action<in XmlFileContentMerger?>) {
        action.execute(file)
    }

    var plusConfigurations: MutableSet<Configuration?>?
        /**
         * Synonym for [.getLibConfigurations].
         */
        get() = this.libConfigurations
        /**
         * Synonym for [.setLibConfigurations].
         */
        set(plusConfigurations) {
            this.libConfigurations = plusConfigurations
        }

    /**
     * Adds a wb-resource.
     *
     *
     * For examples see docs for [EclipseWtp]
     *
     * @param args A map that must contain a deployPath and sourcePath key with corresponding values.
     */
    fun resource(args: MutableMap<String?, String?>) {
        resources = Lists.newArrayList<WbResource?>(
            Iterables.concat<WbResource?>(
                this.resources!!, mutableSetOf<WbResource?>(WbResource(args.get("deployPath"), args.get("sourcePath")))
            )
        )
    }

    /**
     * Adds a property.
     *
     *
     * For examples see docs for [EclipseWtp]
     *
     * @param args A map that must contain a 'name' and 'value' key with corresponding values.
     */
    fun property(args: MutableMap<String?, String?>) {
        properties = Lists.newArrayList<WbProperty?>(
            Iterables.concat<WbProperty?>(
                this.properties!!, mutableSetOf<WbProperty?>(WbProperty(args.get("name"), args.get("value")))
            )
        )
    }

    val fileReferenceFactory: FileReferenceFactory
        get() {
            val referenceFactory = FileReferenceFactory()
            for (pathVariable in pathVariables.entries) {
                referenceFactory.addPathVariable(pathVariable.key, pathVariable.value)
            }
            return referenceFactory
        }

    fun mergeXmlComponent(xmlComponent: WtpComponent) {
        file.getBeforeMerged().execute(xmlComponent)
        val projectInternal = this.project as ProjectInternal
        val ideArtifactRegistry = projectInternal.getServices().get<IdeArtifactRegistry?>(IdeArtifactRegistry::class.java)
        val projectRegistry = projectInternal.getServices().get<ProjectStateRegistry?>(ProjectStateRegistry::class.java)
        WtpComponentFactory(projectInternal, ideArtifactRegistry, projectRegistry).configure(this, xmlComponent)
        file.getWhenMerged().execute(xmlComponent)
    }
}
