/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.publish.maven

import org.gradle.api.Action
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.publish.Publication
import org.gradle.api.publish.VersionMappingStrategy
import org.gradle.api.tasks.Nested
import org.gradle.internal.HasInternalProtocol
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty

/**
 * A `MavenPublication` is the representation/configuration of how Gradle should publish something in Maven format.
 *
 * You directly add a named Maven publication the project's `publishing.publications` container by providing [MavenPublication] as the type.
 * <pre>
 * publishing {
 * publications {
 * myPublicationName(MavenPublication) {
 * // Configure the publication here
 * }
 * }
 * }
</pre> *
 *
 * The default Maven POM identifying attributes are mapped as follows:
 *
 *  * `groupId` - `project.group`
 *  * `artifactId` - `project.name`
 *  * `version` - `project.version`
 *
 *
 *
 *
 * For certain common use cases, it's often sufficient to specify the component to publish, and nothing more ([.from].
 * The published component is used to determine which artifacts to publish, and which dependencies should be listed in the generated POM file.
 *
 *
 * To add additional artifacts to the set published, use the [.artifact] and [.artifact] methods.
 * You can also completely replace the set of published artifacts using [.setArtifacts].
 * Together, these methods give you full control over what artifacts will be published.
 *
 *
 * To customize the metadata published in the generated POM, set properties, e.g. [MavenPom.getDescription], on the POM returned via the [.getPom]
 * method or directly by an action (or closure) passed into [.pom].
 * As a last resort, it is possible to modify the generated POM using the [MavenPom.withXml] method.
 *
 *
 * <pre class='autoTested'>
 * // Example of publishing a Java module with a source artifact and a customized POM
 * plugins {
 * id 'java'
 * id 'maven-publish'
 * }
 *
 * task sourceJar(type: Jar) {
 * from sourceSets.main.allJava
 * archiveClassifier = "sources"
 * }
 *
 * publishing {
 * publications {
 * myPublication(MavenPublication) {
 * from components.java
 * artifact sourceJar
 * pom {
 * name = "Demo"
 * description = "A demonstration of Maven POM customization"
 * url = "http://www.example.com/project"
 * licenses {
 * license {
 * name = "The Apache License, Version 2.0"
 * url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
 * }
 * }
 * developers {
 * developer {
 * id = "johnd"
 * name = "John Doe"
 * email = "john.doe@example.com"
 * }
 * }
 * scm {
 * connection = "scm:svn:http://subversion.example.com/svn/project/trunk/"
 * developerConnection = "scm:svn:https://subversion.example.com/svn/project/trunk/"
 * url = "http://subversion.example.com/svn/project/trunk/"
 * }
 * }
 * }
 * }
 * }
</pre> *
 *
 * @since 1.4
 */
@HasInternalProtocol
interface MavenPublication : Publication {
    @get:Nested
    val pom: MavenPom?

    /**
     * Configures the POM that will be published.
     *
     * The supplied action will be executed against the [.getPom] result. This method also accepts a closure argument, by type coercion.
     *
     * @param configure The configuration action.
     */
    fun pom(configure: Action<in MavenPom?>?)

    /**
     * Provides the software component that should be published.
     *
     *
     *  * Any artifacts declared by the component will be included in the publication.
     *  * The dependencies declared by the component will be included in the published meta-data.
     *
     *
     * Currently 3 types of component are supported: 'components.java' (added by the JavaPlugin), 'components.web' (added by the WarPlugin)
     * and `components.javaPlatform` (added by the JavaPlatformPlugin).
     *
     * For any individual MavenPublication, only a single component can be provided in this way.
     *
     * The following example demonstrates how to publish the 'java' component to a Maven repository.
     * <pre class='autoTested'>
     * plugins {
     * id 'java'
     * id 'maven-publish'
     * }
     *
     * publishing {
     * publications {
     * maven(MavenPublication) {
     * from components.java
     * }
     * }
     * }
    </pre> *
     *
     * @param component The software component to publish.
     */
    fun from(component: SoftwareComponent?)

