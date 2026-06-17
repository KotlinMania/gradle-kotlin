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
package org.gradle.launcher.cli

import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.cli.CommandLineArgumentException
import org.gradle.cli.CommandLineParser
import org.gradle.cli.ParsedCommandLine
import org.gradle.initialization.layout.BuildLayoutFactory
import org.gradle.jvm.toolchain.internal.ToolchainConfiguration
import org.gradle.launcher.cli.converter.BuildLayoutConverter
import org.gradle.launcher.cli.converter.BuildOptionBackedConverter
import org.gradle.launcher.cli.converter.InitialPropertiesConverter
import org.gradle.launcher.cli.converter.LayoutToPropertiesConverter
import org.gradle.launcher.cli.converter.StartParameterConverter
import org.gradle.launcher.daemon.configuration.DaemonBuildOptions
import org.gradle.launcher.daemon.configuration.DaemonParameters
import org.gradle.launcher.daemon.toolchain.ToolchainBuildOptions
import java.io.File

class BuildEnvironmentConfigurationConverter internal constructor(
    private val initialPropertiesConverter: InitialPropertiesConverter,
    private val buildLayoutConverter: BuildLayoutConverter,
    private val layoutToPropertiesConverter: LayoutToPropertiesConverter,
    private val startParameterConverter: StartParameterConverter,
    private val daemonParametersConverter: BuildOptionBackedConverter<DaemonParameters?>,
    private val fileCollectionFactory: FileCollectionFactory
) {
    private val toolchainConfigurationBuildOptionBackedConverter: BuildOptionBackedConverter<ToolchainConfiguration?>

    init {
        this.toolchainConfigurationBuildOptionBackedConverter = BuildOptionBackedConverter<ToolchainConfiguration?>(ToolchainBuildOptions.forToolChainConfiguration())
    }

    constructor(buildLayoutFactory: BuildLayoutFactory, fileCollectionFactory: FileCollectionFactory) : this(
        InitialPropertiesConverter(),
        BuildLayoutConverter(),
        LayoutToPropertiesConverter(buildLayoutFactory),
        StartParameterConverter(),
        BuildOptionBackedConverter<DaemonParameters?>(DaemonBuildOptions()),
        fileCollectionFactory
    )

    @Throws(CommandLineArgumentException::class)
    fun convertParameters(args: ParsedCommandLine, currentDir: File?): Parameters {
        val environmentVariables: MutableMap<String, String> = HashMap<String, String>(System.getenv())
        val initialProperties = initialPropertiesConverter.convert(args)
        val buildLayout = buildLayoutConverter.convert(initialProperties, args, currentDir)
        val properties = layoutToPropertiesConverter.convert(initialProperties, buildLayout)
        val startParameter = StartParameterInternal()
        startParameterConverter.convert(args, buildLayout, properties, environmentVariables, startParameter)

        val daemonParameters = DaemonParameters(buildLayout.getGradleUserHomeDir(), fileCollectionFactory, properties.getRequestedSystemProperties(), environmentVariables)
        daemonParametersConverter.convert(args, properties.getProperties(), environmentVariables, daemonParameters)

        // This is a workaround to maintain existing behavior that allowed
        // toolchain-specific properties to be specified with -P instead of -D
        val gradlePropertiesAsSeenByToolchains: MutableMap<String, String> = HashMap<String, String>()
        gradlePropertiesAsSeenByToolchains.putAll(properties.getProperties())
        gradlePropertiesAsSeenByToolchains.putAll(startParameter.getProjectPropertiesUntracked())
        toolchainConfigurationBuildOptionBackedConverter.convert(args, gradlePropertiesAsSeenByToolchains, environmentVariables, daemonParameters.getToolchainConfiguration())
        daemonParameters.setRequestedJvmCriteriaFromMap(properties.getDaemonJvmProperties())

        return Parameters(startParameter, daemonParameters, buildLayout, properties)
    }

    fun configure(parser: CommandLineParser) {
        initialPropertiesConverter.configure(parser)
        buildLayoutConverter.configure(parser)
        startParameterConverter.configure(parser)
        daemonParametersConverter.configure(parser)
    }
}
