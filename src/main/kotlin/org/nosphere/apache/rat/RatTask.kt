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
import org.gradle.api.file.FileTree
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Console
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import org.gradle.util.GradleVersion
import org.gradle.workers.WorkerExecutor
import org.gradle.workers.IsolationMode.PROCESS

import org.gradle.kotlin.dsl.*

import javax.inject.Inject


private
const val ratVersion = "0.13"


@CacheableTask
open class RatTask private constructor(
    private val patternSet: PatternSet,
    private val workerExecutor: WorkerExecutor
) : DefaultTask(), PatternFilterable by patternSet {

    @Inject
    constructor(workerExecutor: WorkerExecutor) : this(newDefaultPatternSet(), workerExecutor)

    companion object {

        fun newDefaultPatternSet() =
            PatternSet().apply {
                exclude("**/.gradle/**")
            }
    }

    @Console
    val verbose = project.objects.property<Boolean>().apply {
        set(false)
    }

    @Input
    val failOnError = project.objects.property<Boolean>().apply {
        set(true)
    }

    @Internal
    val inputDir = newInputDirectoryProperty().apply {
        set(project.layout.projectDirectory)
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
            project.fileTree(inputDir.get().asFile) {
                if (!patternSet.isEmpty) {
                    include(patternSet.asSpec)
                }
            }

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.NONE)
    val excludeFile = newInputFileProperty()

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.NONE)
    val stylesheet = newInputFileProperty()

    @OutputDirectory
    val reportDir = newOutputDirectoryProperty().apply {
        set(project.layout.projectDirectory.dir(project.provider {
            project.the<ReportingExtension>().file(name).canonicalPath
        }))
    }

    @TaskAction
    @Suppress("unused")
    fun rat(): Unit = workerExecutor.submit(RatWork::class) {
        isolationMode = PROCESS
        classpath(resolveRatClasspath())
        params(buildRatWorkSpec())
    }

    private
    fun resolveRatClasspath() = project.run {
        configurations.detachedConfiguration(
            dependencies.create("org.apache.rat:apache-rat:$ratVersion")
        ).files
    }

    private
    fun buildRatWorkSpec() = RatWorkSpec(
        verbose = verbose.get(),
        failOnError = failOnError.get(),
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

    private
    object CurrentGradle {
        val isLessThanFiveZero = GradleVersion.current() < GradleVersion.version("5.0")
    }

    private
    fun newInputFileProperty() = when {
        CurrentGradle.isLessThanFiveZero -> newInputFile()
        else -> project.objects.fileProperty()
    }

    private
    fun newInputDirectoryProperty() = when {
        CurrentGradle.isLessThanFiveZero -> newInputDirectory()
        else -> project.objects.directoryProperty()
    }

    private
    fun newOutputDirectoryProperty() = when {
        CurrentGradle.isLessThanFiveZero -> newOutputDirectory()
        else -> project.objects.directoryProperty()
    }
}
