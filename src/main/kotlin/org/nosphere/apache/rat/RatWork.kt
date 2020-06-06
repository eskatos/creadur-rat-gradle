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
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters

import org.w3c.dom.Element
import org.w3c.dom.NodeList

import java.io.File
import java.io.FilenameFilter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource

internal
abstract class RatWorkSpec : WorkParameters {
    abstract val verbose: Property<Boolean>
    abstract val failOnError: Property<Boolean>
    abstract val addDefaultMatchers: Property<Boolean>
    abstract val substringMatchers: ListProperty<SubstringMatcher>
    abstract val approvedLicenses: ListProperty<String>
    abstract val baseDir: DirectoryProperty
    abstract val reportedFiles: ConfigurableFileCollection
    abstract val excludeFile: RegularFileProperty
    abstract val stylesheet: RegularFileProperty
    abstract val reportDirectory: DirectoryProperty
}

internal
abstract class RatWork : WorkAction<RatWorkSpec> {

    override fun execute() {

        val reportDir = parameters.reportDirectory.asFile.get()
        reportDir.mkdirs()

        val xmlReportFile = reportDir.resolve("rat-report.xml")
        val plainReportFile = reportDir.resolve("rat-report.txt")
        val htmlReportFile = reportDir.resolve("index.html")

        val config = createReportConfiguration(parameters)

        val stats = ClaimStatistic()

        generateXmlReport(config, stats, xmlReportFile)

        transformReport(xmlReportFile, htmlReportFile, plainReportFile)

        if (stats.numUnApproved > 0) {
            if (parameters.verbose.get()) {
                System.err.println(verboseFailureOutput(xmlReportFile))
            }
            val message = "Apache Rat audit failure - " +
                "${stats.numUnApproved} unapproved license${if (stats.numUnApproved > 1) "s" else ""}\n" +
                "\tSee ${ConsoleRenderer().asClickableFileUrl(htmlReportFile)}"
            if (parameters.failOnError.get()) throw RatException(message)
            else System.err.println(message)
        }
    }

    private
    fun createReportConfiguration(spec: RatWorkSpec): ReportConfiguration =
        ReportConfiguration().apply {

            val matchers = mutableListOf<IHeaderMatcher>()
            if (spec.addDefaultMatchers.get()) matchers.add(Defaults.createDefaultMatcher())
            spec.substringMatchers.get().forEach { substringMatcher ->
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

            if (spec.approvedLicenses.get().isEmpty()) {
                isApproveDefaultLicenses = true
            } else {
                isApproveDefaultLicenses = false
                setApprovedLicenseNames(spec.approvedLicenses.get().map { SimpleLicenseFamily(it) })
            }
        }

    private
    fun generateXmlReport(config: ReportConfiguration, stats: ClaimStatistic, xmlReportFile: File) {

        xmlReportFile.bufferedWriter().use { xmlFileWriter ->
            val writer = XmlWriter(xmlFileWriter)
            XmlReportFactory.createStandardReport(writer, stats, config).run {
                startReport()
                FilesReportable(parameters.reportedFiles.files.toList(), parameters.excludeFile.asFile.orNull).run(this)
                endReport()
            }
            writer.closeDocument()
        }
    }

    private
    fun transformReport(xmlReportFile: File, htmlReportFile: File, plainReportFile: File) =
        TransformerFactory.newInstance().let { factory ->

            factory.newTransformer(StreamSource(parameters.stylesheet.asFile.get())).transform(
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
