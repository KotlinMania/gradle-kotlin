/*
 * Copyright 2014 the original author or authors.
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

import com.google.common.base.Joiner
import com.google.common.collect.ImmutableMap
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.ComponentModuleMetadataDetails
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.notations.ModuleIdentifierNotationConverter
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.internal.typeconversion.NotationParserBuilder

class ComponentModuleMetadataContainer(private val moduleIdentifierFactory: ImmutableModuleIdentifierFactory) {
    private val replacements: MutableMap<ModuleIdentifier, ImmutableModuleReplacements.Replacement> = HashMap<ModuleIdentifier, ImmutableModuleReplacements.Replacement>()

    fun module(sourceModule: Any): ComponentModuleMetadataDetails {
        val parser: NotationParser<Any, ModuleIdentifier> = parser(moduleIdentifierFactory)
        val source = parser.parseNotation(sourceModule)
        return object : ComponentModuleMetadataDetails {
            override fun replacedBy(moduleNotation: Any) {
                replacedBy(moduleNotation, null)
            }

            override fun replacedBy(targetModule: Any, reason: String?) {
                val target = parser.parseNotation(targetModule)
                detectCycles(replacements, source, target)
                replacements.put(source, ImmutableModuleReplacements.Replacement(target, reason))
            }

            override fun getId(): ModuleIdentifier {
                return source
            }

            override fun getReplacedBy(): ModuleIdentifier {
                return Companion.unwrap(replacements.get(source)!!)
            }
        }
    }

    fun getReplacements(): ImmutableModuleReplacements {
        return ImmutableModuleReplacements(ImmutableMap.copyOf<ModuleIdentifier, ImmutableModuleReplacements.Replacement>(replacements))
    }

    companion object {
        private fun detectCycles(replacements: MutableMap<ModuleIdentifier, ImmutableModuleReplacements.Replacement>, source: ModuleIdentifier, target: ModuleIdentifier) {
            if (source == target) {
                throw InvalidUserDataException(String.format("Cannot declare module replacement that replaces self: %s->%s", source, target))
            }

            var m: ModuleIdentifier = Companion.unwrap(replacements.get(target)!!)
            if (m == null) {
                //target does not exist in the map, there's no cycle for sure
                return
            }
            val visited: MutableSet<ModuleIdentifier> = LinkedHashSet<ModuleIdentifier>()
            visited.add(source)
            visited.add(target)

            while (m != null) {
                if (!visited.add(m)) {
                    //module was already visited, there is a cycle
                    throw InvalidUserDataException(
                        String.format(
                            "Cannot declare module replacement %s->%s because it introduces a cycle: %s",
                            source, target, Joiner.on("->").join(visited) + "->" + source
                        )
                    )
                }
                m = Companion.unwrap(replacements.get(m)!!)
            }
        }

        private fun unwrap(replacement: ImmutableModuleReplacements.Replacement): ModuleIdentifier {
            return (if (replacement == null) null else replacement.getTarget())!!
        }

        private fun parser(moduleIdentifierFactory: ImmutableModuleIdentifierFactory): NotationParser<Any, ModuleIdentifier> {
            return NotationParserBuilder
                .toType<ModuleIdentifier>(ModuleIdentifier::class.java)
                .fromCharSequence(ModuleIdentifierNotationConverter(moduleIdentifierFactory))
                .toComposite()
        }
    }
}
