/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.verification.serializer

internal object DependencyVerificationXmlTags {
    const val ALSO_TRUST: String = "also-trust"
    const val ARTIFACT: String = "artifact"
    const val COMPONENT: String = "component"
    const val COMPONENTS: String = "components"
    const val CONFIG: String = "configuration"
    const val ENABLED: String = "enabled"
    const val FILE: String = "file"
    const val GROUP: String = "group"
    const val ID: String = "id"
    const val IGNORED_KEY: String = "ignored-key"
    const val IGNORED_KEYS: String = "ignored-keys"
    const val KEY_SERVER: String = "key-server"
    const val KEY_SERVERS: String = "key-servers"
    const val NAME: String = "name"
    const val ORIGIN: String = "origin"
    const val PGP: String = "pgp"
    const val REASON: String = "reason"
    const val REGEX: String = "regex"
    const val TRUST: String = "trust"
    const val TRUSTED_ARTIFACTS: String = "trusted-artifacts"
    const val TRUSTED_KEY: String = "trusted-key"
    const val TRUSTED_KEYS: String = "trusted-keys"
    const val TRUSTING: String = "trusting"
    const val URI: String = "uri"
    const val VALUE: String = "value"
    const val VERIFICATION_METADATA: String = "verification-metadata"
    const val VERIFY_METADATA: String = "verify-metadata"
    const val VERIFY_SIGNATURES: String = "verify-signatures"
    const val VERSION: String = "version"
    const val KEYRING_FORMAT: String = "keyring-format"
}
