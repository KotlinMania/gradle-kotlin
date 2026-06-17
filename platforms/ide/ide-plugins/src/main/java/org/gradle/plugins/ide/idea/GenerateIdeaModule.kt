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
import org.gradle.plugins.ide.idea.model.IdeaModule
import org.gradle.plugins.ide.idea.model.Module
import org.gradle.plugins.ide.idea.model.PathFactory
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/**
 * Generates an IDEA module file. If you want to fine tune the idea configuration
 *
 *
 * Please refer to interesting examples on idea configuration in [IdeaModule].
 *
 *
 * At this moment nearly all configuration is done via [IdeaModule].
 *
 */
@DisableCachingByDefault(because = "Not made cacheable, yet")
@Deprecated("Will be removed in Gradle 10.")
abstract class GenerateIdeaModule : XmlGeneratorTask<Module?> {
    /**
     * The Idea module model containing the details required to generate the module file.
     */
    @get:Internal
    var module: IdeaModule? = null

    constructor()

    @Inject
    constructor(module: IdeaModule?) {
        this.module = module
    }

    override fun generate() {
        deprecateTask(getName())
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "ide_task_deprecation")!!
            .nagUser()
        super.generate()
    }

    override fun create(): Module? {
        return Module(xmlTransformer, org.gradle.internal.deprecation.DeprecationLogger.whileDisabled<PathFactory?>(org.gradle.internal.Factory { module!!.getPathFactory() })!!)
    }

    override fun configure(xmlModule: Module?) {
        this.module!!.mergeXmlModule(xmlModule)
    }

    val xmlTransformer: XmlTransformer
        get() {
            if (module == null) {
                return super.xmlTransformer
            }
            return whileDisabled<XmlTransformer>(org.gradle.internal.Factory { module!!.getIml().xmlTransformer })
        }

    var outputFile: File?
        /**
         * Configures output *.iml file. It's **optional** because the task should configure it correctly for you (including making sure it is unique in the multi-module build). If you really need to
         * change the output file name it is much easier to do it via the **idea.module.name** property.
         *
         * Please refer to documentation in [IdeaModule] **name** property. In IntelliJ IDEA
         * the module name is the same as the name of the *.iml file.
         */
        get() {
            if (module == null) {
                return super.outputFile
            }
            return module!!.getOutputFile()
        }
        set(newOutputFile) {
            module!!.setOutputFile(newOutputFile)
        }
}
