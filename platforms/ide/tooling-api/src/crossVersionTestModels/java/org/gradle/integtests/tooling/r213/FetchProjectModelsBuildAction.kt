/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.integtests.tooling.r213

import org.gradle.tooling.*
import org.gradle.tooling.model.*
import org.gradle.tooling.model.build.*
import org.gradle.tooling.model.eclipse.*
import org.gradle.tooling.model.gradle.*
import org.gradle.tooling.model.idea.*
import org.gradle.tooling.model.kotlin.dsl.*
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import java.io.File
import org.gradle.integtests.tooling.r48.*

import org.gradle.tooling.BuildAction
import kotlin.collections.ArrayList
import kotlin.collections.MutableList

class FetchProjectModelsBuildAction(private val modelType: Class<*>?) : BuildAction<MutableList<Any?>?> {
    override fun execute(controller: BuildController?): MutableList<Any?> {
        val models: MutableList<Any?> = ArrayList<Any?>()
        for (project in controller.getBuildModel().getProjects()) {
            val model: Any? = controller.getModel(project, modelType)
            models.add(model)
        }
        return models
    }
}
