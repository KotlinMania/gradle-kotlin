/*
 * Copyright 2014 the original author or authors.
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
 * Build init plugins.
 */
package org.gradle.buildinit.plugins

import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.api.internal.plugins.StartScriptGenerator.setApplicationName
import org.gradle.api.internal.plugins.StartScriptGenerator.setGitRef
import org.gradle.api.internal.plugins.StartScriptGenerator.setEntryPoint
import org.gradle.api.internal.plugins.StartScriptGenerator.setClasspath
import org.gradle.api.internal.plugins.StartScriptGenerator.setOptsEnvironmentVar
import org.gradle.api.internal.plugins.StartScriptGenerator.setAppNameSystemProperty
import org.gradle.api.internal.plugins.StartScriptGenerator.setScriptRelPath
import org.gradle.api.internal.plugins.StartScriptGenerator.setDefaultJvmOpts
import org.gradle.api.internal.plugins.StartScriptGenerator.generateUnixScript
import org.gradle.api.internal.plugins.StartScriptGenerator.generateWindowsScript
import org.gradle.StartParameter.isOffline
import org.gradle.wrapper.Download.sendHeadRequest
import org.gradle.util.internal.WrapperDistributionUrlConverter.convertDistributionUrl
import org.gradle.internal.service.ServiceRegistry.get
import org.gradle.internal.deprecation.DeprecationLogger.deprecateMethod
import org.gradle.internal.deprecation.DeprecationMessageBuilder.WithReplacement.replaceWith
import org.gradle.internal.deprecation.DeprecationMessageBuilder.willBeRemovedInGradle10
import org.gradle.internal.deprecation.Documentation.AbstractBuilder.withUpgradeGuideSection
import org.gradle.internal.deprecation.DeprecationMessageBuilder.WithDocumentation.nagUser
import org.gradle.api.logging.Logger.lifecycle
import org.gradle.jvm.toolchain.JavaLanguageVersion.Companion.of
import org.gradle.internal.logging.text.TreeFormatter.node
import org.gradle.internal.logging.text.TreeFormatter.startChildren
import org.gradle.internal.logging.text.TreeFormatter.endChildren
import org.gradle.internal.logging.text.TreeFormatter.toString
import org.gradle.internal.service.ServiceRegistration.add
import org.gradle.internal.service.ServiceRegistration.addProvider
import org.gradle.internal.Cast.uncheckedNonnullCast
import org.gradle.jvm.toolchain.JavaLanguageVersion.asInt
import org.gradle.internal.deprecation.Documentation.Companion.userManual
import org.gradle.api.internal.DocumentationRegistry.getDocumentationRecommendationFor
import org.gradle.api.internal.DocumentationRegistry.sampleForMessage
import org.gradle.api.internal.DocumentationRegistry.getSampleForMessage
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform.Companion.host
import org.gradle.nativeplatform.platform.internal.OperatingSystemInternal.toFamilyName
import org.gradle.nativeplatform.platform.Architecture.getName
import org.gradle.api.internal.DocumentationRegistry.getDocumentationFor
import org.gradle.internal.os.OperatingSystem.Companion.current
import org.gradle.internal.os.OperatingSystem.isWindows
import org.gradle.internal.Factory.create

