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

import static org.nosphere.apache.rat.ConfigurationErrors.configurationError;

import groovy.lang.Closure;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

@CacheableTask
public class RatTask extends DefaultTask implements PatternFilterable {

    private static final String RAT_VERSION = "0.17";

    private final PatternSet patternSet = new PatternSet().exclude("**/.gradle/**");

    private final ObjectFactory objects;

    private final WorkerExecutor workerExecutor;

    private final Property<Boolean> verbose;

    private final Property<Boolean> failOnError;

    private final DirectoryProperty inputDir;

    private final Property<Boolean> addDefaultMatchers;

    private final ListProperty<SubstringMatcher> substringMatchers;

    private final ListProperty<String> approvedLicenses;

    private final DirectoryProperty reportDir;

    private final FileCollection ratClasspath;

    @Inject
    public RatTask(ObjectFactory objects, ProjectLayout layout, WorkerExecutor workerExecutor) {
        this.objects = objects;
        this.workerExecutor = workerExecutor;

        this.verbose = objects.property(Boolean.class);
        this.verbose.set(false);

        this.failOnError = objects.property(Boolean.class);
        this.failOnError.set(true);

        this.inputDir = objects.directoryProperty();
        this.inputDir.set(layout.getProjectDirectory());

        this.addDefaultMatchers = objects.property(Boolean.class);
        this.addDefaultMatchers.set(true);

        this.substringMatchers = objects.listProperty(SubstringMatcher.class);
        this.substringMatchers.set(Collections.<SubstringMatcher>emptyList());

        this.approvedLicenses = objects.listProperty(String.class);
        this.approvedLicenses.set(Collections.<String>emptyList());

        this.reportDir = objects.directoryProperty();
        this.reportDir.set(getProject()
                .getExtensions()
                .getByType(ReportingExtension.class)
                .getBaseDirectory()
                .dir(getName()));

        ScriptHandler buildscript = getProject().getBuildscript();
        DependencyHandler dependencies = buildscript.getDependencies();
        ConfigurableFileCollection ratClasspath = objects.fileCollection();
        ratClasspath.from(buildscript
                .getConfigurations()
                .detachedConfiguration(dependencies.create("org.apache.rat:apache-rat-core:" + RAT_VERSION)));
        this.ratClasspath = ratClasspath;
    }

    @Console
    public Property<Boolean> getVerbose() {
        return verbose;
    }

    @Input
    public Property<Boolean> getFailOnError() {
        return failOnError;
    }

    @Internal
    public DirectoryProperty getInputDir() {
        return inputDir;
    }

    @Input
    public Property<Boolean> getAddDefaultMatchers() {
        return addDefaultMatchers;
    }

    @Nested
    public ListProperty<SubstringMatcher> getSubstringMatchers() {
        return substringMatchers;
    }

    public void substringMatcher(String licenseFamilyCategory, String licenseFamilyName, String... substrings) {
        substringMatchers.add(
                new SubstringMatcher(licenseFamilyCategory, licenseFamilyName, Arrays.asList(substrings)));
    }

    @Input
    public ListProperty<String> getApprovedLicenses() {
        return approvedLicenses;
    }

    public void approvedLicense(String familyNameOrCategory) {
        approvedLicenses.add(familyNameOrCategory);
    }

    @Override
    @Internal
    public Set<String> getIncludes() {
        return patternSet.getIncludes();
    }

    @Override
    @Internal
    public Set<String> getExcludes() {
        return patternSet.getExcludes();
    }

    @Override
    public PatternFilterable setIncludes(Iterable<String> includes) {
        patternSet.setIncludes(includes);
        return this;
    }

    @Override
    public PatternFilterable setExcludes(Iterable<String> excludes) {
        patternSet.setExcludes(excludes);
        return this;
    }

    @Override
    public PatternFilterable include(String... includes) {
        patternSet.include(includes);
        return this;
    }

    @Override
    public PatternFilterable include(Iterable<String> includes) {
        patternSet.include(includes);
        return this;
    }

    @Override
    public PatternFilterable include(Spec<FileTreeElement> includeSpec) {
        patternSet.include(includeSpec);
        return this;
    }

    @Override
    public PatternFilterable include(@SuppressWarnings("rawtypes") Closure includeSpec) {
        patternSet.include(includeSpec);
        return this;
    }

    @Override
    public PatternFilterable exclude(String... excludes) {
        patternSet.exclude(excludes);
        return this;
    }

    @Override
    public PatternFilterable exclude(Iterable<String> excludes) {
        patternSet.exclude(excludes);
        return this;
    }

    @Override
    public PatternFilterable exclude(Spec<FileTreeElement> excludeSpec) {
        patternSet.exclude(excludeSpec);
        return this;
    }

    @Override
    public PatternFilterable exclude(@SuppressWarnings("rawtypes") Closure excludeSpec) {
        patternSet.exclude(excludeSpec);
        return this;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileTree getInputFiles() {
        ConfigurableFileTree fileTree = objects.fileTree();
        fileTree.from(inputDir.get().getAsFile());
        if (!patternSet.isEmpty()) {
            fileTree.include(patternSet.getAsSpec());
        }
        excludeReportDir(fileTree);
        return fileTree;
    }

    private void excludeReportDir(ConfigurableFileTree fileTree) {
        Path input = inputDir.get().getAsFile().toPath().toAbsolutePath().normalize();
        Path report = reportDir.get().getAsFile().toPath().toAbsolutePath().normalize();
        if (report.startsWith(input)) {
            String relative = input.relativize(report).toString().replace(File.separatorChar, '/');
            fileTree.exclude(relative.isEmpty() ? "**" : relative + "/**");
        }
    }

    @OutputDirectory
    public DirectoryProperty getReportDir() {
        return reportDir;
    }

    @InputFiles
    @Classpath
    protected FileCollection getRatClasspath() {
        return ratClasspath;
    }

    @TaskAction
    public void rat() {
        requireAtLeastOneLicenseMatcher();
        WorkQueue workQueue =
                workerExecutor.processIsolation(spec -> spec.getClasspath().from(ratClasspath));
        FileTree inputFiles = getInputFiles();
        workQueue.submit(RatWork.class, parameters -> {
            parameters.getVerbose().set(verbose);
            parameters.getFailOnError().set(failOnError);
            parameters.getAddDefaultMatchers().set(addDefaultMatchers);
            parameters.getSubstringMatchers().set(substringMatchers);
            parameters.getApprovedLicenses().set(approvedLicenses);
            parameters.getInputDir().set(inputDir);
            parameters.getInputFiles().from(inputFiles);
            parameters.getReportDir().set(reportDir);
        });
    }

    private void requireAtLeastOneLicenseMatcher() {
        if (!addDefaultMatchers.get() && substringMatchers.get().isEmpty()) {
            throw configurationError("addDefaultMatchers is false and no substringMatcher is declared, so no"
                    + " license can be recognized. Declare a substringMatcher or set addDefaultMatchers to true.");
        }
    }
}
