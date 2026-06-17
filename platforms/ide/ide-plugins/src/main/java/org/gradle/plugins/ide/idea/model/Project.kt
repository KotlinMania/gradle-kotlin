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
package org.gradle.plugins.ide.idea.model

import com.google.common.base.Objects
import com.google.common.base.Preconditions
import com.google.common.base.Strings
import com.google.common.collect.Sets
import groovy.util.Node
import groovy.util.NodeList
import org.gradle.api.JavaVersion
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.xml.XmlTransformer
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject
import java.io.File

/**
 * Represents the customizable elements of an ipr (via XML hooks everything of the ipr is customizable).
 */
class Project(xmlTransformer: XmlTransformer, pathFactory: Any?) : XmlPersistableConfigurationObject(xmlTransformer) {
    private val pathFactory: PathFactory
    private var modules: MutableList<IdeaModule>? = null
    private var bytecodeVersion: JavaVersion? = null

    /**
     * A set of [Path] instances pointing to the modules contained in the ipr.
     */
    var modulePaths: MutableSet<Path>? = LinkedHashSet<Path>()

    /**
     * A set of wildcard string to be included/excluded from the resources.
     */
    var wildcards: MutableSet<String?>? = LinkedHashSet<String?>()

    /**
     * Represent the jdk information of the project java sdk.
     */
    var jdk: Jdk? = null

    /**
     * The vcs used by the project.
     */
    var vcs: String? = null

    /**
     * The project-level libraries of the IDEA project.
     */
    var projectLibraries: MutableSet<ProjectLibrary>? = LinkedHashSet<ProjectLibrary>()

    init {
        this.pathFactory = pathFactory as PathFactory
    }

    /**
     * Adds a module to the module paths included in the Project.
     *
     * @param moduleFile path to the module's module file
     *
     * @since 4.0
     */
    fun addModulePath(moduleFile: File) {
        modulePaths!!.add(pathFactory.relativePath("PROJECT_DIR", moduleFile))
    }

    val defaultResourceName: String
        get() = "defaultProject.xml"

    @Suppress("deprecation")
    fun configure(
        modules: MutableList<IdeaModule>,
        jdkName: String?, languageLevel: IdeaLanguageLevel, bytecodeVersion: JavaVersion,
        wildcards: MutableCollection<String?>, projectLibraries: MutableCollection<ProjectLibrary?>, vcs: String?
    ) {
        if (!Strings.isNullOrEmpty(jdkName)) {
            jdk = Jdk(jdkName!!, languageLevel)
        }
        this.bytecodeVersion = bytecodeVersion
        this.modules = modules
        for (module in modules) {
            addModulePath(module.getOutputFile())
        }
        this.wildcards!!.addAll(wildcards)
        // overwrite rather than append libraries
        this.projectLibraries = Sets.newLinkedHashSet<ProjectLibrary?>(projectLibraries)
        this.vcs = vcs
    }

    override fun load(xml: Node?) {
        loadModulePaths()
        loadWildcards()
        loadJdk()
        loadProjectLibraries()
    }

    override fun store(xml: Node?) {
        storeModulePaths()
        storeWildcards()
        storeJdk()
        storeBytecodeLevels()
        storeVcs()
        storeProjectLibraries()
    }

    private fun loadModulePaths() {
        for (moduleNode in getChildren(findOrCreateModules(), "module")!!) {
            val fileurl = moduleNode!!.attribute("fileurl") as String
            val filepath = moduleNode.attribute("filepath") as String?
            modulePaths!!.add(pathFactory.path(fileurl, filepath))
        }
    }

    private fun loadWildcards() {
        val wildcardsNodes: MutableList<Node?> = getChildren(findCompilerConfiguration(), "wildcardResourcePatterns")!!
        for (wildcardsNode in wildcardsNodes) {
            for (entry in getChildren(wildcardsNode, "entry")!!) {
                this.wildcards!!.add(entry!!.attribute("name") as String?)
            }
        }
    }

