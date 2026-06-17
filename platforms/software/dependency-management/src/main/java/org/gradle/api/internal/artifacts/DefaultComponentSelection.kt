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
package org.gradle.api.internal.artifacts

import org.gradle.api.artifacts.ComponentMetadata
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.MetadataProvider

class DefaultComponentSelection(private val candidate: ModuleComponentIdentifier, private val metadataProvider: MetadataProvider) : ComponentSelectionInternal {
    private var rejected = false
    private var rejectionReason: String? = null

    override fun getCandidate(): ModuleComponentIdentifier {
        return candidate
    }

    override fun getMetadata(): ComponentMetadata? {
        if (metadataProvider.isUsable) {
            return metadataProvider.componentMetadata
        } else {
            return null
        }
    }

    override fun <T> getDescriptor(descriptorClass: Class<T?>): T? {
        if (metadataProvider.isUsable) {
            if (IvyModuleDescriptor::class.java.isAssignableFrom(descriptorClass)) {
                return descriptorClass.cast(metadataProvider.ivyModuleDescriptor)
            }
        }
        return null
    }

    override fun reject(reason: String) {
        rejected = true
        rejectionReason = reason
    }

    override fun isRejected(): Boolean {
        return rejected
    }

    override fun getRejectionReason(): String? {
        return rejectionReason
    }
}
