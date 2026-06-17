/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.publish.ivy.internal.artifact

import com.google.common.base.Strings
import org.gradle.api.internal.tasks.DefaultTaskDependency
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.tasks.TaskDependency
import java.util.function.Consumer

abstract class AbstractIvyArtifact protected constructor(taskDependencyFactory: TaskDependencyFactory) : IvyArtifactInternal {
    private val allBuildDependencies: TaskDependency
    private val additionalBuildDependencies: DefaultTaskDependency

    var name: String? = null
        get() = if (field != null) field else this.defaultName
        set(name) {
            field = Strings.nullToEmpty(name)
        }
    var type: String? = null
        get() = if (field != null) field else this.defaultType
        set(type) {
            field = Strings.nullToEmpty(type)
        }
    var extension: String? = null
        get() = if (field != null) field else this.defaultExtension
        set(extension) {
            field = Strings.nullToEmpty(extension)
        }
    var classifier: String? = null
        get() = Strings.emptyToNull(if (field != null) field else this.defaultClassifier)
        set(classifier) {
            field = Strings.nullToEmpty(classifier)
        }
    var conf: String? = null
        get() = Strings.emptyToNull(if (field != null) field else this.defaultConf)
        set(conf) {
            field = Strings.nullToEmpty(conf)
        }

    init {
        this.additionalBuildDependencies = DefaultTaskDependency()
        this.allBuildDependencies = taskDependencyFactory.visitingDependencies(Consumer { context: TaskDependencyResolveContext? ->
            context!!.add(this.defaultBuildDependencies)
            additionalBuildDependencies.visitDependencies(context)
        })
    }

    protected abstract val defaultName: String?

    protected abstract val defaultType: String?

    protected abstract val defaultExtension: String?

    protected abstract val defaultClassifier: String?

    protected abstract val defaultConf: String?

    override fun builtBy(vararg tasks: Any) {
        additionalBuildDependencies.add(*tasks)
    }

    override fun getBuildDependencies(): TaskDependency {
        return allBuildDependencies
    }

    protected abstract val defaultBuildDependencies: TaskDependency?

    override fun toString(): String {
        return String.format("%s %s:%s:%s:%s", javaClass.getSimpleName(), this.name, this.type, this.extension, this.classifier)
    }
}
