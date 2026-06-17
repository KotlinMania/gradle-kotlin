/*
 * Copyright 2007 the original author or authors.
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
package org.gradle.api.internal.project

import org.gradle.api.Project
import org.gradle.api.internal.project.ant.AntLoggingAdapterFactory
import org.gradle.internal.concurrent.CompositeStoppable
import java.io.Closeable

class DefaultAntBuilderFactory(private val project: Project, private val loggingAdapterFactory: AntLoggingAdapterFactory) : AntBuilderFactory, Closeable {
    private val stoppable = CompositeStoppable()

    override fun createAntBuilder(): DefaultAntBuilder {
        val loggingAdapter = loggingAdapterFactory.create()
        val antBuilder = DefaultAntBuilder(project, loggingAdapter!!)
        antBuilder.getProject().setBaseDir(project.getProjectDir())
        antBuilder.getProject().removeBuildListener(antBuilder.getProject().getBuildListeners().get(0))
        antBuilder.getProject().addBuildListener(loggingAdapter)
        stoppable.add(antBuilder)
        return antBuilder
    }

    override fun close() {
        stoppable.stop()
    }
}
