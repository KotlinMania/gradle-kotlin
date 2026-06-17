/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.reporting

import com.google.common.collect.Sets
import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.ConfigurationCacheDegradation
import org.gradle.api.reporting.internal.BuildDashboardGenerator
import org.gradle.api.reporting.internal.DefaultBuildDashboardReports
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.Cast.uncheckedNonnullCast
import org.gradle.internal.Describables
import org.gradle.internal.deprecation.DeprecationLogger.whileDisabled
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import org.gradle.util.internal.ClosureBackedAction
import org.gradle.util.internal.CollectionUtils
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.io.Serializable
import java.util.Arrays
import java.util.function.Function
import javax.inject.Inject
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashSet
import kotlin.collections.MutableSet

/**
 * Generates build dashboard report.
 */
@DisableCachingByDefault(because = "Not made cacheable, yet")
abstract class GenerateBuildDashboard : DefaultTask(), Reporting<BuildDashboardReports> {
    private val aggregated: MutableSet<Reporting<out ReportContainer<*>>> = LinkedHashSet<Reporting<out ReportContainer<*>>>()

    private val reports: BuildDashboardReports

    init {
        ConfigurationCacheDegradation.requireDegradation<GenerateBuildDashboard>(this, "Task is not compatible with the Configuration Cache")
        reports = this.objectFactory.newInstance<DefaultBuildDashboardReports>(DefaultBuildDashboardReports::class.java, Describables.quoted("Task", getIdentityPath()))
        reports.getHtml().getRequired().set(true)
    }

    @get:ToBeReplacedByLazyProperty(unreported = true, comment = "Skipped for report since ReportState is private")
    @get:Input
    val inputReports: MutableSet<ReportState>
        get() {
            val inputs: MutableSet<ReportState> =
                LinkedHashSet<ReportState>()
            for (report in this.enabledInputReports) {
                if (getReports().contains(report)) {
                    // A report to be generated, ignore
                    continue
                }
                val outputLocation = report.getOutputLocation().get().getAsFile()
                inputs.add(ReportState(report.getDisplayName(), outputLocation, outputLocation.exists()))
            }
            return inputs
        }

    private val enabledInputReports: MutableSet<Report>
        get() {
            val allAggregatedReports =
                Sets.newHashSet<Reporting<out ReportContainer<*>>>(aggregated)
            allAggregatedReports.addAll(this.aggregatedTasks)

            val enabledReportSets: MutableSet<NamedDomainObjectSet<out Report>> =
                CollectionUtils.collect<NamedDomainObjectSet<out Report>?, Reporting<out ReportContainer<*>>?>(
                    allAggregatedReports,
                    Function { reporting: Reporting<out ReportContainer<*>>? -> reporting!!.getReports().getEnabled() }
                )
            return LinkedHashSet<Report>(
                CollectionUtils.flattenCollections<Report?>(
                    Report::class.java,
                    enabledReportSets
                )
            )
        }

    private val aggregatedTasks: MutableSet<Reporting<out ReportContainer<*>>>
        get() {
            val reports: MutableSet<Reporting<out ReportContainer<*>>> =
                HashSet<Reporting<out ReportContainer<*>>>()
            whileDisabled<Project?>(org.gradle.internal.Factory { this.getProject() })!!
                .allprojects(object : Action<Project> {
                    override fun execute(project: Project) {
                        project.getTasks().all(object : Action<Task> {
                            override fun execute(task: Task) {
                                if (task !is Reporting<*>) {
                                    return
                                }
                                reports.add(uncheckedNonnullCast<Reporting<out ReportContainer<*>>?>(task)!!)
                            }
                        })
                    }
                })
            return reports
        }

    /**
     * Configures which reports are to be aggregated in the build dashboard report generated by this task.
     *
     * <pre>
     * buildDashboard {
     * aggregate codenarcMain, checkstyleMain
     * }
    </pre> *
     *
     * @param reportings an array of [Reporting] instances that are to be aggregated
     */
    @SafeVarargs
    fun aggregate(vararg reportings: Reporting<out ReportContainer<*>>) {
        aggregated.addAll(Arrays.asList<Reporting<out ReportContainer<*>>>(*reportings))
    }

    /**
     * The reports to be generated by this task.
     *
     * @return The reports container
     */
    @Nested
    override fun getReports(): BuildDashboardReports {
        return reports
    }

    /**
     * Configures the reports to be generated by this task.
     *
     * The contained reports can be configured by name and closures.
     *
     * <pre>
     * buildDashboard {
     * reports {
     * html {
     * destination "build/dashboard.html"
     * }
     * }
     * }
    </pre> *
     *
     * @param closure The configuration
     * @return The reports container
     */
    override fun reports(closure: Closure<*>): BuildDashboardReports {
        return reports(ClosureBackedAction<BuildDashboardReports>(closure))
    }

    /**
     * Configures the reports to be generated by this task.
     *
     * The contained reports can be configured by name and closures.
     *
     * <pre>
     * buildDashboard {
     * reports {
     * html {
     * destination "build/dashboard.html"
     * }
     * }
     * }
    </pre> *
     *
     * @param configureAction The configuration
     * @return The reports container
     */
    override fun reports(configureAction: Action<in BuildDashboardReports>): BuildDashboardReports {
        configureAction.execute(reports)
        return reports
    }

    @TaskAction
    fun run() {
        if (getReports().getHtml().getRequired().get()) {
            val generator = BuildDashboardGenerator()
            generator.render(this.enabledInputReports, reports.getHtml().getEntryPoint())
        } else {
            setDidWork(false)
        }
    }

    @get:Inject
    protected abstract val objectFactory: ObjectFactory?

    private class ReportState(private val name: String, private val destination: File, private val available: Boolean) : Serializable {
        override fun equals(obj: Any): Boolean {
            if (obj !is ReportState) {
                return false
            }
            val other = obj
            return name == other.name && destination == other.destination && available == other.available
        }

        override fun hashCode(): Int {
            return name.hashCode() xor destination.hashCode()
        }
    }
}
