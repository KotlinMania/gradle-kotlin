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
 * Tasks that add support for Scala language.
 */
package org.gradle.language.scala.tasks

import org.gradle.jvm.toolchain.JavaLanguageVersion.asInt
import org.gradle.internal.service.ServiceRegistry.get
import org.gradle.api.plugins.jvm.internal.JvmEcosystemUtilities.configureAsRuntimeClasspath
import org.gradle.api.plugins.internal.JavaPluginHelper.getJavaComponent
import org.gradle.api.tasks.SourceSet.getCompileTaskName
import org.gradle.api.plugins.internal.JvmPluginsHelper.compileAgainstJavaOutputs
import org.gradle.api.plugins.internal.JvmPluginsHelper.configureAnnotationProcessorPath
import org.gradle.api.plugins.internal.JvmPluginsHelper.configureOutputDirectoryForSourceSet
import org.gradle.api.tasks.compile.AbstractCompile.classpath
import org.gradle.api.JavaVersion.toString
import org.gradle.api.plugins.internal.DefaultJavaPluginExtension.rawSourceCompatibility
import org.gradle.api.JavaVersion.Companion.toVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion.toString
import org.gradle.api.plugins.internal.DefaultJavaPluginExtension.rawTargetCompatibility
import org.gradle.api.internal.tasks.compile.BaseForkOptionsConverter.transform
import org.gradle.api.internal.tasks.compile.daemon.AbstractDaemonCompiler.mergeForkOptions
import org.gradle.api.logging.Logging.getLogger
import org.gradle.api.internal.tasks.compile.JvmLanguageCompileSpec.setSourceFiles
import org.gradle.api.internal.tasks.compile.JvmLanguageCompileSpec.getSourceFiles
import org.gradle.api.internal.tasks.compile.JavaCompileSpec.getDestinationDir
import org.gradle.util.internal.CollectionUtils.toStringList
import org.gradle.api.logging.Logger.quiet
import org.gradle.api.internal.tasks.compile.JavaCompilerArgumentsBuilder.includeLauncherOptions
import org.gradle.api.internal.tasks.compile.JavaCompilerArgumentsBuilder.includeSourceFiles
import org.gradle.api.internal.tasks.compile.JavaCompilerArgumentsBuilder.build
import org.gradle.api.internal.tasks.compile.CompilerForkUtils.doNotCacheIfForkingViaExecutable
import org.gradle.api.internal.tasks.compile.DefaultJvmLanguageCompileSpec.setSourceFiles
import org.gradle.api.internal.tasks.compile.DefaultJvmLanguageCompileSpec.setDestinationDir
import org.gradle.api.internal.tasks.compile.DefaultJvmLanguageCompileSpec.setWorkingDir
import org.gradle.api.internal.tasks.compile.DefaultJvmLanguageCompileSpec.setTempDir
import org.gradle.api.internal.tasks.compile.DefaultJvmLanguageCompileSpec.setCompileClasspath
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec.setCompileOptions
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec.setAnnotationProcessorPath
import org.gradle.api.internal.tasks.compile.DefaultJvmLanguageCompileSpec.setSourceCompatibility
import org.gradle.api.internal.tasks.compile.DefaultJvmLanguageCompileSpec.setTargetCompatibility
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.api.JavaVersion.Companion.current
import org.gradle.api.JavaVersion.majorVersion

