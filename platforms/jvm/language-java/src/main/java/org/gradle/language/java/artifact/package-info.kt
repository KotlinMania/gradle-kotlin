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
 * Classes representing artifacts relevant to the Java language.
 */
package org.gradle.language.java.artifact

import org.gradle.api.tasks.compile.CompileOptions.isIncremental
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec.getCompileOptions
import org.gradle.api.internal.tasks.compile.MinimalJavaCompileOptions.setSupportsIncrementalCompilationAfterFailure
import org.gradle.api.internal.tasks.compile.MinimalJavaCompileOptions.setSupportsCompilerApi
import org.gradle.api.internal.tasks.compile.MinimalJavaCompileOptions.setSupportsConstantAnalysis
import org.gradle.internal.service.ServiceRegistry.get
import org.gradle.api.internal.tasks.compile.DefaultJvmLanguageCompileSpec.setSourceFiles
import org.gradle.api.internal.tasks.compile.CompilationSourceDirs.Companion.inferSourceRoots
import org.gradle.api.internal.tasks.compile.DefaultJvmLanguageCompileSpec.setDestinationDir
import org.gradle.api.internal.tasks.compile.DefaultJvmLanguageCompileSpec.setWorkingDir
import org.gradle.api.internal.tasks.compile.DefaultJvmLanguageCompileSpec.setTempDir
import org.gradle.api.internal.tasks.compile.DefaultJvmLanguageCompileSpec.setCompileClasspath
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec.setModulePath
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec.setAnnotationProcessorPath
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec.setSourcesRoots
import org.gradle.api.tasks.compile.CompileOptions.isFork
import org.gradle.jvm.toolchain.JavaLanguageVersion.canCompileOrRun
import org.gradle.api.JavaVersion.Companion.toVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion.asInt
import org.gradle.api.internal.tasks.compile.DefaultJvmLanguageCompileSpec.setRelease
import org.gradle.api.JavaVersion.toString
import org.gradle.api.internal.tasks.compile.DefaultJvmLanguageCompileSpec.setSourceCompatibility
import org.gradle.api.internal.tasks.compile.DefaultJvmLanguageCompileSpec.setTargetCompatibility
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec.setCompileOptions
import org.gradle.process.internal.DefaultJavaExecSpec.getJvmArguments
import org.gradle.process.internal.DefaultJavaExecSpec.getMainClass
import org.gradle.process.internal.DefaultJavaExecSpec.getMainModule
import org.gradle.process.internal.DefaultJavaExecSpec.getModularity
import org.gradle.process.internal.DefaultJavaForkOptions.setExtraJvmArgs
import org.gradle.process.internal.ExecActionFactory.newJavaExecAction
import org.gradle.process.internal.DefaultJavaExecSpec.copyTo
import org.gradle.process.internal.JavaExecAction.execute
import org.gradle.process.internal.DefaultJavaExecSpec.getAllJvmArgs
import org.gradle.process.internal.DefaultJavaExecSpec.setAllJvmArgs
import org.gradle.process.internal.DefaultJavaForkOptions.checkDebugConfiguration
import org.gradle.process.internal.DefaultJavaForkOptions.getSystemProperties
import org.gradle.process.internal.DefaultJavaForkOptions.setSystemProperties
import org.gradle.process.internal.DefaultJavaForkOptions.systemProperties
import org.gradle.process.internal.DefaultJavaForkOptions.systemProperty
import org.gradle.process.internal.DefaultJavaForkOptions.getBootstrapClasspath
import org.gradle.process.internal.DefaultJavaForkOptions.setBootstrapClasspath
import org.gradle.process.internal.DefaultJavaForkOptions.bootstrapClasspath
import org.gradle.process.internal.DefaultJavaForkOptions.getMinHeapSize
import org.gradle.process.internal.DefaultJavaForkOptions.setMinHeapSize
import org.gradle.process.internal.DefaultJavaForkOptions.getDefaultCharacterEncoding
import org.gradle.process.internal.DefaultJavaForkOptions.setDefaultCharacterEncoding
import org.gradle.process.internal.DefaultJavaForkOptions.getMaxHeapSize
import org.gradle.process.internal.DefaultJavaForkOptions.setMaxHeapSize
import org.gradle.process.internal.DefaultJavaForkOptions.getEnableAssertions
import org.gradle.process.internal.DefaultJavaForkOptions.setEnableAssertions
import org.gradle.process.internal.DefaultJavaForkOptions.getDebug
import org.gradle.process.internal.DefaultJavaForkOptions.setDebug
import org.gradle.process.internal.DefaultJavaForkOptions.getDebugOptions
import org.gradle.process.internal.DefaultJavaForkOptions.debugOptions
import org.gradle.process.internal.DefaultJavaExecSpec.getArgs
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.process.internal.DefaultJavaExecSpec.setArgs
import org.gradle.process.internal.DefaultJavaExecSpec.args
import org.gradle.process.internal.DefaultJavaExecSpec.getArgumentProviders
import org.gradle.process.internal.DefaultJavaExecSpec.setClasspath
import org.gradle.process.internal.DefaultJavaExecSpec.classpath
import org.gradle.process.internal.DefaultJavaExecSpec.getClasspath
import org.gradle.process.internal.DefaultJavaForkOptions.copyTo
import org.gradle.process.internal.DefaultJavaExecSpec.setStandardInput
import org.gradle.process.internal.DefaultJavaExecSpec.getStandardInput
import org.gradle.process.internal.DefaultJavaExecSpec.setStandardOutput
import org.gradle.process.internal.DefaultJavaExecSpec.getStandardOutput
import org.gradle.process.internal.DefaultJavaExecSpec.setErrorOutput
import org.gradle.process.internal.DefaultJavaExecSpec.getErrorOutput
import org.gradle.process.internal.DefaultJavaExecSpec.setIgnoreExitValue
import org.gradle.process.internal.DefaultJavaExecSpec.isIgnoreExitValue
import org.gradle.process.internal.DefaultJavaExecSpec.getCommandLine
import org.gradle.process.internal.DefaultJavaForkOptions.getJvmArgumentProviders
import org.gradle.api.logging.Logging.getLogger
import org.gradle.internal.serialize.Decoder.readString
import org.gradle.internal.serialize.Decoder.readSmallInt
import org.gradle.internal.serialize.Encoder.writeString
import org.gradle.internal.serialize.Encoder.writeSmallInt
import org.gradle.internal.serialize.Decoder.readNullableString
import org.gradle.internal.serialize.AbstractCollectionSerializer.read
import org.gradle.internal.serialize.Encoder.writeNullableString
import org.gradle.internal.serialize.AbstractCollectionSerializer.write
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.DependentsSet.Companion.empty
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.DependentsSet.Companion.dependencyToAll
import org.gradle.internal.serialize.Decoder.readInt
import org.gradle.internal.serialize.Encoder.writeInt
import org.gradle.internal.serialize.HierarchicalNameSerializer.read
import org.gradle.internal.serialize.HashCodeSerializer.read
import org.gradle.internal.serialize.HierarchicalNameSerializer.write
import org.gradle.internal.serialize.HashCodeSerializer.write
import org.gradle.internal.time.Time.startTimer
import org.gradle.api.internal.tasks.compile.JvmLanguageCompileSpec.setSourceFiles
import org.gradle.api.internal.tasks.compile.JavaCompileSpec.classesToCompile
import org.gradle.api.internal.tasks.compile.JavaCompileSpec.getDestinationDir
import org.gradle.internal.serialize.MapSerializer.read
import org.gradle.internal.serialize.MapSerializer.write
import org.gradle.internal.serialize.BaseSerializerFactory.getSerializerFor
import org.gradle.internal.serialize.Serializer.read
import org.gradle.internal.serialize.Serializer.write
import org.gradle.internal.serialize.Decoder.readByte
import org.gradle.internal.serialize.Encoder.writeByte
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.constants.ConstantToDependentsMapping.Companion.empty
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.constants.ConstantToDependentsMapping.Companion.builder
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.constants.ConstantToDependentsMapping.getConstantDependentsForClass
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.constants.ConstantToDependentsMappingBuilder.addAccessibleDependents
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.constants.ConstantToDependentsMappingBuilder.addPrivateDependents
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.constants.ConstantToDependentsMappingBuilder.build
import org.gradle.internal.serialize.Decoder.readBoolean
import org.gradle.internal.serialize.Encoder.writeBoolean
import org.gradle.api.internal.tasks.compile.CompilationFailedException.getCompilerPartialResult
import org.gradle.internal.file.Deleter.ensureEmptyDirectory
import org.gradle.internal.file.Deleter.deleteRecursively
import org.gradle.api.internal.tasks.compile.ApiCompilerResult.sourceClassesMapping
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingResult.generatedAggregatingTypes
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingResult.generatedTypesWithIsolatedOrigin
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingResult.generatedAggregatingResources
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingResult.generatedResourcesWithIsolatedOrigin
import org.gradle.api.internal.tasks.compile.JvmLanguageCompileSpec.getSourceFiles
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.constants.ConstantsAnalysisResult.constantToDependentsMapping
import org.gradle.process.internal.ClientExecHandleBuilderFactory.newExecHandleBuilder
import org.gradle.process.internal.ClientExecHandleBuilder.executable
import org.gradle.process.internal.ClientExecHandleBuilder.args
import org.gradle.process.internal.BaseExecHandleBuilder.build
import org.gradle.process.internal.ExecHandle.start
import org.gradle.process.internal.ExecHandle.waitForFinish
import org.gradle.util.internal.CollectionUtils.toStringList
import org.gradle.api.logging.Logger.quiet
import org.gradle.api.internal.tasks.compile.JavaCompilerArgumentsBuilder.includeLauncherOptions
import org.gradle.api.internal.tasks.compile.JavaCompilerArgumentsBuilder.includeSourceFiles
import org.gradle.api.internal.tasks.compile.JavaCompilerArgumentsBuilder.build
import org.gradle.api.internal.tasks.compile.JdkJavaCompiler.Companion.canBeUsed
import org.gradle.api.internal.tasks.compile.JavaCompilerArgumentsBuilder.includeMainOptions
import org.gradle.api.internal.tasks.compile.JavaCompilerArgumentsBuilder.includeClasspath
import org.gradle.internal.service.ServiceRegistration.addProvider

