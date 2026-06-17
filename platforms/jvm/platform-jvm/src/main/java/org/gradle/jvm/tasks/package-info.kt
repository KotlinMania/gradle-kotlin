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
/**
 * Tasks that add support for JVM runtime.
 */
package org.gradle.jvm.tasks

import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.api.specs.Spec.isSatisfiedBy
import org.gradle.internal.service.ServiceRegistry.get
import org.gradle.api.internal.artifacts.JavaEcosystemSupport.configureServices
import org.gradle.internal.logging.text.TreeFormatter.node
import org.gradle.internal.logging.text.TreeFormatter.startChildren
import org.gradle.internal.logging.text.TreeFormatter.endChildren
import org.gradle.internal.serialization.Cached.get
import org.gradle.internal.service.ServiceRegistration.addProvider
import org.gradle.internal.jvm.inspection.ConditionalInvalidation.invalidateItemsMatching
import org.gradle.internal.service.ServiceRegistration.add