    private fun loadJdk() {
        val projectRoot = findProjectRootManager()
        val assertKeyword = (projectRoot.attribute("assert-keyword") as String?).toBoolean()
        val jdk15 = (projectRoot.attribute("jdk-15") as String?).toBoolean()
        val languageLevel = projectRoot.attribute("languageLevel") as String?
        val jdkName = projectRoot.attribute("project-jdk-name") as String?
        jdk = Jdk(assertKeyword, jdk15, languageLevel, jdkName)
    }

    private fun loadProjectLibraries() {
        val libraryTable = findOrCreateLibraryTable()
        for (library in getChildren(libraryTable, "library")!!) {
            val projectLibrary = ProjectLibrary()
            projectLibrary.setName((library!!.attribute("name") as kotlin.String?)!!)
            projectLibrary.classes = collectRootUrlAsFiles(getChildren(library, "CLASSES")!!)
            projectLibrary.javadoc = collectRootUrlAsFiles(getChildren(library, "JAVADOC")!!)
            projectLibrary.sources = collectRootUrlAsFiles(getChildren(library, "SOURCES")!!)
            projectLibraries!!.add(projectLibrary)
        }
    }

    private fun collectRootUrlAsFiles(nodes: MutableList<Node?>): MutableSet<File?> {
        val files: MutableSet<File?> = LinkedHashSet<File?>()
        for (node in nodes) {
            for (root in getChildren(node, "root")!!) {
                val url = root!!.attribute("url") as String
                files.add(File(url))
            }
        }
        return files
    }

    private fun storeModulePaths() {
        val modulesNode = Node(null, "modules")
        for (modulePath in modulePaths!!) {
            val attributes: MutableMap<String?, Any?> = LinkedHashMap<String?, Any?>()
            attributes.put("fileurl", modulePath.url)
            attributes.put("filepath", modulePath.relPath)
            modulesNode.appendNode("module", attributes)
        }
        findOrCreateModules()!!.replaceNode(modulesNode)
    }

    private fun storeWildcards() {
        val compilerConfigNode = findCompilerConfiguration()
        val existingNode: Node = findOrCreateFirstChildNamed(compilerConfigNode, "wildcardResourcePatterns")!!
        val wildcardsNode = Node(null, "wildcardResourcePatterns")
        for (wildcard in wildcards!!) {
            val attributes: MutableMap<String?, Any?> = LinkedHashMap<String?, Any?>()
            attributes.put("name", wildcard)
            wildcardsNode.appendNode("entry", attributes)
        }
        existingNode.replaceNode(wildcardsNode)
    }

    private fun storeJdk() {
        val projectRoot = findProjectRootManager()
        setNodeAttribute(projectRoot, "assert-keyword", jdk!!.isAssertKeyword)
        setNodeAttribute(projectRoot, "assert-jdk-15", jdk!!.isJdk15)
        setNodeAttribute(projectRoot, "languageLevel", jdk!!.languageLevel)
        setNodeAttribute(projectRoot, "project-jdk-name", jdk!!.projectJdkName)
    }

    private fun storeBytecodeLevels() {
        val bytecodeLevelConfiguration = findOrCreateBytecodeLevelConfiguration()
        setNodeAttribute(bytecodeLevelConfiguration, "target", bytecodeVersion.toString())
        for (module in modules!!) {
            val bytecodeLevelModules = getChildren(bytecodeLevelConfiguration, "module")
            var moduleNode = findFirstWithAttributeValue(bytecodeLevelModules, "name", module.getName())
            val moduleBytecodeVersionOverwrite = module.getTargetBytecodeVersion()
            if (moduleBytecodeVersionOverwrite == null) {
                if (moduleNode != null) {
                    bytecodeLevelConfiguration.remove(moduleNode)
                }
            } else {
                if (moduleNode == null) {
                    moduleNode = bytecodeLevelConfiguration.appendNode("module")
                    setNodeAttribute(moduleNode, "name", module.getName())
                }
                setNodeAttribute(moduleNode, "target", moduleBytecodeVersionOverwrite.toString())
            }
        }
    }

