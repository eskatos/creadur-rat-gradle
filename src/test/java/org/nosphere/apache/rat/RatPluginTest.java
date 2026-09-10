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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.Test;

public class RatPluginTest extends AbstractPluginTest {

    @Test
    public void successUpToDateAndFromCache() throws IOException {
        withRatBuildScriptUsingJavaLauncher("    verbose.set(true)", "    exclude('guh/**', 'no-license-file.txt')");
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
        withRatBuildScript("    verbose.set(true)");
        withFile("no-license-file.txt", "Nothing here.");

        BuildResult result = buildAndFail("check");
        assertRatTask(result, FAILED);
        assertGeneratedAllReports();
        assertOutputContainsAuditFailureMessage(result);

        assertRatTask(buildAndFail("check"), FAILED);
    }

    @Test
    public void doNotFailButReportErrorsWhenFailOnErrorIsFalse() {
        withRatBuildScript("    verbose.set(true)", "    failOnError.set(false)");
        withFile("no-license-file.txt", "Nothing here.");

        BuildResult result = build("check");
        assertRatTask(result, SUCCESS);
        assertGeneratedAllReports();
        assertOutputContainsAuditFailureMessage(result);

        assertRatTask(build("check"), UP_TO_DATE);
    }

    @Test
    public void canDeclareCustomLicenseMatchers() {
        withRatBuildScript(
                "    verbose.set(true)",
                "    substringMatcher(\"MIT\", \"The MIT License\","
                        + " \"Permission is hereby granted, free of charge, to any person obtaining a copy\")");
        withFile(
                "substring-mit.txt", "// Permission is hereby granted, free of charge, to any person obtaining a copy");

        assertRatTask(build("check", "-s"), SUCCESS);
        assertGeneratedAllReports();
    }

