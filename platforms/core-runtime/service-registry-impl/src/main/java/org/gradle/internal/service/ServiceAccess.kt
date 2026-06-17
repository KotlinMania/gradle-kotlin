/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.internal.service

import java.util.concurrent.atomic.AtomicInteger


/**
 * Factory for [ServiceAccessToken] and [ServiceAccessScope].
 */
internal object ServiceAccess {
    /**
     * Returns a scope that assumes to contain every possible token.
     */
    val publicScope: ServiceAccessScope = object : ServiceAccessScope {
        override fun contains(token: ServiceAccessToken?): Boolean {
            return true
        }

        override fun toString(): String {
            return "Public"
        }
    }

    private val NEXT_ID = AtomicInteger(0)

    /**
     * Creates a new unique token.
     */
    fun createToken(ownerDisplayName: String?): ServiceAccessToken {
        return DefaultServiceAccessToken(NEXT_ID.incrementAndGet(), ownerDisplayName)
    }

    /**
     * Returns a scope that contains only the provided token.
     */
    fun getPrivateScope(token: ServiceAccessToken): ServiceAccessScope {
        return PrivateAccessScope(token)
    }

    private class PrivateAccessScope(private val ownerToken: ServiceAccessToken) : ServiceAccessScope {
        override fun contains(token: ServiceAccessToken?): Boolean {
            return ownerToken == token
        }

        override fun toString(): String {
            return "Private(" + ownerToken + ")"
        }
    }
}
