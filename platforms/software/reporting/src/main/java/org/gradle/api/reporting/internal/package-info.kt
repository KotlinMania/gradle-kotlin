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
package org.gradle.api.reporting.internal

import org.gradle.reporting.HtmlReportRenderer.renderRawSinglePage
import org.gradle.reporting.HtmlPageBuilder.requireResource
import org.gradle.reporting.HtmlPageBuilder.formatDate
import org.gradle.util.internal.CollectionUtils.collect
import org.gradle.util.internal.CollectionUtils.flattenCollections
import org.gradle.internal.deprecation.DeprecationLogger.whileDisabled
import org.gradle.internal.Cast.uncheckedNonnullCast
import org.gradle.internal.deprecation.DeprecationLogger.deprecateMethod
import org.gradle.internal.deprecation.DeprecationMessageBuilder.WithReplacement.replaceWith
import org.gradle.internal.deprecation.DeprecationMessageBuilder.willBeRemovedInGradle10
import org.gradle.internal.deprecation.Documentation.AbstractBuilder.withUpgradeGuideSection
import org.gradle.internal.deprecation.DeprecationMessageBuilder.WithDocumentation.nagUser

