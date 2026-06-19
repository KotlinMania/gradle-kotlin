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
package org.gradle.tooling.internal.protocol

import java.io.Serializable

/**
 *
 * DO NOT CHANGE THIS INTERFACE - it is part of the cross-version protocol.
 *
 *
 * Consumer compatibility: This interface is implemented by all consumer versions from 4.8.
 *
 * Provider compatibility: This interface is consumed by all provider versions from 4.8.
 *
 * @since 4.8
 */
interface InternalPhasedAction : InternalProtocolInterface, Serializable {
    /**
     * Internal version of the action to be run after projects are loaded.
     */
    val projectsLoadedAction: InternalBuildActionVersion2<*>?

    /**
     * Internal version of the action to be run after tasks are run.
     */
    val buildFinishedAction: InternalBuildActionVersion2<*>?
}
