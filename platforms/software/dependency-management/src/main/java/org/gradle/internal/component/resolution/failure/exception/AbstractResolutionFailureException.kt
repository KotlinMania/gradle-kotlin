/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.internal.component.resolution.failure.exception

import com.google.common.collect.ImmutableList
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging.getLogger
import org.gradle.api.problems.Problem
import org.gradle.api.problems.internal.GradleCoreProblemGroup.variantResolution
import org.gradle.api.problems.internal.ProblemsInternal
import org.gradle.api.problems.internal.ResolutionFailureDataSpec
import org.gradle.internal.component.resolution.failure.ReportableAsProblem
import org.gradle.internal.component.resolution.failure.interfaces.ResolutionFailure
import org.gradle.internal.deprecation.Documentation.Companion.userManual
import org.gradle.internal.exceptions.Contextual
import org.gradle.internal.exceptions.ResolutionProvider
import org.gradle.internal.exceptions.StyledException
import org.gradle.util.internal.TextUtil

/**
 * Abstract base class for all [ResolutionFailure]s occurring during dependency resolution that can be handled
 * by the [ResolutionFailureHandler].
 *
 *
 * This exception type carries information about the failure, and implements [ResolutionProvider] to provide a
 * list of resolutions that may help the user to fix the problem.  This class is meant to be immutable.
 *
 * @implNote This class should not be subclassed beyond the existing
 * [VariantSelectionByNameException], [ArtifactSelectionException], [GraphValidationException] and
 * [VariantSelectionByAttributesException] subtypes.  All subtypes should remain immutable.
 */
@Contextual
abstract class AbstractResolutionFailureException @JvmOverloads constructor(message: String, protected val failure: ResolutionFailure, resolutions: MutableList<String>, cause: Throwable? = null) :
    StyledException(message, cause), ResolutionProvider, ReportableAsProblem {
    private val resolutions: ImmutableList<String>

    init {
        this.resolutions = ImmutableList.copyOf<String>(resolutions)

        LOGGER.info("Variant Selection Exception: {} caused by Resolution Failure: {}", this.javaClass.getName(), getFailure()!!.javaClass.getName())
    }

    abstract fun getFailure(): ResolutionFailure?

    public override fun getResolutions(): ImmutableList<String> {
        return resolutions
    }

    override fun reportAsProblem(problemsService: ProblemsInternal): AbstractResolutionFailureException {
        val problem: Problem? = problemsService.internalReporter!!.internalCreate({ builder ->
            val problemId = getFailure()!!.getProblemId()
            builder.id(TextUtil.screamingSnakeToKebabCase(problemId.name), problemId.getDisplayName(), variantResolution())!!
                .contextualLabel(message!!)!!
                .documentedAt(userManual("variant_model", "sec:variant-select-errors"))!!
                .additionalDataInternal(ResolutionFailureDataSpec::class.java, { data -> data!!.from(getFailure()) })
        })
        problemsService.internalReporter!!.reportError(problem!!)

        return this
    }

    companion object {
        protected val LOGGER: Logger = getLogger(AbstractResolutionFailureException::class.java)!!
    }
}
