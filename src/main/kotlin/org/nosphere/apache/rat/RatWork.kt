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
package org.nosphere.apache.rat

import org.apache.rat.Defaults
import org.apache.rat.Report
import org.apache.rat.ReportConfiguration
import org.apache.rat.analysis.IHeaderMatcher
import org.apache.rat.analysis.util.HeaderMatcherMultiplexer
import org.apache.rat.anttasks.SubstringLicenseMatcher
import org.apache.rat.api.RatException
import org.apache.rat.document.impl.FileDocument
import org.apache.rat.license.SimpleLicenseFamily
import org.apache.rat.report.IReportable
import org.apache.rat.report.RatReport
import org.apache.rat.report.claim.ClaimStatistic
import org.apache.rat.report.xml.XmlReportFactory
import org.apache.rat.report.xml.writer.impl.base.XmlWriter

import org.gradle.internal.logging.ConsoleRenderer

import org.w3c.dom.Element
import org.w3c.dom.NodeList

import java.io.File
import java.io.FilenameFilter
import java.io.Serializable
import javax.inject.Inject
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource


internal
data class RatWorkSpec(
    val verbose: Boolean,
    val failOnError: Boolean,
    val addDefaultMatchers: Boolean,
    val substringMatchers: List<SubstringMatcher>,
    val approvedLicenses: List<String>,
    val baseDir: File,
    val reportedFiles: List<File>,
    val excludeFile: File?,
    val stylesheet: File,
    val reportDirectory: File
) : Serializable


internal
open class RatWork @Inject constructor(
    private val spec: RatWorkSpec
) : Runnable {

    override fun run() {

        spec.reportDirectory.mkdirs()

        val xmlReportFile = spec.reportDirectory.resolve("rat-report.xml")
        val plainReportFile = spec.reportDirectory.resolve("rat-report.txt")
        val htmlReportFile = spec.reportDirectory.resolve("index.html")

        val config = createReportConfiguration(spec)

        val stats = ClaimStatistic()

        generateXmlReport(config, stats, xmlReportFile)

        transformReport(xmlReportFile, htmlReportFile, plainReportFile)

        if (stats.numUnApproved > 0) {
            if (spec.verbose) {
                System.err.println(verboseFailureOutput(xmlReportFile))
            }
            val message = "Apache Rat audit failure - " +
                "${stats.numUnApproved} unapproved license${if (stats.numUnApproved > 1) "s" else ""}\n" +
                "\tSee ${ConsoleRenderer().asClickableFileUrl(htmlReportFile)}"
            if (spec.failOnError) throw RatException(message)
            else System.err.println(message)
        }
    }

    private
    fun createReportConfiguration(spec: RatWorkSpec): ReportConfiguration =
        ReportConfiguration().apply {

            val matchers = mutableListOf<IHeaderMatcher>()
            if (spec.addDefaultMatchers) matchers.add(Defaults.createDefaultMatcher())
            spec.substringMatchers.forEach { substringMatcher ->
                matchers.add(SubstringLicenseMatcher().apply {
                    licenseFamilyCategory = substringMatcher.licenseFamilyCategory
                    licenseFamilyName = substringMatcher.licenseFamilyName
                    substringMatcher.substrings.forEach { substring ->
                        addConfiguredPattern(SubstringLicenseMatcher.Pattern().apply {
                            this.substring = substring
                        })
                    }
                })
            }
            headerMatcher = HeaderMatcherMultiplexer(matchers)

            if (spec.approvedLicenses.isEmpty()) {
                isApproveDefaultLicenses = true
            } else {
                isApproveDefaultLicenses = false
                setApprovedLicenseNames(spec.approvedLicenses.map { SimpleLicenseFamily(it) })
            }
        }

    private
    fun generateXmlReport(config: ReportConfiguration, stats: ClaimStatistic, xmlReportFile: File) {

        xmlReportFile.bufferedWriter().use { xmlFileWriter ->
            val writer = XmlWriter(xmlFileWriter)
            XmlReportFactory.createStandardReport(writer, stats, config).run {
                startReport()
                FilesReportable(spec.reportedFiles, spec.excludeFile).run(this)
                endReport()
            }
            writer.closeDocument()
        }
    }

    private
    fun transformReport(xmlReportFile: File, htmlReportFile: File, plainReportFile: File) =
        TransformerFactory.newInstance().let { factory ->

            factory.newTransformer(StreamSource(spec.stylesheet)).transform(
                StreamSource(xmlReportFile),
                StreamResult(htmlReportFile)
            )

            factory.newTransformer(StreamSource(Defaults.getPlainStyleSheet())).transform(
                StreamSource(xmlReportFile),
                StreamResult(plainReportFile)
            )
        }

    private
    fun verboseFailureOutput(xmlReportFile: File): String =
        unapprovedFilesFrom(xmlReportFile).joinToString(
            separator = "\n - ",
            prefix = "Files with unapproved licenses:\n - ",
            postfix = "\n"
        )

    private
    fun unapprovedFilesFrom(xmlReportFile: File): List<String> =
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(xmlReportFile)
            .getElementsByTagName("resource")
            .toElementList()
            .filter { resource ->
                resource.childNodes.toElementList().any {
                    it.tagName == "license-approval" && it.getAttribute("name") == "false"
                }
            }
            .map { it.getAttribute("name") }
            .sorted()

    private
    fun NodeList.toElementList(): List<Element> =
        ArrayList<Element>(length).apply {
            for (idx in 0 until length) {
                add(item(idx) as Element)
            }
        }
}

private
class FilesReportable(
    private val files: List<File>,
    private val excludeFile: File?
) : IReportable {

    override fun run(report: RatReport) {
        for (file in files.filter(excludeFileFilter())) {
            report.report(FileDocument(file))
        }
    }

    private
    fun excludeFileFilter(): (File) -> Boolean {
        val lines = excludeFile?.takeIf { it.isFile }?.readLines()?.filter { it.isNotBlank() }
        if (lines.isNullOrEmpty()) return { true }
        val filter = createFilenameFilter(lines)
        return { file -> filter.accept(file.parentFile, file.name) }
    }

    private
    fun createFilenameFilter(lines: List<String>) =
        Report::class.java.getDeclaredMethod("parseExclusions", List::class.java).run {
            isAccessible = true
            invoke(null, lines)
        } as FilenameFilter
}
