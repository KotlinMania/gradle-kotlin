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
package org.gradle.plugins.ide.tooling.r33

import org.gradle.tooling.BuildAction
import kotlin.collections.ArrayList
import kotlin.collections.MutableList

class FetchEclipseProjects : BuildAction<MutableList<EclipseProject?>?> {
    public override fun execute(controller: BuildController): MutableList<EclipseProject?> {
        val eclipseProjects: MutableList<EclipseProject?> = ArrayList<EclipseProject?>()
        val build: GradleBuild = controller.getBuildModel()
        val all: ArrayList<GradleBuild?> = ArrayList<GradleBuild?>()
        all.add(build)
        collectEclipseProjects(build, eclipseProjects, controller, all)
        return eclipseProjects
    }

    private fun collectEclipseProjects(build: GradleBuild, eclipseProjects: MutableList<EclipseProject?>, controller: BuildController, all: MutableList<GradleBuild?>) {
        for (project in build.getProjects()) {
            eclipseProjects.add(controller.getModel(project, EclipseProject::class.java))
        }
        for (includedBuild in build.getIncludedBuilds()) {
            if (!all.contains(includedBuild)) {
                all.add(includedBuild)
                collectEclipseProjects(includedBuild, eclipseProjects, controller, all)
            }
        }
    }
}
