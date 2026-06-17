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
package org.gradle.plugins.ear.descriptor.internal

import groovy.lang.Closure
import groovy.namespace.QName
import groovy.util.Node
import groovy.xml.XmlParser
import org.gradle.api.Action
import org.gradle.api.XmlProvider
import org.gradle.api.internal.DomNode
import org.gradle.api.model.ObjectFactory
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.IoActions
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.file.PathToFileResolver
import org.gradle.internal.xml.XmlTransformer
import org.gradle.plugins.ear.descriptor.DeploymentDescriptor
import org.gradle.plugins.ear.descriptor.EarModule
import org.gradle.plugins.ear.descriptor.EarSecurityRole
import org.gradle.plugins.ear.descriptor.EarWebModule
import org.xml.sax.SAXException
import org.xml.sax.SAXNotRecognizedException
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.io.Reader
import java.io.Writer
import java.lang.Boolean
import java.util.regex.Pattern
import javax.inject.Inject
import kotlin.Any
import kotlin.Exception
import kotlin.String
import kotlin.checkNotNull

class DefaultDeploymentDescriptor @Inject constructor(private val fileResolver: PathToFileResolver?, private val objectFactory: ObjectFactory) : DeploymentDescriptor {
    // For tests
    val transformer: XmlTransformer = XmlTransformer()

    private var fileName: String? = "application.xml"
    private var version: String? = "6"
    private var applicationName: String? = null
    private var initializeInOrder: Boolean? = Boolean.FALSE
    private var description: String? = null
    private var displayName: String? = null
    private var libraryDirectory: String? = null
    private var modules: MutableSet<EarModule> = LinkedHashSet<EarModule>()
    private var securityRoles: MutableSet<EarSecurityRole>? = LinkedHashSet<EarSecurityRole>()
    private var moduleTypeMappings: MutableMap<String?, String?> = LinkedHashMap<String?, String?>()

    override fun getFileName(): String? {
        return fileName
    }

    override fun setFileName(fileName: String) {
        this.fileName = fileName
        readFrom(File("META-INF", fileName))
    }

    override fun getVersion(): String? {
        return version
    }

    override fun setVersion(version: String?) {
        this.version = version
    }

    override fun getApplicationName(): String? {
        return applicationName
    }

    override fun setApplicationName(applicationName: String?) {
        this.applicationName = applicationName
    }

    override fun getInitializeInOrder(): kotlin.Boolean? {
        return initializeInOrder
    }

    override fun setInitializeInOrder(initializeInOrder: kotlin.Boolean?) {
        this.initializeInOrder = initializeInOrder
    }

    override fun getDescription(): String? {
        return description
    }

    override fun setDescription(description: String?) {
        this.description = description
    }

    override fun getDisplayName(): String? {
        return displayName
    }

    override fun setDisplayName(displayName: String?) {
        this.displayName = displayName
    }

    override fun getLibraryDirectory(): String? {
        return libraryDirectory
    }

    override fun setLibraryDirectory(libraryDirectory: String?) {
        this.libraryDirectory = libraryDirectory
    }

    override fun getModules(): MutableSet<EarModule> {
        return modules
    }

    override fun setModules(modules: MutableSet<EarModule>) {
        this.modules = modules
    }

    override fun getSecurityRoles(): MutableSet<EarSecurityRole>? {
        return securityRoles
    }

    override fun setSecurityRoles(securityRoles: MutableSet<EarSecurityRole>?) {
        this.securityRoles = securityRoles
    }

    override fun getModuleTypeMappings(): MutableMap<String?, String?> {
        return moduleTypeMappings
    }

    override fun setModuleTypeMappings(moduleTypeMappings: MutableMap<String?, String?>) {
        this.moduleTypeMappings = moduleTypeMappings
    }

    override fun module(module: EarModule, type: String?): DefaultDeploymentDescriptor {
        modules.add(module)
        moduleTypeMappings.put(module.getPath(), type)
        return this
    }

    override fun module(path: String?, type: String?): DefaultDeploymentDescriptor {
        return module(DefaultEarModule(path), type)
    }

    override fun webModule(path: String?, contextRoot: String?): DefaultDeploymentDescriptor {
        modules.add(DefaultEarWebModule(path, contextRoot))
        moduleTypeMappings.put(path, "web")
        return this
    }

    override fun securityRole(role: EarSecurityRole?): DefaultDeploymentDescriptor {
        securityRoles!!.add(role!!)
        return this
    }

