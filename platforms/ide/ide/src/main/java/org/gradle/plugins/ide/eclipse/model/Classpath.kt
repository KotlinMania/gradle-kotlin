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
import groovy.util.Node
import groovy.util.NodeList
import org.gradle.internal.xml.XmlTransformer
import org.gradle.plugins.ide.eclipse.internal.EclipsePluginConstants
import org.gradle.plugins.ide.eclipse.model.internal.FileReferenceFactory
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject
import java.io.File
import java.util.function.Supplier
import java.util.stream.Collectors

/**
 * Represents the customizable elements of an eclipse classpath file. (via XML hooks everything is customizable).
 */
class Classpath(xmlTransformer: XmlTransformer?, private val fileReferenceFactory: FileReferenceFactory) : XmlPersistableConfigurationObject(xmlTransformer) {
    var entries: MutableList<ClasspathEntry?> = ArrayList<ClasspathEntry?>()

    @JvmOverloads
    constructor(fileReferenceFactory: FileReferenceFactory = FileReferenceFactory()) : this(XmlTransformer(), fileReferenceFactory)

    override fun getDefaultResourceName(): String {
        return "defaultClasspath.xml"
    }

    override fun load(xml: Node) {
        for (e in (xml.get("classpathentry") as groovy.util.NodeList?)!!) {
            val entryNode = e as Node
            val kind = entryNode.attribute("kind")
            if ("src" == kind) {
                val path = entryNode.attribute("path") as String
                entries.add(if (path.startsWith("/")) ProjectDependency(entryNode) else SourceFolder(entryNode))
            } else if ("var" == kind) {
                entries.add(Variable(entryNode, fileReferenceFactory))
            } else if ("con" == kind) {
                entries.add(Container(entryNode))
            } else if ("lib" == kind) {
                entries.add(Library(entryNode, fileReferenceFactory))
            } else if ("output" == kind) {
                entries.add(Output(entryNode))
            }
        }
    }

    // TODO: Change this signature once we can break compatibility
    fun configure(newEntries: MutableList<*>): Any {
        val newSourceFolders = newEntries.stream()
            .filter { obj: Any? -> SourceFolder::class.java.isInstance(obj) }
            .map<SourceFolder?> { obj: Any? -> SourceFolder::class.java.cast(obj) }
            .collect(Collectors.toList())

        val updatedEntries: MutableSet<ClasspathEntry?> = entries.stream()
            .filter { entry: ClasspathEntry? -> shouldKeepEntry(newSourceFolders, entry) }
            .collect(Collectors.toCollection(Supplier { LinkedHashSet() }))

        updatedEntries.addAll(newEntries as MutableList<ClasspathEntry?>) //merge new and old entries with matching path entries
        return Lists.newArrayList<ClasspathEntry?>(updatedEntries).also { entries = it }
    }

    private fun shouldKeepEntry(newEntries: MutableList<SourceFolder?>, entry: ClasspathEntry?): Boolean {
        return !isDependency(entry) && !isJreContainer(entry) && !isOutputLocation(entry) && !isExistingEntryDuplicate(newEntries, entry)
    }

    override fun store(xml: Node) {
        val classpathEntryNodes = xml.get("classpathentry") as NodeList
        for (classpathEntry in classpathEntryNodes) {
            xml.remove(classpathEntry as Node?)
        }
        for (entry in filterDuplicateProjectDependencies(entries)) {
            entry.appendNode(xml)
        }
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val classpath = o as Classpath
        return Objects.equal(entries, classpath.entries)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(entries)
    }

    override fun toString(): String {
        return "Classpath{entries=" + entries + "}"
    }

    private fun isDependency(entry: ClasspathEntry?): Boolean {
        return entry is ProjectDependency || entry is AbstractLibrary
    }

    private fun isJreContainer(entry: ClasspathEntry?): Boolean {
        return entry is Container && entry.getPath().startsWith("org.eclipse.jdt.launching.JRE_CONTAINER")
    }

