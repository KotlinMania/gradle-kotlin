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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.writer

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Sets
import com.google.common.io.Files
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.gradle.api.Action
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.DependencyVerifyingModuleComponentRepository
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.ArtifactVerificationOperation
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.DefaultKeyServers
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.DependencyVerificationOverride
import org.gradle.api.internal.artifacts.verification.exceptions.DependencyVerificationException
import org.gradle.api.internal.artifacts.verification.model.ChecksumKind
import org.gradle.api.internal.artifacts.verification.model.ComponentVerificationMetadata
import org.gradle.api.internal.artifacts.verification.model.IgnoredKey
import org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationsXmlReader.readFromXml
import org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationsXmlWriter.Companion.serialize
import org.gradle.api.internal.artifacts.verification.signatures.BuildTreeDefinedKeys
import org.gradle.api.internal.artifacts.verification.signatures.SignatureVerificationResultBuilder
import org.gradle.api.internal.artifacts.verification.signatures.SignatureVerificationService
import org.gradle.api.internal.artifacts.verification.signatures.SignatureVerificationServiceFactory
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerificationConfiguration
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifier
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifierBuilder
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging.getLogger
import org.gradle.internal.Factory
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.component.external.model.ExternalModuleComponentGraphResolveState
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.deprecation.DeprecatableConfiguration
import org.gradle.internal.hash.ChecksumService
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationQueue
import org.gradle.internal.operations.RunnableBuildOperation
import org.gradle.security.internal.Fingerprint.Companion.fromString
import org.gradle.security.internal.PGPUtils.getSize
import org.gradle.security.internal.PGPUtils.getUserIDs
import org.gradle.security.internal.PublicKeyResultBuilder
import org.gradle.security.internal.PublicKeyService
import org.gradle.security.internal.SecuritySupport.loadKeyRingFile
import org.gradle.security.internal.SecuritySupport.toLongIdHexString
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.lang.String
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.util.SortedMap
import java.util.TreeMap
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import java.util.stream.Stream
import kotlin.Any
import kotlin.Boolean
import kotlin.Exception
import kotlin.IllegalArgumentException
import kotlin.Long
import kotlin.Throws
import kotlin.code
import kotlin.collections.HashSet
import kotlin.collections.MutableCollection
import kotlin.collections.MutableList
import kotlin.collections.MutableSet
import kotlin.collections.contains
import kotlin.collections.map
import kotlin.collections.mutableListOf
import kotlin.collections.mutableSetOf
import kotlin.map
import kotlin.sequences.map
import kotlin.synchronized
import kotlin.text.StringBuilder
import kotlin.text.map
import kotlin.text.toByteArray
import kotlin.text.uppercase

