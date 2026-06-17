/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy

import org.gradle.internal.component.external.model.DefaultImmutableCapability.Companion.of
import org.gradle.internal.rules.RuleActionAdapter.createFromAction
import org.gradle.internal.rules.RuleActionAdapter.createFromClosure
import org.gradle.internal.deprecation.DeprecationLogger.deprecateMethod
import org.gradle.internal.deprecation.DeprecationMessageBuilder.willBeRemovedInGradle10
import org.gradle.internal.deprecation.Documentation.AbstractBuilder.withUpgradeGuideSection
import org.gradle.internal.deprecation.DeprecationMessageBuilder.WithDocumentation.nagUser
import org.gradle.internal.rules.RuleActionAdapter.createFromRuleSource
import org.gradle.api.internal.artifacts.GlobalDependencyResolutionRules.dependencySubstitutionRules
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal.rules
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal.addRule
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal.useTarget

