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
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.apache.rat.Defaults;
import org.apache.rat.ReportConfiguration;
import org.apache.rat.analysis.IHeaderMatcher;
import org.apache.rat.analysis.util.HeaderMatcherMultiplexer;
import org.apache.rat.anttasks.SubstringLicenseMatcher;
import org.apache.rat.api.RatException;
import org.apache.rat.license.ILicenseFamily;
import org.apache.rat.license.SimpleLicenseFamily;
import org.apache.rat.report.RatReport;
import org.apache.rat.report.claim.ClaimStatistic;
import org.apache.rat.report.xml.XmlReportFactory;
import org.apache.rat.report.xml.writer.impl.base.XmlWriter;
import org.gradle.api.GradleException;
import org.gradle.internal.logging.ConsoleRenderer;
import org.gradle.workers.WorkAction;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public abstract class RatWork implements WorkAction<RatWorkSpec> {

    @Override
    public void execute() {

        File reportDir = getParameters().getReportDirectory().getAsFile().get();
        reportDir.mkdirs();

        File xmlReportFile = new File(reportDir, "rat-report.xml");
        File plainReportFile = new File(reportDir, "rat-report.txt");
        File htmlReportFile = new File(reportDir, "index.html");

        ReportConfiguration config = createReportConfiguration(getParameters());

        ClaimStatistic stats = new ClaimStatistic();

        generateXmlReport(config, stats, xmlReportFile);

        transformReport(xmlReportFile, htmlReportFile, plainReportFile);

        if (stats.getNumUnApproved() > 0) {
            if (getParameters().getVerbose().get()) {
                System.err.println(verboseFailureOutput(xmlReportFile));
            }
            String message = "Apache Rat audit failure - "
                    + stats.getNumUnApproved() + " unapproved license" + (stats.getNumUnApproved() > 1 ? "s" : "")
                    + "\n"
                    + "\tSee " + new ConsoleRenderer().asClickableFileUrl(htmlReportFile);
            if (getParameters().getFailOnError().get()) {
                throw new GradleException(message);
            } else {
                System.err.println(message);
            }
        }
    }

    private ReportConfiguration createReportConfiguration(RatWorkSpec spec) {

        ReportConfiguration config = new ReportConfiguration();

        List<IHeaderMatcher> matchers = new ArrayList<>();
        if (spec.getAddDefaultMatchers().get()) {
            matchers.add(Defaults.createDefaultMatcher());
        }
        for (SubstringMatcher substringMatcher : spec.getSubstringMatchers().get()) {
            SubstringLicenseMatcher matcher = new SubstringLicenseMatcher();
            matcher.setLicenseFamilyCategory(substringMatcher.getLicenseFamilyCategory());
            matcher.setLicenseFamilyName(substringMatcher.getLicenseFamilyName());
            for (String substring : substringMatcher.getSubstrings()) {
                SubstringLicenseMatcher.Pattern pattern = new SubstringLicenseMatcher.Pattern();
                pattern.setSubstring(substring);
                matcher.addConfiguredPattern(pattern);
            }
            matchers.add(matcher);
        }
        config.setHeaderMatcher(new HeaderMatcherMultiplexer(matchers));

        if (spec.getApprovedLicenses().get().isEmpty()) {
            config.setApproveDefaultLicenses(true);
        } else {
            config.setApproveDefaultLicenses(false);
            List<ILicenseFamily> families = new ArrayList<>();
            for (String familyName : spec.getApprovedLicenses().get()) {
                families.add(new SimpleLicenseFamily(familyName));
            }
            config.setApprovedLicenseNames(families);
        }

        return config;
    }

    private void generateXmlReport(ReportConfiguration config, ClaimStatistic stats, File xmlReportFile) {

        try (Writer xmlFileWriter = Files.newBufferedWriter(xmlReportFile.toPath(), StandardCharsets.UTF_8)) {
            XmlWriter writer = new XmlWriter(xmlFileWriter);
            RatReport report = XmlReportFactory.createStandardReport(writer, stats, config);
            report.startReport();
            new FilesReportable(
                            new ArrayList<>(getParameters().getReportedFiles().getFiles()))
                    .run(report);
            report.endReport();
            writer.closeDocument();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (RatException ex) {
            throw new GradleException(ex.getMessage(), ex);
        }
    }

    private void transformReport(File xmlReportFile, File htmlReportFile, File plainReportFile) {

        TransformerFactory factory = TransformerFactory.newInstance();
        try {
            Transformer htmlTransformer = factory.newTransformer(
                    new StreamSource(RatWork.class.getResourceAsStream("apache-rat-output-to-html.xsl")));
            htmlTransformer.transform(new StreamSource(xmlReportFile), new StreamResult(htmlReportFile));

            Transformer plainTransformer = factory.newTransformer(new StreamSource(Defaults.getPlainStyleSheet()));
            plainTransformer.transform(new StreamSource(xmlReportFile), new StreamResult(plainReportFile));
        } catch (TransformerException ex) {
            throw new GradleException(ex.getMessage(), ex);
        }
    }

    private String verboseFailureOutput(File xmlReportFile) {
        return "Files with unapproved licenses:\n - " + String.join("\n - ", unapprovedFilesFrom(xmlReportFile)) + "\n";
    }

    private List<String> unapprovedFilesFrom(File xmlReportFile) {
        try {
            NodeList resources = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(xmlReportFile)
                    .getElementsByTagName("resource");
            List<String> unapprovedFiles = new ArrayList<>();
            for (Element resource : toElementList(resources)) {
                for (Element child : toElementList(resource.getChildNodes())) {
                    if ("license-approval".equals(child.getTagName()) && "false".equals(child.getAttribute("name"))) {
                        unapprovedFiles.add(resource.getAttribute("name"));
                        break;
                    }
                }
            }
            Collections.sort(unapprovedFiles);
            return unapprovedFiles;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (ParserConfigurationException | SAXException ex) {
            throw new GradleException(ex.getMessage(), ex);
        }
    }

    private List<Element> toElementList(NodeList nodes) {
        List<Element> elements = new ArrayList<>(nodes.getLength());
        for (int idx = 0; idx < nodes.getLength(); idx++) {
            elements.add((Element) nodes.item(idx));
        }
        return elements;
    }
}
