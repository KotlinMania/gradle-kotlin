/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.notations

import com.google.common.collect.Interner
import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.ModuleDependencyCapabilitiesHandler
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.artifacts.DefaultProjectDependencyFactory
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyConstraint
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependencyConstraint
import org.gradle.api.internal.artifacts.dependencies.DependencyVariant
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.problems.Problems
import org.gradle.api.provider.Provider
import org.gradle.internal.exceptions.DiagnosticsVisitor
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.typeconversion.NotationConvertResult
import org.gradle.internal.typeconversion.NotationConverter
import org.gradle.internal.typeconversion.NotationConverterToNotationParserAdapter
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.internal.typeconversion.NotationParserBuilder
import org.gradle.internal.typeconversion.TypeConversionException
import org.gradle.internal.typeconversion.TypedNotationConverter

class DependencyConstraintNotationParser private constructor(
    val notationParser: NotationParser<Any?, DependencyConstraint?>?,
    val stringNotationParser: NotationParser<String?, out DependencyConstraint?>?,
    val minimalExternalModuleDependencyNotationParser: NotationParser<MinimalExternalModuleDependency?, out DependencyConstraint?>?,
    val projectDependencyNotationParser: NotationParser<ProjectDependency?, out DependencyConstraint?>?
) {
    private class ProjectDependencyNotationConverter(private val instantiator: Instantiator) : TypedNotationConverter<ProjectDependency?, DependencyConstraint?>(ProjectDependency::class.java) {
        override fun parseType(notation: ProjectDependency): DependencyConstraint {
            return instantiator.newInstance<DefaultProjectDependencyConstraint>(DefaultProjectDependencyConstraint::class.java, notation)
        }
    }

    private class MinimalExternalDependencyNotationConverter(private val instantiator: Instantiator, private val attributesFactory: AttributesFactory) :
        NotationConverter<MinimalExternalModuleDependency?, DefaultDependencyConstraint?> {
        @Throws(TypeConversionException::class)
        override fun convert(notation: MinimalExternalModuleDependency, result: NotationConvertResult<in DefaultDependencyConstraint?>) {
            val dependencyConstraint = instantiator.newInstance<DefaultDependencyConstraint>(DefaultDependencyConstraint::class.java, notation.getModule(), notation.getVersionConstraint())
            if (notation is DependencyVariant) {
                dependencyConstraint.setAttributesFactory(attributesFactory)
                val dependencyVariant = notation as DependencyVariant
                dependencyConstraint.attributes(Action { attributes: AttributeContainer? -> dependencyVariant.mutateAttributes(attributes!!) })
                dependencyVariant.mutateCapabilities(UnsupportedCapabilitiesHandler.Companion.INSTANCE)
                val classifier = dependencyVariant.classifier
                val artifactType = dependencyVariant.artifactType
                if (classifier != null || artifactType != null) {
                    throw InvalidUserDataException("Classifier and artifact types aren't supported by dependency constraints")
                }
            }
            result.converted(dependencyConstraint)
        }

        override fun describe(visitor: DiagnosticsVisitor?) {
        }
    }

    private class UnsupportedCapabilitiesHandler : ModuleDependencyCapabilitiesHandler {
        override fun requireCapability(capabilityNotation: Any) {
            throw InvalidUserDataException("Capabilities are not supported by dependency constraints")
        }

        override fun requireCapabilities(vararg capabilityNotations: Any?) {
            throw InvalidUserDataException("Capabilities are not supported by dependency constraints")
        }

        override fun requireFeature(featureName: String) {
            throw InvalidUserDataException("Capabilities are not supported by dependency constraints")
        }

        override fun requireFeature(featureName: Provider<String?>) {
            throw InvalidUserDataException("Capabilities are not supported by dependency constraints")
        }

        companion object {
            private val INSTANCE = UnsupportedCapabilitiesHandler()
        }
    }

    companion object {
        fun parser(
            instantiator: Instantiator,
            dependencyFactory: DefaultProjectDependencyFactory?,
            stringInterner: Interner<String?>?,
            attributesFactory: AttributesFactory,
            problems: Problems?
        ): DependencyConstraintNotationParser {
            val stringNotationConverter = DependencyStringNotationConverter<DefaultDependencyConstraint?>(instantiator, DefaultDependencyConstraint::class.java, stringInterner, problems)
            val minimalExternalDependencyNotationConverter = MinimalExternalDependencyNotationConverter(instantiator, attributesFactory)
            val projectDependencyNotationConverter = ProjectDependencyNotationConverter(instantiator)
            val notationParser = NotationParserBuilder
                .toType<DependencyConstraint?>(DependencyConstraint::class.java)
                .fromType<MinimalExternalModuleDependency?>(MinimalExternalModuleDependency::class.java, minimalExternalDependencyNotationConverter)
                .fromCharSequence(stringNotationConverter)
                .converter(DependencyMapNotationConverter<DefaultDependencyConstraint?>(instantiator, DefaultDependencyConstraint::class.java))
                .fromType<Project?>(Project::class.java, DependencyConstraintProjectNotationConverter(instantiator, dependencyFactory))
                .converter(projectDependencyNotationConverter)
                .invalidNotationMessage("Comprehensive documentation on dependency notations is available in DSL reference for DependencyConstraintHandler type.")
                .toComposite()
            return DependencyConstraintNotationParser(
                notationParser,
                NotationConverterToNotationParserAdapter<String?, DefaultDependencyConstraint?>(stringNotationConverter),
                NotationConverterToNotationParserAdapter<MinimalExternalModuleDependency?, DefaultDependencyConstraint?>(minimalExternalDependencyNotationConverter),
                NotationConverterToNotationParserAdapter<ProjectDependency?, DependencyConstraint?>(projectDependencyNotationConverter)
            )
        }
    }
}
