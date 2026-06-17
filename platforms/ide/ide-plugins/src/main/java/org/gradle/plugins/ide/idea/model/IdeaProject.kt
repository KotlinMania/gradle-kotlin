/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.plugins.ide.idea.model

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.plugins.ide.IdeWorkspace
import org.gradle.plugins.ide.api.XmlFileContentMerger
import org.gradle.plugins.ide.idea.internal.IdeaModuleMetadata
import org.gradle.plugins.ide.internal.IdeArtifactRegistry
import org.gradle.plugins.ide.internal.IdeDeprecations.nagDeprecatedProperty
import org.gradle.util.internal.ConfigureUtil
import java.io.File
import javax.inject.Inject

/**
 * Enables fine-tuning project details (*.ipr file) of the IDEA plugin.
 *
 *
 * Example of use with a blend of all possible properties.
 * Typically you don't have to configure IDEA module directly because Gradle configures it for you.
 *
 * <pre class='autoTestedWithDeprecations'>
 * import org.gradle.plugins.ide.idea.model.*
 *
 * plugins {
 * id 'java'
 * id 'idea'
 * }
 *
 * idea {
 * project {
 * //if you want to set specific jdk and language level
 * jdkName = '1.6'
 * languageLevel = '1.5'
 *
 * //you can update the source wildcards
 * wildcards += '!?*.ruby'
 *
 * //you can configure the VCS used by the project
 * vcs = 'Git'
 *
 * //you can change the modules of the *.ipr
 * //modules = project(':some-project').idea.module
 *
 * //you can change the output file
 * outputFile = new File(outputFile.parentFile, 'someBetterName.ipr')
 *
 * //you can add project-level libraries
 * projectLibraries &lt;&lt; new ProjectLibrary(name: "my-library", classes: [new File("path/to/library")])
 * }
 * }
</pre> *
 *
 * For tackling edge cases users can perform advanced configuration on resulting XML file.
 * It is also possible to affect the way IDEA plugin merges the existing configuration
 * via beforeMerged and whenMerged closures.
 *
 *
 * beforeMerged and whenMerged closures receive [Project] object
 *
 *
 * Examples of advanced configuration:
 *
 * <pre class='autoTestedWithDeprecations'>
 * plugins {
 * id 'java'
 * id 'idea'
 * }
 *
 * idea {
 * project {
 * ipr {
 * //you can tinker with the output *.ipr file before it's written out
 * withXml {
 * def node = it.asNode()
 * node.appendNode('iLove', 'tinkering with the output *.ipr file!')
 * }
 *
 * //closure executed after *.ipr content is loaded from existing file
 * //but before gradle build information is merged
 * beforeMerged { project -&gt;
 * //you can tinker with [Project]
 * }
 *
 * //closure executed after *.ipr content is loaded from existing file
 * //and after gradle build information is merged
 * whenMerged { project -&gt;
 * //you can tinker with [Project]
 * }
 * }
 * }
 * }
</pre> *
 */
