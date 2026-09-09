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
package org.nosphere.apache.rat;

import static org.gradle.testkit.runner.TaskOutcome.FAILED;
import static org.gradle.testkit.runner.TaskOutcome.FROM_CACHE;
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

public class RatPluginTest extends AbstractPluginTest {

    @Test
    public void successUpToDateAndFromCache() throws IOException {
        withBuildScript(String.join(
                "\n",
                "plugins {",
                "    id(\"base\")",
                "    id(\"org.nosphere.apache.rat\")",
                "}",
                "tasks.rat {",
                "    verbose.set(true)",
                "    excludes = [",
                "        'build.gradle', 'settings.gradle', 'build/**', '.gradle/**', '.gradle-test-kit/**',",
                "    ]",
                "    exclude(",
                "        'guh/**',",
                "        'no-license-file.txt'",
                "    )",
                "}"));
        withFile("no-license-file.txt", "Nothing here.");

        assertRatTask(build("check"), SUCCESS);
        assertGeneratedAllReports();

        assertRatTask(build("check"), UP_TO_DATE);

        String gradleUserHome = new File(getRootDir(), "guh").getCanonicalPath();

        assertRatTask(build("clean", "check", "--build-cache", "-g", gradleUserHome), SUCCESS);

        assertRatTask(build("clean", "check", "--build-cache", "-g", gradleUserHome), FROM_CACHE);
        assertGeneratedAllReports();
    }

    @Test
    public void failTheBuildWhenFindingAFileWithUnapprovedOrUnknownLicense() {
        withBuildScript(String.join(
                "\n",
                "plugins {",
                "    id(\"base\")",
                "    id(\"org.nosphere.apache.rat\")",
                "}",
                "tasks.rat {",
                "    verbose.set(true)",
                "    excludes = [",
                "        'build.gradle', 'settings.gradle', 'build/**', '.gradle/**', '.gradle-test-kit/**'",
                "    ]",
                "}"));
        withFile("no-license-file.txt", "Nothing here.");

        BuildResult result = buildAndFail("check");
        assertRatTask(result, FAILED);
        assertGeneratedAllReports();
        assertOutputContainsAuditFailureMessage(result);

        assertRatTask(buildAndFail("check"), FAILED);
    }

    @Test
    public void doNotFailButReportErrorsWhenFailOnErrorIsFalse() {
        withBuildScript(String.join(
                "\n",
                "plugins {",
                "    id(\"base\")",
                "    id(\"org.nosphere.apache.rat\")",
                "}",
                "tasks.rat {",
                "    verbose.set(true)",
                "    failOnError.set(false)",
                "    excludes = [",
                "        'build.gradle', 'settings.gradle', 'build/**', '.gradle/**', '.gradle-test-kit/**'",
                "    ]",
                "}"));
        withFile("no-license-file.txt", "Nothing here.");

        BuildResult result = build("check");
        assertRatTask(result, SUCCESS);
        assertGeneratedAllReports();
        assertOutputContainsAuditFailureMessage(result);

        assertRatTask(build("check"), UP_TO_DATE);
    }

    @Test
    public void canDeclareCustomLicenseMatchers() {
        withBuildScript(String.join(
                "\n",
                "plugins {",
                "    id(\"base\")",
                "    id(\"org.nosphere.apache.rat\")",
                "}",
                "tasks.rat {",
                "    verbose.set(true)",
                "    excludes = ['build.gradle', 'settings.gradle', 'build/**', '.gradle/**', '.gradle-test-kit/**']",
                "    substringMatcher(\"MIT\", \"The MIT License\","
                        + " \"Permission is hereby granted, free of charge, to any person obtaining a copy\")",
                "}"));
        withFile(
                "substring-mit.txt", "// Permission is hereby granted, free of charge, to any person obtaining a copy");

        assertRatTask(build("check", "-s"), SUCCESS);
        assertGeneratedAllReports();
    }

    @Test
    public void canDisableDefaultLicenseMatchers() {
        withBuildScript(String.join(
                "\n",
                "plugins {",
                "    id(\"base\")",
                "    id(\"org.nosphere.apache.rat\")",
                "}",
                "tasks.rat {",
                "    verbose.set(true)",
                "    addDefaultMatchers.set(false)",
                "    excludes = ['build.gradle', 'settings.gradle', 'build/**', '.gradle/**', '.gradle-test-kit/**']",
                "}"));
        withFile("default-licensed.txt", Fixtures.commentedApacheLicenseHeader());

        assertRatTask(buildAndFail("check", "-s"), FAILED);
        assertGeneratedAllReports();
    }

