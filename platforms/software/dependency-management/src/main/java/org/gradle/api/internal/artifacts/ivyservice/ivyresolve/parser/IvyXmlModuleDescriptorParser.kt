/*
 * Copyright 2012 the original author or authors.
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

import com.google.common.base.Joiner
import com.google.common.collect.Sets
import org.apache.ivy.core.IvyPatternHelper
import org.apache.ivy.core.NormalRelativeUrlResolver
import org.apache.ivy.core.RelativeUrlResolver
import org.apache.ivy.core.module.descriptor.Configuration
import org.apache.ivy.core.module.descriptor.ConfigurationAware
import org.apache.ivy.core.module.descriptor.DefaultDependencyArtifactDescriptor
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import org.apache.ivy.core.module.descriptor.DependencyArtifactDescriptor
import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.apache.ivy.core.module.descriptor.ExcludeRule
import org.apache.ivy.core.module.descriptor.ExtraInfoHolder
import org.apache.ivy.core.module.descriptor.IncludeRule
import org.apache.ivy.core.module.descriptor.License
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.core.module.id.ArtifactId
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.plugins.matcher.PatternMatcher
import org.apache.ivy.plugins.parser.xml.XmlModuleDescriptorParser
import org.apache.ivy.util.extendable.DefaultExtendableItem
import org.apache.ivy.util.url.URLHandlerRegistry
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory.module
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil.createModuleId
import org.gradle.api.internal.artifacts.ivyservice.NamespaceId
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.PatternMatchers
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory
import org.gradle.api.internal.component.ArtifactType
import org.gradle.api.resources.MissingResourceException
import org.gradle.internal.classloader.ClassLoaderUtils
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.Companion.newId
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata.id
import org.gradle.internal.component.external.model.ivy.MutableIvyModuleResolveMetadata
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.local.FileResourceRepository
import org.gradle.internal.resource.local.LocallyAvailableExternalResource
import org.gradle.internal.resource.transfer.UrlExternalResource
import org.gradle.internal.xml.XmlFactories
import org.gradle.util.internal.CollectionUtils
import org.gradle.util.internal.TextUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import org.xml.sax.ext.LexicalHandler
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.MalformedURLException
import java.net.URL
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.function.Function
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParser

/**
 * Copied from org.apache.ivy.plugins.parser.xml.XmlModuleDescriptorParser into Gradle codebase, and heavily modified.
 */
