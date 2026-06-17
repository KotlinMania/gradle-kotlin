/*
 * Copyright 2023 the original author or authors.
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
/**
 * new Problems API
 */
package org.gradle.api.problems

import org.gradle.operations.problems.FileLocation.path
import org.gradle.operations.problems.LineInFileLocation.line
import org.gradle.operations.problems.LineInFileLocation.column
import org.gradle.operations.problems.LineInFileLocation.length
import org.gradle.operations.problems.OffsetInFileLocation.offset
import org.gradle.operations.problems.OffsetInFileLocation.length
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer.serialize
import org.gradle.operations.problems.Problem.definition
import org.gradle.operations.problems.Problem.severity
import org.gradle.operations.problems.Problem.contextualLabel
import org.gradle.operations.problems.Problem.solutions
import org.gradle.operations.problems.Problem.details
import org.gradle.operations.problems.Problem.originLocations
import org.gradle.operations.problems.Problem.contextualLocations

