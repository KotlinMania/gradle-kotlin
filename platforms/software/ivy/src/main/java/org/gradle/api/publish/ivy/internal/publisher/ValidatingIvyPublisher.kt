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
package org.gradle.api.publish.ivy.internal.publisher

import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.DescriptorParseContext
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.DisconnectedDescriptorParseContext
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.DisconnectedIvyXmlModuleDescriptorParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.IvyModuleDescriptorConverter
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParseException
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory
import org.gradle.api.publish.internal.PublicationFieldValidator
import org.gradle.api.publish.ivy.InvalidIvyPublicationException
import org.gradle.api.publish.ivy.IvyArtifact
import org.gradle.internal.component.external.model.ivy.MutableIvyModuleResolveMetadata
import org.gradle.internal.resource.local.FileResourceRepository
import java.io.File

class ValidatingIvyPublisher(
    private val delegate: IvyPublisher,
    moduleIdentifierFactory: ImmutableModuleIdentifierFactory,
    fileResourceRepository: FileResourceRepository?,
    metadataFactory: IvyMutableModuleMetadataFactory?
) : IvyPublisher {
    private val parserSettings: DescriptorParseContext = DisconnectedDescriptorParseContext()
    private val moduleDescriptorParser: DisconnectedIvyXmlModuleDescriptorParser

    init {
        moduleDescriptorParser = DisconnectedIvyXmlModuleDescriptorParser(IvyModuleDescriptorConverter(moduleIdentifierFactory), moduleIdentifierFactory, fileResourceRepository, metadataFactory)
    }

    override fun publish(publication: IvyNormalizedPublication, repository: IvyArtifactRepository?) {
        validateMetadata(publication)
        validateArtifacts(publication)
        checkNoDuplicateArtifacts(publication)
        delegate.publish(publication, repository)
    }

    private fun validateMetadata(publication: IvyNormalizedPublication) {
        val identity = publication.getCoordinates()

        val organisation = field(publication, "organisation", identity.getGroup())
            .notEmpty()!!
            .validInFileName()
        val moduleName = field(publication, "module name", identity.getName())
            .notEmpty()!!
            .validInFileName()
        val revision = field(publication, "revision", identity.getVersion())
            .notEmpty()!!
            .validInFileName()

        val metadata = parseIvyFile(publication)
        val moduleId: ModuleVersionIdentifier = metadata.moduleVersionId
        organisation!!.matches(moduleId.getGroup())
        moduleName!!.matches(moduleId.getName())
        revision!!.matches(moduleId.getVersion())

        field(publication, "branch", metadata.branch)
            .optionalNotEmpty()!!
            .doesNotContainSpecialCharacters(true)

        field(publication, "status", metadata.status)
            .optionalNotEmpty()!!
            .validInFileName()
    }

    private fun parseIvyFile(publication: IvyNormalizedPublication): MutableIvyModuleResolveMetadata {
        try {
            val parseResult = moduleDescriptorParser.parseMetaData(parserSettings, publication.getIvyDescriptorFile(), true)
            return parseResult!!.result!!
        } catch (pe: MetaDataParseException) {
            throw InvalidIvyPublicationException(publication.getName(), pe.getLocalizedMessage(), pe)
        }
    }

    private fun validateArtifacts(publication: IvyNormalizedPublication) {
        for (artifact in publication.getAllArtifacts()) {
            field(publication, "artifact name", artifact.getName())
                .notEmpty()!!.validInFileName()
            field(publication, "artifact type", artifact.getType())
                .notEmpty()!!.validInFileName()
            field(publication, "artifact extension", artifact.getExtension())
                .notNull()!!.validInFileName()
            field(publication, "artifact classifier", artifact.getClassifier())
                .optionalNotEmpty()!!.validInFileName()

            checkCanPublish(publication.getName(), artifact)
        }
    }

    private fun checkNoDuplicateArtifacts(publication: IvyNormalizedPublication) {
        val verified: MutableSet<IvyArtifact> = HashSet<IvyArtifact>()

        for (artifact in publication.getAllArtifacts()) {
            checkNotDuplicate(publication, verified, artifact.getName(), artifact.getExtension(), artifact.getType(), artifact.getClassifier())
            verified.add(artifact)
        }

        // Check that ivy.xml isn't duplicated
        checkNotDuplicate(publication, verified, "ivy", "xml", "xml", null)
    }

    private fun checkNotDuplicate(publication: IvyNormalizedPublication, verified: MutableSet<IvyArtifact>, name: String?, extension: String?, type: String?, classifier: String?) {
        for (alreadyVerified in verified) {
            if (hasCoordinates(alreadyVerified, name, extension, type, classifier)) {
                val message = String.format(
                    "multiple artifacts with the identical name, extension, type and classifier ('%s', %s', '%s', '%s').",
                    name, extension, type, classifier
                )
                throw InvalidIvyPublicationException(publication.getName(), message)
            }
        }
    }

    private fun hasCoordinates(one: IvyArtifact, name: String?, extension: String?, type: String?, classifier: String?): Boolean {
        return one.getName() == name
                && one.getType() == type
                && one.getExtension() == extension
                && one.getClassifier() == classifier
    }

    private fun checkCanPublish(name: String?, artifact: IvyArtifact) {
        val artifactFile: File = artifact.file
        if (artifactFile.isDirectory()) {
            throw InvalidIvyPublicationException(name, String.format("artifact file is a directory: '%s'", artifactFile))
        }
    }

    private fun field(publication: IvyNormalizedPublication, name: String?, value: String?): IvyFieldValidator {
        return IvyFieldValidator(publication.getName(), name, value)
    }

    private class IvyFieldValidator(publicationName: String?, name: String?, value: String?) :
        PublicationFieldValidator<IvyFieldValidator?>(IvyFieldValidator::class.java, publicationName, name, value) {
        fun matches(expectedValue: String?): IvyFieldValidator {
            if (value != expectedValue) {
                throw InvalidIvyPublicationException(
                    publicationName,
                    String.format("supplied %s does not match ivy descriptor (cannot edit %1\$s directly in the ivy descriptor file).", name)
                )
            }
            return this
        }

        override fun failure(message: String?): InvalidUserDataException? {
            throw InvalidIvyPublicationException(publicationName, message)
        }
    }
}
