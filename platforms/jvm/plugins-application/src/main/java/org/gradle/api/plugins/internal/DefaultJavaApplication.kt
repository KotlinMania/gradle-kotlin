/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.plugins.internal

import org.gradle.api.Project
import org.gradle.api.file.CopySpec
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaApplication

abstract class DefaultJavaApplication(objectFactory: ObjectFactory?, project: Project) : JavaApplication {
    private var applicationName: String? = null
    private var applicationDefaultJvmArgs: Iterable<String?> = ArrayList<String?>()
    private var executableDirectory = "bin"
    private var applicationDistribution: CopySpec

    init {
        this.applicationDistribution = project.copySpec()
    }

    override fun getApplicationName(): String {
        return applicationName!!
    }

    override fun setApplicationName(applicationName: String) {
        this.applicationName = applicationName
    }

    override fun getApplicationDefaultJvmArgs(): Iterable<String?> {
        return applicationDefaultJvmArgs
    }

    override fun setApplicationDefaultJvmArgs(applicationDefaultJvmArgs: Iterable<String?>) {
        this.applicationDefaultJvmArgs = applicationDefaultJvmArgs
    }

    override fun getExecutableDir(): String {
        return executableDirectory
    }

    override fun setExecutableDir(executableDir: String) {
        this.executableDirectory = executableDir
    }

    override fun getApplicationDistribution(): CopySpec {
        return applicationDistribution
    }

    override fun setApplicationDistribution(applicationDistribution: CopySpec) {
        this.applicationDistribution = applicationDistribution
    }
}
