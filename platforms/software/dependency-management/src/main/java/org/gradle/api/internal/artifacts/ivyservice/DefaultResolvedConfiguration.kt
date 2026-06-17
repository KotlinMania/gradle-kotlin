/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice

import org.gradle.api.artifacts.LenientConfiguration
import org.gradle.api.artifacts.ResolveException
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.internal.artifacts.configurations.ResolutionHost
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.VisitedGraphResults
import java.util.function.Consumer

class DefaultResolvedConfiguration(
    private val graphResults: VisitedGraphResults,
    private val resolutionHost: ResolutionHost,
    private val visitedArtifacts: VisitedArtifactSet,
    private val configuration: LenientConfigurationInternal
) : ResolvedConfiguration {
    override fun hasError(): Boolean {
        return graphResults.hasAnyFailure()
    }

    @Throws(ResolveException::class)
    override fun rethrowFailure() {
        if (!graphResults.hasAnyFailure()) {
            return
        }

        val failures: MutableList<Throwable?> = ArrayList<Throwable?>()
        graphResults.visitFailures(Consumer { e: Throwable? -> failures.add(e) })
        resolutionHost.rethrowFailuresAndReportProblems("dependencies", failures)
    }

    override fun getLenientConfiguration(): LenientConfiguration {
        return configuration
    }

    @Throws(ResolveException::class)
    override fun getFirstLevelModuleDependencies(): MutableSet<ResolvedDependency?> {
        rethrowFailure()
        return configuration.getFirstLevelModuleDependencies()
    }

    @Throws(ResolveException::class)
    override fun getResolvedArtifacts(): MutableSet<ResolvedArtifact?> {
        val visitor = ArtifactCollectingVisitor()
        visitedArtifacts.select(configuration.implicitSelectionSpec).visitArtifacts(visitor, false)
        resolutionHost.rethrowFailuresAndReportProblems("artifacts", visitor.getFailures())
        return visitor.getArtifacts()
    }
}
