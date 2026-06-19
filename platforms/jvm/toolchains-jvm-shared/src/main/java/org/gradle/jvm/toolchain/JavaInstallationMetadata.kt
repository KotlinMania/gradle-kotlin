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
package org.gradle.jvm.toolchain

import org.gradle.api.file.Directory
import org.gradle.api.Incubating
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal

/**
 * Metadata about a Java tool obtained from a toolchain.
 *
 * @see JavaLauncher
 *
 * @see JavaCompiler
 *
 * @see JavadocTool
 *
 *
 * @since 6.7
 */
interface JavaInstallationMetadata {
    @get:Input
    val languageVersion: JavaLanguageVersion?

    @get:Internal
    val javaRuntimeVersion: String?

    @get:Internal
    val jvmVersion: String?

    @get:Internal
    val vendor: String?

    @get:Internal
    val installationPath: Directory?

    @get:Incubating
    @get:Internal
    val isCurrentJvm: Boolean
}
