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
package org.gradle.api.internal.artifacts

import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope

/**
 * A factory for [ComponentMetadataProcessor].
 *
 *
 * In a build, [component metadata rules][org.gradle.api.artifacts.ComponentMetadataRule] can be added to transform dependencies metadata.
 * These are registered with the [ComponentMetadataHandler][org.gradle.api.artifacts.dsl.ComponentMetadataHandler] which does not have contextual information,
 * such as the repository from which the dependency comes from.
 *
 *
 * The [MetadataResolutionContext] enables a [ComponentMetadataProcessor] to execute with the proper context.
 */
@ServiceScope(Scope.Project::class)
interface ComponentMetadataProcessorFactory {
    /**
     * Creates a contextual [ComponentMetadataProcessor]
     *
     * @param resolutionContext the provided context
     * @return a `ComponentMetadataProcessor`
     */
    fun createComponentMetadataProcessor(resolutionContext: MetadataResolutionContext?): ComponentMetadataProcessor?
}
