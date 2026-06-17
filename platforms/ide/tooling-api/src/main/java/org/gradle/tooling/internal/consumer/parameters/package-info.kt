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
package org.gradle.tooling.internal.consumer.parameters

import org.gradle.internal.Factory.create
import org.gradle.tooling.internal.consumer.connection.AbstractConsumerConnection.configure
import org.gradle.tooling.internal.consumer.connection.AbstractConsumerConnection.versionDetails
import org.gradle.internal.logging.progress.ProgressLoggerFactory.newOperation
import org.gradle.internal.logging.progress.ProgressLogger.setDescription
import org.gradle.internal.logging.progress.ProgressLogger.started
import org.gradle.internal.logging.progress.ProgressLogger.completed
import org.gradle.internal.event.ListenerBroadcast.addAll
import org.gradle.internal.event.ListenerBroadcast.getSource
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter.unpack
import org.gradle.internal.event.ListenerBroadcast.isEmpty
import org.gradle.tooling.events.ProgressListener.statusChanged
import org.gradle.internal.Cast.uncheckedNonnullCast
import org.gradle.tooling.events.test.internal.source.DefaultNoSource.instance
import org.gradle.tooling.events.test.internal.source.DefaultOtherSource.instance
import org.gradle.tooling.events.test.Destination.Companion.fromCode
import org.gradle.tooling.events.problems.internal.DefaultSeverity.Companion.from
import org.gradle.tooling.events.task.internal.TaskExecutionDetails.Companion.unsupported

