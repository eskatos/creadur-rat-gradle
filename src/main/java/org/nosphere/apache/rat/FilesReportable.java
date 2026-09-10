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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import org.apache.rat.api.Document;
import org.apache.rat.api.RatException;
import org.apache.rat.document.DocumentName;
import org.apache.rat.document.DocumentNameMatcher;
import org.apache.rat.report.IReportable;
import org.apache.rat.report.RatReport;
import org.gradle.api.GradleException;

class FilesReportable implements IReportable {

    private final Path baseDir;

    private final DocumentName baseName;

    private final List<File> files;

    FilesReportable(File baseDir, List<File> files) {
        this.baseDir = baseDir.toPath();
        this.baseName = DocumentName.builder(baseDir).build();
        this.files = files;
    }

    // Rat's walker catches RatException, logs it, and skips all subsequent files silently.
    // Rethrow unchecked to prevent that.
    @Override
    public void run(RatReport report) {
        for (File file : files) {
            try {
                report.report(new ReportedFile(documentNameOf(file), file));
            } catch (RatException ex) {
                throw new GradleException("Apache Rat failed to audit " + file, ex);
            }
        }
    }

    @Override
    public DocumentName getName() {
        return baseName;
    }

    private DocumentName documentNameOf(File file) {
        return baseName.resolve(baseDir.relativize(file.toPath()).toString());
    }

    private static final class ReportedFile extends Document {

        private final File file;

        ReportedFile(DocumentName name, File file) {
            super(name, DocumentNameMatcher.MATCHES_ALL);
            this.file = file;
        }

        @Override
        public InputStream inputStream() throws IOException {
            return Files.newInputStream(file.toPath());
        }

        @Override
        public boolean isDirectory() {
            return false;
        }

        @Override
        public SortedSet<Document> listChildren() {
            return Collections.emptySortedSet();
        }
    }
}
