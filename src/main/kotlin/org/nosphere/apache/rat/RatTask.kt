/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.nosphere.apache.rat

import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Console
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import org.gradle.workers.WorkerExecutor

import org.gradle.kotlin.dsl.*

import javax.inject.Inject

private
const val ratVersion = "0.13"

@CacheableTask
open class RatTask private constructor(
    private val patternSet: PatternSet,
    private val providers: ProviderFactory,
    private val objects: ObjectFactory,
    private val layout: ProjectLayout,
    private val workerExecutor: WorkerExecutor
) : DefaultTask(), PatternFilterable by patternSet {

    @Inject
    constructor(
        providers: ProviderFactory,
        objects: ObjectFactory,
        layout: ProjectLayout,
        workerExecutor: WorkerExecutor
    ) : this(newDefaultPatternSet(), providers, objects, layout, workerExecutor)

    companion object {

        fun newDefaultPatternSet() =
            PatternSet().apply {
                exclude("**/.gradle/**")
            }
    }

    @Console
    val verbose = objects.property<Boolean>().apply {
        set(false)
    }

    @Input
    val failOnError = objects.property<Boolean>().apply {
        set(true)
    }

    @Internal
    val inputDir = objects.directoryProperty().apply {
        set(layout.projectDirectory)
    }

    @get:Input
    val addDefaultMatchers = objects.property<Boolean>().apply {
        set(true)
    }

    @get:Nested
    val substringMatchers = objects.listProperty<SubstringMatcher>().apply {
        set(emptyList())
    }

    fun substringMatcher(licenseFamilyCategory: String, licenseFamilyName: String, vararg substrings: String) {
        substringMatchers.add(SubstringMatcher(licenseFamilyCategory, licenseFamilyName, substrings.toList()))
    }

    @get:Input
    val approvedLicenses = objects.listProperty<String>().apply {
        set(emptyList())
    }

    fun approvedLicense(familyName: String) {
        approvedLicenses.add(familyName)
    }

    @Internal
    override fun getIncludes(): MutableSet<String> = patternSet.includes

    @Internal
    override fun getExcludes(): MutableSet<String> = patternSet.excludes

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @Suppress("unused")
    val inputFiles: FileTree
        get() =
            objects.fileTree().apply {
                from(inputDir.get().asFile)
                if (!patternSet.isEmpty) {
                    include(patternSet.asSpec)
                }
            }

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.NONE)
    val excludeFile = objects.fileProperty()

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.NONE)
    val stylesheet = objects.fileProperty()

    @OutputDirectory
    val reportDir = objects.directoryProperty().apply {
        set(layout.projectDirectory.dir(providers.provider {
            project.the<ReportingExtension>().file(name).canonicalPath
        }))
    }

    @get:InputFiles
    @get:Classpath
    internal
    val ratClasspath: FileCollection = objects.fileCollection().apply {
        from(project.run {
            configurations.detachedConfiguration(
                dependencies.create("org.apache.rat:apache-rat:$ratVersion")
            )
        })
    }

    @TaskAction
    @Suppress("unused")
    fun rat(): Unit =
        workerExecutor.processIsolation {
            classpath.from(ratClasspath)
        }.submit(RatWork::class) {
            verbose.set(this@RatTask.verbose)
            failOnError.set(this@RatTask.failOnError)
            addDefaultMatchers.set(this@RatTask.addDefaultMatchers)
            substringMatchers.set(this@RatTask.substringMatchers)
            approvedLicenses.set(this@RatTask.approvedLicenses)
            baseDir.set(this@RatTask.inputDir)
            reportedFiles.from(this@RatTask.inputFiles)
            excludeFile.set(this@RatTask.excludeFile)
            stylesheet.set(this@RatTask.stylesheet.takeIf { it.isPresent } ?: defaultStylesheet())
            reportDirectory.set(this@RatTask.reportDir)
        }

    private
    fun defaultStylesheet() =
        temporaryDirectory.map { tmpDir ->
            tmpDir.file("default-stylesheet.xsl").apply {
                asFile.parentFile.mkdirs()
                RatTask::class.java.getResourceAsStream("apache-rat-output-to-html.xsl").buffered().use { input ->
                    asFile.outputStream().buffered().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }

    private
    val temporaryDirectory: Provider<Directory>
        get() = layout.dir(providers.provider { temporaryDir })
}
