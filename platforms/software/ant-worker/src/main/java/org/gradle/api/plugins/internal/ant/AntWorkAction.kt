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
package org.gradle.api.plugins.internal.ant

import org.gradle.api.Action
import org.gradle.api.internal.project.IsolatedAntBuilder.execute
import org.gradle.api.internal.project.IsolatedAntBuilder.withClasspath
import org.gradle.api.internal.project.antbuilder.AntBuilderDelegate
import org.gradle.internal.jvm.Jvm
import org.gradle.workers.WorkAction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.inject.Inject

abstract class AntWorkAction<T : AntWorkParameters?> : WorkAction<T?> {
    override fun execute() {
        LOGGER.info("Running {} with toolchain '{}'.", this.actionName, Jvm.current().getJavaHome().getAbsolutePath())

        this.isolatedAntBuilder
            .withClasspath(getParameters()!!.getAntLibraryClasspath())
            .execute(Action { antBuilder: AntBuilderDelegate -> this.execute(antBuilder) })
    }

    protected abstract val actionName: String?

    protected abstract fun execute(antBuilder: AntBuilderDelegate)

    @get:Inject
    protected abstract val isolatedAntBuilder: IsolatedAntBuilder?

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(AntWorkAction::class.java)
    }
}
