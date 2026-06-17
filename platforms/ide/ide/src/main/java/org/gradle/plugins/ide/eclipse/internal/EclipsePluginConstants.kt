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
package org.gradle.plugins.ide.eclipse.internal

object EclipsePluginConstants {
    const val DEFAULT_PROJECT_OUTPUT_PATH: String = "bin/default"
    const val TEST_SOURCES_ATTRIBUTE_KEY: String = "test"
    const val TEST_SOURCES_ATTRIBUTE_VALUE: String = "true"
    const val MODULE_ATTRIBUTE_KEY: String = "module"
    const val MODULE_ATTRIBUTE_VALUE: String = "true"

    // TODO The scope information is superseded by test attributes. We can delete the corresponding code bits once we make sure that the majority of Buildship users use test sources.
    const val GRADLE_USED_BY_SCOPE_ATTRIBUTE_NAME: String = "gradle_used_by_scope"
    const val GRADLE_SCOPE_ATTRIBUTE_NAME: String = "gradle_scope"

    const val WITHOUT_TEST_CODE_ATTRIBUTE_KEY: String = "without_test_code"
    const val WITHOUT_TEST_CODE_ATTRIBUTE_VALUE: String = "true"
}
