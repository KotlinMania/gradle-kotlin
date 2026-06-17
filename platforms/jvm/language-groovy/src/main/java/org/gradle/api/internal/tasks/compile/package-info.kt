/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.tasks.compile

import org.gradle.api.tasks.compile.CompileOptions.setIncremental
import org.gradle.util.internal.IncubationLogger.incubatingFeatureUsed
import org.gradle.api.internal.tasks.compile.GroovyCompileSpec.incrementalCompilationEnabled
import org.gradle.internal.service.ServiceRegistry.get
import org.gradle.api.internal.tasks.compile.CompilationSourceDirs.Companion.inferSourceRoots
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec.setSourcesRoots
import org.gradle.api.internal.tasks.compile.DefaultJvmLanguageCompileSpec.setSourceFiles
import org.gradle.api.internal.tasks.compile.DefaultJvmLanguageCompileSpec.setDestinationDir
import org.gradle.api.internal.tasks.compile.DefaultJvmLanguageCompileSpec.setWorkingDir
import org.gradle.api.internal.tasks.compile.DefaultJvmLanguageCompileSpec.setTempDir
import org.gradle.api.internal.tasks.compile.DefaultJvmLanguageCompileSpec.setCompileClasspath
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec.setAnnotationProcessorPath
import org.gradle.api.internal.tasks.compile.DefaultGroovyJavaJointCompileSpec.setGroovyClasspath
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec.setCompileOptions
import org.gradle.api.internal.tasks.compile.DefaultGroovyJavaJointCompileSpec.setGroovyCompileOptions
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec.getCompileOptions
import org.gradle.api.internal.tasks.compile.MinimalJavaCompileOptions.setSupportsCompilerApi
import org.gradle.api.tasks.compile.CompileOptions.isIncremental
import org.gradle.api.internal.tasks.compile.JavaCompileSpec.annotationProcessingConfigured
import org.gradle.api.internal.tasks.compile.DefaultGroovyJavaJointCompileSpec.getGroovyCompileOptions
import org.gradle.api.JavaVersion.Companion.toVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion.asInt
import org.gradle.api.JavaVersion.toString
import org.gradle.api.internal.tasks.compile.DefaultJvmLanguageCompileSpec.setSourceCompatibility
import org.gradle.api.internal.tasks.compile.DefaultJvmLanguageCompileSpec.setTargetCompatibility
import org.gradle.jvm.toolchain.JavaLanguageVersion.toString
import org.gradle.internal.file.Deleter.ensureEmptyDirectory
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.api.problems.ProblemId.Companion.create
import org.gradle.api.problems.internal.GradleCoreProblemGroup.compilation
import org.gradle.api.problems.internal.GradleCoreProblemGroup.CompilationProblemGroup.groovy
import org.gradle.api.problems.ProblemReporter.throwing
import org.gradle.api.problems.ProblemSpec.contextualLabel
import org.gradle.api.problems.ProblemSpec.solution
import org.gradle.internal.service.ServiceRegistration.addProvider
import org.gradle.api.internal.tasks.compile.MinimalGroovyCompileOptions.isFork
import org.gradle.api.logging.Logging.getLogger
import org.gradle.api.internal.tasks.compile.JvmLanguageCompileSpec.getSourceFiles
import org.gradle.api.internal.tasks.compile.JvmLanguageCompileSpec.setSourceFiles
import org.gradle.util.internal.CollectionUtils.toStringList
import org.gradle.api.logging.Logger.quiet
import org.gradle.api.internal.tasks.compile.JavaCompilerArgumentsBuilder.includeLauncherOptions
import org.gradle.api.internal.tasks.compile.JavaCompilerArgumentsBuilder.includeSourceFiles
import org.gradle.api.internal.tasks.compile.JavaCompilerArgumentsBuilder.build

