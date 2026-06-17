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

import com.google.common.base.Joiner
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonInternal
import java.util.function.Consumer

/**
 * A utility class that packages the logic necessary to build a message describing why a [ComponentState] was rejected.
 */
internal class ComponentRejectedMessageBuilder {
    fun buildFailureMessage(module: ModuleResolveState): String {
        var hasRejectAll = false
        for (candidate in module.getSelectors()) {
            val versionConstraint = candidate.getVersionConstraint()
            if (versionConstraint != null) {
                hasRejectAll = hasRejectAll or versionConstraint.isRejectAll
            }
        }
        val sb = StringBuilder()
        if (hasRejectAll) {
            sb.append("Module '").append(module.getId()).append("' has been rejected:\n")
        } else {
            sb.append("Cannot find a version of '").append(module.getId()).append("' that satisfies the version constraints:\n")
        }

        module.visitAllIncomingEdges(Consumer { incomingEdge: EdgeState? ->
            val selector: ComponentSelector = incomingEdge!!.getDependencyMetadata().selector!!
            for (path in MessageBuilderHelper.formattedPathsTo(incomingEdge)) {
                sb.append("   ").append(path)
                sb.append(" --> ")
                renderSelector(sb, selector)
                renderReason(sb, incomingEdge.getReason())
                sb.append("\n")
            }
        })

        return sb.toString()
    }

    companion object {
        private fun renderSelector(sb: StringBuilder, selector: ComponentSelector) {
            sb.append('\'').append(selector.getDisplayName()).append('\'')
        }

        private fun renderReason(sb: StringBuilder, selectionReason: ComponentSelectionReasonInternal) {
            if (selectionReason.hasCustomDescriptions()) {
                sb.append(" because of the following reason")
                val reasons: MutableList<String> = ArrayList<String>(1)
                for (componentSelectionDescriptor in selectionReason.getDescriptions()!!) {
                    if (componentSelectionDescriptor.hasCustomDescription()) {
                        reasons.add(componentSelectionDescriptor.getDescription())
                    }
                }
                if (reasons.size == 1) {
                    sb.append(": ").append(reasons.get(0))
                } else {
                    sb.append("s: ")
                    Joiner.on(", ").appendTo(sb, reasons)
                }
            }
        }
    }
}
