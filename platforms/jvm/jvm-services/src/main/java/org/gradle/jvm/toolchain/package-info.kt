/*
 * Copyright 2025 the original author or authors.
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
 * Defines tools and configuration options that can build things that run on the JVM.
 */
package org.gradle.jvm.toolchain

import org.gradle.api.JavaVersion.Companion.toVersion
import org.gradle.api.internal.jvm.JavaVersionParser.parseMajorVersion
import org.gradle.internal.serialization.Cached.get
import org.gradle.internal.os.OperatingSystem.Companion.current
import org.gradle.internal.os.OperatingSystem.getExecutableName
import org.gradle.process.internal.ClientExecHandleBuilderFactory.newExecHandleBuilder
import org.gradle.process.internal.ClientExecHandleBuilder.setWorkingDir
import org.gradle.process.internal.ClientExecHandleBuilder.executable
import org.gradle.process.internal.ClientExecHandleBuilder.args
import org.gradle.process.internal.ClientExecHandleBuilder.setStandardOutput
import org.gradle.process.internal.ClientExecHandleBuilder.setErrorOutput
import org.gradle.process.internal.BaseExecHandleBuilder.build
import org.gradle.process.internal.ExecHandle.start
import org.gradle.process.internal.ExecHandle.waitForFinish
import org.gradle.internal.serialize.Decoder.readString
import org.gradle.internal.serialize.Encoder.writeString
import org.gradle.api.logging.Logger.log
import org.gradle.api.logging.Logging.getLogger
import org.gradle.internal.logging.progress.ProgressLoggerFactory.newOperation
import org.gradle.internal.logging.progress.ProgressLogger.start
import org.gradle.internal.logging.progress.ProgressLogger.progress
import org.gradle.internal.logging.progress.ProgressLogger.completed
import org.gradle.internal.os.OperatingSystem.isMacOsX
import org.gradle.internal.serialize.BaseSerializerFactory.getSerializerFor
import org.gradle.process.internal.ClientExecHandleBuilder.commandLine
import org.gradle.process.internal.ClientExecHandleBuilder.environment
import org.gradle.process.ExecResult.assertNormalExitValue
import org.gradle.internal.os.OperatingSystem.isLinux
import org.gradle.internal.os.OperatingSystem.isWindows