    @Test
    public void canDisableDefaultLicenseMatchers() {
        withRatBuildScript(
                "    verbose.set(true)",
                "    addDefaultMatchers.set(false)",
                "    substringMatcher(\"MYFOO\", \"Foo License\", \"" + Fixtures.FOO_MARKER + "\")");
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
        withRatBuildScript("    verbose.set(true)", "    approvedLicenses.add(\"The MIT License\")");
        withFile("mit-licensed.txt", Fixtures.MIT_LICENSE_TEXT);
        withFile("default-licensed.txt", Fixtures.commentedApacheLicenseHeader());

        BuildResult result = buildAndFail("check", "-s");
        assertRatTask(result, FAILED);
        assertGeneratedAllReports();
        assertOutputContains(result, "Apache Rat audit failure - 1 unapproved license");
        assertOutputContains(result, "default-licensed.txt");
        assertOutputDoesNotContain(result, "mit-licensed.txt");
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
    public void xslStylesheetsAreAudited() {
        withRatBuildScript("    verbose.set(true)");
        withFile(
                "transform.xsl",
                "<?xml version=\"1.0\"?>\n<xsl:stylesheet version=\"1.0\""
                        + " xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"><xsl:template match=\"/\"/></xsl:stylesheet>\n");

        BuildResult result = buildAndFail("check");
        assertRatTask(result, FAILED);
        assertOutputContains(result, "Apache Rat audit failure - 1 unapproved license");
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

    @Test
    public void customFamilyCanBeApprovedByName() {
        withRatBuildScript(
                "    substringMatcher(\"MYFOO\", \"Foo License\", \"" + Fixtures.FOO_MARKER + "\")",
                "    approvedLicenses.add(\"Foo License\")");
        withFile("foo-marker.txt", Fixtures.FOO_MARKER);

        assertRatTask(build("check"), SUCCESS);
        assertGeneratedAllReports();
    }

    @Test
    public void approvedLicensesAcceptsCategory() {
        withRatBuildScript("    verbose.set(true)", "    approvedLicenses.add(\"MIT\")");
        withFile("mit-licensed.txt", Fixtures.MIT_LICENSE_TEXT);
        withFile("default-licensed.txt", Fixtures.commentedApacheLicenseHeader());

        BuildResult result = buildAndFail("check");
        assertRatTask(result, FAILED);
        assertOutputContains(result, "Apache Rat audit failure - 1 unapproved license");
        assertOutputContains(result, "default-licensed.txt");
        assertOutputDoesNotContain(result, "mit-licensed.txt");
    }

    @Test
    public void unknownApprovedLicenseFailsEvenWithFailOnErrorFalse() {
        withRatBuildScript("    failOnError.set(false)", "    approvedLicenses.add(\"Apache License Version 2.0\")");
        withFile("default-licensed.txt", Fixtures.commentedApacheLicenseHeader());

        BuildResult result = buildAndFail("check");
        assertRatTask(result, FAILED);
        assertOutputContains(result, "approvedLicenses");
        assertOutputContains(result, "Apache License Version 2.0");
        assertOutputContains(result, "[AL   ] Apache License");
        assertOutputContains(result, "[MIT  ] The MIT License");
        assertOutputContains(result, "failOnError does not apply to configuration errors");
        assertOutputDoesNotContain(result, "See file:");
    }

    @Test
    public void ambiguousApprovedLicenseNameFails() {
        withRatBuildScript(
                "    substringMatcher(\"MYMIT\", \"The MIT License\", \"" + Fixtures.FOO_MARKER + "\")",
                "    approvedLicenses.add(\"The MIT License\")");
        withFile("foo-marker.txt", Fixtures.FOO_MARKER);

        BuildResult result = buildAndFail("check");
        assertRatTask(result, FAILED);
        assertOutputContains(result, "Apache Rat configuration error");
        assertOutputContains(result, "'The MIT License' matches several categories: MIT, MYMIT");
        assertOutputContains(result, "Use the category instead");
        assertOutputDoesNotContain(result, "See file:");
    }

    @Test
    public void approvedLicensesResolvesAgainstDefaultsWhenMatchersAreDisabled() {
        withRatBuildScript(
                "    addDefaultMatchers.set(false)",
                "    substringMatcher(\"MYFOO\", \"Foo License\", \"" + Fixtures.FOO_MARKER + "\")",
                "    approvedLicenses.add(\"MIT\")");
        withFile("foo-marker.txt", Fixtures.FOO_MARKER);

        BuildResult result = buildAndFail("check");
        assertRatTask(result, FAILED);
        assertOutputContains(result, "Apache Rat audit failure - 1 unapproved license");
        assertOutputDoesNotContain(result, "Apache Rat configuration error");
    }

    @Test
    public void longSubstringMatcherCategoryFailsAtConfigurationTime() {
        withRatBuildScript(
                "    substringMatcher(\"BSD-3-Clause\", \"My BSD License\", \"" + Fixtures.FOO_MARKER + "\")");
        withFile("foo-marker.txt", Fixtures.FOO_MARKER);

        BuildResult result = buildAndFail("check");
        assertRatTaskDidNotRun(result);
        assertOutputContains(result, "BSD-3-Clause");
        assertOutputContains(result, "5 characters");
    }

    @Test
    public void substringMatcherCollidingWithKnownFamilyFails() {
        withRatBuildScript(
                "    failOnError.set(false)",
                "    substringMatcher(\"MIT\", \"My Own License\", \"" + Fixtures.FOO_MARKER + "\")");
        withFile("foo-marker.txt", Fixtures.FOO_MARKER);

        BuildResult result = buildAndFail("check");
        assertRatTask(result, FAILED);
        assertOutputContains(result, "Apache Rat configuration error");
        assertOutputContains(result, "MIT");
        assertOutputContains(result, "My Own License");
        assertOutputContains(result, "The MIT License");
        assertOutputContains(result, "failOnError does not apply to configuration errors");
        assertOutputDoesNotContain(result, "See file:");
    }

    @Test
    public void twoSubstringMatchersSharingACategoryFail() {
        withRatBuildScript(
                "    substringMatcher(\"MYFOO\", \"Foo License\", \"" + Fixtures.FOO_MARKER + "\")",
                "    substringMatcher(\"MYFOO\", \"Bar License\", \"" + Fixtures.BAR_MARKER + "\")");
        withFile("foo-marker.txt", Fixtures.FOO_MARKER);
        withFile("bar-marker.txt", Fixtures.BAR_MARKER);

        BuildResult result = buildAndFail("check");
        assertRatTask(result, FAILED);
        assertOutputContains(result, "Apache Rat configuration error");
        assertOutputContains(result, "MYFOO");
        assertOutputContains(result, "Foo License");
        assertOutputContains(result, "Bar License");
    }

    @Test
    public void collidingCategoryIsLegalWhenDefaultsAreDisabled() {
        withRatBuildScript(
                "    addDefaultMatchers.set(false)",
                "    substringMatcher(\"MIT\", \"My Own License\", \"" + Fixtures.FOO_MARKER + "\")",
                "    approvedLicenses.add(\"My Own License\")");
        withFile("foo-marker.txt", Fixtures.FOO_MARKER);

        assertRatTask(build("check"), SUCCESS);
        assertGeneratedAllReports();
    }

    @Test
    public void cleanRunPrintsNothingFromRat() {
        withRatBuildScript("    verbose.set(false)");
        withFile("default-licensed.txt", Fixtures.commentedApacheLicenseHeader());

        BuildResult result = build("check");
        assertRatTask(result, SUCCESS);
        assertOutputDoesNotContain(result, "Excluding");
        assertOutputDoesNotContain(result, "INFO:");
    }

    @Test
    public void verbosePrintsTheWorkerJvm() {
        withRatBuildScript("    verbose.set(true)");
        withFile("default-licensed.txt", Fixtures.commentedApacheLicenseHeader());

        BuildResult result = build("check");
        assertRatTask(result, SUCCESS);
        assertOutputContains(result, "Apache Rat runs on Java ");
    }

    @Test
    public void javaLauncherRunsTheWorkerOnThatJvm() {
        withRatBuildScriptUsingJavaLauncher("    verbose.set(true)");
        withFile("default-licensed.txt", Fixtures.commentedApacheLicenseHeader());

        BuildResult result = build("check");
        assertRatTask(result, SUCCESS);
        assertOutputContains(result, "Apache Rat runs on Java 17.");
        assertOutputContains(result, jdk17Home());
    }

    @Test
    public void workerRunsOnJava17WhenTheDaemonIsOlder() {
        withRatBuildScript("    verbose.set(true)");
        withFile("default-licensed.txt", Fixtures.commentedApacheLicenseHeader());

        BuildResult result = build("check");
        assertRatTask(result, SUCCESS);
        if (daemonRunsJava17()) {
            assertOutputContains(
                    result,
                    "Apache Rat runs on Java " + System.getProperty("java.version") + " ("
                            + System.getProperty("java.home") + ")");
        } else {
            assertOutputContains(result, "Apache Rat runs on Java 17.");
            assertOutputContains(result, jdk17Home());
        }
    }

    @Test
    public void missingJava17FailsNamingTheToolchain() {
        withRatBuildScript();
        withFile("default-licensed.txt", Fixtures.commentedApacheLicenseHeader());

        if (daemonRunsJava17()) {
            assertRatTask(buildWithoutJava17("check"), SUCCESS);
        } else {
            BuildResult result = buildAndFailWithoutJava17("check");
            assertNotEquals(SUCCESS, outcomeOf(result, ":rat"));
            assertOutputContains(result, "languageVersion=17");
        }
    }

    @Test
    public void missingJava17FailsEvenWithFailOnErrorFalse() {
        withRatBuildScript("    failOnError.set(false)");
        withFile("default-licensed.txt", Fixtures.commentedApacheLicenseHeader());

        if (daemonRunsJava17()) {
            assertRatTask(buildWithoutJava17("check"), SUCCESS);
        } else {
            BuildResult result = buildAndFailWithoutJava17("check");
            assertNotEquals(SUCCESS, outcomeOf(result, ":rat"));
            assertOutputContains(result, "languageVersion=17");
        }
    }

    @Test
    public void verbosePrintsLicenseFamilyTable() {
        withRatBuildScript(
                "    verbose.set(true)",
                "    substringMatcher(\"MYFOO\", \"Foo License\", \"" + Fixtures.FOO_MARKER + "\")");
        withFile("default-licensed.txt", Fixtures.commentedApacheLicenseHeader());

        BuildResult result = build("check");
        assertRatTask(result, SUCCESS);
        assertOutputContains(result, "License families:");
        assertOutputContains(result, "[MYFOO] Foo License - not approved");
        assertOutputContains(result, "[MIT  ] The MIT License - approved");
    }

    @Test
    public void verboseRevealsRatInfoMessages() {
        withRatBuildScript("    verbose.set(true)");
        withFile("default-licensed.txt", Fixtures.commentedApacheLicenseHeader());

        BuildResult result = build("check");
        assertRatTask(result, SUCCESS);
        assertOutputContains(result, "Excluding");
    }

    @Test
    public void reportDirIsNeverAudited() {
        withBuildScript(String.join(
                "\n",
                "plugins {",
                "    id(\"base\")",
                "    id(\"org.nosphere.apache.rat\")",
                "}",
                "tasks.rat {",
                "    exclude('build.gradle', 'settings.gradle', '.gradle-test-kit/**')",
                "}"));
        withFile("default-licensed.txt", Fixtures.commentedApacheLicenseHeader());

        assertRatTask(build("check"), SUCCESS);
        assertRatTask(build("check"), SUCCESS);
        assertFalse(readReport("rat-report.xml").contains("reports/rat/"), "Expected the reports to be left out");
    }

    @Test
    public void substringMatcherWithoutSubstringsFailsAtConfigurationTime() {
        withRatBuildScript("    substringMatcher(\"MYFOO\", \"Foo License\")");
        withFile("foo-marker.txt", Fixtures.FOO_MARKER);

        BuildResult result = buildAndFail("check");
        assertRatTaskDidNotRun(result);
        assertOutputContains(result, "Foo License");
        assertOutputContains(result, "declares no substring");
    }

    @Test
    public void auditFailureMessageRespectsQuiet() {
        withRatBuildScript("    verbose.set(true)", "    failOnError.set(false)");
        withFile("no-license-file.txt", "Nothing here.");

        BuildResult result = build("check", "-q");
        assertRatTask(result, SUCCESS);
        assertOutputDoesNotContain(result, "Apache Rat audit failure");
        assertOutputDoesNotContain(result, "Files with unapproved licenses");
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

    private static String versionElementOf(String xmlReport) {
        int start = xmlReport.indexOf("<version");
        int end = xmlReport.indexOf("/>", start);
        return start < 0 || end < 0 ? "(no version element)" : xmlReport.substring(start, end + 2);
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
        return reportFile("rat-report.xml");
    }

    private File plainReportFile() {
        return reportFile("rat-report.txt");
    }

    private File htmlReportFile() {
        return reportFile("index.html");
    }

    private String relativeToRootDir(File file) {
        return getRootDir().toPath().relativize(file.toPath()).toString();
    }
}
