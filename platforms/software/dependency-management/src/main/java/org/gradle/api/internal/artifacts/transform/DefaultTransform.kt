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

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSortedMap
import com.google.common.reflect.TypeToken
import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.InputArtifactDependencies
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.api.internal.file.FileLookup
import org.gradle.api.internal.plugins.DslObject
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.NodeExecutionContext
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.internal.tasks.properties.AbstractValidatingProperty
import org.gradle.api.internal.tasks.properties.FileParameterUtils
import org.gradle.api.internal.tasks.properties.InputParameterUtils
import org.gradle.api.problems.internal.ProblemsInternal
import org.gradle.api.provider.Provider
import org.gradle.api.reflect.InjectionPointQualifier
import org.gradle.internal.Describables
import org.gradle.internal.execution.InputFingerprinter
import org.gradle.internal.execution.InputVisitor
import org.gradle.internal.execution.WorkValidationException
import org.gradle.internal.execution.model.InputNormalizer
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.fingerprint.DirectorySensitivity
import org.gradle.internal.fingerprint.FileCollectionFingerprint
import org.gradle.internal.fingerprint.FileNormalizer
import org.gradle.internal.fingerprint.LineEndingSensitivity
import org.gradle.internal.hash.ClassLoaderHierarchyHasher
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hasher
import org.gradle.internal.hash.Hashing
import org.gradle.internal.instantiation.InstanceFactory
import org.gradle.internal.instantiation.InstantiationScheme
import org.gradle.internal.isolated.IsolationScheme
import org.gradle.internal.isolation.Isolatable
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.logging.text.TreeFormatter
import org.gradle.internal.model.CalculatedValueContainer
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.model.ValueCalculator
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.operations.BuildOperationType
import org.gradle.internal.operations.RunnableBuildOperation
import org.gradle.internal.properties.InputBehavior
import org.gradle.internal.properties.InputFilePropertyType
import org.gradle.internal.properties.OutputFilePropertyType
import org.gradle.internal.properties.PropertyValue
import org.gradle.internal.properties.PropertyVisitor
import org.gradle.internal.properties.bean.PropertyWalker
import org.gradle.internal.reflect.DefaultTypeValidationContext
import org.gradle.internal.reflect.validation.TypeAwareProblemBuilder
import org.gradle.internal.reflect.validation.TypeValidationContext
import org.gradle.internal.service.ServiceLookup
import org.gradle.internal.service.ServiceLookupException
import org.gradle.internal.service.UnknownServiceException
import org.gradle.internal.snapshot.ValueSnapshot
import org.gradle.model.internal.type.ModelType
import org.gradle.work.InputChanges
import java.io.File
import java.lang.reflect.Type
import java.util.function.Consumer
import java.util.function.Supplier

class DefaultTransform : Transform {
    private val implementationClass: Class<out TransformAction<*>>
    private val fromAttributes: ImmutableAttributes
    private val toAttributes: ImmutableAttributes
    private val fileNormalizer: FileNormalizer
    private val dependenciesNormalizer: FileNormalizer
    private val fileLookup: FileLookup
    private val internalServices: ServiceLookup
    private val requiresDependencies: Boolean
    private val requiresInputChanges: Boolean
    private val instanceFactory: InstanceFactory<out TransformAction<*>>
    private val cacheable: Boolean
    val isolatedParameters: CalculatedValueContainer<IsolatedParameters, IsolateTransformParameters>
    private val artifactDirectorySensitivity: DirectorySensitivity
    private val dependenciesDirectorySensitivity: DirectorySensitivity
    private val artifactLineEndingSensitivity: LineEndingSensitivity
    private val dependenciesLineEndingSensitivity: LineEndingSensitivity

