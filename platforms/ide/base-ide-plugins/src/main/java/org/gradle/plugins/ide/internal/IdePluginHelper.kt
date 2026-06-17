/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.plugins.ide.internal

import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.internal.AbstractTask
import org.gradle.api.internal.ConfigurationCacheDegradation
import org.gradle.internal.Cast.uncheckedNonnullCast
import org.jspecify.annotations.NullMarked

/**
 * This class provides a place for sharing functionality with [IdePlugin] subclasses
 * without inadvertently making them API (as happens to public or protected members in [IdePlugin]).
 */
@NullMarked
object IdePluginHelper {
    /**
     * Returns a configuration action that requires unconditional graceful degradation
     * on the consumed task.
     */
    @JvmStatic
    fun withGracefulDegradation(): Action<Task> {
        return Action { task: Task ->
            ConfigurationCacheDegradation.requireDegradation<AbstractTask>(
                uncheckedNonnullCast<AbstractTask?>(task)!!,
                "Task is not compatible with the configuration cache"
            )
        }
    }
}
