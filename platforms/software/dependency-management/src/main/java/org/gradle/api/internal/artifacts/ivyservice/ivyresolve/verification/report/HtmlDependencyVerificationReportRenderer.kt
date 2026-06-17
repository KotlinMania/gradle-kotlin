/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.report

import org.apache.commons.lang3.StringEscapeUtils
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.RepositoryAwareVerificationFailure
import org.gradle.api.internal.artifacts.verification.verifier.ChecksumVerificationFailure
import org.gradle.api.internal.artifacts.verification.verifier.DeletedArtifact
import org.gradle.api.internal.artifacts.verification.verifier.InvalidSignatureFile
import org.gradle.api.internal.artifacts.verification.verifier.MissingChecksums
import org.gradle.api.internal.artifacts.verification.verifier.MissingSignature
import org.gradle.api.internal.artifacts.verification.verifier.OnlyIgnoredKeys
import org.gradle.api.internal.artifacts.verification.verifier.SignatureVerificationFailure
import org.gradle.api.internal.artifacts.verification.verifier.VerificationFailure
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.lang.String
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.TreeMap
import java.util.function.Consumer
import kotlin.Boolean
import kotlin.Suppress

/**
 * Generates an HTML report for verification. This report, unlike the text report,
 * is cumulative, meaning that it keeps state and will be incrementally feeded with
 * new contents.
 */
