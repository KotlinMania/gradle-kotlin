/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.internal.enterprise.test

import org.gradle.StartParameter.isNoBuildScan
import org.gradle.StartParameter.isBuildScan
import org.gradle.internal.service.ServiceRegistration.add
import org.gradle.internal.service.ServiceRegistry.find
import org.gradle.api.internal.StartParameterInternal.isUseEmptySettings
import org.gradle.internal.deprecation.DeprecationLogger.deprecateIndirectUsage
import org.gradle.internal.deprecation.DeprecationMessageBuilder.withAdvice
import org.gradle.internal.deprecation.DeprecationMessageBuilder.willBecomeAnErrorInGradle10
import org.gradle.internal.deprecation.Documentation.AbstractBuilder.withUpgradeGuideSection
import org.gradle.internal.deprecation.DeprecationMessageBuilder.WithDocumentation.nagUser
import org.gradle.internal.cc.impl.InputTrackingState.disableForCurrentThread
import org.gradle.internal.cc.impl.InputTrackingState.restoreForCurrentThread
import org.gradle.process.internal.JavaForkOptionsFactory.newJavaForkOptions

