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

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

public class MultiProjectTest extends AbstractPluginTest {

    private static final List<String> RAT_TASKS = Arrays.asList(":rat", ":sub1:rat", ":sub2:rat");

    @Test
    public void auditsEveryProject() {
        withFile("settings.gradle", "include('sub1', 'sub2')");
        withBuildScript(ratBuildScript("'sub1/**', 'sub2/**'"));
        withFile("sub1/build.gradle", ratBuildScript(""));
        withFile("sub2/build.gradle", ratBuildScript(""));
        withFile("default-licensed.txt", Fixtures.commentedApacheLicenseHeader());
        withFile("sub1/default-licensed.txt", Fixtures.commentedApacheLicenseHeader());
        withFile("sub2/default-licensed.txt", Fixtures.commentedApacheLicenseHeader());

        BuildResult first = build("check");
        assertRatTasks(first, SUCCESS);
        assertOutputDoesNotContain(first, "cannot access");
        assertOutputContains(first, "Configuration cache entry stored.");

        BuildResult second = build("check");
        assertRatTasks(second, UP_TO_DATE);
        assertOutputContains(second, "Reusing configuration cache.");
    }

    @Test
    public void crossProjectAccessFailsUnderIsolatedProjects() {
        withFile("settings.gradle", "include('sub1', 'sub2')");
        withBuildScript(ratBuildScript("'sub1/**', 'sub2/**'"));
        withFile("sub1/build.gradle", ratBuildScript("") + "\nprintln(rootProject.version)\n");
        withFile("sub2/build.gradle", ratBuildScript(""));
        withFile("default-licensed.txt", Fixtures.commentedApacheLicenseHeader());
        withFile("sub1/default-licensed.txt", Fixtures.commentedApacheLicenseHeader());
        withFile("sub2/default-licensed.txt", Fixtures.commentedApacheLicenseHeader());

        if (isolatedProjectsEnabled()) {
            BuildResult result = buildAndFail("check");
            assertTrue(
                    result.getOutput().toLowerCase(Locale.ROOT).contains("cannot access"),
                    "Expected the cross-project access to be rejected");
        } else {
            assertRatTasks(build("check"), SUCCESS);
        }
    }

    private void assertRatTasks(BuildResult result, TaskOutcome outcome) {
        for (String path : RAT_TASKS) {
            assertEquals(outcome, outcomeOf(result, path), path);
        }
    }

    private static String ratBuildScript(String extraExcludes) {
        return String.join(
                "\n",
                "plugins {",
                "    id(\"base\")",
                "    id(\"org.nosphere.apache.rat\")",
                "}",
                "tasks.rat {",
                "    excludes = ['build.gradle', 'settings.gradle', 'build/**', '.gradle/**', '.gradle-test-kit/**'"
                        + (extraExcludes.isEmpty() ? "" : ", " + extraExcludes) + "]",
                "}");
    }
}
