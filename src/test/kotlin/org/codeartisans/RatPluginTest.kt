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
package org.codeartisans

import org.gradle.testkit.runner.TaskOutcome
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized


@RunWith(Parameterized::class)
class RatPluginTest(gradleVersion: String) : AbstractPluginTest(gradleVersion) {

    @Test
    fun `success`() {
        withBuildScript("""
            plugins {
                id("base")
                id("org.codeartisans.rat")
            }
            repositories {
                gradlePluginPortal()
            }
            tasks.rat {
                verbose.set(true)
                excludes = [
                    'build.gradle', 'settings.gradle', 'build/**', '.gradle/**', '.gradle-test-kit/**', 'guh/**',
                    'no-license-file.txt'
                ]
            }
        """)
        withFile("no-license-file.txt", "Nothing here.")

        build("check").apply {
            println(output)
            assertThat(outcomeOf(":rat"), equalTo(TaskOutcome.SUCCESS))
            assertTrue(rootDir.resolve("build/reports/rat/rat-report.xml").isFile)
            assertTrue(rootDir.resolve("build/reports/rat/rat-report.txt").isFile)
            assertTrue(rootDir.resolve("build/reports/rat/index.html").isFile)
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
                id("org.codeartisans.rat")
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
            assertTrue(rootDir.resolve("build/reports/rat/rat-report.xml").isFile)
            assertTrue(rootDir.resolve("build/reports/rat/rat-report.txt").isFile)
            assertTrue(rootDir.resolve("build/reports/rat/index.html").isFile)
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
                id("org.codeartisans.rat")
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
            assertTrue(rootDir.resolve("build/reports/rat/rat-report.xml").isFile)
            assertTrue(rootDir.resolve("build/reports/rat/rat-report.txt").isFile)
            assertTrue(rootDir.resolve("build/reports/rat/index.html").isFile)
        }

        build("check").apply {
            println(output)
            assertThat(outcomeOf(":rat"), equalTo(TaskOutcome.UP_TO_DATE))
        }
    }
}