    constructor(
        implementationClass: Class<out TransformAction<*>>,
        parameterObject: TransformParameters,
        fromAttributes: ImmutableAttributes,
        toAttributes: ImmutableAttributes,
        inputArtifactNormalizer: FileNormalizer,
        dependenciesNormalizer: FileNormalizer,
        cacheable: Boolean,
        artifactDirectorySensitivity: DirectorySensitivity,
        dependenciesDirectorySensitivity: DirectorySensitivity,
        artifactLineEndingSensitivity: LineEndingSensitivity,
        dependenciesLineEndingSensitivity: LineEndingSensitivity,
        buildOperationRunner: BuildOperationRunner,
        classLoaderHierarchyHasher: ClassLoaderHierarchyHasher,
        isolatableFactory: IsolatableFactory,
        fileCollectionFactory: FileCollectionFactory,
        fileLookup: FileLookup,
        parameterPropertyWalker: PropertyWalker,
        actionInstantiationScheme: InstantiationScheme,
        owner: DomainObjectContext,
        calculatedValueContainerFactory: CalculatedValueContainerFactory,
        internalServices: ServiceLookup
    ) {
        this.implementationClass = implementationClass
        this.fromAttributes = fromAttributes
        this.toAttributes = toAttributes
        this.fileNormalizer = inputArtifactNormalizer
        this.dependenciesNormalizer = dependenciesNormalizer
        this.fileLookup = fileLookup
        this.internalServices = internalServices
        this.instanceFactory = actionInstantiationScheme.forType(implementationClass)
        this.requiresDependencies = instanceFactory.serviceInjectionTriggeredByAnnotation(InputArtifactDependencies::class.java)
        this.requiresInputChanges = instanceFactory.requiresService(InputChanges::class.java)
        this.cacheable = cacheable
        this.artifactDirectorySensitivity = artifactDirectorySensitivity
        this.dependenciesDirectorySensitivity = dependenciesDirectorySensitivity
        this.artifactLineEndingSensitivity = artifactLineEndingSensitivity
        this.dependenciesLineEndingSensitivity = dependenciesLineEndingSensitivity
        this.isolatedParameters = calculatedValueContainerFactory.create<IsolatedParameters, IsolateTransformParameters>(
            Describables.of("parameters of", this),
            DefaultTransform.IsolateTransformParameters(
                parameterObject, implementationClass, cacheable, owner, parameterPropertyWalker,
                isolatableFactory, buildOperationRunner, classLoaderHierarchyHasher, fileCollectionFactory,
                (internalServices.get(org.gradle.api.problems.internal.ProblemsInternal::class.java) as org.gradle.api.problems.internal.ProblemsInternal?)!!,
                (internalServices.get(org.gradle.api.internal.DocumentationRegistry::class.java) as org.gradle.api.internal.DocumentationRegistry?)!!
            )
        )
    }

    /**
     * Used to recreate a transformer from the configuration cache.
     */
    constructor(
        implementationClass: Class<out TransformAction<*>>,
        isolatedParameters: CalculatedValueContainer<IsolatedParameters, IsolateTransformParameters>,
        fromAttributes: ImmutableAttributes,
        toAttributes: ImmutableAttributes,
        inputArtifactNormalizer: FileNormalizer,
        dependenciesNormalizer: FileNormalizer,
        cacheable: Boolean,
        fileLookup: FileLookup,
        actionInstantiationScheme: InstantiationScheme,
        internalServices: ServiceLookup,
        artifactDirectorySensitivity: DirectorySensitivity,
        dependenciesDirectorySensitivity: DirectorySensitivity,
        artifactLineEndingSensitivity: LineEndingSensitivity,
        dependenciesLineEndingSensitivity: LineEndingSensitivity
    ) {
        this.implementationClass = implementationClass
        this.fromAttributes = fromAttributes
        this.toAttributes = toAttributes
        this.fileNormalizer = inputArtifactNormalizer
        this.dependenciesNormalizer = dependenciesNormalizer
        this.fileLookup = fileLookup
        this.internalServices = internalServices
        this.instanceFactory = actionInstantiationScheme.forType(implementationClass)
        this.requiresDependencies = instanceFactory.serviceInjectionTriggeredByAnnotation(InputArtifactDependencies::class.java)
        this.requiresInputChanges = instanceFactory.requiresService(InputChanges::class.java)
        this.cacheable = cacheable
        this.isolatedParameters = isolatedParameters
        this.artifactDirectorySensitivity = artifactDirectorySensitivity
        this.dependenciesDirectorySensitivity = dependenciesDirectorySensitivity
        this.artifactLineEndingSensitivity = artifactLineEndingSensitivity
        this.dependenciesLineEndingSensitivity = dependenciesLineEndingSensitivity
    }

