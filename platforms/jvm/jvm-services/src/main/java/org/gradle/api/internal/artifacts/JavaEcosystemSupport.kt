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
package org.gradle.api.internal.artifacts

import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Ordering
import org.gradle.api.Action
import org.gradle.api.ActionConfiguration
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.AttributesSchema
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.MultipleCandidatesDetails
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmEnvironment
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.internal.attributes.AttributeDescriberRegistry
import org.gradle.api.model.ObjectFactory
import java.util.function.Consumer
import javax.inject.Inject

object JavaEcosystemSupport {
    /**
     * The Java API of a library, packaged as a JAR only. Must not include classes directories.
     *
     * Available for compatibility with previously published modules.  Should **NOT** be used for new publishing.
     * No plans for permanent removal.
     */
    @Deprecated("")
    const val DEPRECATED_JAVA_API_JARS: String = "java-api-jars"

    /**
     * The Java runtime of a component, packaged as JAR only. Must not include classes directories.
     *
     * Available for compatibility with previously published modules.  Should **NOT** be used for new publishing.
     * No plans for permanent removal.
     */
    @Deprecated("")
    const val DEPRECATED_JAVA_RUNTIME_JARS: String = "java-runtime-jars"

    /**
     * The Java API of a library, packaged as class path elements, either a JAR or a classes directory. Should not include resources, but may.
     *
     * Available for compatibility with previously published modules.  Should **NOT** be used for new publishing.
     * No plans for permanent removal.
     */
    @Deprecated("")
    const val DEPRECATED_JAVA_API_CLASSES: String = "java-api-classes"

    /**
     * The Java runtime classes of a component, packaged as class path elements, either a JAR or a classes directory. Should not include resources, but may.
     *
     * Available for compatibility with previously published modules.  Should **NOT** be used for new publishing.
     * No plans for permanent removal.
     */
    @Deprecated("")
    const val DEPRECATED_JAVA_RUNTIME_CLASSES: String = "java-runtime-classes"

    /**
     * The Java runtime resources of a component, packaged as class path elements, either a JAR or a classes directory. Should not include classes, but may.
     *
     * Available for compatibility with previously published modules.  Should **NOT** be used for new publishing.
     * No plans for permanent removal.
     */
    @Deprecated("")
    const val DEPRECATED_JAVA_RUNTIME_RESOURCES: String = "java-runtime-resources"

    /**
     * Configure the dependency management services so that they properly participate
     * in dependency resolution for the Jvm ecosystem.
     */
    @JvmStatic
    fun configureServices(
        attributesSchema: AttributesSchema,
        attributeDescribers: AttributeDescriberRegistry,
        objectFactory: ObjectFactory
    ) {
        configureUsage(attributesSchema, objectFactory)
        configureLibraryElements(attributesSchema, objectFactory)
        configureBundling(attributesSchema)
        configureTargetPlatform(attributesSchema)
        configureTargetEnvironment(attributesSchema)
        configureConsumerDescriptors(attributeDescribers)
        attributesSchema.attributeDisambiguationPrecedence(
            Category.CATEGORY_ATTRIBUTE,
            Usage.USAGE_ATTRIBUTE,
            TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
            LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
            Bundling.BUNDLING_ATTRIBUTE,
            TargetJvmEnvironment.Companion.TARGET_JVM_ENVIRONMENT_ATTRIBUTE
        )
    }

    private fun configureConsumerDescriptors(describers: AttributeDescriberRegistry) {
        describers.addDescriber(JavaEcosystemAttributesDescriber())
    }

    private fun configureTargetPlatform(attributesSchema: AttributesSchema) {
        val targetPlatformSchema = attributesSchema.attribute<Int>(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE)
        targetPlatformSchema.getCompatibilityRules().ordered(Ordering.natural<Int>())
        targetPlatformSchema.getDisambiguationRules().pickLast(Ordering.natural<Int>())
    }

    private fun configureTargetEnvironment(attributesSchema: AttributesSchema) {
        val targetEnvironmentSchema = attributesSchema.attribute<TargetJvmEnvironment>(TargetJvmEnvironment.Companion.TARGET_JVM_ENVIRONMENT_ATTRIBUTE)
        targetEnvironmentSchema.getCompatibilityRules().add(TargetJvmEnvironmentCompatibilityRules::class.java)
        targetEnvironmentSchema.getDisambiguationRules().add(TargetJvmEnvironmentDisambiguationRules::class.java)
    }

    private fun configureBundling(attributesSchema: AttributesSchema) {
        val bundlingSchema = attributesSchema.attribute<Bundling>(Bundling.BUNDLING_ATTRIBUTE)
        bundlingSchema.getCompatibilityRules().add(BundlingCompatibilityRules::class.java)
        bundlingSchema.getDisambiguationRules().add(BundlingDisambiguationRules::class.java)
    }

