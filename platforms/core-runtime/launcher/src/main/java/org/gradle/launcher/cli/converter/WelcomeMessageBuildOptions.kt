/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.api.launcher.cli.WelcomeMessageConfiguration
import org.gradle.api.launcher.cli.WelcomeMessageDisplayMode
import org.gradle.cli.OptionCategory
import org.gradle.internal.buildoption.BuildOption
import org.gradle.internal.buildoption.BuildOptionSet
import org.gradle.internal.buildoption.EnumBuildOption
import org.gradle.internal.buildoption.Origin

class WelcomeMessageBuildOptions : BuildOptionSet<WelcomeMessageConfiguration?>() {
    override fun getAllOptions(): MutableList<out BuildOption<in WelcomeMessageConfiguration?>?> {
        return options
    }

    class WelcomeMessageOption : EnumBuildOption<WelcomeMessageDisplayMode?, WelcomeMessageConfiguration?>(
        PROPERTY_NAME,
        WelcomeMessageDisplayMode::class.java,
        WelcomeMessageDisplayMode.entries.toTypedArray(),
        PROPERTY_NAME
    ) {
        override fun applyTo(value: WelcomeMessageDisplayMode?, settings: WelcomeMessageConfiguration, origin: Origin?) {
            settings.welcomeMessageDisplayMode = value
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.CONFIGURATION
        }

        companion object {
            const val PROPERTY_NAME: String = "org.gradle.welcome"
        }
    }

    companion object {
        private val options = mutableListOf<BuildOption<WelcomeMessageConfiguration?>?>(WelcomeMessageOption())
    }
}
