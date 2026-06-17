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
package org.gradle.api.publish.maven.internal.tasks

import com.google.common.base.Joiner
import com.google.common.collect.Streams
import org.apache.maven.model.CiManagement
import org.apache.maven.model.Contributor
import org.apache.maven.model.Dependency
import org.apache.maven.model.DependencyManagement
import org.apache.maven.model.DeploymentRepository
import org.apache.maven.model.Developer
import org.apache.maven.model.DistributionManagement
import org.apache.maven.model.Exclusion
import org.apache.maven.model.IssueManagement
import org.apache.maven.model.License
import org.apache.maven.model.MailingList
import org.apache.maven.model.Model
import org.apache.maven.model.Organization
import org.apache.maven.model.Relocation
import org.apache.maven.model.Scm
import org.apache.maven.model.io.xpp3.MavenXpp3Writer
import org.gradle.api.Action
import org.gradle.api.XmlProvider
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser
import org.gradle.api.internal.lambdas.SerializableLambdas
import org.gradle.api.provider.Provider
import org.gradle.api.publish.maven.MavenPomCiManagement
import org.gradle.api.publish.maven.MavenPomContributor
import org.gradle.api.publish.maven.MavenPomDeploymentRepository
import org.gradle.api.publish.maven.MavenPomDeveloper
import org.gradle.api.publish.maven.MavenPomIssueManagement
import org.gradle.api.publish.maven.MavenPomLicense
import org.gradle.api.publish.maven.MavenPomMailingList
import org.gradle.api.publish.maven.MavenPomOrganization
import org.gradle.api.publish.maven.MavenPomRelocation
import org.gradle.api.publish.maven.MavenPomScm
import org.gradle.api.publish.maven.internal.dependencies.MavenDependency
import org.gradle.api.publish.maven.internal.dependencies.MavenPomDependencies
import org.gradle.api.publish.maven.internal.publication.MavenPomDistributionManagementInternal
import org.gradle.api.publish.maven.internal.publication.MavenPomInternal
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.xml.XmlTransformer
import org.gradle.util.internal.GUtil
import java.io.File
import java.io.IOException
import java.io.Writer
import java.util.Properties
import java.util.stream.Collectors
import java.util.stream.Stream

object MavenPomFileGenerator {
    private const val POM_FILE_ENCODING = "UTF-8"
    private const val POM_VERSION = "4.0.0"

    fun generateSpec(pom: MavenPomInternal): MavenPomSpec {
        val model = Model()
        model.setModelVersion(POM_VERSION)

        val coordinates = pom.getCoordinates()
        model.setGroupId(coordinates.getGroupId().get())
        model.setArtifactId(coordinates.getArtifactId().get())
        model.setVersion(coordinates.getVersion().get())

        model.setPackaging(pom.getPackagingProperty().getOrNull())
        model.setName(pom.getName().getOrNull())
        model.setDescription(pom.getDescription().getOrNull())
        model.setUrl(pom.getUrl().getOrNull())
        model.setInceptionYear(pom.getInceptionYear().getOrNull())

        if (pom.getOrganization() != null) {
            model.setOrganization(MavenPomFileGenerator.convertOrganization(pom.getOrganization()!!))
        }
        if (pom.getScm() != null) {
            model.setScm(MavenPomFileGenerator.convertScm(pom.getScm()!!))
        }
        if (pom.getIssueManagement() != null) {
            model.setIssueManagement(MavenPomFileGenerator.convertIssueManagement(pom.getIssueManagement()!!))
        }
        if (pom.getCiManagement() != null) {
            model.setCiManagement(MavenPomFileGenerator.convertCiManagement(pom.getCiManagement()!!))
        }
        if (pom.getDistributionManagement() != null) {
            model.setDistributionManagement(MavenPomFileGenerator.convertDistributionManagement(pom.getDistributionManagement()!!))
        }
        for (license in pom.getLicenses()) {
            model.addLicense(convertLicense(license))
        }
        for (developer in pom.getDevelopers()) {
            model.addDeveloper(convertDeveloper(developer))
        }
        for (contributor in pom.getContributors()) {
            model.addContributor(convertContributor(contributor))
        }
        for (mailingList in pom.getMailingLists()) {
            model.addMailingList(convertMailingList(mailingList))
        }
        for (property in pom.getProperties().get().entries) {
            model.addProperty(property.key, property.value)
        }

        val dependencies = pom.getDependencies().getOrNull()
        if (dependencies != null) {
            model.setDependencyManagement(convertDependencyManagement(dependencies))
            model.setDependencies(convertDependencies(dependencies))
        }

        val xmlTransformer = XmlTransformer()
        xmlTransformer.addAction(pom.getXmlAction())
        if (pom.getWriteGradleMetadataMarker().get()) {
            xmlTransformer.addFinalizer(SerializableLambdas.action<XmlProvider?>(SerializableLambdas.SerializableAction { obj: MavenPomFileGenerator?, xmlProvider: XmlProvider ->
                insertGradleMetadataMarker(
                    xmlProvider
                )
            }))
        }

        return MavenPomSpec(model, xmlTransformer)
    }

