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

import groovy.lang.Closure
import groovy.util.Node
import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.XmlProvider
import java.io.File
import java.io.InputStream
import java.io.OutputStream

internal class NonRenamableProject(private val delegate: Project) : Project(null) {
    override fun setName(name: String?) {
        throw InvalidUserDataException("Configuring eclipse project name in 'beforeMerged' or 'whenMerged' hook is not allowed.")
    }

    override fun getDefaultResourceName(): String? {
        return delegate.getDefaultResourceName()
    }

    override fun getName(): String? {
        return delegate.getName()
    }

    override fun getComment(): String? {
        return delegate.getComment()
    }

    override fun setComment(comment: String?) {
        delegate.setComment(comment)
    }

    override fun getReferencedProjects(): MutableSet<String?>? {
        return delegate.getReferencedProjects()
    }

    override fun setReferencedProjects(referencedProjects: MutableSet<String?>?) {
        delegate.setReferencedProjects(referencedProjects)
    }

    override fun getNatures(): MutableList<String?>? {
        return delegate.getNatures()
    }

    override fun setNatures(natures: MutableList<String?>?) {
        delegate.setNatures(natures)
    }

    override fun getBuildCommands(): MutableList<BuildCommand?>? {
        return delegate.getBuildCommands()
    }

    override fun setBuildCommands(buildCommands: MutableList<BuildCommand?>?) {
        delegate.setBuildCommands(buildCommands)
    }

    override fun getLinkedResources(): MutableSet<Link?>? {
        return delegate.getLinkedResources()
    }

    override fun setLinkedResources(linkedResources: MutableSet<Link?>?) {
        delegate.setLinkedResources(linkedResources)
    }

    override fun configure(eclipseProject: EclipseProject): Any? {
        return delegate.configure(eclipseProject)
    }

    override fun load(xml: Node?) {
        delegate.load(xml)
    }

    override fun store(xml: Node?) {
        delegate.store(xml)
    }

    @Throws(Exception::class)
    override fun load(inputStream: InputStream?) {
        delegate.load(inputStream)
    }

    override fun store(outputStream: OutputStream?) {
        delegate.store(outputStream)
    }

    override fun getXml(): Node? {
        return delegate.getXml()
    }

    override fun transformAction(action: Closure<*>?) {
        delegate.transformAction(action)
    }

    override fun transformAction(action: Action<in XmlProvider?>?) {
        delegate.transformAction(action)
    }

    override fun load(inputFile: File?) {
        delegate.load(inputFile)
    }

    override fun loadDefaults() {
        delegate.loadDefaults()
    }

    override fun store(outputFile: File?) {
        delegate.store(outputFile)
    }
}
