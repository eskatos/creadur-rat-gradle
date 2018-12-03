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
package org.codeartisans.rat

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileTree
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Console
import org.gradle.workers.WorkerExecutor
import org.gradle.workers.IsolationMode.PROCESS

import org.gradle.kotlin.dsl.*

import javax.inject.Inject


private
const val ratVersion = "0.13"


@CacheableTask
open class RatTask @Inject constructor(
        private val workerExecutor: WorkerExecutor
) : DefaultTask() {

    @Console
    val verbose = project.objects.property<Boolean>().apply {
        set(false)
    }

    @Input
    val failOnError = project.objects.property<Boolean>().apply {
        set(true)
    }

    @Internal
    val inputDir = newInputDirectory().apply {
        set(project.layout.projectDirectory)
    }

    @Internal
    val excludes = project.objects.listProperty<String>().apply {
        set(listOf("**/.gradle/**"))
    }

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @Suppress("unused")
    val inputFiles: FileTree
        get() = inputDir.map {
            project.fileTree(inputDir.get().asFile) {
                exclude(this@RatTask.excludes.get())
            }
        }.get()

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.NONE)
    val excludeFile = newInputFile()

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.NONE)
    val stylesheet = newInputFile()

    @OutputDirectory
    val reportDir = newOutputDirectory().apply {
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
}
