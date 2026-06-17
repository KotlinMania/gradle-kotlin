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
/**
 * This package contains a number of utilities used to facilitate the
 * integration of Java plugins. It provides the implementation of the
 * [JvmPluginServices] service.
 */
package org.gradle.api.plugins.jvm.internal

import org.gradle.api.tasks.SourceSet.Companion.isMain
import org.gradle.internal.service.ServiceRegistry.get
import org.gradle.api.internal.tasks.DefaultSourceSet.configurationNameOf
import org.gradle.api.plugins.jvm.internal.JvmLanguageUtilities.useDefaultTargetPlatformInference
import org.gradle.api.plugins.jvm.internal.JvmPluginServices.configureAsApiElements
import org.gradle.api.plugins.jvm.internal.JvmPluginServices.configureAsRuntimeElements
import org.gradle.api.plugins.jvm.internal.JvmPluginServices.configureClassesDirectoryVariant
import org.gradle.api.plugins.jvm.internal.JvmPluginServices.configureResourcesDirectoryVariant
import org.gradle.api.plugins.jvm.internal.JvmPluginServices.configureAsSources
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.api.internal.jvm.JavaVersionParser.parseMajorVersion
import org.gradle.api.tasks.compile.AbstractCompile.classpath
import org.gradle.api.plugins.jvm.internal.JvmLanguageSourceDirectoryBuilder.compiledBy
import org.gradle.internal.Cast.cast
import org.gradle.api.internal.tasks.DefaultSourceSetOutput.getClassesDirs
import org.gradle.api.internal.tasks.DefaultSourceSetOutput.getGeneratedSourcesDirs
import org.gradle.api.JavaVersion.toString
import org.gradle.api.JavaVersion.Companion.toVersion
import org.gradle.internal.deprecation.DeprecationLogger.deprecateBehaviour
import org.gradle.internal.deprecation.DeprecationMessageBuilder.withAdvice
import org.gradle.internal.deprecation.DeprecationMessageBuilder.willBecomeAnErrorInGradle10
import org.gradle.internal.deprecation.Documentation.AbstractBuilder.withUpgradeGuideSection
import org.gradle.internal.deprecation.DeprecationMessageBuilder.WithDocumentation.nagUser
import org.gradle.api.plugins.jvm.internal.JvmFeatureInternal.withApi
import org.gradle.internal.service.ServiceRegistration.add
import org.gradle.jvm.toolchain.JavaLanguageVersion.toString
import org.gradle.api.JavaVersion.Companion.current
import org.gradle.api.plugins.jvm.internal.JvmFeatureInternal.maybeRegisterJavadocElements
import org.gradle.api.plugins.jvm.internal.JvmFeatureInternal.maybeRegisterSourcesElements
import org.gradle.util.internal.CollectionUtils.join
import org.gradle.internal.deprecation.DeprecationMessageBuilder.withContext
import org.gradle.api.internal.tasks.compile.CompilationSourceDirs.Companion.inferSourceRoots
import org.gradle.api.tasks.compile.JavaCompile.getOptions
import org.gradle.api.internal.tasks.compile.JavaCompileExecutableUtils.getExecutableOverrideToolchainSpec
import org.gradle.api.internal.tasks.DefaultSourceSetOutput.setResourcesContributor
import org.gradle.api.tasks.SourceSet.compiledBy
import org.gradle.api.plugins.jvm.internal.JvmPluginServices.configureAsCompileClasspath
import org.gradle.api.plugins.jvm.internal.JvmEcosystemUtilities.configureAsRuntimeClasspath
import org.gradle.internal.deprecation.DeprecationLogger.deprecateTask
import org.gradle.internal.deprecation.DeprecationMessageBuilder.willBeRemovedInGradle10
import org.gradle.api.tasks.internal.JavaExecExecutableUtils.getExecutableOverrideToolchainSpec