    @Test
    public void canDeclareWhatLicenseFamiliesAreApproved() {
        withBuildScript(String.join(
                "\n",
                "plugins {",
                "    id(\"base\")",
                "    id(\"org.nosphere.apache.rat\")",
                "}",
                "tasks.rat {",
                "    verbose.set(true)",
                "    approvedLicenses.add(\"MIT\")",
                "    excludes = ['build.gradle', 'settings.gradle', 'build/**', '.gradle/**', '.gradle-test-kit/**']",
                "}"));
        withFile("default-licensed.txt", Fixtures.commentedApacheLicenseHeader());

        assertRatTask(buildAndFail("check", "-s"), FAILED);
        assertGeneratedAllReports();
    }

    @Test
    public void stylesheetPropertyIsGone() {
        withBuildScript(String.join(
                "\n",
                "plugins {",
                "    id(\"base\")",
                "    id(\"org.nosphere.apache.rat\")",
                "}",
                "tasks.rat {",
                "    stylesheet.set(file(\"custom.xsl\"))",
                "}"));

        BuildResult result = buildAndFail("rat");
        assertRatTaskDidNotRun(result);
        assertOutputContains(result, "unknown property 'stylesheet'");
    }

    @Test
    public void excludeFilePropertyIsGone() {
        withBuildScript(String.join(
                "\n",
                "plugins {",
                "    id(\"base\")",
                "    id(\"org.nosphere.apache.rat\")",
                "}",
                "tasks.rat {",
                "    excludeFile.set(file(\".rat-excludes.txt\"))",
                "}"));

        BuildResult result = buildAndFail("rat");
        assertRatTaskDidNotRun(result);
        assertOutputContains(result, "unknown property 'excludeFile'");
    }

    /**
     * Regression test for https://github.com/eskatos/creadur-rat-gradle/issues/23
     */
    @Test
    public void runWithTheTaskThatMarkedNotCompatibleWithConfigurationCache() {
        String someTask = isGreaterOrEqualThan(gradleVersion, "7.4")
                ? String.join(
                        "\n",
                        "tasks.register(\"someTask\") {",
                        "    doFirst {",
                        "        logger.log(LogLevel.WARN, \"This task is not compatible with configuration cache.\")",
                        "    }",
                        "    notCompatibleWithConfigurationCache(\"\")",
                        "}",
                        "tasks.check {",
                        "    dependsOn(\"someTask\")",
                        "}")
                : "";

        withBuildScript(String.join(
                "\n",
                "plugins {",
                "    id(\"base\")",
                "    id(\"org.nosphere.apache.rat\")",
                "}",
                someTask,
                "tasks.rat {",
                "    verbose.set(true)",
                "    excludes = [",
                "        'build.gradle', 'settings.gradle', 'build/**', '.gradle/**', '.gradle-test-kit/**',",
                "    ]",
                "    exclude(",
                "        'guh/**',",
                "        'no-license-file.txt'",
                "    )",
                "}"));
        withFile("no-license-file.txt", "Nothing here.");

        assertRatTask(build("check"), SUCCESS);
    }

    private void assertRatTask(BuildResult result, TaskOutcome outcome) {
        assertEquals(outcome, outcomeOf(result, ":rat"));
    }

    private void assertOutputContainsAuditFailureMessage(BuildResult result) {
        assertOutputContains(result, "Apache Rat audit failure - 1 unapproved license");
        assertOutputContains(result, relativeToRootDir(htmlReportFile()).replace("\\", "/"));
    }

    private void assertGeneratedAllReports() {
        assertTrue(xmlReportFile().isFile());
        assertTrue(plainReportFile().isFile());
        assertTrue(htmlReportFile().isFile());
    }

    private File xmlReportFile() {
        return new File(getRootDir(), "build/reports/rat/rat-report.xml");
    }

    private File plainReportFile() {
        return new File(getRootDir(), "build/reports/rat/rat-report.txt");
    }

    private File htmlReportFile() {
        return new File(getRootDir(), "build/reports/rat/index.html");
    }

    private String relativeToRootDir(File file) {
        return getRootDir().toPath().relativize(file.toPath()).toString();
    }
}
