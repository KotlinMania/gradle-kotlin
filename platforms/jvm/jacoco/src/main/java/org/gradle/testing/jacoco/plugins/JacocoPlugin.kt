/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.testing.jacoco.plugins

import org.apache.commons.lang3.StringUtils
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.Transformer
import org.gradle.api.artifacts.ConfigurablePublishArtifact
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConsumableConfiguration
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.TestSuiteName
import org.gradle.api.attributes.VerificationType
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.lambdas.SerializableLambdas
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JvmTestSuitePlugin
import org.gradle.api.plugins.ReportingBasePlugin
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.plugins.jvm.JvmTestSuiteTarget
import org.gradle.api.reporting.ConfigurableReport
import org.gradle.api.reporting.DirectoryReport
import org.gradle.api.reporting.Report
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.reporting.SingleFileReport
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.jacoco.JacocoAgentJar
import org.gradle.internal.reflect.Instantiator
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.testing.base.TestingExtension
import org.gradle.testing.jacoco.tasks.JacocoBase
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport
import java.io.File
import javax.inject.Inject

/**
 * Plugin that provides support for generating Jacoco coverage data.
 *
 * @see [JaCoCo plugin reference](https://docs.gradle.org/current/userguide/jacoco_plugin.html)
 */
abstract class JacocoPlugin @Inject constructor(private val instantiator: Instantiator) : Plugin<Project> {
    private var project: ProjectInternal? = null

    override fun apply(project: Project) {
        project.getPluginManager().apply(ReportingBasePlugin::class.java)
        this.project = project as ProjectInternal
        addJacocoConfigurations()
        val agent = instantiator.newInstance<JacocoAgentJar>(JacocoAgentJar::class.java, this.project!!.getServices().get<FileOperations?>(FileOperations::class.java))
        val extension = project.getExtensions().create<JacocoPluginExtension>(PLUGIN_EXTENSION_NAME, JacocoPluginExtension::class.java, project, agent)
        extension.setToolVersion(DEFAULT_JACOCO_VERSION)
        val reportingExtension = project.getExtensions().getByName(ReportingExtension.NAME) as ReportingExtension
        extension.getReportsDirectory().convention(reportingExtension.getBaseDirectory().dir("jacoco"))

        configureAgentDependencies(agent, extension)
        configureTaskClasspathDefaults(extension)
        applyToDefaultTasks(extension)
        configureJacocoReportsDefaults(extension)
        addDefaultReportAndCoverageVerificationTasks(extension)
        configureCoverageDataElementsVariants(project)
    }

    /**
     * Creates the configurations used by plugin.
     */
    @Suppress("deprecation")
    private fun addJacocoConfigurations() {
        val configurations = project!!.getConfigurations()
        configurations.resolvableDependencyScopeLocked(AGENT_CONFIGURATION_NAME, Action { agentConf: Configuration? ->
            agentConf!!.setDescription("The Jacoco agent to use to get coverage data.")
        })
        configurations.resolvableDependencyScopeLocked(ANT_CONFIGURATION_NAME, Action { antConf: Configuration? ->
            antConf!!.setDescription("The Jacoco ant tasks to use to get execute Gradle tasks.")
        })
    }

    /**
     * Configures the agent dependencies using the 'jacocoAnt' configuration. Uses the version declared in 'toolVersion' of the Jacoco extension if no dependencies are explicitly declared.
     *
     * @param extension the extension that has the tool version to use
     */
    private fun configureAgentDependencies(jacocoAgentJar: JacocoAgentJar, extension: JacocoPluginExtension) {
        val config = project!!.getConfigurations().getAt(AGENT_CONFIGURATION_NAME)
        jacocoAgentJar.setAgentConf(config)
        config.defaultDependencies(Action { dependencies: DependencySet? -> dependencies!!.add(project!!.getDependencies().create("org.jacoco:org.jacoco.agent:" + extension.getToolVersion())) })
    }

    /**
     * Configures the classpath for Jacoco tasks using the 'jacocoAnt' configuration. Uses the version information declared in 'toolVersion' of the Jacoco extension if no dependencies are explicitly
     * declared.
     *
     * @param extension the JacocoPluginExtension
     */
    private fun configureTaskClasspathDefaults(extension: JacocoPluginExtension) {
        val config = this.project!!.getConfigurations().getAt(ANT_CONFIGURATION_NAME)
        project!!.getTasks().withType<JacocoBase>(JacocoBase::class.java).configureEach(Action { task: JacocoBase? -> task!!.setJacocoClasspath(config) })
        config.defaultDependencies(Action { dependencies: DependencySet? -> dependencies!!.add(project!!.getDependencies().create("org.jacoco:org.jacoco.ant:" + extension.getToolVersion())) })
    }

