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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact

import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.internal.DisplayName
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.model.VariantIdentifier
import java.io.File

class ArtifactVisitorToResolvedFileVisitorAdapter(private val visitor: ResolvedFileVisitor) : ArtifactVisitor {
    override fun prepareForVisit(source: FileCollectionInternal.Source): FileCollectionStructureVisitor.VisitType {
        return visitor.prepareForVisit(source)
    }

    fun visitFile(file: File) {
        visitor.visitFile(file)
    }

    override fun visitArtifact(
        artifactSetName: DisplayName?,
        sourceVariantId: VariantIdentifier?,
        attributes: ImmutableAttributes?,
        capabilities: ImmutableCapabilities?,
        artifact: ResolvableArtifact
    ) {
        visitor.visitFile(artifact.file!!)
    }

    override fun visitFailure(failure: Throwable) {
        visitor.visitFailure(failure)
    }

    override fun endVisitCollection(source: FileCollectionInternal.Source) {
        visitor.endVisitCollection(source)
    }

    override fun requireArtifactFiles(): Boolean {
        return true
    }
}
