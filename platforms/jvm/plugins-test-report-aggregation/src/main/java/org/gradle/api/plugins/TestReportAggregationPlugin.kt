/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.api.plugins

import org.gradle.api.Action
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer
import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Transformer
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyScopeConfiguration
import org.gradle.api.artifacts.ResolvableConfiguration
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.TestSuiteName
import org.gradle.api.attributes.VerificationType
import org.gradle.api.internal.lambdas.SerializableLambdas
import org.gradle.api.internal.tasks.testing.DefaultAggregateTestReport
import org.gradle.api.plugins.jvm.internal.JvmEcosystemUtilities.configureAsRuntimeClasspath
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.testing.AggregateTestReport
import org.gradle.api.tasks.testing.TestReport
import org.gradle.testing.base.TestSuite
import org.gradle.testing.base.TestingExtension
import org.gradle.testing.base.plugins.TestingBasePlugin
import java.util.concurrent.Callable
import javax.inject.Inject

/**
 * Adds configurations to for resolving variants containing test execution results, which may span multiple subprojects.  Reacts to the presence of the jvm-test-suite plugin and creates
 * tasks to collect test results for each named test-suite.
 *
 * @since 7.4
 * @see [Test Report Aggregation Plugin reference](https://docs.gradle.org/current/userguide/test_report_aggregation_plugin.html)
 */
@Incubating
abstract class TestReportAggregationPlugin : Plugin<Project?> {
    @get:Inject
    protected abstract val dependencyFactory: DependencyFactory?

    @get:Inject
    protected abstract val jvmPluginServices: JvmPluginServices?

    override fun apply(project: Project) {
        project.getPluginManager().apply("org.gradle.reporting-base")

        val configurations = project.getConfigurations()
        val testAggregation: Configuration = configurations.dependencyScope(TEST_REPORT_AGGREGATION_CONFIGURATION_NAME, Action { dependencyScope: DependencyScopeConfiguration? ->
            dependencyScope!!.setDescription("A configuration to collect test execution results.")
        }).get()

        // A resolvable configuration to collect test results
        val testResultsConf: Configuration = configurations.resolvable("aggregateTestReportResults", Action { resolvable: ResolvableConfiguration? ->
            resolvable!!.extendsFrom(testAggregation)
            resolvable.setDescription("Graph needed for the aggregated test results report.")
        }).get()

        val reporting = project.getExtensions().getByType<ReportingExtension>(ReportingExtension::class.java)
        reporting.getReports().registerBinding<AggregateTestReport?>(AggregateTestReport::class.java, DefaultAggregateTestReport::class.java)

        val objects = project.getObjects()

        val testReportDirectory = objects.directoryProperty().convention(reporting.getBaseDirectory().dir(TestingBasePlugin.TESTS_DIR_NAME))
        // prepare testReportDirectory with a reasonable default, but override with JavaPluginExtension#testReportDirectory if available
        project.getPlugins().withId("java-base", Action { plugin: Plugin<*>? ->
            val javaPluginExtension = project.getExtensions().getByType<JavaPluginExtension>(JavaPluginExtension::class.java)
            testReportDirectory.convention(javaPluginExtension.testReportDir)
            // If the current project is jvm-based, aggregate dependent projects as jvm-based as well.
            this.jvmPluginServices.configureAsRuntimeClasspath(testResultsConf)
        })

        // Iterate and configure each user-specified report.
        reporting.getReports().withType<AggregateTestReport?>(AggregateTestReport::class.java).all(Action { report: AggregateTestReport? ->
            report!!.reportTask.configure(Action { task: TestReport? ->
                val testResults = Callable {
                    testResultsConf.getIncoming().artifactView(Action { view: ArtifactView.ViewConfiguration? ->
                        view!!.withVariantReselection()
                        view.componentFilter(SerializableLambdas.spec<ComponentIdentifier?>(SerializableLambdas.SerializableSpec { id: ComponentIdentifier? -> id is ProjectComponentIdentifier }))
                        view.attributes(Action { attributes: AttributeContainer? ->
                            attributes!!.attribute<Category?>(Category.CATEGORY_ATTRIBUTE, attributes.named<Category?>(Category::class.java, Category.VERIFICATION))
                            attributes.attributeProvider<TestSuiteName?>(TestSuiteName.TEST_SUITE_NAME_ATTRIBUTE, report.testSuiteName.map<TestSuiteName?>(Transformer { tt: String? ->
                                attributes.named<TestSuiteName?>(
                                    TestSuiteName::class.java, tt
                                )
                            }))
                            attributes.attribute<VerificationType?>(
                                VerificationType.VERIFICATION_TYPE_ATTRIBUTE,
                                attributes.named<VerificationType?>(VerificationType::class.java, VerificationType.TEST_RESULTS)
                            )
                        })
                    }).getFiles()
                }
                task!!.testResults.from(testResults)
                task.destinationDirectory.convention(testReportDirectory.dir(report.testSuiteName.map<String?>(Transformer { tt: String? -> tt + "/aggregated-results" })))
            })
        })

        // convention for synthesizing reports based on existing test suites in "this" project
        project.getPlugins().withId("test-suite-base", Action { plugin: Plugin<*>? ->
            // Depend on this project for aggregation
            testAggregation.getDependencies().add(this.dependencyFactory.createProjectDependency())

            val testing = project.getExtensions().getByType<TestingExtension>(TestingExtension::class.java)
            val testSuites: ExtensiblePolymorphicDomainObjectContainer<TestSuite?> = testing.getSuites()
            testSuites.withType<TestSuite?>(TestSuite::class.java).all(Action { testSuite: TestSuite? ->
                reporting.getReports().create<AggregateTestReport?>(testSuite!!.getName() + "AggregateTestReport", AggregateTestReport::class.java, Action { report: AggregateTestReport? ->
                    report!!.testSuiteName.convention(testSuite.getName())
                })
            })
        })
    }

    companion object {
        const val TEST_REPORT_AGGREGATION_CONFIGURATION_NAME: String = "testReportAggregation"
    }
}