    private fun convertOrganization(source: MavenPomOrganization): Organization {
        val target = Organization()
        target.setName(source.getName().getOrNull())
        target.setUrl(source.getUrl().getOrNull())
        return target
    }

    private fun convertLicense(source: MavenPomLicense): License {
        val target = License()
        target.setName(source.getName().getOrNull())
        target.setUrl(source.getUrl().getOrNull())
        target.setDistribution(source.getDistribution().getOrNull())
        target.setComments(source.getComments().getOrNull())
        return target
    }

    private fun convertDeveloper(source: MavenPomDeveloper): Developer {
        val target = Developer()
        target.setId(source.getId().getOrNull())
        target.setName(source.getName().getOrNull())
        target.setEmail(source.getEmail().getOrNull())
        target.setUrl(source.getUrl().getOrNull())
        target.setOrganization(source.getOrganization().getOrNull())
        target.setOrganizationUrl(source.getOrganizationUrl().getOrNull())
        target.setRoles(ArrayList<String?>(source.getRoles().get()))
        target.setTimezone(source.getTimezone().getOrNull())
        target.setProperties(MavenPomFileGenerator.convertProperties(source.getProperties()))
        return target
    }

    private fun convertContributor(source: MavenPomContributor): Contributor {
        val target = Contributor()
        target.setName(source.getName().getOrNull())
        target.setEmail(source.getEmail().getOrNull())
        target.setUrl(source.getUrl().getOrNull())
        target.setOrganization(source.getOrganization().getOrNull())
        target.setOrganizationUrl(source.getOrganizationUrl().getOrNull())
        target.setRoles(ArrayList<String?>(source.getRoles().get()))
        target.setTimezone(source.getTimezone().getOrNull())
        target.setProperties(MavenPomFileGenerator.convertProperties(source.getProperties()))
        return target
    }

    private fun convertProperties(source: Provider<MutableMap<String?, String?>?>): Properties {
        val target = Properties()
        target.putAll(source.getOrElse(mutableMapOf<String?, String?>())!!)
        return target
    }

    private fun convertScm(source: MavenPomScm): Scm {
        val target = Scm()
        target.setConnection(source.getConnection().getOrNull())
        target.setDeveloperConnection(source.getDeveloperConnection().getOrNull())
        target.setUrl(source.getUrl().getOrNull())
        target.setTag(source.getTag().getOrNull())
        return target
    }

    private fun convertIssueManagement(source: MavenPomIssueManagement): IssueManagement {
        val target = IssueManagement()
        target.setSystem(source.getSystem().getOrNull())
        target.setUrl(source.getUrl().getOrNull())
        return target
    }

    private fun convertCiManagement(source: MavenPomCiManagement): CiManagement {
        val target = CiManagement()
        target.setSystem(source.getSystem().getOrNull())
        target.setUrl(source.getUrl().getOrNull())
        return target
    }

    private fun convertDistributionManagement(source: MavenPomDistributionManagementInternal): DistributionManagement {
        val target = DistributionManagement()
        target.setDownloadUrl(source.getDownloadUrl().getOrNull())
        if (source.getRelocation() != null) {
            target.setRelocation(MavenPomFileGenerator.convertRelocation(source.getRelocation()!!))
        }
        if (source.getRepository() != null) {
            target.setRepository(MavenPomFileGenerator.convertDeploymentRepository(source.getRepository()!!))
        }
        return target
    }

    private fun convertRelocation(source: MavenPomRelocation): Relocation {
        val target = Relocation()
        target.setGroupId(source.getGroupId().getOrNull())
        target.setArtifactId(source.getArtifactId().getOrNull())
        target.setVersion(source.getVersion().getOrNull())
        target.setMessage(source.getMessage().getOrNull())
        return target
    }

