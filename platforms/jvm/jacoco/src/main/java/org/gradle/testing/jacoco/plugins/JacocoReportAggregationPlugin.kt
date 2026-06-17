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
package org.gradle.testing.jacoco.plugins

import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Transformer
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.DependencyScopeConfiguration
import org.gradle.api.artifacts.ResolvableConfiguration
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.TestSuiteName
import org.gradle.api.attributes.VerificationType
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.lambdas.SerializableLambdas
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.specs.Spec
import org.gradle.internal.jacoco.DefaultJacocoCoverageReport
import org.gradle.testing.base.TestingExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import javax.inject.Inject

/**
 * Adds configurations to for resolving variants containing JaCoCo code coverage results, which may span multiple subprojects.  Reacts to the presence of the jvm-test-suite plugin and creates
 * tasks to collect code coverage results for each named test-suite.
 *
 * @see [JaCoCo Report Aggregation Plugin reference](https://docs.gradle.org/current/userguide/jacoco_report_aggregation_plugin.html)
 *
 * @since 7.4
 */
@Incubating
abstract class JacocoReportAggregationPlugin : Plugin<Project> {
    @get:Inject
    protected abstract val dependencyFactory: DependencyFactory?

    @get:Inject
    protected abstract val ecosystemUtilities: JvmPluginServices?

    override fun apply(project: Project) {
        project.getPluginManager().apply("org.gradle.reporting-base")
        project.getPluginManager().apply("jvm-ecosystem")
        project.getPluginManager().apply("jacoco")

        val configurations = project.getConfigurations()
        val jacocoAggregation = configurations.dependencyScope(JACOCO_AGGREGATION_CONFIGURATION_NAME, Action { conf: DependencyScopeConfiguration? ->
            conf!!.setDescription("Collects project dependencies for purposes of JaCoCo coverage report aggregation")
        })

        val codeCoverageResultsConf = configurations.resolvable("aggregateCodeCoverageReportResults", Action { conf: ResolvableConfiguration? ->
            conf!!.setDescription("Resolvable configuration used to gather files for the JaCoCo coverage report aggregation via ArtifactViews, not intended to be used directly")
            conf.extendsFrom(jacocoAggregation.get())
            project.getPlugins().withType<JavaBasePlugin>(JavaBasePlugin::class.java, Action { plugin: JavaBasePlugin ->
                // If the current project is jvm-based, aggregate dependent projects as jvm-based as well.
                this.ecosystemUtilities.configureAsRuntimeClasspath(conf)
            })
        })

        val sourceDirectories = codeCoverageResultsConf.map<FileCollection>(Transformer { conf: ResolvableConfiguration? ->
            conf!!.getIncoming().artifactView(Action { view: ArtifactView.ViewConfiguration? ->
                view!!.withVariantReselection()
                view.componentFilter(projectComponent())
                this.ecosystemUtilities.configureAsSources(view)
            }).getFiles()
        }
        )

        val classDirectories = codeCoverageResultsConf.map<FileCollection>(Transformer { conf: ResolvableConfiguration? ->
            conf!!.getIncoming().artifactView(Action { view: ArtifactView.ViewConfiguration? ->
                view!!.componentFilter(projectComponent())
                view.attributes(Action { attributes: AttributeContainer? ->
                    attributes!!.attribute<LibraryElements>(
                        LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, attributes.named<LibraryElements>(
                            LibraryElements::class.java, LibraryElements.CLASSES
                        )
                    )
                })
            }).getFiles()
        }
        )

        val reporting = project.getExtensions().getByType<ReportingExtension>(ReportingExtension::class.java)
        reporting.getReports().registerBinding<JacocoCoverageReport>(JacocoCoverageReport::class.java, DefaultJacocoCoverageReport::class.java)

        // Iterate and configure each user-specified report.
        reporting.getReports().withType<JacocoCoverageReport>(JacocoCoverageReport::class.java).all(Action { report: JacocoCoverageReport? ->
            report!!.getReportTask().configure(Action { task: JacocoReport? ->
                task!!.getSourceDirectories().from(sourceDirectories)
                task.getClassDirectories().from(classDirectories)
                val executionData = codeCoverageResultsConf.map<FileCollection>(Transformer { conf: ResolvableConfiguration? ->
                    conf!!.getIncoming().artifactView(Action { view: ArtifactView.ViewConfiguration? ->
                        view!!.withVariantReselection()
                        view.componentFilter(projectComponent())
                        view.attributes(Action { attributes: AttributeContainer? ->
                            attributes!!.attribute<Category>(Category.CATEGORY_ATTRIBUTE, attributes.named<Category>(Category::class.java, Category.VERIFICATION))
                            attributes.attributeProvider<TestSuiteName>(TestSuiteName.TEST_SUITE_NAME_ATTRIBUTE, report.getTestSuiteName().map<TestSuiteName>(Transformer { tt: String? ->
                                attributes.named<TestSuiteName>(
                                    TestSuiteName::class.java, tt!!
                                )
                            }))
                            attributes.attribute<VerificationType>(
                                VerificationType.VERIFICATION_TYPE_ATTRIBUTE,
                                attributes.named<VerificationType>(VerificationType::class.java, VerificationType.JACOCO_RESULTS)
                            )
                            attributes.attribute<String>(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.BINARY_DATA_TYPE)
                        })
                    }).getFiles()
                }
                )
                task.getExecutionData().from(executionData)
            })
        })

        // convention for synthesizing reports based on existing test suites in "this" project
        project.getPlugins().withId("jvm-test-suite", Action { plugin: Plugin<*>? ->
            // Depend on this project for aggregation
            jacocoAggregation.configure(Action { conf: DependencyScopeConfiguration? ->
                conf!!.getDependencies().add(this.dependencyFactory.createProjectDependency())
            })

            val testing = project.getExtensions().getByType<TestingExtension>(TestingExtension::class.java)
            val testSuites = testing.getSuites()
            testSuites.withType<JvmTestSuite>(JvmTestSuite::class.java).all(Action { testSuite: JvmTestSuite? ->
                reporting.getReports().create<JacocoCoverageReport>(testSuite!!.getName() + "CodeCoverageReport", JacocoCoverageReport::class.java, Action { report: JacocoCoverageReport ->
                    report.getTestSuiteName().convention(testSuite.getName())
                })
            })
        })
    }

    companion object {
        const val JACOCO_AGGREGATION_CONFIGURATION_NAME: String = "jacocoAggregation"

        private fun projectComponent(): Spec<ComponentIdentifier?> {
            return SerializableLambdas.spec<ComponentIdentifier>(SerializableLambdas.SerializableSpec { id: ComponentIdentifier -> id is ProjectComponentIdentifier })
        }
    }
}
