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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph

class CompositeDependencyGraphVisitor(visitors: MutableList<DependencyGraphVisitor>) : DependencyGraphVisitor {
    private val visitors: MutableList<DependencyGraphVisitor>

    init {
        this.visitors = visitors
    }

    override fun start(root: RootGraphNode) {
        for (visitor in visitors) {
            visitor.start(root)
        }
    }

    override fun visitNode(node: DependencyGraphNode) {
        for (visitor in visitors) {
            visitor.visitNode(node)
        }
    }

    override fun visitEdges(node: DependencyGraphNode) {
        for (visitor in visitors) {
            visitor.visitEdges(node)
        }
    }

    override fun finish(root: RootGraphNode) {
        for (visitor in visitors) {
            visitor.finish(root)
        }
    }
}
