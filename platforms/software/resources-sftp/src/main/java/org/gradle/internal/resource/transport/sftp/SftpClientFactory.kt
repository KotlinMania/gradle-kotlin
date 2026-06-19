/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.internal.resource.transport.sftp

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ListMultimap
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Logger
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import org.gradle.api.credentials.PasswordCredentials
import org.gradle.api.resources.ResourceException
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.slf4j.LoggerFactory
import java.net.URI
import javax.annotation.concurrent.ThreadSafe

@ThreadSafe
@ServiceScope(Scope.Global::class)
class SftpClientFactory : Stoppable {
    private val sftpClientCreator = SftpClientCreator()
    private val lock = Any()
    private val allClients: MutableList<LockableSftpClient> = ArrayList()
    private val idleClients: ListMultimap<SftpHost, LockableSftpClient> = ArrayListMultimap.create()

    fun createSftpClient(uri: URI, credentials: PasswordCredentials): LockableSftpClient {
        synchronized(lock) {
            val sftpHost = SftpHost(uri, credentials)
            return acquireClient(sftpHost)
        }
    }

    private fun acquireClient(sftpHost: SftpHost): LockableSftpClient {
        return if (idleClients.containsKey(sftpHost)) reuseExistingOrCreateNewClient(sftpHost) else createNewClient(sftpHost)
    }

    private fun reuseExistingOrCreateNewClient(sftpHost: SftpHost): LockableSftpClient {
        val clientsByHost = idleClients.get(sftpHost)

        var client: LockableSftpClient? = null
        if (clientsByHost.isEmpty()) {
            LOGGER.debug("No existing sftp clients.  Creating a new one.")
            client = createNewClient(sftpHost)
        } else {
            client = clientsByHost.removeAt(0)
            if (!client!!.isConnected()) {
                LOGGER.info("Tried to reuse an existing sftp client, but unexpectedly found it disconnected.  Discarding and trying again.")
                discard(client)
                client = reuseExistingOrCreateNewClient(sftpHost)
            } else {
                LOGGER.debug("Reusing an existing sftp client.")
            }
        }

        return client
    }

    private fun createNewClient(sftpHost: SftpHost): LockableSftpClient {
        val client = sftpClientCreator.createNewClient(sftpHost)
        allClients.add(client)
        return client
    }

    private fun discard(client: LockableSftpClient) {
        try {
            client.stop()
        } finally {
            allClients.remove(client)
        }
    }

    private class SftpClientCreator {
        private var jsch: JSch? = null

        fun createNewClient(sftpHost: SftpHost): LockableSftpClient {
            try {
                val session = createJsch().getSession(sftpHost.username, sftpHost.hostname, sftpHost.port)
                session.setPassword(sftpHost.password)
                session.connect()
                val channel = session.openChannel("sftp")
                channel.connect()
                return DefaultLockableSftpClient(sftpHost, channel as ChannelSftp, session)
            } catch (e: JSchException) {
                val serverUri = URI.create(String.format("sftp://%s:%d", sftpHost.hostname, sftpHost.port))
                if (e.message == "Auth fail") {
                    throw ResourceException(serverUri, String.format("Password authentication not supported or invalid credentials for SFTP server at %s", serverUri), e)
                }
                throw ResourceException(serverUri, String.format("Could not connect to SFTP server at %s", serverUri), e)
            }
        }

        fun createJsch(): JSch {
            if (jsch == null) {
                JSch.setConfig("PreferredAuthentications", "password")
                JSch.setConfig("MaxAuthTries", "1")
                jsch = JSch()
                if (LOGGER.isDebugEnabled()) {
                    JSch.setLogger(object : Logger {
                        override fun isEnabled(level: Int): Boolean {
                            return true
                        }

                        override fun log(level: Int, message: String?) {
                            LOGGER.debug(message)
                        }
                    })
                }
                jsch!!.setHostKeyRepository(object : HostKeyRepository {
                    override fun check(host: String?, key: ByteArray?): Int {
                        return HostKeyRepository.OK
                    }

                    override fun add(hostkey: HostKey?, ui: UserInfo?) {
                    }

                    override fun remove(host: String?, type: String?) {
                    }

                    override fun remove(host: String?, type: String?, key: ByteArray?) {
                    }

                    override fun getKnownHostsRepositoryID(): String {
                        return "allow-everything"
                    }

                    override fun getHostKey(): Array<HostKey?>? {
                        throw UnsupportedOperationException()
                    }

                    override fun getHostKey(host: String?, type: String?): Array<HostKey?> {
                        return arrayOfNulls<HostKey>(0)
                    }
                })
            }
            return jsch!!
        }
    }

    fun releaseSftpClient(sftpClient: LockableSftpClient) {
        synchronized(lock) {
            idleClients.put(sftpClient.getHost(), sftpClient)
        }
    }

    override fun stop() {
        synchronized(lock) {
            try {
                CompositeStoppable.stoppable(allClients).stop()
            } finally {
                allClients.clear()
                idleClients.clear()
            }
        }
    }

    private class DefaultLockableSftpClient(private val host: SftpHost, private val channelSftp: ChannelSftp, private val session: Session) : LockableSftpClient {
        override fun stop() {
            channelSftp.disconnect()
            session.disconnect()
        }

        override fun getHost(): SftpHost {
            return host
        }

        override fun getSftpClient(): ChannelSftp {
            return channelSftp
        }

        override fun isConnected(): Boolean {
            return channelSftp.isConnected
        }
    }

    companion object {
        private val LOGGER: org.slf4j.Logger = LoggerFactory.getLogger(SftpClientFactory::class.java)
    }
}
