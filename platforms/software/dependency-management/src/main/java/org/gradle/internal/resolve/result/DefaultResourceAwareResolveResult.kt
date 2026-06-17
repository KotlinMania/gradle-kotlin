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
package org.gradle.internal.resolve.result

import com.google.common.collect.ImmutableList
import org.gradle.internal.resource.ExternalResourceName

open class DefaultResourceAwareResolveResult : ResourceAwareResolveResult {
    private var attempted: MutableSet<String?>? = null

    override fun getAttempted(): MutableList<String?> {
        return if (attempted == null) mutableListOf<String?>() else ImmutableList.copyOf<String?>(attempted)
    }

    override fun attempted(locationDescription: String?) {
        if (attempted == null) {
            attempted = LinkedHashSet<String?>()
        }
        attempted!!.add(locationDescription)
    }

    override fun attempted(location: ExternalResourceName) {
        attempted(location.getDisplayName())
    }

    override fun applyTo(target: ResourceAwareResolveResult) {
        if (attempted != null) {
            for (location in attempted) {
                target.attempted(location)
            }
        }
    }
}
