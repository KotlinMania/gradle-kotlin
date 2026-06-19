/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.api.problems.internal

import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.problems.buildtree.ProblemStream
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer

class ProblemsInfrastructure(
    @JvmField val additionalDataBuilderFactory: AdditionalDataBuilderFactory?,
    val instantiator: Instantiator?,
    val payloadSerializer: PayloadSerializer?,
    val isolatableFactory: IsolatableFactory?,
    val isolatableSerializer: IsolatableToBytesSerializer?,
    val problemStream: ProblemStream?
)
