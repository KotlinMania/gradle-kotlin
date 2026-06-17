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
package org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.internal.Try
import org.gradle.internal.component.model.IvyArtifactName
import org.jspecify.annotations.NullMarked

/**
 * A dependency substitution applicator is responsible for applying substitution rules to dependency metadata.
 * Substitution result may either be the same module (no substitution), a different module (target of the substitution
 * is going to be different) or a failure.
 */
@NullMarked
interface DependencySubstitutionApplicator {
    /**
     * Execute any dependency substitution rules that apply to the given dependency sector and artifacts.
     *
     * @return the result of applying any substitution rules.
     */
    fun applySubstitutions(selector: ComponentSelector, artifacts: ImmutableList<IvyArtifactName>): Try<SubstitutionResult?>?

    companion object {
        /**
         * A substitution applicator that does not perform any substitutions.
         */
        val NO_OP: DependencySubstitutionApplicator =
            org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionApplicator { selector: ComponentSelector, artifacts: ImmutableList<IvyArtifactName> ->
                Try.successful(
                    DefaultDependencySubstitutionApplicator.DefaultSubstitutionResult.Companion.NO_OP
                )
            }
    }
}
