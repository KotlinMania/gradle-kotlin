/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.api.problems.internal

import org.gradle.api.problems.ProblemGroup

object GradleCoreProblemGroup {
    private val COMPILATION_PROBLEM_GROUP = DefaultCompilationProblemGroup()
    private val DEPRECATION_PROBLEM_GROUP = ProblemGroup.create("deprecation", "Deprecation")
    private val VALIDATION_PROBLEM_GROUP = DefaultValidationProblemGroup()
    private val PLUGIN_APPLICATION_PROBLEM_GROUP = ProblemGroup.create("plugin-application", "Plugin application")
    private val TASK_SELECTION_PROBLEM_GROUP = ProblemGroup.create("task-selection", "Task selection")
    private val VERSION_CATALOG_PROBLEM_GROUP = ProblemGroup.create("dependency-version-catalog", "Version catalog")
    private val VARIANT_RESOLUTION_PROBLEM_GROUP = ProblemGroup.create("dependency-variant-resolution", "Variant resolution")
    private val CONFIGURATION_USAGE_PROBLEM_GROUP = ProblemGroup.create("configuration-usage", "Configuration usage")
    private val DAEMON_TOOLCHAIN_PROBLEM_GROUP: DaemonToolchainProblemGroup = DefaultDaemonToolchainProblemGroup()
    private val SCRIPTS_PROBLEM_GROUP = ProblemGroup.create("scripts", "Scripts")

    @JvmStatic
    fun compilation(): CompilationProblemGroup {
        return COMPILATION_PROBLEM_GROUP
    }

    fun deprecation(): ProblemGroup {
        return DEPRECATION_PROBLEM_GROUP
    }

    @JvmStatic
    fun validation(): ValidationProblemGroup {
        return VALIDATION_PROBLEM_GROUP
    }

    @JvmStatic
    fun pluginApplication(): ProblemGroup {
        return PLUGIN_APPLICATION_PROBLEM_GROUP
    }

    @JvmStatic
    fun taskSelection(): ProblemGroup {
        return TASK_SELECTION_PROBLEM_GROUP
    }

    @JvmStatic
    fun versionCatalog(): ProblemGroup {
        return VERSION_CATALOG_PROBLEM_GROUP
    }

    @JvmStatic
    fun variantResolution(): ProblemGroup {
        return VARIANT_RESOLUTION_PROBLEM_GROUP
    }

    @JvmStatic
    fun configurationUsage(): ProblemGroup {
        return CONFIGURATION_USAGE_PROBLEM_GROUP
    }

    @JvmStatic
    fun daemonToolchain(): DaemonToolchainProblemGroup {
        return DAEMON_TOOLCHAIN_PROBLEM_GROUP
    }

    @JvmStatic
    fun scripts(): ProblemGroup {
        return SCRIPTS_PROBLEM_GROUP
    }

    interface CompilationProblemGroup {
        fun thisGroup(): ProblemGroup?
        fun java(): ProblemGroup?
        fun groovy(): ProblemGroup?
        fun groovyDsl(): ProblemGroup?
    }

    interface ValidationProblemGroup {
        fun thisGroup(): ProblemGroup?
        fun property(): ProblemGroup?
        fun type(): ProblemGroup?
    }

    interface DaemonToolchainProblemGroup {
        fun thisGroup(): ProblemGroup?
        fun configurationGeneration(): ProblemGroup?
    }

    private class DefaultCompilationProblemGroup : CompilationProblemGroup {
        private val thisGroup = ProblemGroup.create("compilation", "Compilation")
        private val java = ProblemGroup.create("java", "Java compilation", thisGroup)
        private val groovy = ProblemGroup.create("groovy", "Groovy compilation", thisGroup)
        var groovyDsl: ProblemGroup = ProblemGroup.create("groovy-dsl", "Groovy DSL script compilation", thisGroup)

        override fun thisGroup(): ProblemGroup {
            return thisGroup
        }

        override fun java(): ProblemGroup {
            return this.java
        }

        override fun groovy(): ProblemGroup {
            return this.groovy
        }

        override fun groovyDsl(): ProblemGroup {
            return this.groovyDsl
        }
    }

    private class DefaultValidationProblemGroup : ValidationProblemGroup {
        private val thisGroup = ProblemGroup.create("validation", "Validation")
        private val property = ProblemGroup.create("property-validation", "Gradle property validation", thisGroup)
        private val type = ProblemGroup.create("type-validation", "Gradle type validation", thisGroup)

        override fun thisGroup(): ProblemGroup {
            return thisGroup
        }

        override fun property(): ProblemGroup {
            return property
        }

        override fun type(): ProblemGroup {
            return type
        }
    }

    private class DefaultDaemonToolchainProblemGroup : DaemonToolchainProblemGroup {
        private val thisGroup = ProblemGroup.create("daemon-toolchain", "Daemon toolchain")
        private val configurationGeneration = ProblemGroup.create("configuration-generation", "Gradle configuration generation", thisGroup)

        override fun thisGroup(): ProblemGroup {
            return thisGroup
        }

        override fun configurationGeneration(): ProblemGroup {
            return configurationGeneration
        }
    }
}