class WriteDependencyVerificationFile(
    private val verificationFile: File,
    private val buildOperationExecutor: BuildOperationExecutor,
    private val checksums: MutableList<String>,
    private val checksumService: ChecksumService,
    private val signatureVerificationServiceFactory: SignatureVerificationServiceFactory,
    private val isDryRun: Boolean,
    private val isExportKeyring: Boolean
) : DependencyVerificationOverride, ArtifactVerificationOperation {
    private val verificationsBuilder = DependencyVerifierBuilder()
    private val entriesToBeWritten: MutableSet<VerificationEntry> = Sets.newLinkedHashSetWithExpectedSize<VerificationEntry>(512)
    private val generatePgpInfo: Boolean

    private var hasMissingSignatures = false
    private var hasMissingKeys = false
    private var hasFailedVerification = false

    init {
        this.generatePgpInfo = checksums.contains(PGP)
        maybeCleanupDryRunFiles()
    }

    private val isWriteVerificationFile: Boolean
        get() = !checksums.isEmpty()

    private fun validateChecksums() {
        if (this.isWriteVerificationFile) {
            assertSupportedChecksums()
            warnAboutInsecureChecksums()
        }
    }

    private fun assertSupportedChecksums() {
        for (checksum in checksums) {
            if (!SUPPORTED_CHECKSUMS.contains(checksum)) {
                // we cannot throw an exception at this stage because this happens too early
                // in the build and the user feedback isn't great ("cannot create service blah!")
                LOGGER.warn("Invalid checksum type: '" + checksum + "'. You must choose one or more in " + SUPPORTED_CHECKSUMS)
            }
        }
        assertPgpHasChecksumFallback(checksums)
    }

    private fun assertPgpHasChecksumFallback(kinds: MutableList<String>) {
        if (kinds.size == 1 && PGP == kinds.get(0)) {
            throw DependencyVerificationException("Generating a file with signature verification requires at least one checksum type (sha256 or sha512) as fallback.")
        }
    }

    private fun warnAboutInsecureChecksums() {
        if (checksums.stream().noneMatch { o: String? -> SECURE_CHECKSUMS.contains(o) }) {
            LOGGER.warn(
                "You chose to generate " + String.join(
                    " and ",
                    checksums
                ) + " checksums but they are all considered insecure. You should consider adding at least one of " + String.join(" or ", SECURE_CHECKSUMS) + "."
            )
        }
    }

    override fun overrideDependencyVerification(original: ModuleComponentRepository<ExternalModuleComponentGraphResolveState?>): ModuleComponentRepository<ExternalModuleComponentGraphResolveState?> {
        return DependencyVerifyingModuleComponentRepository(original, this, generatePgpInfo)
    }

    private fun maybeCleanupDryRunFiles() {
        if (isDryRun) {
            var removed = false
            removed = removed or (mayBeDryRunFile(verificationFile).delete() || removed)
            if (isExportKeyring) {
                val existingKeyring = BuildTreeDefinedKeys(verificationFile.getParentFile(), verificationsBuilder.keyringFormat)
                removed = removed or mayBeDryRunFile(existingKeyring.asciiKeyringsFile).delete()
                removed = removed or mayBeDryRunFile(existingKeyring.binaryKeyringsFile).delete()
            }
            if (removed) {
                LOGGER.lifecycle("Removed dry-run verification files from the previous run")
            }
        }
    }

    override fun buildFinished(gradle: GradleInternal) {
        ensureOutputDirCreated()
        maybeReadExistingFile()
        // when we generate the verification file, we intentionally ignore if the "use key servers" flag is false
        // because otherwise it forces the user to remove the option in the XML file, generate, then switch it back.
        val offline = gradle.getStartParameter().isOffline()
        val existingKeyring = BuildTreeDefinedKeys(verificationFile.getParentFile(), verificationsBuilder.keyringFormat)
        val signatureVerificationService = signatureVerificationServiceFactory.create(
            existingKeyring,
            DefaultKeyServers.getOrDefaults(verificationsBuilder.keyServers),
            !offline
        )
        if (!verificationsBuilder.isUseKeyServers && !offline) {
            LOGGER.lifecycle("Will use key servers to download missing keys. If you really want to ignore key servers when generating the verification file, you can use the --offline flag in addition")
        }
        try {
            validateChecksums()
            resolveAllConfigurationsConcurrently(gradle)
            computeChecksumsConcurrently(signatureVerificationService!!)
            writeEntriesSerially()
            serializeResult(signatureVerificationService, existingKeyring)
        } catch (e: IOException) {
            throw throwAsUncheckedException(e)
        } finally {
            signatureVerificationService!!.stop()
        }
    }

    fun ensureOutputDirCreated(): Boolean {
        return verificationFile.getParentFile().mkdirs()
    }

    @Throws(IOException::class)
    private fun serializeResult(signatureVerificationService: SignatureVerificationService, existingKeyring: BuildTreeDefinedKeys) {
        val out = mayBeDryRunFile(verificationFile)
        if (generatePgpInfo) {
            verificationsBuilder.isVerifySignatures = true
        }
        val verifier = verificationsBuilder.build()
        if (this.isWriteVerificationFile) {
            serialize(
                verifier,
                FileOutputStream(out)
            )
        }
        if (isExportKeyring) {
            exportKeys(signatureVerificationService, verifier, existingKeyring)
        }
    }

    private fun mayBeDryRunFile(originalFile: File): File {
        if (!isDryRun) {
            return originalFile
        } else {
            return File(originalFile.getParent(), Files.getNameWithoutExtension(originalFile.getName()) + ".dryrun." + Files.getFileExtension(originalFile.getName()))
        }
    }

    @Throws(IOException::class)
    private fun exportKeys(signatureVerificationService: SignatureVerificationService, verifier: DependencyVerifier, existingKeyring: BuildTreeDefinedKeys) {
        val keysToExport: MutableSet<kotlin.String> = HashSet<kotlin.String>()
        verifier.configuration
            .trustedKeys
            .stream()
            .map<kotlin.String>(DependencyVerificationConfiguration.TrustedKey::keyId)
            .forEach { e: kotlin.String? -> keysToExport.add(e!!) }
        verifier.configuration
            .ignoredKeys
            .stream()
            .map<Any>(IgnoredKey::getKeyId)
            .forEach { e: Any? -> keysToExport.add(e) }
        verifier.getVerificationMetadata()
            .stream()
            .flatMap<Any> { md: ComponentVerificationMetadata? -> md!!.artifactVerifications!!.stream() }
            .flatMap<Any> { avm: Any? -> Stream.concat<T>(avm.trustedPgpKeys.stream(), avm.ignoredPgpKeys.stream().map(IgnoredKey::getKeyId)) }
            .forEach { e: Any? -> keysToExport.add(e) }

        exportKeyRingCollection(
            signatureVerificationService.publicKeyService,
            existingKeyring,
            keysToExport,
            verifier.configuration.keyringFormat
        )
    }

    private fun maybeReadExistingFile() {
        if (isDryRun) {
            val previous = mayBeDryRunFile(verificationFile)
            if (previous.exists()) {
                LOGGER.info("Found dependency verification dryrun metadata file, updating")
                try {
                    readFromXml(FileInputStream(previous), verificationsBuilder)
                } catch (e: FileNotFoundException) {
                    throw throwAsUncheckedException(e)
                }
                return
            }
        }
        if (verificationFile.exists()) {
            LOGGER.info("Found dependency verification metadata file, updating")
            try {
                readFromXml(FileInputStream(verificationFile), verificationsBuilder)
            } catch (e: FileNotFoundException) {
                throw throwAsUncheckedException(e)
            }
        }
    }

    private fun writeEntriesSerially() {
        val previousEntry = AtomicReference<PgpEntry>()
        entriesToBeWritten.stream()
            .sorted()
            .filter { entry: VerificationEntry? -> this.shouldWriteEntry(entry!!) }
            .forEachOrdered { e: VerificationEntry? -> registerEntryToBuilder(e!!, previousEntry) }
        printWarnings()
    }

    private fun printWarnings() {
        if (hasMissingKeys || hasFailedVerification) {
            val sb = StringBuilder("A verification file was generated but some problems were discovered:\n")
            if (hasMissingSignatures) {
                sb.append("   - some artifacts aren't signed or the signature couldn't be retrieved.")
                sb.append("\n")
            }
            if (hasMissingKeys) {
                sb.append("   - some keys couldn't be downloaded. They were automatically added as ignored keys but you should review if this is acceptable. Look for entries with the following comment: ")
                sb.append(KEY_NOT_DOWNLOADED)
                sb.append("\n")
            }
            if (hasFailedVerification) {
                sb.append("   - some signature verification failed. Checksums were generated for those artifacts but you MUST check if there's an actual problem. Look for entries with the following comment: ")
                sb.append(PGP_VERIFICATION_FAILED)
                sb.append("\n")
            }
            LOGGER.warn(sb.toString())
        }
    }

    private fun registerEntryToBuilder(entry: VerificationEntry, previousEntry: AtomicReference<PgpEntry>) {
        // checksums are written _after_ PGP, so if the previous entry was PGP and
        // that it matches the artifact id we don't always need to write the checksum
        var pgpEntry = previousEntry.get()
        if (pgpEntry != null && pgpEntry.id != entry.id) {
            // previous entry was on unrelated module
            pgpEntry = null
            previousEntry.set(null)
        }
        if (entry is ChecksumEntry) {
            val checksum = entry
            if (pgpEntry == null || (entry.id == pgpEntry.id && pgpEntry.isRequiringChecksums())) {
                val origin = "Generated by Gradle"
                var reason: kotlin.String? = null
                if (pgpEntry != null) {
                    if (pgpEntry.isFailed()) {
                        hasFailedVerification = true
                        reason = "PGP signature verification failed!"
                    } else {
                        if (pgpEntry.hasSignatureFile()) {
                            hasMissingKeys = true
                            reason = "A key couldn't be downloaded"
                        } else {
                            hasMissingSignatures = true
                            reason = "Artifact is not signed"
                        }
                    }
                }
                verificationsBuilder.addChecksum(entry.id, checksum.getChecksumKind(), checksum.getChecksum(), origin, reason)
            }
        } else {
            val pgp = entry as PgpEntry
            previousEntry.set(pgp)
            val failedKeys: MutableSet<kotlin.String> = Sets.newTreeSet<kotlin.String>(pgp.getFailed())
            for (failedKey in failedKeys) {
                verificationsBuilder.addIgnoredKey(pgp.id, IgnoredKey(failedKey, PGP_VERIFICATION_FAILED))
            }
            if (pgp.hasArtifactLevelKeys()) {
                for (key in pgp.getArtifactLevelKeys()) {
                    if (!failedKeys.contains(key)) {
                        verificationsBuilder.addTrustedKey(pgp.id, key)
                    }
                }
            }
        }
    }

    private fun shouldWriteEntry(entry: VerificationEntry): Boolean {
        if (entry is ChecksumEntry) {
            return entry.getChecksum() != null && !isTrustedArtifact(entry.id)
        }
        return !isTrustedArtifact(entry.id)
    }

    private fun resolveAllConfigurationsConcurrently(gradle: GradleInternal) {
        buildOperationExecutor.runAllWithAccessToProjectState<RunnableBuildOperation>(Action { queue: BuildOperationQueue<RunnableBuildOperation>? ->
            val allProjects = gradle.getOwner().getProjects().getAllProjects()
            for (projectState in allProjects) {
                queue!!.add(object : RunnableBuildOperation {
                    override fun run(context: BuildOperationContext) {
                        resolveAllConfigurationsAndForceDownload(projectState)
                    }

                    override fun description(): BuildOperationDescriptor.Builder {
                        val displayName = "Resolving configurations of " + projectState.getDisplayName()
                        return@runAllWithAccessToProjectState BuildOperationDescriptor.displayName(displayName)
                            .progressDisplayName(displayName)
                    }
                })
            }
        })
    }

    private fun computeChecksumsConcurrently(signatureVerificationService: SignatureVerificationService) {
        val collectedIgnoredKeys = if (generatePgpInfo) Sets.newConcurrentHashSet<kotlin.String>() else null
        buildOperationExecutor.runAll<RunnableBuildOperation>(Action { queue: BuildOperationQueue<RunnableBuildOperation>? ->
            for (entry in entriesToBeWritten) {
                if (shouldSkipVerification(entry.getArtifactKind())) {
                    continue
                }
                if (!entry.getFile().exists()) {
                    LOGGER.warn("Cannot compute checksum for " + entry.getFile() + " because it doesn't exist. It may indicate a corrupt or tampered cache.")
                    continue
                }
                if (entry is ChecksumEntry) {
                    queueChecksumVerification(queue!!, entry)
                } else {
                    queueSignatureVerification(queue!!, signatureVerificationService, entry as PgpEntry, collectedIgnoredKeys!!)
                }
            }
        })
        if (generatePgpInfo) {
            postProcessPgpResults(collectedIgnoredKeys!!)
        }
    }

    private fun postProcessPgpResults(collectedIgnoredKeys: MutableSet<kotlin.String>) {
        for (ignoredKey in collectedIgnoredKeys) {
            verificationsBuilder.addIgnoredKey(IgnoredKey(ignoredKey, KEY_NOT_DOWNLOADED))
        }
        val grouper = PgpKeyGrouper(verificationsBuilder, entriesToBeWritten)
        grouper.performPgpKeyGrouping()
    }

    private fun queueSignatureVerification(
        queue: BuildOperationQueue<RunnableBuildOperation>,
        signatureVerificationService: SignatureVerificationService,
        entry: PgpEntry,
        ignoredKeys: MutableSet<kotlin.String>
    ) {
        queue.add(object : RunnableBuildOperation {
            override fun run(context: BuildOperationContext) {
                val signature = entry.getSignatureFile().create()
                if (signature != null) {
                    val builder: SignatureVerificationResultBuilder = WriterSignatureVerificationResult(ignoredKeys, entry)
                    signatureVerificationService.verify(entry.file, signature, mutableSetOf<kotlin.String?>(), mutableSetOf<kotlin.String?>(), builder)
                }
            }

            override fun description(): BuildOperationDescriptor.Builder {
                return BuildOperationDescriptor.displayName("Verifying dependency signature")
                    .progressDisplayName("Verifying signature of " + entry.id)
            }
        })
    }

    private fun queueChecksumVerification(queue: BuildOperationQueue<RunnableBuildOperation>, entry: ChecksumEntry) {
        queue.add(object : RunnableBuildOperation {
            override fun run(context: BuildOperationContext) {
                entry.setChecksum(createHash(entry.getFile(), entry.getChecksumKind()))
            }

            override fun description(): BuildOperationDescriptor.Builder {
                return BuildOperationDescriptor.displayName("Computing checksums")
                    .progressDisplayName("Computing checksum of " + entry.id)
            }
        })
    }

    override fun onArtifact(
        kind: ArtifactVerificationOperation.ArtifactKind,
        id: ModuleComponentArtifactIdentifier,
        mainFile: File,
        signatureFile: Factory<File?>,
        repositoryName: kotlin.String,
        repositoryId: kotlin.String
    ) {
        for (checksum in checksums) {
            if (PGP == checksum) {
                addPgp(id, kind, mainFile, signatureFile)
            } else {
                addChecksum(id, kind, mainFile, ChecksumKind.valueOf(checksum))
            }
        }
    }

    private fun addPgp(id: ModuleComponentArtifactIdentifier, kind: ArtifactVerificationOperation.ArtifactKind, mainFile: File, signatureFile: Factory<File?>) {
        val entry = PgpEntry(id, kind, mainFile, signatureFile)
        synchronized(entriesToBeWritten) {
            entriesToBeWritten.add(entry)
        }
    }

    private fun shouldSkipVerification(kind: ArtifactVerificationOperation.ArtifactKind): Boolean {
        return kind == ArtifactVerificationOperation.ArtifactKind.METADATA && !verificationsBuilder.isVerifyMetadata
    }

    private fun addChecksum(id: ModuleComponentArtifactIdentifier, artifactKind: ArtifactVerificationOperation.ArtifactKind, file: File, kind: ChecksumKind) {
        val e = ChecksumEntry(id, artifactKind, file, kind)
        synchronized(entriesToBeWritten) {
            entriesToBeWritten.add(e)
        }
    }

    private fun isTrustedArtifact(id: ModuleComponentArtifactIdentifier): Boolean {
        return verificationsBuilder.trustedArtifacts.stream().anyMatch { artifact: DependencyVerificationConfiguration.TrustedArtifact? -> artifact!!.matches(id) }
    }

    private fun createHash(file: File, kind: ChecksumKind): kotlin.String {
        try {
            return checksumService.hash(file, kind.algorithm!!).toString()
        } catch (e: Exception) {
            LOGGER.debug("Error while snapshotting " + file, e)
            return null
        }
    }

    @Throws(IOException::class)
    private fun exportKeyRingCollection(
        publicKeyService: PublicKeyService,
        existingKeyring: BuildTreeDefinedKeys,
        publicKeys: MutableSet<kotlin.String>,
        keyringFormat: DependencyVerificationConfiguration.KeyringFormat?
    ) {
        val existingRings = loadExistingKeyRing(existingKeyring)
        val builder = PGPPublicKeyRingListBuilder()
        for (publicKey in publicKeys) {
            if (publicKey.length <= 16) {
                publicKeyService.findByLongId(BigInteger(publicKey, 16).toLong(), builder)
            } else {
                publicKeyService.findByFingerprint(fromString(publicKey).bytes, builder)
            }
        }

        val keysSeenInVerifier = builder.build()
            .stream()
            .filter { keyring: PGPPublicKeyRing -> getSize(keyring) != 0 }

        val allKeyRings: MutableCollection<PGPPublicKeyRing> = uniqueKeyRings(Stream.concat<PGPPublicKeyRing>(keysSeenInVerifier, existingRings.stream()))

        val asciiArmoredFile = mayBeDryRunFile(existingKeyring.asciiKeyringsFile)
        val keyringFile = mayBeDryRunFile(existingKeyring.binaryKeyringsFile)

        if (keyringFormat == null) {
            writeAsciiArmoredKeyRingFile(asciiArmoredFile, allKeyRings)
            writeBinaryKeyringFile(keyringFile, allKeyRings)
            LOGGER.lifecycle("Exported {} keys to {} and {}", allKeyRings.size, keyringFile, asciiArmoredFile)
        } else if (keyringFormat == DependencyVerificationConfiguration.KeyringFormat.ARMORED) {
            writeAsciiArmoredKeyRingFile(asciiArmoredFile, allKeyRings)
            LOGGER.lifecycle("Exported {} keys to {}", allKeyRings.size, asciiArmoredFile)
        } else if (keyringFormat == DependencyVerificationConfiguration.KeyringFormat.BINARY) {
            writeBinaryKeyringFile(keyringFile, allKeyRings)
            LOGGER.lifecycle("Exported {} keys to {}", allKeyRings.size, keyringFile)
        } else {
            throw IllegalArgumentException("Unknown keyring format " + keyringFormat)
        }
    }

    @Throws(IOException::class)
    private fun writeAsciiArmoredKeyRingFile(ascii: File, allKeyRings: MutableCollection<PGPPublicKeyRing>) {
        if (ascii.exists()) {
            ascii.delete()
        }
        var hasKey = false
        for (keyRing in allKeyRings) {
            // First let's write some human readable info about the keyring being serialized
            FileOutputStream(ascii, true).use { out ->
                if (hasKey) {
                    out.write('\n'.code)
                }
                val pks = keyRing.getPublicKeys()
                while (pks.hasNext()) {
                    var hasUid = false
                    val pk = pks.next()
                    val keyType = if (pk.isMasterKey()) "pub" else "sub"
                    out.write((keyType + "    " + toLongIdHexString(pk.getKeyID()).uppercase() + "\n").toByteArray(StandardCharsets.US_ASCII))
                    val userIDs: MutableList<kotlin.String> = getUserIDs(pk)
                    for (uid in userIDs) {
                        hasUid = true
                        // We can store UTF-8 text despite being an ASCII-armored file,
                        // as ArmoredInputStream only cares about finding armor headers and does not otherwise care about encoding
                        out.write(("uid    " + uid + "\n").toByteArray(StandardCharsets.UTF_8))
                    }
                    if (hasUid) {
                        out.write('\n'.code)
                    }
                }
            }
            FileOutputStream(ascii, true).use { fos ->
                ArmoredOutputStream.builder().build(fos).use { out ->
                    keyRing.encode(out, true)
                }
            }
            hasKey = true
        }
    }

    @Throws(IOException::class)
    private fun writeBinaryKeyringFile(keyringFile: File, allKeyRings: MutableCollection<PGPPublicKeyRing>) {
        FileOutputStream(keyringFile).use { out ->
            for (keyRing in allKeyRings) {
                keyRing.encode(out, true)
            }
        }
    }

    private class PGPPublicKeyRingListBuilder : PublicKeyResultBuilder {
        private val builder = ImmutableList.builder<PGPPublicKeyRing>()

        override fun keyRing(keyring: PGPPublicKeyRing) {
            builder.add(keyring)
        }

        override fun publicKey(publicKey: PGPPublicKey) {
        }

        fun build(): MutableList<PGPPublicKeyRing> {
            return builder.build()
        }
    }

    @Throws(IOException::class)
    private fun loadExistingKeyRing(keyrings: BuildTreeDefinedKeys): MutableList<PGPPublicKeyRing> {
        val effectiveFile = mayBeDryRunFile(keyrings.effectiveKeyringsFile!!)
        if (!effectiveFile.exists()) {
            return mutableListOf<PGPPublicKeyRing>()
        }
        val existingRings: MutableList<PGPPublicKeyRing> = loadKeyRingFile(effectiveFile)
        LOGGER.info("Existing keyring file contains {} keyrings", existingRings.size)
        return existingRings
    }

    companion object {
        private val LOGGER: Logger = getLogger(WriteDependencyVerificationFile::class.java)!!
        private val MODULE_COMPONENT_FILES = Action { conf: ArtifactView.ViewConfiguration ->
            conf.componentFilter(org.gradle.api.specs.Spec { id: ComponentIdentifier? -> id is ModuleComponentIdentifier })
            conf.setLenient(true)
        }
        private const val PGP = "pgp"
        private const val MD5 = "md5"
        private const val SHA1 = "sha1"
        private const val SHA256 = "sha256"
        private const val SHA512 = "sha512"
        private val SUPPORTED_CHECKSUMS: MutableSet<kotlin.String> = ImmutableSet.of<kotlin.String>(MD5, SHA1, SHA256, SHA512, PGP)
        private val SECURE_CHECKSUMS: MutableSet<kotlin.String> = ImmutableSet.of<kotlin.String>(SHA256, SHA512, PGP)
        private const val PGP_VERIFICATION_FAILED = "PGP verification failed"
        private const val KEY_NOT_DOWNLOADED = "Key couldn't be downloaded from any key server"

        private fun resolveAllConfigurationsAndForceDownload(projectState: ProjectState) {
            projectState.applyToMutableState(Consumer { p: ProjectInternal? ->
                p!!.getConfigurations().all(Action { cnf: Configuration? ->
                    if ((cnf as DeprecatableConfiguration).canSafelyBeResolved()) {
                        try {
                            resolveAndDownloadExternalFiles(cnf)
                        } catch (e: Exception) {
                            LOGGER.debug("Cannot resolve configuration {}: {}", cnf.getName(), e.message)
                        }
                    }
                })
            }
            )
        }

        private fun resolveAndDownloadExternalFiles(cnf: Configuration) {
            cnf.getIncoming().artifactView(MODULE_COMPONENT_FILES).getFiles().getFiles()
        }

        private fun uniqueKeyRings(keyRings: Stream<PGPPublicKeyRing>): MutableCollection<PGPPublicKeyRing> {
            val seenKeyIds: SortedMap<Long, PGPPublicKeyRing> = TreeMap<Long, PGPPublicKeyRing>()
            keyRings.forEach { keyRing: PGPPublicKeyRing? ->
                val keyId = keyRing!!.getPublicKey().getKeyID()
                val current = seenKeyIds.get(keyId)
                if (current == null || getSize(current) < getSize(keyRing)) {
                    seenKeyIds.put(keyId, keyRing)
                }
            }
            return seenKeyIds.values
        }
    }
}
