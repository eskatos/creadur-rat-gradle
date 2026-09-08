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

import org.apache.rat.Report;
import org.apache.rat.api.RatException;
import org.apache.rat.document.impl.FileDocument;
import org.apache.rat.report.IReportable;
import org.apache.rat.report.RatReport;

import org.gradle.api.GradleException;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

class FilesReportable implements IReportable {

    private final List<File> files;

    private final File excludeFile;

    FilesReportable(List<File> files, File excludeFile) {
        this.files = files;
        this.excludeFile = excludeFile;
    }

    @Override
    public void run(RatReport report) throws RatException {
        FilenameFilter filter = excludeFileFilter();
        for (File file : files) {
            if (filter == null || filter.accept(file.getParentFile(), file.getName())) {
                report.report(new FileDocument(file));
            }
        }
    }

    private FilenameFilter excludeFileFilter() {
        if (excludeFile == null || !excludeFile.isFile()) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(excludeFile.toPath(), StandardCharsets.UTF_8)) {
                if (!line.trim().isEmpty()) {
                    lines.add(line);
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        if (lines.isEmpty()) {
            return null;
        }
        return createFilenameFilter(lines);
    }

    private FilenameFilter createFilenameFilter(List<String> lines) {
        try {
            Method parseExclusions = Report.class.getDeclaredMethod("parseExclusions", List.class);
            parseExclusions.setAccessible(true);
            return (FilenameFilter) parseExclusions.invoke(null, lines);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            throw new GradleException(ex.getMessage(), ex);
        }
    }
}
