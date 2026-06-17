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
package org.gradle.api.reporting.components.internal

import org.apache.commons.lang3.StringUtils
import org.gradle.api.tasks.diagnostics.internal.text.TextReportBuilder
import org.gradle.language.base.DependentSourceSet
import org.gradle.language.base.LanguageSourceSet
import org.gradle.platform.base.DependencySpec
import org.gradle.platform.base.ProjectDependencySpec
import org.gradle.reporting.ReportRenderer
import java.io.File

internal class SourceSetRenderer : ReportRenderer<LanguageSourceSet?, TextReportBuilder?>() {
    public override fun render(sourceSet: LanguageSourceSet, builder: TextReportBuilder) {
        builder.heading(StringUtils.capitalize(sourceSet.getDisplayName()))
        renderSourceSetDirectories(sourceSet, builder)
        renderSourceSetDependencies(sourceSet, builder)
    }

    private fun renderSourceSetDirectories(sourceSet: LanguageSourceSet, builder: TextReportBuilder) {
        val srcDirs: MutableSet<File?> = sourceSet.getSource().getSrcDirs()
        if (srcDirs.isEmpty()) {
            builder.item("No source directories")
        } else {
            for (file in srcDirs) {
                builder.item("srcDir", file)
            }
            val source = sourceSet.getSource()
            val includes: MutableSet<String?> = source.getIncludes()
            if (!includes.isEmpty()) {
                builder.item("includes", includes)
            }
            val excludes: MutableSet<String?> = source.getExcludes()
            if (!excludes.isEmpty()) {
                builder.item("excludes", excludes)
            }
            val filterIncludes: MutableSet<String?> = source.getFilter().getIncludes()
            if (!filterIncludes.isEmpty()) {
                builder.item("limit to", filterIncludes)
            }
        }
    }

    private fun renderSourceSetDependencies(sourceSet: LanguageSourceSet?, builder: TextReportBuilder) {
        if (sourceSet is DependentSourceSet) {
            val dependencies = sourceSet.getDependencies()
            if (!dependencies.isEmpty()) {
                builder.collection<DependencySpec?>("dependencies", dependencies.getDependencies(), object : ReportRenderer<DependencySpec?, TextReportBuilder?>() {
                    public override fun render(model: DependencySpec?, output: TextReportBuilder) {
                        if (model is ProjectDependencySpec) {
                            output.item(model.getDisplayName())
                        }
                    }
                }, "dependencies")
            }
        }
    }

    companion object {
        val SORT_ORDER: Comparator<LanguageSourceSet?> = Comparator { o1: LanguageSourceSet?, o2: LanguageSourceSet? -> o1!!.getDisplayName().compareTo(o2!!.getDisplayName(), ignoreCase = true) }
    }
}
