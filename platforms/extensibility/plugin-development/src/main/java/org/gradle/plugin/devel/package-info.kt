/*
 * Copyright 2016 the original author or authors.
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
/**
 * Classes for assisting with plugin development.
 */
package org.gradle.plugin.devel

import org.gradle.api.logging.Logging.getLogger
import org.gradle.internal.deprecation.Documentation.Companion.userManual
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.service.ServiceRegistryBuilder.Companion.builder
import org.gradle.internal.service.ServiceRegistryBuilder.displayName
import org.gradle.internal.service.ServiceRegistryBuilder.provider
import org.gradle.internal.service.ServiceRegistration.add
import org.gradle.internal.service.scopes.GradleModuleServices.registerGlobalServices
import org.gradle.internal.service.ServiceRegistryBuilder.build
import org.gradle.internal.service.ServiceRegistry.getAll
import org.gradle.internal.service.ServiceRegistry.get
import org.gradle.internal.deprecation.Documentation.Companion.upgradeMinorGuide
import org.gradle.internal.deprecation.Documentation.Companion.upgradeMajorGuide
import org.gradle.api.internal.DocumentationRegistry.getDocumentationRecommendationFor
import org.gradle.internal.file.Deleter.ensureEmptyDirectory
import org.gradle.api.JavaVersion.majorVersion
import org.gradle.internal.deprecation.DeprecationLogger.deprecateIndirectUsage
import org.gradle.internal.deprecation.DeprecationMessageBuilder.withAdvice
import org.gradle.internal.deprecation.DeprecationMessageBuilder.withContext
import org.gradle.internal.deprecation.DeprecationMessageBuilder.willBecomeAnErrorInGradle10
import org.gradle.internal.deprecation.Documentation.AbstractBuilder.withUpgradeGuideSection
import org.gradle.internal.deprecation.DeprecationMessageBuilder.WithDocumentation.nagUser
import org.gradle.internal.deprecation.Documentation.getConsultDocumentationMessage

