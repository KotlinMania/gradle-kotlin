/*
 * Copyright 2010 the original author or authors.
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
 * Classes and interfaces for testing custom task and plugin implementations.
 */
package org.gradle.testfixtures

import org.gradle.api.logging.Logging.getLogger
import org.gradle.internal.service.ServiceRegistry.get
import org.gradle.api.JavaVersion.Companion.current
import org.gradle.api.JavaVersion.majorVersion
import org.gradle.api.internal.DocumentationRegistry.getDocumentationFor
import org.gradle.StartParameter.setGradleUserHomeDir
import org.gradle.StartParameter.setCurrentDir
import org.gradle.api.internal.StartParameterInternal.doNotSearchUpwards
import org.gradle.internal.nativeintegration.services.NativeServices.NativeServicesMode.Companion.fromSystemProperties
import org.gradle.internal.nativeintegration.services.NativeServices.Companion.initializeOnDaemon
import org.gradle.internal.time.Time.currentTimeMillis
import org.gradle.api.internal.StartParameterInternal.toBuildLayoutConfiguration
import org.gradle.internal.deprecation.DeprecationLogger.init
import org.gradle.internal.service.ServiceRegistryBuilder.Companion.builder
import org.gradle.internal.service.ServiceRegistryBuilder.displayName
import org.gradle.internal.service.ServiceRegistryBuilder.parent
import org.gradle.internal.logging.services.LoggingServiceRegistry.Companion.newNestedLogging
import org.gradle.internal.nativeintegration.services.NativeServices.Companion.getInstance
import org.gradle.internal.service.ServiceRegistryBuilder.provider
import org.gradle.internal.service.ServiceRegistryBuilder.build
import org.gradle.internal.instrumentation.agent.AgentStatus.Companion.disabled
import org.gradle.internal.logging.LoggingManagerFactory.createLoggingManager
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.serialize.Serializer.read
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.serialize.Serializer.write
import org.gradle.internal.serialize.OutputStreamBackedEncoder.flush

