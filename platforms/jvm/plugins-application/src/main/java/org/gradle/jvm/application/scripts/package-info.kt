/*
 * Copyright 2015 the original author or authors.
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
 * Classes that enable JVM application script generation.
 */
package org.gradle.jvm.application.scripts

import org.gradle.api.tasks.compile.JavaCompile.getOptions
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.api.tasks.JavaExec.getMainModule
import org.gradle.api.tasks.JavaExec.setClasspath
import org.gradle.api.tasks.JavaExec.getMainClass
import org.gradle.api.tasks.JavaExec.getJvmArguments
import org.gradle.api.tasks.JavaExec.getModularity
import org.gradle.api.tasks.internal.JavaExecExecutableUtils.getExecutableOverrideToolchainSpec
import org.gradle.util.internal.CollectionUtils.toStringList
import org.gradle.internal.os.OperatingSystem.Companion.current
import org.gradle.internal.os.OperatingSystem.isWindows
import org.gradle.internal.deprecation.DeprecationLogger.deprecateMethod
import org.gradle.internal.deprecation.DeprecationMessageBuilder.willBeRemovedInGradle10
import org.gradle.internal.deprecation.Documentation.AbstractBuilder.withUpgradeGuideSection
import org.gradle.internal.deprecation.DeprecationMessageBuilder.WithDocumentation.nagUser
import org.gradle.process.internal.JavaExecHandleFactory.newJavaExec
import org.gradle.process.internal.JavaExecHandleBuilder.setExecutable
import org.gradle.process.internal.JavaExecHandleBuilder.setClasspath
import org.gradle.process.internal.JavaExecHandleBuilder.setArgs
import org.gradle.process.internal.ExecHandle.state
import org.gradle.process.internal.JavaExecHandleBuilder.build
import org.gradle.process.internal.ExecHandle.start
import org.gradle.process.internal.ExecHandle.abort

