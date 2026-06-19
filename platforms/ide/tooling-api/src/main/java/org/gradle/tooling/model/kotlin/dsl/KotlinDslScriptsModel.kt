/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.tooling.model.kotlin.dsl

import java.io.File
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel.Companion.SCRIPTS_GRADLE_PROPERTY_NAME

/**
 * Editor model for a set of Kotlin DSL scripts.
 *
 *
 * Can only be requested on the root project, the builder will throw otherwise.
 *
 *
 * Requires the `prepareKotlinBuildScriptModel` task to be executed before building the model.
 * See [KotlinDslModelsParameters.PREPARATION_TASK_NAME]
 *
 *
 * The set of scripts can be provided as a Gradle property named `org.gradle.kotlin.dsl.provider.scripts`,
 * as a list of absolute paths separated by `|`.
 * If none are provided, then the model is built for all the Kotlin DSL scripts known to belong to this build.
 * See [SCRIPTS_GRADLE_PROPERTY_NAME].
 *
 *
 * The Gradle Kotlin DSL script provider must be running in "classpath" mode.
 * This is done by providing the system property `-Dorg.gradle.kotlin.dsl.provider.mode=classpath`.
 * See [KotlinDslModelsParameters.CLASSPATH_MODE_SYSTEM_PROPERTY_DECLARATION].
 * In this mode, Gradle Kotlin DSL scripts compilation or evaluation failures will be ignored, collected and
 * exceptions will be returned in the built model.
 * Optionally, it can also be set in a strict mode by providing the system property value `-Dorg.gradle.kotlin.dsl.provider.mode=strict-classpath`.
 * See [KotlinDslModelsParameters.STRICT_CLASSPATH_MODE_SYSTEM_PROPERTY_DECLARATION].
 *
 *
 * Optionally, an identifier can be provided as a Gradle property named `org.gradle.kotlin.dsl.provider.cid`,
 * it can then be used to correlate Gradle and TAPI client log statements.
 * See [KotlinDslModelsParameters.CORRELATION_ID_GRADLE_PROPERTY_NAME].
 *
 * @since 6.0
 */
interface KotlinDslScriptsModel {
    /**
     * Script models by file.
     */
    val scriptModels: MutableMap<File, KotlinDslScriptModel>?

    companion object {
        /**
         * Gradle property name for the set of scripts to be queried for.
         */
        const val SCRIPTS_GRADLE_PROPERTY_NAME: String = "org.gradle.tooling.model.kotlin.dsl.scripts"
    }
}
