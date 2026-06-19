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
package org.gradle.internal.resource.transport.http

import com.google.common.base.Preconditions
import org.gradle.api.credentials.Credentials
import org.jspecify.annotations.NullMarked


interface HttpProxySettings {
    val proxy: HttpProxy?

    class HttpProxy(@JvmField val host: String?, @JvmField val port: Int, username: String?, password: String?) {
        @JvmField
        val credentials: HttpProxyCredentials?

        init {
            if (username == null || username.isEmpty()) {
                credentials = null
            } else {
                credentials = HttpProxyCredentials(username, password)
            }
        }
    }

    @NullMarked
    class HttpProxyCredentials(username: String, @JvmField val password: String?) : Credentials {
        @JvmField
        val username: String

        init {
            this.username = Preconditions.checkNotNull<String>(username)
        }
    }
}
