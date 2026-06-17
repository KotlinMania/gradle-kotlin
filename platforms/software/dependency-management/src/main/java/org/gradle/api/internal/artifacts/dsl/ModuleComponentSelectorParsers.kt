/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.dsl

import org.gradle.api.IllegalDependencyNotation
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderConvertible
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector.Companion.newSelector
import org.gradle.internal.deprecation.DeprecationLogger.deprecateAction
import org.gradle.internal.exceptions.DiagnosticsVisitor
import org.gradle.internal.typeconversion.MapKey
import org.gradle.internal.typeconversion.MapNotationConverter
import org.gradle.internal.typeconversion.NotationConvertResult
import org.gradle.internal.typeconversion.NotationConverter
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.internal.typeconversion.NotationParserBuilder
import org.gradle.internal.typeconversion.TypeConversionException
import org.gradle.internal.typeconversion.TypeInfo
import org.gradle.internal.typeconversion.TypedNotationConverter

object ModuleComponentSelectorParsers {
    fun multiParser(dslContext: String): NotationParser<Any, MutableSet<ModuleComponentSelector>> {
        return builder(dslContext).toFlatteningComposite()
    }

    fun parser(dslContext: String): NotationParser<Any, ModuleComponentSelector> {
        return builder(dslContext).toComposite()
    }

    private fun builder(dslContext: String): NotationParserBuilder<Any, ModuleComponentSelector> {
        return NotationParserBuilder
            .toType<ModuleComponentSelector>(ModuleComponentSelector::class.java)
            .fromCharSequence(StringConverter())
            .converter(MapConverter())
            .converter(ProviderConverter(dslContext))
            .converter(ProviderConvertibleConverter(dslContext))
            .converter(ExternalDependencyConverter())
            .converter(ModuleVersionSelectorConverter())
    }

    internal class MapConverter : MapNotationConverter<ModuleComponentSelector>() {
        override fun describe(visitor: DiagnosticsVisitor) {
            visitor.candidate("Maps").example("[group: 'org.gradle', name: 'gradle-core', version: '1.0']")
        }

        protected fun parseMap(@MapKey("group") group: String, @MapKey("name") name: String, @MapKey("version") version: String): ModuleComponentSelector {
            return newSelector(DefaultModuleIdentifier.newId(group, name), version)
        }
    }

    internal class StringConverter : NotationConverter<String, ModuleComponentSelector> {
        override fun describe(visitor: DiagnosticsVisitor) {
            visitor.candidate("String or CharSequence values").example("'org.gradle:gradle-core:1.0'")
        }

        @Throws(TypeConversionException::class)
        override fun convert(notation: String, result: NotationConvertResult<in ModuleComponentSelector>) {
            val parsed: ParsedModuleStringNotation?
            try {
                parsed = ParsedModuleStringNotation(notation, null)
            } catch (e: IllegalDependencyNotation) {
                throw InvalidUserDataException(
                    ("Invalid format: '" + notation + "'. The correct notation is a 3-part group:name:version notation, "
                            + "e.g: 'org.gradle:gradle-core:1.0'")
                )
            }

            if (parsed.getGroup() == null || parsed.getName() == null || parsed.getVersion() == null) {
                throw InvalidUserDataException(
                    ("Invalid format: '" + notation + "'. Group, name and version cannot be empty. Correct example: "
                            + "'org.gradle:gradle-core:1.0'")
                )
            }
            result.converted(newSelector(DefaultModuleIdentifier.newId(parsed.getGroup(), parsed.getName()), parsed.getVersion()))
        }
    }

    internal class ProviderConvertibleConverter(caller: String) :
        TypedNotationConverter<ProviderConvertible<*>, ModuleComponentSelector>(TypeInfo<ProviderConvertible<*>?>(ProviderConvertible::class.java)) {
        private val providerConverter: ProviderConverter

        init {
            this.providerConverter = ProviderConverter(caller)
        }

        override fun describe(visitor: DiagnosticsVisitor) {
            visitor.candidate("Version catalog type-safe accessors.")
        }

        override fun parseType(notation: ProviderConvertible<*>): ModuleComponentSelector {
            return providerConverter.parseType(notation.asProvider())
        }
    }

