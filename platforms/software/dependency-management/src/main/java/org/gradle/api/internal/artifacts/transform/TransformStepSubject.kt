/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.artifacts.transform

import com.google.common.collect.ImmutableList
import org.gradle.api.Describable
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import java.io.File

/**
 * Transform subject is either an initial artifact for the transform chain or a result of a previous transform step.
 */
abstract class TransformStepSubject : Describable {
    /**
     * The files which should be transformed.
     */
    abstract val files: ImmutableList<File>?

    /**
     * Component identifier of the initial subject.
     */
    abstract val initialComponentIdentifier: ComponentIdentifier

    /**
     * Creates a subsequent subject by having transformed this subject.
     */
    fun createSubjectFromResult(result: ImmutableList<File>): TransformStepSubject {
        return Transformed(this, result)
    }

    private class Initial(private val artifact: ResolvableArtifact) : TransformStepSubject() {
        override fun getFiles(): ImmutableList<File> {
            return ImmutableList.of<File>(artifact.file)
        }

        override fun getDisplayName(): String {
            return artifact.id.getDisplayName()
        }

        override fun getInitialComponentIdentifier(): ComponentIdentifier {
            return artifact.id.getComponentIdentifier()
        }
    }

    private class Transformed(private val previous: TransformStepSubject, private val files: ImmutableList<File>) : TransformStepSubject() {
        override fun getFiles(): ImmutableList<File> {
            return files
        }

        override fun getInitialComponentIdentifier(): ComponentIdentifier {
            return previous.initialComponentIdentifier
        }

        override fun getDisplayName(): String {
            return previous.getDisplayName()
        }

        override fun toString(): String {
            return getDisplayName()
        }
    }

    companion object {
        fun initial(artifact: ResolvableArtifact): TransformStepSubject {
            return Initial(artifact)
        }
    }
}
