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

import com.google.common.base.Predicate
import com.google.common.collect.Sets
import org.gradle.api.Action
import org.gradle.api.tasks.diagnostics.internal.graph.NodeRenderer
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency
import org.gradle.internal.graph.GraphRenderer
import org.gradle.internal.logging.text.StyledTextOutput

class DependentComponentsGraphRenderer(private val renderer: GraphRenderer, showNonBuildable: Boolean, showTestSuites: Boolean) {
    private val nodeRenderer: DependentBinaryNodeRenderer
    private val showDependentPredicate: ShowDependentPredicate

    init {
        this.nodeRenderer = DependentBinaryNodeRenderer()
        this.showDependentPredicate = ShowDependentPredicate(showNonBuildable, showTestSuites)
    }

    fun render(root: DependentComponentsRenderableDependency) {
        renderChildren(getChildren(root))
    }

    private fun renderChildren(children: MutableSet<out RenderableDependency>) {
        renderer.startChildren()
        var idx = 0
        for (child in children) {
            val last = idx++ == children.size - 1
            doRender(child, last)
        }
        renderer.completeChildren()
    }

    private fun doRender(node: RenderableDependency, last: Boolean) {
        renderer.visit(Action { output: StyledTextOutput? -> nodeRenderer.renderNode(output!!, node, false) }, last)
        renderChildren(getChildren(node))
    }

    fun hasSeenTestSuite(): Boolean {
        return nodeRenderer.seenTestSuite
    }

    fun hasHiddenTestSuite(): Boolean {
        return showDependentPredicate.hiddenTestSuite
    }

    fun hasHiddenNonBuildable(): Boolean {
        return showDependentPredicate.hiddenNonBuildable
    }

    private fun getChildren(node: RenderableDependency): MutableSet<out RenderableDependency> {
        return Sets.filter(node.getChildren(), showDependentPredicate)
    }

    private class DependentBinaryNodeRenderer : NodeRenderer {
        private var seenTestSuite = false

        override fun renderNode(output: StyledTextOutput, node: RenderableDependency, alreadyRendered: Boolean) {
            output.text(node.getName())
            if (node is DependentComponentsRenderableDependency) {
                val dep = node
                if (dep.isTestSuite()) {
                    output.withStyle(StyledTextOutput.Style.Info)!!.text(" (t)")
                    seenTestSuite = true
                }
                if (!dep.isBuildable()) {
                    output.withStyle(StyledTextOutput.Style.Info)!!.text(" NOT BUILDABLE")
                }
            }
        }
    }

    private class ShowDependentPredicate(private val showNonBuildable: Boolean, private val showTestSuites: Boolean) : Predicate<RenderableDependency?> {
        private var hiddenNonBuildable = false
        private var hiddenTestSuite = false

        override fun apply(node: RenderableDependency?): Boolean {
            if (node is DependentComponentsRenderableDependency) {
                val dep = node
                val hideNonBuildable = !dep.isBuildable() && !showNonBuildable
                val hideTestSuite = dep.isTestSuite() && !showTestSuites
                if (hideNonBuildable) {
                    hiddenNonBuildable = true
                }
                if (hideTestSuite) {
                    hiddenTestSuite = true
                }
                return !hideNonBuildable && !hideTestSuite
            }
            return false
        }
    }
}