    override fun getInputArtifactNormalizer(): FileNormalizer {
        return fileNormalizer
    }

    override fun getInputArtifactDependenciesNormalizer(): FileNormalizer {
        return dependenciesNormalizer
    }

    override fun isIsolated(): Boolean {
        return isolatedParameters.isFinalized()
    }

    override fun requiresDependencies(): Boolean {
        return requiresDependencies
    }

    override fun requiresInputChanges(): Boolean {
        return requiresInputChanges
    }

    override fun isCacheable(): Boolean {
        return cacheable
    }

    override fun getInputArtifactDirectorySensitivity(): DirectorySensitivity {
        return artifactDirectorySensitivity
    }

    override fun getInputArtifactDependenciesDirectorySensitivity(): DirectorySensitivity {
        return dependenciesDirectorySensitivity
    }

    override fun getInputArtifactLineEndingNormalization(): LineEndingSensitivity {
        return artifactLineEndingSensitivity
    }

    override fun getInputArtifactDependenciesLineEndingNormalization(): LineEndingSensitivity {
        return dependenciesLineEndingSensitivity
    }

    override fun getSecondaryInputHash(): HashCode {
        return isolatedParameters.get().secondaryInputsHash
    }

    override fun transform(inputArtifactProvider: Provider<FileSystemLocation>, outputDir: File, dependencies: TransformDependencies, inputChanges: InputChanges?): TransformExecutionResult {
        val transformAction = newTransformAction(inputArtifactProvider, dependencies, inputChanges)
        val transformOutputs = DefaultTransformOutputs(inputArtifactProvider.get().getAsFile(), outputDir, fileLookup)
        transformAction.transform(transformOutputs)
        return transformOutputs.getRegisteredOutputs()
    }

    override fun visitDependencies(context: TaskDependencyResolveContext) {
        context.add(isolatedParameters)
    }

    override fun isolateParametersIfNotAlready() {
        isolatedParameters.finalizeIfNotAlready()
    }


    private fun newTransformAction(inputArtifactProvider: Provider<FileSystemLocation>, transformDependencies: TransformDependencies, inputChanges: InputChanges?): TransformAction<*> {
        val parameters: TransformParameters? = isolatedParameters.get().isolatedParameterObject.isolate()
        var services =
            IsolationScheme<TransformAction<*>?, TransformParameters>(TransformAction::class.java, TransformParameters::class.java, TransformParameters.None::class.java).servicesForImplementation(
                parameters!!,
                internalServices,
                mutableSetOf<Class<*>>()
            )
        services = TransformServiceLookup(inputArtifactProvider, if (requiresDependencies) transformDependencies else null, inputChanges, services)
        return instanceFactory.newInstance(services)
    }

    override fun getFromAttributes(): ImmutableAttributes {
        return fromAttributes
    }

    override fun getToAttributes(): ImmutableAttributes {
        return toAttributes
    }

    override fun getImplementationClass(): Class<out TransformAction<*>> {
        return implementationClass
    }

    override fun getDisplayName(): String {
        return implementationClass.getSimpleName()
    }

