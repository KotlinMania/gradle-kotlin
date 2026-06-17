/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.internal.resolve

import com.google.common.base.Objects
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.attributes.ImmutableAttributesEntry
import org.gradle.api.internal.attributes.matching.AttributeMatcher
import org.gradle.internal.logging.text.TreeFormatter
import java.util.function.Function

class RejectedByAttributesVersion(id: ModuleComponentIdentifier?, val matchingDescription: MutableList<AttributeMatcher.MatchingDescription<*>>) : RejectedVersion(id) {
    override fun describeTo(builder: TreeFormatter) {
        matchingDescription.sort(DESCRIPTION_COMPARATOR)
        builder.node(getId().getVersion())
        builder.startChildren()
        for (description in matchingDescription) {
            builder.node("Attribute '" + description.getRequested().getKey().getName() + "'")
            if (description.isMatch()) {
                builder.append(" matched. ")
            } else {
                builder.append(" didn't match. ")
            }
            builder.append("Requested " + prettify(description.getRequested()) + ", was: " + prettify(description.getFound()))
        }
        builder.endChildren()
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        if (!super.equals(o)) {
            return false
        }
        val that = o as RejectedByAttributesVersion
        return Objects.equal(getId(), that.getId())
    }

    override fun hashCode(): Int {
        return getId().hashCode()
    }

    companion object {
        private val DESCRIPTION_COMPARATOR: Comparator<AttributeMatcher.MatchingDescription<*>?> =
            Comparator.comparing<AttributeMatcher.MatchingDescription<*>?, String?>(Function { o: AttributeMatcher.MatchingDescription<*>? -> o!!.getRequested().getKey().getName() })

        private fun prettify(entry: ImmutableAttributesEntry<*>?): String {
            if (entry != null) {
                return "'" + entry.getIsolatedValue() + "'"
            } else {
                return "not found"
            }
        }
    }
}
