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
import com.google.common.base.Strings
import com.google.common.collect.Lists
import com.google.common.collect.Sets
import groovy.util.Node
import org.gradle.internal.xml.XmlTransformer
import org.gradle.plugins.ide.eclipse.model.internal.DefaultResourceFilter
import org.gradle.plugins.ide.eclipse.model.internal.DefaultResourceFilterMatcher
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject

/**
 * Represents the customizable elements of an eclipse project file. (via XML hooks everything is customizable).
 */
open class Project(xmlTransformer: XmlTransformer?) : XmlPersistableConfigurationObject(xmlTransformer) {
    /**
     * The name used for the name of the eclipse project
     */
    open var name: String? = null

    /**
     * A comment used for the eclipse project
     */
    open var comment: String? = null

    /**
     * The referenced projects of this Eclipse project.
     */
    open var referencedProjects: MutableSet<String?>? = LinkedHashSet<String?>()

    /**
     * The natures to be added to this Eclipse project.
     */
    open var natures: MutableList<String>? = ArrayList<String>()

    /**
     * The build commands to be added to this Eclipse project.
     */
    open var buildCommands: MutableList<BuildCommand>? = ArrayList<BuildCommand>()

    /**
     * The linkedResources to be added to this Eclipse project.
     */
    open var linkedResources: MutableSet<Link>? = LinkedHashSet<Link>()
    /**
     * The resource filters of this Eclipse project.
     *
     * @since 3.5
     */
    /**
     * Sets the resource filters of this Eclipse project.
     *
     * @since 3.5
     */
    var resourceFilters: MutableSet<ResourceFilter>? = LinkedHashSet<ResourceFilter>()

    public override fun getDefaultResourceName(): String? {
        return "defaultProject.xml"
    }

    open fun configure(eclipseProject: EclipseProject): Any? {
        name = Strings.nullToEmpty(eclipseProject.getName())
        comment = Strings.nullToEmpty(eclipseProject.getComment())
        referencedProjects!!.addAll(eclipseProject.getReferencedProjects())
        natures!!.addAll(eclipseProject.getNatures())
        natures = Lists.newArrayList<String?>(Sets.newLinkedHashSet<String?>(natures!!))
        buildCommands!!.addAll(eclipseProject.getBuildCommands())
        buildCommands = Lists.newArrayList<BuildCommand?>(Sets.newLinkedHashSet<BuildCommand?>(buildCommands!!))
        resourceFilters!!.addAll(eclipseProject.getResourceFilters())
        return linkedResources!!.addAll(eclipseProject.getLinkedResources())
    }

    public override fun load(xml: Node?) {
        val nameNode: Node? = XmlPersistableConfigurationObject.Companion.findFirstChildNamed(xml, "name")
        name = if (nameNode != null) nameNode.text() else ""
        val commentNode: Node? = XmlPersistableConfigurationObject.Companion.findFirstChildNamed(xml, "comment")
        comment = if (commentNode != null) commentNode.text() else ""
        readReferencedProjects()
        readNatures()
        readBuildCommands()
        readLinkedResources()
        readResourceFilters()
    }

    private fun readReferencedProjects() {
        for (projectNode in XmlPersistableConfigurationObject.Companion.getChildren(XmlPersistableConfigurationObject.Companion.findFirstChildNamed(getXml(), "projects"), "project")) {
            referencedProjects!!.add(projectNode.text())
        }
    }

    private fun readNatures() {
        for (natureNode in XmlPersistableConfigurationObject.Companion.getChildren(XmlPersistableConfigurationObject.Companion.findFirstChildNamed(getXml(), "natures"), "nature")) {
            natures!!.add(natureNode.text())
        }
    }

    private fun readBuildCommands() {
        for (commandNode in XmlPersistableConfigurationObject.Companion.getChildren(XmlPersistableConfigurationObject.Companion.findFirstChildNamed(getXml(), "buildSpec"), "buildCommand")) {
            val name: String? = XmlPersistableConfigurationObject.Companion.findFirstChildNamed(commandNode, "name").text()
            val arguments: MutableMap<String?, String?> = LinkedHashMap<String?, String?>()
            for (dictionaryNode in XmlPersistableConfigurationObject.Companion.getChildren(XmlPersistableConfigurationObject.Companion.findFirstChildNamed(commandNode, "arguments"), "dictionary")) {
                val key: String? = XmlPersistableConfigurationObject.Companion.findFirstChildNamed(dictionaryNode, "key").text()
                val value: String? = XmlPersistableConfigurationObject.Companion.findFirstChildNamed(dictionaryNode, "value").text()
                arguments.put(key, value)
            }
            buildCommands!!.add(BuildCommand(name, arguments))
        }
    }

