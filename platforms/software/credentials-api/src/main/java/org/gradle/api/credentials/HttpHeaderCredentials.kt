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
package org.gradle.api.credentials

import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty

/**
 * Credentials that can be used to login to a protected server, e.g. a remote repository by using HTTP header.
 *
 * The properties used for creating credentials from a property are `repoAuthHeaderName` and `repoAuthHeaderValue`, where `repo` is the identity of the repository.
 *
 * @since 4.10
 */
interface HttpHeaderCredentials : Credentials {
    /**
     * Sets the header name to use when authenticating.
     *
     * @param name The header name. May be null.
     */
    @get:ToBeReplacedByLazyProperty
    var name: String?

    /**
     * Sets the header value to use when authenticating.
     *
     * @param value The header value. May be null.
     */
    @get:ToBeReplacedByLazyProperty
    var value: String?
}
