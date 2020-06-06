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
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.model.ObjectFactory
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Console
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import org.gradle.workers.WorkerExecutor
import org.gradle.workers.IsolationMode.PROCESS

import org.gradle.kotlin.dsl.*

import javax.inject.Inject

private
const val ratVersion = "0.13"

@CacheableTask
open class RatTask private constructor(
    private val patternSet: PatternSet,
    private val objects: ObjectFactory,
    private val workerExecutor: WorkerExecutor
) : DefaultTask(), PatternFilterable by patternSet {

    @Inject
    constructor(
        objects: ObjectFactory,
        workerExecutor: WorkerExecutor
    ) : this(newDefaultPatternSet(), objects, workerExecutor)

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
        set(project.layout.projectDirectory)
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
        set(project.layout.projectDirectory.dir(project.provider {
            project.the<ReportingExtension>().file(name).canonicalPath
        }))
    }

    @get:Internal
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
    fun rat(): Unit = workerExecutor.submit(RatWork::class) {
        isolationMode = PROCESS
        classpath(ratClasspath)
        params(buildRatWorkSpec())
    }

    private
    fun buildRatWorkSpec() = RatWorkSpec(
        verbose = verbose.get(),
        failOnError = failOnError.get(),
        addDefaultMatchers = addDefaultMatchers.get(),
        substringMatchers = substringMatchers.get(),
        approvedLicenses = approvedLicenses.get(),
        baseDir = inputDir.asFile.get(),
        reportedFiles = inputFiles.files.filter { it.isFile },
        excludeFile = excludeFile.orNull?.asFile,
        stylesheet = stylesheet.asFile.orNull ?: defaultStylesheet(),
        reportDirectory = reportDir.asFile.get()
    )

    private
    fun defaultStylesheet() =
        temporaryDir.resolve("default-stylesheet.xsl").apply {
            parentFile.mkdirs()
            RatTask::class.java.getResourceAsStream("apache-rat-output-to-html.xsl").buffered().use { input ->
                outputStream().buffered().use { output ->
                    input.copyTo(output)
                }
            }
        }
}
