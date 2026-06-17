/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.internal.jacoco

import org.gradle.api.plugins.internal.ant.AntWorkParameters

interface JacocoReportParameters : AntWorkParameters {
    val projectName: Property<String?>?
    val encoding: Property<String?>?

    val allClassesDirs: ConfigurableFileCollection?
    val allSourcesDirs: ConfigurableFileCollection?
    val executionData: ConfigurableFileCollection?

    val generateHtml: Property<Boolean?>?
    val htmlDestination: DirectoryProperty?

    val generateXml: Property<Boolean?>?
    val xmlDestination: RegularFileProperty?

    val generateCsv: Property<Boolean?>?
    val csvDestination: RegularFileProperty?
}
