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

import org.gradle.testkit.runner.TaskOutcome
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized


@RunWith(Parameterized::class)
class RatPluginTest(gradleVersion: String) : AbstractPluginTest(gradleVersion) {

    @Test
    fun `success, up-to-date and from-cache`() {
        withBuildScript("""
            plugins {
                id("base")
                id("org.nosphere.apache.rat")
            }
            repositories {
                gradlePluginPortal()
            }
            tasks.rat {
                verbose.set(true)
                excludes = [
                    'build.gradle', 'settings.gradle', 'build/**', '.gradle/**', '.gradle-test-kit/**',
                ]
                exclude(
                    'guh/**',
                    'no-license-file.txt'
                )
            }
        """)
        withFile("no-license-file.txt", "Nothing here.")

        build("check").apply {
            println(output)
            assertThat(outcomeOf(":rat"), equalTo(TaskOutcome.SUCCESS))
            assertGeneratedAllReports()
        }

        build("check").apply {
            println(output)
            assertThat(outcomeOf(":rat"), equalTo(TaskOutcome.UP_TO_DATE))
        }

        build("clean", "check", "--build-cache", "-g", rootDir.resolve("guh").canonicalPath).apply {
            println(output)
            assertThat(outcomeOf(":rat"), equalTo(TaskOutcome.SUCCESS))
        }

        build("clean", "check", "--build-cache", "-g", rootDir.resolve("guh").canonicalPath).apply {
            println(output)
            assertThat(outcomeOf(":rat"), equalTo(TaskOutcome.FROM_CACHE))
        }
    }

    @Test
    fun `fail the build when finding a file with unapproved or unknown license`() {
        withBuildScript("""
            plugins {
                id("base")
                id("org.nosphere.apache.rat")
            }
            repositories {
                gradlePluginPortal()
            }
            tasks.rat {
                verbose.set(true)
                excludes = [
                    'build.gradle', 'settings.gradle', 'build/**', '.gradle/**', '.gradle-test-kit/**'
                ]
            }
        """)
        withFile("no-license-file.txt", "Nothing here.")

        buildAndFail("check").apply {
            println(output)
            assertThat(outcomeOf(":rat"), equalTo(TaskOutcome.FAILED))
            assertGeneratedAllReports()
            assertThat(output, containsString("Apache Rat audit failure - 1 unapproved license"))
            assertThat(output, containsString(htmlReportFile.absolutePath))
        }

        buildAndFail("check").apply {
            println(output)
            assertThat(outcomeOf(":rat"), equalTo(TaskOutcome.FAILED))
        }
    }

    @Test
    fun `do not fail but report errors when failOnError is false`() {
        withBuildScript("""
            plugins {
                id("base")
                id("org.nosphere.apache.rat")
            }
            repositories {
                gradlePluginPortal()
            }
            tasks.rat {
                verbose.set(true)
                failOnError.set(false)
                excludes = [
                    'build.gradle', 'settings.gradle', 'build/**', '.gradle/**', '.gradle-test-kit/**'
                ]
            }
        """)
        withFile("no-license-file.txt", "Nothing here.")

        build("check").apply {
            println(output)
            assertThat(outcomeOf(":rat"), equalTo(TaskOutcome.SUCCESS))
            assertGeneratedAllReports()
            assertThat(output, containsString("Apache Rat audit failure - 1 unapproved license"))
            assertThat(output, containsString(htmlReportFile.absolutePath))
        }

        build("check").apply {
            println(output)
            assertThat(outcomeOf(":rat"), equalTo(TaskOutcome.UP_TO_DATE))
        }
    }

    @Test
    fun `no deprecation warnings`() {
        withBuildScript("""
            plugins {
                id("base")
                id("org.nosphere.apache.rat")
            }
            repositories {
                gradlePluginPortal()
            }
            tasks.rat {
                excludes = [
                    'build.gradle', 'settings.gradle', 'build/**', '.gradle/**', '.gradle-test-kit/**',
                ]
            }
        """)

        build("rat", "--warning-mode=all").apply {
            println(output)
            assertThat(outcomeOf(":rat"), equalTo(TaskOutcome.SUCCESS))
            assertThat(output, not(containsString("has been deprecated")))
        }
    }

    private
    fun assertGeneratedAllReports() {
        assertTrue(xmlReportFile.isFile)
        assertTrue(plainReportFile.isFile)
        assertTrue(htmlReportFile.isFile)
    }

    private
    val xmlReportFile
        get() = rootDir.resolve("build/reports/rat/rat-report.xml")

    private
    val plainReportFile
        get() = rootDir.resolve("build/reports/rat/rat-report.txt")

    private
    val htmlReportFile
        get() = rootDir.resolve("build/reports/rat/index.html")

}
