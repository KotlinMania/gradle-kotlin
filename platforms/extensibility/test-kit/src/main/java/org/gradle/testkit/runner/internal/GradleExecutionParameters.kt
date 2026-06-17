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
package org.gradle.testkit.runner.internal

import org.gradle.internal.classpath.ClassPath
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class GradleExecutionParameters(
    val gradleProvider: GradleProvider?,
    val gradleUserHome: File?,
    val projectDir: File?,
    val buildArgs: MutableList<String?>?,
    val jvmArgs: MutableList<String?>?,
    val injectedClassPath: ClassPath?,
    val isEmbedded: Boolean,
    val standardOutput: OutputStream?,
    val standardError: OutputStream?,
    val standardInput: InputStream?,
    val environment: MutableMap<String?, String?>?
)
