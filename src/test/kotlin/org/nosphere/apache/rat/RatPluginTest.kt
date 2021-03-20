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

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.TaskOutcome.FAILED
import org.gradle.testkit.runner.TaskOutcome.FROM_CACHE
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class RatPluginTest(testMatrix: TestMatrix) : AbstractPluginTest(testMatrix) {

    @Test
    fun `success, up-to-date and from-cache`() {
        withBuildScript(
            """
            plugins {
                id("base")
                id("org.nosphere.apache.rat")
            }
            repositories {
                mavenCentral()
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
            """
        )
        withFile("no-license-file.txt", "Nothing here.")

        build("check") {
            assertRatTask(SUCCESS)
            assertGeneratedAllReports()
        }

        build("check") {
            if (testMatrix.isGradleMin63) assertRatTask(UP_TO_DATE)
            else assertRatTask(SUCCESS)
        }

        build("clean", "check", "--build-cache", "-g", rootDir.resolve("guh").canonicalPath) {
            assertRatTask(SUCCESS)
        }

        build("clean", "check", "--build-cache", "-g", rootDir.resolve("guh").canonicalPath) {
            assertRatTask(FROM_CACHE)
            assertGeneratedAllReports()
        }
    }

    @Test
    fun `fail the build when finding a file with unapproved or unknown license`() {
        withBuildScript(
            """
            plugins {
                id("base")
                id("org.nosphere.apache.rat")
            }
            repositories {
                mavenCentral()
            }
            tasks.rat {
                verbose.set(true)
                excludes = [
                    'build.gradle', 'settings.gradle', 'build/**', '.gradle/**', '.gradle-test-kit/**'
                ]
            }
            """
        )
        withFile("no-license-file.txt", "Nothing here.")

        buildAndFail("check") {
            assertRatTask(FAILED)
            assertGeneratedAllReports()
            assertOutputContainsAuditFailureMessage()
        }

        buildAndFail("check") {
            assertRatTask(FAILED)
        }
    }

    @Test
    fun `do not fail but report errors when failOnError is false`() {
        withBuildScript(
            """
            plugins {
                id("base")
                id("org.nosphere.apache.rat")
            }
            repositories {
                mavenCentral()
            }
            tasks.rat {
                verbose.set(true)
                failOnError.set(false)
                excludes = [
                    'build.gradle', 'settings.gradle', 'build/**', '.gradle/**', '.gradle-test-kit/**'
                ]
            }
            """
        )
        withFile("no-license-file.txt", "Nothing here.")

        build("check") {
            assertRatTask(SUCCESS)
            assertGeneratedAllReports()
            assertOutputContainsAuditFailureMessage()
        }

        build("check") {
            if (testMatrix.isGradleMin63) assertRatTask(UP_TO_DATE)
            else assertRatTask(SUCCESS)
        }
    }

    @Test
    fun `can declare custom license matchers`() {
        withBuildScript(
            """
            plugins {
                id("base")
                id("org.nosphere.apache.rat")
            }
            repositories {
                mavenCentral()
            }
            tasks.rat {
                verbose.set(true)
                excludes = ['build.gradle', 'settings.gradle', 'build/**', '.gradle/**', '.gradle-test-kit/**']
                substringMatcher("MIT", "The MIT License", "Permission is hereby granted, free of charge, to any person obtaining a copy")
            }
            """
        )
        withFile(
            "substring-mit.txt",
            "// Permission is hereby granted, free of charge, to any person obtaining a copy"
        )

        build("check", "-s") {
            assertRatTask(SUCCESS)
            assertGeneratedAllReports()
        }
    }

    @Test
    fun `can disable default license matchers`() {
        withBuildScript(
            """
            plugins {
                id("base")
                id("org.nosphere.apache.rat")
            }
            repositories {
                mavenCentral()
            }
            tasks.rat {
                verbose.set(true)
                addDefaultMatchers.set(false)
                excludes = ['build.gradle', 'settings.gradle', 'build/**', '.gradle/**', '.gradle-test-kit/**']
            }
            """
        )
        withFile("default-licensed.sh", apacheLicenseHeader.prependIndent("# "))

        buildAndFail("check", "-s") {
            assertRatTask(FAILED)
            assertGeneratedAllReports()
        }
    }

    @Test
    fun `can declare what license families are approved`() {
        withBuildScript(
            """
            plugins {
                id("base")
                id("org.nosphere.apache.rat")
            }
            repositories {
                mavenCentral()
            }
            tasks.rat {
                verbose.set(true)
                approvedLicenses.add("MIT")
                excludes = ['build.gradle', 'settings.gradle', 'build/**', '.gradle/**', '.gradle-test-kit/**']
            }
            """
        )
        withFile("default-licensed.sh", apacheLicenseHeader.prependIndent("# "))

        buildAndFail("check", "-s") {
            assertRatTask(FAILED)
            assertGeneratedAllReports()
        }
    }

    private
    fun BuildResult.assertRatTask(outcome: TaskOutcome) {
        assertThat(outcomeOf(":rat"), equalTo(outcome))
    }

    private
    fun BuildResult.assertOutputContainsAuditFailureMessage(unApprovedLicenses: Int = 1) {
        assertThat(output, containsString("Apache Rat audit failure - $unApprovedLicenses unapproved license"))
        assertThat(output, containsString(htmlReportFile.absolutePath))
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

    private
    val apacheLicenseHeader = """
        Licensed to the Apache Software Foundation (ASF) under one
        or more contributor license agreements.  See the NOTICE file
        distributed with this work for additional information
        regarding copyright ownership.  The ASF licenses this file
        to you under the Apache License, Version 2.0 (the
        "License"); you may not use this file except in compliance
        with the License.  You may obtain a copy of the License at

          http://www.apache.org/licenses/LICENSE-2.0

        Unless required by applicable law or agreed to in writing,
        software distributed under the License is distributed on an
        "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
        KIND, either express or implied.  See the License for the
        specific language governing permissions and limitations
        under the License.
    """.trimIndent()
}
