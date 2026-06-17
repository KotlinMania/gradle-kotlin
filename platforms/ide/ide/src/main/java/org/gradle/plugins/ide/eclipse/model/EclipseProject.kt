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

import com.google.common.collect.ImmutableSet
import com.google.common.collect.Sets
import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.plugins.ide.api.XmlFileContentMerger
import org.gradle.plugins.ide.eclipse.model.internal.DefaultResourceFilter
import org.gradle.util.internal.ClosureBackedAction
import org.gradle.util.internal.ConfigureUtil
import java.util.Arrays
import javax.inject.Inject

/**
 * Enables fine-tuning project details (.project file) of the Eclipse plugin
 *
 *
 * Example of use with a blend of all possible properties.
 * Bear in mind that usually you don't have configure eclipse project directly because Gradle configures it for free!
 *
 * <pre class='autoTested'>
 * plugins {
 * id 'java'
 * id 'eclipse'
 * }
 *
 * eclipse {
 * project {
 * //if you don't like the name Gradle has chosen
 * name = 'someBetterName'
 *
 * //if you want to specify the Eclipse project's comment
 * comment = 'Very interesting top secret project'
 *
 * //if you want to append some extra referenced projects in a declarative fashion:
 * referencedProjects 'someProject', 'someOtherProject'
 * //if you want to assign referenced projects
 * referencedProjects = ['someProject'] as Set
 *
 * //if you want to append some extra natures in a declarative fashion:
 * natures 'some.extra.eclipse.nature', 'some.another.interesting.nature'
 * //if you want to assign natures in a groovy fashion:
 * natures = ['some.extra.eclipse.nature', 'some.another.interesting.nature']
 *
 * //if you want to append some extra build command:
 * buildCommand 'buildThisLovelyProject'
 * //if you want to append a build command with parameters:
 * buildCommand 'buildItWithTheArguments', argumentOne: "I'm first", argumentTwo: "I'm second"
 *
 * //if you want to create an extra link in the eclipse project,
 * //by location uri:
 * linkedResource name: 'someLinkByLocationUri', type: 'someLinkType', locationUri: 'file://someUri'
 * //by location:
 * linkedResource name: 'someLinkByLocation', type: 'someLinkType', location: '/some/location'
 *
 * //if you don't want any node_modules folder to appear in Eclipse, you can filter it out:
 * resourceFilter {
 * appliesTo = 'FOLDERS'
 * type = 'EXCLUDE_ALL'
 * matcher {
 * id = 'org.eclipse.ui.ide.multiFilter'
 * arguments = '1.0-name-matches-false-false-node_modules'
 * }
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
 * beforeMerged and whenMerged closures receive [Project] object
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
 * project {
 *
 * file {
 * //if you want to mess with the resulting XML in whatever way you fancy
 * withXml {
 * def node = it.asNode()
 * node.appendNode('xml', 'is what I love')
 * }
 *
 * //closure executed after .project content is loaded from existing file
 * //but before gradle build information is merged
 * beforeMerged { project -&gt;
 * //if you want skip merging natures... (a very abstract example)
 * project.natures.clear()
 * }
 *
 * //closure executed after .project content is loaded from existing file
 * //and after gradle build information is merged
 * whenMerged { project -&gt;
 * //you can tinker with the [Project] here
 * }
 * }
 * }
 * }
</pre> *
 */
