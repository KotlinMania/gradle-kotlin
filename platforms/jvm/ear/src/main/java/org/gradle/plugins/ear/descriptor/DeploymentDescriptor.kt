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
package org.gradle.plugins.ear.descriptor

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.XmlProvider
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import java.io.Reader
import java.io.Writer

/**
 * A deployment descriptor such as application.xml.
 */
interface DeploymentDescriptor {
    @get:ToBeReplacedByLazyProperty
    var fileName: String?

    @get:ToBeReplacedByLazyProperty
    var version: String?

    @get:ToBeReplacedByLazyProperty
    var applicationName: String?

    @get:ToBeReplacedByLazyProperty
    var initializeInOrder: Boolean?

    @get:ToBeReplacedByLazyProperty
    var description: String?

    @get:ToBeReplacedByLazyProperty
    var displayName: String?

    @get:ToBeReplacedByLazyProperty
    var libraryDirectory: String?

    @get:ToBeReplacedByLazyProperty
    var modules: MutableSet<EarModule?>?

    /**
     * Add a module to the deployment descriptor.
     *
     * @param module
     * The module to add.
     * @param type
     * The type of the module, such as "ejb", "java", etc.
     * @return this.
     */
    fun module(module: EarModule?, type: String?): DeploymentDescriptor?

    /**
     * Add a module to the deployment descriptor.
     *
     * @param path
     * The path of the module to add.
     * @param type
     * The type of the module, such as "ejb", "java", etc.
     * @return this.
     */
    fun module(path: String?, type: String?): DeploymentDescriptor?

    /**
     * Add a web module to the deployment descriptor.
     *
     * @param path
     * The path of the module to add.
     * @param contextRoot
     * The context root type of the web module.
     * @return this.
     */
    fun webModule(path: String?, contextRoot: String?): DeploymentDescriptor?

    @get:ToBeReplacedByLazyProperty
    var securityRoles: MutableSet<EarSecurityRole?>?

    /**
     * Add a security role to the deployment descriptor.
     *
     * @param role
     * The security role to add.
     * @return this.
     */
    fun securityRole(role: EarSecurityRole?): DeploymentDescriptor?

    /**
     * Add a security role to the deployment descriptor.
     *
     * @param role
     * The name of the security role to add.
     * @return this.
     */
    fun securityRole(role: String?): DeploymentDescriptor?

    /**
     * Add a security role to the deployment descriptor after configuring it with the given action.
     *
     * @param action an action to configure the security role
     * @return this.
     */
    fun securityRole(action: Action<in EarSecurityRole?>?): DeploymentDescriptor?

    @get:ToBeReplacedByLazyProperty
    var moduleTypeMappings: MutableMap<String?, String?>?

    /**
     * Adds a closure to be called when the XML document has been created. The XML is passed to the closure as a
     * parameter in form of a [groovy.util.Node]. The closure can modify the XML before it is written to the
     * output file. This allows additional JavaEE version 6 elements like "data-source" or "resource-ref" to be
     * included.
     *
     * @param closure
     * The closure to execute when the XML has been created
     * @return this
     */
    fun withXml(@DelegatesTo(XmlProvider::class) closure: Closure<*>?): DeploymentDescriptor?

    /**
     * Adds an action to be called when the XML document has been created. The XML is passed to the action as a
     * parameter in form of a [groovy.util.Node]. The action can modify the XML before it is written to the output
     * file. This allows additional JavaEE version 6 elements like "data-source" or "resource-ref" to be included.
     *
     * @param action
     * The action to execute when the XML has been created
     * @return this
     */
    fun withXml(action: Action<in XmlProvider?>?): DeploymentDescriptor?

    /**
     * Reads the deployment descriptor from a reader.
     *
     * @param reader
     * The reader to read the deployment descriptor from
     * @return this
     */
    fun readFrom(reader: Reader?): DeploymentDescriptor?

    /**
     * Reads the deployment descriptor from a file. The paths are resolved as defined by
     * [org.gradle.api.Project.file]
     *
     * @param path
     * The path of the file to read the deployment descriptor from
     * @return whether the descriptor could be read from the given path
     */
    fun readFrom(path: Any?): Boolean

    /**
     * Writes the deployment descriptor into a writer.
     *
     * @param writer
     * The writer to write the deployment descriptor to
     * @return this
     */
    fun writeTo(writer: Writer?): DeploymentDescriptor?

    /**
     * Writes the deployment descriptor into a file. The paths are resolved as defined by
     * [org.gradle.api.Project.file]
     *
     * @param path
     * The path of the file to write the deployment descriptor into.
     * @return this
     */
    fun writeTo(path: Any?): DeploymentDescriptor?
}
