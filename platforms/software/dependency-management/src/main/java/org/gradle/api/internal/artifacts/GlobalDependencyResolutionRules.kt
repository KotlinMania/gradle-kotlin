/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.Action
import org.gradle.api.artifacts.DependencySubstitution
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionRules
import org.gradle.internal.Actions
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.util.internal.CollectionUtils
import java.util.function.Function
import javax.inject.Inject

@ServiceScope(Scope.Project::class)
class GlobalDependencyResolutionRules @Inject constructor(ruleProviders: MutableList<DependencySubstitutionRules>) {
    val dependencySubstitutionRules: DependencySubstitutionRules

    init {
        this.dependencySubstitutionRules = CompositeSubstitutionRules(ruleProviders)
    }

    private class CompositeSubstitutionRules @Inject constructor(private val ruleProviders: MutableList<DependencySubstitutionRules>) : DependencySubstitutionRules {
        override fun getRuleAction(): Action<DependencySubstitution?> {
            return Actions.composite<DependencySubstitution?>(
                CollectionUtils.collect<Action<DependencySubstitution?>?, DependencySubstitutionRules?>(
                    ruleProviders,
                    Function { obj: DependencySubstitutionRules? -> obj!!.ruleAction })
            )
        }

        override fun rulesMayAddProjectDependency(): Boolean {
            for (ruleProvider in ruleProviders) {
                if (ruleProvider.rulesMayAddProjectDependency()) {
                    return true
                }
            }
            return false
        }
    }
}
