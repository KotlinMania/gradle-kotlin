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
package org.gradle.internal.component.resolution.failure.formatting

import com.google.common.collect.ImmutableSet
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.attributes.AttributeDescriber
import org.gradle.internal.exceptions.StyledException
import org.gradle.internal.logging.text.StyledTextOutput

/**
 * An [AttributeDescriber] decorator that styles the output of a delegate for the console.
 */
class StyledAttributeDescriber(private val delegate: AttributeDescriber) : AttributeDescriber {
    override fun getDescribableAttributes(): ImmutableSet<Attribute<*>> {
        return delegate.getDescribableAttributes()
    }

    override fun describeAttributeSet(attributes: MutableMap<Attribute<*>, *>): String {
        return StyledException.style(StyledTextOutput.Style.Header, delegate.describeAttributeSet(attributes))
    }

    override fun describeMissingAttribute(attribute: Attribute<*>, consumerValue: Any): String {
        return StyledException.style(StyledTextOutput.Style.Info, delegate.describeMissingAttribute(attribute, consumerValue))
    }

    override fun describeExtraAttribute(attribute: Attribute<*>, producerValue: Any): String {
        return StyledException.style(StyledTextOutput.Style.Info, delegate.describeExtraAttribute(attribute, producerValue))
    }
}
