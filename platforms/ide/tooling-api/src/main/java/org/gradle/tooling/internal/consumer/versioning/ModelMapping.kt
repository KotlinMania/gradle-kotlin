/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.internal.consumer.versioning

import org.gradle.tooling.internal.protocol.ModelIdentifier
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.build.Help
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.gradle.ProjectPublications
import org.gradle.tooling.model.idea.BasicIdeaProject
import org.gradle.tooling.model.idea.IdeaProject

class ModelMapping {
    fun getModelIdentifierFromModelType(modelType: Class<*>): ModelIdentifier {
        if (modelType == Void::class.java) {
            return DefaultModelIdentifier(ModelIdentifier.NULL_MODEL)
        }
        return DefaultModelIdentifier(modelType.getName())
    }

    fun getVersionAdded(modelType: Class<*>?): String? {
        return MODEL_VERSIONS.get(modelType)
    }

    private class DefaultModelIdentifier(private val model: String) : ModelIdentifier {
        override fun toString(): String {
            return "tooling model " + model
        }

        override fun getName(): String {
            return model
        }
    }

    companion object {
        private val MODEL_VERSIONS: MutableMap<Class<*>?, String?> = HashMap<Class<*>?, String?>()

        init {
            addModelVersions(MODEL_VERSIONS)
        }

        private fun addModelVersions(map: MutableMap<Class<*>?, String?>) {
            map.put(HierarchicalEclipseProject::class.java, "1.0-milestone-3")
            map.put(EclipseProject::class.java, "1.0-milestone-3")
            map.put(IdeaProject::class.java, "1.0-milestone-5")
            map.put(GradleProject::class.java, "1.0-milestone-5")
            map.put(BasicIdeaProject::class.java, "1.0-milestone-5")
            map.put(BuildEnvironment::class.java, "1.0-milestone-8")
            map.put(Void::class.java, "1.0-milestone-3")
            map.put(GradleBuild::class.java, "1.8")
            map.put(ProjectPublications::class.java, "1.12")
            map.put(Help::class.java, "9.4")
        }
    }
}
