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
package org.gradle.plugins.ide.idea.model

import com.google.common.base.Objects
import com.google.common.base.Preconditions
import com.google.common.base.Strings
import groovy.util.Node
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.xml.XmlTransformer
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject

/**
 * Represents the customizable elements of an iml (via XML hooks everything of the iml is customizable).
 */
class Module(withXmlActions: XmlTransformer?, private val pathFactory: PathFactory) : XmlPersistableConfigurationObject(withXmlActions) {
    /**
     * The directory for the content root of the module.
     * Defaults to the project directory.
     * If null, the directory containing the output file will be used.
     */
    var contentPath: Path? = null

    /**
     * The directories containing the production sources.
     * Must not be null.
     */
    var sourceFolders: MutableSet<Path> = LinkedHashSet<Path>()

    /**
     * The directories containing the test sources.
     * Must not be null.
     */
    var testSourceFolders: MutableSet<Path> = LinkedHashSet<Path>()
    /**
     * The directories containing resources.
     * Must not be null.
     * @since 4.7
     */
    /**
     * Sets the directories containing resources.
     * @since 4.7
     */
    var resourceFolders: MutableSet<Path> = LinkedHashSet<Path>()
    /**
     * The directories containing test resources.
     * Must not be null.
     * @since 4.7
     */
    /**
     * Sets the directories containing test resources.
     * @since 4.7
     */
    var testResourceFolders: MutableSet<Path> = LinkedHashSet<Path>()

    /**
     * The directories containing generated the production sources.
     * Must not be null.
     */
    var generatedSourceFolders: MutableSet<Path?> = LinkedHashSet<Path?>()

    /**
     * The directories to be excluded.
     * Must not be null.
     */
    var excludeFolders: MutableSet<Path> = LinkedHashSet<Path>()

    /**
     * If true, output directories for this module will be located below the output directory for the project;
     * otherwise, [.outputDir] and [.testOutputDir] will take effect.
     */
    var isInheritOutputDirs: Boolean = false

    /**
     * The output directory for production classes.
     * If `null`, no entry will be created.
     */
    var outputDir: Path? = null

    /**
     * The output directory for test classes.
     * If `null`, no entry will be created.
     */
    var testOutputDir: Path? = null

    /**
     * The dependencies of this module.
     * Must not be null.
     */
    var dependencies: MutableSet<Dependency> = LinkedHashSet<Dependency>()
    private var jdkName: String? = null
    private var languageLevel: String? = null

    fun getJdkName(): String {
        return jdkName!!
    }

    fun setJdkName(jdkName: String) {
        this.jdkName = jdkName
    }

    override fun getDefaultResourceName(): String {
        return "defaultModule.xml"
    }

    fun configure(
        contentPath: Path?,
        sourceFolders: MutableSet<Path?>, testSourceFolders: MutableSet<Path?>,
        resourceFolders: MutableSet<Path?>, testResourceFolders: MutableSet<Path?>,
        generatedSourceFolders: MutableSet<Path?>,
        excludeFolders: MutableSet<Path?>,
        inheritOutputDirs: Boolean?, outputDir: Path?, testOutputDir: Path?,
        dependencies: MutableSet<Dependency>, jdkName: String, languageLevel: String?
    ): Any {
        this.languageLevel = languageLevel
        this.contentPath = contentPath
        this.sourceFolders.addAll(sourceFolders)
        this.testSourceFolders.addAll(testSourceFolders)
        this.resourceFolders.addAll(resourceFolders)
        this.testResourceFolders.addAll(testResourceFolders)
        this.generatedSourceFolders.addAll(generatedSourceFolders)
        this.excludeFolders.addAll(excludeFolders)
        if (inheritOutputDirs != null) {
            this.isInheritOutputDirs = inheritOutputDirs
        }
        if (outputDir != null) {
            this.outputDir = outputDir
        }
        if (testOutputDir != null) {
            this.testOutputDir = testOutputDir
        }
        this.dependencies = dependencies // overwrite rather than append dependencies
        if (!Strings.isNullOrEmpty(jdkName)) {
            this.jdkName = jdkName
        } else {
            this.jdkName = INHERITED
        }
        return this.jdkName!!
    }

    override fun load(xml: Node?) {
        readJdkFromXml()
        readSourceAndExcludeFolderFromXml()
        readInheritOutputDirsFromXml()
        readOutputDirsFromXml()
        readDependenciesFromXml()
    }

    override fun store(xml: Node?) {
        addJdkToXml()
        setContentURL()
        removeSourceAndExcludeFolderFromXml()
        addSourceAndExcludeFolderToXml()
        writeInheritOutputDirsToXml()
        writeSourceLanguageLevel()
        addOutputDirsToXml()

        removeDependenciesFromXml()
        addDependenciesToXml()
    }

