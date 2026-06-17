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
package org.gradle.swiftpm.internal

import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectComponentPublication
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.provider.Providers
import org.gradle.api.provider.Provider
import org.gradle.internal.DisplayName

class NativeProjectPublication(private val displayName: DisplayName, private val swiftPmTarget: SwiftPmTarget?) : ProjectComponentPublication {
    override fun getDisplayName(): DisplayName {
        return displayName
    }

    override fun <T> getCoordinates(type: Class<T?>): T? {
        if (type.isAssignableFrom(SwiftPmTarget::class.java)) {
            return type.cast(swiftPmTarget)
        }
        return null
    }

    override fun getComponent(): Provider<SoftwareComponentInternal?> {
        return Providers.notDefined<SoftwareComponentInternal?>()
    }

    override fun isAlias(): Boolean {
        return false
    }

    override fun isLegacy(): Boolean {
        return false
    }
}