abstract class EclipseProject @Inject constructor(
    /**
     * See [.file]
     */
    val file: XmlFileContentMerger
) {
    /**
     * Configures eclipse project name. It is **optional** because the task should configure it correctly for you.
     * By default it will try to use the **project.name** or prefix it with a part of a **project.path**
     * to make sure the moduleName is unique in the scope of a multi-module build.
     * The 'uniqueness' of a module name is required for correct import
     * into Eclipse and the task will make sure the name is unique.
     *
     *
     * The logic that makes sure project names are unique is available **since** 1.0-milestone-2
     *
     *
     * If your project has problems with unique names it is recommended to always run gradle eclipse from the root, e.g. for all subprojects, including generation of .classpath.
     * If you run the generation of the eclipse project only for a single subproject then you may have different results
     * because the unique names are calculated based on eclipse projects that are involved in the specific build run.
     *
     *
     * If you update the project names then make sure you run gradle eclipse from the root, e.g. for all subprojects.
     * The reason is that there may be subprojects that depend on the subproject with amended eclipse project name.
     * So you want them to be generated as well because the project dependencies in .classpath need to refer to the amended project name.
     * Basically, for non-trivial projects it is recommended to always run gradle eclipse from the root.
     *
     *
     * For example see docs for [EclipseProject]
     */
    var name: String? = null

    /**
     * A comment used for the eclipse project. By default it will be configured to **project.description**
     *
     *
     * For example see docs for [EclipseProject]
     */
    var comment: String? = null

    /**
     * The referenced projects of this Eclipse project (*not*: java build path project references).
     *
     *
     * Referencing projects does not mean adding a build path dependencies between them!
     * If you need to configure a build path dependency use Gradle's dependencies section or
     * eclipse.classpath.whenMerged { classpath -&gt; ... to manipulate the classpath entries
     *
     *
     * For example see docs for [EclipseProject]
     */
    var referencedProjects: MutableSet<String?> = LinkedHashSet<String?>()

    /**
     * The natures to be added to this Eclipse project.
     *
     *
     * For example see docs for [EclipseProject]
     */
    var natures: MutableList<String?> = ArrayList<String?>()

    /**
     * The build commands to be added to this Eclipse project.
     *
     *
     * For example see docs for [EclipseProject]
     */
    var buildCommands: MutableList<BuildCommand?> = ArrayList<BuildCommand?>()

    /**
     * The linked resources to be added to this Eclipse project.
     *
     *
     * For example see docs for [EclipseProject]
     */
    var linkedResources: MutableSet<Link?> = LinkedHashSet<Link?>()

    /**
     * The resource filters of the eclipse project.
     * @since 3.5
     */
    val resourceFilters: MutableSet<ResourceFilter?> = LinkedHashSet<ResourceFilter?>()


    /**
     * The referenced projects of this Eclipse project (*not*: java build path project references).
     *
     * Referencing projects does not mean adding a build path dependencies between them! If you need to
     * configure a build path dependency use Gradle's dependencies section or eclipse.classpath.whenMerged { classpath -&gt; ... to manipulate the classpath entries
     *
     * @param referencedProjects The name of the project references.
     */
    fun referencedProjects(vararg referencedProjects: String?) {
        checkNotNull(referencedProjects)
        this.referencedProjects.addAll(Arrays.asList<String?>(*referencedProjects))
    }

    /**
     * Appends natures entries to the eclipse project.
     *
     * For example see docs for [EclipseProject]
     *
     * @param natures the nature names
     */
    fun natures(vararg natures: String?) {
        checkNotNull(natures)
        this.natures.addAll(Arrays.asList<String?>(*natures))
    }

    /**
     * Adds a build command with arguments to the eclipse project.
     *
     * For example see docs for [EclipseProject]
     *
     * @param args A map with arguments, where the key is the name of the argument and the value the value.
     * @param buildCommand The name of the build command.
     * @see .buildCommand
     */
    fun buildCommand(args: MutableMap<String?, String?>?, buildCommand: String) {
        checkNotNull(buildCommand)
        buildCommands.add(BuildCommand(buildCommand, args))
    }

    /**
     * Adds a build command to the eclipse project.
     *
     * For example see docs for [EclipseProject]
     *
     * @param buildCommand The name of the build command
     * @see .buildCommand
     */
    fun buildCommand(buildCommand: String) {
        checkNotNull(buildCommand)
        buildCommands.add(BuildCommand(buildCommand))
    }

    /**
     * Adds a resource link (aka 'source link') to the eclipse project.
     *
     * For example see docs for [EclipseProject]
     *
     * @param args A maps with the args for the link. Legal keys for the map are name, type, location and locationUri.
     */
    fun linkedResource(args: MutableMap<String?, String?>) {
        val illegalArgs: MutableSet<String?> = Sets.difference<String?>(args.keys, VALID_LINKED_RESOURCE_ARGS)
        if (!illegalArgs.isEmpty()) {
            throw InvalidUserDataException("You provided illegal argument for a link: " + illegalArgs + ". Valid link args are: " + VALID_LINKED_RESOURCE_ARGS)
        }

        linkedResources.add(Link(args.get("name"), args.get("type"), args.get("location"), args.get("locationUri")))
    }

    /**
     * Adds a resource filter to the eclipse project.
     *
     *
     * For examples, see docs for [ResourceFilter]
     *
     * @param configureClosure The closure to use to configure the resource filter.
     * @since 3.5
     */
    fun resourceFilter(@DelegatesTo(value = ResourceFilter::class, strategy = Closure.DELEGATE_FIRST) configureClosure: Closure<*>?): ResourceFilter {
        return resourceFilter(ClosureBackedAction<ResourceFilter?>(configureClosure))
    }

    /**
     * Adds a resource filter to the eclipse project.
     *
     *
     * For examples, see docs for [ResourceFilter]
     *
     * @param configureAction The action to use to configure the resource filter.
     * @since 3.5
     */
    fun resourceFilter(configureAction: Action<in ResourceFilter?>): ResourceFilter {
        val f: ResourceFilter = DefaultResourceFilter()
        configureAction.execute(f)
        resourceFilters.add(f)
        return f
    }

    /**
     * Enables advanced configuration like tinkering with the output XML or affecting the way existing .project content is merged with gradle build information
     *
     * The object passed to whenMerged{}
     * and beforeMerged{} closures is of type [Project]
     *
     *
     *
     * For example see docs for [EclipseProject]
     */
    fun file(@DelegatesTo(XmlFileContentMerger::class) closure: Closure<*>?) {
        ConfigureUtil.configure<XmlFileContentMerger?>(closure, file)
    }

    /**
     * Enables advanced configuration like tinkering with the output XML or affecting the way existing .project content is merged with gradle build information.
     *
     * For example see docs for [EclipseProject]
     *
     * @since 3.5
     */
    fun file(action: Action<in XmlFileContentMerger?>) {
        action.execute(file)
    }

    fun mergeXmlProject(xmlProject: Project) {
        val decoratedProject: Project = NonRenamableProject(xmlProject)
        file.getBeforeMerged().execute(decoratedProject)
        xmlProject.configure(this)
        file.getWhenMerged().execute(decoratedProject)
    }

    companion object {
        val VALID_LINKED_RESOURCE_ARGS: ImmutableSet<String?> = ImmutableSet.of<String?>("name", "type", "location", "locationUri")
    }
}
