/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.wrapper

import java.net.URI

class WrapperConfiguration {
    var distribution: URI? = null
    var distributionBase: String = PathAssembler.GRADLE_USER_HOME_STRING
    var distributionPath: String = Install.DEFAULT_DISTRIBUTION_PATH
    var distributionSha256Sum: String? = null
    var zipBase: String = PathAssembler.GRADLE_USER_HOME_STRING
    var zipPath: String = Install.DEFAULT_DISTRIBUTION_PATH
    var networkTimeout: Int = Download.Companion.DEFAULT_NETWORK_TIMEOUT_MILLISECONDS
    var validateDistributionUrl: Boolean = true
    var retries: Int = Install.DEFAULT_NETWORK_RETRIES
    var retryBackOffMs: Int = Install.DEFAULT_NETWORK_RETRY_BACK_OFF_MS
}
