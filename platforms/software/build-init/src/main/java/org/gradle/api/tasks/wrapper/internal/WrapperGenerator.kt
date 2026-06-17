/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.api.tasks.wrapper.internal

import com.google.common.collect.ImmutableList
import com.google.common.io.ByteStreams
import org.gradle.api.GradleException
import org.gradle.api.internal.plugins.ExecutableJar
import org.gradle.api.internal.plugins.StartScriptGenerator
import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.util.PropertiesUtils
import org.gradle.util.GradleVersion
import org.gradle.util.internal.DefaultGradleVersion
import org.gradle.util.internal.DistributionLocator
import org.gradle.util.internal.GFileUtils
import org.gradle.wrapper.WrapperExecutor
import org.jspecify.annotations.NullMarked
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.util.Properties

@NullMarked
object WrapperGenerator {
    fun getPropertiesFile(jarFileDestination: File): File {
        return File(jarFileDestination.getParentFile(), jarFileDestination.getName().replace("\\.jar$".toRegex(), ".properties"))
    }

    fun getBatchScript(scriptFile: File): File {
        return File(scriptFile.getParentFile(), scriptFile.getName().replaceFirst("(\\.[^\\.]+)?$".toRegex(), ".bat"))
    }

    fun getDistributionUrl(gradleVersion: GradleVersion, distributionType: Wrapper.DistributionType): String {
        val distType = distributionType.name.lowercase()
        return DistributionLocator().getDistributionFor(gradleVersion, distType).toASCIIString()
    }

    fun generate(
        archiveBase: Wrapper.PathBase, archivePath: String,
        distributionBase: Wrapper.PathBase, distributionPath: String,
        distributionSha256Sum: String?,
        wrapperPropertiesOutputFile: File,
        wrapperJarOutputFile: File, jarFileRelativePath: String,
        unixScript: File, batchScript: File,
        distributionUrl: String?,
        validateDistributionUrl: Boolean,
        networkTimeout: Int?,
        retries: Int?,
        retryBackOffMs: Int?
    ) {
        writeProperties(
            wrapperPropertiesOutputFile,
            distributionUrl,
            distributionSha256Sum,
            distributionBase,
            distributionPath,
            archiveBase,
            archivePath,
            networkTimeout,
            validateDistributionUrl,
            retries,
            retryBackOffMs
        )
        writeWrapperJar(wrapperJarOutputFile)
        writeScripts(jarFileRelativePath, unixScript, batchScript)
    }

    private fun writeProperties(
        propertiesFileDestination: File,
        distributionUrl: String?,
        distributionSha256Sum: String?,
        distributionBase: Wrapper.PathBase,
        distributionPath: String,
        archiveBase: Wrapper.PathBase,
        archivePath: String,
        networkTimeout: Int?,
        validateDistributionUrl: Boolean,
        retries: Int?,
        retryBackOffMs: Int?
    ) {
        val wrapperProperties = Properties()
        wrapperProperties.put(WrapperExecutor.DISTRIBUTION_URL_PROPERTY, distributionUrl)
        if (distributionSha256Sum != null) {
            wrapperProperties.put(WrapperExecutor.DISTRIBUTION_SHA_256_SUM, distributionSha256Sum)
        }
        wrapperProperties.put(WrapperExecutor.DISTRIBUTION_BASE_PROPERTY, distributionBase.toString())
        wrapperProperties.put(WrapperExecutor.DISTRIBUTION_PATH_PROPERTY, distributionPath)
        wrapperProperties.put(WrapperExecutor.ZIP_STORE_BASE_PROPERTY, archiveBase.toString())
        wrapperProperties.put(WrapperExecutor.ZIP_STORE_PATH_PROPERTY, archivePath)
        if (networkTimeout != null) {
            wrapperProperties.put(WrapperExecutor.NETWORK_TIMEOUT_PROPERTY, networkTimeout.toString())
        }
        wrapperProperties.put(WrapperExecutor.VALIDATE_DISTRIBUTION_URL, validateDistributionUrl.toString())
        if (retries != null) {
            wrapperProperties.put(WrapperExecutor.RETRIES_PROPERTY, retries.toString())
        }
        if (retryBackOffMs != null) {
            wrapperProperties.put(WrapperExecutor.RETRY_BACK_OFF_PROPERTY, retryBackOffMs.toString())
        }
        GFileUtils.parentMkdirs(propertiesFileDestination)
        try {
            PropertiesUtils.store(wrapperProperties, propertiesFileDestination)
        } catch (e: IOException) {
            throw throwAsUncheckedException(e)
        }
    }

    private fun writeWrapperJar(destination: File) {
        val jarFileSource = Wrapper::class.java.getResource("/gradle-wrapper.jar")
        if (jarFileSource == null) {
            throw GradleException("Cannot locate wrapper JAR resource.")
        }
        try {
            jarFileSource.openStream().use { `in` ->
                Files.newOutputStream(destination.toPath()).use { out ->
                    ByteStreams.copy(`in`, out)
                }
            }
        } catch (e: IOException) {
            throw UncheckedIOException("Failed to write wrapper JAR to " + destination, e)
        }
    }

    private fun writeScripts(jarFileRelativePath: String, unixScript: File, batchScript: File) {
        val generator = StartScriptGenerator()
        generator.setApplicationName("gradlew")
        generator.setGitRef(DefaultGradleVersion.current().getScriptTemplateGitRevision())
        generator.setEntryPoint(ExecutableJar(jarFileRelativePath))
        generator.setClasspath(mutableListOf<String?>())
        generator.setOptsEnvironmentVar("GRADLE_OPTS")
        generator.setAppNameSystemProperty("org.gradle.appname")
        generator.setScriptRelPath(unixScript.getName())
        generator.setDefaultJvmOpts(ImmutableList.of<String?>("-Xmx64m", "-Xms64m"))

        generator.generateUnixScript(unixScript)
        generator.generateWindowsScript(batchScript)
    }
}
