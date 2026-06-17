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
package org.gradle.internal.component.external.model

/**
 * This is a deprecated internal class that exists specifically to be used by the
 * Android and Kotlin Android plugins, which expect this type.
 *
 *
 * Be careful to not confuse it with the [ImmutableCapability][org.gradle.api.internal.capabilities.ImmutableCapability]
 * interface.
 *
 *
 * Without this class in place these smoke tests fail:
 *
 *  * `KotlinPluginAndroidKotlinDSLSmokeTest` and `KotlinPluginAndroidGroovyDSLSmokeTest` with kotlin 1.6.21, agp 7.3.1 - 8.0.0-alpha11.
 *  * `AndroidPluginsSmokeTest` with 7.3.1 - 8.0.0-alpha11.
 *  * `AndroidSantaTrackerIncrementalCompilationSmokeTest`, `AndroidSantaTrackerCachingCompilationSmokeTest`, `AndroidSantaTrackerDeprecationSmokeTest` with the same versions.
 *
 * They all fail with some version (different configurations and task names) of:
 *
 * `
 * Could not resolve all dependencies for configuration ':cityquiz:debugRuntimeClasspath'.
 * Could not create task ':santa-tracker:generateDebugFeatureTransitiveDeps'.
 * org/gradle/internal/component/external/model/ImmutableCapability
` *
 *
 * The task at fault is `com.android.build.gradle.internal.tasks.featuresplit.PackagedDependenciesWriterTask``.
 *
 */
@Deprecated("Use {@link DefaultImmutableCapability} instead.")
class ImmutableCapability(group: String?, name: String?, version: String?) : DefaultImmutableCapability(group, name, version)