abstract class IdeaProject @Inject constructor(
    /**
     * An owner of this IDEA project.
     *
     *
     * If IdeaProject requires some information from gradle this field should not be used for this purpose.
     */
    val project: Project, private val ipr: XmlFileContentMerger
) : IdeWorkspace {
    private val projectPathRegistry: ProjectStateRegistry?
    private val artifactRegistry: IdeArtifactRegistry?

    /**
     * Modules for the ipr file.
     *
     *
     * See the examples in the docs for [IdeaProject]
     */
    var modules: MutableList<IdeaModule?>? = null

    /**
     * The java version used for defining the project sdk.
     *
     *
     * See the examples in the docs for [IdeaProject]
     */
    var jdkName: String? = null
    /**
     * The default Java language Level to use for this project.
     *
     *
     * Generally, it isn't recommended to change this value. Instead, you are encouraged to set `sourceCompatibility` and `targetCompatibility`
     * for your Gradle projects which allows you to have full control over language levels in Gradle projects, and means that Gradle and IDEA will use the same
     * settings when compiling.
     *
     *
     * When not explicitly set, this is calculated as the maximum language level for the Idea modules of this Idea project.
     */
    /**
     * Sets the java language level for the project.
     *
     *
     * When explicitly set in the build script, this setting overrides any calculated values for Idea project
     * and Idea module.
     *
     * @since 4.0
     */
    var languageLevel: IdeaLanguageLevel? = null

    /**
     * The target bytecode version to use for this project.
     *
     *
     * Generally, it isn't recommended to change this value. Instead, you are encouraged to set `sourceCompatibility` and `targetCompatibility`
     * for your Gradle projects which allows you to have full control over language levels in Gradle projects, and means that Gradle and IDEA will use the same
     * settings when compiling.
     *
     *
     * When `languageLevel` is not explicitly set, this is calculated as the maximum target bytecode version for the Idea modules of this Idea project.
     */
    var targetBytecodeVersion: JavaVersion? = null

    /**
     * The vcs for the project.
     *
     *
     * Values are the same as used in IDEA's "Version Control" preference window (e.g. 'Git', 'Subversion').
     *
     *
     * See the examples in the docs for [IdeaProject].
     */
    var vcs: String? = null

    /**
     * The wildcard resource patterns.
     *
     *
     * See the examples in the docs for [IdeaProject].
     */
    var wildcards: MutableSet<String?>? = LinkedHashSet<String?>()
    private val outputFile: RegularFileProperty

    /**
     * The project-level libraries to be added to the IDEA project.
     */
    var projectLibraries: MutableSet<ProjectLibrary?>? = LinkedHashSet<ProjectLibrary?>()

    @get:Deprecated("Will be removed in Gradle 10.")
    @set:Deprecated("Will be removed in Gradle 10.")
    var pathFactory: PathFactory? = null
        /**
         * Returns the path factory used to construct paths in the generated *.ipr file.
         *
         */
        get() {
            nagDeprecatedProperty(IdeaProject::class.java, "pathFactory")
            return field
        }
        /**
         * Sets the path factory used to construct paths in the generated *.ipr file.
         *
         */
        set(pathFactory) {
            nagDeprecatedProperty(IdeaProject::class.java, "pathFactory")
            field = pathFactory
        }

    init {
        val services = (project as ProjectInternal).getServices()
        this.projectPathRegistry = services.get<ProjectStateRegistry?>(ProjectStateRegistry::class.java)
        this.artifactRegistry = services.get<IdeArtifactRegistry?>(IdeArtifactRegistry::class.java)
        this.outputFile = project.getObjects().fileProperty()
    }

    override fun getDisplayName(): String {
        return "IDEA project"
    }

    val location: Provider<RegularFile?>
        get() = outputFile

    /**
     * See [.ipr]
     *
     */
    @Deprecated("Will be removed in Gradle 10.")
    fun getIpr(): XmlFileContentMerger {
        nagDeprecatedProperty(IdeaProject::class.java, "ipr")
        return ipr
    }

    /**
     * Enables advanced configuration like tinkering with the output XML
     * or affecting the way existing *.ipr content is merged with Gradle build information.
     *
     *
     * See the examples in the docs for [IdeaProject]
     *
     */
    @Deprecated("Will be removed in Gradle 10.")
    fun ipr(@DelegatesTo(XmlFileContentMerger::class) closure: Closure<*>?) {
        nagDeprecatedProperty(IdeaProject::class.java, "ipr")
        ConfigureUtil.configure<XmlFileContentMerger?>(closure, ipr)
    }

    /**
     * Enables advanced configuration like tinkering with the output XML
     * or affecting the way existing *.ipr content is merged with Gradle build information.
     *
     *
     * See the examples in the docs for [IdeaProject]
     *
     * @since 3.5
     */
    @Deprecated("Will be removed in Gradle 10.")
    fun ipr(action: Action<in XmlFileContentMerger?>) {
        nagDeprecatedProperty(IdeaProject::class.java, "ipr")
        action.execute(ipr)
    }

    @get:Suppress("deprecation")
    val name: String
        /**
         * The name of the IDEA project. It is a convenience property that returns the name of the output file (without the file extension).
         * In IDEA, the project name is driven by the name of the 'ipr' file.
         */
        get() = getOutputFile().getName().replaceFirst("\\.ipr$".toRegex(), "")

    /**
     * Sets the java language level for the project.
     * Pass a valid Java version number (e.g. '1.5') or IDEA language level (e.g. 'JDK_1_5').
     *
     *
     * See the examples in the docs for [IdeaProject].
     *
     *
     * When explicitly set in the build script, this setting overrides any calculated values for Idea project
     * and Idea module.
     */
    fun setLanguageLevel(languageLevel: Any?) {
        this.languageLevel = IdeaLanguageLevel(languageLevel)
    }

    /**
     * Output *.ipr
     *
     *
     * See the examples in the docs for [IdeaProject].
     */
    fun getOutputFile(): File {
        return outputFile.get().getAsFile()
    }

    fun setOutputFile(outputFile: File?) {
        this.outputFile.set(outputFile)
    }

    /**
     * Merges the existing *.ipr content with the configuration from this model.
     *
     */
    @Deprecated("Will be removed in Gradle 10.")
    fun mergeXmlProject(xmlProject: org.gradle.plugins.ide.idea.model.Project) {
        nagDeprecatedProperty(IdeaProject::class.java, "ipr")
        ipr.beforeMerged.execute(xmlProject)
        xmlProject.configure(this.modules, this.jdkName, this.languageLevel, this.targetBytecodeVersion, this.wildcards, this.projectLibraries, this.vcs)
        configureModulePaths(xmlProject)
        ipr.whenMerged.execute(xmlProject)
    }

    private fun configureModulePaths(xmlProject: org.gradle.plugins.ide.idea.model.Project) {
        val thisProjectId = projectPathRegistry!!.stateFor(project).getComponentIdentifier()
        for (reference in artifactRegistry!!.getIdeProjects<IdeaModuleMetadata?>(IdeaModuleMetadata::class.java)!!) {
            val otherBuildId = reference!!.owningProject!!.getBuild()
            if (thisProjectId.getBuild() == otherBuildId) {
                // IDEA Module for project in current build: handled via `modules` model elements.
                continue
            }
            xmlProject.addModulePath(reference.get()!!.file)
        }
    }
}
