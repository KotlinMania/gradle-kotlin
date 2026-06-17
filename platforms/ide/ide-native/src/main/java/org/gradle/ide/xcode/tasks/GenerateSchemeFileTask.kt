/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.ide.xcode.tasks

import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.ide.xcode.XcodeProject
import org.gradle.ide.xcode.internal.DefaultXcodeProject
import org.gradle.ide.xcode.tasks.internal.XcodeSchemeFile
import org.gradle.internal.serialization.Cached
import org.gradle.plugins.ide.api.XmlGeneratorTask
import org.gradle.work.DisableCachingByDefault

/**
 * Task for generating a Xcode scheme file (e.g. `Foo.xcodeproj/xcshareddata/xcschemes/Foo.xcscheme`). An Xcode scheme defines a collection of targets to build, a configuration to use when building, and a collection of tests to execute.
 *
 *
 * You can have as many schemes as you want, but only one can be active at a time. You can include a scheme in a project—in which case it’s available in every workspace that includes that project, or in the workspace—in which case it’s available only in that workspace.
 *
 *
 * This task is used in conjunction with [GenerateXcodeProjectFileTask].
 *
 * @see XcodeProject
 *
 * @since 4.2
 */
@Incubating
@DisableCachingByDefault(because = "Not made cacheable, yet")
abstract class GenerateSchemeFileTask : XmlGeneratorTask<XcodeSchemeFile?>() {
    @Transient
    private var xcodeProject: DefaultXcodeProject? = null
    private val spec = Cached.of({ this.calculateSpec() })

    @Internal
    fun getXcodeProject(): XcodeProject {
        return xcodeProject!!
    }

    fun setXcodeProject(xcodeProject: XcodeProject?) {
        this.xcodeProject = xcodeProject as DefaultXcodeProject
    }

    private fun calculateSpec(): SchemeFileSpec {
        val targets: MutableList<TargetSpec> = ArrayList<TargetSpec>()
        for (target in xcodeProject!!.getTargets()) {
            targets.add(
                GenerateSchemeFileTask.TargetSpec(
                    target.getName(),
                    target.getId(),
                    target.getProductName(),
                    target.isRunnable(),
                    target.isUnitTest(),
                    target.getDefaultConfigurationName()
                )
            )
        }
        return SchemeFileSpec(
            getProject().getName(),
            targets
        )
    }

    override fun configure(schemeFile: XcodeSchemeFile) {
        val spec = this.spec.get()
        configureBuildAction(spec!!, schemeFile.getBuildAction())
        configureTestAction(spec, schemeFile.getTestAction())
        configureLaunchAction(spec, schemeFile.getLaunchAction())
        configureArchiveAction(schemeFile.getArchiveAction())
        configureAnalyzeAction(schemeFile.getAnalyzeAction())
        configureProfileAction(schemeFile.getProfileAction())
    }

    private fun configureBuildAction(spec: SchemeFileSpec, action: XcodeSchemeFile.BuildAction) {
        for (xcodeTarget in spec.targets) {
            action.entry(Action { buildActionEntry: XcodeSchemeFile.BuildActionEntry? ->
                buildActionEntry!!.setBuildForAnalysing(!xcodeTarget.unitTest)
                buildActionEntry.setBuildForArchiving(!xcodeTarget.unitTest)
                buildActionEntry.setBuildForProfiling(!xcodeTarget.unitTest)
                buildActionEntry.setBuildForRunning(!xcodeTarget.unitTest)
                buildActionEntry.setBuildForTesting(xcodeTarget.unitTest)
                buildActionEntry.setBuildableReference(toBuildableReference(spec, xcodeTarget))
            })
        }
    }

    private fun configureTestAction(spec: SchemeFileSpec, action: XcodeSchemeFile.TestAction) {
        action.setBuildConfiguration(DefaultXcodeProject.Companion.BUILD_DEBUG)

        for (xcodeTarget in spec.targets) {
            if (xcodeTarget.unitTest) {
                action.setBuildConfiguration(DefaultXcodeProject.Companion.TEST_DEBUG)
                action.entry(Action { testableEntry: XcodeSchemeFile.TestableEntry? ->
                    testableEntry!!.setSkipped(false)
                    val buildableReference = toBuildableReference(spec, xcodeTarget)
                    testableEntry.setBuildableReference(buildableReference)
                })
            }
        }
    }

    private fun configureLaunchAction(spec: SchemeFileSpec, action: XcodeSchemeFile.LaunchAction) {
        action.setBuildConfiguration(spec.targets.iterator().next().defaultConfigurationName.get())
        for (xcodeTarget in spec.targets) {
            val buildableReference = toBuildableReference(spec, xcodeTarget)
            if (xcodeTarget.runnable) {
                action.setBuildableProductRunnable(buildableReference)
            }
            action.setBuildableReference(buildableReference)
            break
        }
    }

    private fun configureArchiveAction(action: XcodeSchemeFile.ArchiveAction) {
        action.setBuildConfiguration(DefaultXcodeProject.Companion.BUILD_DEBUG)
    }

    private fun configureProfileAction(action: XcodeSchemeFile.ProfileAction) {
        action.setBuildConfiguration(DefaultXcodeProject.Companion.BUILD_DEBUG)
    }

    private fun configureAnalyzeAction(action: XcodeSchemeFile.AnalyzeAction) {
        action.setBuildConfiguration(DefaultXcodeProject.Companion.BUILD_DEBUG)
    }

    val inputFile: File?
        get() = null

    override fun create(): XcodeSchemeFile? {
        return XcodeSchemeFile(xmlTransformer)
    }

    private fun toBuildableReference(spec: SchemeFileSpec, target: TargetSpec): XcodeSchemeFile.BuildableReference {
        val buildableReference = XcodeSchemeFile.BuildableReference()
        buildableReference.setBuildableIdentifier("primary")
        buildableReference.setBlueprintIdentifier(target.id)
        buildableReference.setBuildableName(target.productName)
        buildableReference.setBlueprintName(target.name)
        buildableReference.setContainerRelativePath(spec.projectName + ".xcodeproj")

        return buildableReference
    }

    private class TargetSpec(val name: String?, val id: String?, val productName: String?, val runnable: Boolean, val unitTest: Boolean, val defaultConfigurationName: Provider<String?>)

    private class SchemeFileSpec(val projectName: String?, val targets: MutableList<TargetSpec>)
}