    private fun storeVcs() {
        if (!Strings.isNullOrEmpty(vcs)) {
            setNodeAttribute(findVcsDirectoryMappings(), "vcs", vcs)
        }
    }

    private fun storeProjectLibraries() {
        val libraryTable = findOrCreateLibraryTable()
        if (projectLibraries!!.isEmpty()) {
            xml!!.remove(libraryTable)
            return
        }
        libraryTable.setValue(NodeList())
        for (library in projectLibraries!!) {
            library.addToNode(libraryTable, pathFactory)
        }
    }

    private fun findProjectRootManager(): Node {
        return findFirstWithAttributeValue(getChildren(xml, "component"), "name", "ProjectRootManager")!!
    }

    private fun findOrCreateModules(): Node? {
        val moduleManager = findFirstWithAttributeValue(getChildren(xml, "component"), "name", "ProjectModuleManager")
        Preconditions.checkNotNull<Node?>(moduleManager)
        var modules = findFirstChildNamed(moduleManager, "modules")
        if (modules == null) {
            modules = moduleManager!!.appendNode("modules")
        }
        return modules
    }

    private fun findCompilerConfiguration(): Node {
        return findFirstWithAttributeValue(getChildren(xml, "component"), "name", "CompilerConfiguration")!!
    }

    private fun findOrCreateBytecodeLevelConfiguration(): Node {
        var compilerConfiguration = findCompilerConfiguration()
        if (compilerConfiguration == null) {
            val attributes: MutableMap<String?, Any?> = LinkedHashMap<String?, Any?>()
            attributes.put("name", "CompilerConfiguration")
            compilerConfiguration = xml!!.appendNode("component", attributes)
        }
        return findOrCreateFirstChildNamed(compilerConfiguration, "bytecodeTargetLevel")!!
    }

    private fun findVcsDirectoryMappings(): Node {
        val vcsDirMappings = findFirstWithAttributeValue(getChildren(xml, "component"), "name", "VcsDirectoryMappings")
        return findFirstChildNamed(vcsDirMappings, "mapping")!!
    }

    private fun findOrCreateLibraryTable(): Node {
        var libraryTable = findFirstWithAttributeValue(getChildren(xml, "component"), "name", "libraryTable")
        if (libraryTable == null) {
            val attributes: MutableMap<String?, Any?> = LinkedHashMap<String?, Any?>()
            attributes.put("name", "libraryTable")
            libraryTable = xml!!.appendNode("component", attributes)
        }
        return libraryTable
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (javaClass != o.javaClass) {
            return false
        }
        val project = o as Project
        return Objects.equal(jdk, project.jdk)
                && Objects.equal(modulePaths, project.modulePaths)
                && Objects.equal(projectLibraries, project.projectLibraries)
                && Objects.equal(wildcards, project.wildcards)
                && Objects.equal(vcs, project.vcs)
    }

    override fun hashCode(): Int {
        var result: Int
        result = if (modulePaths != null) modulePaths.hashCode() else 0
        result = 31 * result + (if (wildcards != null) wildcards.hashCode() else 0)
        result = 31 * result + (if (projectLibraries != null) projectLibraries.hashCode() else 0)
        result = 31 * result + (if (jdk != null) jdk.hashCode() else 0)
        result = 31 * result + (if (vcs != null) vcs.hashCode() else 0)
        return result
    }

    companion object {
        private fun setNodeAttribute(node: Node, key: String?, value: Any?) {
            val attributes = uncheckedCast<MutableMap<String?, Any?>?>(node.attributes())
            attributes!!.put(key, value)
        }
    }
}