internal class HtmlDependencyVerificationReportRenderer(
    private val documentationRegistry: DocumentationRegistry,
    private val verificationFile: File,
    private val writeFlags: MutableList<String>,
    private val htmlReportOutputDirectory: File,
    private val useKeyServers: Boolean
) : DependencyVerificationReportRenderer {
    private val sections: MutableMap<String, Section> = TreeMap<String, Section>()
    private var currentSection: Section? = null
    private val contents = StringBuilder()
    private var hasMissingKeys = false

    override fun startNewSection(title: String) {
        currentSection = sections.get(title)
        if (currentSection == null) {
            currentSection = Section(title)
            this.sections.put(title, currentSection!!)
        }
    }

    override fun startArtifactErrors(action: Runnable) {
        action.run()
    }

    override fun startNewArtifact(key: ModuleComponentArtifactIdentifier, action: Runnable) {
        currentSection!!.newArtifact(ArtifactErrors(key))
        action.run()
    }

    override fun reportFailure(failure: RepositoryAwareVerificationFailure) {
        currentSection.currentArtifact!!.addFailure(failure)
    }

    override fun reportAsMultipleErrors(action: Runnable) {
        action.run()
    }

    override fun finish(highLevelErrors: VerificationHighLevelErrors) {
    }

    fun renderNavBar() {
        contents.append(
            "<nav class=\"uk-navbar-container\" uk-navbar>\n" +
                    "    <div class=\"uk-navbar-left\">\n" +
                    "        <a href=\"\" class=\"uk-navbar-item uk-logo\"><img src=\"img/gradle-logo.png\" width=\"120\"></a>\n" +
                    "        <ul class=\"uk-navbar-nav\">\n" +
                    "<li class=\"uk-active\"><a href=\"#\">Dependency verification report</a></li>\n" +
                    "        </ul>\n" +
                    "    </div>\n" +
                    "</nav>\n" +
                    "\n"
        )
    }

    fun renderSections() {
        contents.append("<div class=\"uk-container uk-container-expand\">\n")
            .append("        <ul uk-accordion>\n")
        var first = true
        for (section in sections.values) {
            if (first) {
                contents.append("            <li class=\"uk-open\">\n")
            } else {
                contents.append("            <li>\n")
            }
            prerenderSection(section)
            renderSection(section)
            contents.append("            </li>\n")
            first = false
        }
        contents.append("         </ul>\n")
            .append("        </div>\n")
    }

    fun writeReport(): File {
        generateContent()
        ensureReportDirectoryCreated()
        copyReportResources()
        return doWriteReport()
    }

    private fun ensureReportDirectoryCreated() {
        htmlReportOutputDirectory.mkdirs()
    }

    private fun copyReportResources() {
        copyReportResource(htmlReportOutputDirectory, "css", "uikit.min.css")
        copyReportResource(htmlReportOutputDirectory, "js", "uikit.min.js")
        copyReportResource(htmlReportOutputDirectory, "js", "uikit-icons.min.js")
        copyReportResource(htmlReportOutputDirectory, "img", "gradle-logo.png")
    }

    private fun doWriteReport(): File {
        val reportFile = File(htmlReportOutputDirectory, "dependency-verification-report.html")
        try {
            OutputStreamWriter(FileOutputStream(reportFile, false), StandardCharsets.UTF_8).use { prn ->
                prn.write(contents.toString())
            }
        } catch (e: IOException) {
            throw throwAsUncheckedException(e)
        }
        return reportFile
    }

    private fun generateContent() {
        contents.setLength(0)
        contents.append(
            "<!DOCTYPE html>\n" +
                    "<html>\n" +
                    "    <head>\n" +
                    "        <title>Dependency verification report</title>\n" +
                    "        <meta charset=\"utf-8\">\n" +
                    "        <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n" +
                    "        <link rel=\"stylesheet\" href=\"css/uikit.min.css\" />\n" +
                    "        <script src=\"js/uikit.min.js\"></script>\n" +
                    "        <script src=\"js/uikit-icons.min.js\"></script>\n" +
                    "    </head>\n" +
                    "    <body>\n"
        )
        renderNavBar()
        renderSections()
        registerModals()
        registerStickyTip()
        contents.append(
            "    </body>\n" +
                    "</html>\n" +
                    "\n"
        )
    }

    private fun registerStickyTip() {
        contents.append("    <div class=\"uk-container uk-padding\">\n")
            .append("        <div class=\"uk-card uk-card-default uk-card-body\" style=\"z-index: 980;\" uk-sticky=\"bottom: true\">\n")
            .append("            <h2 class=\"uk-modal-title\">Troubleshooting</h2>\n")
            .append("            <p>Please review the errors reported above carefully.")
            .append("            Click on the icons near to the error descriptions for information about how to fix a particular problem.")
            .append("            It is recommended that you edit the ").append(verificationFileLink()).append(" manually. ")
            .append("            However, if you are confident that those are false positives, Gradle can help you by generating the missing verification metadata.")

        if (!useKeyServers && hasMissingKeys) {
            contents.append("            In this case, you can ask Gradle to export all keys it used for verification of this build to the keyring with the following command-line:</p>")
                .append("            <pre>./gradlew --write-verification-metadata ").append(verificationOptions()).append(" --export-keys help</pre>")
        } else {
            contents.append("            In this case, you can run with the following command-line:</p>")
                .append("            <pre>./gradlew --write-verification-metadata ").append(verificationOptions()).append(" help</pre>")
        }

        contents.append("            <p>In any case you <b>must review the result</b> of this operation.")
            .append("            <p>Please refer to the <a href=\"").append(documentationRegistry.getDocumentationFor("dependency_verification"))
            .append("\" target=\"_blank\">documentation</a> for more information.</p>\n")
            .append("        </div>\n")
            .append("    </div>\n")
    }

    private fun verificationOptions(): String {
        return String.join(",", writeFlags)
    }

    private fun verificationFileLink(): kotlin.String {
        return "<a href=\"" + verificationFile.toURI().toASCIIString() + "\">verification file</a>"
    }

    private fun registerModal(id: kotlin.String, title: kotlin.String, vararg lines: kotlin.String) {
        contents.append("<div id=\"").append(id).append("\" uk-modal>\n")
            .append("    <div class=\"uk-modal-dialog uk-modal-body\">\n")
            .append("        <h2 class=\"uk-modal-title\">")
            .append(title)
            .append("</h2>\n")
        for (line in lines) {
            contents.append(line).append("\n")
        }
        contents.append("        <p>See the <a href=\"").append(documentationRegistry.getDocumentationFor("dependency_verification", "sec:^"))
            .append("\" target=\"_blank\">documentation</a> to get more information.</p>\n")
            .append("        <button class=\"uk-button uk-button-primary uk-modal-close\" type=\"button\">Ok</button>\n")
            .append("    </div>\n")
            .append("</div>\n")
    }

    private fun registerModals() {
        registerModal(
            "verified-not-trusted", "Key isn't trusted",
            "        <p>This indicates that a dependency was <i>verified</i>, that the signature matched, but that you don't <i>trust</i> this signature.</p>\n",
            "        <p>If you trust the author of the signature, you need to add the key to the trusted keys.</p>"
        )
        registerModal(
            "signature-didnt-match", "Signature didn't match",
            "        <p>This indicates that a dependency was <i>signed</i> but that the signature verification <b>failed</b>.</p>",
            "        <p>This happens when a dependency was compromised or that the signature was made for a different artifact than the one you got.</p>",
            "        <p>It's important that you <b>carefully review this problem</b>.</p>"
        )
        registerModal(
            "ignored-key", "A key was ignored",
            "        <p>This indicates that a dependency was <i>signed with an ignored key</i>.</p>",
            "        <p>You must provide at least one checksum so that verification can pass.</p>"
        )
        registerModal(
            "missing-key", "Public key couldn't be found",
            "        <p>This indicates that a dependency was <i>signed</i> but that Gradle couldn't download the public key to verify the signature.</p>",
            "        <p>You should check if the key is valid, and if so, provide a key server where to download it.</p>"
        )
        registerModal(
            "missing-checksums", "Checksums are missing",
            "        <p>This indicates that the dependency verification file doesn't contain at least one checksum for this artifact.</p>",
            "        <p>You must provide at least one checksum for artifact verification to pass.</p>"
        )
        registerModal(
            "checksum-mismatch", "Incorrect checksum",
            "        <p>This indicates that the dependency verification file <b>failed</b> because the actual checksum of the dependency artifact didn't match the expected checksum declared in the verification metadata.</p>",
            "        <p>This happens when a dependency was compromised or that downloaded artifact isn't the one that you expected.</p>",
            "        <p>It's important that you <b>carefully review this problem</b>.</p>"
        )
        registerModal(
            "deleted-artifact", "Deleted artifact",
            "        <p>This error usually indicated that the local dependency cache was tampered with.</p>",
            "        <p>This happens when someone manually deletes an artifact from the Gradle dependency cache, which is now corrupt.</p>"
        )
        registerModal(
            "signature-file-missing", "Missing signature file",
            "        <p>The signature file for this artifact wasn't found.</p>",
            "        <p>Usually it indicates that the signature doesn't exist in the repository the artifact was downloaded from.</p>",
            "        <p>In general this is not a problem but you should then declare at least one checksum for verification to pass.</p>"
        )
        registerModal(
            "signature-file-corrupt", "Corrupt signature file",
            "        <p>The signature file (.asc) for this artifact <b>could not be parsed</b>.</p>",
            "        <p>This usually means the file is malformed (invalid ASCII armor or corrupt PGP packets), the upstream repository serves a broken signature, the file was truncated during download or the repository is misconfigured.</p>",
            "        <p>Because the signature cannot be read, Gradle cannot decide whether the artifact is trustworthy. <b>Carefully review this problem</b>: fetch the signature from a trusted source, or contact the upstream maintainers to fix the published signature.</p>"
        )
    }

    private fun copyReportResource(outputDirectory: File, dirName: kotlin.String, fileName: kotlin.String) {
        val targetDir = File(outputDirectory, dirName)
        targetDir.mkdirs()
        copyResource(fileName, File(targetDir, fileName))
    }

    private fun copyResource(name: kotlin.String, outputFile: File) {
        try {
            javaClass.getResourceAsStream(name).use { `in` ->
                Files.copy(`in`, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: IOException) {
            throw throwAsUncheckedException(e)
        }
    }

    private fun prerenderSection(section: Section) {
        val size = section.errors.size
        contents.append("<a class=\"uk-accordion-title\" href=\"#\"><span uk-icon=\"chevron-right\"></span>").append(section.title)
            .append("&nbsp;<span class=\"uk-badge\">")
            .append(size)
            .append(if (size < 2) " error" else " errors")
            .append("</span></a>")
    }

    private fun renderSection(section: Section) {
        contents.append("<div class=\"uk-accordion-content\">")
            .append("<table class=\"uk-table uk-table-hover uk-table-divider uk-table-middle uk-table-small\">\n")
            .append("    <thead>\n")
            .append("        <tr>\n")
            .append("            <th class=\"uk-table-shrink uk-width-auto uk-text-nowrap\">Module</th>\n")
            .append("            <th class=\"uk-table-shrink uk-width-auto uk-text-nowrap\">Artifact</th>\n")
            .append("            <th class=\"uk-table-shrink uk-width-expand\">Problem(s)</th>\n")
            .append("        </tr>\n")
            .append("    </thead>\n")
            .append("    <tbody>\n")
        section.errors.forEach(Consumer { currentArtifact: ArtifactErrors? -> this.formatErrors(currentArtifact!!) })
        contents.append(
            "    </tbody>\n" +
                    "</table>"
        )
            .append("</div>")
    }

    private fun formatErrors(currentArtifact: ArtifactErrors) {
        contents.append("        <tr>\n")
        val firstFailure = currentArtifact.failures.get(0)
        reportItem(currentArtifact.key.getComponentIdentifier().getDisplayName())
        reportItem(createFileLink(currentArtifact.key, firstFailure.failure, firstFailure.repositoryName))
        contents.append("            <td>\n")
        currentArtifact.failures.forEach(Consumer { failure: RepositoryAwareVerificationFailure? -> this.formatError(failure!!) })
        contents.append("            </td>\n")
            .append("        </tr>\n")
    }

    private fun formatError(failure: RepositoryAwareVerificationFailure) {
        val vf = failure.failure
        reportSignatureProblems(vf)
        reportChecksumProblems(vf)
        reportOtherProblems(vf)
    }

    private fun createFileLink(key: ModuleComponentArtifactIdentifier, vf: VerificationFailure, repositoryName: kotlin.String): kotlin.String {
        var fileLink = "<div uk-tooltip=\"title: From repository '" + repositoryName + "'\">"
        fileLink += "<a href=\"" + vf.filePath.toURI().toASCIIString() + "\">" + key.fileName + "</a>"
        val signatureFile: File = vf.signatureFile
        if (signatureFile != null) {
            fileLink += "&nbsp;<a href=\"" + signatureFile.toURI().toASCIIString() + "\">(.asc)</a>"
        }
        fileLink += "</div>"
        return fileLink
    }

    private fun reportOtherProblems(vf: VerificationFailure) {
        if (vf is DeletedArtifact) {
            val reason = "Artifact has been deleted from dependency cache"
            reportItem(reason, "deleted-artifact", "info")
        }
    }

    private fun reportChecksumProblems(vf: VerificationFailure) {
        if (vf is MissingChecksums) {
            val reason: kotlin.String = warning("Checksums are missing from verification metadata")
            reportItem(reason, "missing-checksums", "info")
        } else if (vf is ChecksumVerificationFailure) {
            val cvf = vf
            val reason = "Expected a " + cvf.kind + " checksum of " + Companion.expected(cvf.expected!!) + " but was " + Companion.actual(cvf.actual!!)
            reportItem(reason, "checksum-mismatch", "warning")
        } else if (vf is OnlyIgnoredKeys) {
            val reason = "All public keys have been ignored"
            reportItem(reason, "missing-checksums", "info")
        }
    }

    private fun reportSignatureProblems(vf: VerificationFailure) {
        if (vf is MissingSignature) {
            reportItem("Signature file is missing", "signature-file-missing", "info")
        } else if (vf is InvalidSignatureFile) {
            val isf = vf
            val reason: kotlin.String = actual("Signature file is corrupt: " + isf.causeDescription)
            reportItem(reason, "signature-file-corrupt", "warning")
        } else if (vf is SignatureVerificationFailure) {
            val svf = vf
            val errors: MutableMap<kotlin.String, SignatureVerificationFailure.SignatureError> = svf.errors
            errors.forEach { (keyId: kotlin.String?, error: SignatureVerificationFailure.SignatureError?) ->
                val sb = StringBuilder()
                val publicKey = error!!.publicKey
                if (publicKey != null) {
                    svf.appendKeyDetails(sb, publicKey)
                } else {
                    sb.append("(not found)")
                }
                @Suppress("deprecation") val keyDetails = StringEscapeUtils.escapeHtml4(sb.toString())
                val keyInfo = "<b>" + keyId + " " + keyDetails + "</b>"
                when (error.kind) {
                    SignatureVerificationFailure.FailureKind.PASSED_NOT_TRUSTED -> {
                        val reason: kotlin.String = warning("Artifact was signed with key " + keyInfo + " but this key is not in your trusted key list")
                        reportItem(reason, "verified-not-trusted", "question")
                    }

                    SignatureVerificationFailure.FailureKind.FAILED -> {
                        reason = actual("Artifact was signed with key " + keyInfo + " but signature didn't match")
                        reportItem(reason, "signature-didnt-match", "warning")
                    }

                    SignatureVerificationFailure.FailureKind.IGNORED_KEY -> {
                        reason = grey("Artifact was signed with an ignored key: " + keyInfo)
                        reportItem(reason, "ignored-key", "info")
                    }

                    SignatureVerificationFailure.FailureKind.MISSING_KEY -> {
                        if (useKeyServers) {
                            reason = warning("Key " + keyInfo + " couldn't be found in local key file or remote key servers so verification couldn't be performed.")
                        } else {
                            reason = warning("Key " + keyInfo + " couldn't be found in local key file so verification couldn't be performed. Enable key resolution with --export-keys.")
                        }
                        reportItem(reason, "missing-key", "warning")
                        hasMissingKeys = true
                    }
                }
            }
        }
    }

    private fun reportItem(item: kotlin.String) {
        contents.append("            <td class=\"uk-text-nowrap\"")
            .append(">").append(item).append("</td>\n")
    }

    private fun reportItem(item: kotlin.String, tipTarget: kotlin.String, tipIcon: kotlin.String) {
        val tip = "<a uk-toggle=\"target: #" + tipTarget + "\" uk-icon=\"icon: " + tipIcon + "\"></a>"
        contents.append("            <p>").append(item).append("&nbsp;").append(tip).append("</p>\n")
    }

    private class Section(private val title: kotlin.String) {
        private val errors: MutableList<ArtifactErrors> = ArrayList<ArtifactErrors>()
        private var currentArtifact: ArtifactErrors? = null

        fun newArtifact(artifactErrors: ArtifactErrors) {
            errors.add(artifactErrors)
            currentArtifact = artifactErrors
        }
    }

    private class ArtifactErrors(val key: ModuleComponentArtifactIdentifier) {
        val failures: MutableList<RepositoryAwareVerificationFailure> = ArrayList<RepositoryAwareVerificationFailure>()

        fun addFailure(failure: RepositoryAwareVerificationFailure) {
            failures.add(failure)
        }
    }

    companion object {
        private fun expected(text: kotlin.String): kotlin.String {
            return emphasize(text, "blue")
        }

        private fun actual(text: kotlin.String): kotlin.String {
            return emphasize(text, "#ee442f")
        }

        private fun warning(text: kotlin.String): kotlin.String {
            return emphasize(text, "#c59434")
        }

        private fun grey(text: kotlin.String): kotlin.String {
            return emphasize(text, "#cccccc")
        }

        private fun emphasize(text: kotlin.String, color: kotlin.String): kotlin.String {
            return "<span style=\"font-weight:bold; color: " + color + "\">" + text + "</span>"
        }
    }
}
