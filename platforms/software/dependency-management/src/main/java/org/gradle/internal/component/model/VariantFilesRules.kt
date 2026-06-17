/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.internal.component.model

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.MutableVariantFilesMetadata
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.repositories.resolver.DefaultMutableVariantFilesMetadata
import org.gradle.internal.Cast.uncheckedNonnullCast
import org.gradle.internal.component.external.model.AbstractMutableModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.ComponentVariant
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetadata
import org.gradle.internal.component.external.model.UrlBackedArtifactMetadata
import org.gradle.internal.component.external.model.VariantMetadataRules
import java.util.LinkedList

class VariantFilesRules {
    private val actions: MutableList<VariantMetadataRules.VariantAction<in MutableVariantFilesMetadata>> = LinkedList<VariantMetadataRules.VariantAction<in MutableVariantFilesMetadata>>()

    fun addFilesAction(action: VariantMetadataRules.VariantAction<in MutableVariantFilesMetadata>) {
        actions.add(action)
    }

    fun <T : ComponentVariant.File?> executeForFiles(variant: VariantResolveMetadata, declaredFiles: ImmutableList<T?>, componentIdentifier: ModuleComponentIdentifier): ImmutableList<T?> {
        val filesMetadata = execute(variant)
        if (filesMetadata.getFiles().isEmpty()) {
            return declaredFiles
        }
        val builder = ImmutableList.Builder<T?>()
        if (!filesMetadata.isClearExistingFiles()) {
            builder.addAll(declaredFiles)
        }
        for (file in filesMetadata.getFiles()) {
            builder.add(uncheckedNonnullCast<T?>(AbstractMutableModuleComponentResolveMetadata.FileImpl(file.getName(), file.getUrl())))
        }
        return builder.build()
    }

    fun <T : ComponentArtifactMetadata?> executeForArtifacts(variant: VariantResolveMetadata, artifacts: ImmutableList<T?>, componentIdentifier: ModuleComponentIdentifier): ImmutableList<T?> {
        val filesMetadata = execute(variant)
        if (filesMetadata.getFiles().isEmpty()) {
            return artifacts
        }
        val builder = ImmutableList.Builder<T?>()
        if (!filesMetadata.isClearExistingFiles()) {
            for (existingArtifact in artifacts) {
                if (isFilePathUnambiguous<T?>(existingArtifact)) {
                    builder.add(existingArtifact)
                }
            }
        }
        for (file in filesMetadata.getFiles()) {
            builder.add(uncheckedNonnullCast<T?>(UrlBackedArtifactMetadata(componentIdentifier, file.getName(), file.getUrl())))
        }
        return builder.build()
    }

    private fun execute(variant: VariantResolveMetadata): DefaultMutableVariantFilesMetadata {
        val filesMetadata = DefaultMutableVariantFilesMetadata()
        for (action in actions) {
            action.maybeExecute(variant, filesMetadata)
        }
        return filesMetadata
    }

    /**
     * If the artifact was sourced from pom metadata using the 'packaging' attribute in the pom,
     * we don't know if the extension of the artifact is the one indicated by the packaging or 'jar'.
     * So we remove the artifact such that the user can explicitly add it in the rule.
     */
    private fun <T : ComponentArtifactMetadata?> isFilePathUnambiguous(existingArtifact: T?): Boolean {
        return existingArtifact !is DefaultModuleComponentArtifactMetadata || "jar" == existingArtifact.getName().getExtension()
    }
}
