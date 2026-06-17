/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.launcher.cli.converter

import org.gradle.cli.CommandLineConverter
import org.gradle.cli.CommandLineParser
import org.gradle.cli.ParsedCommandLine
import org.gradle.cli.SystemPropertiesCommandLineConverter
import org.gradle.internal.Cast
import org.gradle.launcher.configuration.InitialProperties
import org.gradle.process.internal.CurrentProcess
import java.util.Collections

class InitialPropertiesConverter {
    private val systemPropertiesCommandLineConverter: CommandLineConverter<MutableMap<String?, String?>> = SystemPropertiesCommandLineConverter()

    fun configure(parser: CommandLineParser?) {
        systemPropertiesCommandLineConverter.configure(parser)
    }

    fun convert(commandLine: ParsedCommandLine?): InitialProperties {
        val currentProcessJvmOptions = CurrentProcess(null).getJvmOptions()
        val target: MutableMap<String?, String?> = Cast.uncheckedCast<MutableMap<String?, String?>>(HashMap<String?, Any?>(currentProcessJvmOptions.immutableSystemProperties))!!
        val requestedSystemProperties = systemPropertiesCommandLineConverter.convert(commandLine, target)

        return object : InitialProperties {
            override fun getRequestedSystemProperties(): MutableMap<String?, String?> {
                return Collections.unmodifiableMap<String?, String?>(requestedSystemProperties)
            }
        }
    }
}