    private fun readJdkFromXml() {
        val jdk: Node? = XmlPersistableConfigurationObject.Companion.findFirstWithAttributeValue(findOrderEntries(), "type", "jdk")
        jdkName = if (jdk != null) jdk.attribute("jdkName") as String else INHERITED
    }

    private fun readSourceAndExcludeFolderFromXml() {
        for (sourceFolder in findSourceFolder()!!) {
            val url = sourceFolder.attribute("url") as String?
            val isTestSource = sourceFolder.attribute("isTestSource") as String?
            if (isTestSource != null) {
                if ("false" == isTestSource) {
                    sourceFolders.add(pathFactory.path(url))
                } else {
                    testSourceFolders.add(pathFactory.path(url))
                }
            }
            if ("true" == sourceFolder.attribute("generated")) {
                generatedSourceFolders.add(pathFactory.path(url))
            }
            val type = sourceFolder.attribute("type") as String?
            if ("java-resource" == type) {
                resourceFolders.add(pathFactory.path(url))
            } else if ("java-test-resource" == type) {
                testResourceFolders.add(pathFactory.path(url))
            }
        }
        for (excludeFolder in findExcludeFolder()!!) {
            excludeFolders.add(pathFactory.path(excludeFolder.attribute("url") as String?))
        }
    }

    private fun readInheritOutputDirsFromXml(): Boolean {
        return ("true" == this.newModuleRootManager.attribute("inherit-compiler-output")).also { this.isInheritOutputDirs = it }
    }

    private fun readOutputDirsFromXml(): Path? {
        val outputDirNode = findOutputDir()
        val testOutputDirNode = findTestOutputDir()
        val outputDirUrl = if (outputDirNode != null) outputDirNode.attribute("url") as String? else null
        val testOutputDirUrl = if (testOutputDirNode != null) testOutputDirNode.attribute("url") as String? else null
        outputDir = if (outputDirUrl != null) pathFactory.path(outputDirUrl) else null
        return (if (testOutputDirUrl != null) pathFactory.path(testOutputDirUrl) else null).also { testOutputDir = it }
    }

    private fun readDependenciesFromXml() {
        for (orderEntry in findOrderEntries()!!) {
            val orderEntryType = orderEntry.attribute("type")
            if ("module-library" == orderEntryType) {
                val classes: MutableSet<Path?> = LinkedHashSet<Path?>()
                val javadoc: MutableSet<Path?> = LinkedHashSet<Path?>()
                val sources: MutableSet<Path?> = LinkedHashSet<Path?>()
                val jarDirectories: MutableSet<JarDirectory?> = LinkedHashSet<JarDirectory?>()
                for (library in XmlPersistableConfigurationObject.Companion.getChildren(orderEntry, "library")) {
                    for (classesNode in XmlPersistableConfigurationObject.Companion.getChildren(library, "CLASSES")) {
                        readDependenciesPathsFromXml(classes, classesNode)
                    }
                    for (javadocNode in XmlPersistableConfigurationObject.Companion.getChildren(library, "JAVADOC")) {
                        readDependenciesPathsFromXml(javadoc, javadocNode)
                    }
                    for (sourcesNode in XmlPersistableConfigurationObject.Companion.getChildren(library, "SOURCES")) {
                        readDependenciesPathsFromXml(sources, sourcesNode)
                    }
                    for (jarDirNode in XmlPersistableConfigurationObject.Companion.getChildren(library, "jarDirectory")) {
                        jarDirectories.add(JarDirectory(pathFactory.path(jarDirNode.attribute("url") as String?), (jarDirNode.attribute("recursive") as String?).toBoolean()))
                    }
                }
                val moduleLibrary = ModuleLibrary(classes, javadoc, sources, jarDirectories, orderEntry.attribute("scope") as String?)
                dependencies.add(moduleLibrary)
            } else if ("module" == orderEntryType) {
                dependencies.add(ModuleDependency(orderEntry.attribute("module-name") as String?, orderEntry.attribute("scope") as String?))
            }
        }
    }

    private fun readDependenciesPathsFromXml(paths: MutableSet<Path?>, node: Node?) {
        for (classesRoot in XmlPersistableConfigurationObject.Companion.getChildren(node, "root")) {
            paths.add(pathFactory.path(classesRoot.attribute("url") as String?))
        }
    }

