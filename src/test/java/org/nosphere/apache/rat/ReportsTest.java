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
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class ReportsTest extends AbstractPluginTest {

    @Test
    public void htmlNamesUnapprovedFile() {
        withFailingAuditTree();

        assertRatTask(buildAndFail("check"), FAILED);
        String unapprovedFiles = elementWithId(htmlReport(), "unapproved-files");
        assertNotNull(unapprovedFiles, "Expected an element with id unapproved-files");
        assertTrue(unapprovedFiles.contains("no-license-file.txt"), unapprovedFiles);
    }

    @Test
    public void htmlCountsMatchXml() throws Exception {
        withFailingAuditTree();

        assertRatTask(buildAndFail("check"), FAILED);
        String unapprovedCount = statisticCount("Unapproved");
        assertEquals("1", unapprovedCount);
        String rendered = elementWithId(htmlReport(), "unapproved-count");
        assertNotNull(rendered, "Expected an element with id unapproved-count");
        assertEquals(unapprovedCount, rendered.trim());
    }

    @Test
    public void cleanAuditRendersAsPass() {
        withCleanAuditTree();

        assertRatTask(build("check"), SUCCESS);
        String html = htmlReport();
        String status = elementWithId(html, "audit-status");
        assertNotNull(status, "Expected an element with id audit-status");
        assertTrue(status.contains("No unapproved licenses"), status);
        assertNull(elementWithId(html, "unapproved-files"), "Expected no unapproved-files element on a clean audit");
    }

    @Test
    public void htmlHasNoNetworkReferences() {
        withFailingAuditTree();

        assertRatTask(buildAndFail("check"), FAILED);
        String html = htmlReport();
        for (String reference : new String[] {"http://", "https://", "<link rel=\"stylesheet\"", "<script src="}) {
            assertFalse(html.contains(reference), "index.html must not reference the network: " + reference);
        }
    }

    @Test
    public void xmlReportIsXml() {
        withCleanAuditTree();

        assertRatTask(build("check"), SUCCESS);
        String xml = withoutXmlDeclaration(readReport("rat-report.xml"));
        assertTrue(xml.startsWith("<rat-report"), () -> "Expected an XML report, got: " + xml);
    }

    @Test
    public void plainReportNamesUnapprovedFile() {
        withFailingAuditTree();

        assertRatTask(buildAndFail("check"), FAILED);
        String plain = readReport("rat-report.txt");
        assertFalse(plain.trim().startsWith("<"), "Expected a plain text report, got: " + plain);
        assertTrue(plain.contains("! /no-license-file.txt"), plain);
    }

    private static String withoutXmlDeclaration(String document) {
        String trimmed = document.trim();
        if (trimmed.startsWith("<?xml")) {
            return trimmed.substring(trimmed.indexOf("?>") + 2).trim();
        }
        return trimmed;
    }

    private String statisticCount(String name) throws Exception {
        Document xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(reportFile("rat-report.xml"));
        NodeList statistics = xml.getElementsByTagName("statistic");
        for (int i = 0; i < statistics.getLength(); i++) {
            Element statistic = (Element) statistics.item(i);
            if (name.equals(statistic.getAttribute("name"))) {
                return statistic.getAttribute("count");
            }
        }
        throw new AssertionError("No statistic named " + name + " in rat-report.xml");
    }

    private void withFailingAuditTree() {
        withRatBuildScript();
        withFile("default-licensed.txt", Fixtures.commentedApacheLicenseHeader());
        withFile("no-license-file.txt", "Nothing here.");
        withBinaryFile("image.png", Fixtures.PNG);
    }

    private void withCleanAuditTree() {
        withRatBuildScript();
        withFile("default-licensed.txt", Fixtures.commentedApacheLicenseHeader());
    }

    private String htmlReport() {
        return readReport("index.html");
    }

    private static String elementWithId(String html, String id) {
        Matcher matcher = Pattern.compile(
                        "<(\\w+)[^>]*\\sid=\"" + Pattern.quote(id) + "\"[^>]*>(.*?)</\\1>", Pattern.DOTALL)
                .matcher(html);
        return matcher.find() ? matcher.group(2) : null;
    }
}
