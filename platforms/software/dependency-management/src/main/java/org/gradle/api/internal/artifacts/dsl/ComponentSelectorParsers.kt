/*
 * Copyright 2015 the original author or authors.
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

import com.google.common.collect.ImmutableSet
import org.gradle.api.IllegalDependencyNotation
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.artifacts.capability.CapabilitySelector
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector.Companion.newSelector
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector
import org.gradle.internal.exceptions.DiagnosticsVisitor
import org.gradle.internal.typeconversion.MapKey
import org.gradle.internal.typeconversion.MapNotationConverter
import org.gradle.internal.typeconversion.NotationConvertResult
import org.gradle.internal.typeconversion.NotationConverter
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.internal.typeconversion.NotationParserBuilder
import org.gradle.internal.typeconversion.TypeConversionException

object ComponentSelectorParsers {
    private val BUILDER: NotationParserBuilder<Any, ComponentSelector> = NotationParserBuilder
        .toType<ComponentSelector>(ComponentSelector::class.java)
        .fromCharSequence(StringConverter())
        .converter(MapConverter())
        .fromType<Project>(Project::class.java, ProjectConverter())

    fun multiParser(): NotationParser<Any, MutableSet<ComponentSelector>> {
        return builder().toFlatteningComposite()
    }

    fun parser(): NotationParser<Any, ComponentSelector> {
        return builder().toComposite()
    }

    private fun builder(): NotationParserBuilder<Any, ComponentSelector> {
        return BUILDER
    }

    internal class MapConverter : MapNotationConverter<ComponentSelector>() {
        override fun describe(visitor: DiagnosticsVisitor) {
            visitor.example("Maps, e.g. [group: 'org.gradle', name:'gradle-core', version: '1.0'].")
        }

        protected fun parseMap(@MapKey("group") group: String, @MapKey("name") name: String, @MapKey("version") version: String): ModuleComponentSelector {
            return newSelector(DefaultModuleIdentifier.newId(group, name), DefaultImmutableVersionConstraint.of(version))
        }
    }

    internal class StringConverter : NotationConverter<String, ComponentSelector> {
        override fun describe(visitor: DiagnosticsVisitor) {
            visitor.example("String or CharSequence values, e.g. 'org.gradle:gradle-core:1.0'.")
        }

        @Throws(TypeConversionException::class)
        override fun convert(notation: String, result: NotationConvertResult<in ComponentSelector>) {
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
            result.converted(newSelector(DefaultModuleIdentifier.newId(parsed.getGroup(), parsed.getName()), DefaultImmutableVersionConstraint.of(parsed.getVersion())))
        }
    }

    internal class ProjectConverter : NotationConverter<Project, ComponentSelector> {
        override fun describe(visitor: DiagnosticsVisitor) {
            visitor.example("Project objects, e.g. project(':api').")
        }

        @Throws(TypeConversionException::class)
        override fun convert(notation: Project, result: NotationConvertResult<in ComponentSelector>) {
            val identity = (notation as ProjectInternal).getOwner().getIdentity()
            result.converted(DefaultProjectComponentSelector(identity, ImmutableAttributes.EMPTY, ImmutableSet.of<CapabilitySelector>()))
        }
    }
}
