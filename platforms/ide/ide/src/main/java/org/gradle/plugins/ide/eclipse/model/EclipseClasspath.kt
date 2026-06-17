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

import com.google.common.base.Preconditions
import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.SourceSet
import org.gradle.internal.xml.XmlTransformer
import org.gradle.plugins.ide.api.XmlFileContentMerger
import org.gradle.plugins.ide.eclipse.model.internal.ClasspathFactory
import org.gradle.plugins.ide.eclipse.model.internal.EclipseClassPathUtil
import org.gradle.plugins.ide.eclipse.model.internal.FileReferenceFactory
import org.gradle.plugins.ide.internal.IdeArtifactRegistry
import org.gradle.plugins.ide.internal.resolver.DefaultGradleApiSourcesResolver
import org.gradle.util.internal.ConfigureUtil
import java.io.File
import java.util.Arrays
import javax.inject.Inject

/**
 * The build path settings for the generated Eclipse project. Used by the
 * [org.gradle.plugins.ide.eclipse.GenerateEclipseClasspath] task to generate an Eclipse .classpath file.
 *
 *
 * The following example demonstrates the various configuration options.
 * Keep in mind that all properties have sensible defaults; only configure them explicitly
 * if the defaults don't match your needs.
 *
 * <pre class='autoTested'>
 * plugins {
 * id 'java'
 * id 'eclipse'
 * }
 *
 * configurations {
 * provided
 * someBoringConfig
 * }
 *
 * eclipse {
 * //if you want parts of paths in resulting file to be replaced by variables (files):
 * pathVariables 'GRADLE_HOME': file('/best/software/gradle'), 'TOMCAT_HOME': file('../tomcat')
 *
 * classpath {
 * //you can tweak the classpath of the Eclipse project by adding extra configurations:
 * plusConfigurations += [ configurations.provided ]
 *
 * //you can also remove configurations from the classpath:
 * minusConfigurations += [ configurations.someBoringConfig ]
 *
 * //if you want to append extra containers:
 * containers 'someFriendlyContainer', 'andYetAnotherContainer'
 *
 * //customizing the classes output directory:
 * defaultOutputDir = file('build-eclipse')
 *
 * //default settings for downloading sources and Javadoc:
 * downloadSources = true
 * downloadJavadoc = false
 *
 * //if you want to expose test classes to dependent projects
 * containsTestFixtures = true
 *
 * //customizing which Eclipse source directories should be marked as test
 * testSourceSets = [sourceSets.test]
 *
 * //customizing which dependencies should be marked as test on the project's classpath
 * testConfigurations = [configurations.testCompileClasspath, configurations.testRuntimeClasspath]
 * }
 * }
</pre> *
 *
 * For tackling edge cases, users can perform advanced configuration on the resulting XML file.
 * It is also possible to affect the way that the Eclipse plugin merges the existing configuration
 * via beforeMerged and whenMerged closures.
 *
 *
 * The beforeMerged and whenMerged closures receive a [Classpath] object.
 *
 *
 * Examples of advanced configuration:
 *
 * <pre class='autoTested'>
 * plugins {
 * id 'java'
 * id 'eclipse'
 * }
 *
 * eclipse {
 * classpath {
 * file {
 * //if you want to mess with the resulting XML in whatever way you fancy
 * withXml {
 * def node = it.asNode()
 * node.appendNode('xml', 'is what I love')
 * }
 *
 * //closure executed after .classpath content is loaded from existing file
 * //but before gradle build information is merged
 * beforeMerged { classpath -&gt;
 * //you can tinker with the [Classpath] here
 * }
 *
 * //closure executed after .classpath content is loaded from existing file
 * //and after gradle build information is merged
 * whenMerged { classpath -&gt;
 * //you can tinker with the [Classpath] here
 * }
 * }
 * }
 * }
</pre> *
 */
abstract class EclipseClasspath @Inject constructor(val project: Project?) {
    /**
     * The source sets to be added.
     *
     *
     * See [EclipseClasspath] for an example.
     */
    var sourceSets: Iterable<SourceSet?>? = null

    /**
     * The configurations whose files are to be added as classpath entries.
     *
     *
     * See [EclipseClasspath] for an example.
     */
    var plusConfigurations: MutableCollection<Configuration?>? = ArrayList<Configuration?>()

