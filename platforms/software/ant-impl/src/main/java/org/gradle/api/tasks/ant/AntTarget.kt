/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.tasks.ant

import org.apache.tools.ant.Target
import org.gradle.api.internal.ConfigurationCacheDegradation
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * A task which executes an Ant target.
 */
@DisableCachingByDefault(because = "Gradle would require more information to cache this task")
abstract class AntTarget : ConventionTask() {
    /**
     * Returns the Ant target to execute.
     */
    /**
     * Sets the Ant target to execute.
     */
    @JvmField
    @get:ToBeReplacedByLazyProperty
    @get:Internal
    var target: Target? = null
    /**
     * Returns the Ant project base directory to use when executing the target.
     */
    /**
     * Sets the Ant project base directory to use when executing the target.
     */
    @JvmField
    @get:ToBeReplacedByLazyProperty
    @get:Internal
    var baseDir: File? = null

    init {
        ConfigurationCacheDegradation.requireDegradation<AntTarget?>(this, "Task is not compatible with the Configuration Cache")
    }

    @TaskAction
    fun executeAntTarget() {
        val oldBaseDir = target!!.getProject().getBaseDir()
        target!!.getProject().setBaseDir(baseDir)
        try {
            target!!.performTasks()
        } finally {
            target!!.getProject().setBaseDir(oldBaseDir)
        }
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    @ToBeReplacedByLazyProperty
    override fun getDescription(): String? {
        return if (target == null) null else target!!.getDescription()
    }

    /**
     * {@inheritDoc}
     */
    override fun setDescription(description: String?) {
        if (target != null) {
            target!!.setDescription(description)
        }
    }
}
