/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.artifacts.transform

import org.gradle.api.Action

/**
 * A series of [TransformStep]s.
 */
class TransformChain
/**
 * @param init The initial steps of this chain, or null if this chain only contains one step.
 * @param last The last step of this chain.
 */(
    /**
     * @return The initial steps of this chain, or null if this chain only contains one step.
     */
    val init: TransformChain?,
    /**
     * @return The last step of this chain.
     */
    val last: TransformStep
) {
    fun requiresDependencies(): Boolean {
        return (init != null && init.requiresDependencies()) || last.requiresDependencies()
    }

    val displayName: String
        get() {
            val lastDisplayName = last.getDisplayName()
            return if (init == null)
                lastDisplayName
            else
                init.getDisplayName() + " -> " + lastDisplayName
        }

    fun visitTransformSteps(action: Action<in TransformStep>) {
        if (init != null) {
            init.visitTransformSteps(action)
        }
        action.execute(last)
    }

    fun length(): Int {
        return (if (init == null) 0 else init.length()) + 1
    }
}
