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
package org.gradle.language.cpp.tooling.r410

import org.gradle.tooling.BuildController
import org.gradle.tooling.model.Model
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.gradle.tooling.model.gradle.GradleBuild

fun BuildController?.getBuildModel(): GradleBuild = this!!.buildModel!!

fun GradleBuild?.getProjects(): List<BasicGradleProject> = this!!.projects!!.filterNotNull()

fun GradleBuild?.getEditableBuilds(): List<GradleBuild> = this!!.editableBuilds!!.filterNotNull()

@Suppress("UNCHECKED_CAST")
fun <T> BuildController?.getModel(target: Model?, modelType: Class<out T>?): T = this!!.getModel(target, modelType as Class<T?>?) as T