    private fun addJdkToXml() {
        Preconditions.checkNotNull<String?>(jdkName)
        val orderEntries = findOrderEntries()
        val moduleJdk: Node? = XmlPersistableConfigurationObject.Companion.findFirstWithAttributeValue(orderEntries, "type", "jdk")
        val moduleRootManager = this.newModuleRootManager
        if (jdkName != INHERITED) {
            val inheritedJdk: Node? = XmlPersistableConfigurationObject.Companion.findFirstWithAttributeValue(orderEntries, "type", "inheritedJdk")
            if (inheritedJdk != null) {
                inheritedJdk.parent().remove(inheritedJdk)
            }
            if (moduleJdk != null) {
                moduleRootManager.remove(moduleJdk)
            }
            val attributes: MutableMap<String?, Any?> = LinkedHashMap<String?, Any?>()
            attributes.put("type", "jdk")
            attributes.put("jdkName", jdkName)
            attributes.put("jdkType", "JavaSDK")
            moduleRootManager.appendNode("orderEntry", attributes)
        } else if (XmlPersistableConfigurationObject.Companion.findFirstWithAttributeValue(orderEntries, "type", "inheritedJdk") == null) {
            if (moduleJdk != null) {
                moduleRootManager.remove(moduleJdk)
            }
            val attributes: MutableMap<String?, Any?> = LinkedHashMap<String?, Any?>()
            attributes.put("type", "inheritedJdk")
            moduleRootManager.appendNode("orderEntry", attributes)
        }
    }

    private fun setContentURL() {
        if (contentPath != null) {
            setNodeAttribute(findOrCreateContentNode(), "url", contentPath!!.getUrl())
        }
    }

    private fun removeSourceAndExcludeFolderFromXml() {
        val content = findOrCreateContentNode()
        for (sourceFolder in findSourceFolder()!!) {
            content.remove(sourceFolder)
        }
        for (excludeFolder in findExcludeFolder()!!) {
            content.remove(excludeFolder)
        }
    }

    private fun addSourceAndExcludeFolderToXml() {
        val content = findOrCreateContentNode()
        for (path in sourceFolders) {
            val attributes: MutableMap<String?, Any?> = LinkedHashMap<String?, Any?>()
            attributes.put("url", path.getUrl())
            attributes.put("isTestSource", "false")
            if (generatedSourceFolders.contains(path)) {
                attributes.put("generated", "true")
            }
            content.appendNode("sourceFolder", attributes)
        }
        for (path in testSourceFolders) {
            val attributes: MutableMap<String?, Any?> = LinkedHashMap<String?, Any?>()
            attributes.put("url", path.getUrl())
            attributes.put("isTestSource", "true")
            if (generatedSourceFolders.contains(path)) {
                attributes.put("generated", "true")
            }
            content.appendNode("sourceFolder", attributes)
        }
        for (path in resourceFolders) {
            if (sourceFolders.contains(path)) {
                continue
            }
            val attributes: MutableMap<String?, Any?> = LinkedHashMap<String?, Any?>()
            attributes.put("url", path.getUrl())
            attributes.put("type", "java-resource")
            if (generatedSourceFolders.contains(path)) {
                attributes.put("generated", "true")
            }
            content.appendNode("sourceFolder", attributes)
        }
        for (path in testResourceFolders) {
            if (testSourceFolders.contains(path)) {
                continue
            }
            val attributes: MutableMap<String?, Any?> = LinkedHashMap<String?, Any?>()
            attributes.put("url", path.getUrl())
            attributes.put("type", "java-test-resource")
            if (generatedSourceFolders.contains(path)) {
                attributes.put("generated", "true")
            }
            content.appendNode("sourceFolder", attributes)
        }
        for (path in excludeFolders) {
            val attributes: MutableMap<String?, Any?> = LinkedHashMap<String?, Any?>()
            attributes.put("url", path.getUrl())
            content.appendNode("excludeFolder", attributes)
        }
    }

    private fun writeInheritOutputDirsToXml() {
        setNodeAttribute(this.newModuleRootManager, "inherit-compiler-output", this.isInheritOutputDirs)
    }

    private fun writeSourceLanguageLevel() {
        if (languageLevel != null) {
            setNodeAttribute(this.newModuleRootManager, "LANGUAGE_LEVEL", languageLevel)
        }
    }

    private fun addOutputDirsToXml() {
        if (outputDir != null) {
            Companion.setNodeAttribute(findOrCreateOutputDir()!!, "url", outputDir!!.getUrl())
        }
        if (testOutputDir != null) {
            Companion.setNodeAttribute(findOrCreateTestOutputDir()!!, "url", testOutputDir!!.getUrl())
        }
    }

    private fun removeDependenciesFromXml() {
        val moduleRoot = this.newModuleRootManager
        for (orderEntry in findOrderEntries()!!) {
            if (isDependencyOrderEntry(orderEntry)) {
                moduleRoot.remove(orderEntry)
            }
        }
    }

