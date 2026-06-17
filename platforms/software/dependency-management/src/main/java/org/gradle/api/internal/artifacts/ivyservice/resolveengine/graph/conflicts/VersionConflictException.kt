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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts

import org.gradle.api.artifacts.result.ComponentSelectionCause
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.GraphValidationException
import org.gradle.internal.exceptions.ResolutionProvider

class VersionConflictException(conflict: Conflict, resolutions: MutableList<String>) : GraphValidationException(buildMessage(conflict)), ResolutionProvider {
    val resolutions: MutableList<String>

    init {
        this.resolutions = resolutions
    }

    companion object {
        private fun buildMessage(conflict: Conflict): String {
            val conflictDescription: String = getConflictDescription(conflict)
            val moduleId = conflict.getModuleId()

            return "Conflict found for module '" + moduleId.getGroup() + ":" + moduleId.getName() + "': " + conflictDescription
        }

        private fun getConflictDescription(conflict: Conflict): String {
            var conflictDescription: String = null
            for (description in conflict.getSelectionReason().getDescriptions()) {
                if (description.getCause() == ComponentSelectionCause.CONFLICT_RESOLUTION) {
                    conflictDescription = description.getDescription()
                }
            }
            checkNotNull(conflictDescription)
            return conflictDescription
        }
    }
}
