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
package org.gradle.nativeplatform.test.internal

import org.gradle.api.tasks.diagnostics.internal.text.TextReportBuilder
import org.gradle.model.internal.manage.schema.ModelSchemaStore
import org.gradle.nativeplatform.internal.AbstractNativeBinaryRenderer
import org.gradle.nativeplatform.test.NativeTestSuiteBinarySpec
import javax.inject.Inject

class NativeTestSuiteBinaryRenderer @Inject constructor(schemaStore: ModelSchemaStore?) : AbstractNativeBinaryRenderer<NativeTestSuiteBinarySpec?>(schemaStore) {
    val targetType: Class<NativeTestSuiteBinarySpec?>?
        get() = NativeTestSuiteBinarySpec::class.java

    override fun renderTasks(binary: NativeTestSuiteBinarySpec, builder: TextReportBuilder) {
        builder.item("install using task", binary.getTasks().getInstall().getPath())
        builder.item("run using task", binary.getTasks().getRun().getPath())
    }

    override fun renderOutputs(binary: NativeTestSuiteBinarySpec, builder: TextReportBuilder) {
        builder.item("executable file", binary.getExecutableFile())
    }

    override fun renderDetails(binary: NativeTestSuiteBinarySpec, builder: TextReportBuilder) {
        val testSuite = binary.getTestSuite()
        val testedComponent = testSuite.getTestedComponent()
        if (testedComponent != null) {
            builder.item("component under test", testedComponent.getDisplayName())
        }
        val testedBinary = binary.getTestedBinary()
        if (testedBinary != null) {
            builder.item("binary under test", testedBinary.getDisplayName())
        }
        super.renderDetails(binary, builder)
    }
}
