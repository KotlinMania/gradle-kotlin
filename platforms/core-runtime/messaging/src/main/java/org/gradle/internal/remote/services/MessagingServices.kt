/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.remote.services

import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.id.IdGenerator
import org.gradle.internal.id.UUIDGenerator
import org.gradle.internal.remote.MessagingClient
import org.gradle.internal.remote.MessagingServer
import org.gradle.internal.remote.internal.IncomingConnector
import org.gradle.internal.remote.internal.OutgoingConnector
import org.gradle.internal.remote.internal.hub.MessageHubBackedClient
import org.gradle.internal.remote.internal.hub.MessageHubBackedServer
import org.gradle.internal.remote.internal.inet.InetAddressFactory
import org.gradle.internal.remote.internal.inet.TcpIncomingConnector
import org.gradle.internal.remote.internal.inet.TcpOutgoingConnector
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistrationProvider
import java.util.UUID

/**
 * A factory for a set of messaging services. Provides the following services:
 *
 *
 *
 *  * [MessagingClient]
 *
 *  * [MessagingServer]
 *
 *
 */
open class MessagingServices : ServiceRegistrationProvider {
    private val idGenerator: IdGenerator<UUID> = UUIDGenerator()

    @Provides
    protected fun createInetAddressFactory(): InetAddressFactory {
        return InetAddressFactory()
    }

    @Provides
    protected fun createOutgoingConnector(): OutgoingConnector {
        return TcpOutgoingConnector()
    }

    @Provides
    protected fun createIncomingConnector(executorFactory: ExecutorFactory, inetAddressFactory: InetAddressFactory): IncomingConnector {
        return TcpIncomingConnector(
            executorFactory,
            inetAddressFactory,
            idGenerator,
            10
        )
    }

    @Provides
    protected fun createMessagingClient(outgoingConnector: OutgoingConnector, executorFactory: ExecutorFactory): MessagingClient {
        return MessageHubBackedClient(
            outgoingConnector,
            executorFactory
        )
    }

    @Provides
    protected fun createMessagingServer(incomingConnector: IncomingConnector, executorFactory: ExecutorFactory): MessagingServer {
        return MessageHubBackedServer(
            incomingConnector,
            executorFactory
        )
    }
}