    private fun configureUsage(attributesSchema: AttributesSchema, objectFactory: ObjectFactory) {
        val usageSchema = attributesSchema.attribute<Usage>(Usage.USAGE_ATTRIBUTE)
        usageSchema.getCompatibilityRules().add(UsageCompatibilityRules::class.java)
        usageSchema.getDisambiguationRules().add(UsageDisambiguationRules::class.java, object : Action<ActionConfiguration> {
            override fun execute(actionConfiguration: ActionConfiguration) {
                actionConfiguration.params(objectFactory.named<Usage>(Usage::class.java, Usage.JAVA_API))
                actionConfiguration.params(objectFactory.named<Usage>(Usage::class.java, DEPRECATED_JAVA_API_JARS))
                actionConfiguration.params(objectFactory.named<Usage>(Usage::class.java, Usage.JAVA_RUNTIME))
                actionConfiguration.params(objectFactory.named<Usage>(Usage::class.java, DEPRECATED_JAVA_RUNTIME_JARS))
            }
        })
    }

    private fun configureLibraryElements(attributesSchema: AttributesSchema, objectFactory: ObjectFactory) {
        val libraryElementsSchema = attributesSchema.attribute<LibraryElements>(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE)
        libraryElementsSchema.getCompatibilityRules().add(LibraryElementsCompatibilityRules::class.java)
        libraryElementsSchema.getDisambiguationRules().add(LibraryElementsDisambiguationRules::class.java, Action { actionConfiguration: ActionConfiguration ->
            actionConfiguration.params(
                objectFactory.named<LibraryElements>(
                    LibraryElements::class.java, LibraryElements.JAR
                )
            )
        })
    }

    @VisibleForTesting
    class UsageDisambiguationRules @Inject constructor(
        val javaApi: Usage,
        val javaApiJars: Usage,
        val javaRuntime: Usage,
        val javaRuntimeJars: Usage
    ) : AttributeDisambiguationRule<Usage> {
        val apiVariants: ImmutableSet<Usage>
        val runtimeVariants: ImmutableSet<Usage>

        init {
            this.apiVariants = ImmutableSet.of<Usage>(javaApi, javaApiJars)
            this.runtimeVariants = ImmutableSet.of<Usage>(javaRuntime, javaRuntimeJars)
        }

        override fun execute(details: MultipleCandidatesDetails<Usage>) {
            val candidateValues = details.getCandidateValues()
            val consumerValue = details.getConsumerValue()
            if (consumerValue == null) {
                if (candidateValues.contains(javaRuntimeJars)) {
                    // Use the Jars when nothing has been requested
                    details.closestMatch(javaRuntimeJars)
                } else if (candidateValues.contains(javaRuntime)) {
                    // Use the runtime when nothing has been requested
                    details.closestMatch(javaRuntime)
                }
            } else {
                if (javaRuntime == consumerValue) {
                    // we're asking for a runtime variant, prefer -jars first
                    if (candidateValues.contains(javaRuntimeJars)) {
                        details.closestMatch(javaRuntimeJars)
                    } else if (candidateValues.contains(javaRuntime)) {
                        details.closestMatch(javaRuntime)
                    }
                } else if (javaApi == consumerValue) {
                    // we're asking for an API variant, prefer -jars first for runtime
                    if (candidateValues.contains(javaApiJars)) {
                        details.closestMatch(javaApiJars)
                    } else if (candidateValues.contains(javaApi)) {
                        details.closestMatch(javaApi)
                    } else if (candidateValues.contains(javaRuntimeJars)) {
                        details.closestMatch(javaRuntimeJars)
                    } else if (candidateValues.contains(javaRuntime)) {
                        details.closestMatch(javaRuntime)
                    }
                } else if (candidateValues.contains(consumerValue)) {
                    details.closestMatch(consumerValue)
                }
            }
        }
    }

    @VisibleForTesting
    class UsageCompatibilityRules : AttributeCompatibilityRule<Usage> {
        override fun execute(details: CompatibilityCheckDetails<Usage>) {
            val consumerValue = details.getConsumerValue()
            val producerValue = details.getProducerValue()
            if (consumerValue == null) {
                // consumer didn't express any preferences, everything fits
                details.compatible()
                return
            }
            if (consumerValue.getName() == Usage.JAVA_API) {
                if (COMPATIBLE_WITH_JAVA_API.contains(producerValue!!.getName())) {
                    details.compatible()
                }
                return
            }
            if (consumerValue.getName() == Usage.JAVA_RUNTIME && producerValue!!.getName() == DEPRECATED_JAVA_RUNTIME_JARS) {
                details.compatible()
                return
            }
        }

        companion object {
            private val COMPATIBLE_WITH_JAVA_API: MutableSet<String> = ImmutableSet.of<String>(
                DEPRECATED_JAVA_API_JARS,
                DEPRECATED_JAVA_RUNTIME_JARS,
                Usage.JAVA_RUNTIME
            )
        }
    }

