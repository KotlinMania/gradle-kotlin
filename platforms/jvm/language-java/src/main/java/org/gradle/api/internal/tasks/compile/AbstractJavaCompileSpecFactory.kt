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
package org.gradle.api.internal.tasks.compile

import org.gradle.api.internal.tasks.compile.JdkJavaCompiler.Companion.canBeUsed
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.internal.Factory
import org.gradle.internal.jvm.Jvm
import org.gradle.jvm.toolchain.JavaInstallationMetadata
import org.gradle.jvm.toolchain.internal.JavaExecutableUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

abstract class AbstractJavaCompileSpecFactory<T : JavaCompileSpec?>(private val compileOptions: CompileOptions, private val toolchain: JavaInstallationMetadata) : Factory<T?> {
    override fun create(): T? {
        val toolchainJavaHome = toolchain.installationPath.getAsFile()
        if (!toolchain.languageVersion.canCompileOrRun(8)) {
            LOGGER.info("Compilation mode: command line compilation")
            return getCommandLineSpec(Jvm.forHome(toolchainJavaHome).getJavacExecutable())
        }

        if (compileOptions.isFork) {
            @Suppress("deprecation") val forkJavaHome = compileOptions.forkOptions!!.javaHome
            if (forkJavaHome != null) {
                LOGGER.info("Compilation mode: command line compilation")
                return getCommandLineSpec(Jvm.forHome(forkJavaHome).getJavacExecutable())
            }

            val forkExecutable = compileOptions.forkOptions!!.executable
            if (forkExecutable != null) {
                LOGGER.info("Compilation mode: command line compilation")
                return getCommandLineSpec(JavaExecutableUtils.resolveExecutable(forkExecutable))
            }

            val languageVersion = toolchain.languageVersion.asInt()
            LOGGER.info("Compilation mode: forking compiler")
            return getForkingSpec(toolchainJavaHome, languageVersion)
        }

        if (toolchain.isCurrentJvm && canBeUsed()) {
            // Please keep it in mind, that when using TestKit with debug enabled (i.e. in embedded mode), this line won't be reached after Java 16 (JEP 396)
            // If you need this to be executed, add the necessary configs from JPMSConfiguration to the test runner executing Gradle
            LOGGER.info("Compilation mode: in-process compilation")
            return this.inProcessSpec
        }

        LOGGER.info("Compilation mode: default, forking compiler")
        return getForkingSpec(toolchainJavaHome, toolchain.languageVersion.asInt())
    }

    protected abstract fun getCommandLineSpec(executable: File?): T?

    protected abstract fun getForkingSpec(javaHome: File?, javaLanguageVersion: Int): T?

    protected abstract val inProcessSpec: T?

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(AbstractJavaCompileSpecFactory::class.java)
    }
}
