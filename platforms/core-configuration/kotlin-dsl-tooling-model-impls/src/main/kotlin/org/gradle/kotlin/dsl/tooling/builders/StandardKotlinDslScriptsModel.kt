/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.kotlin.dsl.tooling.builders

import org.gradle.tooling.model.kotlin.dsl.EditorPosition
import org.gradle.tooling.model.kotlin.dsl.EditorReport
import org.gradle.tooling.model.kotlin.dsl.EditorReportSeverity
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import java.io.File
import java.io.Serializable


data class StandardKotlinDslScriptsModel(
    private val commonModel: CommonKotlinDslScriptModel,
    private val dehydratedScriptModels: Map<File, KotlinDslScriptModel>
) : KotlinDslScriptsModel, Serializable {

    override val scriptModels: MutableMap<File, KotlinDslScriptModel> =
        dehydratedScriptModels.mapValuesTo(LinkedHashMap()) { (_, lightModel) ->
            StandardKotlinDslScriptModel(
                (commonModel.classPath + lightModel.classPath.orEmpty()).toMutableList(),
                (commonModel.sourcePath + lightModel.sourcePath.orEmpty()).toMutableList(),
                (commonModel.implicitImports + lightModel.implicitImports.orEmpty()).toMutableList(),
                lightModel.editorReports.orEmpty().toMutableList(),
                lightModel.exceptions.orEmpty().toMutableList()
            )
        }

}


data class CommonKotlinDslScriptModel(
    val classPath: List<File>,
    val sourcePath: List<File>,
    val implicitImports: List<String>
) : Serializable


data class StandardKotlinDslScriptModel(
    override val classPath: MutableList<File>,
    override val sourcePath: MutableList<File>,
    override val implicitImports: MutableList<String>,
    override val editorReports: MutableList<EditorReport>,
    override val exceptions: MutableList<String>
) : KotlinDslScriptModel, Serializable


data class StandardEditorReport(
    override val severity: EditorReportSeverity,
    override val message: String,
    override val position: EditorPosition? = null
) : EditorReport, Serializable


data class StandardEditorPosition(
    override val line: Int,
    override val column: Int = 0
) : EditorPosition, Serializable
