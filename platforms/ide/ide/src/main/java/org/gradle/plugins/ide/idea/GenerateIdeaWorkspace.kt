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
import org.gradle.internal.xml.XmlTransformer
import org.gradle.plugins.ide.api.XmlGeneratorTask
import org.gradle.plugins.ide.idea.model.IdeaWorkspace
import org.gradle.plugins.ide.idea.model.Workspace
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/**
 * Generates an IDEA workspace file *only* for root project. There's little you can configure about workspace generation at the moment.
 *
 */
@DisableCachingByDefault(because = "Not made cacheable, yet")
@Deprecated("Will be removed in Gradle 10.")
abstract class GenerateIdeaWorkspace : XmlGeneratorTask<Workspace?> {
    /**
     * The Idea workspace model containing the details required to generate the workspace file.
     */
    @get:Internal
    var workspace: IdeaWorkspace? = null

    constructor()

    @Inject
    constructor(workspace: IdeaWorkspace?) {
        this.workspace = workspace
    }

    override fun generate() {
        deprecateTask(getName())
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "ide_task_deprecation")!!
            .nagUser()
        super.generate()
    }

    override fun create(): Workspace {
        return Workspace(getXmlTransformer())
    }

    override fun configure(xmlWorkspace: Workspace?) {
        this.workspace!!.mergeXmlWorkspace(xmlWorkspace)
    }

    override fun getXmlTransformer(): XmlTransformer? {
        if (workspace == null) {
            return super.getXmlTransformer()
        }
        return workspace!!.getIws().getXmlTransformer()
    }
}