    private fun convertDeploymentRepository(source: MavenPomDeploymentRepository): DeploymentRepository {
        val target = DeploymentRepository()
        target.setId(source.getId().getOrNull())
        target.setName(source.getName().getOrNull())
        target.setUniqueVersion(source.getUniqueVersion().getOrElse(true))
        target.setUrl(source.getUrl().getOrNull())
        target.setLayout(source.getLayout().getOrElse("default"))
        return target
    }

    private fun convertMailingList(source: MavenPomMailingList): MailingList {
        val target = MailingList()
        target.setName(source.getName().getOrNull())
        target.setSubscribe(source.getSubscribe().getOrNull())
        target.setUnsubscribe(source.getUnsubscribe().getOrNull())
        target.setPost(source.getPost().getOrNull())
        target.setArchive(source.getArchive().getOrNull())
        target.setOtherArchives(ArrayList<String?>(source.getOtherArchives().get()))
        return target
    }

    private fun convertDependencyManagement(dependencies: MavenPomDependencies): DependencyManagement? {
        if (dependencies.getDependencyManagement().isEmpty()) {
            return null
        }

        val converted = dependencies.getDependencyManagement().stream()
            .map<Dependency?> { obj: MavenDependency? -> MavenPomFileGenerator.convertDependencyManagementDependency() }
            .collect(Collectors.toList())

        val dm = DependencyManagement()
        dm.setDependencies(converted)
        return dm
    }

    private fun convertDependencies(dependencies: MavenPomDependencies): MutableList<Dependency?> {
        return dependencies.getDependencies().stream()
            .map<Dependency?> { obj: MavenDependency? -> MavenPomFileGenerator.convertDependency() }
            .collect(Collectors.toList())
    }

    private fun convertDependency(dependency: MavenDependency): Dependency {
        val mavenDependency = Dependency()
        mavenDependency.setGroupId(dependency.getGroupId())
        mavenDependency.setArtifactId(dependency.getArtifactId())
        mavenDependency.setVersion(dependency.getVersion())
        mavenDependency.setType(dependency.getType())
        mavenDependency.setScope(dependency.getScope())
        mavenDependency.setClassifier(dependency.getClassifier())
        if (dependency.isOptional()) {
            // Not using setOptional(optional) in order to avoid <optional>false</optional> in the common case
            mavenDependency.setOptional(true)
        }

        for (excludeRule in dependency.getExcludeRules()) {
            val exclusion = Exclusion()
            exclusion.setGroupId(GUtil.elvis<String?>(excludeRule.getGroup(), "*"))
            exclusion.setArtifactId(GUtil.elvis<String?>(excludeRule.getModule(), "*"))
            mavenDependency.addExclusion(exclusion)
        }
        return mavenDependency
    }

    private fun convertDependencyManagementDependency(dependency: MavenDependency): Dependency {
        val mavenDependency = Dependency()
        mavenDependency.setGroupId(dependency.getGroupId())
        mavenDependency.setArtifactId(dependency.getArtifactId())
        mavenDependency.setVersion(dependency.getVersion())
        val type = dependency.getType()
        if (type != null) {
            mavenDependency.setType(type)
        }
        mavenDependency.setScope(dependency.getScope())
        return mavenDependency
    }

    private fun insertGradleMetadataMarker(xmlProvider: XmlProvider) {
        val comment: String = Joiner.on("").join(
            Streams.concat<String>(
                MetaDataParser.GRADLE_METADATA_MARKER_COMMENT_LINES.stream(),
                Stream.of<String>(MetaDataParser.GRADLE_6_METADATA_MARKER)
            )
                .map<String> { content: String? -> "<!-- " + content + " -->\n  " }
                .iterator()
        )

        val builder = xmlProvider.asString()
        val idx = builder.indexOf("<modelVersion")
        builder.insert(idx, comment)
    }

    class MavenPomSpec(private val model: Model?, private val xmlTransformer: XmlTransformer) {
        fun writeTo(file: File?) {
            xmlTransformer.transform(file, POM_FILE_ENCODING, Action { writer: Writer? ->
                try {
                    MavenXpp3Writer().write(writer, model)
                } catch (e: IOException) {
                    throw throwAsUncheckedException(e)
                }
            })
        }
    }
}
