/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.internal.locking

import org.gradle.api.logging.Logging.getLogger
import org.gradle.api.internal.DocumentationRegistry.getDocumentationRecommendationFor
import org.gradle.api.specs.Spec.isSatisfiedBy
import org.gradle.StartParameter.isWriteDependencyLocks
import org.gradle.StartParameter.lockedDependenciesToUpdate
import org.gradle.api.logging.Logger.lifecycle
import org.gradle.internal.lazy.Lazy.Companion.locking
import org.gradle.internal.lazy.Lazy.Factory.of

