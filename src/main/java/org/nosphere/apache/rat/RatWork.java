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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.apache.commons.io.function.IOSupplier;
import org.apache.rat.ReportConfiguration;
import org.apache.rat.Reporter;
import org.apache.rat.analysis.TikaProcessor;
import org.apache.rat.api.Document;
import org.apache.rat.api.RatException;
import org.apache.rat.commandline.StyleSheets;
import org.apache.rat.report.claim.ClaimStatistic;
import org.apache.rat.utils.DefaultLog;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.logging.ConsoleRenderer;
import org.gradle.workers.WorkAction;

public abstract class RatWork implements WorkAction<RatWorkSpec> {

    private static final Logger LOGGER = Logging.getLogger(RatWork.class);

    private static final List<String> SCRIPT_MEDIA_TYPES = Arrays.asList(
            "application/x-sh", "application/x-bat", "application/javascript", "application/rls-services+xml");

    @Override
    public void execute() {
        RatWorkSpec spec = getParameters();
        boolean verbose = spec.getVerbose().get();
        DefaultLog.setInstance(new RatLogBridge(LOGGER, verbose));
        restoreScriptDocumentTypes();
        File reportDir = spec.getReportDirectory().getAsFile().get();
        reportDir.mkdirs();
        RatConfigurationBuilder builder = new RatConfigurationBuilder(spec);
        ReportConfiguration config = builder.build();
        if (verbose) {
            LOGGER.lifecycle("License families:\n" + String.join("\n", builder.licenseFamilyTable()));
        }
        Reporter reporter = new Reporter(config);
        ClaimStatistic stats = runAudit(reporter);
        report(reporter, reportDir);
        verdict(spec, config, stats, reporter, reportDir);
    }

    private static void verdict(
            RatWorkSpec spec, ReportConfiguration config, ClaimStatistic stats, Reporter reporter, File reportDir) {
        int unapproved = stats.getCounter(ClaimStatistic.Counter.UNAPPROVED);
        if (config.getClaimValidator().isValid(ClaimStatistic.Counter.UNAPPROVED, unapproved)) {
            return;
        }
        if (spec.getVerbose().get()) {
            LOGGER.lifecycle(unapprovedFilesListing(reporter));
        }
        String message = "Apache Rat audit failure - " + unapproved + " unapproved license"
                + (unapproved > 1 ? "s" : "") + "\n"
                + "\tSee " + new ConsoleRenderer().asClickableFileUrl(new File(reportDir, "index.html"));
        if (spec.getFailOnError().get()) {
            throw new GradleException(message);
        }
        LOGGER.warn(message);
    }

    private static ClaimStatistic runAudit(Reporter reporter) {
        try {
            return reporter.execute();
        } catch (RatException ex) {
            throw new GradleException(ex.getMessage(), ex);
        }
    }

    private static void report(Reporter reporter, File reportDir) {
        writeReport(reporter, StyleSheets.XML.getStyleSheet(), new File(reportDir, "rat-report.xml"));
        writeReport(reporter, StyleSheets.PLAIN.getStyleSheet(), new File(reportDir, "rat-report.txt"));
        writeHtmlReport(reporter, new File(reportDir, "index.html"));
    }

    private static void writeReport(Reporter reporter, IOSupplier<InputStream> stylesheet, File target) {
        try {
            reporter.output(stylesheet, () -> new FileOutputStream(target));
        } catch (RatException ex) {
            throw new GradleException(ex.getMessage(), ex);
        }
    }

    private static String unapprovedFilesListing(Reporter reporter) {
        ByteArrayOutputStream listing = new ByteArrayOutputStream();
        try {
            reporter.output(StyleSheets.UNAPPROVED_LICENSES.getStyleSheet(), () -> listing);
        } catch (RatException ex) {
            throw new GradleException(ex.getMessage(), ex);
        }
        return new String(listing.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void writeHtmlReport(Reporter reporter, File target) {
        try (InputStream stylesheet = RatWork.class.getResourceAsStream("apache-rat-output-to-html.xsl");
                OutputStream output = new FileOutputStream(target)) {
            Transformer transformer = TransformerFactory.newInstance().newTransformer(new StreamSource(stylesheet));
            transformer.transform(new DOMSource(reporter.getDocument()), new StreamResult(output));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (TransformerException ex) {
            throw new GradleException(ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static void restoreScriptDocumentTypes() {
        try {
            Field documentTypeMap = TikaProcessor.class.getDeclaredField("DOCUMENT_TYPE_MAP");
            documentTypeMap.setAccessible(true);
            Map<String, Document.Type> typesByMediaType = (Map<String, Document.Type>) documentTypeMap.get(null);
            for (String mediaType : SCRIPT_MEDIA_TYPES) {
                typesByMediaType.put(mediaType, Document.Type.STANDARD);
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            throw new GradleException("Unable to register script media types as text documents with Apache Rat", ex);
        }
    }
}