    /**
     * The configurations whose files are to be excluded from the classpath entries.
     *
     *
     * See [EclipseClasspath] for an example.
     */
    var minusConfigurations: MutableCollection<Configuration?>? = ArrayList<Configuration?>()

    /**
     * The classpath containers to be added.
     *
     *
     * See [EclipseClasspath] for an example.
     */
    var containers: MutableSet<String?> = LinkedHashSet<String?>()

    /**
     * The default output directory where Eclipse puts compiled classes.
     *
     *
     * See [EclipseClasspath] for an example.
     */
    var defaultOutputDir: File? = null

    /**
     * Whether to download and associate source Jars with the dependency Jars. Defaults to true.
     *
     *
     * See [EclipseClasspath] for an example.
     */
    var isDownloadSources: Boolean = true

    /**
     * Whether to download and associate Javadoc Jars with the dependency Jars. Defaults to false.
     *
     *
     * See [EclipseClasspath] for an example.
     */
    var isDownloadJavadoc: Boolean = false

    /**
     * See [.file].
     */
    var file: XmlFileContentMerger = XmlFileContentMerger(XmlTransformer())

    var pathVariables: MutableMap<String?, File?> = HashMap<String?, File?>()

    var isProjectDependenciesOnly: Boolean = false

    var classFolders: MutableList<File?>? = null

    init {
        this.containsTestFixtures.convention(false)
    }

    @get:Incubating
    abstract val baseSourceOutputDir: DirectoryProperty?

    /**
     * Further classpath containers to be added.
     *
     *
     * See [EclipseClasspath] for an example.
     *
     * @param containers the classpath containers to be added
     */
    fun containers(vararg containers: String?) {
        Preconditions.checkNotNull<Array<String?>?>(containers)
        this.containers.addAll(Arrays.asList<String?>(*containers))
    }

    /**
     * Enables advanced configuration like tinkering with the output XML or affecting the way
     * that the contents of an existing .classpath file is merged with Gradle build information.
     * The object passed to the whenMerged{} and beforeMerged{} closures is of type [Classpath].
     *
     *
     * See [EclipseProject] for an example.
     */
    fun file(@DelegatesTo(XmlFileContentMerger::class) closure: Closure<*>?) {
        ConfigureUtil.configure<XmlFileContentMerger?>(closure, file)
    }

    /**
     * Enables advanced configuration like tinkering with the output XML or affecting the way
     * that the contents of an existing .classpath file is merged with Gradle build information.
     * The object passed to the whenMerged{} and beforeMerged{} closures is of type [Classpath].
     *
     *
     * See [EclipseProject] for an example.
     *
     * @since 3.5
     */
    fun file(action: Action<in XmlFileContentMerger?>) {
        action.execute(file)
    }

    /**
     * Calculates, resolves and returns dependency entries of this classpath.
     */
    fun resolveDependencies(): MutableList<ClasspathEntry?> {
        val projectInternal = this.project as ProjectInternal
        val ideArtifactRegistry = projectInternal.getServices().get<IdeArtifactRegistry?>(IdeArtifactRegistry::class.java)
        val classpathFactory = ClasspathFactory(this, ideArtifactRegistry, DefaultGradleApiSourcesResolver(projectInternal.newDetachedResolver()), EclipseClassPathUtil.isInferModulePath(this.project))
        return classpathFactory.createEntries()
    }


    fun mergeXmlClasspath(xmlClasspath: Classpath) {
        file.getBeforeMerged().execute(xmlClasspath)
        val entries = resolveDependencies()
        xmlClasspath.configure(entries)
        file.getWhenMerged().execute(xmlClasspath)
    }

    val fileReferenceFactory: FileReferenceFactory
        get() {
            val referenceFactory = FileReferenceFactory()
            pathVariables.forEach { (key: String?, value: File?) -> referenceFactory.addPathVariable(key, value) }
            return referenceFactory
        }

    /**
     * Returns `true` if the classpath contains test fixture classes that should be visible
     * through incoming project dependencies.
     *
     * @since 6.8
     */
    abstract val containsTestFixtures: Property<Boolean?>?

    @get:Incubating
    abstract val testSourceSets: SetProperty<SourceSet?>?

    @get:Incubating
    abstract val testConfigurations: SetProperty<Configuration?>?
}