    private fun isOutputLocation(entry: ClasspathEntry?): Boolean {
        return entry is Output
    }

    /**
     * Creates a new [FileReference] instance.
     *
     *
     * The created object can be used to configure custom source or javadoc location on [Library] and on [Variable] objects.
     *
     *
     * This method can receive either String or File instances.
     *
     * @param reference The object to transform into a new file reference. Can be instance of File or String.
     * @return The new file reference.
     * @see AbstractLibrary.setJavadocPath
     * @see AbstractLibrary.setSourcePath
     */
    fun fileReference(reference: Any?): FileReference? {
        if (reference is File) {
            return fileReferenceFactory.fromFile(reference)
        } else if (reference is String) {
            return fileReferenceFactory.fromVariablePath(reference)
        } else {
            val type = if (reference == null) "null" else reference.javaClass.getName()
            throw RuntimeException("File reference can only be created from File or String instances but " + type + " was passed")
        }
    }

    companion object {
        private fun isExistingEntryDuplicate(newSourceFolders: MutableList<SourceFolder?>, existingEntry: ClasspathEntry?): Boolean {
            if (existingEntry !is SourceFolder) {
                return false
            }

            val sourceFolder = existingEntry
            return newSourceFolders.stream().anyMatch { newSourceFolder: SourceFolder? ->
                Objects.equal(sourceFolder.getKind(), newSourceFolder!!.getKind())
                        && Objects.equal(sourceFolder.getPath(), newSourceFolder.getPath())
                        && Objects.equal(sourceFolder.getExcludes(), newSourceFolder.getExcludes())
                        && Objects.equal(sourceFolder.getIncludes(), newSourceFolder.getIncludes())
            }
        }

        /*
     * Gradle 5.6 introduced closed project substitution for Buildship: https://github.com/gradle/gradle/pull/9405
     * The feature is built upon the EclipseProject TAPI model which is based on the result of the Eclipse plugin.
     *
     * To distinguish between different task dependencies the closed project substitution feature had to change
     * the equals/hashCode implementation of ProjectDependency which lead to duplicate project dependencies
     * in the .classpath file when the 'eclipse' task is invoked (which - btw - the Buildship plugin does not do).
     *
     * What we do here is a quick workaround to remove the duplication from the generated .classpath files. The
     * proper solution has a much larger scope: we'd need to decouple the EclipseProject TAPI model generation
     * from the files generated by the 'eclipse' Gradle plugin.
     */
        private fun filterDuplicateProjectDependencies(entries: MutableList<ClasspathEntry?>): MutableList<ClasspathEntry> {
            val mainSourcePaths: MutableSet<String?> = HashSet<String?>()
            for (entry in entries) {
                if (entry is ProjectDependency) {
                    val projectDependency = entry
                    if (!hasTestSourcesAttribute(projectDependency)) {
                        mainSourcePaths.add(projectDependency.getPath())
                    }
                }
            }

            val filtered: MutableList<ClasspathEntry> = ArrayList<ClasspathEntry>(entries.size)
            val paths: MutableSet<String?> = HashSet<String?>()
            for (entry in entries) {
                if (entry is ProjectDependency) {
                    val projectDependency = entry
                    val path = projectDependency.getPath()
                    // skip duplicate paths and also test source paths with a corresponding main source path
                    if (!paths.contains(path) && (!mainSourcePaths.contains(path) || !hasTestSourcesAttribute(projectDependency))) {
                        paths.add(path)
                        filtered.add(entry)
                    }
                } else {
                    filtered.add(entry!!)
                }
            }
            return filtered
        }

        private fun hasTestSourcesAttribute(projectDependency: ProjectDependency): Boolean {
            val value = projectDependency.getEntryAttributes().get(EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_KEY)
            return EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_VALUE == value
        }
    }
}
