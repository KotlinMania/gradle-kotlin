/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.nativeplatform.internal

import org.gradle.api.tasks.diagnostics.internal.text.TextReportBuilder
import org.gradle.model.internal.manage.schema.ModelSchemaStore
import org.gradle.nativeplatform.NativeExecutableBinarySpec
import javax.inject.Inject

class NativeExecutableBinaryRenderer @Inject constructor(schemaStore: ModelSchemaStore?) : AbstractNativeBinaryRenderer<NativeExecutableBinarySpec?>(schemaStore) {
    val targetType: Class<NativeExecutableBinarySpec?>?
        get() = NativeExecutableBinarySpec::class.java

    override fun renderTasks(binary: NativeExecutableBinarySpec, builder: TextReportBuilder) {
        builder.item("install using task", binary.getTasks().getInstall().getPath())
    }

    override fun renderOutputs(binary: NativeExecutableBinarySpec, builder: TextReportBuilder) {
        builder.item("executable file", binary.getExecutable().getFile())
    }
}