    /**
     * Applies the Jacoco agent to all tasks of type `Test`.
     *
     * @param extension the extension to apply Jacoco with
     */
    private fun applyToDefaultTasks(extension: JacocoPluginExtension) {
        project!!.getTasks().withType<Test>(Test::class.java).configureEach(Action { task: Test? -> extension.applyTo(task) })
    }

    private fun configureJacocoReportsDefaults(extension: JacocoPluginExtension) {
        project!!.getTasks().withType<JacocoReport>(JacocoReport::class.java).configureEach(Action { reportTask: JacocoReport? -> configureJacocoReportDefaults(extension, reportTask!!) })
    }

    private fun configureJacocoReportDefaults(extension: JacocoPluginExtension, reportTask: JacocoReport) {
        reportTask.getReports()
            .all(SerializableLambdas.action<ConfigurableReport>(SerializableLambdas.SerializableAction { report: ConfigurableReport -> report.getRequired().convention(report.getName() == "html") }
            ))
        val reportsDir = extension.getReportsDirectory()
        reportTask.getReports().all(SerializableLambdas.action<ConfigurableReport>(SerializableLambdas.SerializableAction { report: ConfigurableReport ->
            if (report.getOutputType() == Report.OutputType.DIRECTORY) {
                (report as DirectoryReport).getOutputLocation().convention(reportsDir.dir(reportTask.getName() + "/" + report.getName()))
            } else {
                (report as SingleFileReport).getOutputLocation().convention(reportsDir.file(reportTask.getName() + "/" + reportTask.getName() + "." + report.getName()))
            }
        }))
    }

    /**
     * Adds report and coverage verification tasks for specific default test tasks.
     *
     * @param extension the extension describing the test task names
     */
    private fun addDefaultReportAndCoverageVerificationTasks(extension: JacocoPluginExtension) {
        project!!.getPlugins().withType<JavaPlugin>(JavaPlugin::class.java, Action { javaPlugin: JavaPlugin ->
            val testing = project!!.getExtensions().getByType<TestingExtension>(TestingExtension::class.java)
            val defaultTestSuite = testing.getSuites().withType<JvmTestSuite>(JvmTestSuite::class.java).getByName(JvmTestSuitePlugin.DEFAULT_TEST_SUITE_NAME)
            defaultTestSuite.getTargets().configureEach({ target: JvmTestSuiteTarget? ->
                val testTask = target!!.testTask
                addDefaultReportTask(extension, testTask)
                addDefaultCoverageVerificationTask(testTask)
            })
        })
    }

