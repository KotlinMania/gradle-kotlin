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
package org.gradle.internal.verifier

import org.gradle.util.internal.GUtil
import java.net.URI
import java.util.Objects
import java.util.function.Consumer

/**
 * Used to create instances of [HttpRedirectVerifier].
 */
object HttpRedirectVerifierFactory {
    /**
     * Verifies that the base URL and all subsequent redirects followed during an interaction with a server are done so securely unless
     * the user has explicitly opted out from this protection.
     *
     * @param baseHost The host specified by the user.
     * @param allowInsecureProtocol If true, allows HTTP based connections.
     * @param insecureBaseHost Callback when the base host URL is insecure.
     * @param insecureRedirect Callback when the server returns an 30x redirect to an insecure server.
     */
    fun create(
        baseHost: URI?,
        allowInsecureProtocol: Boolean,
        insecureBaseHost: Runnable?,
        insecureRedirect: Consumer<URI?>?
    ): HttpRedirectVerifier? {
        Objects.requireNonNull<Runnable?>(insecureBaseHost, "insecureBaseHost must not be null")
        Objects.requireNonNull<Consumer<URI?>?>(insecureRedirect, "insecureRedirect must not be null")
        if (allowInsecureProtocol) {
            return NoopHttpRedirectVerifier.Companion.instance
        } else {
            // Verify that the base URL is secure now.
            if (baseHost != null && !GUtil.isSecureUrl(baseHost)) {
                insecureBaseHost!!.run()
            }

            // Verify that any future redirect locations are secure.
            // Lambda will be called back on for every redirect in the chain.
            return HttpRedirectVerifier { redirectLocations: MutableCollection<URI?>? ->
                redirectLocations!!
                    .stream()
                    .filter { url: URI? -> !GUtil.isSecureUrl(url) }
                    .forEach(insecureRedirect)
            }
        }
    }

    private class NoopHttpRedirectVerifier : HttpRedirectVerifier {
        override fun validateRedirects(redirectLocations: MutableCollection<URI?>?) {
            // Noop
        }

        companion object {
            private val instance = NoopHttpRedirectVerifier()
        }
    }
}
