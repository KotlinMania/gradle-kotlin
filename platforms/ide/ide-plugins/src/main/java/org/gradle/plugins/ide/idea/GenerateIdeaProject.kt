/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.idea

import org.gradle.api.tasks.Internal
import org.gradle.internal.deprecation.DeprecationLogger.deprecateTask
import org.gradle.internal.deprecation.DeprecationLogger.whileDisabled
import org.gradle.internal.xml.XmlTransformer
import org.gradle.plugins.ide.api.XmlGeneratorTask
import org.gradle.plugins.ide.idea.model.IdeaProject
import org.gradle.plugins.ide.idea.model.PathFactory
import org.gradle.plugins.ide.idea.model.Project
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/**
 * Generates an IDEA project file for root project *only*. If you want to fine tune the idea configuration
 *
 * At this moment nearly all configuration is done via [IdeaProject].
 *
 */
@DisableCachingByDefault(because = "Not made cacheable, yet")
@Deprecated("Will be removed in Gradle 10.")
abstract class GenerateIdeaProject : XmlGeneratorTask<Project?> {
    /**
     * The Idea project model containing the details required to generate the project file.
     */
    @get:Internal
    var ideaProject: IdeaProject? = null

    constructor()

    @Inject
    constructor(ideaProject: IdeaProject?) {
        this.ideaProject = ideaProject
    }

    override fun generate() {
        deprecateTask(getName())
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "ide_task_deprecation")!!
            .nagUser()
        super.generate()
    }

    override fun configure(xmlModule: Project?) {
        whileDisabled(Runnable { this.ideaProject!!.mergeXmlProject(xmlModule) })
    }

    public override fun create(): Project {
        val project = Project(xmlTransformer, whileDisabled<PathFactory?>(org.gradle.internal.Factory { ideaProject!!.getPathFactory() }))
        return project
    }

    val xmlTransformer: XmlTransformer
        get() {
            if (ideaProject == null) {
                return super.xmlTransformer
            }
            return whileDisabled<XmlTransformer>(org.gradle.internal.Factory { ideaProject!!.getIpr().xmlTransformer })
        }

    var outputFile: File?
        /**
         * output *.ipr file
         */
        get() {
            if (ideaProject == null) {
                return super.outputFile
            }
            return ideaProject!!.getOutputFile()
        }
        set(newOutputFile) {
            ideaProject!!.setOutputFile(newOutputFile)
        }
}
