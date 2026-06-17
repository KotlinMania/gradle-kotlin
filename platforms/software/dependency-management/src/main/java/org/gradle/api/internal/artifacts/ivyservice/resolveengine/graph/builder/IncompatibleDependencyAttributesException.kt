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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.GraphValidationException
import org.gradle.api.internal.attributes.AttributeMergingException
import org.gradle.internal.exceptions.ResolutionProvider
import org.gradle.internal.logging.text.TreeFormatter
import java.lang.String
import java.util.function.Consumer
import kotlin.Any
import kotlin.Boolean

/**
 * Thrown when two or more incoming dependency requirements for the same module request
 * conflicting values for the same attribute.
 *
 *
 * This typically occurs when multiple dependency constraints — declared directly or
 * contributed transitively through other projects — disagree on the value of a single
 * attribute for a shared target module.
 *
 *
 * Implements [ResolutionProvider] so suggested fixes render in the build
 * output's "Try" section rather than embedded in the exception message.
 */
class IncompatibleDependencyAttributesException(module: ModuleResolveState, cause: AttributeMergingException) : GraphValidationException(buildMessage(module, cause.getAttribute())),
    ResolutionProvider {
    val resolutions: MutableList<String>

    init {
        this.resolutions = buildResolutions(cause.getAttribute())
    }

    companion object {
        private fun buildMessage(module: ModuleResolveState, attribute: Attribute<*>): String {
            val fmt = TreeFormatter()

            fmt.node("Cannot select a variant of '" + module.getId())
            fmt.append("' because the dependency requirements request incompatible values for attribute '")
            fmt.append(attribute.getName())
            fmt.append("'.")

            val incomingEdges: MutableSet<EdgeState> = LinkedHashSet<EdgeState>()
            module.visitAllIncomingEdges(Consumer { e: EdgeState? -> incomingEdges.add(e!!) })

            val distinctValues: MutableSet<String> = LinkedHashSet<String>()
            for (edge in incomingEdges) {
                val value: Any? = findRequestedAttributeValue(edge, attribute)
                if (value != null) {
                    distinctValues.add("'" + value + "'")
                }
            }

            fmt.startChildren()
            if (!distinctValues.isEmpty()) {
                fmt.node("Requested values: " + String.join(", ", distinctValues))
            }

            for (edge in incomingEdges) {
                val value: Any? = findRequestedAttributeValue(edge, attribute)
                if (value == null) {
                    continue
                }
                val selector = edge.getSelector()
                val isConstraint = edge.getDependencyMetadata().isConstraint
                for (path in MessageBuilderHelper.formattedPathsTo(edge)) {
                    val quotelessPath: kotlin.String = stripSingleQuotesFromPath(path)
                    fmt.node(quotelessPath + " " + formatAttributeQuery(selector, attribute, value, isConstraint))
                }
            }
            fmt.endChildren()

            return fmt.toString()
        }

        private fun stripSingleQuotesFromPath(path: kotlin.String): kotlin.String {
            return path.replace("'([^()]+)' \\(".toRegex(), "$1 (")
        }

        private fun findRequestedAttributeValue(edge: EdgeState, attribute: Attribute<*>): Any? {
            val selector = edge.getSelector().getSelector()
            if (selector is ModuleComponentSelector) {
                return selector.getAttributes().getAttribute(attribute)
            }
            return null
        }

        private fun formatAttributeQuery(state: SelectorState, attribute: Attribute<*>, value: Any, isConstraint: Boolean): kotlin.String {
            val verb = if (isConstraint) "requires" else "depends on"
            return verb + " '" + state.getRequested() + "' with attribute '" + attribute.getName() + "' = '" + value + "'"
        }

        private fun buildResolutions(attribute: Attribute<*>): MutableList<kotlin.String> {
            val docs = DocumentationRegistry()
            return ImmutableList.of<kotlin.String>(
                "Configure all dependencies to use the same value for the attribute '" + attribute.getName() + "'.",
                "For advanced cases where different values should be treated as compatible, define a compatibility rule. See: " + docs.getDocumentationFor(
                    "variant_attributes",
                    "sec:abm-compatibility-rules"
                ) + "."
            )
        }
    }
}