    private fun readLinkedResources() {
        for (linkNode in XmlPersistableConfigurationObject.Companion.getChildren(XmlPersistableConfigurationObject.Companion.findFirstChildNamed(getXml(), "linkedResources"), "link")) {
            val nameNode: Node? = XmlPersistableConfigurationObject.Companion.findFirstChildNamed(linkNode, "name")
            val typeNode: Node? = XmlPersistableConfigurationObject.Companion.findFirstChildNamed(linkNode, "type")
            val locationNode: Node? = XmlPersistableConfigurationObject.Companion.findFirstChildNamed(linkNode, "location")
            val locationUriNode: Node? = XmlPersistableConfigurationObject.Companion.findFirstChildNamed(linkNode, "locationURI")
            linkedResources!!.add(
                Link(
                    if (nameNode != null) nameNode.text() else null,
                    if (typeNode != null) typeNode.text() else null,
                    if (locationNode != null) locationNode.text() else null,
                    if (locationUriNode != null) locationUriNode.text() else null
                )
            )
        }
    }

    private fun readResourceFilters() {
        for (filterNode in XmlPersistableConfigurationObject.Companion.getChildren(XmlPersistableConfigurationObject.Companion.findFirstChildNamed(getXml(), "filteredResources"), "filter")) {
            val typeNode: Node? = XmlPersistableConfigurationObject.Companion.findFirstChildNamed(filterNode, "type")
            val matcherNode: Node? = XmlPersistableConfigurationObject.Companion.findFirstChildNamed(filterNode, "matcher")
            val typeString: String = (if (typeNode != null) typeNode.text() else null)!!
            val typeBitmask = typeString.toInt()
            val appliesTo = resourceFilterTypeBitmaskToAppliesTo(typeBitmask)
            val type = resourceFilterTypeBitmaskToType(typeBitmask)
            val recursive = isResourceFilterTypeBitmaskRecursive(typeBitmask)
            val matcher = readResourceFilterMatcher(matcherNode)
            resourceFilters!!.add(
                DefaultResourceFilter(
                    appliesTo,
                    type,
                    recursive,
                    matcher
                )
            )
        }
    }

    public override fun store(xml: Node) {
        for (childNodeName in mutableListOf<String?>("name", "comment", "projects", "natures", "buildSpec", "linkedResources", "filteredResources")) {
            val childNode: Node? = XmlPersistableConfigurationObject.Companion.findFirstChildNamed(xml, childNodeName)
            if (childNode != null) {
                xml.remove(childNode)
            }
        }
        xml.appendNode("name", Strings.nullToEmpty(name))
        xml.appendNode("comment", Strings.nullToEmpty(comment))
        addReferencedProjectsToXml()
        addNaturesToXml()
        addBuildSpecToXml()
        addLinkedResourcesToXml()
        addResourceFiltersToXml()
    }

    private fun addReferencedProjectsToXml() {
        val referencedProjectsNode = getXml().appendNode("projects")
        for (projectName in referencedProjects!!) {
            referencedProjectsNode.appendNode("project", projectName)
        }
    }

    private fun addNaturesToXml() {
        val naturesNode = getXml().appendNode("natures")
        for (nature in natures!!) {
            naturesNode.appendNode("nature", nature)
        }
    }

    private fun addBuildSpecToXml() {
        val buildSpec = getXml().appendNode("buildSpec")
        for (command in buildCommands!!) {
            val commandNode = buildSpec.appendNode("buildCommand")
            commandNode.appendNode("name", command.getName())
            val argumentsNode = commandNode.appendNode("arguments")
            for (argument in command.getArguments().entries) {
                val dictionaryNode = argumentsNode.appendNode("dictionary")
                dictionaryNode.appendNode("key", argument.key)
                dictionaryNode.appendNode("value", argument.value)
            }
        }
    }

    private fun addLinkedResourcesToXml() {
        val parent = getXml().appendNode("linkedResources")
        for (link in linkedResources!!) {
            val linkNode = parent.appendNode("link")
            linkNode.appendNode("name", link.getName())
            linkNode.appendNode("type", link.getType())
            if (!Strings.isNullOrEmpty(link.getLocation())) {
                linkNode.appendNode("location", link.getLocation())
            }
            if (!Strings.isNullOrEmpty(link.getLocationUri())) {
                linkNode.appendNode("locationURI", link.getLocationUri())
            }
        }
    }

    private fun addResourceFiltersToXml() {
        val parent = getXml().appendNode("filteredResources")
        var filterId = 1
        for (resourceFilter in resourceFilters!!) {
            val filterNode = parent.appendNode("filter")
            filterNode.appendNode("id", filterId++)
            val type = getResourceFilterType(resourceFilter)
            filterNode.appendNode("type", type)
            filterNode.appendNode("name") // always empty
            addResourceFilterMatcherToXml(filterNode, resourceFilter.getMatcher())
        }
    }

