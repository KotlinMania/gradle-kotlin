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
package org.gradle.internal.enterprise.test.impl

import com.google.common.collect.ImmutableList
import org.gradle.internal.enterprise.test.CandidateClassFile
import org.gradle.internal.enterprise.test.InputFileProperty
import org.gradle.internal.enterprise.test.OutputFileProperty
import org.gradle.internal.enterprise.test.TestTaskFilters
import org.gradle.internal.enterprise.test.TestTaskForkOptions
import org.gradle.internal.enterprise.test.TestTaskProperties
import java.util.stream.Stream

internal class DefaultTestTaskProperties(
    private val usingJUnitPlatform: Boolean,
    private val forkEvery: Long,
    private val isDryRun: Boolean,
    private val filters: TestTaskFilters,
    private val forkOptions: TestTaskForkOptions,
    private val candidateClassFiles: ImmutableList<CandidateClassFile>,
    private val inputFileProperties: ImmutableList<InputFileProperty>,
    private val outputFileProperties: ImmutableList<OutputFileProperty>
) : TestTaskProperties {
    override fun isUsingJUnitPlatform(): Boolean {
        return usingJUnitPlatform
    }

    override fun getForkEvery(): Long {
        return forkEvery
    }

    override fun isDryRun(): Boolean {
        return isDryRun
    }

    override fun getFilters(): TestTaskFilters {
        return filters
    }

    override fun getForkOptions(): TestTaskForkOptions {
        return forkOptions
    }

    override fun getCandidateClassFiles(): Stream<CandidateClassFile> {
        return candidateClassFiles.stream()
    }

    override fun getInputFileProperties(): Stream<InputFileProperty> {
        return inputFileProperties.stream()
    }

    override fun getOutputFileProperties(): Stream<OutputFileProperty> {
        return outputFileProperties.stream()
    }
}
