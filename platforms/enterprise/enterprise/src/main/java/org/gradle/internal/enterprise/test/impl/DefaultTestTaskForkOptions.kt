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
package org.gradle.internal.enterprise.test.impl

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import org.gradle.internal.enterprise.test.TestTaskForkOptions
import java.io.File
import java.util.stream.Stream
import java.util.stream.StreamSupport

internal class DefaultTestTaskForkOptions(
    private val workingDir: File,
    private val executable: String,
    private val javaMajorVersion: Int,
    private val classpath: Iterable<File>,
    private val modulePath: Iterable<File>,
    jvmArgs: MutableList<String>,
    environment: MutableMap<String, String>
) : TestTaskForkOptions {
    private val jvmArgs: MutableList<String>
    private val environment: MutableMap<String, String>

    init {
        this.jvmArgs = ImmutableList.copyOf<String>(jvmArgs)
        this.environment = ImmutableMap.copyOf<String, String>(environment)
    }

    override fun getWorkingDir(): File {
        return workingDir
    }

    override fun getExecutable(): String {
        return executable
    }

    override fun getJavaMajorVersion(): Int {
        return javaMajorVersion
    }

    override fun getClasspath(): Stream<File> {
        return StreamSupport.stream<File>(classpath.spliterator(), false)
    }

    override fun getModulePath(): Stream<File> {
        return StreamSupport.stream<File>(modulePath.spliterator(), false)
    }

    override fun getJvmArgs(): MutableList<String> {
        return jvmArgs
    }

    override fun getEnvironment(): MutableMap<String, String> {
        return environment
    }
}