    internal class ProviderConverter(private val caller: String) : TypedNotationConverter<Provider<*>, ModuleComponentSelector>(TypeInfo<Provider<*>?>(Provider::class.java)) {
        override fun describe(visitor: DiagnosticsVisitor) {
            visitor.candidate("Version catalog type-safe accessors.")
        }

        public override fun parseType(notation: Provider<*>): ModuleComponentSelector {
            val providerTargetClass = getProviderTargetClass(notation)
            if (!MinimalExternalModuleDependency::class.java.isAssignableFrom(providerTargetClass)) {
                val notationAsString = if (notation.getOrNull() == null) null else notation.get().toString()
                throw InvalidUserDataException(
                    "Cannot convert a version catalog entry '" + notationAsString + "' to an object of type ModuleComponentSelector. " +
                            "Only dependency accessors are supported but not plugin, bundle or version accessors for '" + caller + "'."
                )
            }
            val dependency = notation.get() as MinimalExternalModuleDependency
            if (isNotRequiredVersionOnly(dependency.getVersionConstraint())) {
                throw InvalidUserDataException("Cannot convert a version catalog entry: '" + notation.get() + "' to an object of type ModuleComponentSelector. Rich versions are not supported for '" + caller + "'.")
            } else if (dependency.getVersionConstraint().getRequiredVersion().isEmpty()) {
                throw InvalidUserDataException("Cannot convert a version catalog entry: '" + notation.get() + "' to an object of type ModuleComponentSelector. Version cannot be empty for '" + caller + "'.")
            } else {
                return newSelector(dependency.getModule(), dependency.getVersionConstraint().getRequiredVersion())
            }
        }

        private fun getProviderTargetClass(notation: Provider<*>): Class<*> {
            return (if (notation.getOrNull() == null)
                null
            else
                notation.get().javaClass)!!
        }

        private fun isNotRequiredVersionOnly(constraint: VersionConstraint): Boolean {
            return !constraint.getPreferredVersion().isEmpty() || !constraint.getStrictVersion().isEmpty() || !constraint.getRejectedVersions().isEmpty() || constraint.getBranch() != null
        }
    }

    internal class ExternalDependencyConverter : TypedNotationConverter<ExternalDependency, ModuleComponentSelector>(TypeInfo<ExternalDependency?>(ExternalDependency::class.java)) {
        override fun describe(visitor: DiagnosticsVisitor) {
            visitor.candidate("ExternalDependency instances.")
        }

        override fun parseType(notation: ExternalDependency): ModuleComponentSelector {
            return newSelector(notation.getModule(), notation.getVersionConstraint())
        }
    }

    internal class ModuleVersionSelectorConverter : TypedNotationConverter<ModuleVersionSelector, ModuleComponentSelector>(TypeInfo<ModuleVersionSelector?>(ModuleVersionSelector::class.java)) {
        override fun describe(visitor: DiagnosticsVisitor) {
            visitor.candidate("ModuleVersionSelector instances. (deprecated)")
        }

        override fun parseType(notation: ModuleVersionSelector): ModuleComponentSelector {
            deprecateAction("Converting an instance of ModuleVersionSelector to ModuleComponentSelector")
                .withAdvice("Don't create or use ModuleVersionSelector instances and pass one of the other supported notations instead.")!!
                .willBecomeAnErrorInGradle10()
                .withUpgradeGuideSection(9, "deprecate_moduleversionselector_to_modulecomponentselector")!!
                .nagUser()
            return newSelector(
                notation.getModule(), DefaultImmutableVersionConstraint.of(notation.getVersion())
            )
        }
    }
}
