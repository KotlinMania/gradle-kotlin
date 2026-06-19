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
package org.gradle.tooling

import org.gradle.api.Action
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.GradleTask
import org.gradle.tooling.model.HierarchicalElement
import org.gradle.tooling.model.Model
import org.gradle.tooling.model.Task
import org.gradle.tooling.model.TaskSelector
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.build.Help
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.eclipse.EclipseRuntime
import org.gradle.tooling.model.eclipse.EclipseWorkspace
import org.gradle.tooling.model.eclipse.EclipseWorkspaceProject
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.gradle.tooling.model.gradle.BuildInvocations
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import java.io.File

@Suppress("UNCHECKED_CAST")
fun <T> BuildController?.getModel(modelType: Class<out T>?): T = this!!.getModel(modelType as Class<T?>?) as T

@Suppress("UNCHECKED_CAST")
fun <T> BuildController?.findModel(modelType: Class<out T>?): T? = this!!.findModel(modelType as Class<T?>?) as T?

@Suppress("UNCHECKED_CAST")
fun <T> BuildController?.getModel(target: Model?, modelType: Class<out T>?): T = this!!.getModel(target, modelType as Class<T?>?) as T

@Suppress("UNCHECKED_CAST")
fun <T> BuildController?.findModel(target: Model?, modelType: Class<out T>?): T? = this!!.findModel(target, modelType as Class<T?>?) as T?

@Suppress("UNCHECKED_CAST")
fun <T, P> BuildController?.getModel(modelType: Class<out T>?, parameterType: Class<P>?, parameterInitializer: Action<in P?>): T =
    this!!.getModel(modelType as Class<T?>?, parameterType as Class<P?>?, parameterInitializer) as T

@Suppress("UNCHECKED_CAST")
fun <T, P> BuildController?.findModel(modelType: Class<out T>?, parameterType: Class<P>?, parameterInitializer: Action<in P?>): T? =
    this!!.findModel(modelType as Class<T?>?, parameterType as Class<P?>?, parameterInitializer) as T?

@Suppress("UNCHECKED_CAST")
fun <T, P> BuildController?.getModel(target: Model?, modelType: Class<out T>?, parameterType: Class<P>?, parameterInitializer: Action<in P?>): T =
    this!!.getModel(target, modelType as Class<T?>?, parameterType as Class<P?>?, parameterInitializer) as T

@Suppress("UNCHECKED_CAST")
fun <T, P> BuildController?.findModel(target: Model?, modelType: Class<out T>?, parameterType: Class<P>?, parameterInitializer: Action<in P?>): T? =
    this!!.findModel(target, modelType as Class<T?>?, parameterType as Class<P?>?, parameterInitializer) as T?

fun BuildController?.getBuildModel(): GradleBuild = this!!.buildModel!!

fun BuildController?.send(value: Any?) = this!!.send(value)

@Suppress("UNCHECKED_CAST")
fun <M> BuildController?.fetch(modelType: Class<out M>?): FetchModelResult<M> = this!!.fetch(modelType as Class<M?>?) as FetchModelResult<M>

@Suppress("UNCHECKED_CAST")
fun <M> BuildController?.fetch(target: Model?, modelType: Class<out M>?): FetchModelResult<M> = this!!.fetch(target, modelType as Class<M?>?) as FetchModelResult<M>

fun BuildController?.getCanQueryProjectModelInParallel(modelType: Class<*>): Boolean = this!!.getCanQueryProjectModelInParallel(modelType)

@Suppress("UNCHECKED_CAST")
fun <T> BuildController?.run(actions: MutableCollection<out BuildAction<out T?>?>): MutableList<T?> = this!!.run(actions)!!

@Suppress("UNCHECKED_CAST")
fun <T> BuildController?.run(actions: List<BuildAction<T?>>): MutableList<T?> = this!!.run(actions as MutableCollection<out BuildAction<out T?>?>)!!

@Suppress("UNCHECKED_CAST")
fun <M, P> BuildController?.fetch(modelType: Class<out M>?, parameterType: Class<P>?, parameterInitializer: Action<in P?>): FetchModelResult<M> =
    this!!.fetch(modelType as Class<M?>?, parameterType as Class<P?>?, parameterInitializer) as FetchModelResult<M>

@Suppress("UNCHECKED_CAST")
fun <M, P> BuildController?.fetch(target: Model?, modelType: Class<out M>?, parameterType: Class<P>?, parameterInitializer: Action<in P?>): FetchModelResult<M> =
    this!!.fetch(target, modelType as Class<M?>?, parameterType as Class<P?>?, parameterInitializer) as FetchModelResult<M>

