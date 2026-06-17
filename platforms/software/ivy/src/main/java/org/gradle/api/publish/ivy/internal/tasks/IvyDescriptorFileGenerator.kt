/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.api.publish.ivy.internal.tasks

import com.google.common.base.Joiner
import com.google.common.collect.Streams
import org.gradle.api.Action
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.DependencyArtifact
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceResolver.publish
import org.gradle.api.internal.lambdas.SerializableLambdas
import org.gradle.api.publish.ivy.IvyConfiguration
import org.gradle.api.publish.ivy.IvyModuleDescriptorAuthor
import org.gradle.api.publish.ivy.IvyModuleDescriptorDescription
import org.gradle.api.publish.ivy.IvyModuleDescriptorLicense
import org.gradle.api.publish.ivy.internal.artifact.IvyArtifactInternal
import org.gradle.api.publish.ivy.internal.artifact.NormalizedIvyArtifact
import org.gradle.api.publish.ivy.internal.dependency.IvyDependency
import org.gradle.api.publish.ivy.internal.dependency.IvyExcludeRule
import org.gradle.api.publish.ivy.internal.publication.IvyModuleDescriptorSpecInternal
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.xml.SimpleXmlWriter
import org.gradle.internal.xml.XmlTransformer
import org.gradle.util.internal.CollectionUtils
import java.io.File
import java.io.IOException
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.stream.Stream
import javax.xml.namespace.QName

object IvyDescriptorFileGenerator {
    private const val IVY_FILE_ENCODING = "UTF-8"
    private const val IVY_DATE_PATTERN = "yyyyMMddHHmmss"

    fun generateSpec(descriptor: IvyModuleDescriptorSpecInternal): DescriptorFileSpec {
        val model = Model()

        val coordinates = descriptor.getCoordinates()
        model.organisation = coordinates.getOrganisation().get()
        model.module = coordinates.getModule().get()
        model.revision = coordinates.getRevision().get()

        model.status = descriptor.getStatus()
        model.branch = descriptor.getBranch()
        model.extraInfo = descriptor.getExtraInfo().asMap()
        model.description = descriptor.getDescription()

        model.authors.addAll(descriptor.getAuthors())
        model.licenses.addAll(descriptor.getLicenses())
        model.configurations.addAll(descriptor.getConfigurations().get())

        val globalExcludes = descriptor.getGlobalExcludes().getOrNull()
        if (globalExcludes != null) {
            model.globalExcludes.addAll(globalExcludes)
        }

        val dependencies = descriptor.getDependencies().getOrNull()
        if (dependencies != null) {
            model.dependencies.addAll(dependencies)
        }

        for (ivyArtifact in descriptor.getArtifacts().get()) {
            model.artifacts.add((ivyArtifact as IvyArtifactInternal).asNormalisedArtifact())
        }

        val xmlTransformer = XmlTransformer()
        xmlTransformer.addAction(descriptor.getXmlAction())
        if (descriptor.getWriteGradleMetadataMarker().get()) {
            xmlTransformer.addFinalizer(SerializableLambdas.action<XmlProvider>(SerializableLambdas.SerializableAction { obj: IvyDescriptorFileGenerator?, xmlProvider: XmlProvider ->
                insertGradleMetadataMarker(
                    xmlProvider
                )
            }))
        }

        return DescriptorFileSpec(model, xmlTransformer)
    }

    fun insertGradleMetadataMarker(xmlProvider: XmlProvider) {
        val comment: String = Joiner.on("").join(
            Streams.concat<String>(
                MetaDataParser.GRADLE_METADATA_MARKER_COMMENT_LINES.stream(),
                Stream.of<String>(MetaDataParser.GRADLE_6_METADATA_MARKER)
            )
                .map<String> { content: String? -> "<!-- " + content + " -->\n  " }
                .iterator()
        )

        val builder = xmlProvider.asString()
        val idx = builder.indexOf("<info")
        builder.insert(idx, comment)
    }

