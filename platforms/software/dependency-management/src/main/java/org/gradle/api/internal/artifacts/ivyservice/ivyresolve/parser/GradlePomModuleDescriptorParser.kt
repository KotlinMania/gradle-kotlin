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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.data.MavenDependencyKey
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.data.PomDependencyMgt
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.MavenVersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory
import org.gradle.api.internal.component.ArtifactType
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.Companion.newId
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.external.model.maven.MutableMavenModuleResolveMetadata
import org.gradle.internal.resource.local.FileResourceRepository
import org.gradle.internal.resource.local.LocallyAvailableExternalResource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.xml.sax.SAXException
import java.io.IOException
import java.text.ParseException

/**
 * This based on a copy of org.apache.ivy.plugins.parser.m2.PomModuleDescriptorParser, but now heavily refactored.
 */
class GradlePomModuleDescriptorParser(
    gradleVersionSelectorScheme: VersionSelectorScheme,
    moduleIdentifierFactory: ImmutableModuleIdentifierFactory,
    fileResourceRepository: FileResourceRepository?, metadataFactory: MavenMutableModuleMetadataFactory
) : AbstractModuleDescriptorParser<MutableMavenModuleResolveMetadata?>(fileResourceRepository) {
    private val gradleVersionSelectorScheme: VersionSelectorScheme?
    private val mavenVersionSelectorScheme: VersionSelectorScheme
    private val moduleIdentifierFactory: ImmutableModuleIdentifierFactory
    private val metadataFactory: MavenMutableModuleMetadataFactory

    init {
        this.gradleVersionSelectorScheme = gradleVersionSelectorScheme
        mavenVersionSelectorScheme = MavenVersionSelectorScheme(gradleVersionSelectorScheme)
        this.moduleIdentifierFactory = moduleIdentifierFactory
        this.metadataFactory = metadataFactory
    }

    override fun getTypeName(): String {
        return "POM"
    }

    override fun toString(): String {
        return "gradle pom parser"
    }

    @Throws(IOException::class, ParseException::class, SAXException::class)
    override fun doParseDescriptor(
        parserSettings: DescriptorParseContext,
        resource: LocallyAvailableExternalResource,
        validate: Boolean
    ): MetaDataParser.ParseResult<MutableMavenModuleResolveMetadata?> {
        val pomReader = PomReader(resource, moduleIdentifierFactory)
        val mdBuilder = GradlePomModuleDescriptorBuilder(pomReader, gradleVersionSelectorScheme, mavenVersionSelectorScheme)

        doParsePom(parserSettings, mdBuilder, pomReader)

        val dependencies = mdBuilder.getDependencies()
        val cid = mdBuilder.getComponentIdentifier()
        val metadata = metadataFactory.create(cid, dependencies)
        metadata.status = mdBuilder.getStatus()
        if (pomReader.relocation != null) {
            metadata.packaging = "pom"
            metadata.isRelocated = true
        } else {
            metadata.packaging = pomReader.packaging
            metadata.isRelocated = false
        }
        return MetaDataParser.ParseResult.< MutableMavenModuleResolveMetadata > of < org . gradle . internal . component . external . model . maven . MutableMavenModuleResolveMetadata ? > (metadata, pomReader.hasGradleMetadataMarker())
    }

    @Throws(IOException::class, SAXException::class)
    private fun doParsePom(parserSettings: DescriptorParseContext, mdBuilder: GradlePomModuleDescriptorBuilder, pomReader: PomReader) {
        pomReader.resolveGAV()

        var groupId = pomReader.groupId
        var artifactId = pomReader.artifactId
        var version = pomReader.version

        if (pomReader.hasParent()) {
            //Is there any other parent properties?

            val parentGroupId = pomReader.parentGroupId
            val parentArtifactId = pomReader.parentArtifactId
            val parentVersion = pomReader.parentVersion

            if (!(parentGroupId == groupId && parentArtifactId == artifactId && parentVersion == version)) {
                // Only attempt loading the parent if it has different coordinates
                val parentId = DefaultModuleComponentSelector.newSelector(
                    DefaultModuleIdentifier.newId(parentGroupId, parentArtifactId),
                    DefaultImmutableVersionConstraint(parentVersion!!)
                )
                val parentPomReader = parsePomForSelector(parserSettings, parentId, pomReader.allPomProperties)
                pomReader.setPomParent(parentPomReader)

                // Current POM can derive version/artifactId from parent. Resolve GAV and substitute values
                pomReader.resolveGAV()
                groupId = pomReader.groupId
                artifactId = pomReader.artifactId
                version = pomReader.version
            }
        }
        mdBuilder.setModuleRevId(groupId, artifactId, version)

        val relocation: ModuleVersionIdentifier = pomReader.relocation
        if (relocation != null) {
            if (groupId != null && artifactId != null && artifactId == relocation.getName() && groupId == relocation.getGroup()) {
                LOGGER.error(
                    "POM relocation to an other version number is not fully supported in Gradle : {} relocated to {}.",
                    mdBuilder.getComponentIdentifier(), relocation
                )
                LOGGER.warn("Please update your dependency to directly use the correct version '{}'.", relocation)
                LOGGER.warn("Resolution will only pick dependencies of the relocated element.  Artifacts and other metadata will be ignored.")
                val relocatedModule = parsePomForId(parserSettings, newId(relocation), HashMap<String?, String?>())
                addDependencies(mdBuilder, relocatedModule)
            } else {
                LOGGER.info(
                    (mdBuilder.getComponentIdentifier()
                        .toString() + " is relocated to " + relocation
                            + ". Please update your dependencies.")
                )
                LOGGER.debug("Relocated module will be considered as a dependency")
                val selector = DefaultModuleComponentSelector.newSelector(
                    DefaultModuleIdentifier.newId(relocation.getGroup(), relocation.getName()), DefaultMutableVersionConstraint(relocation.getVersion())
                )
                mdBuilder.addDependencyForRelocation(selector)
            }
        } else {
            overrideDependencyMgtsWithImported(parserSettings, pomReader)
            addDependencies(mdBuilder, pomReader)
        }
    }

    private fun addDependencies(mdBuilder: GradlePomModuleDescriptorBuilder, pomReader: PomReader) {
        for (dependencyMgt in pomReader.getDependencyMgt().values) {
            if (!isDependencyImportScoped(dependencyMgt!!)) {
                mdBuilder.addConstraint(dependencyMgt)
            }
        }

        for (dependency in pomReader.getDependencies().values) {
            mdBuilder.addDependency(dependency)
        }
    }

    /**
     * Overrides existing dependency management information with imported ones if existing.
     *
     * @param parseContext Parse context
     * @param pomReader POM reader
     */
    @Throws(IOException::class, SAXException::class)
    private fun overrideDependencyMgtsWithImported(parseContext: DescriptorParseContext, pomReader: PomReader) {
        val importedDependencyMgts = parseImportedDependencyMgts(parseContext, pomReader.parseDependencyMgt())
        pomReader.addImportedDependencyMgts(importedDependencyMgts)
    }

    /**
     * Parses imported dependency management information.
     *
     * @param parseContext Parse context
     * @param currentDependencyMgts Current dependency management information
     * @return Imported dependency management information
     */
    @Throws(IOException::class, SAXException::class)
    private fun parseImportedDependencyMgts(parseContext: DescriptorParseContext, currentDependencyMgts: MutableCollection<PomDependencyMgt>): MutableMap<MavenDependencyKey?, PomDependencyMgt?> {
        val importedDependencyMgts: MutableMap<MavenDependencyKey?, PomDependencyMgt?> = LinkedHashMap<MavenDependencyKey?, PomDependencyMgt?>()

        for (currentDependencyMgt in currentDependencyMgts) {
            if (isDependencyImportScoped(currentDependencyMgt)) {
                val importedId = DefaultModuleComponentSelector.newSelector(
                    DefaultModuleIdentifier.newId(currentDependencyMgt.groupId, currentDependencyMgt.artifactId),
                    DefaultImmutableVersionConstraint(currentDependencyMgt.version)
                )
                val importedPom = parsePomForSelector(parseContext, importedId, HashMap<String?, String?>())
                for (entry in importedPom.getDependencyMgt().entries) {
                    if (!importedDependencyMgts.containsKey(entry.key)) {
                        importedDependencyMgts.put(entry.key, entry.value)
                    }
                }
            }
        }
        return importedDependencyMgts
    }

    /**
     * Checks if dependency has scope "import".
     *
     * @param dependencyMgt Dependency management element
     * @return Flag
     */
    private fun isDependencyImportScoped(dependencyMgt: PomDependencyMgt): Boolean {
        return DEPENDENCY_IMPORT_SCOPE == dependencyMgt.scope
    }

    @Throws(IOException::class, SAXException::class)
    private fun parsePomForId(parseContext: DescriptorParseContext, identifier: ModuleComponentIdentifier?, childProperties: MutableMap<String?, String?>): PomReader {
        return parsePomResource(parseContext, parseContext.getMetaDataArtifact(identifier, ArtifactType.MAVEN_POM), childProperties)
    }

    @Throws(IOException::class, SAXException::class)
    private fun parsePomForSelector(parseContext: DescriptorParseContext, selector: ModuleComponentSelector, childProperties: MutableMap<String?, String?>): PomReader {
        val acceptor = mavenVersionSelectorScheme.parseSelector(selector.getVersion())
        val localResource = parseContext.getMetaDataArtifact(selector, acceptor, ArtifactType.MAVEN_POM)
        return parsePomResource(parseContext, localResource, childProperties)
    }

    @Throws(SAXException::class, IOException::class)
    private fun parsePomResource(parseContext: DescriptorParseContext, localResource: LocallyAvailableExternalResource, childProperties: MutableMap<String?, String?>): PomReader {
        val pomReader = PomReader(localResource, moduleIdentifierFactory, childProperties)
        val mdBuilder = GradlePomModuleDescriptorBuilder(pomReader, gradleVersionSelectorScheme, mavenVersionSelectorScheme)
        doParsePom(parseContext, mdBuilder, pomReader)
        return pomReader
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(GradlePomModuleDescriptorParser::class.java)
        private const val DEPENDENCY_IMPORT_SCOPE = "import"
    }
}