    @VisibleForTesting
    internal class LibraryElementsDisambiguationRules @Inject constructor(val jar: LibraryElements) : AttributeDisambiguationRule<LibraryElements> {
        override fun execute(details: MultipleCandidatesDetails<LibraryElements>) {
            val candidateValues = details.getCandidateValues()
            val consumerValue = details.getConsumerValue()
            if (consumerValue == null) {
                if (candidateValues.contains(jar)) {
                    // Use the jar when nothing has been requested
                    details.closestMatch(jar)
                }
            } else if (candidateValues.contains(consumerValue)) {
                // Use what they requested, if available
                details.closestMatch(consumerValue)
            }
        }
    }

    @VisibleForTesting
    internal class LibraryElementsCompatibilityRules : AttributeCompatibilityRule<LibraryElements> {
        override fun execute(details: CompatibilityCheckDetails<LibraryElements>) {
            val consumerValue = details.getConsumerValue()
            val producerValue = details.getProducerValue()
            if (consumerValue == null) {
                // consumer didn't express any preferences, everything fits
                details.compatible()
                return
            }
            val consumerValueName = consumerValue.getName()
            val producerValueName = producerValue!!.getName()
            if (LibraryElements.CLASSES == consumerValueName || LibraryElements.RESOURCES == consumerValueName || LibraryElements.CLASSES_AND_RESOURCES == consumerValueName) {
                // JAR is compatible with classes or resources
                if (LibraryElements.JAR == producerValueName) {
                    details.compatible()
                    return
                }
            }
        }
    }

    private class TargetJvmEnvironmentCompatibilityRules  // public constructor to make reflective initialization happy.
        : AttributeCompatibilityRule<TargetJvmEnvironment> {
        override fun execute(details: CompatibilityCheckDetails<TargetJvmEnvironment>) {
            details.compatible()
        }
    }

    private class TargetJvmEnvironmentDisambiguationRules  // public constructor to make reflective initialization happy.
        : AttributeDisambiguationRule<TargetJvmEnvironment> {
        override fun execute(details: MultipleCandidatesDetails<TargetJvmEnvironment>) {
            val consumerValue = details.getConsumerValue()
            if (consumerValue != null && details.getCandidateValues().contains(consumerValue)) {
                details.closestMatch(consumerValue) // exact match
            } else {
                val standardJvm = details.getCandidateValues().stream().filter { c: TargetJvmEnvironment -> TargetJvmEnvironment.Companion.STANDARD_JVM == c.getName() }.findFirst()
                standardJvm.ifPresent(Consumer { candidate: TargetJvmEnvironment -> details.closestMatch(candidate) })
            }
        }
    }

    @VisibleForTesting
    internal class BundlingCompatibilityRules : AttributeCompatibilityRule<Bundling> {
        override fun execute(details: CompatibilityCheckDetails<Bundling>) {
            val consumerValue = details.getConsumerValue()
            val producerValue = details.getProducerValue()
            if (consumerValue == null) {
                // consumer didn't express any preference, everything fits
                details.compatible()
                return
            }
            val consumerValueName = consumerValue.getName()
            val producerValueName = producerValue!!.getName()
            if (Bundling.EXTERNAL == consumerValueName) {
                if (COMPATIBLE_WITH_EXTERNAL.contains(producerValueName)) {
                    details.compatible()
                }
            } else if (Bundling.EMBEDDED == consumerValueName) {
                // asking for a fat jar. If everything available is a shadow jar, that's fine
                if (Bundling.SHADOWED == producerValueName) {
                    details.compatible()
                }
            }
        }

        companion object {
            private val COMPATIBLE_WITH_EXTERNAL: MutableSet<String> =
                ImmutableSet.of<String>( // if we ask for "external" dependencies, it's still fine to bring a fat jar if nothing else is available
                    Bundling.EMBEDDED,
                    Bundling.SHADOWED
                )
        }
    }

    @VisibleForTesting
    internal class BundlingDisambiguationRules : AttributeDisambiguationRule<Bundling> {
        override fun execute(details: MultipleCandidatesDetails<Bundling>) {
            val consumerValue = details.getConsumerValue()
            val candidateValues: MutableSet<Bundling> = details.getCandidateValues()
            if (consumerValue != null && candidateValues.contains(consumerValue)) {
                details.closestMatch(consumerValue)
                return
            }
            if (consumerValue == null) {
                var embedded: Bundling? = null
                for (candidateValue in candidateValues) {
                    if (Bundling.EXTERNAL == candidateValue.getName()) {
                        details.closestMatch(candidateValue)
                        return
                    } else if (Bundling.EMBEDDED == candidateValue.getName()) {
                        embedded = candidateValue
                    }
                }
                if (embedded != null) {
                    details.closestMatch(embedded)
                }
            } else {
                val consumerValueName = consumerValue.getName()
                if (Bundling.EXTERNAL == consumerValueName) {
                    for (candidateValue in candidateValues) {
                        if (Bundling.EMBEDDED == candidateValue.getName()) {
                            details.closestMatch(candidateValue)
                            return
                        }
                    }
                }
            }
        }
    }
}
