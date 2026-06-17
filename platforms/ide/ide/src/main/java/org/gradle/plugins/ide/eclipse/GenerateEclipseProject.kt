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
package org.gradle.plugins.ide.eclipse

import org.gradle.api.tasks.Internal
import org.gradle.internal.deprecation.DeprecationLogger.deprecateTask
import org.gradle.internal.xml.XmlTransformer
import org.gradle.plugins.ide.api.XmlFileContentMerger
import org.gradle.plugins.ide.api.XmlGeneratorTask
import org.gradle.plugins.ide.eclipse.model.EclipseProject
import org.gradle.plugins.ide.eclipse.model.Project
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/**
 * Generates an Eclipse `.project` file. If you want to fine tune the eclipse configuration
 *
 *
 * At this moment nearly all configuration is done via [EclipseProject].
 *
 */
@DisableCachingByDefault(because = "Not made cacheable, yet")
@Deprecated("Will be removed in Gradle 10.")
abstract class GenerateEclipseProject : XmlGeneratorTask<Project?> {
    /**
     * The Eclipse project model that contains the details required to generate the project file.
     */
    @get:Internal
    var projectModel: EclipseProject?

    constructor() {
        getXmlTransformer()!!.setIndentation("\t")
        projectModel = getInstantiator().newInstance<EclipseProject?>(EclipseProject::class.java, XmlFileContentMerger(getXmlTransformer()))
    }

    @Inject
    constructor(projectModel: EclipseProject?) {
        this.projectModel = projectModel
    }

    override fun generate() {
        deprecateTask(getName())
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "ide_task_deprecation")!!
            .nagUser()
        super.generate()
    }

    override fun create(): Project {
        return Project(getXmlTransformer())
    }

    override fun configure(project: Project?) {
        projectModel!!.mergeXmlProject(project)
    }

    override fun getXmlTransformer(): XmlTransformer? {
        if (projectModel == null) {
            return super.getXmlTransformer()
        }
        return projectModel!!.getFile().getXmlTransformer()
    }
}
