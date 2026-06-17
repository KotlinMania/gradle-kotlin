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
package org.gradle.nativeplatform.toolchain.internal.tools

import org.gradle.api.Action
import org.gradle.internal.Actions
import org.gradle.nativeplatform.toolchain.internal.ToolType

open class DefaultCommandLineToolConfiguration(@JvmField val toolType: ToolType?) : CommandLineToolConfigurationInternal {
    private val argActions: MutableList<Action<in MutableList<String?>?>?> = ArrayList<Action<in MutableList<String?>?>?>()

    override fun withArguments(action: Action<in MutableList<String?>?>?) {
        argActions.add(action)
    }

    override fun getArgAction(): Action<MutableList<String?>?> {
        return Actions.composite<MutableList<String?>?>(argActions)
    }
}