    private class Model {
        private var branch: String? = null
        private var status: String? = null
        private var organisation: String? = null
        private var module: String? = null
        private var revision: String? = null
        private val licenses: MutableList<IvyModuleDescriptorLicense> = ArrayList<IvyModuleDescriptorLicense>()
        private val authors: MutableList<IvyModuleDescriptorAuthor> = ArrayList<IvyModuleDescriptorAuthor>()
        private var description: IvyModuleDescriptorDescription? = null
        private var extraInfo: MutableMap<QName, String>? = null
        private val configurations: MutableList<IvyConfiguration> = ArrayList<IvyConfiguration>()
        private val artifacts: MutableList<NormalizedIvyArtifact> = ArrayList<NormalizedIvyArtifact>()
        private val dependencies: MutableList<IvyDependency> = ArrayList<IvyDependency>()
        private val globalExcludes: MutableList<IvyExcludeRule> = ArrayList<IvyExcludeRule>()
    }

    class ModelWriter(private val model: Model) {
        private val ivyDateFormat = SimpleDateFormat(IVY_DATE_PATTERN)

        @Throws(IOException::class)
        private fun writeDescriptor(writer: Writer) {
            val xmlWriter = OptionalAttributeXmlWriter(writer, "  ", IVY_FILE_ENCODING)
            xmlWriter.startElement("ivy-module").attribute("version", "2.0")
            if (usesClassifier(model)) {
                xmlWriter.attribute("xmlns:m", "http://ant.apache.org/ivy/maven")
            }

            xmlWriter.startElement("info")
                .attribute("organisation", model.organisation)
                .attribute("module", model.module)
                .attribute("branch", model.branch)
                .attribute("revision", model.revision)
                .attribute("status", model.status)
                .attribute("publication", ivyDateFormat.format(Date()))

            for (license in model.licenses) {
                xmlWriter.startElement("license")
                    .attribute("name", license.name.getOrNull())
                    .attribute("url", license.getUrl().getOrNull())
                    .endElement()
            }

            for (author in model.authors) {
                xmlWriter.startElement("ivyauthor")
                    .attribute("name", author.name.getOrNull())
                    .attribute("url", author.getUrl().getOrNull())
                    .endElement()
            }

            if (model.description != null) {
                xmlWriter.startElement("description")
                    .attribute("homepage", model.description!!.homepage.getOrNull())
                    .characters(model.description.getText().getOrElse(""))
                    .endElement()
            }

            if (model.extraInfo != null) {
                for (entry in model.extraInfo!!.entries) {
                    if (entry.key != null) {
                        xmlWriter.startElement("ns:" + entry.key.getLocalPart())
                            .attribute("xmlns:ns", entry.key.getNamespaceURI())
                            .characters(entry.value)
                            .endElement()
                    }
                }
            }

            xmlWriter.endElement()

            writeConfigurations(xmlWriter)
            writePublications(xmlWriter)
            writeDependencies(xmlWriter)
            xmlWriter.endElement()
        }

        @Throws(IOException::class)
        private fun writeConfigurations(xmlWriter: OptionalAttributeXmlWriter) {
            xmlWriter.startElement("configurations")
            for (configuration in model.configurations) {
                xmlWriter.startElement("conf")
                    .attribute("name", configuration.getName())
                    .attribute("visibility", "public")
                if (configuration.getExtends().size() > 0) {
                    xmlWriter.attribute("extends", CollectionUtils.join(",", configuration.getExtends()))
                }
                xmlWriter.endElement()
            }
            xmlWriter.endElement()
        }

        @Throws(IOException::class)
        private fun writePublications(xmlWriter: OptionalAttributeXmlWriter) {
            xmlWriter.startElement("publications")
            for (artifact in model.artifacts) {
                xmlWriter.startElement("artifact")
                    .attribute("name", artifact.name)
                    .attribute("type", artifact.type)
                    .attribute("ext", artifact.extension)
                    .attribute("conf", artifact.getConf())
                    .attribute("m:classifier", artifact.getClassifier())
                    .endElement()
            }
            xmlWriter.endElement()
        }

