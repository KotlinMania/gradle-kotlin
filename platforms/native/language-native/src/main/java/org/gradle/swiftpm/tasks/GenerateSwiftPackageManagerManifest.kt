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
package org.gradle.swiftpm.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.nativeplatform.Linkage
import org.gradle.swiftpm.internal.BranchDependency
import org.gradle.swiftpm.internal.DefaultLibraryProduct
import org.gradle.swiftpm.internal.DefaultPackage
import org.gradle.swiftpm.internal.VersionDependency
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.TreeSet

/**
 * A task that produces a Swift Package Manager manifest.
 *
 * @since 4.6
 */
@DisableCachingByDefault(because = "Not made cacheable, yet")
abstract class GenerateSwiftPackageManagerManifest : DefaultTask() {
    private val projectName: String

    init {
        projectName = getProject().getName()
    }

    @get:Input
    abstract val `package`: Property<Package?>?

    @get:OutputFile
    abstract val manifestFile: RegularFileProperty?

    @TaskAction
    fun generate() {
        val srcPackage = this.`package`.get() as DefaultPackage
        val manifest = this.manifestFile.get().getAsFile().toPath()
        try {
            val baseDir = manifest.getParent()
            Files.createDirectories(baseDir)
            PrintWriter(Files.newBufferedWriter(manifest, StandardCharsets.UTF_8)).use { writer ->
                writer.println("// swift-tools-version:4.0")
                writer.println("//")
                writer.println("// GENERATED FILE - do not edit")
                writer.println("//")
                writer.println("import PackageDescription")
                writer.println()
                writer.println("let package = Package(")
                writer.println("    name: \"" + projectName + "\",")
                writer.println("    products: [")
                for (product in srcPackage.getProducts()) {
                    if (product.isExecutable()) {
                        writer.print("        .executable(")
                        writer.print("name: \"")
                        writer.print(product.getName())
                        writer.print("\"")
                    } else {
                        writer.print("        .library(")
                        writer.print("name: \"")
                        writer.print(product.getName())
                        val library = product as DefaultLibraryProduct
                        if (library.getLinkage() == Linkage.SHARED) {
                            writer.print("\", type: .dynamic")
                        } else {
                            writer.print("\", type: .static")
                        }
                    }
                    writer.print(", targets: [\"")
                    writer.print(product.getTarget().getName())
                    writer.println("\"]),")
                }
                writer.println("    ],")
                if (!srcPackage.getDependencies().isEmpty()) {
                    writer.println("    dependencies: [")
                    for (dependency in srcPackage.getDependencies()) {
                        writer.print("        .package(url: \"")
                        if (dependency.getUrl().getScheme() == "file") {
                            writer.print(baseDir.relativize(File(dependency.getUrl()).toPath()))
                        } else {
                            writer.print(dependency.getUrl())
                        }
                        writer.print("\", ")
                        if (dependency is VersionDependency) {
                            val versionDependency = dependency
                            if (versionDependency.getUpperBound() == null) {
                                writer.print("from: \"")
                                writer.print(versionDependency.getLowerBound())
                                writer.print("\"")
                            } else if (versionDependency.isUpperInclusive()) {
                                writer.print("\"")
                                writer.print(versionDependency.getLowerBound())
                                writer.print("\"...\"")
                                writer.print(versionDependency.getUpperBound())
                                writer.print("\"")
                            } else {
                                writer.print("\"")
                                writer.print(versionDependency.getLowerBound())
                                writer.print("\"..<\"")
                                writer.print(versionDependency.getUpperBound())
                                writer.print("\"")
                            }
                        } else {
                            writer.print(".branch(\"")
                            writer.print((dependency as BranchDependency).getBranch())
                            writer.print("\")")
                        }
                        writer.println("),")
                    }
                    writer.println("    ],")
                }
                writer.println("    targets: [")
                for (target in srcPackage.getTargets()) {
                    writer.println("        .target(")
                    writer.print("            name: \"")
                    writer.print(target.getName())
                    writer.println("\",")
                    if (!target.getRequiredTargets().isEmpty() || !target.getRequiredProducts().isEmpty()) {
                        writer.println("            dependencies: [")
                        for (dep in target.getRequiredTargets()) {
                            writer.print("                .target(name: \"")
                            writer.print(dep)
                            writer.println("\"),")
                        }
                        for (dep in target.getRequiredProducts()) {
                            writer.print("                .product(name: \"")
                            writer.print(dep)
                            writer.println("\"),")
                        }
                        writer.println("            ],")
                    }
                    writer.print("            path: \"")
                    val productPath = target.getPath().toPath()
                    val relPath = baseDir.relativize(productPath).toString()
                    writer.print(if (relPath.isEmpty()) "." else relPath)
                    writer.println("\",")
                    writer.println("            sources: [")
                    val sorted: MutableSet<String?> = TreeSet<String?>()
                    for (sourceFile in target.getSourceFiles()) {
                        sorted.add(productPath.relativize(sourceFile.toPath()).toString())
                    }
                    for (sourcePath in sorted) {
                        writer.print("                \"")
                        writer.print(sourcePath)
                        writer.println("\",")
                    }
                    writer.print("            ]")
                    if (target.getPublicHeaderDir() != null) {
                        writer.println(",")
                        writer.print("            publicHeadersPath: \"")
                        writer.print(productPath.relativize(target.getPublicHeaderDir()!!.toPath()))
                        writer.print("\"")
                    }
                    writer.println()
                    writer.println("        ),")
                }
                writer.print("    ]")
                if (srcPackage.getSwiftLanguageVersion() != null) {
                    writer.println(",")
                    writer.print("    swiftLanguageVersions: [")
                    writer.print(srcPackage.getSwiftLanguageVersion()!!.getVersion())
                    writer.print("]")
                }
                writer.println()
                writer.println(")")
            }
        } catch (e: IOException) {
            throw GradleException(String.format("Could not write manifest file %s.", manifest), e)
        }
    }
}
