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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.gradle.api.JavaVersion;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.gradle.util.GradleVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

public abstract class AbstractPluginTest {

    protected final GradleVersion gradleVersion = GradleVersion.version(testedGradleVersion());

    private final boolean configurationCache = isGreaterOrEqualThan(gradleVersion, "6.3");

    @TempDir(cleanup = CleanupMode.NEVER)
    File tmpDir;

    @BeforeEach
    public void setup() {
        System.out.println("Gradle " + gradleVersion + " on Java " + JavaVersion.current()
                + " with Configuration Cache = " + configurationCache);
        System.out.println();
        withFile("settings.gradle", "");
    }

    @AfterEach
    public void deleteRootDir() {
        deleteRecursivelyIgnoringFailures(tmpDir.toPath());
    }

    protected File getRootDir() {
        return tmpDir;
    }

    protected void withFile(String path, String text) {
        withBinaryFile(path, text.getBytes(StandardCharsets.UTF_8));
    }

    protected void withBinaryFile(String path, byte[] bytes) {
        try {
            Files.write(new File(getRootDir(), path).toPath(), bytes);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    protected void withBuildScript(String text) {
        withFile("build.gradle", text);
    }

    protected void withRatBuildScript(String... taskConfiguration) {
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

    protected File reportFile(String name) {
        return new File(getRootDir(), "build/reports/rat/" + name);
    }

    protected String readReport(String name) {
        try {
            return new String(Files.readAllBytes(reportFile(name).toPath()), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    protected BuildResult build(String... arguments) {
        return gradleRunnerFor(arguments).build();
    }

    protected BuildResult buildAndFail(String... arguments) {
        return gradleRunnerFor(arguments).buildAndFail();
    }

    protected TaskOutcome outcomeOf(BuildResult result, String path) {
        BuildTask task = result.task(path);
        return task == null ? null : task.getOutcome();
    }

    protected void assertRatTask(BuildResult result, TaskOutcome outcome) {
        assertEquals(outcome, outcomeOf(result, ":rat"));
    }

    protected void assertRatTaskDidNotRun(BuildResult result) {
        assertNull(outcomeOf(result, ":rat"));
    }

    protected void assertOutputContains(BuildResult result, String expected) {
        assertTrue(result.getOutput().contains(expected), () -> "Expected build output to contain: " + expected);
    }

    protected void assertOutputDoesNotContain(BuildResult result, String unexpected) {
        assertFalse(
                result.getOutput().contains(unexpected), () -> "Expected build output not to contain: " + unexpected);
    }

    protected static boolean isGreaterOrEqualThan(GradleVersion gradleVersion, String version) {
        return gradleVersion.compareTo(GradleVersion.version(version)) >= 0;
    }

    private GradleRunner gradleRunnerFor(String... arguments) {
        List<String> allArguments = new ArrayList<>(Arrays.asList(arguments));
        allArguments.addAll(extraArguments());
        return GradleRunner.create()
                .withGradleVersion(gradleVersion.getVersion())
                .withPluginClasspath()
                .forwardOutput()
                .withProjectDir(getRootDir())
                .withArguments(allArguments);
    }

    private List<String> extraArguments() {
        List<String> arguments = new ArrayList<>();
        arguments.add("--stacktrace");
        arguments.add("--warning-mode=fail");
        if (configurationCache) {
            arguments.add("--configuration-cache");
        }
        return arguments;
    }

    private static void deleteRecursivelyIgnoringFailures(Path path) {
        try (Stream<Path> paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        } catch (IOException ignored) {
        }
    }

    private static String testedGradleVersion() {
        String version = System.getProperty("testedGradleVersion");
        if (version == null) {
            throw new IllegalStateException("System Property `testedGradleVersion` is not set!");
        }
        return version;
    }
}
