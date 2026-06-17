/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.Transformer
import org.gradle.api.internal.file.BasicFileResolver
import org.gradle.cli.OptionCategory
import org.gradle.internal.buildoption.BuildOption
import org.gradle.internal.buildoption.BuildOptionSet
import org.gradle.internal.buildoption.CommandLineOptionConfiguration
import org.gradle.internal.buildoption.Origin
import org.gradle.internal.buildoption.StringBuildOption
import java.io.File
import java.util.Collections

class BuildLayoutParametersBuildOptions : BuildOptionSet<BuildLayoutParameters?>() {
    override fun getAllOptions(): MutableList<BuildOption<in BuildLayoutParameters?>?> {
        return options!!
    }

    class GradleUserHomeOption : StringBuildOption<BuildLayoutParameters?>(
        BuildLayoutParameters.GRADLE_USER_HOME_PROPERTY_KEY,
        CommandLineOptionConfiguration.create("gradle-user-home", "g", "Specifies the Gradle user home directory. Default is ~/.gradle.")
    ) {
        override fun applyTo(value: String, settings: BuildLayoutParameters, origin: Origin) {
            val resolver: Transformer<File?, String?> = BasicFileResolver(settings.getCurrentDir())
            settings.setGradleUserHomeDir(resolver.transform(value))
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.CONFIGURATION
        }
    }

    class ProjectDirOption :
        StringBuildOption<BuildLayoutParameters?>(null, CommandLineOptionConfiguration.create("project-dir", "p", "Specifies the start directory for Gradle. Default is the current directory.")) {
        override fun applyTo(value: String, settings: BuildLayoutParameters, origin: Origin) {
            val resolver: Transformer<File?, String?> = BasicFileResolver(settings.getCurrentDir())
            val projectDir = resolver.transform(value)
            settings.setCurrentDir(projectDir)
            settings.setProjectDir(projectDir)
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.CONFIGURATION
        }
    }

    companion object {
        private var options: MutableList<BuildOption<in BuildLayoutParameters?>?>? = null

        init {
            val options: MutableList<BuildOption<BuildLayoutParameters?>?> = ArrayList<BuildOption<BuildLayoutParameters?>?>()
            options.add(GradleUserHomeOption())
            options.add(ProjectDirOption())
            Companion.options = Collections.unmodifiableList<BuildOption<in BuildLayoutParameters?>?>(options)
        }
    }
}
