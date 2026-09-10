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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import java.util.Collections;
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

    static final int RAT_JAVA_VERSION = RatTask.RAT_JAVA_VERSION;

    static final JavaVersion RAT_JAVA = JavaVersion.toVersion(RAT_JAVA_VERSION);

    protected final GradleVersion gradleVersion = GradleVersion.version(testedGradleVersion());

    @TempDir(cleanup = CleanupMode.NEVER)
    File tmpDir;

    @BeforeEach
    public void setup() {
        System.out.println("Gradle " + gradleVersion + " on Java " + JavaVersion.current());
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
        withRatBuildScript(Collections.<String>emptyList(), Arrays.asList(taskConfiguration));
    }

    protected void withRatBuildScriptUsingJavaLauncher(int javaVersion, String... taskConfiguration) {
        List<String> configuration = new ArrayList<>();
        configuration.add("    javaLauncher.set(javaToolchains.launcherFor {"
                + " it.languageVersion.set(JavaLanguageVersion.of(" + javaVersion + ")) })");
        configuration.addAll(Arrays.asList(taskConfiguration));
        withRatBuildScript(Collections.singletonList("java-base"), configuration);
    }

    private void withRatBuildScript(List<String> extraPlugins, List<String> taskConfiguration) {
        List<String> lines = new ArrayList<>();
        lines.add("plugins {");
        lines.add("    id(\"base\")");
        for (String plugin : extraPlugins) {
            lines.add("    id(\"" + plugin + "\")");
        }
        lines.add("    id(\"org.nosphere.apache.rat\")");
        lines.add("}");
        lines.add("tasks.rat {");
        lines.add(
                "    excludes = ['build.gradle', 'settings.gradle', 'build/**', '.gradle/**', '.gradle-test-kit/**']");
        lines.addAll(taskConfiguration);
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
        return gradleRunnerFor(installedJdkHomes(), true, arguments).build();
    }

    protected BuildResult buildAndFail(String... arguments) {
        return gradleRunnerFor(installedJdkHomes(), true, arguments).buildAndFail();
    }

    protected BuildResult buildWithoutToolchains(String... arguments) {
        return gradleRunnerFor("", true, arguments).build();
    }

    protected BuildResult buildAndFailWithoutToolchains(String... arguments) {
        return gradleRunnerFor("", true, arguments).buildAndFail();
    }

    protected BuildResult buildAndFailWithoutToolchainsNorConfigurationCache(String... arguments) {
        return gradleRunnerFor("", false, arguments).buildAndFail();
    }

    protected static boolean daemonRunsRatJavaOrLater() {
        return JavaVersion.current().isCompatibleWith(RAT_JAVA);
    }

    protected static String jdkHome(int javaVersion) {
        return requiredSystemProperty("jdkHome." + javaVersion);
    }

    protected static String ratJdkHome() {
        return jdkHome(RAT_JAVA_VERSION);
    }

    private static String installedJdkHomes() {
        List<String> homes = new ArrayList<>();
        for (String name : System.getProperties().stringPropertyNames()) {
            if (name.startsWith("jdkHome.")) {
                homes.add(System.getProperty(name));
            }
        }
        return String.join(",", homes);
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

    protected void assertRatTaskDidNotSucceed(BuildResult result) {
        assertNotEquals(TaskOutcome.SUCCESS, outcomeOf(result, ":rat"));
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

    private GradleRunner gradleRunnerFor(
            String javaInstallationPaths, boolean configurationCache, String... arguments) {
        List<String> allArguments = new ArrayList<>(Arrays.asList(arguments));
        allArguments.addAll(extraArguments(javaInstallationPaths, configurationCache));
        return GradleRunner.create()
                .withGradleVersion(gradleVersion.getVersion())
                .withPluginClasspath()
                .forwardOutput()
                .withProjectDir(getRootDir())
                .withArguments(allArguments);
    }

    private List<String> extraArguments(String javaInstallationPaths, boolean configurationCache) {
        List<String> arguments = new ArrayList<>();
        arguments.add("--stacktrace");
        arguments.add("--warning-mode=fail");
        arguments.add(toolchainProperty("auto-detect", "false"));
        arguments.add(toolchainProperty("auto-download", "false"));
        arguments.add(toolchainProperty("paths", javaInstallationPaths));
        if (configurationCache) {
            arguments.add("--configuration-cache");
        }
        return arguments;
    }

    private String toolchainProperty(String name, String value) {
        String prefix = isGreaterOrEqualThan(gradleVersion, "9.7") ? "-D" : "-P";
        return prefix + "org.gradle.java.installations." + name + "=" + value;
    }

    private static String requiredSystemProperty(String name) {
        String value = System.getProperty(name);
        if (value == null) {
            throw new IllegalStateException("System Property `" + name + "` is not set!");
        }
        return value;
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
