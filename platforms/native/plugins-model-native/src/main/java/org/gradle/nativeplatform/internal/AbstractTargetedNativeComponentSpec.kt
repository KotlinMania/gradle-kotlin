/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.nativeplatform.internal

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Named
import org.gradle.nativeplatform.BuildType
import org.gradle.nativeplatform.Flavor
import org.gradle.platform.base.internal.DefaultPlatformRequirement.Companion.create
import org.gradle.platform.base.internal.PlatformRequirement
import java.util.Collections

abstract class AbstractTargetedNativeComponentSpec : AbstractNativeComponentSpec(), TargetedNativeComponentInternal {
    private val targetPlatforms: MutableList<PlatformRequirement?> = ArrayList<PlatformRequirement?>()
    private val buildTypes: MutableSet<String?> = HashSet<String?>()
    private val flavors: MutableSet<String?> = HashSet<String?>()

    override fun getTargetPlatforms(): MutableList<PlatformRequirement?> {
        return Collections.unmodifiableList<PlatformRequirement?>(targetPlatforms)
    }

    override fun targetPlatform(targetPlatform: String) {
        this.targetPlatforms.add(create(targetPlatform))
    }

    override fun targetFlavors(vararg flavorSelectors: String?) {
        Collections.addAll<String?>(flavors, *flavorSelectors)
    }

    override fun targetBuildTypes(vararg buildTypeSelectors: String?) {
        Collections.addAll<String?>(buildTypes, *buildTypeSelectors)
    }

    override fun chooseFlavors(candidates: MutableSet<out Flavor?>): MutableSet<Flavor?> {
        return chooseElements<Flavor?>(Flavor::class.java, candidates, flavors)
    }

    override fun chooseBuildTypes(candidates: MutableSet<out BuildType?>): MutableSet<BuildType?> {
        return chooseElements<BuildType?>(BuildType::class.java, candidates, buildTypes)
    }

    protected fun <T : Named?> chooseElements(type: Class<T?>, candidates: MutableSet<out T?>, names: MutableSet<String?>): MutableSet<T?> {
        if (names.isEmpty()) {
            return LinkedHashSet<T?>(candidates)
        }

        val unusedNames: MutableSet<String?> = HashSet<String?>(names)
        val chosen: MutableSet<T?> = LinkedHashSet<T?>()
        for (candidate in candidates) {
            if (unusedNames.remove(candidate!!.getName())) {
                chosen.add(candidate)
            }
        }

        if (!unusedNames.isEmpty()) {
            throw InvalidUserDataException(String.format("Invalid %s: '%s'", type.getSimpleName(), unusedNames.iterator().next()))
        }

        return chosen
    }
}