    private fun addDefaultReportTask(extension: JacocoPluginExtension, testTaskProvider: TaskProvider<out Task>) {
        val testTaskName = testTaskProvider.getName()
        project!!.getTasks().register<JacocoReport>(
            "jacoco" + StringUtils.capitalize(testTaskName) + "Report",
            JacocoReport::class.java,
            Action { reportTask: JacocoReport ->
                reportTask.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP)
                reportTask.setDescription(String.format("Generates code coverage report for the %s task.", testTaskName))
                reportTask.executionData(testTaskProvider.get())
                reportTask.sourceSets(project!!.getExtensions().getByType<SourceSetContainer>(SourceSetContainer::class.java).getByName("main"))
                // TODO: Change the default location for these reports to follow the convention defined in ReportOutputDirectoryAction
                val reportsDir = extension.getReportsDirectory()
                reportTask.getReports().all(SerializableLambdas.action<ConfigurableReport>(SerializableLambdas.SerializableAction { report: ConfigurableReport ->
                    // For someone looking for the difference between this and the duplicate code above
                    // this one uses the `testTaskProvider` and the `reportTask`. The other just
                    // uses the `reportTask`.
                    // https://github.com/gradle/gradle/issues/6343
                    if (report.getOutputType() == Report.OutputType.DIRECTORY) {
                        (report as DirectoryReport).getOutputLocation().convention(reportsDir.dir(testTaskName + "/" + report.getName()))
                    } else {
                        (report as SingleFileReport).getOutputLocation().convention(reportsDir.file(testTaskName + "/" + reportTask.getName() + "." + report.getName()))
                    }
                }))
            })
    }

    private fun addDefaultCoverageVerificationTask(testTaskProvider: TaskProvider<out Task>) {
        project!!.getTasks().register<JacocoCoverageVerification>(
            "jacoco" + StringUtils.capitalize(testTaskProvider.getName()) + "CoverageVerification",
            JacocoCoverageVerification::class.java,
            Action { coverageVerificationTask: JacocoCoverageVerification ->
                coverageVerificationTask.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP)
                coverageVerificationTask.setDescription(String.format("Verifies code coverage metrics based on specified rules for the %s task.", testTaskProvider.getName()))
                coverageVerificationTask.executionData(testTaskProvider.get())
                coverageVerificationTask.sourceSets(project!!.getExtensions().getByType<SourceSetContainer>(SourceSetContainer::class.java).getByName("main"))
            })
    }

    companion object {
        /**
         * The jacoco version used if none is explicitly specified.
         *
         * @since 3.4
         */
        const val DEFAULT_JACOCO_VERSION: String = "0.8.14"
        const val AGENT_CONFIGURATION_NAME: String = "jacocoAgent"
        const val ANT_CONFIGURATION_NAME: String = "jacocoAnt"
        const val PLUGIN_EXTENSION_NAME: String = "jacoco"

        private fun configureCoverageDataElementsVariants(project: Project) {
            project.getPlugins().withType<JvmTestSuitePlugin>(JvmTestSuitePlugin::class.java, Action { p: JvmTestSuitePlugin ->
                val testing = project.getExtensions().getByType<TestingExtension>(TestingExtension::class.java)
                testing.getSuites().withType<JvmTestSuite>(JvmTestSuite::class.java).configureEach(Action { suite: JvmTestSuite? ->
                    // TODO: Eventually, we want a jacoco results variant for each target, but cannot do so now because:
                    // 1. Targets need a way to uniquely identify themselves via attributes. We do not have an API to describe
                    //    a target using attributes yet.
                    // 2. If a suite has multiple jacoco results variants, we get ambiguity when resolving the jacoco results variant.
                    //    We should add a feature to dependency management allowing ArtifactView to select multiple variants from the target component.
                    val jacocoResultsVariant: NamedDomainObjectProvider<ConsumableConfiguration> = Companion.createCoverageDataVariant(project as ProjectInternal, suite!!)
                    suite.getTargets().configureEach({ target: JvmTestSuiteTarget? ->
                        jacocoResultsVariant.configure(Action { variant: ConsumableConfiguration? ->
                            val resultsDir = target!!.testTask.map<File>(Transformer { task: Test? ->
                                task!!.getExtensions().getByType<JacocoTaskExtension>(
                                    JacocoTaskExtension::class.java
                                ).getDestinationFile()
                            }
                            )
                            variant!!.getOutgoing().artifact(
                                resultsDir,
                                Action { artifact: ConfigurablePublishArtifact? -> artifact!!.setType(ArtifactTypeDefinition.BINARY_DATA_TYPE) }
                            )
                        })
                    })
                })
            })
        }

        private fun createCoverageDataVariant(project: ProjectInternal, suite: JvmTestSuite): NamedDomainObjectProvider<ConsumableConfiguration> {
            val variantName = String.format("coverageDataElementsFor%s", StringUtils.capitalize(suite.getName()))

            return project.getConfigurations().consumable(variantName, Action { conf: ConsumableConfiguration? ->
                conf!!.setDescription("Binary results containing Jacoco test coverage for all targets in the '" + suite.getName() + "' Test Suite.")
                conf.attributes(Action { attributes: AttributeContainer? ->
                    attributes!!.attribute<Category>(Category.CATEGORY_ATTRIBUTE, attributes.named<Category>(Category::class.java, Category.VERIFICATION))
                    attributes.attribute<VerificationType>(
                        VerificationType.VERIFICATION_TYPE_ATTRIBUTE,
                        attributes.named<VerificationType>(VerificationType::class.java, VerificationType.JACOCO_RESULTS)
                    )

                    // TODO: Allow targets to define attributes uniquely identifying themselves.
                    // Then, create a jacoco results variant for each target instead of each suite.
                    attributes.attribute<TestSuiteName>(TestSuiteName.TEST_SUITE_NAME_ATTRIBUTE, attributes.named<TestSuiteName>(TestSuiteName::class.java, suite.getName()))
                })
            })
        }
    }
}
