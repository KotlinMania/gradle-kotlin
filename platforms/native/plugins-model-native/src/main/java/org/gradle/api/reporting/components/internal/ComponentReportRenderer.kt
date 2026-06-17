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

import com.google.common.collect.Sets
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.tasks.diagnostics.internal.TextReportRenderer
import org.gradle.api.tasks.diagnostics.internal.text.TextReportBuilder
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.language.base.LanguageSourceSet
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.ComponentSpec
import org.gradle.reporting.ReportRenderer

class ComponentReportRenderer(fileResolver: FileResolver?, binaryRenderer: TypeAwareBinaryRenderer?) : TextReportRenderer() {
    private val componentRenderer: ComponentRenderer
    private val sourceSetRenderer: TrackingReportRenderer<LanguageSourceSet?, TextReportBuilder?>
    private val binaryRenderer: TrackingReportRenderer<BinarySpec?, TextReportBuilder?>

    init {
        setFileResolver(fileResolver)
        this.sourceSetRenderer = TrackingReportRenderer<LanguageSourceSet?, TextReportBuilder?>(SourceSetRenderer())
        this.binaryRenderer = TrackingReportRenderer<BinarySpec?, TextReportBuilder?>(binaryRenderer)
        this.componentRenderer = ComponentRenderer(this.sourceSetRenderer, this.binaryRenderer)
    }

    override fun complete() {
        getTextOutput()!!.println()
        getTextOutput()!!.println("Note: currently not all plugins register their components, so some components may not be visible here.")
        super.complete()
    }

    fun renderComponents(components: MutableCollection<ComponentSpec?>) {
        if (components.isEmpty()) {
            getTextOutput()!!.withStyle(StyledTextOutput.Style.Info)!!.println("No components defined for this project.")
            return
        }
        var seen = false
        for (component in components) {
            if (seen) {
                getBuilder()!!.getOutput().println()
            } else {
                seen = true
            }
            getBuilder()!!.item<ComponentSpec?>(component, componentRenderer)
        }
    }

    fun renderSourceSets(sourceSets: MutableCollection<LanguageSourceSet?>) {
        val additionalSourceSets = collectAdditionalSourceSets(sourceSets)
        outputCollection<LanguageSourceSet?>(additionalSourceSets, "Additional source sets", sourceSetRenderer, "source sets")
    }

    fun renderBinaries(binaries: MutableCollection<BinarySpec?>) {
        val additionalBinaries = collectAdditionalBinaries(binaries)
        outputCollection<BinarySpec?>(additionalBinaries, "Additional binaries", binaryRenderer, "binaries")
    }

    private fun collectAdditionalSourceSets(sourceSets: MutableCollection<LanguageSourceSet?>): MutableSet<LanguageSourceSet?> {
        val result: MutableSet<LanguageSourceSet?> = Sets.newTreeSet<LanguageSourceSet?>(SourceSetRenderer.Companion.SORT_ORDER)
        result.addAll(sourceSets)
        result.removeAll(sourceSetRenderer.getItems())
        return result
    }

    private fun collectAdditionalBinaries(binaries: MutableCollection<BinarySpec?>): MutableSet<BinarySpec?> {
        val result: MutableSet<BinarySpec?> = Sets.newTreeSet<BinarySpec?>(TypeAwareBinaryRenderer.Companion.SORT_ORDER)
        result.addAll(binaries)
        result.removeAll(binaryRenderer.getItems())
        return result
    }

    private fun <T> outputCollection(items: MutableCollection<out T?>, title: String?, renderer: ReportRenderer<T?, TextReportBuilder?>?, elementsPlural: String?) {
        if (!items.isEmpty()) {
            getBuilder()!!.getOutput().println()
            getBuilder()!!.collection<T?>(title, items, renderer, elementsPlural)
        }
    }
}
