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
package org.apache.rat.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.internal.project.IsolatedAntBuilder

class RatTask extends DefaultTask {

    boolean failOnError = true
    boolean verbose = false

    @InputDirectory
    File inputDir = project.file( '.' )

    @Input
    List<String> excludes = []

    @OutputDirectory
    File reportDir = project.file( 'build/reports/rat' )

    def xmlReport = new File( reportDir , 'rat-report.xml' ).absolutePath
    def htmlReport = new File( reportDir, 'index.html' ).absolutePath

    @TaskAction
    def rat() {
        if( !reportDir.exists() ) {
            reportDir.mkdirs()
        }
        generateXmlReport()
        def errorCount = countUnaprovedUnknownLicenses()
        generateHtmlReport()
        if( failOnError && errorCount > 0 ) {
            throw new GradleException( "Found $errorCount files with unapproved/unknown licenses. See $htmlReport" )
        }
    }

    def generateXmlReport() {
        def antBuilder = services.get( IsolatedAntBuilder )
        def ratClasspath = project.configurations.rat
        antBuilder.withClasspath( ratClasspath ).execute {
            ant.taskdef( resource: 'org/apache/rat/anttasks/antlib.xml' )
            ant.report( format: 'xml', reportFile: xmlReport ) {
                fileset( dir: inputDir.absolutePath ) {
                    patternset {
                        excludes.each { exclude( name: it ) }
                    }
                }
            }
        }
        project.logger.info "Rat XML report: $xmlReport"
    }

    def countUnaprovedUnknownLicenses() {
        def ratXml = new XmlParser().parse( xmlReport )
        def errorCount = 0
        ratXml.resource.each { resource ->
            if( resource.'license-approval'.@name[0] == "false" ) {
                def log = 'Unapproved/unknown license: ' + resource.@name
                if( verbose ) println( log )
                else  project.logger.debug( log )
                errorCount++
            }
        }
        return errorCount
    }

    def generateHtmlReport() {
        def stylesheet = project.file( 'build/tmp/rat/stylesheet.xsl' )
        stylesheet.parentFile.mkdirs()
        stylesheet.text = this.getClass().getResource( 'apache-rat-output-to-html.xsl' ).text
        def antBuilder = services.get( IsolatedAntBuilder )
        def ratClasspath = project.configurations.rat
        antBuilder.withClasspath( ratClasspath ).execute {
            ant.xslt(
                in: xmlReport,
                style: stylesheet.absolutePath,
                out: htmlReport,
                classpath: ratClasspath
            )
        }
        project.logger.info "Rat HTML report: $htmlReport"
    }

}
