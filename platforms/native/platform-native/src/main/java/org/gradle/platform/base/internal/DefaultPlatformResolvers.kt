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
package org.gradle.platform.base.internal

import org.gradle.api.InvalidUserDataException
import org.gradle.platform.base.Platform
import org.gradle.platform.base.PlatformContainer
import org.gradle.util.internal.CollectionUtils

class DefaultPlatformResolvers(private val platforms: PlatformContainer) : PlatformResolvers {
    private val platformResolvers: MutableList<PlatformResolver<*>> = ArrayList<PlatformResolver<*>>()

    override fun register(platformResolver: PlatformResolver<*>?) {
        platformResolvers.add(platformResolver!!)
    }

    override fun <T : Platform?> resolve(type: Class<T?>, platformRequirement: PlatformRequirement): T? {
        for (platformResolver in platformResolvers) {
            if (platformResolver.getType() == type) {
                val pr = platformResolver as PlatformResolver<T?>
                val resolved = pr.resolve(platformRequirement)
                if (resolved != null) {
                    return resolved
                }
            }
        }
        return
        T > < T > resolveFromContainer < T ? > (type, platformRequirement)
    }

    private fun <T : Platform?> resolveFromContainer(type: Class<T?>, platformRequirement: PlatformRequirement): T? {
        val target = platformRequirement.getPlatformName()

        val allWithType = platforms.withType<T?>(type)
        val matching: T? = CollectionUtils.findFirst<T?>(allWithType, org.gradle.api.specs.Spec { element: T? -> element!!.getName() == target })

        if (matching == null) {
            throw InvalidUserDataException(String.format("Invalid %s: %s", type.getSimpleName(), target))
        }
        return matching
    }
}