    private class TransformServiceLookup(
        inputFileProvider: Provider<FileSystemLocation>,
        transformDependencies: TransformDependencies?,
        inputChanges: InputChanges?,
        private val delegate: ServiceLookup
    ) : ServiceLookup {
        private val injectionPoints: ImmutableList<InjectionPoint>

        init {
            val builder = ImmutableList.builder<InjectionPoint>()
            builder.add(InjectionPoint.Companion.injectedByAnnotation(InputArtifact::class.java, FILE_SYSTEM_LOCATION_PROVIDER, Supplier { inputFileProvider }))
            if (transformDependencies != null) {
                builder.add(
                    InjectionPoint.Companion.injectedByAnnotation(
                        InputArtifactDependencies::class.java,
                        Supplier { transformDependencies.getFiles().orElseThrow<IllegalStateException>(Supplier { IllegalStateException("Transform does not use artifact dependencies.") }) })
                )
            }
            if (inputChanges != null) {
                builder.add(InjectionPoint.Companion.injectedByType(InputChanges::class.java, Supplier { inputChanges }))
            }
            this.injectionPoints = builder.build()
        }

        fun find(serviceType: Type, annotatedWith: Class<out Annotation>?): Any? {
            val serviceTypeToken = TypeToken.of(serviceType)
            for (injectionPoint in injectionPoints) {
                if (annotatedWith == injectionPoint.annotation && serviceTypeToken.isSupertypeOf(injectionPoint.injectedType)) {
                    return injectionPoint.getValueToInject()
                }
            }
            return null
        }

        @Throws(ServiceLookupException::class)
        override fun find(serviceType: Type): Any? {
            val result = find(serviceType, null)
            if (result != null) {
                return result
            }
            return delegate.find(serviceType)
        }

        @Throws(UnknownServiceException::class, ServiceLookupException::class)
        override fun get(serviceType: Type): Any {
            val result: Any = find(serviceType)!!
            if (result == null) {
                throw UnknownServiceException(serviceType, "No service of type " + serviceType + " available.")
            }
            return result
        }

        @Throws(UnknownServiceException::class, ServiceLookupException::class)
        override fun get(serviceType: Type, annotatedWith: Class<out Annotation>): Any {
            val result = find(serviceType, annotatedWith)
            if (result != null) {
                return result
            }
            return delegate.get(serviceType, annotatedWith)!!
        }

        private class InjectionPoint(val annotation: Class<out Annotation>?, val injectedType: Type, private val valueToInject: Supplier<Any>) {
            fun getValueToInject(): Any {
                return valueToInject.get()
            }

            companion object {
                fun injectedByAnnotation(annotation: Class<out Annotation>, valueToInject: Supplier<Any>): InjectionPoint {
                    return InjectionPoint(annotation, determineTypeFromAnnotation(annotation), valueToInject)
                }

                fun injectedByAnnotation(annotation: Class<out Annotation>, injectedType: Type, valueToInject: Supplier<Any>): InjectionPoint {
                    return InjectionPoint(annotation, injectedType, valueToInject)
                }

                fun injectedByType(injectedType: Class<*>, valueToInject: Supplier<Any>): InjectionPoint {
                    return InjectionPoint(null, injectedType, valueToInject)
                }

                private fun determineTypeFromAnnotation(annotation: Class<out Annotation>): Class<*> {
                    val supportedTypes: Array<Class<*>> = annotation.getAnnotation<InjectionPointQualifier>(InjectionPointQualifier::class.java).supportedTypes
                    require(supportedTypes.size == 1) { "Cannot determine supported type for annotation " + annotation.getName() }
                    return supportedTypes[0]
                }
            }
        }

        companion object {
            private val FILE_SYSTEM_LOCATION_PROVIDER = object : TypeToken<Provider<FileSystemLocation>>() {
            }.getType()
        }
    }

    class IsolatedParameters(val isolatedParameterObject: Isolatable<out TransformParameters>, val secondaryInputsHash: HashCode)

