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
package org.gradle.api.internal.artifacts.transform

import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.InputArtifactDependencies
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.artifacts.TransformRegistration
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileLookup
import org.gradle.api.internal.tasks.properties.FileParameterUtils
import org.gradle.api.problems.internal.ProblemsInternal
import org.gradle.internal.execution.InputFingerprinter
import org.gradle.internal.execution.WorkValidationException
import org.gradle.internal.fingerprint.DirectorySensitivity
import org.gradle.internal.fingerprint.FileNormalizer
import org.gradle.internal.fingerprint.LineEndingSensitivity
import org.gradle.internal.hash.ClassLoaderHierarchyHasher
import org.gradle.internal.instantiation.InstantiationScheme
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.properties.InputBehavior
import org.gradle.internal.properties.InputFilePropertyType
import org.gradle.internal.properties.PropertyValue
import org.gradle.internal.properties.PropertyVisitor
import org.gradle.internal.properties.annotations.TypeMetadataStore
import org.gradle.internal.properties.bean.PropertyWalker
import org.gradle.internal.reflect.DefaultTypeValidationContext
import org.gradle.internal.service.ServiceLookup

class DefaultTransformRegistrationFactory(
    private val buildOperationRunner: BuildOperationRunner,
    private val isolatableFactory: IsolatableFactory,
    private val classLoaderHierarchyHasher: ClassLoaderHierarchyHasher,
    private val transformInvocationFactory: TransformInvocationFactory,
    private val fileCollectionFactory: FileCollectionFactory,
    private val fileLookup: FileLookup,
    private val inputFingerprinter: InputFingerprinter,
    private val calculatedValueContainerFactory: CalculatedValueContainerFactory,
    private val owner: DomainObjectContext,
    parameterScheme: TransformParameterScheme,
    actionScheme: TransformActionScheme,
    private val internalServices: ServiceLookup
) : TransformRegistrationFactory {
    private val parametersPropertyWalker: PropertyWalker
    private val actionMetadataStore: TypeMetadataStore
    private val actionInstantiationScheme: InstantiationScheme

    init {
        this.actionInstantiationScheme = actionScheme.getInstantiationScheme()
        this.actionMetadataStore = actionScheme.getInspectionScheme().getMetadataStore()
        this.parametersPropertyWalker = parameterScheme.getInspectionScheme().getPropertyWalker()
    }

    override fun create(from: ImmutableAttributes, to: ImmutableAttributes, implementation: Class<out TransformAction<*>>, parameterObject: TransformParameters): TransformRegistration {
        val actionMetadata = actionMetadataStore.getTypeMetadata(implementation)
        val cacheable = implementation.isAnnotationPresent(CacheableTransform::class.java)
        val problems = internalServices.get(ProblemsInternal::class.java) as ProblemsInternal?
        val validationContext = DefaultTypeValidationContext.withoutRootType(cacheable, problems!!)
        actionMetadata.visitValidationFailures(null, validationContext)

        // Should retain this on the metadata rather than calculate on each invocation
        var inputArtifactNormalizer: FileNormalizer? = null
        var dependenciesNormalizer: FileNormalizer? = null
        var artifactDirectorySensitivity = DirectorySensitivity.DEFAULT
        var dependenciesDirectorySensitivity = DirectorySensitivity.DEFAULT
        var artifactLineEndingSensitivity = LineEndingSensitivity.DEFAULT
        var dependenciesLineEndingSensitivity = LineEndingSensitivity.DEFAULT
        for (propertyMetadata in actionMetadata.getPropertiesMetadata()) {
            // Should ask the annotation handler to figure this out instead
            val propertyType = propertyMetadata.getPropertyType()
            val visitor = NormalizerCollectingVisitor()
            if (propertyType == InputArtifact::class.java) {
                actionMetadata.getAnnotationHandlerFor(propertyMetadata).visitPropertyValue(propertyMetadata.getPropertyName(), PropertyValue.ABSENT, propertyMetadata, visitor)
                inputArtifactNormalizer = visitor.normalizer
                artifactDirectorySensitivity = visitor.directorySensitivity
                artifactLineEndingSensitivity = visitor.lineEndingSensitivity
                DefaultTransform.Companion.validateInputFileNormalizer(propertyMetadata.getPropertyName(), inputArtifactNormalizer, cacheable, validationContext)
            } else if (propertyType == InputArtifactDependencies::class.java) {
                actionMetadata.getAnnotationHandlerFor(propertyMetadata).visitPropertyValue(propertyMetadata.getPropertyName(), PropertyValue.ABSENT, propertyMetadata, visitor)
                dependenciesNormalizer = visitor.normalizer
                dependenciesDirectorySensitivity = visitor.directorySensitivity
                dependenciesLineEndingSensitivity = visitor.lineEndingSensitivity
                DefaultTransform.Companion.validateInputFileNormalizer(propertyMetadata.getPropertyName(), dependenciesNormalizer, cacheable, validationContext)
            }
        }
        val errors = validationContext.getErrors()
        if (!errors.isEmpty()) {
            val exception = WorkValidationException.withSummaryForType(implementation, errors.size)
            throw problems.reporter!!.throwing(exception, errors)
        }
        val transform: Transform = DefaultTransform(
            implementation,
            parameterObject,
            from,
            to,
            FileParameterUtils.normalizerOrDefault(inputArtifactNormalizer),
            FileParameterUtils.normalizerOrDefault(dependenciesNormalizer),
            cacheable,
            artifactDirectorySensitivity,
            dependenciesDirectorySensitivity,
            artifactLineEndingSensitivity,
            dependenciesLineEndingSensitivity,
            buildOperationRunner,
            classLoaderHierarchyHasher,
            isolatableFactory,
            fileCollectionFactory,
            fileLookup,
            parametersPropertyWalker,
            actionInstantiationScheme,
            owner,
            calculatedValueContainerFactory,
            internalServices
        )

        return DefaultTransformRegistration(from, to, TransformStep(transform, transformInvocationFactory, owner, inputFingerprinter))
    }

    private class DefaultTransformRegistration(val from: ImmutableAttributes, val to: ImmutableAttributes, val transformStep: TransformStep) : TransformRegistration {
        override fun toString(): String {
            return transformStep.toString() + " transform from " + from + " to " + to
        }
    }

    private class NormalizerCollectingVisitor : PropertyVisitor {
        private var normalizer: FileNormalizer? = null
        private var directorySensitivity = DirectorySensitivity.DEFAULT
        private var lineEndingSensitivity = LineEndingSensitivity.DEFAULT

        override fun visitInputFileProperty(
            propertyName: String,
            optional: Boolean,
            behavior: InputBehavior,
            directorySensitivity: DirectorySensitivity,
            lineEndingSensitivity: LineEndingSensitivity,
            fileNormalizer: FileNormalizer?,
            value: PropertyValue,
            filePropertyType: InputFilePropertyType
        ) {
            this.normalizer = fileNormalizer
            this.directorySensitivity = directorySensitivity
            this.lineEndingSensitivity = lineEndingSensitivity
        }
    }
}
