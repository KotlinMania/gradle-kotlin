/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.initialization

import org.gradle.cli.AbstractCommandLineConverter
import org.gradle.cli.CommandLineArgumentException
import org.gradle.cli.CommandLineConverter
import org.gradle.cli.CommandLineParser
import org.gradle.cli.ParsedCommandLine

class LayoutCommandLineConverter : AbstractCommandLineConverter<BuildLayoutParameters?>() {
    private val converter: CommandLineConverter<BuildLayoutParameters?> = BuildLayoutParametersBuildOptions().commandLineConverter()

    @Throws(CommandLineArgumentException::class)
    override fun convert(options: ParsedCommandLine?, target: BuildLayoutParameters?): BuildLayoutParameters? {
        converter.convert(options, target)
        return target
    }

    override fun configure(parser: CommandLineParser?) {
        converter.configure(parser)
    }
}
