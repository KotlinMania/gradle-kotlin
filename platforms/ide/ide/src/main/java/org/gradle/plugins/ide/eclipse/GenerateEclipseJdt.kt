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
package org.gradle.plugins.ide.eclipse

import org.gradle.api.internal.PropertiesTransformer
import org.gradle.api.tasks.Internal
import org.gradle.internal.deprecation.DeprecationLogger.deprecateTask
import org.gradle.internal.deprecation.DeprecationLogger.whileDisabled
import org.gradle.plugins.ide.api.PropertiesFileContentMerger
import org.gradle.plugins.ide.api.PropertiesGeneratorTask
import org.gradle.plugins.ide.eclipse.model.EclipseJdt
import org.gradle.plugins.ide.eclipse.model.Jdt
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/**
 * Generates the Eclipse JDT configuration file. If you want to fine tune the eclipse configuration
 *
 *
 * At this moment nearly all configuration is done via [EclipseJdt].
 *
 */
@DisableCachingByDefault(because = "Not made cacheable, yet")
@Deprecated("Will be removed in Gradle 10.")
abstract class GenerateEclipseJdt : PropertiesGeneratorTask<Jdt?> {
    private var jdt: EclipseJdt?

    constructor() {
        jdt = getInstantiator().newInstance<EclipseJdt?>(EclipseJdt::class.java, PropertiesFileContentMerger(getTransformer()))
    }

    @Inject
    constructor(jdt: EclipseJdt?) {
        this.jdt = jdt
    }

    override fun generate() {
        deprecateTask(getName())
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "ide_task_deprecation")!!
            .nagUser()
        super.generate()
    }

    override fun create(): Jdt {
        return Jdt(getTransformer())
    }

    override fun configure(jdtContent: Jdt) {
        whileDisabled(Runnable {
            val jdtModel = getJdt()
            jdtModel.getFile().getBeforeMerged().execute(jdtContent)
            jdtContent.setSourceCompatibility(jdtModel.getSourceCompatibility())
            jdtContent.setTargetCompatibility(jdtModel.getTargetCompatibility())
            jdtModel.getFile().getWhenMerged().execute(jdtContent)
        })
    }

    override fun getTransformer(): PropertiesTransformer? {
        if (jdt == null) {
            return super.getTransformer()
        }
        return whileDisabled<PropertiesTransformer?>(org.gradle.internal.Factory { jdt!!.getFile().getTransformer() })
    }

    /**
     * Eclipse JDT model that contains information needed to generate the JDT file.
     */
    @Internal
    fun getJdt(): EclipseJdt {
        return jdt!!
    }

    fun setJdt(jdt: EclipseJdt?) {
        this.jdt = jdt
    }
}
