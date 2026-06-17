/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.reporting.dependents.internal

import org.gradle.api.Action
import org.gradle.api.tasks.diagnostics.internal.text.TextReportBuilder
import org.gradle.internal.graph.GraphRenderer
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.platform.base.ComponentSpec
import org.gradle.platform.base.VariantComponentSpec
import org.gradle.platform.base.internal.BinarySpecInternal
import org.gradle.platform.base.internal.ComponentSpecInternal
import org.gradle.platform.base.internal.dependents.DependentBinariesResolver
import org.gradle.reporting.ReportRenderer

class DependentComponentsRenderer(private val resolver: DependentBinariesResolver?, private val showNonBuildable: Boolean, private val showTestSuites: Boolean) :
    ReportRenderer<ComponentSpec?, TextReportBuilder?>() {
    private var seenTestSuite = false
    private var hiddenTestSuite = false
    private var hiddenNonBuildable = false

    public override fun render(component: ComponentSpec, builder: TextReportBuilder) {
        val internalProtocol = component as ComponentSpecInternal
        val root = getRenderableDependencyOf(component, internalProtocol)
        if (!showNonBuildable && !root.isBuildable()) {
            hiddenNonBuildable = true
            return
        }
        val output = builder.getOutput()
        val renderer = GraphRenderer(output)
        renderer.visit(Action { output1: StyledTextOutput? ->
            output1!!.withStyle(StyledTextOutput.Style.Identifier)!!.text(component.getName())
            output1.withStyle(StyledTextOutput.Style.Description)!!.text(" - Components that depend on " + component.getDisplayName())
        }, true)
        val dependentsGraphRenderer = DependentComponentsGraphRenderer(renderer, showNonBuildable, showTestSuites)
        if (root.getChildren().isEmpty()) {
            output.withStyle(StyledTextOutput.Style.Info)!!.text("No dependents")
            output.println()
        } else {
            dependentsGraphRenderer.render(root)
            output.println()
        }
        if (dependentsGraphRenderer.hasSeenTestSuite()) {
            seenTestSuite = true
        }
        if (dependentsGraphRenderer.hasHiddenTestSuite()) {
            hiddenTestSuite = true
        }
        if (dependentsGraphRenderer.hasHiddenNonBuildable()) {
            hiddenNonBuildable = true
        }
    }

    private fun getRenderableDependencyOf(componentSpec: ComponentSpec, internalProtocol: ComponentSpecInternal): DependentComponentsRenderableDependency {
        if (resolver != null && componentSpec is VariantComponentSpec) {
            val variantComponentSpec = componentSpec
            val children = LinkedHashSet<DependentComponentsRenderableDependency>()
            for (binarySpec in variantComponentSpec.getBinaries().withType<BinarySpecInternal>(BinarySpecInternal::class.java)) {
                val resolvedBinary = resolver.resolve(binarySpec)
                children.add(DependentComponentsRenderableDependency.Companion.of(resolvedBinary.getRoot()))
            }
            return DependentComponentsRenderableDependency.Companion.of(componentSpec, internalProtocol, children)
        } else {
            return of(componentSpec, internalProtocol)
        }
    }

    fun printLegend(builder: TextReportBuilder) {
        if (seenTestSuite || hiddenTestSuite || hiddenNonBuildable) {
            val output = builder.getOutput()
            if (seenTestSuite) {
                output.withStyle(StyledTextOutput.Style.Info)!!.println("(t) - Test suite binary")
                if (hiddenNonBuildable) {
                    output.println()
                }
            } else if (hiddenTestSuite) {
                output.withStyle(StyledTextOutput.Style.Info)!!.println("Some test suites were not shown, use --test-suites or --all to show them.")
            }
            if (hiddenNonBuildable) {
                output.withStyle(StyledTextOutput.Style.Info)!!.println("Some non-buildable components were not shown, use --non-buildable or --all to show them.")
            }
        }
    }
}
