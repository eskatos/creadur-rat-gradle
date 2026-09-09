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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
                "    substringMatcher(\"MYFOO\", \"Foo License\", \"" + Fixtures.FOO_MARKER + "\")",
                "    excludes = ['build.gradle', 'settings.gradle', 'build/**', '.gradle/**', '.gradle-test-kit/**']",
                "}"));
        withFile("default-licensed.txt", Fixtures.commentedApacheLicenseHeader());

        BuildResult result = buildAndFail("check", "-s");
        assertRatTask(result, FAILED);
        assertOutputContains(result, "Apache Rat audit failure - 1 unapproved license");
        assertGeneratedAllReports();
    }

    @Test
    public void disablingDefaultMatchersWithoutCustomMatchersIsAConfigurationError() {
        withRatBuildScript("    addDefaultMatchers.set(false)", "    failOnError.set(false)");
        withFile("default-licensed.txt", Fixtures.commentedApacheLicenseHeader());

        BuildResult result = buildAndFail("check");
        assertRatTask(result, FAILED);
        assertOutputContains(result, "addDefaultMatchers is false and no substringMatcher is declared");
        assertOutputDoesNotContain(result, "See file:");
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
    public void passesWhenTheFileSetHasNoStandardDocuments() {
        withRatBuildScript();
        withBinaryFile("image.png", Fixtures.PNG);

        assertRatTask(build("check"), SUCCESS);
        assertGeneratedAllReports();
    }

    @Test
    public void passesWhenTheFileSetIsEmpty() {
        withRatBuildScript();

        assertRatTask(build("check"), SUCCESS);
        assertGeneratedAllReports();
    }

    @Test
    public void customFamilyIsNotApprovedByDefault() {
        withRatBuildScript("    substringMatcher(\"MYFOO\", \"Foo License\", \"" + Fixtures.FOO_MARKER + "\")");
        withFile("foo-marker.txt", Fixtures.FOO_MARKER);

        BuildResult result = buildAndFail("check");
        assertRatTask(result, FAILED);
        assertOutputContains(result, "Apache Rat audit failure - 1 unapproved license");
    }

    @Test
    public void mitFileIsApprovedByDefault() {
        withRatBuildScript();
        withFile("mit-licensed.txt", Fixtures.MIT_LICENSE_TEXT);
        withFile("default-licensed.txt", Fixtures.commentedApacheLicenseHeader());

        assertRatTask(build("check"), SUCCESS);
        assertGeneratedAllReports();
    }

    @Test
    public void verboseListsUnapprovedFiles() {
        withRatBuildScript("    verbose.set(true)");
        withFile("no-license-file.txt", "Nothing here.");

        BuildResult result = buildAndFail("check");
        assertRatTask(result, FAILED);
        assertOutputContains(result, "Files with unapproved licenses");
        assertOutputContains(result, "no-license-file.txt");
    }

    @Test
    public void xmlReportIsXml() {
        withRatBuildScript();
        withFile("default-licensed.txt", Fixtures.commentedApacheLicenseHeader());

        assertRatTask(build("check"), SUCCESS);
        String xml = withoutXmlDeclaration(readReport("rat-report.xml"));
        assertTrue(xml.startsWith("<rat-report"), () -> "Expected an XML report, got: " + xml);
    }

    private static String withoutXmlDeclaration(String document) {
        String trimmed = document.trim();
        if (trimmed.startsWith("<?xml")) {
            return trimmed.substring(trimmed.indexOf("?>") + 2).trim();
        }
        return trimmed;
    }

    @Test
    public void plainReportNamesUnapprovedFile() {
        withRatBuildScript();
        withFile("no-license-file.txt", "Nothing here.");

        assertRatTask(buildAndFail("check"), FAILED);
        assertTrue(readReport("rat-report.txt").contains("no-license-file.txt"));
    }

    @Test
    public void customMatcherOnKnownFamilyIsUsed() {
        withRatBuildScript("    substringMatcher(\"MIT\", \"The MIT License\", \"" + Fixtures.FOO_MARKER + "\")");
        withFile("foo-marker.txt", Fixtures.FOO_MARKER);

        assertRatTask(build("check"), SUCCESS);
        assertGeneratedAllReports();
    }

    @Test
    public void twoMatchersOnTheSameCustomFamilyBothMatch() {
        withRatBuildScript(
                "    substringMatcher(\"MYFOO\", \"Foo License\", \"" + Fixtures.FOO_MARKER + "\")",
                "    substringMatcher(\"MYFOO\", \"Foo License\", \"" + Fixtures.BAR_MARKER + "\")");
        withFile("foo-marker.txt", Fixtures.FOO_MARKER);
        withFile("bar-marker.txt", Fixtures.BAR_MARKER);

        BuildResult result = buildAndFail("check");
        assertRatTask(result, FAILED);
        assertOutputContains(result, "Apache Rat audit failure - 2 unapproved licenses");
        assertFalse(readReport("rat-report.xml").contains("?????"), "Expected every file to match a license");
    }

    @Test
    public void scriptFilesAreAudited() {
        withRatBuildScript("    verbose.set(true)");
        withFile("script.sh", "echo unlicensed\n");
        withFile("script.bat", "@echo unlicensed\r\n");
        withFile("script.js", "console.log('unlicensed');\n");
        withFile("script.rs", "fn main() {}\n");
        withFile("run", "#!/bin/sh\necho unlicensed\n");

        BuildResult result = buildAndFail("check");
        assertRatTask(result, FAILED);
        assertOutputContains(result, "Apache Rat audit failure - 5 unapproved licenses");
    }

    @Test
    public void jsonStaysBinary() {
        withRatBuildScript();
        withFile("data.json", "{}\n");

        assertRatTask(build("check"), SUCCESS);
        assertGeneratedAllReports();
    }

    @Test
    public void bundlesRat017() {
        withRatBuildScript();
        withFile("default-licensed.txt", Fixtures.commentedApacheLicenseHeader());

        assertRatTask(build("check"), SUCCESS);
        assertEquals(
                "<version product=\"Apache Creadur RAT::Core\" vendor=\"Apache Software Foundation\""
                        + " version=\"0.17\"/>",
                versionElementOf(readReport("rat-report.xml")));
    }

    private static String versionElementOf(String xmlReport) {
        int start = xmlReport.indexOf("<version");
        int end = xmlReport.indexOf("/>", start);
        return start < 0 || end < 0 ? "(no version element)" : xmlReport.substring(start, end + 2);
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

    private void withRatBuildScript(String... taskConfiguration) {
        List<String> lines = new ArrayList<>(Arrays.asList(
                "plugins {",
                "    id(\"base\")",
                "    id(\"org.nosphere.apache.rat\")",
                "}",
                "tasks.rat {",
                "    excludes = ['build.gradle', 'settings.gradle', 'build/**', '.gradle/**', '.gradle-test-kit/**']"));
        lines.addAll(Arrays.asList(taskConfiguration));
        lines.add("}");
        withBuildScript(String.join("\n", lines));
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
