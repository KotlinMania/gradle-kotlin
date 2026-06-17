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
package org.gradle.tooling.events.transform.internal

import org.gradle.tooling.events.OperationDescriptor
import org.gradle.tooling.events.internal.DefaultOperationDescriptor
import org.gradle.tooling.events.transform.TransformOperationDescriptor
import org.gradle.tooling.internal.protocol.events.InternalTransformDescriptor

class DefaultTransformOperationDescriptor(descriptor: InternalTransformDescriptor, parent: OperationDescriptor?, private val dependencies: MutableSet<OperationDescriptor?>?) :
    DefaultOperationDescriptor(descriptor, parent), TransformOperationDescriptor {
    private val transformer: TransformOperationDescriptor.TransformerDescriptor
    private val subject: TransformOperationDescriptor.SubjectDescriptor

    init {
        this.transformer = DefaultTransformerDescriptor(descriptor.transformerName)
        this.subject = DefaultSubjectDescriptor(descriptor.subjectName)
    }

    override fun getTransformer(): TransformOperationDescriptor.TransformerDescriptor {
        return transformer
    }

    override fun getSubject(): TransformOperationDescriptor.SubjectDescriptor {
        return subject
    }

    override fun getDependencies(): MutableSet<out OperationDescriptor?>? {
        return dependencies
    }

    private class DefaultTransformerDescriptor(private val displayName: String?) : TransformOperationDescriptor.TransformerDescriptor {
        override fun getDisplayName(): String? {
            return displayName
        }
    }

    private class DefaultSubjectDescriptor(private val displayName: String?) : TransformOperationDescriptor.SubjectDescriptor {
        override fun getDisplayName(): String? {
            return displayName
        }
    }
}