    override fun securityRole(role: String?): DeploymentDescriptor {
        securityRoles!!.add(DefaultEarSecurityRole(role))
        return this
    }

    override fun securityRole(action: Action<in EarSecurityRole?>): DeploymentDescriptor {
        val role: EarSecurityRole = objectFactory.newInstance<DefaultEarSecurityRole>(DefaultEarSecurityRole::class.java)
        action.execute(role)
        securityRoles!!.add(role)
        return this
    }

    override fun withXml(closure: Closure<*>?): DeploymentDescriptor {
        transformer.addAction(closure)
        return this
    }

    override fun withXml(action: Action<in XmlProvider?>?): DeploymentDescriptor {
        transformer.addAction(action)
        return this
    }

    //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    override fun readFrom(path: Any?): kotlin.Boolean {
        if (fileResolver == null) {
            return false
        }
        val descriptorFile = fileResolver.resolve(path)
        if (descriptorFile == null || !descriptorFile.exists()) {
            return false
        }
        try {
            val reader = FileReader(descriptorFile)
            readFrom(reader)
            return true
        } catch (e: IOException) {
            throw throwAsUncheckedException(e)
        }
    }

    override fun readFrom(reader: Reader?): DeploymentDescriptor {
        try {
            val appNode: Node = createParser().parse(reader)
            version = appNode.attribute("version") as String?
            for (child in uncheckedCast<MutableList<Node>?>(appNode.children())!!) {
                val childLocalName: String = localNameOf(child)
                when (childLocalName) {
                    "application-name" -> applicationName = child.text()

                    "initialize-in-order" -> initializeInOrder = child.text().toBoolean()

                    "description" -> description = child.text()

                    "display-name" -> displayName = child.text()

                    "library-directory" -> libraryDirectory = child.text()

                    "module" -> {
                        val module: EarModule? = null
                        for (moduleNode in uncheckedCast<MutableList<Node>?>(child.children())!!) {
                            val moduleNodeLocalName: String = localNameOf(moduleNode)
                            if (moduleNodeLocalName == "web") {
                                val webUri: String? = childNodeText(moduleNode, "web-uri")
                                val contextRoot: String? = childNodeText(moduleNode, "context-root")
                                module = DefaultEarWebModule(webUri, contextRoot)
                                modules.add(module)
                                moduleTypeMappings.put(module.getPath(), "web")
                            } else if (moduleNodeLocalName == "alt-dd") {
                                checkNotNull(module)
                                module.setAltDeployDescriptor(moduleNode.text())
                            } else {
                                module = DefaultEarModule(moduleNode.text())
                                modules.add(module)
                                moduleTypeMappings.put(module.getPath(), moduleNodeLocalName)
                            }
                        }
                    }

                    "security-role" -> {
                        val roleName: String? = childNodeText(child, "role-name")
                        val description: String? = childNodeText(child, "description")
                        securityRoles!!.add(DefaultEarSecurityRole(roleName, description))
                    }

                    else -> withXml(object : Action<XmlProvider?> {
                        override fun execute(xmlProvider: XmlProvider) {
                            xmlProvider.asNode().append(child)
                        }
                    })
                }
            }
        } catch (ex: IOException) {
            throw throwAsUncheckedException(ex)
        } catch (ex: SAXException) {
            throw throwAsUncheckedException(ex)
        } finally {
            IoActions.closeQuietly(reader)
        }
        return this
    }

    override fun writeTo(path: Any?): DefaultDeploymentDescriptor {
        transformer.transform(toXmlNode(), fileResolver!!.resolve(path))
        return this
    }

    override fun writeTo(writer: Writer?): DefaultDeploymentDescriptor {
        transformer.transform(toXmlNode(), writer)
        return this
    }

    private fun toXmlNode(): DomNode {
        val root = DomNode(nodeNameFor("application")!!)
        val rootAttributes = uncheckedCast<MutableMap<String?, String?>?>(root.attributes())
        if (version != null) {
            rootAttributes!!.put("version", version)
        }
        if ("1.3" != version) {
            rootAttributes!!.put("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance")
        }
        if ("1.3" == version) {
            root.setPublicId("-//Sun Microsystems, Inc.//DTD J2EE Application 1.3//EN")
            root.setSystemId("http://java.sun.com/dtd/application_1_3.dtd")
        } else if ("1.4" == version) {
            rootAttributes!!.put("xsi:schemaLocation", "http://java.sun.com/xml/ns/j2ee http://java.sun.com/xml/ns/j2ee/application_1_4.xsd")
        } else if ("5" == version || "6" == version) {
            rootAttributes!!.put("xsi:schemaLocation", "http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/application_" + version + ".xsd")
        } else if ("7" == version || "8" == version) {
            rootAttributes!!.put("xsi:schemaLocation", "http://xmlns.jcp.org/xml/ns/javaee http://xmlns.jcp.org/xml/ns/javaee/application_" + version + ".xsd")
        } else if (version != null && JAKARTA_VERSION_PATTERN.matcher(version).matches()) {
            rootAttributes!!.put("xsi:schemaLocation", "https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/application_" + version + ".xsd")
        }
        if (applicationName != null) {
            Node(root, nodeNameFor("application-name"), applicationName)
        }
        if (description != null) {
            Node(root, nodeNameFor("description"), description)
        }
        if (displayName != null) {
            Node(root, nodeNameFor("display-name"), displayName)
        }
        if (initializeInOrder != null && initializeInOrder) {
            Node(root, nodeNameFor("initialize-in-order"), initializeInOrder)
        }
        for (module in modules) {
            val moduleNode = Node(root, nodeNameFor("module"))
            module.toXmlNode(moduleNode, moduleNameFor(module))
        }
        if (securityRoles != null) {
            for (role in securityRoles) {
                val roleNode = Node(root, nodeNameFor("security-role"))
                if (role.getDescription() != null) {
                    Node(roleNode, nodeNameFor("description"), role.getDescription())
                }
                Node(roleNode, nodeNameFor("role-name"), role.getRoleName())
            }
        }
        if (libraryDirectory != null) {
            Node(root, nodeNameFor("library-directory"), libraryDirectory)
        }
        return root
    }

    private fun moduleNameFor(module: EarModule): Any? {
        var name = moduleTypeMappings.get(module.getPath())
        if (name == null) {
            if (module is EarWebModule) {
                name = "web"
            } else {
                // assume EJB is the most common kind of EAR deployment
                name = "ejb"
            }
        }
        return nodeNameFor(name)
    }

    private fun nodeNameFor(name: String): Any? {
        if ("1.3" == version) {
            return name
        } else if ("1.4" == version) {
            return QName("http://java.sun.com/xml/ns/j2ee", name)
        } else if ("5" == version || "6" == version) {
            return QName("http://java.sun.com/xml/ns/javaee", name)
        } else if ("7" == version || "8" == version) {
            return QName("http://xmlns.jcp.org/xml/ns/javaee", name)
        } else if (version != null && JAKARTA_VERSION_PATTERN.matcher(version).matches()) {
            return QName("https://jakarta.ee/xml/ns/jakartaee", name)
        } else {
            return QName(name)
        }
    }

    companion object {
        private const val ACCESS_EXTERNAL_DTD = "http://javax.xml.XMLConstants/property/accessExternalDTD"
        private const val ALLOW_ANY_EXTERNAL_DTD = "all"

        // Pattern to match plausible Jakarta EE Versions "9", "10", "11" ... "99"
        private val JAKARTA_VERSION_PATTERN: Pattern = Pattern.compile("9|[1-9][0-9]")

        private fun createParser(): XmlParser {
            try {
                val parser = XmlParser(false, true, true)
                try {
                    // If not set for >= JAXP 1.5 / Java8 won't allow referencing DTDs, e.g.
                    // using http URLs, because Groovy's XmlParser requests FEATURE_SECURE_PROCESSING
                    parser.setProperty(ACCESS_EXTERNAL_DTD, ALLOW_ANY_EXTERNAL_DTD)
                } catch (ignore: SAXNotRecognizedException) {
                    // property requires >= JAXP 1.5 / Java8
                }
                return parser
            } catch (ex: Exception) {
                throw throwAsUncheckedException(ex)
            }
        }

        private fun childNodeText(root: Node, name: String?): String? {
            for (child in uncheckedCast<MutableList<Node>?>(root.children())!!) {
                if (localNameOf(child) == name) {
                    return child.text()
                }
            }
            return null
        }

        private fun localNameOf(node: Node): String {
            return if (node.name() is QName) (node.name() as QName).getLocalPart() else node.name().toString()
        }
    }
}