open class IvyXmlModuleDescriptorParser(
    private val moduleDescriptorConverter: IvyModuleDescriptorConverter?,
    private val moduleIdentifierFactory: ImmutableModuleIdentifierFactory?,
    fileResourceRepository: FileResourceRepository?,
    private val metadataFactory: IvyMutableModuleMetadataFactory?
) : AbstractModuleDescriptorParser<MutableIvyModuleResolveMetadata?>(fileResourceRepository) {
    @Throws(IOException::class, ParseException::class)
    override fun doParseDescriptor(parseContext: DescriptorParseContext, resource: LocallyAvailableExternalResource, validate: Boolean): MetaDataParser.ParseResult<MutableIvyModuleResolveMetadata?> {
        val parser = createParser(parseContext, resource, populateProperties())
        parser.isValidate = validate
        parser.parse()

        val moduleDescriptor = parser.moduleDescriptor
        postProcess(moduleDescriptor)

        return MetaDataParser.ParseResult.< MutableIvyModuleResolveMetadata > of < org . gradle . internal . component . external . model . ivy . MutableIvyModuleResolveMetadata ? > (parser.getMetaData(), parser.hasGradleMetadataRedirect)
    }

    @Throws(MalformedURLException::class)
    protected open fun createParser(parseContext: DescriptorParseContext, resource: LocallyAvailableExternalResource, properties: MutableMap<String?, String?>?): Parser {
        return Parser(parseContext, moduleDescriptorConverter, resource, resource.getFile().toURI().toURL(), moduleIdentifierFactory, metadataFactory, properties)
    }

    protected fun postProcess(moduleDescriptor: DefaultModuleDescriptor?) {
    }

    override fun getTypeName(): String {
        return "Ivy file"
    }

    private fun populateProperties(): MutableMap<String?, String?> {
        val properties = HashMap<String?, String?>()
        val baseDir = File(".").getAbsolutePath()
        properties.put("ivy.default.settings.dir", baseDir)
        properties.put("ivy.basedir", baseDir)

        val propertyNames: MutableSet<String> =
            CollectionUtils.collect<String?, MutableMap.MutableEntry<Any?, Any?>?>(System.getProperties().entries, Function { entry: MutableMap.MutableEntry<Any?, Any?>? -> entry!!.key.toString() })

        for (property in propertyNames) {
            properties.put(property, System.getProperty(property))
        }
        return properties
    }

    protected abstract class AbstractParser protected constructor(protected val resource: ExternalResource) : DefaultHandler() {
        protected var defaultConf: String? = null // used only as defaultconf, not used for
            get() = if (field != null)
                field
            else
                (if (defaultConfMapping != null) defaultConfMapping else DEFAULT_CONF_MAPPING)

        // guessing right side part of a mapping
        protected var defaultConfMapping: String? = null // same as default conf but is used

        // for guessing right side part of a mapping
        protected var defaultConfMappingDescriptor: DefaultDependencyDescriptor? = null
            get() {
                if (field == null) {
                    field = DefaultDependencyDescriptor(createModuleRevisionId("", "", ""), false)
                    parseDepsConfs(defaultConfMapping, field!!, false, false)
                }
                return field
            }
            private set

        private val errors: MutableList<String?> = ArrayList<String?>()

        protected val md: DefaultModuleDescriptor
        protected var metaData: IvyModuleResolveMetaDataBuilder? = null

        init {
            // used for log and date only
            md = DefaultModuleDescriptor(XmlModuleDescriptorParser.getInstance(), null)
        }

        @Throws(ParseException::class)
        protected fun checkErrors() {
            if (!errors.isEmpty()) {
                throw ParseException(Joiner.on(TextUtil.getPlatformLineSeparator()).join(errors), 0)
            }
            if (this.md.getModuleRevisionId() == null) {
                throw MetaDataParseException("Ivy file", this.resource, GradleException("Not a valid Ivy file"))
            }
        }

        protected fun parseDepsConfs(
            confs: String?, dd: DefaultDependencyDescriptor,
            useDefaultMappingToGuessRightOperand: Boolean = defaultConfMapping != null, evaluateConditions: Boolean = true
        ) {
            if (confs == null) {
                return
            }

            val conf = confs.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            parseDepsConfs(conf, dd, useDefaultMappingToGuessRightOperand, evaluateConditions)
        }

        protected fun parseDepsConfs(
            conf: Array<String>, dd: DefaultDependencyDescriptor,
            useDefaultMappingToGuessRightOperand: Boolean, evaluateConditions: Boolean = true
        ) {
            replaceConfigurationWildcards(md)
            for (s in conf) {
                val ops = s.split("->".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (ops.size == 1) {
                    val modConfs = ops[0].split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    if (!useDefaultMappingToGuessRightOperand) {
                        for (modConf in modConfs) {
                            dd.addDependencyConfiguration(modConf.trim { it <= ' ' }, modConf.trim { it <= ' ' })
                        }
                    } else {
                        for (modConf in modConfs) {
                            val depConfs = this.defaultConfMappingDescriptor!!
                                .getDependencyConfigurations(modConf)
                            if (depConfs.size > 0) {
                                for (depConf in depConfs) {
                                    val mappedDependency = if (evaluateConditions)
                                        evaluateCondition(depConf.trim { it <= ' ' }, dd)
                                    else
                                        depConf.trim { it <= ' ' }
                                    if (mappedDependency != null) {
                                        dd.addDependencyConfiguration(
                                            modConf.trim { it <= ' ' },
                                            mappedDependency
                                        )
                                    }
                                }
                            } else {
                                // no default mapping found for this configuration, map
                                // configuration to itself
                                dd.addDependencyConfiguration(
                                    modConf.trim { it <= ' ' }, modConf
                                        .trim { it <= ' ' })
                            }
                        }
                    }
                } else if (ops.size == 2) {
                    val modConfs = ops[0].split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val depConfs = ops[1].split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    for (modConf in modConfs) {
                        for (depConf in depConfs) {
                            val mappedDependency = if (evaluateConditions) evaluateCondition(
                                depConf.trim { it <= ' ' }, dd
                            ) else depConf.trim { it <= ' ' }
                            if (mappedDependency != null) {
                                dd.addDependencyConfiguration(modConf.trim { it <= ' ' }, mappedDependency)
                            }
                        }
                    }
                } else {
                    addError("invalid conf " + s + " for " + dd)
                }
            }

            if (md.isMappingOverride()) {
                addExtendingConfigurations(conf, dd, useDefaultMappingToGuessRightOperand)
            }
        }

        /**
         * Evaluate the optional condition in the given configuration, like "[org=MYORG]confX". If
         * the condition evaluates to true, the configuration is returned, if the condition
         * evaluate to false, null is returned. If there are no conditions, the configuration
         * itself is returned.
         *
         * @param conf
         * the configuration to evaluate
         * @param dd
         * the dependencydescriptor to which the configuration will be added
         * @return the evaluated condition
         */
        private fun evaluateCondition(conf: String, dd: DefaultDependencyDescriptor): String? {
            if (conf.get(0) != '[') {
                return conf
            }

            val endConditionIndex = conf.indexOf(']')
            if (endConditionIndex == -1) {
                addError("invalid conf " + conf + " for " + dd)
                return null
            }

            val condition = conf.substring(1, endConditionIndex)

            val notEqualIndex = condition.indexOf("!=")
            if (notEqualIndex == -1) {
                val equalIndex = condition.indexOf('=')
                if (equalIndex == -1) {
                    addError("invalid conf " + conf + " for " + dd.getDependencyRevisionId())
                    return null
                }

                var leftOp = condition.substring(0, equalIndex).trim { it <= ' ' }
                val rightOp = condition.substring(equalIndex + 1).trim { it <= ' ' }

                // allow organisation synonyms, like 'org' or 'organization'
                if (leftOp == "org" || leftOp == "organization") {
                    leftOp = "organisation"
                }

                val attrValue = dd.getAttribute(leftOp)
                if (rightOp != attrValue) {
                    return null
                }
            } else {
                var leftOp = condition.substring(0, notEqualIndex).trim { it <= ' ' }
                val rightOp = condition.substring(notEqualIndex + 2).trim { it <= ' ' }

                // allow organisation synonyms, like 'org' or 'organization'
                if (leftOp == "org" || leftOp == "organization") {
                    leftOp = "organisation"
                }

                val attrValue = dd.getAttribute(leftOp)
                if (rightOp == attrValue) {
                    return null
                }
            }

            return conf.substring(endConditionIndex + 1)
        }

        private fun addExtendingConfigurations(
            confs: Array<String>, dd: DefaultDependencyDescriptor,
            useDefaultMappingToGuessRightOperand: Boolean
        ) {
            for (conf in confs) {
                addExtendingConfigurations(conf, dd, useDefaultMappingToGuessRightOperand)
            }
        }

        private fun addExtendingConfigurations(
            conf: String, dd: DefaultDependencyDescriptor,
            useDefaultMappingToGuessRightOperand: Boolean
        ) {
            val configsToAdd: MutableSet<String?> = HashSet<String?>()
            val configs = md.getConfigurations()
            for (config in configs) {
                val ext = config.getExtends()
                for (s in ext) {
                    if (conf == s) {
                        val configName = config.getName()
                        configsToAdd.add(configName)
                        addExtendingConfigurations(
                            configName, dd,
                            useDefaultMappingToGuessRightOperand
                        )
                    }
                }
            }

            val confs: Array<String> = configsToAdd.toTypedArray<String?>()
            parseDepsConfs(confs, dd, useDefaultMappingToGuessRightOperand)
        }

        protected fun addError(msg: String?) {
            errors.add(msg + " in " + resource.getDisplayName())
        }

        override fun warning(ex: SAXParseException) {
            LOGGER.warn("xml parsing: {}: {}", getLocationString(ex), ex.message)
        }

        override fun error(ex: SAXParseException) {
            addError("xml parsing: " + getLocationString(ex) + ": " + ex.message)
        }

        override fun fatalError(ex: SAXParseException) {
            addError("[Fatal Error] " + getLocationString(ex) + ": " + ex.message)
        }

        /** Returns a string of the location.  */
        private fun getLocationString(ex: SAXParseException): String {
            val str = StringBuilder()

            var systemId = ex.getSystemId()
            if (systemId != null) {
                val index = systemId.lastIndexOf('/')
                if (index != -1) {
                    systemId = systemId.substring(index + 1)
                }
                str.append(systemId)
            } else {
                str.append(this.resource.getDisplayName())
            }
            str.append(':')
            str.append(ex.getLineNumber())
            str.append(':')
            str.append(ex.getColumnNumber())

            return str.toString()
        } // getLocationString(SAXParseException):String

        @get:Throws(ParseException::class)
        val moduleDescriptor: DefaultModuleDescriptor
            get() {
                checkErrors()
                return md
            }

        fun getMetaData(): MutableIvyModuleResolveMetadata? {
            return metaData!!.build()
        }

        private fun replaceConfigurationWildcards(md: ModuleDescriptor) {
            val configs = md.getConfigurations()
            for (config in configs) {
                config.replaceWildcards(md)
            }
        }

        companion object {
            private const val DEFAULT_CONF_MAPPING = "*->*"
        }
    }

    open class Parser(/* how and what do we have to parse */val parseContext: DescriptorParseContext,
                      private val moduleDescriptorConverter: IvyModuleDescriptorConverter?,
                      res: ExternalResource,
                      private val descriptorURL: URL,
                      private val moduleIdentifierFactory: ImmutableModuleIdentifierFactory?,
                      private val metadataFactory: IvyMutableModuleMetadataFactory?,
                      val properties: MutableMap<String?, String?>?
    ) : AbstractParser(res), LexicalHandler {
        enum class State {
            NONE,
            INFO,
            CONF,
            PUB,
            DEP,
            DEP_ARTIFACT,
            ARTIFACT_INCLUDE,
            ARTIFACT_EXCLUDE,
            CONFLICT,
            EXCLUDE,
            DEPS,
            DESCRIPTION,
            EXTRA_INFO
        }

        private val relativeUrlResolver: RelativeUrlResolver = NormalRelativeUrlResolver()

        var isValidate: Boolean = true

        /* Parsing state */
        private var state = State.NONE
        private var defaultMatcher: PatternMatcher? = null
        private var dd: DefaultDependencyDescriptor? = null
        private var confAware: ConfigurationAware? = null
        private var artifact: BuildableIvyArtifact? = null
        private var conf: String? = null
        private var artifactsDeclared = false
        private var buffer: StringBuffer? = null
        private var descriptorVersion: String? = null
        private var publicationsDefaultConf: Array<String>?
        private var hasGradleMetadataRedirect = false

        open fun newParser(res: ExternalResource, descriptorURL: URL): Parser {
            val parser = Parser(parseContext, moduleDescriptorConverter, res, descriptorURL, moduleIdentifierFactory, metadataFactory, properties)
            parser.isValidate = this.isValidate
            return parser
        }

        @Throws(ParseException::class)
        fun parse() {
            this.resource.withContent(Action { inputStream: InputStream? ->
                val schemaURL = if (this.isValidate) this.schemaURL else null
                val inSrc = InputSource(inputStream)
                inSrc.setSystemId(descriptorURL.toExternalForm())
                try {
                    ParserHelper.parse(inSrc, schemaURL, this@Parser)
                } catch (e: Exception) {
                    throw MetaDataParseException("Ivy file", this.resource, e)
                }
            })
            checkErrors()
            maybeAddDefaultConfiguration()
            replaceConfigurationWildcards()
            maybeAddDefaultArtifact()
            validateConfigurations()
            validateArtifacts()
            validateExcludes()
            this.md.check()
        }

        private fun validateConfigurations() {
            for (configuration in this.md.getConfigurations()) {
                for (parent in configuration.getExtends()) {
                    requireNotNull(this.md.getConfiguration(parent)) { "Configuration '" + configuration.getName() + "' extends configuration '" + parent + "' which is not declared." }
                }
            }
        }

        private fun validateExcludes() {
            for (excludeRule in this.md.getAllExcludeRules()) {
                for (conf in excludeRule.getConfigurations()) {
                    requireNotNull(this.md.getConfiguration(conf)) { "Exclude rule " + excludeRule.getId() + " is mapped to configuration '" + conf + "' which is not declared." }
                }
            }
        }

        private fun validateArtifacts() {
            for (artifact in metaData!!.getArtifacts()) {
                for (conf in artifact.configurations!!) {
                    requireNotNull(this.md.getConfiguration(conf)) { "Artifact " + artifact.artifactName!!.displayName + " is mapped to configuration '" + conf + "' which is not declared." }
                }
            }
        }

        private fun maybeAddDefaultArtifact() {
            if (!artifactsDeclared) {
                val implicitArtifact: IvyArtifactName = DefaultIvyArtifactName(this.md.getModuleRevisionId().getName(), "jar", "jar")
                val configurationNames: MutableSet<String?> = Sets.newHashSet<String?>(*this.md.getConfigurationsNames())
                metaData!!.addArtifact(implicitArtifact, configurationNames)
            }
        }

        @Throws(SAXException::class)
        override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
            try {
                if (state == State.DESCRIPTION) {
                    // make sure we don't interpret any tag while in description tag
                    descriptionStarted(qName, attributes)
                } else if ("ivy-module" == qName) {
                    ivyModuleStarted(attributes)
                } else if ("info" == qName) {
                    infoStarted(attributes)
                } else if (state == State.INFO && "extends" == qName) {
                    extendsStarted(attributes)
                } else if (state == State.INFO && "license" == qName) {
                    this.md.addLicense(License(substitute(attributes.getValue("name")), substitute(attributes.getValue("url"))))
                } else if (state == State.INFO && "ivyauthor" == qName) {
                    // nothing to do, we don't store this
                    return
                } else if (state == State.INFO && "repository" == qName) {
                    // nothing to do, we don't store this
                    return
                } else if (state == State.INFO && "description" == qName) {
                    this.md.setHomePage(substitute(attributes.getValue("homepage")))
                    state = State.DESCRIPTION
                    buffer = StringBuffer()
                } else if (state == State.INFO && isOtherNamespace(qName)) {
                    buffer = StringBuffer()
                    state = State.EXTRA_INFO
                } else if ("configurations" == qName) {
                    configurationStarted(attributes)
                } else if ("publications" == qName) {
                    publicationsStarted(attributes)
                } else if ("dependencies" == qName) {
                    dependenciesStarted(attributes)
                } else if ("conflicts" == qName) {
                    state = State.CONFLICT
                    maybeAddDefaultConfiguration()
                } else if ("artifact" == qName) {
                    artifactStarted(qName, attributes)
                } else if ("include" == qName && state == State.DEP) {
                    addIncludeRule(qName, attributes)
                } else if ("exclude" == qName && state == State.DEP) {
                    addExcludeRule(qName, attributes)
                } else if ("exclude" == qName && state == State.DEPS) {
                    state = State.EXCLUDE
                    parseRule(qName, attributes)
                    this.md.addExcludeRule(confAware as ExcludeRule?)
                } else if ("dependency" == qName) {
                    dependencyStarted(attributes)
                } else if ("conf" == qName) {
                    confStarted(attributes)
                } else if ("mapped" == qName) {
                    dd!!.addDependencyConfiguration(conf, substitute(attributes.getValue("name")))
                } else if (("conflict" == qName && state == State.DEPS) || ("manager" == qName && state == State.CONFLICT)) {
                    LOGGER.debug("Ivy.xml conflict managers are not supported by Gradle. Ignoring conflict manager declared in {}", this.resource.getDisplayName())
                } else if ("override" == qName && state == State.DEPS) {
                    LOGGER.debug("Ivy.xml dependency overrides are not supported by Gradle. Ignoring override declared in {}", this.resource.getDisplayName())
                } else if ("include" == qName && state == State.CONF) {
                    includeConfStarted(attributes)
                } else if (this.isValidate && state != State.EXTRA_INFO && state != State.DESCRIPTION) {
                    addError("unknown tag " + qName)
                }
            } catch (ex: Exception) {
                if (ex is SAXException) {
                    throw ex
                }
                val sax = SAXException(
                    "Problem occurred while parsing ivy file: "
                            + ex.message, ex
                )
                sax.initCause(ex)
                throw sax
            }
        }

        @Throws(ParseException::class)
        private fun extendsStarted(attributes: Attributes) {
            val parentOrganisation = attributes.getValue("organisation")
            val parentModule = attributes.getValue("module")
            val parentRevision = attributes.getValue("revision")
            val location = elvis(attributes.getValue("location"), "../ivy.xml")

            val extendType = elvis(attributes.getValue("extendType"), "all").lowercase()
            val extendTypes = Arrays.asList<String?>(*extendType.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())

            var parent: ModuleDescriptor?
            try {
                LOGGER.debug("Trying to parse included ivy file :{}", location)
                parent = parseOtherIvyFileOnFileSystem(location)
                if (parent != null) {
                    //verify that the parsed descriptor is the correct parent module.
                    val expected = createModuleId(parentOrganisation, parentModule)
                    val pid = parent.getModuleRevisionId().getModuleId()
                    if (expected != pid) {
                        LOGGER.warn("Ignoring parent Ivy file {}; expected {} but found {}", location, expected, pid)
                        parent = null
                    }
                }

                // if the included ivy file is not found on file system, tries to resolve using
                // repositories
                if (parent == null) {
                    LOGGER.debug(
                        "Trying to parse included ivy file by asking repository for module :{}#{};{}",
                        parentOrganisation, parentModule, parentRevision
                    )
                    parent = parseOtherIvyFile(parentOrganisation, parentModule, parentRevision)
                }
            } catch (e: Exception) {
                throw ParseException("Unable to parse included ivy file for " + parentOrganisation + "#" + parentModule + ";" + parentRevision, 0).initCause(e) as ParseException?
            }

            if (parent == null) {
                throw ParseException("Unable to parse included ivy file for " + parentOrganisation + "#" + parentModule + ";" + parentRevision, 0)
            }

            mergeWithOtherModuleDescriptor(extendTypes, parent)
        }

        private fun mergeWithOtherModuleDescriptor(extendTypes: MutableList<String?>, parent: ModuleDescriptor) {
            if (extendTypes.contains("all")) {
                mergeAll(parent)
            } else {
                if (extendTypes.contains("info")) {
                    mergeInfo(parent)
                }

                if (extendTypes.contains("configurations")) {
                    mergeConfigurations(parent.getModuleRevisionId(), parent.getConfigurations())
                }

                if (extendTypes.contains("dependencies")) {
                    mergeDependencies(parent.getDependencies())
                }

                if (extendTypes.contains("description")) {
                    mergeDescription(parent.getDescription())
                }
            }
        }

        private fun mergeAll(parent: ModuleDescriptor) {
            val sourceMrid = parent.getModuleRevisionId()
            mergeInfo(parent)
            mergeConfigurations(sourceMrid, parent.getConfigurations())
            mergeDependencies(parent.getDependencies())
            mergeDescription(parent.getDescription())
        }

        private fun mergeInfo(parent: ModuleDescriptor) {
            val parentMrid = parent.getModuleRevisionId()

            val descriptor = this.md
            val currentMrid = descriptor.getModuleRevisionId()

            val mergedMrid: ModuleRevisionId? = createModuleRevisionId(
                mergeValue(parentMrid.getOrganisation(), currentMrid.getOrganisation()),
                currentMrid.getName(),
                mergeValue(parentMrid.getBranch(), currentMrid.getBranch()),
                mergeValue(parentMrid.getRevision(), currentMrid.getRevision()),
                mergeValues(parentMrid.getQualifiedExtraAttributes(), currentMrid.getQualifiedExtraAttributes())
            )

            descriptor.setModuleRevisionId(mergedMrid)
            descriptor.setResolvedModuleRevisionId(mergedMrid)

            descriptor.setStatus(mergeValue(parent.getStatus(), descriptor.getStatus()))
            if (descriptor.getNamespace() == null && parent is DefaultModuleDescriptor) {
                val parentNamespace = parent.getNamespace()
                descriptor.setNamespace(parentNamespace)
            }
        }

        private fun mergeConfigurations(sourceMrid: ModuleRevisionId?, configurations: Array<Configuration>) {
            val md = this.md
            for (configuration in configurations) {
                LOGGER.debug("Merging configuration with: {}", configuration.getName())
                //copy configuration from parent descriptor
                md.addConfiguration(Configuration(configuration, sourceMrid))
            }
        }

        private fun mergeDependencies(dependencies: Array<DependencyDescriptor>) {
            val md = this.md
            for (dependencyDescriptor in dependencies) {
                LOGGER.debug("Merging dependency with: {}", dependencyDescriptor.getDependencyRevisionId())
                md.addDependency(dependencyDescriptor)
            }
        }

        private fun mergeDescription(description: String?) {
            val current = this.md.getDescription()
            if (current == null || current.trim { it <= ' ' }.length == 0) {
                this.md.setDescription(description)
            }
        }

        @Throws(ParseException::class, IOException::class)
        private fun parseOtherIvyFileOnFileSystem(location: String?): ModuleDescriptor? {
            val url = relativeUrlResolver.getURL(descriptorURL, location)
            LOGGER.debug("Trying to load included ivy file from {}", url)
            val resource = UrlExternalResource.open(url)
            try {
                return parseModuleDescriptor(resource, url)
            } catch (e: MissingResourceException) {
                // Ignore
                return null
            }
        }

        @Throws(IOException::class, ParseException::class, SAXException::class)
        protected open fun parseOtherIvyFile(parentOrganisation: String?, parentModule: String?, parentRevision: String): ModuleDescriptor {
            val importedId = newId(DefaultModuleIdentifier.newId(parentOrganisation, parentModule!!), parentRevision)
            val externalResource = parseContext.getMetaDataArtifact(importedId, ArtifactType.IVY_DESCRIPTOR)

            return parseModuleDescriptor(externalResource, externalResource.getFile().toURI().toURL())
        }

        @Throws(ParseException::class)
        private fun parseModuleDescriptor(externalResource: ExternalResource, descriptorURL: URL): ModuleDescriptor {
            val parser = newParser(externalResource, descriptorURL)
            parser.parse()
            return parser.moduleDescriptor
        }

        private fun publicationsStarted(attributes: Attributes) {
            state = State.PUB
            artifactsDeclared = true
            maybeAddDefaultConfiguration()
            val defaultConf = substitute(attributes.getValue("defaultconf"))
            if (defaultConf != null) {
                this.publicationsDefaultConf = defaultConf.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            }
        }

        private fun isOtherNamespace(qName: String): Boolean {
            return qName.indexOf(':') != -1
        }

        @Throws(SAXException::class, IOException::class, ParserConfigurationException::class, ParseException::class)
        private fun includeConfStarted(attributes: Attributes) {
            val url = relativeUrlResolver.getURL(descriptorURL, substitute(attributes.getValue("file")), substitute(attributes.getValue("url")))
            if (url == null) {
                throw SAXException("include tag must have a file or an url attribute")
            }

            // create a new temporary parser to read the configurations from
            // the specified file.
            val parser = newParser(UrlExternalResource.open(url), url)
            ParserHelper.parse(url, null, parser)

            // add the configurations from this temporary parser to this module descriptor
            val configs = parser.moduleDescriptor.getConfigurations()
            for (config in configs) {
                this.md.addConfiguration(config)
            }
            if (parser.defaultConfMapping != null) {
                LOGGER.debug(
                    "setting default conf mapping from imported configurations file: {}",
                    parser.defaultConfMapping
                )
                this.defaultConfMapping = parser.defaultConfMapping
            }
            if (parser.defaultConf != null) {
                LOGGER.debug(
                    "setting default conf from imported configurations file: {}",
                    parser.defaultConf
                )
                this.defaultConf = parser.defaultConf
            }
            if (parser.md.isMappingOverride()) {
                LOGGER.debug("enabling mapping-override from imported configurations file")
                this.md.setMappingOverride(true)
            }
        }

        private fun confStarted(attributes: Attributes) {
            val conf = substitute(attributes.getValue("name"))
            when (state) {
                State.CONF -> {
                    val visibility = Configuration.Visibility.getVisibility(elvis(substitute(attributes.getValue("visibility")), "public"))
                    val description = substitute(attributes.getValue("description"))
                    val extend: Array<String?>? =
                        if (substitute(attributes.getValue("extends")) == null) null else substitute(attributes.getValue("extends")).split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val transitiveValue = attributes.getValue("transitive")
                    val transitive = (transitiveValue == null) || attributes.getValue("transitive").toBoolean()
                    val deprecated = attributes.getValue("deprecated")
                    val configuration = Configuration(conf, visibility, description, extend, transitive, deprecated)
                    fillExtraAttributes(
                        configuration, attributes,
                        arrayOf<String>("name", "visibility", "extends", "transitive", "description", "deprecated")
                    )
                    this.md.addConfiguration(configuration)
                }

                State.PUB -> if ("*" == conf) {
                    val confs = this.md.getConfigurationsNames()
                    for (confName in confs) {
                        artifact!!.addConfiguration(confName)
                    }
                } else {
                    artifact!!.addConfiguration(conf)
                }

                State.DEP -> {
                    this.conf = conf
                    val mappeds = substitute(attributes.getValue("mapped"))
                    if (mappeds != null) {
                        val mapped = mappeds.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                        for (depConf in mapped) {
                            dd!!.addDependencyConfiguration(conf, depConf.trim { it <= ' ' })
                        }
                    }
                }

                State.DEP_ARTIFACT, State.ARTIFACT_INCLUDE, State.ARTIFACT_EXCLUDE -> addConfiguration(conf)
                else -> if (this.isValidate) {
                    addError("conf tag found in invalid tag: " + state)
                }
            }
        }

        private fun dependencyStarted(attributes: Attributes) {
            state = State.DEP
            var org = substitute(attributes.getValue("org"))
            if (org == null) {
                org = this.md.getModuleRevisionId().getOrganisation()
            }
            val force = substitute(attributes.getValue("force")).toBoolean()
            val changing = substitute(attributes.getValue("changing")).toBoolean()

            val transitiveValue = substitute(attributes.getValue("transitive"))
            val transitive = (transitiveValue == null) || transitiveValue.toBoolean()

            val name = substitute(attributes.getValue("name"))
            val branch = substitute(attributes.getValue("branch"))
            val branchConstraint = substitute(attributes.getValue("branchConstraint"))
            val rev = substitute(attributes.getValue("rev"))
            val revConstraint = substitute(attributes.getValue("revConstraint"))

            val extraAttributes = getExtraAttributes(attributes, org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.IvyXmlModuleDescriptorParser.Companion.DEPENDENCY_REGULAR_ATTRIBUTES)

            val revId: ModuleRevisionId = createModuleRevisionId(org, name, branch, rev, extraAttributes)
            val dynamicId: ModuleRevisionId
            if ((revConstraint == null) && (branchConstraint == null)) {
                // no dynamic constraints defined, so dynamicId equals revId
                dynamicId = createModuleRevisionId(org, name, branch, rev, extraAttributes, false)
            } else {
                if (branchConstraint == null) {
                    // this situation occurs when there was no branch defined
                    // in the original dependency descriptor. So the dynamicId
                    // shouldn't contain a branch neither
                    dynamicId = createModuleRevisionId(org, name, null, revConstraint, extraAttributes, false)
                } else {
                    dynamicId = createModuleRevisionId(org, name, branchConstraint, revConstraint, extraAttributes)
                }
            }

            dd = org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor(this.md, revId, dynamicId, force, changing, transitive)
            this.md.addDependency(dd)
            val confs = substitute(attributes.getValue("conf"))
            if (confs != null && confs.length > 0) {
                parseDepsConfs(confs, dd!!)
            }
        }

        @Throws(MalformedURLException::class)
        private fun artifactStarted(qName: String?, attributes: Attributes) {
            if (state == State.PUB) {
                // this is a published artifact
                val artName = elvis(substitute(attributes.getValue("name")), this.md.getModuleRevisionId().getName())
                val type = elvis(substitute(attributes.getValue("type")), "jar")
                val ext = elvis(substitute(attributes.getValue("ext")), type)
                val classifier = readClassifierAttribute(attributes)
                artifact = BuildableIvyArtifact(artName, type, ext, classifier)
                val confs = substitute(attributes.getValue("conf"))

                // Only add confs if they are specified. if they aren't, endElement will handle this only if there are no conf defined in sub elements
                if (confs != null && confs.length > 0) {
                    val conf: Array<String>
                    if ("*" == confs) {
                        conf = this.md.getConfigurationsNames()
                    } else {
                        conf = confs.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    }
                    for (confName in conf) {
                        artifact!!.addConfiguration(confName.trim { it <= ' ' })
                    }
                }
            } else if (state == State.DEP) {
                // this is an artifact asked for a particular dependency
                addDependencyArtifacts(qName, attributes)
            } else if (this.isValidate) {
                addError("artifact tag found in invalid tag: " + state)
            }
        }

        /**
         * Handle the 'classifier' attribute in any namespace: different tools publish differently.
         */
        private fun readClassifierAttribute(attributes: Attributes): String? {
            for (i in 0..<attributes.getLength()) {
                if (attributes.getLocalName(i) == "classifier") {
                    return attributes.getValue(i)
                }
            }
            return null
        }

        private fun dependenciesStarted(attributes: Attributes) {
            state = State.DEPS
            val defaultConf = substitute(attributes.getValue("defaultconf"))
            if (defaultConf != null) {
                this.defaultConf = defaultConf
            }
            val defaultConfMapping = substitute(attributes.getValue("defaultconfmapping"))
            if (defaultConfMapping != null) {
                this.defaultConfMapping = defaultConfMapping
            }
            val confMappingOverride = substitute(attributes.getValue("confmappingoverride"))
            if (confMappingOverride != null) {
                this.md.setMappingOverride(confMappingOverride.toBoolean())
            }
            maybeAddDefaultConfiguration()
        }

        private fun configurationStarted(attributes: Attributes) {
            state = State.CONF
            this.defaultConfMapping = substitute(attributes.getValue("defaultconfmapping"))
            this.defaultConf = substitute(attributes.getValue("defaultconf"))
            this.md.setMappingOverride(substitute(attributes.getValue("confmappingoverride")).toBoolean())
        }

        private fun infoStarted(attributes: Attributes) {
            state = State.INFO
            val org = substitute(attributes.getValue("organisation"))
            val module = substitute(attributes.getValue("module"))
            val revision = substitute(attributes.getValue("revision"))
            val branch = substitute(attributes.getValue("branch"))
            val extraAttributes = getExtraAttributes(attributes, arrayOf<String>("organisation", "module", "revision", "status", "publication", "branch", "namespace", "default", "resolver"))
            this.md.setModuleRevisionId(createModuleRevisionId(org, module, branch, revision, extraAttributes))

            this.md.setStatus(elvis(substitute(attributes.getValue("status")), "integration"))
            this.md.setDefault(substitute(attributes.getValue("default")).toBoolean())
            val pubDate = substitute(attributes.getValue("publication"))
            if (pubDate != null && pubDate.length > 0) {
                try {
                    val ivyDateFormat = SimpleDateFormat(org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.IvyXmlModuleDescriptorParser.Companion.IVY_DATE_FORMAT_PATTERN)
                    this.md.setPublicationDate(ivyDateFormat.parse(pubDate))
                } catch (e: ParseException) {
                    addError("invalid publication date format: " + pubDate)
                }
            }
        }

        @Throws(SAXException::class)
        private fun ivyModuleStarted(attributes: Attributes) {
            descriptorVersion = attributes.getValue("version")
            val versionIndex = ALLOWED_VERSIONS.indexOf(descriptorVersion)
            if (versionIndex == -1) {
                addError("invalid version " + descriptorVersion)
                throw SAXException("invalid version " + descriptorVersion)
            }
            if (versionIndex >= ALLOWED_VERSIONS.indexOf("1.3")) {
                LOGGER.debug("post 1.3 ivy file: using {} as default matcher", PatternMatcher.EXACT)
                defaultMatcher = getMatcher(PatternMatcher.EXACT)
            } else {
                LOGGER.debug("pre 1.3 ivy file: using {} as default matcher", PatternMatcher.EXACT_OR_REGEXP)
                defaultMatcher = getMatcher(PatternMatcher.EXACT_OR_REGEXP)
            }

            for (i in 0..<attributes.getLength()) {
                if (attributes.getQName(i).startsWith("xmlns:")) {
                    this.md.addExtraAttributeNamespace(attributes.getQName(i).substring("xmlns:".length), attributes.getValue(i))
                }
            }
        }

        private fun descriptionStarted(qName: String?, attributes: Attributes) {
            buffer!!.append("<").append(qName)
            for (i in 0..<attributes.getLength()) {
                buffer!!.append(" ")
                buffer!!.append(attributes.getQName(i))
                buffer!!.append("=\"")
                buffer!!.append(attributes.getValue(i))
                buffer!!.append("\"")
            }
            buffer!!.append(">")
        }

        @Throws(MalformedURLException::class)
        private fun addDependencyArtifacts(tag: String?, attributes: Attributes) {
            state = State.DEP_ARTIFACT
            parseRule(tag, attributes)
        }

        @Throws(MalformedURLException::class)
        private fun addIncludeRule(tag: String?, attributes: Attributes) {
            state = State.ARTIFACT_INCLUDE
            parseRule(tag, attributes)
        }

        @Throws(MalformedURLException::class)
        private fun addExcludeRule(tag: String?, attributes: Attributes) {
            state = State.ARTIFACT_EXCLUDE
            parseRule(tag, attributes)
        }

        @Throws(MalformedURLException::class)
        private fun parseRule(tag: String?, attributes: Attributes) {
            var name = substitute(attributes.getValue("name"))
            if (name == null) {
                name = substitute(attributes.getValue("artifact"))
                if (name == null) {
                    name = if ("artifact" == tag)
                        dd!!.getDependencyId().getName()
                    else
                        PatternMatchers.ANY_EXPRESSION
                }
            }
            var type = substitute(attributes.getValue("type"))
            if (type == null) {
                type = if ("artifact" == tag) "jar" else PatternMatchers.ANY_EXPRESSION
            }
            var ext = substitute(attributes.getValue("ext"))
            ext = if (ext != null) ext else type
            if (state == State.DEP_ARTIFACT) {
                val url = substitute(attributes.getValue("url"))
                val extraAttributes = getExtraAttributes(attributes, arrayOf<String>("name", "type", "ext", "url", "conf"))
                confAware = DefaultDependencyArtifactDescriptor(dd, name, type, ext, if (url == null) null else URL(url), extraAttributes)
            } else if (state == State.ARTIFACT_INCLUDE) {
                val matcher = getPatternMatcher(attributes.getValue("matcher"))
                val org = elvis(substitute(attributes.getValue("org")), PatternMatchers.ANY_EXPRESSION)
                val module = elvis(substitute(attributes.getValue("module")), org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.PatternMatchers.ANY_EXPRESSION)
                val aid: ArtifactId = org.apache.ivy.core.module.id.ArtifactId(org.gradle.api.internal.artifacts.ivyservice.IvyUtil.createModuleId(org, module), name, type, ext)
                val extraAttributes = getExtraAttributes(attributes, arrayOf<String>("org", "module", "name", "type", "ext", "matcher", "conf"))
                confAware = org.apache.ivy.core.module.descriptor.DefaultIncludeRule(aid, matcher, extraAttributes)
            } else { // _state == ARTIFACT_EXCLUDE || EXCLUDE
                val matcher = getPatternMatcher(attributes.getValue("matcher"))
                val org = elvis(substitute(attributes.getValue("org")), PatternMatchers.ANY_EXPRESSION)
                val module = elvis(substitute(attributes.getValue("module")), org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.PatternMatchers.ANY_EXPRESSION)
                val aid: ArtifactId = org.apache.ivy.core.module.id.ArtifactId(org.gradle.api.internal.artifacts.ivyservice.IvyUtil.createModuleId(org, module), name, type, ext)
                val extraAttributes = getExtraAttributes(attributes, arrayOf<String>("org", "module", "name", "type", "ext", "matcher", "conf"))
                confAware = org.apache.ivy.core.module.descriptor.DefaultExcludeRule(aid, matcher, extraAttributes)
            }
            val confs = substitute(attributes.getValue("conf"))
            // only add confs if they are specified. if they aren't, endElement will handle this
            // only if there are no conf defined in sub elements
            if (confs != null && confs.length > 0) {
                val conf: Array<String>
                if ("*" == confs) {
                    conf = this.md.getConfigurationsNames()
                } else {
                    conf = confs.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                }
                for (confName in conf) {
                    addConfiguration(confName.trim { it <= ' ' })
                }
            }
        }

        private fun addConfiguration(c: String?) {
            confAware!!.addConfiguration(c)
            if (state != State.EXCLUDE) {
                // we are currently adding a configuration to either an include, exclude or artifact
                // element
                // of a dependency. This means that we have to add this element to the corresponding
                // conf
                // of the current dependency descriptor
                if (confAware is DependencyArtifactDescriptor) {
                    dd!!.addDependencyArtifact(c, confAware as DependencyArtifactDescriptor)
                } else if (confAware is IncludeRule) {
                    dd!!.addIncludeRule(c, confAware as IncludeRule)
                } else if (confAware is ExcludeRule) {
                    dd!!.addExcludeRule(c, confAware as ExcludeRule)
                }
            }
        }

        private fun getPatternMatcher(m: String?): PatternMatcher {
            val matcherName = substitute(m)
            val matcher = (if (matcherName == null) defaultMatcher else getMatcher(matcherName))!!
            requireNotNull(matcher) { "unknown matcher " + matcherName }
            return matcher
        }

        override fun characters(ch: CharArray?, start: Int, length: Int) {
            if (buffer != null) {
                buffer!!.append(ch, start, length)
            }
        }

        @Suppress("deprecation")
        override fun endElement(uri: String, localName: String, qName: String?) {
            if (state == State.PUB && "artifact" == qName) {
                if (artifact!!.getConfigurations().isEmpty()) {
                    val confs = (if (publicationsDefaultConf == null) this.md.getConfigurationsNames() else publicationsDefaultConf)!!
                    for (confName in confs) {
                        artifact!!.addConfiguration(confName.trim { it <= ' ' })
                    }
                }
                metaData!!.addArtifact(artifact!!.getArtifact(), artifact!!.getConfigurations())
                artifact = null
            } else if ("configurations" == qName) {
                maybeAddDefaultConfiguration()
            } else if ((state == State.DEP_ARTIFACT && "artifact" == qName)
                || (state == State.ARTIFACT_INCLUDE && "include" == qName)
                || (state == State.ARTIFACT_EXCLUDE && "exclude" == qName)
            ) {
                state = State.DEP
                if (confAware!!.getConfigurations().size == 0) {
                    val confs = this.md.getConfigurationsNames()
                    for (confName in confs) {
                        addConfiguration(confName)
                    }
                }
                confAware = null
            } else if ("exclude" == qName && state == State.EXCLUDE) {
                if (confAware!!.getConfigurations().size == 0) {
                    val confs = this.md.getConfigurationsNames()
                    for (confName in confs) {
                        addConfiguration(confName)
                    }
                }
                confAware = null
                state = State.DEPS
            } else if ("dependency" == qName && state == State.DEP) {
                if (dd!!.getModuleConfigurations().size == 0) {
                    parseDepsConfs(this.defaultConf, dd!!)
                }
                state = State.DEPS
            } else if ("dependencies" == qName && state == State.DEPS) {
                state = State.NONE
            } else if (state == State.INFO && "info" == qName) {
                metaData = IvyModuleResolveMetaDataBuilder(this.md, moduleDescriptorConverter, metadataFactory)
                state = State.NONE
            } else if (state == State.DESCRIPTION && "description" == qName) {
                this.md.setDescription(if (buffer == null) "" else buffer.toString().trim { it <= ' ' })
                buffer = null
                state = State.INFO
            } else if (state == State.EXTRA_INFO) {
                this.md.addExtraInfo(
                    ExtraInfoHolder(
                        NamespaceId(uri, localName).encode(),
                        if (buffer == null) "" else buffer.toString()
                    )
                )
                buffer = null
                state = State.INFO
            } else if (state == State.DESCRIPTION) {
                if (buffer.toString().endsWith("<" + qName + ">")) {
                    buffer!!.deleteCharAt(buffer!!.length - 1)
                    buffer!!.append("/>")
                } else {
                    buffer!!.append("</").append(qName).append(">")
                }
            }
        }

        private fun maybeAddDefaultConfiguration() {
            if (this.md.getConfigurations().size == 0) {
                this.md.addConfiguration(Configuration("default"))
            }
        }

        private fun replaceConfigurationWildcards() {
            val configs = this.md.getConfigurations()
            for (config in configs) {
                config.replaceWildcards(this.md)
            }
        }

        private val schemaURL: URL
            get() {
                val resource: URL? = checkNotNull(javaClass.getClassLoader().getResource("org/apache/ivy/plugins/parser/xml/ivy.xsd"))
                return resource
            }

        private fun elvis(value: String?, defaultValue: String): String {
            return if (value != null) value else defaultValue
        }

        private fun substitute(value: String?): String {
            return IvyPatternHelper.substituteVariables(value, properties)
        }

        private fun getExtraAttributes(attributes: Attributes, ignoredAttributeNames: Array<String?>): MutableMap<String?, String?> {
            val ret: MutableMap<String?, String?> = HashMap<String?, String?>()
            val ignored: MutableCollection<String?> = Arrays.asList<String?>(*ignoredAttributeNames)
            for (i in 0..<attributes.getLength()) {
                if (!ignored.contains(attributes.getQName(i))) {
                    ret.put(attributes.getQName(i), substitute(attributes.getValue(i)))
                }
            }
            return ret
        }

        private fun fillExtraAttributes(item: DefaultExtendableItem, attributes: Attributes, ignoredAttNames: Array<String?>) {
            val extraAttributes = getExtraAttributes(attributes, ignoredAttNames)
            for (name in extraAttributes.keys) {
                item.setExtraAttribute(name, extraAttributes.get(name))
            }
        }

        private fun getMatcher(matcherName: String?): PatternMatcher {
            return PatternMatchers.getInstance().getMatcher(matcherName)
        }

        // Handler to detect Gradle metadata redirects
        override fun startDTD(name: String?, publicId: String?, systemId: String?) {
        }

        override fun endDTD() {
        }

        override fun startEntity(name: String?) {
        }

        override fun endEntity(name: String?) {
        }

        override fun startCDATA() {
        }

        override fun endCDATA() {
        }

        override fun comment(ch: CharArray, start: Int, length: Int) {
            val comment = String(ch, start, length)
            if (comment.contains(MetaDataParser.GRADLE_6_METADATA_MARKER) || comment.contains(MetaDataParser.GRADLE_METADATA_MARKER)) {
                hasGradleMetadataRedirect = true
            }
        }

        companion object {
            private val ALLOWED_VERSIONS = mutableListOf<String?>("1.0", "1.1", "1.2", "1.3", "1.4", "2.0", "2.1", "2.2")

            private fun mergeValue(inherited: String?, override: String?): String? {
                return if (override == null) inherited else override
            }

            private fun mergeValues(inherited: MutableMap<String?, String?>, overrides: MutableMap<String?, String?>): MutableMap<String?, String?> {
                val dup = LinkedHashMap<String?, String?>(inherited.size + overrides.size)
                dup.putAll(inherited)
                dup.putAll(overrides)
                return dup
            }
        }
    }

    object ParserHelper {
        const val JAXP_SCHEMA_LANGUAGE
                : String = "http://java.sun.com/xml/jaxp/properties/schemaLanguage"

        const val JAXP_SCHEMA_SOURCE
                : String = "http://java.sun.com/xml/jaxp/properties/schemaSource"

        const val XML_NAMESPACE_PREFIXES
                : String = "http://xml.org/sax/features/namespace-prefixes"

        const val W3C_XML_SCHEMA: String = "http://www.w3.org/2001/XMLSchema"

        @Throws(ParserConfigurationException::class, SAXException::class)
        private fun newSAXParser(schema: URL?, schemaStream: InputStream?): SAXParser {
            if (schema == null) {
                val parserFactory = XmlFactories.newSAXParserFactory()
                parserFactory.setValidating(false)
                parserFactory.setNamespaceAware(true)
                val parser = parserFactory.newSAXParser()
                parser.getXMLReader().setFeature(XML_NAMESPACE_PREFIXES, true)
                return parser
            } else {
                val parserFactory = XmlFactories.newSAXParserFactory()
                parserFactory.setValidating(true)
                parserFactory.setNamespaceAware(true)

                val parser = parserFactory.newSAXParser()
                parser.setProperty(JAXP_SCHEMA_LANGUAGE, W3C_XML_SCHEMA)
                parser.setProperty(JAXP_SCHEMA_SOURCE, schemaStream)
                parser.getXMLReader().setFeature(XML_NAMESPACE_PREFIXES, true)
                return parser
            }
        }

        @Throws(SAXException::class, IOException::class, ParserConfigurationException::class)
        fun parse(
            xmlURL: URL, schema: URL?, handler: DefaultHandler?
        ) {
            URLHandlerRegistry.getDefault().openStream(xmlURL).use { xmlStream ->
                val inSrc = InputSource(xmlStream)
                inSrc.setSystemId(xmlURL.toExternalForm())
                parse(inSrc, schema, handler)
            }
        }

        @Throws(SAXException::class, IOException::class, ParserConfigurationException::class)
        fun parse(
            xmlStream: InputSource?, schema: URL?, handler: DefaultHandler?
        ) {
            var schemaStream: InputStream? = null
            try {
                if (schema != null) {
                    schemaStream = URLHandlerRegistry.getDefault().openStream(schema)
                }

                // Set the context classloader to the bootstrap classloader, to work around how JAXP locates implementation classes
                // This should ensure that the JAXP classes provided by the JVM are used, rather than some other implementation
                val original = Thread.currentThread().getContextClassLoader()
                Thread.currentThread().setContextClassLoader(ClassLoaderUtils.getPlatformClassLoader())
                try {
                    val parser = newSAXParser(schema, schemaStream)
                    parser.setProperty("http://xml.org/sax/properties/lexical-handler", handler)
                    parser.parse(xmlStream, handler)
                } finally {
                    Thread.currentThread().setContextClassLoader(original)
                }
            } finally {
                if (schemaStream != null) {
                    try {
                        schemaStream.close()
                    } catch (ex: IOException) {
                        // ignored
                    }
                }
            }
        }
    }

    override fun toString(): String {
        return "ivy parser"
    }

    companion object {
        val DEPENDENCY_REGULAR_ATTRIBUTES: Array<String?> = arrayOf<String>("org", "name", "branch", "branchConstraint", "rev", "revConstraint", "force", "transitive", "changing", "conf")

        const val IVY_DATE_FORMAT_PATTERN: String = "yyyyMMddHHmmss"

        private val LOGGER: Logger = LoggerFactory.getLogger(IvyXmlModuleDescriptorParser::class.java)
    }
}
