/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.nativeplatform.test.cunit.plugins

import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskContainer
import org.gradle.internal.service.ServiceRegistry
import org.gradle.language.c.CSourceSet
import org.gradle.language.c.plugins.CLangPlugin
import org.gradle.model.Each
import org.gradle.model.ModelMap
import org.gradle.model.Mutate
import org.gradle.model.Path
import org.gradle.model.RuleSource
import org.gradle.nativeplatform.test.cunit.CUnitTestSuiteBinarySpec
import org.gradle.nativeplatform.test.cunit.CUnitTestSuiteSpec
import org.gradle.nativeplatform.test.cunit.internal.DefaultCUnitTestSuiteBinary
import org.gradle.nativeplatform.test.cunit.internal.DefaultCUnitTestSuiteSpec
import org.gradle.nativeplatform.test.cunit.tasks.GenerateCUnitLauncher
import org.gradle.nativeplatform.test.internal.NativeTestSuites
import org.gradle.nativeplatform.test.plugins.NativeBinariesTestPlugin
import org.gradle.platform.base.ComponentBinaries
import org.gradle.platform.base.ComponentType
import org.gradle.platform.base.TypeBuilder
import org.gradle.testing.base.TestSuiteContainer
import java.io.File

/**
 * A plugin that sets up the infrastructure for testing native binaries with CUnit.
 */
@Incubating
abstract class CUnitPlugin : Plugin<Project?> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(NativeBinariesTestPlugin::class.java)
        project.getPluginManager().apply(CLangPlugin::class.java)
    }

    internal class Rules : RuleSource() {
        @ComponentType
        fun registerCUnitTestSuiteSpecType(builder: TypeBuilder<CUnitTestSuiteSpec?>) {
            builder.defaultImplementation(DefaultCUnitTestSuiteSpec::class.java)
        }

        @Mutate
        fun configureCUnitTestSuiteSources(@Each suite: CUnitTestSuiteSpec, @Path("buildDir") buildDir: File?) {
            suite.getSources().create<CSourceSet?>(CUNIT_LAUNCHER_SOURCE_SET, CSourceSet::class.java, object : Action<CSourceSet?> {
                override fun execute(launcherSources: CSourceSet) {
                    val baseDir = File(buildDir, "src/" + suite.getName() + "/cunitLauncher")
                    launcherSources.getSource().srcDir(File(baseDir, "c"))
                    launcherSources.exportedHeaders.srcDir(File(baseDir, "headers"))
                }
            })

            suite.getSources().withType<CSourceSet?>(CSourceSet::class.java).named("c", object : Action<CSourceSet?> {
                override fun execute(cSourceSet: CSourceSet) {
                    cSourceSet.lib(suite.getSources().get(CUNIT_LAUNCHER_SOURCE_SET))
                }
            })
        }

        @Mutate
        fun createCUnitLauncherTasks(tasks: TaskContainer, testSuites: TestSuiteContainer) {
            for (suite in testSuites.withType<CUnitTestSuiteSpec>(CUnitTestSuiteSpec::class.java).values()) {
                val taskName = suite.getName() + "CUnitLauncher"
                @Suppress("deprecation") val skeletonTask = tasks.create<GenerateCUnitLauncher>(taskName, GenerateCUnitLauncher::class.java)

                val launcherSources = findLauncherSources(suite)
                skeletonTask.setSourceDir(launcherSources.getSource().getSrcDirs().iterator().next())
                skeletonTask.setHeaderDir(launcherSources.exportedHeaders.getSrcDirs().iterator().next())
                launcherSources.builtBy(skeletonTask)
            }
        }

        private fun findLauncherSources(suite: CUnitTestSuiteSpec): CSourceSet {
            return suite.getSources().withType<CSourceSet>(CSourceSet::class.java)
                .get(org.gradle.nativeplatform.test.cunit.plugins.CUnitPlugin.Rules.Companion.CUNIT_LAUNCHER_SOURCE_SET)!!
        }

        @ComponentType
        fun registerCUnitTestBinaryType(builder: TypeBuilder<CUnitTestSuiteBinarySpec?>) {
            builder.defaultImplementation(DefaultCUnitTestSuiteBinary::class.java)
        }

        @ComponentBinaries
        fun createCUnitTestBinaries(
            binaries: ModelMap<CUnitTestSuiteBinarySpec?>?,
            testSuite: CUnitTestSuiteSpec?,
            @Path("buildDir") buildDir: File?,
            serviceRegistry: ServiceRegistry?
        ) {
            NativeTestSuites.createNativeTestSuiteBinaries<CUnitTestSuiteBinarySpec?>(binaries, testSuite, CUnitTestSuiteBinarySpec::class.java, "CUnitExe", buildDir, serviceRegistry)
        }

        companion object {
            private const val CUNIT_LAUNCHER_SOURCE_SET = "cunitLauncher"
        }
    }
}
