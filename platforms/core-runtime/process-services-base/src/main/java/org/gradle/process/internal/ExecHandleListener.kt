/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.process.internal

import org.gradle.process.ExecResult

interface ExecHandleListener {
    /**
     * Called before the execution of the ExecHandle starts. Unlike [.executionStarted], this method is called synchronously from [ExecHandle.start].
     *
     * @param execHandle the handle that is about to start
     */
    fun beforeExecutionStarted(execHandle: ExecHandle?)

    /**
     * Called before the worker thread starts running the `execHandle`.
     *
     * @param execHandle the handle that is about to start
     */
    fun executionStarted(execHandle: ExecHandle?)

    fun executionFinished(execHandle: ExecHandle?, execResult: ExecResult?)
}