    class IsolateTransformParameters(
        val parameterObject: TransformParameters,
        val implementationClass: Class<*>,
        val isCacheable: Boolean,
        private val owner: DomainObjectContext,
        private val parameterPropertyWalker: PropertyWalker,
        private val isolatableFactory: IsolatableFactory,
        private val buildOperationRunner: BuildOperationRunner,
        private val classLoaderHierarchyHasher: ClassLoaderHierarchyHasher,
        private val fileCollectionFactory: FileCollectionFactory,
        private val problems: ProblemsInternal,
        private val documentationRegistry: DocumentationRegistry
    ) : ValueCalculator<IsolatedParameters> {
        override fun usesMutableProjectState(): Boolean {
            return owner.getProject() != null
        }

        override fun getOwningProject(): ProjectInternal? {
            return owner.getProject()
        }

        override fun visitDependencies(context: TaskDependencyResolveContext) {
            parameterPropertyWalker.visitProperties(parameterObject, TypeValidationContext.NOOP, object : PropertyVisitor {
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
                    context.add(value.getTaskDependencies())
                }
            })
        }

        override fun calculateValue(context: NodeExecutionContext): IsolatedParameters {
            val inputFingerprinter = context.getService<InputFingerprinter>(InputFingerprinter::class.java)
            return isolateParameters(inputFingerprinter)
        }

        private fun isolateParameters(inputFingerprinter: InputFingerprinter): IsolatedParameters {
            val model = owner.getModel()
            if (!model.hasMutableState()) {
                // This may happen when a task visits artifacts using a FileCollection instance created from a Configuration instance in a different project (not an artifact produced by a different project, these work fine)
                // There is a check in DefaultConfiguration that deprecates resolving dependencies via FileCollection instance created by a different project, however that check may not
                // necessarily be triggered. For example, the configuration may be legitimately resolved by some other task prior to the problematic task running
                // TODO - hoist this up into configuration file collection visiting (and not when visiting the upstream dependencies of a transform), and deprecate this in Gradle 7.x
                //
                // This may also happen when a transform takes upstream dependencies and the dependencies are transformed using a different transform
                // In this case, the main thread that schedules the work should isolate the transform parameters prior to scheduling the work. However, the dependencies may
                // be filtered from the result, so that the transform is not visited by the main thread, or the transform worker may start work before the main thread
                // has a chance to isolate the upstream transform
                // TODO - ensure all transform parameters required by a transform worker are isolated prior to starting the worker
                //
                // Force access to the state of the owner, regardless of whether any other thread has access. This is because attempting to acquire a lock for a project may deadlock
                // when performed from a worker thread (see DefaultBuildOperationQueue.waitForCompletion() which intentionally does not release the project locks while waiting)
                // TODO - add validation to fail eagerly when a worker attempts to lock a project
                //
                return model.forceAccessToMutableState<IsolatedParameters> { o: Any? -> doIsolateParameters(inputFingerprinter) }
            } else {
                return doIsolateParameters(inputFingerprinter)
            }
        }

        private fun doIsolateParameters(inputFingerprinter: InputFingerprinter): IsolatedParameters {
            try {
                return isolateParametersExclusively(inputFingerprinter)
            } catch (e: Exception) {
                val formatter = TreeFormatter()
                formatter.node("Could not isolate parameters ").appendValue(parameterObject).append(" of artifact transform ").appendType(implementationClass)
                throw VariantTransformConfigurationException(formatter.toString(), e, documentationRegistry)
            }
        }

