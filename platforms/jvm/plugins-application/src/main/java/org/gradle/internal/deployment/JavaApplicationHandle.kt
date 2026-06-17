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
package org.gradle.internal.deployment

import org.gradle.deployment.internal.Deployment
import org.gradle.deployment.internal.DeploymentHandle
import org.gradle.process.internal.ExecHandle
import org.gradle.process.internal.ExecHandleState
import org.gradle.process.internal.JavaExecHandleBuilder
import javax.inject.Inject

class JavaApplicationHandle @Inject constructor(private val builder: JavaExecHandleBuilder) : DeploymentHandle {
    private var handle: ExecHandle? = null

    override fun isRunning(): Boolean {
        return handle != null && handle!!.state == ExecHandleState.STARTED
    }

    override fun start(deployment: Deployment) {
        handle = builder.build().start()
    }

    override fun stop() {
        handle!!.abort()
    }
}