    private fun addResourceFilterMatcherToXml(parent: Node, matcher: ResourceFilterMatcher) {
        val matcherNode = parent.appendNode("matcher")
        matcherNode.appendNode("id", matcher.getId())
        // A matcher may have either arguments or children, but not both
        if (!Strings.isNullOrEmpty(matcher.getArguments())) {
            matcherNode.appendNode("arguments", matcher.getArguments())
        } else if (!matcher.getChildren().isEmpty()) {
            val argumentsNode = matcherNode.appendNode("arguments")
            for (m in matcher.getChildren()) {
                addResourceFilterMatcherToXml(argumentsNode, m)
            }
        }
    }

    private fun getResourceFilterType(resourceFilter: ResourceFilter): Int {
        var type = 0
        when (resourceFilter.getType()) {
            ResourceFilterType.INCLUDE_ONLY -> type = type or 1
            ResourceFilterType.EXCLUDE_ALL -> type = type or 2
        }
        when (resourceFilter.getAppliesTo()) {
            ResourceFilterAppliesTo.FILES -> type = type or 4
            ResourceFilterAppliesTo.FOLDERS -> type = type or 8
            ResourceFilterAppliesTo.FILES_AND_FOLDERS -> type = type or 12
        }
        if (resourceFilter.isRecursive()) {
            type = type or 16
        }
        return type
    }

    private fun resourceFilterTypeBitmaskToAppliesTo(type: Int): ResourceFilterAppliesTo? {
        Preconditions.checkArgument(type >= 0)
        if (((type and 8) != 0) && ((type and 4) != 0)) { // order is important here, this must come first
            return ResourceFilterAppliesTo.FILES_AND_FOLDERS
        }
        if ((type and 8) != 0) {
            return ResourceFilterAppliesTo.FOLDERS
        }
        if ((type and 4) != 0) {
            return ResourceFilterAppliesTo.FILES
        }
        return null
    }

    private fun resourceFilterTypeBitmaskToType(type: Int): ResourceFilterType? {
        Preconditions.checkArgument(type >= 0)
        if ((type and 1) != 0) {
            return ResourceFilterType.INCLUDE_ONLY
        }
        if ((type and 2) != 0) {
            return ResourceFilterType.EXCLUDE_ALL
        }
        return null
    }

    private fun isResourceFilterTypeBitmaskRecursive(type: Int): Boolean {
        Preconditions.checkArgument(type >= 0)
        return (type and 16) != 0
    }

    private fun readResourceFilterMatcher(matcherNode: Node?): ResourceFilterMatcher? {
        if (matcherNode == null) {
            return null
        }
        val idNode: Node? = XmlPersistableConfigurationObject.Companion.findFirstChildNamed(matcherNode, "id")
        val argumentsNode: Node? = XmlPersistableConfigurationObject.Companion.findFirstChildNamed(matcherNode, "arguments")
        var arguments: String? = null
        val children: MutableSet<ResourceFilterMatcher?> = LinkedHashSet<ResourceFilterMatcher?>()
        // A matcher may have either a text argument or children matcher nodes, but not both
        if (argumentsNode != null && XmlPersistableConfigurationObject.Companion.findFirstChildNamed(argumentsNode, "matcher") != null) {
            for (childMatcherNode in XmlPersistableConfigurationObject.Companion.getChildren(argumentsNode, "matcher")) {
                val childMatcher = readResourceFilterMatcher(childMatcherNode)
                if (childMatcher != null) {
                    children.add(childMatcher)
                }
            }
        } else {
            arguments = if (argumentsNode != null) argumentsNode.text() else null
        }
        return DefaultResourceFilterMatcher(
            if (idNode != null) idNode.text() else null,
            arguments,
            children
        )
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (javaClass != o.javaClass) {
            return false
        }
        val project = o as Project
        return Objects.equal(buildCommands, project.buildCommands)
                && Objects.equal(comment, project.comment)
                && Objects.equal(linkedResources, project.linkedResources)
                && Objects.equal(resourceFilters, project.resourceFilters)
                && Objects.equal(name, project.name)
                && Objects.equal(natures, project.natures)
                && Objects.equal(referencedProjects, project.referencedProjects)
    }

    override fun hashCode(): Int {
        var result: Int
        result = if (name != null) name.hashCode() else 0
        result = 31 * result + (if (comment != null) comment.hashCode() else 0)
        result = 31 * result + (if (referencedProjects != null) referencedProjects.hashCode() else 0)
        result = 31 * result + (if (natures != null) natures.hashCode() else 0)
        result = 31 * result + (if (buildCommands != null) buildCommands.hashCode() else 0)
        result = 31 * result + (if (linkedResources != null) linkedResources.hashCode() else 0)
        result = 31 * result + (if (resourceFilters != null) resourceFilters.hashCode() else 0)
        return result
    }

    override fun toString(): String {
        return ("Project{"
                + "name='" + name + "\'"
                + ", comment='" + comment + "\'"
                + ", referencedProjects=" + referencedProjects
                + ", natures=" + natures
                + ", buildCommands=" + buildCommands
                + ", linkedResources=" + linkedResources
                + ", resourceFilters=" + resourceFilters
                + "}")
    }

    companion object {
        const val PROJECT_FILE_NAME: String = ".project"
    }
}
