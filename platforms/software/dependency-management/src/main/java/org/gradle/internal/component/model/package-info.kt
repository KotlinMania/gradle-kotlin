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
package org.gradle.internal.component.model

import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.Cast.uncheckedNonnullCast
import org.gradle.internal.deprecation.DeprecationLogger.deprecateConfiguration
import org.gradle.internal.deprecation.DeprecationMessageBuilder.ConfigurationDeprecationTypeSelector.forConsumption
import org.gradle.internal.deprecation.DeprecationMessageBuilder.willBecomeAnErrorInNextMajorGradleVersion
import org.gradle.internal.deprecation.Documentation.AbstractBuilder.withUserManual
import org.gradle.internal.deprecation.DeprecationMessageBuilder.WithDocumentation.nagUser
import org.gradle.util.internal.CollectionUtils.filter
import org.gradle.api.logging.Logging.getLogger
import org.gradle.api.logging.Logger.debug
import org.gradle.internal.lazy.Lazy.Companion.locking
import org.gradle.internal.lazy.Lazy.Factory.of
import org.gradle.internal.logging.text.TreeFormatter.startChildren
import org.gradle.internal.logging.text.TreeFormatter.endChildren
import org.gradle.internal.logging.text.TreeFormatter.toString
import org.gradle.internal.logging.text.TreeFormatter.node
import org.gradle.internal.logging.text.TreeFormatter.append
import org.gradle.api.internal.DocumentationRegistry.getDocumentationFor
import org.gradle.api.problems.internal.GradleCoreProblemGroup.variantResolution
import org.gradle.internal.deprecation.Documentation.Companion.userManual
import org.gradle.api.problems.internal.AdditionalDataBuilderFactory.hasProviderForSpec
import org.gradle.api.problems.internal.AdditionalDataBuilderFactory.registerAdditionalDataProvider
import org.gradle.internal.deprecation.DeprecationLogger.deprecateType
import org.gradle.internal.deprecation.DeprecationMessageBuilder.withAdvice
import org.gradle.internal.deprecation.DeprecationMessageBuilder.willBeRemovedInGradle10