    protected fun isDependencyOrderEntry(orderEntry: Any): Boolean {
        return mutableListOf<String?>("module-library", "module").contains((orderEntry as Node).attribute("type") as String?)
    }

    private fun addDependenciesToXml() {
        val moduleRoot = this.newModuleRootManager
        for (dependency in dependencies) {
            dependency.addToNode(moduleRoot)
        }
    }

    private val newModuleRootManager: Node
        get() {
            val newModuleRootManager: Node = XmlPersistableConfigurationObject.Companion.findFirstWithAttributeValue(
                XmlPersistableConfigurationObject.Companion.getChildren(
                    getXml(),
                    "component"
                ), "name", "NewModuleRootManager"
            )
            Preconditions.checkNotNull<Node?>(newModuleRootManager)
            return newModuleRootManager
        }

    private fun findOrCreateOutputDir(): Node? {
        val outputDirNode = findOutputDir()
        if (outputDirNode != null) {
            return outputDirNode
        }
        return this.newModuleRootManager.appendNode("output")
    }

    private fun findOrCreateTestOutputDir(): Node? {
        val testOutputDirNode = findTestOutputDir()
        if (testOutputDirNode != null) {
            return testOutputDirNode
        }
        return this.newModuleRootManager.appendNode("output-test")
    }

    private fun findOrCreateContentNode(): Node {
        val newModuleRootManager = this.newModuleRootManager
        val contentNode: Node? = XmlPersistableConfigurationObject.Companion.findFirstChildNamed(newModuleRootManager, CONTENT)
        if (contentNode != null) {
            return contentNode
        }
        return newModuleRootManager.appendNode(CONTENT)
    }

    private fun findSourceFolder(): MutableList<Node>? {
        return XmlPersistableConfigurationObject.Companion.getChildren(findOrCreateContentNode(), "sourceFolder")
    }

    private fun findExcludeFolder(): MutableList<Node>? {
        return XmlPersistableConfigurationObject.Companion.getChildren(findOrCreateContentNode(), "excludeFolder")
    }

    private fun findOutputDir(): Node? {
        return XmlPersistableConfigurationObject.Companion.findFirstChildNamed(this.newModuleRootManager, "output")
    }

    private fun findTestOutputDir(): Node? {
        return XmlPersistableConfigurationObject.Companion.findFirstChildNamed(this.newModuleRootManager, "output-test")
    }

    private fun findOrderEntries(): MutableList<Node>? {
        return XmlPersistableConfigurationObject.Companion.getChildren(this.newModuleRootManager, "orderEntry")
    }

    override fun toString(): String {
        return ("Module{"
                + "dependencies=" + dependencies
                + ", sourceFolders=" + sourceFolders
                + ", testSourceFolders=" + testSourceFolders
                + ", resourceFolders=" + resourceFolders
                + ", testResourceFolders=" + testResourceFolders
                + ", generatedSourceFolders=" + generatedSourceFolders
                + ", excludeFolders=" + excludeFolders
                + ", inheritOutputDirs=" + this.isInheritOutputDirs
                + ", jdkName=" + jdkName
                + ", outputDir=" + outputDir
                + ", testOutputDir=" + testOutputDir + "}")
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (javaClass != o.javaClass) {
            return false
        }
        val module = o as Module
        return Objects.equal(dependencies, module.dependencies)
                && Objects.equal(excludeFolders, module.excludeFolders)
                && Objects.equal(outputDir, module.outputDir)
                && Objects.equal(sourceFolders, module.sourceFolders)
                && Objects.equal(generatedSourceFolders, module.generatedSourceFolders)
                && Objects.equal(jdkName, module.jdkName)
                && Objects.equal(testOutputDir, module.testOutputDir)
                && Objects.equal(testSourceFolders, module.testSourceFolders)
                && Objects.equal(resourceFolders, module.resourceFolders)
                && Objects.equal(testResourceFolders, module.testResourceFolders)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(
            sourceFolders,
            generatedSourceFolders,
            testSourceFolders,
            resourceFolders,
            testResourceFolders,
            excludeFolders,
            this.isInheritOutputDirs,
            jdkName,
            outputDir,
            testOutputDir,
            dependencies
        )
    }

    companion object {
        const val INHERITED: String = "inherited"

        private const val CONTENT = "content"

        private fun setNodeAttribute(node: Node, key: String?, value: Any?) {
            val attributes = uncheckedCast<MutableMap<String?, Any?>?>(node.attributes())
            attributes!!.put(key, value)
        }
    }
}