    /**
     * Creates a custom [MavenArtifact] to be included in the publication.
     *
     * The `artifact` method can take a variety of input:
     *
     *  * A [org.gradle.api.artifacts.PublishArtifact] instance. Extension and classifier values are taken from the wrapped instance.
     *  * An [org.gradle.api.tasks.bundling.AbstractArchiveTask] instance. Extension and classifier values are taken from the wrapped instance.
     *  * Anything that can be resolved to a [java.io.File] via the [org.gradle.api.Project.file] method.
     * Extension and classifier values are interpolated from the file name.
     *  * A [java.util.Map] that contains a 'source' entry that can be resolved as any of the other input types, including file.
     * This map can contain a 'classifier' and an 'extension' entry to further configure the constructed artifact.
     *
     *
     * The following example demonstrates the addition of various custom artifacts.
     * <pre class='autoTested'>
     * plugins {
     * id 'maven-publish'
     * }
     *
     * task sourceJar(type: Jar) {
     * archiveClassifier = "sources"
     * }
     *
     * publishing {
     * publications {
     * maven(MavenPublication) {
     * artifact sourceJar // Publish the output of the sourceJar task
     * artifact 'my-file-name.jar' // Publish a file created outside of the build
     * artifact source: sourceJar, classifier: 'src', extension: 'zip'
     * }
     * }
     * }
    </pre> *
     *
     * @param source The source of the artifact content.
     */
    fun artifact(source: Any?): MavenArtifact?

    /**
     * Creates an [MavenArtifact] to be included in the publication, which is configured by the associated action.
     *
     * The first parameter is used to create a custom artifact and add it to the publication, as per [.artifact].
     * The created [MavenArtifact] is then configured using the supplied action, which can override the extension or classifier of the artifact.
     * This method also accepts the configure action as a closure argument, by type coercion.
     *
     * <pre class='autoTested'>
     * plugins {
     * id 'maven-publish'
     * }
     *
     * task sourceJar(type: Jar) {
     * archiveClassifier = "sources"
     * }
     *
     * publishing {
     * publications {
     * maven(MavenPublication) {
     * artifact(sourceJar) {
     * // These values will be used instead of the values from the task. The task values will not be updated.
     * classifier = "src"
     * extension = "zip"
     * }
     * artifact("my-docs-file.htm") {
     * classifier = "documentation"
     * extension = "html"
     * }
     * }
     * }
     * }
    </pre> *
     *
     * @param source The source of the artifact.
     * @param config An action to configure the values of the constructed [MavenArtifact].
     */
    fun artifact(source: Any?, config: Action<in MavenArtifact?>?): MavenArtifact?

    /**
     * Returns the complete set of artifacts for this publication.
     * @return the artifacts.
     */
    /**
     * Clears any previously added artifacts from [.getArtifacts] and creates artifacts from the specified sources.
     * Each supplied source is interpreted as per [.artifact].
     *
     * For example, to exclude the dependencies declared by a component and instead use a custom set of artifacts:
     * <pre class='autoTested'>
     * plugins {
     * id 'java'
     * id 'maven-publish'
     * }
     *
     * task sourceJar(type: Jar) {
     * archiveClassifier = "sources"
     * }
     *
     * publishing {
     * publications {
     * maven(MavenPublication) {
     * from components.java
     * artifacts = ["my-custom-jar.jar", sourceJar]
     * }
     * }
     * }
    </pre> *
     *
     * @param sources The set of artifacts for this publication.
     */
    var artifacts: MavenArtifactSet?

    /**
     * Sets the groupId for this publication.
     */
    @get:ToBeReplacedByLazyProperty
    var groupId: String?

    /**
     * Sets the artifactId for this publication.
     */
    @get:ToBeReplacedByLazyProperty
    var artifactId: String?

    /**
     * Sets the version for this publication.
     */
    @get:ToBeReplacedByLazyProperty
    var version: String?

    /**
     * Configures the version mapping strategy.
     *
     * For example, to use resolved versions for runtime dependencies:
     * <pre class='autoTested'>
     * plugins {
     * id 'java'
     * id 'maven-publish'
     * }
     *
     * publishing {
     * publications {
     * maven(MavenPublication) {
     * from components.java
     * versionMapping {
     * usage('java-runtime'){
     * fromResolutionResult()
     * }
     * }
     * }
     * }
     * }
    </pre> *
     *
     * @param configureAction the configuration
     *
     * @since 5.2
     */
    fun versionMapping(configureAction: Action<in VersionMappingStrategy?>?)

    /**
     * Silences the compatibility warnings for the Maven publication for the specified variant.
     *
     * Warnings are emitted when Gradle features are used that cannot be mapped completely to Maven POM.
     *
     * @param variantName the variant to silence warning for
     *
     * @since 6.0
     */
    fun suppressPomMetadataWarningsFor(variantName: String?)


    /**
     * Silences all the compatibility warnings for the Maven publication.
     *
     * Warnings are emitted when Gradle features are used that cannot be mapped completely to Maven POM.
     *
     * @since 6.0
     */
    fun suppressAllPomMetadataWarnings()
}
