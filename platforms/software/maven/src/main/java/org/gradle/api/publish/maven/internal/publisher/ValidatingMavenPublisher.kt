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
package org.gradle.api.publish.maven.internal.publisher

import org.apache.commons.lang3.ObjectUtils
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.codehaus.plexus.util.xml.pull.XmlPullParserException
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.publish.PublicationArtifact
import org.gradle.api.publish.internal.PublicationFieldValidator
import org.gradle.api.publish.maven.InvalidMavenPublicationException
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import java.io.File
import java.io.FileReader
import java.io.IOException

class ValidatingMavenPublisher(private val delegate: MavenPublisher) : MavenPublisher {
    override fun publish(publication: MavenNormalizedPublication, artifactRepository: MavenArtifactRepository?) {
        validateIdentity(publication)
        validateArtifacts(publication)
        checkNoDuplicateArtifacts(publication)

        delegate.publish(publication, artifactRepository)
    }

    private fun validateIdentity(publication: MavenNormalizedPublication) {
        val model = parsePomFileIntoMavenModel(publication)

        field(publication, "artifactId", publication.getArtifactId())
            .validMavenIdentifier()
            .matches(model.getArtifactId())

        val hasParentPom = model.getParent() != null
        val groupIdValidator = field(publication, "groupId", publication.getGroupId())
            .validMavenIdentifier()
        val versionValidator = field(publication, "version", publication.getVersion())
            .notEmpty()!!
            .validInFileName()

        if (!hasParentPom) {
            groupIdValidator.matches(model.getGroupId())
            versionValidator!!.matches(model.getVersion())
        }
    }

    private fun parsePomFileIntoMavenModel(publication: MavenNormalizedPublication): Model {
        val pomFile: File = publication.getPomArtifact().file
        try {
            val model = readModelFromPom(pomFile)
            model.setPomFile(pomFile)
            return model
        } catch (parseException: XmlPullParserException) {
            throw InvalidMavenPublicationException(
                publication.getName(),
                "POM file is invalid. Check any modifications you have made to the POM file.",
                parseException
            )
        } catch (ex: IOException) {
            throw throwAsUncheckedException(ex)
        }
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readModelFromPom(pomFile: File): Model {
        // Note: source files can have non-UTF8 encoding. FileReader uses default Charset and also handles invalid characters.
        FileReader(pomFile).use { reader ->
            return MavenXpp3Reader().read(reader)
        }
    }

    private fun validateArtifacts(publication: MavenNormalizedPublication) {
        for (artifact in publication.getAllArtifacts()) {
            field(publication, "artifact extension", artifact.getExtension())
                .notNull()!!
                .validInFileName()
            field(publication, "artifact classifier", artifact.getClassifier())
                .optionalNotEmpty()!!
                .validInFileName()

            checkCanPublish(publication.getName(), artifact)
        }
    }

    private fun checkNoDuplicateArtifacts(publication: MavenNormalizedPublication) {
        val verified: MutableSet<MavenArtifact> = HashSet<MavenArtifact>()
        for (artifact in publication.getAllArtifacts()) {
            checkNotDuplicate(publication, verified, artifact.getExtension(), artifact.getClassifier())
            verified.add(artifact)
        }
    }

    private fun checkNotDuplicate(publication: MavenNormalizedPublication, artifacts: MutableSet<MavenArtifact>, extension: String?, classifier: String?) {
        for (artifact in artifacts) {
            if (ObjectUtils.equals(artifact.getExtension(), extension) && ObjectUtils.equals(artifact.getClassifier(), classifier)) {
                val message = String.format(
                    "multiple artifacts with the identical extension and classifier ('%s', '%s').", extension, classifier
                )
                throw InvalidMavenPublicationException(publication.getName(), message)
            }
        }
    }

    private fun checkCanPublish(publicationName: String?, artifact: PublicationArtifact) {
        val artifactFile: File = artifact.file
        if (artifactFile == null || !artifactFile.exists()) {
            throw InvalidMavenPublicationException(publicationName, String.format("artifact file does not exist: '%s'", artifactFile))
        }
        if (artifactFile.isDirectory()) {
            throw InvalidMavenPublicationException(publicationName, String.format("artifact file is a directory: '%s'", artifactFile))
        }
    }

    private fun field(publication: MavenNormalizedPublication, name: String?, value: String?): MavenFieldValidator {
        return MavenFieldValidator(publication.getName(), name, value)
    }

    private class MavenFieldValidator(publicationName: String?, name: String?, value: String?) :
        PublicationFieldValidator<MavenFieldValidator?>(MavenFieldValidator::class.java, publicationName, name, value) {
        fun validMavenIdentifier(): MavenFieldValidator {
            notEmpty()
            if (!value!!.matches(ID_REGEX.toRegex())) {
                throw failure(String.format("%s (%s) is not a valid Maven identifier (%s).", name, value, ID_REGEX))
            }
            return this
        }

        fun matches(valueFromPomFile: String?): MavenFieldValidator {
            if (value != valueFromPomFile) {
                throw failure(String.format("supplied %s (%s) does not match value from POM file (%s). Cannot edit %1\$s directly in the POM file.", name, value, valueFromPomFile))
            }
            return this
        }

        override fun failure(message: String?): InvalidMavenPublicationException {
            return InvalidMavenPublicationException(publicationName, message)
        }
    }

    companion object {
        private const val ID_REGEX = "[A-Za-z0-9_\\-.]+"
    }
}
