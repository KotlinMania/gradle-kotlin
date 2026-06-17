/*
 * Copyright 2023 the original author or authors.
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

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.ComponentSelectionReason

/**
 * Describes a version conflict between two or more versions of a module in a dependency graph.
 */
class Conflict(
    participants: ImmutableList<Participant>,
    moduleId: ModuleIdentifier,
    selectionReason: ComponentSelectionReason
) {
    val participants: MutableList<Participant>
    val moduleId: ModuleIdentifier
    val selectionReason: ComponentSelectionReason

    init {
        this.participants = participants
        this.moduleId = moduleId
        this.selectionReason = selectionReason
    }

    /**
     * A component that was involved in the conflict.
     */
    class Participant(version: String, id: ComponentIdentifier) {
        val version: String
        val id: ComponentIdentifier

        init {
            this.version = version
            this.id = id
        }
    }
}