        @Throws(IOException::class)
        private fun writeDependencies(xmlWriter: OptionalAttributeXmlWriter) {
            xmlWriter.startElement("dependencies")
            for (dependency in model.dependencies) {
                val org = dependency.getOrganisation()
                val module = dependency.getModule()

                xmlWriter.startElement("dependency")
                    .attribute("org", org)
                    .attribute("name", module)
                    .attribute("rev", dependency.getRevision())
                    .attribute("conf", dependency.getConfMapping())
                    .attribute("revConstraint", dependency.getRevConstraint())

                if (!dependency.isTransitive()) {
                    xmlWriter.attribute("transitive", "false")
                }

                for (dependencyArtifact in dependency.getArtifacts()) {
                    org.gradle.api.publish.ivy.internal.tasks.IvyDescriptorFileGenerator.ModelWriter.Companion.writeDependencyArtifact(dependencyArtifact, xmlWriter)
                }
                for (excludeRule in dependency.getExcludeRules()) {
                    org.gradle.api.publish.ivy.internal.tasks.IvyDescriptorFileGenerator.ModelWriter.Companion.writeDependencyExclude(excludeRule, xmlWriter)
                }
                xmlWriter.endElement()
            }
            for (excludeRule in model.globalExcludes) {
                writeGlobalExclude(excludeRule, xmlWriter)
            }
            xmlWriter.endElement()
        }

        private class OptionalAttributeXmlWriter(writer: Writer, indent: String, encoding: String) : SimpleXmlWriter(writer, indent, encoding) {
            @Throws(IOException::class)
            override fun startElement(name: String): OptionalAttributeXmlWriter {
                super.startElement(name)
                return this
            }

            @Throws(IOException::class)
            override fun attribute(name: String, value: String?): OptionalAttributeXmlWriter {
                if (value != null) {
                    super.attribute(name, value)
                }
                return this
            }

            @Throws(IOException::class)
            override fun comment(comment: String): OptionalAttributeXmlWriter {
                super.comment(comment)
                return this
            }
        }

        companion object {
            @Throws(IOException::class)
            private fun writeDependencyExclude(excludeRule: ExcludeRule, xmlWriter: OptionalAttributeXmlWriter) {
                xmlWriter.startElement("exclude")
                    .attribute("org", excludeRule.getGroup())
                    .attribute("module", excludeRule.getModule())
                    .endElement()
            }

            @Throws(IOException::class)
            private fun writeDependencyArtifact(dependencyArtifact: DependencyArtifact, xmlWriter: OptionalAttributeXmlWriter) {
                // TODO Use IvyArtifact here
                xmlWriter.startElement("artifact")
                    .attribute("name", dependencyArtifact.getName())
                    .attribute("type", dependencyArtifact.getType())
                    .attribute("ext", dependencyArtifact.getExtension())
                    .attribute("m:classifier", dependencyArtifact.getClassifier())
                    .endElement()
            }

            @Throws(IOException::class)
            private fun writeGlobalExclude(excludeRule: IvyExcludeRule, xmlWriter: OptionalAttributeXmlWriter) {
                xmlWriter.startElement("exclude")
                    .attribute("org", excludeRule.getOrg())
                    .attribute("module", excludeRule.getModule())
                    .attribute("conf", excludeRule.getConf())
                    .endElement()
            }

            private fun usesClassifier(model: Model): Boolean {
                for (artifact in model.artifacts) {
                    if (artifact.getClassifier() != null) {
                        return true
                    }
                }
                for (dependency in model.dependencies) {
                    for (dependencyArtifact in dependency.getArtifacts()) {
                        if (dependencyArtifact.getClassifier() != null) {
                            return true
                        }
                    }
                }
                return false
            }
        }
    }

    class DescriptorFileSpec(private val model: Model, private val xmlTransformer: XmlTransformer) {
        fun writeTo(destination: File) {
            xmlTransformer.transform(destination, IVY_FILE_ENCODING, Action { writer: Writer? ->
                try {
                    ModelWriter(model).writeDescriptor(writer)
                } catch (e: IOException) {
                    throw throwAsUncheckedException(e)
                }
            })
        }
    }
}
