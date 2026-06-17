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
package org.gradle.internal.logging.serializer

import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.events.BooleanQuestionPromptEvent
import org.gradle.internal.logging.events.IntQuestionPromptEvent
import org.gradle.internal.logging.events.LogEvent
import org.gradle.internal.logging.events.LogLevelChangeEvent
import org.gradle.internal.logging.events.OutputEvent
import org.gradle.internal.logging.events.ProgressCompleteEvent
import org.gradle.internal.logging.events.ProgressEvent
import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.internal.logging.events.ReadStdInEvent
import org.gradle.internal.logging.events.SelectOptionPromptEvent
import org.gradle.internal.logging.events.StyledTextOutputEvent
import org.gradle.internal.logging.events.TextQuestionPromptEvent
import org.gradle.internal.logging.events.UserInputRequestEvent
import org.gradle.internal.logging.events.UserInputResumeEvent
import org.gradle.internal.logging.events.YesNoQuestionPromptEvent
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.serialize.BaseSerializerFactory
import org.gradle.internal.serialize.DefaultSerializerRegistry
import org.gradle.internal.serialize.ListSerializer
import org.gradle.internal.serialize.Serializer

object OutputEventSerializer {
    @JvmStatic
    fun registerTypes(registry: DefaultSerializerRegistry) {
        val factory = BaseSerializerFactory()
        val logLevelSerializer = factory.getSerializerFor<LogLevel>(LogLevel::class.java)
        val throwableSerializer = factory.getSerializerFor<Throwable>(Throwable::class.java)

        registry.register<LogEvent>(LogEvent::class.java, LogEventSerializer(logLevelSerializer, throwableSerializer))
        registry.register<UserInputRequestEvent>(UserInputRequestEvent::class.java, UserInputRequestEventSerializer())
        registry.register<YesNoQuestionPromptEvent>(YesNoQuestionPromptEvent::class.java, YesNoQuestionPromptEventSerializer())
        registry.register<BooleanQuestionPromptEvent>(BooleanQuestionPromptEvent::class.java, BooleanQuestionPromptEventSerializer())
        registry.register<TextQuestionPromptEvent>(TextQuestionPromptEvent::class.java, TextQuestionPromptEventSerializer())
        registry.register<IntQuestionPromptEvent>(IntQuestionPromptEvent::class.java, IntQuestionPromptEventSerializer())
        registry.register<SelectOptionPromptEvent>(SelectOptionPromptEvent::class.java, SelectOptionPromptEventSerializer())
        registry.register<UserInputResumeEvent>(UserInputResumeEvent::class.java, UserInputResumeEventSerializer())
        registry.register<ReadStdInEvent>(ReadStdInEvent::class.java, ReadStdInEventSerializer())
        registry.register<StyledTextOutputEvent>(
            StyledTextOutputEvent::class.java,
            StyledTextOutputEventSerializer(
                logLevelSerializer,
                ListSerializer<StyledTextOutputEvent.Span?>(SpanSerializer(factory.getSerializerFor<StyledTextOutput.Style>(StyledTextOutput.Style::class.java)))
            )
        )
        registry.register<ProgressStartEvent>(ProgressStartEvent::class.java, ProgressStartEventSerializer())
        registry.register<ProgressCompleteEvent>(ProgressCompleteEvent::class.java, ProgressCompleteEventSerializer())
        registry.register<ProgressEvent>(ProgressEvent::class.java, ProgressEventSerializer())
        registry.register<LogLevelChangeEvent>(LogLevelChangeEvent::class.java, LogLevelChangeEventSerializer(logLevelSerializer))
    }

    fun create(): Serializer<OutputEvent> {
        val registry = DefaultSerializerRegistry()
        registerTypes(registry)
        return registry.build<OutputEvent>(OutputEvent::class.java)
    }
}
