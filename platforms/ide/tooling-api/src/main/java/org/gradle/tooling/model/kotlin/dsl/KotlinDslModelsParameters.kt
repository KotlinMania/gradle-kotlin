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


/**
 * Parameters for Kotlin DSL models.
 *
 * @since 6.0
 */
object KotlinDslModelsParameters {
    const val PREPARATION_TASK_NAME: String = "prepareKotlinBuildScriptModel"

    const val CORRELATION_ID_GRADLE_PROPERTY_NAME: String = "org.gradle.kotlin.dsl.provider.cid"

    const val PROVIDER_MODE_SYSTEM_PROPERTY_NAME: String = "org.gradle.kotlin.dsl.provider.mode"

    const val CLASSPATH_MODE_SYSTEM_PROPERTY_VALUE: String = "classpath"

    const val STRICT_CLASSPATH_MODE_SYSTEM_PROPERTY_VALUE: String = "strict-classpath"

    val CLASSPATH_MODE_SYSTEM_PROPERTY_DECLARATION: String = "-D" + PROVIDER_MODE_SYSTEM_PROPERTY_NAME + "=" + CLASSPATH_MODE_SYSTEM_PROPERTY_VALUE

    val STRICT_CLASSPATH_MODE_SYSTEM_PROPERTY_DECLARATION: String = "-D" + PROVIDER_MODE_SYSTEM_PROPERTY_NAME + "=" + STRICT_CLASSPATH_MODE_SYSTEM_PROPERTY_VALUE
}
