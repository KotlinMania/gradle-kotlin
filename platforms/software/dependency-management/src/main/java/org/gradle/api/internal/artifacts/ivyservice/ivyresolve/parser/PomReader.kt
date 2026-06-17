/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser

import com.google.common.collect.ImmutableList
import org.apache.commons.io.IOUtils
import org.apache.ivy.core.IvyPatternHelper
import org.apache.ivy.plugins.parser.m2.PomReader
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.data.MavenDependencyKey
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.data.PomDependencyMgt
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.data.PomProfile
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.classloader.ClassLoaderUtils
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.local.LocallyAvailableExternalResource
import org.gradle.internal.xml.XmlFactories
import org.w3c.dom.Comment
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

/**
 * Copied from org.apache.ivy.plugins.parser.m2.PomReader.
 */
class PomReader @JvmOverloads constructor(
    resource: LocallyAvailableExternalResource,
    private val moduleIdentifierFactory: ImmutableModuleIdentifierFactory,
    childPomProperties: MutableMap<String?, String?> = mutableMapOf<String?, String?>()
) : PomParent {
    private var pomParent: PomParent = RootPomParent()

    /**
     * @return properties of both current and children poms.
     */
    val allPomProperties: MutableMap<String?, String?> = HashMap<String?, String?>()
    private val effectiveProperties: MutableMap<String?, String?> = HashMap<String?, String?>()
    private var declaredDependencyMgts: MutableList<PomDependencyMgt>? = null
    private var declaredActivePomProfiles: MutableList<PomProfile>? = null
    private var resolvedDependencyMgts: MutableMap<MavenDependencyKey?, PomDependencyMgt?>? = null
    private val importedDependencyMgts: MutableMap<MavenDependencyKey?, PomDependencyMgt?> = LinkedHashMap<MavenDependencyKey?, PomDependencyMgt?>()
    private var resolvedDependencies: MutableMap<MavenDependencyKey?, PomDependencyData?>? = null

    private val projectElement: Element
    private val parentElement: Element?

    fun setPomParent(pomParent: PomParent) {
        this.pomParent = pomParent
        for (entry in pomParent.getProperties().entries) {
            maybeSetEffectiveProperty(entry.key, entry.value)
        }
    }

    private fun setDefaultParentGavProperties() {
        maybeSetGavProperties(GavProperty.PARENT_GROUP_ID, this.parentGroupId)
        maybeSetGavProperties(GavProperty.PARENT_VERSION, this.parentVersion)
        maybeSetGavProperties(GavProperty.PARENT_ARTIFACT_ID, this.parentArtifactId)
    }

    private fun maybeSetGavProperties(gavProperty: GavProperty, propertyValue: String?) {
        for (name in gavProperty.names) {
            maybeSetEffectiveProperty(name, propertyValue)
        }
    }

    private fun setPomProperties(pomProperties: MutableMap<String?, String?>) {
        if (!pomProperties.isEmpty()) {
            this.allPomProperties.putAll(pomProperties)
            for (pomProperty in pomProperties.entries) {
                maybeSetEffectiveProperty(pomProperty.key, pomProperty.value)
            }
        }
    }

    /**
     * Sets properties for all active profiles. Properties from an active profile override existing POM properties.
     */
    private fun setActiveProfileProperties() {
        for (activePomProfile in parseActivePomProfiles()!!) {
            for (property in activePomProfile.properties.entries) {
                effectiveProperties.put(property.key, property.value)
            }
        }
    }

    /**
     * Add a property if not yet set and value is not null.
     * This guarantee that property keep the first value that is put on it and that the properties
     * are never null.
     */
    private fun maybeSetEffectiveProperty(prop: String?, `val`: String?) {
        if (!effectiveProperties.containsKey(prop) && `val` != null) {
            effectiveProperties.put(prop, `val`)
        }
    }

    private enum class GavProperty(vararg names: String?) {
        PARENT_GROUP_ID("parent.groupId", "project.parent.groupId"),
        PARENT_ARTIFACT_ID("parent.artifactId", "project.parent.artifactId"),
        PARENT_VERSION("parent.version", "project.parent.version"),
        GROUP_ID("project.groupId", "pom.groupId", "groupId"),
        ARTIFACT_ID("project.artifactId", "pom.artifactId", "artifactId"),
        VERSION("project.version", "pom.version", "version");

        val names: ImmutableList<String?>

        init {
            this.names = ImmutableList.copyOf<String?>(names)
        }
    }

    override fun toString(): String {
        return projectElement.getOwnerDocument().getDocumentURI()
    }

    fun hasParent(): Boolean {
        return parentElement != null
    }

    override fun getProperties(): MutableMap<String?, String?> {
        return effectiveProperties
    }

    fun addImportedDependencyMgts(inherited: MutableMap<MavenDependencyKey?, PomDependencyMgt?>) {
        check(resolvedDependencyMgts == null) { "Cannot add imported dependency management elements after dependency management elements have been resolved for this POM." }
        importedDependencyMgts.putAll(inherited)
    }

    private fun checkNotNull(value: String?, name: String?, element: String? = null) {
        if (value == null) {
            val attributeName = if (element == null) name else element + " " + name
            throw RuntimeException("Missing required attribute: " + attributeName)
        }
    }

    val groupId: String
        get() {
            var groupId = PomDomParser.getFirstChildText(
                projectElement,
                GROUP_ID
            )
            if (groupId == null) {
                groupId = PomDomParser.getFirstChildText(
                    parentElement,
                    GROUP_ID
                )
            }
            checkNotNull(groupId, GROUP_ID)
            return replaceProps(groupId)
        }

    val parentGroupId: String
        get() {
            var groupId = PomDomParser.getFirstChildText(
                parentElement,
                GROUP_ID
            )
            if (groupId == null) {
                groupId = PomDomParser.getFirstChildText(
                    projectElement,
                    GROUP_ID
                )
            }
            checkNotNull(groupId, GROUP_ID)
            return replaceProps(groupId)
        }

    val artifactId: String
        get() {
            var `val` = PomDomParser.getFirstChildText(
                projectElement,
                ARTIFACT_ID
            )
            if (`val` == null) {
                `val` = PomDomParser.getFirstChildText(
                    parentElement,
                    ARTIFACT_ID
                )
            }
            checkNotNull(`val`, ARTIFACT_ID)
            return replaceProps(`val`)
        }

    val parentArtifactId: String
        get() {
            var `val` = PomDomParser.getFirstChildText(
                parentElement,
                ARTIFACT_ID
            )
            if (`val` == null) {
                `val` = PomDomParser.getFirstChildText(
                    projectElement,
                    ARTIFACT_ID
                )
            }
            checkNotNull(`val`, ARTIFACT_ID)
            return replaceProps(`val`)
        }

    val version: String?
        get() {
            var `val` = PomDomParser.getFirstChildText(
                projectElement,
                VERSION
            )
            if (`val` == null) {
                `val` = PomDomParser.getFirstChildText(
                    parentElement,
                    VERSION
                )
            }
            return replaceProps(`val`)
        }

    val parentVersion: String?
        get() {
            var `val` = PomDomParser.getFirstChildText(
                parentElement,
                VERSION
            )
            if (`val` == null) {
                `val` = PomDomParser.getFirstChildText(
                    projectElement,
                    VERSION
                )
            }
            return replaceProps(`val`)
        }

    val packaging: String
        get() {
            var `val` = PomDomParser.getFirstChildText(
                projectElement,
                PACKAGING
            )
            if (`val` == null) {
                `val` = "jar"
            }
            return replaceProps(`val`)
        }

    fun hasGradleMetadataMarker(): Boolean {
        val childNodes = projectElement.getChildNodes()
        for (i in 0..<childNodes.getLength()) {
            val node = childNodes.item(i)
            if (node is Comment) {
                val comment = node.getNodeValue()
                if (comment.contains(MetaDataParser.Companion.GRADLE_6_METADATA_MARKER) || comment.contains(MetaDataParser.Companion.GRADLE_METADATA_MARKER)) {
                    return true
                }
            }
        }
        return false
    }

    val relocation: ModuleVersionIdentifier?
        get() {
            val distrMgt = PomDomParser.getFirstChildElement(
                projectElement,
                DISTRIBUTION_MGT
            )
            val relocation = PomDomParser.getFirstChildElement(
                distrMgt,
                RELOCATION
            )
            if (relocation == null) {
                return null
            } else {
                var relocGroupId = PomDomParser.getFirstChildText(
                    relocation,
                    GROUP_ID
                )
                var relocArtId = PomDomParser.getFirstChildText(
                    relocation,
                    ARTIFACT_ID
                )
                var relocVersion = PomDomParser.getFirstChildText(
                    relocation,
                    VERSION
                )
                relocGroupId = if (relocGroupId == null) this.groupId else replaceProps(relocGroupId)
                relocArtId = if (relocArtId == null) this.artifactId else replaceProps(relocArtId)
                relocVersion = if (relocVersion == null) this.version else replaceProps(relocVersion)
                return DefaultModuleVersionIdentifier.newId(relocGroupId, relocArtId, relocVersion!!)
            }
        }

    /**
     * Returns all dependencies for this POM, including those inherited from parent POMs.
     */
    override fun getDependencies(): MutableMap<MavenDependencyKey?, PomDependencyData?> {
        if (resolvedDependencies == null) {
            resolvedDependencies = resolveDependencies()
        }
        return resolvedDependencies!!
    }

    private fun resolveDependencies(): MutableMap<MavenDependencyKey?, PomDependencyData?> {
        val dependencies: MutableMap<MavenDependencyKey?, PomDependencyData?> = LinkedHashMap<MavenDependencyKey?, PomDependencyData?>()

        for (dependency in getDependencyData(projectElement)) {
            dependencies.put(dependency.getId(), dependency)
        }

        // Maven adds inherited dependencies last
        for (entry in pomParent.getDependencies().entries) {
            if (!dependencies.containsKey(entry.key)) {
                dependencies.put(entry.key, entry.value)
            }
        }

        for (pomProfile in parseActivePomProfiles()!!) {
            for (dependency in pomProfile.dependencies) {
                dependencies.put(dependency.getId(), dependency)
            }
        }

        return dependencies
    }

    private fun getDependencyData(parentElement: Element?): MutableList<PomDependencyData> {
        val depElements: MutableList<PomDependencyData> = ArrayList<PomDependencyData>()
        val dependenciesElement = PomDomParser.getFirstChildElement(parentElement, DEPENDENCIES)
        if (dependenciesElement != null) {
            val childs = dependenciesElement.getChildNodes()
            for (i in 0..<childs.getLength()) {
                val node = childs.item(i)
                if (node is Element && DEPENDENCY == node.getNodeName()) {
                    depElements.add(PomReader.PomDependencyData(node))
                }
            }
        }

        return depElements
    }

    /**
     * Returns all dependency management elements for this POM, including those inherited from parent and imported POMs.
     */
    override fun getDependencyMgt(): MutableMap<MavenDependencyKey?, PomDependencyMgt?> {
        if (resolvedDependencyMgts == null) {
            resolvedDependencyMgts = resolveDependencyMgt()
        }
        return resolvedDependencyMgts!!
    }

    private fun resolveDependencyMgt(): MutableMap<MavenDependencyKey?, PomDependencyMgt?> {
        val dependencies: MutableMap<MavenDependencyKey?, PomDependencyMgt?> = LinkedHashMap<MavenDependencyKey?, PomDependencyMgt?>()
        dependencies.putAll(pomParent.getDependencyMgt())
        dependencies.putAll(importedDependencyMgts)
        for (dependencyMgt in parseDependencyMgt()) {
            dependencies.put(dependencyMgt.id, dependencyMgt)
        }
        return dependencies
    }

    /**
     * Parses the dependency management elements declared in this POM without removing the duplicates.
     *
     * @return Parsed dependency management elements
     */
    fun parseDependencyMgt(): MutableList<PomDependencyMgt> {
        if (declaredDependencyMgts == null) {
            val dependencyMgts = getDependencyMgt(projectElement)

            for (pomProfile in parseActivePomProfiles()!!) {
                dependencyMgts.addAll(pomProfile.dependencyMgts)
            }

            declaredDependencyMgts = dependencyMgts
        }

        return declaredDependencyMgts!!
    }

    private fun getDependencyMgt(parentElement: Element?): MutableList<PomDependencyMgt> {
        val depMgmtElements: MutableList<PomDependencyMgt> = ArrayList<PomDependencyMgt>()
        var dependenciesElement = PomDomParser.getFirstChildElement(parentElement, DEPENDENCY_MGT)
        dependenciesElement = PomDomParser.getFirstChildElement(dependenciesElement, DEPENDENCIES)

        if (dependenciesElement != null) {
            val childs = dependenciesElement.getChildNodes()
            for (i in 0..<childs.getLength()) {
                val node = childs.item(i)
                if (node is Element && DEPENDENCY == node.getNodeName()) {
                    depMgmtElements.add(PomReader.PomDependencyMgtElement(node))
                }
            }
        }

        return depMgmtElements
    }

    override fun findDependencyDefaults(dependencyKey: MavenDependencyKey?): PomDependencyMgt? {
        return getDependencyMgt().get(dependencyKey)
    }

    fun resolveGAV() {
        setGavPropertyValue(GavProperty.GROUP_ID, this.groupId)
        setGavPropertyValue(GavProperty.ARTIFACT_ID, this.artifactId)
        setGavPropertyValue(GavProperty.VERSION, this.version)
    }

    private fun setGavPropertyValue(gavProperty: GavProperty, propertyValue: String?) {
        for (name in gavProperty.names) {
            effectiveProperties.put(name, propertyValue)
        }
    }

    open inner class PomDependencyMgtElement internal constructor(private val depElement: Element?) : PomDependencyMgt {
        override fun getId(): MavenDependencyKey {
            return MavenDependencyKey(getGroupId(), getArtifactId(), getType(), getClassifier())
        }

        /**
         * @see org.apache.ivy.plugins.parser.m2.PomDependencyMgt.getGroupId
         */
        override fun getGroupId(): String {
            val `val` = PomDomParser.getFirstChildText(depElement, GROUP_ID)
            checkNotNull(`val`, GROUP_ID, DEPENDENCY)
            return replaceProps(`val`)
        }

        /**
         * @see org.apache.ivy.plugins.parser.m2.PomDependencyMgt.getArtifactId
         */
        override fun getArtifactId(): String {
            val `val` = PomDomParser.getFirstChildText(depElement, ARTIFACT_ID)
            checkNotNull(`val`, ARTIFACT_ID, DEPENDENCY)
            return replaceProps(`val`)
        }

        /**
         * @see org.apache.ivy.plugins.parser.m2.PomDependencyMgt.getVersion
         */
        override fun getVersion(): String {
            val `val` = PomDomParser.getFirstChildText(depElement, VERSION)
            return replaceProps(`val`)
        }

        override fun getScope(): String {
            val `val` = PomDomParser.getFirstChildText(depElement, SCOPE)
            return replaceProps(`val`)
        }

        override fun getType(): String {
            var `val` = PomDomParser.getFirstChildText(depElement, TYPE)
            `val` = replaceProps(`val`)

            if (`val` == null) {
                `val` = "jar"
            }

            return `val`
        }

        override fun getClassifier(): String {
            val `val` = PomDomParser.getFirstChildText(depElement, CLASSIFIER)
            return replaceProps(`val`)
        }

        override fun getExcludedModules(): MutableList<ModuleIdentifier?> {
            val exclusionsElement = PomDomParser.getFirstChildElement(depElement, EXCLUSIONS)
            if (exclusionsElement != null) {
                val childs = exclusionsElement.getChildNodes()
                val exclusions: MutableList<ModuleIdentifier?> = ArrayList<ModuleIdentifier?>()
                for (i in 0..<childs.getLength()) {
                    val node = childs.item(i)
                    if (node is Element && EXCLUSION == node.getNodeName()) {
                        val groupId = PomDomParser.getFirstChildText(node, GROUP_ID)
                        val artifactId = PomDomParser.getFirstChildText(node, ARTIFACT_ID)
                        if ((groupId != null) || (artifactId != null)) {
                            val resolvedGroupId = if (groupId != null) replaceProps(groupId) else "*"
                            val resolvedArtifactId = if (artifactId != null) replaceProps(artifactId) else "*"
                            exclusions.add(moduleIdentifierFactory.module(resolvedGroupId, resolvedArtifactId))
                        }
                    }
                }
                return exclusions
            }
            return mutableListOf<ModuleIdentifier?>()
        }
    }

    inner class PomDependencyData internal constructor(private val depElement: Element?) : PomDependencyMgtElement(
        depElement
    ) {
        val isOptional: Boolean
            get() {
                val e = PomDomParser.getFirstChildElement(
                    depElement,
                    OPTIONAL
                )
                return (e != null) && "true".equals(PomDomParser.getTextContent(e).trim { it <= ' ' }, ignoreCase = true)
            }
    }

    inner class PomProfileElement internal constructor(private val element: Element?) : PomProfile {
        private var declaredDependencyMgts: MutableList<PomDependencyMgt>? = null
        private var declaredDependencies: MutableList<PomDependencyData>? = null

        override fun getId(): String? {
            return PomDomParser.getFirstChildText(element, PROFILE_ID)
        }

        override fun getProperties(): MutableMap<String?, String?> {
            return parseProperties(element)
        }

        override fun getDependencyMgts(): MutableList<PomDependencyMgt> {
            if (declaredDependencyMgts == null) {
                declaredDependencyMgts = getDependencyMgt(element)
            }

            return declaredDependencyMgts!!
        }

        override fun getDependencies(): MutableList<PomDependencyData> {
            if (declaredDependencies == null) {
                declaredDependencies = getDependencyData(element)
            }

            return declaredDependencies!!
        }
    }

    /**
     * Parses all active profiles that can be found in POM.
     *
     * @return Active POM profiles
     */
    private fun parseActivePomProfiles(): MutableList<PomProfile>? {
        if (declaredActivePomProfiles == null) {
            val activeByDefaultPomProfiles: MutableList<PomProfile?> = ArrayList<PomProfile?>()
            val activeByAbsenceOfPropertyPomProfiles: MutableList<PomProfile?> = ArrayList<PomProfile?>()
            val profilesElement = PomDomParser.getFirstChildElement(projectElement, PROFILES)

            if (profilesElement != null) {
                for (profileElement in PomDomParser.getAllChilds(profilesElement)) {
                    if (PROFILE == profileElement.getNodeName()) {
                        val activationElement = PomDomParser.getFirstChildElement(profileElement, PROFILE_ACTIVATION)

                        if (activationElement != null) {
                            val activeByDefault = PomDomParser.getFirstChildText(activationElement, PROFILE_ACTIVATION_ACTIVE_BY_DEFAULT)

                            if ("true" == activeByDefault) {
                                activeByDefaultPomProfiles.add(PomReader.PomProfileElement(profileElement))
                            } else {
                                val propertyElement = PomDomParser.getFirstChildElement(activationElement, PROFILE_ACTIVATION_PROPERTY)

                                if (propertyElement != null) {
                                    if (isActivationPropertyActivated(propertyElement)) {
                                        activeByAbsenceOfPropertyPomProfiles.add(PomReader.PomProfileElement(profileElement))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            declaredActivePomProfiles = determineActiveProfiles(activeByDefaultPomProfiles, activeByAbsenceOfPropertyPomProfiles)
        }

        return declaredActivePomProfiles
    }

    /**
     * If a profile is identified as active through any other activation method than activeByDefault, none of the existing
     * profiles marked as activeByDefault apply.
     *
     * @param activeByDefaultPomProfiles Parsed profiles that are active by default
     * @param activeByAbsenceOfPropertyPomProfiles Parsed profiles that are activated by absence of property
     * @return List of active profiles that are not activeByDefault
     */
    private fun determineActiveProfiles(activeByDefaultPomProfiles: MutableList<PomProfile?>?, activeByAbsenceOfPropertyPomProfiles: MutableList<PomProfile?>): MutableList<PomProfile>? {
        return if (!activeByAbsenceOfPropertyPomProfiles.isEmpty()) activeByAbsenceOfPropertyPomProfiles else activeByDefaultPomProfiles
    }

    /**
     * Checks if activation property is active through absence of system property.
     *
     * @param propertyElement Property element
     * @return Activation indicator
     * @see [Maven documentation](http://books.sonatype.com/mvnref-book/reference/profiles-sect-activation.html.profiles-sect-activation-config)
     */
    private fun isActivationPropertyActivated(propertyElement: Element?): Boolean {
        val propertyName = PomDomParser.getFirstChildText(propertyElement, "name")
        return propertyName!!.startsWith("!")
    }

    private fun parseProperties(parentElement: Element?): MutableMap<String?, String?> {
        val pomProperties: MutableMap<String?, String?> = HashMap<String?, String?>()
        val propsEl = PomDomParser.getFirstChildElement(parentElement, PROPERTIES)
        if (propsEl != null) {
            propsEl.normalize()
        }
        for (prop in PomDomParser.getAllChilds(propsEl)) {
            pomProperties.put(prop.getNodeName(), PomDomParser.getTextContent(prop))
        }
        return pomProperties
    }

    private fun replaceProps(`val`: String?): String {
        if (`val` == null) {
            return null
        } else {
            return IvyPatternHelper.substituteVariables(`val`, effectiveProperties).trim { it <= ' ' }
        }
    }

    init {
        setPomProperties(childPomProperties)
        val systemId = resource.getFile().toURI().toASCIIString()
        val pomDomDoc = resource.withContent<Document?>(ExternalResource.ContentAction { inputStream: InputStream? ->
            try {
                return@withContent parseToDom(inputStream, systemId)
            } catch (e: Exception) {
                throw MetaDataParseException("POM", resource, e)
            }
        }).getResult()
        projectElement = pomDomDoc!!.getDocumentElement()
        if (PROJECT != projectElement.getNodeName() && MODEL != projectElement.getNodeName()) {
            throw SAXParseException("project must be the root tag", systemId, systemId, 0, 0)
        }
        parentElement = PomDomParser.getFirstChildElement(projectElement, PARENT)

        setDefaultParentGavProperties()
        setPomProperties(parseProperties(projectElement))
        setActiveProfileProperties()
    }

    companion object {
        private const val PACKAGING = "packaging"
        private const val DEPENDENCY = "dependency"
        private const val DEPENDENCIES = "dependencies"
        private const val DEPENDENCY_MGT = "dependencyManagement"
        private const val PROJECT = "project"
        private const val MODEL = "model"
        private const val GROUP_ID = "groupId"
        private const val ARTIFACT_ID = "artifactId"
        private const val VERSION = "version"
        private const val PARENT = "parent"
        private const val SCOPE = "scope"
        private const val CLASSIFIER = "classifier"
        private const val OPTIONAL = "optional"
        private const val EXCLUSIONS = "exclusions"
        private const val EXCLUSION = "exclusion"
        private const val DISTRIBUTION_MGT = "distributionManagement"
        private const val RELOCATION = "relocation"
        private const val PROPERTIES = "properties"
        private const val TYPE = "type"
        private const val PROFILES = "profiles"
        private const val PROFILE = "profile"
        private const val PROFILE_ID = "id"
        private const val PROFILE_ACTIVATION = "activation"
        private const val PROFILE_ACTIVATION_ACTIVE_BY_DEFAULT = "activeByDefault"
        private const val PROFILE_ACTIVATION_PROPERTY = "property"
        private val M2_ENTITIES_RESOURCE: ByteArray
        private val DOCUMENT_BUILDER_FACTORY: DocumentBuilderFactory

        init {
            val bytes: ByteArray
            try {
                bytes = IOUtils.toByteArray(PomReader::class.java.getResourceAsStream("m2-entities.ent"))
            } catch (e: IOException) {
                throw throwAsUncheckedException(e)
            }
            M2_ENTITIES_RESOURCE = bytes

            // Set the context classloader the bootstrap classloader, to work around the way that JAXP locates implementation classes
            // This should ensure that the JAXP classes provided by the JVM are used, rather than some other implementation
            val original = Thread.currentThread().getContextClassLoader()
            Thread.currentThread().setContextClassLoader(ClassLoaderUtils.getPlatformClassLoader())
            try {
                DOCUMENT_BUILDER_FACTORY = XmlFactories.newDocumentBuilderFactory()
                DOCUMENT_BUILDER_FACTORY.setValidating(false)
            } finally {
                Thread.currentThread().setContextClassLoader(original)
            }
        }

        private val M2_ENTITY_RESOLVER: EntityResolver = object : EntityResolver {
            override fun resolveEntity(publicId: String?, systemId: String?): InputSource? {
                if ((systemId != null) && systemId.endsWith("m2-entities.ent")) {
                    return InputSource(ByteArrayInputStream(M2_ENTITIES_RESOURCE))
                }
                return null
            }
        }

        private fun getDocBuilder(entityResolver: EntityResolver?): DocumentBuilder {
            try {
                val docBuilder: DocumentBuilder = DOCUMENT_BUILDER_FACTORY.newDocumentBuilder()
                if (entityResolver != null) {
                    docBuilder.setEntityResolver(entityResolver)
                }
                return docBuilder
            } catch (e: ParserConfigurationException) {
                throw throwAsUncheckedException(e)
            }
        }

        @Throws(IOException::class, SAXException::class)
        private fun parseToDom(stream: InputStream?, systemId: String?): Document? {
            // Set the context classloader the bootstrap classloader, to work around the way that JAXP locates implementation classes
            // This should ensure that the JAXP classes provided by the JVM are used, rather than some other implementation
            val original = Thread.currentThread().getContextClassLoader()
            Thread.currentThread().setContextClassLoader(ClassLoaderUtils.getPlatformClassLoader())
            try {
                val dtdStream: InputStream = PomDomParser.AddDTDFilterInputStream(stream)
                return getDocBuilder(M2_ENTITY_RESOLVER).parse(dtdStream, systemId)
            } finally {
                Thread.currentThread().setContextClassLoader(original)
            }
        }

        /**
         * Checks if the given value contains variable substitutions.
         *
         * @param value value to check
         * @return true if the value contains substitutions, false otherwise.
         */
        fun hasUnresolvedSubstitutions(value: String): Boolean {
            return value.contains("$") && VAR_PATTERN.matcher(value).matches()
        }

        // Copied from IvyPatternHelper
        private val VAR_PATTERN: Pattern = Pattern.compile("\\$\\{(.*?)\\}")
    }
}
