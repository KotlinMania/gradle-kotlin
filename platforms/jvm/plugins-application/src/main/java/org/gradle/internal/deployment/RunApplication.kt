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
package org.gradle.internal.deployment

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.JavaExec.setClasspath
import org.gradle.api.tasks.TaskAction
import org.gradle.deployment.internal.DeploymentRegistry
import org.gradle.internal.jvm.Jvm
import org.gradle.process.internal.JavaExecHandleBuilder.setArgs
import org.gradle.process.internal.JavaExecHandleBuilder.setClasspath
import org.gradle.process.internal.JavaExecHandleBuilder.setExecutable
import org.gradle.process.internal.JavaExecHandleFactory.newJavaExec
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

@DisableCachingByDefault(because = "Produces no cacheable output")
abstract class RunApplication : DefaultTask() {
    @get:Input
    var mainClassName: String? = null
    private var arguments: MutableCollection<String?>? = null
    private var classpath: FileCollection? = null

    @get:Internal
    var changeBehavior: DeploymentRegistry.ChangeBehavior = DeploymentRegistry.ChangeBehavior.RESTART

    @Classpath
    fun getClasspath(): FileCollection {
        return classpath!!
    }

    fun setClasspath(classpath: FileCollection) {
        this.classpath = classpath
    }

    @Input
    fun getArguments(): MutableCollection<String?> {
        return arguments!!
    }

    fun setArguments(arguments: MutableCollection<String?>) {
        this.arguments = arguments
    }

    @TaskAction
    fun startApplication() {
        val registry = this.deploymentRegistry
        val handle = registry.get<JavaApplicationHandle?>(getPath(), JavaApplicationHandle::class.java)
        if (handle == null) {
            val builder = this.execActionFactory.newJavaExec()
            builder!!.setExecutable(Jvm.current().getJavaExecutable())
            builder.setClasspath(classpath!!)
            builder.mainClass.set(mainClassName)
            builder.setArgs(arguments!!)
            registry.start<JavaApplicationHandle?>(getPath(), changeBehavior, JavaApplicationHandle::class.java, builder)
        }
    }

    @get:Inject
    protected abstract val deploymentRegistry: DeploymentRegistry

    @get:Inject
    protected abstract val execActionFactory: JavaExecHandleFactory?
}