fun GradleBuild?.getProjects(): List<BasicGradleProject> = this!!.projects!!.filterNotNull()
fun GradleBuild?.getIncludedBuilds(): List<GradleBuild> = this!!.includedBuilds!!.filterNotNull()
fun GradleBuild?.getEditableBuilds(): List<GradleBuild> = this!!.editableBuilds!!.filterNotNull()
fun GradleBuild?.getRootProject() = this!!.rootProject!!
fun GradleBuild?.getBuildIdentifier() = this!!.buildIdentifier

fun BasicGradleProject?.getName() = this!!.name
fun BasicGradleProject?.getPath() = this!!.path

fun GradleProject?.getName() = this!!.name
fun GradleProject?.getPath() = this!!.path
fun GradleProject?.getChildren(): List<GradleProject> = this!!.children!!.filterNotNull()
fun GradleProject?.getTasks(): List<GradleTask> = this!!.tasks!!.filterNotNull()

fun EclipseProject?.getName() = this!!.name
fun EclipseProject?.getChildren(): List<EclipseProject> = this!!.children!!.filterNotNull()
fun EclipseProject?.getGradleProject() = this!!.gradleProject

fun EclipseWorkspace?.getProjects(): MutableList<EclipseWorkspaceProject> = this!!.projects!!.filterNotNull().toMutableList()
fun EclipseWorkspaceProject?.getName() = this!!.name
fun EclipseWorkspaceProject?.getLocation() = this!!.location
fun EclipseWorkspaceProject?.isOpen() = this!!.isOpen

fun IdeaProject?.getName() = this!!.name
fun IdeaProject?.getModules(): List<IdeaModule> = this!!.modules!!.filterNotNull()
fun IdeaModule?.getName() = this!!.name

fun BuildInvocations?.getTasks(): List<Task> = this!!.tasks!!.filterNotNull()
fun BuildInvocations?.getTaskSelectors(): List<TaskSelector> = this!!.taskSelectors!!.filterNotNull()

fun Task?.getName() = this!!.name
fun TaskSelector?.getName() = this!!.name
fun GradleTask?.getName() = this!!.name
fun HierarchicalElement?.getName() = this!!.name
fun HierarchicalElement?.getChildren(): List<HierarchicalElement> = this!!.children!!.filterNotNull()

fun BuildEnvironment?.getGradle() = this!!.gradle
fun BuildEnvironment?.getVersionInfo(): String = call(this, "getVersionInfo") as String
fun Help?.getBuildIdentifier() = call(this, "getBuildIdentifier")
fun Help?.getRenderedText(): String = call(this, "getRenderedText") as String
fun EclipseRuntime?.getWorkspace() = this!!.workspace
fun EclipseRuntime?.getGradleUserHome(): File? = call(this, "getGradleUserHome") as File?
fun KotlinDslScriptsModel?.getScriptModels() = this!!.scriptModels!!
fun KotlinDslScriptModel?.getExceptions() = this!!.exceptions

fun <M> FetchModelResult<M>?.getModel(): M? = this!!.model
fun FetchModelResult<*>?.getFailures(): List<Failure?> = this!!.failures!!.filterNotNull()

private fun call(receiver: Any?, method: String): Any? = receiver!!.javaClass.getMethod(method).invoke(receiver)
private fun call(receiver: Any?, method: String, value: Any?) {
    receiver!!.javaClass.methods.first { it.name == method && it.parameterCount == 1 }.invoke(receiver, value)
}

fun Any?.getThing(): Any? = call(this, "getThing")

fun Any?.getValue(): String = call(this, "getValue") as String
fun Any?.getParameterValue(): String = call(this, "getParameterValue") as String
fun Any?.setValue(value: String?) = call(this, "setValue", value)
fun Any?.setTasks(tasks: List<String?>?) = call(this, "setTasks", tasks)
fun Any?.getFailures(): List<Failure?> = call(this, "getFailures") as List<Failure?>
fun Any?.getCauses(): List<Failure?> = call(this, "getCauses") as List<Failure?>
fun Any?.getMessage(): String = call(this, "getMessage") as String
fun Any?.getDescription(): String? = call(this, "getDescription") as String?
fun Any?.setWorkspace(workspace: EclipseWorkspace?) = call(this, "setWorkspace", workspace)
fun Any?.getRootDir(): File? = call(this, "getRootDir") as File?
fun Any?.getBuildIdentifier() = call(this, "getBuildIdentifier")
fun Any?.getGradleUserHome(): File? = call(this, "getGradleUserHome") as File?