        private fun isolateParametersExclusively(inputFingerprinter: InputFingerprinter): IsolatedParameters {
            val isolatedParameterObject = isolatableFactory.isolate<TransformParameters>(parameterObject)

            val hasher = Hashing.newHasher()
            hasher.putString(implementationClass.getName())
            hasher.putHash(classLoaderHierarchyHasher.getClassLoaderHash(implementationClass.getClassLoader())!!)

            val isolatedTransformParameters = isolatedParameterObject.isolate()
            buildOperationRunner.run(object : RunnableBuildOperation {
                override fun run(context: BuildOperationContext) {
                    // TODO wolfs - schedule fingerprinting separately, it can be done without having the project lock
                    Companion.fingerprintParameters(
                        inputFingerprinter,
                        fileCollectionFactory,
                        parameterPropertyWalker,
                        hasher,
                        isolatedTransformParameters!!,
                        this.isCacheable,
                        problems
                    )
                    context.setResult(FingerprintTransformInputsOperation.Result.Companion.INSTANCE)
                }

                override fun description(): BuildOperationDescriptor.Builder {
                    return BuildOperationDescriptor
                        .displayName("Fingerprint transform inputs")
                        .details(FingerprintTransformInputsOperation.Details.Companion.INSTANCE)
                }
            })
            val secondaryInputsHash = hasher.hash()
            return IsolatedParameters(isolatedParameterObject, secondaryInputsHash)
        }
    }

    /*
     * This operation is only used here temporarily. Should be replaced with a more stable operation in the long term.
     */
    interface FingerprintTransformInputsOperation : BuildOperationType<FingerprintTransformInputsOperation.Details, FingerprintTransformInputsOperation.Result> {
        interface Details {
            companion object {
                val INSTANCE: Details = object : Details {
                }
            }
        }

        interface Result {
            companion object {
                val INSTANCE: Result = object : Result {
                }
            }
        }
    }

    companion object {
        private const val CACHEABLE_TRANSFORM_CANT_USE_ABSOLUTE_SENSITIVITY = "CACHEABLE_TRANSFORM_CANT_USE_ABSOLUTE_SENSITIVITY"

        fun validateInputFileNormalizer(propertyName: String, normalizer: FileNormalizer?, cacheable: Boolean, validationContext: TypeValidationContext) {
            if (cacheable) {
                if (normalizer === InputNormalizer.ABSOLUTE_PATH) {
                    validationContext.visitPropertyError(Action { problem: TypeAwareProblemBuilder? ->
                        problem!!
                            .forProperty(propertyName)
                            .id(
                                org.gradle.util.internal.TextUtil.screamingSnakeToKebabCase(org.gradle.api.internal.artifacts.transform.DefaultTransform.Companion.CACHEABLE_TRANSFORM_CANT_USE_ABSOLUTE_SENSITIVITY),
                                "Property declared to be sensitive to absolute paths",
                                org.gradle.api.problems.internal.GradleCoreProblemGroup.validation().property()!!
                            )!! // TODO (donat) missing test coverage
                            .documentedAt(org.gradle.internal.deprecation.Documentation.Companion.userManual("validation_problems", "cacheable_transform_cant_use_absolute_sensitivity"))!!
                            .contextualLabel("is declared to be sensitive to absolute paths")!!
                            .details("This is not allowed for cacheable transforms")!!
                            .solution("Use a different normalization strategy via @PathSensitive, @Classpath or @CompileClasspath")
                    })
                }
            }
        }

        private const val ARTIFACT_TRANSFORM_SHOULD_NOT_DECLARE_OUTPUT = "ARTIFACT_TRANSFORM_SHOULD_NOT_DECLARE_OUTPUT"

        private fun fingerprintParameters(
            inputFingerprinter: InputFingerprinter,
            fileCollectionFactory: FileCollectionFactory,
            propertyWalker: PropertyWalker,
            hasher: Hasher,
            parameterObject: Any,
            cacheable: Boolean,
            problems: ProblemsInternal
        ) {
            val validationContext = DefaultTypeValidationContext.withoutRootType(cacheable, problems)
            val result = inputFingerprinter.fingerprintInputProperties(
                ImmutableSortedMap.of<String, ValueSnapshot>(),
                ImmutableSortedMap.of<String, FileCollectionFingerprint>(),
                ImmutableSortedMap.of<String, ValueSnapshot>(),
                ImmutableSortedMap.of<String, CurrentFileCollectionFingerprint>(),
                Consumer { visitor: InputVisitor? ->
                    propertyWalker.visitProperties(parameterObject, validationContext, object : PropertyVisitor {
                        override fun visitInputProperty(
                            propertyName: String,
                            value: PropertyValue,
                            optional: Boolean
                        ) {
                            try {
                                // TODO Unify this with AbstractValidatingProperty.validate();
                                //   we are doing a slightly different version of the same code here,
                                //   see https://github.com/gradle/gradle/issues/10846
                                val preparedValue = InputParameterUtils.prepareInputParameterValue(value)

                                if (preparedValue == null && !optional) {
                                    AbstractValidatingProperty.reportValueNotSet(propertyName, validationContext, true)
                                }
                                visitor!!.visitInputProperty(propertyName, InputVisitor.ValueSupplier { preparedValue })
                            } catch (e: Throwable) {
                                throw InvalidUserDataException(
                                    String.format(
                                        "Error while evaluating property '%s' of %s",
                                        propertyName,
                                        getParameterObjectDisplayName(parameterObject)
                                    ), e
                                )
                            }
                        }

                        override fun visitInputFileProperty(
                            propertyName: String,
                            optional: Boolean,
                            behavior: InputBehavior,
                            directorySensitivity: DirectorySensitivity,
                            lineEndingNormalization: LineEndingSensitivity,
                            normalizer: FileNormalizer?,
                            value: PropertyValue,
                            filePropertyType: InputFilePropertyType
                        ) {
                            validateInputFileNormalizer(propertyName, normalizer, cacheable, validationContext)
                            visitor!!.visitInputFileProperty(
                                propertyName,
                                behavior,
                                InputVisitor.InputFileValueSupplier(
                                    value,
                                    if (normalizer == null) InputNormalizer.ABSOLUTE_PATH else normalizer,
                                    directorySensitivity,
                                    lineEndingNormalization,
                                    Supplier { FileParameterUtils.resolveInputFileValue(fileCollectionFactory, filePropertyType, value) })
                            )
                        }

                        override fun visitOutputFileProperty(
                            propertyName: String,
                            optional: Boolean,
                            value: PropertyValue,
                            filePropertyType: OutputFilePropertyType
                        ) {
                            validationContext.visitPropertyError(Action { problem: TypeAwareProblemBuilder? ->
                                problem!!
                                    .forProperty(propertyName)
                                    .id(
                                        org.gradle.util.internal.TextUtil.screamingSnakeToKebabCase(org.gradle.api.internal.artifacts.transform.DefaultTransform.Companion.ARTIFACT_TRANSFORM_SHOULD_NOT_DECLARE_OUTPUT),
                                        "Artifact transform should not declare output",
                                        org.gradle.api.problems.internal.GradleCoreProblemGroup.validation().property()!!
                                    )!! // TODO (donat) missing test coverage
                                    .contextualLabel("declares an output")!!
                                    .documentedAt(
                                        org.gradle.internal.deprecation.Documentation.Companion.userManual(
                                            "validation_problems",
                                            org.gradle.api.internal.artifacts.transform.DefaultTransform.Companion.ARTIFACT_TRANSFORM_SHOULD_NOT_DECLARE_OUTPUT.lowercase()
                                        )
                                    )!!
                                    .details("is annotated with an output annotation")!!
                                    .solution("Remove the output property and use the TransformOutputs parameter from transform(TransformOutputs) instead")
                            }
                            )
                        }
                    })
                },  // We are not validating transform parameter locations
                FileCollectionStructureVisitor.NO_OP
            )

            val validationMessages = validationContext.getErrors()
            if (!validationMessages.isEmpty()) {
                val exception = WorkValidationException.withSummaryForTransformParameter(
                    getParameterObjectDisplayName(parameterObject), validationMessages.size
                )
                throw problems.reporter!!.throwing(exception, validationMessages)
            }

            for (entry in result.getValueSnapshots().entries) {
                hasher.putString(entry.key)
                entry.value.appendToHasher(hasher)
            }
            for (entry in result.getFileFingerprints().entries) {
                hasher.putString(entry.key)
                hasher.putHash(entry.value.getHash())
            }
        }

        private fun getParameterObjectDisplayName(parameterObject: Any): String {
            return ModelType.of(DslObject(parameterObject).getDeclaredType()).getDisplayName()
        }
    }
}
