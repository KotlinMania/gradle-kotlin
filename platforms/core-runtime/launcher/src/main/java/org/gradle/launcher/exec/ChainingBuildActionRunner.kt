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
package org.gradle.launcher.exec

import org.gradle.internal.buildtree.BuildActionRunner
import org.gradle.internal.buildtree.BuildTreeLifecycleController
import org.gradle.internal.invocation.BuildAction

class ChainingBuildActionRunner(private val runners: MutableList<out BuildActionRunner>) : BuildActionRunner {
    override fun run(action: BuildAction, buildController: BuildTreeLifecycleController): BuildActionRunner.Result {
        for (runner in runners) {
            val result = runner.run(action, buildController)
            if (result.hasResult()) {
                return result
            }
        }
        throw UnsupportedOperationException(String.format("Don't know how to run a build action of type %s.", action.javaClass.getSimpleName()))
    }
}
