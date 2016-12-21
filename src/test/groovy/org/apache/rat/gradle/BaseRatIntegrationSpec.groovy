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

import nebula.test.IntegrationSpec

/**
 * BaseRatPlugin IntegrationSpec.
 */
class BaseRatIntegrationSpec extends IntegrationSpec
{
  def 'do not create rat task'()
  {
    setup:
    fork = true
    def inputDir = buildFile.parentFile.absolutePath.replaceAll( '\\\\', '/' )
    buildFile << """
            apply plugin: 'org.nosphere.apache.rat-base'
            task test {
              doLast {
                assert project.tasks.findByName('rat') == null
              }
            }
        """.stripIndent()

    expect:
    runTasksSuccessfully 'test'
  }

  def 'allow creation of arbitrary rat tasks'()
  {
    setup:
    fork = true
    def inputDir = buildFile.parentFile.absolutePath.replaceAll( '\\\\', '/' )
    buildFile << """
            apply plugin: 'org.nosphere.apache.rat-base'
            task someRat(type: org.apache.rat.gradle.RatTask) {
              verbose = true
              inputDir = '$inputDir'
              excludes = [
                'build.gradle', 'settings.gradle', 'build/**', '.gradle/**', '.gradle-test-kit/**'
              ]
            }
        """.stripIndent()

    expect:
    runTasksSuccessfully 'someRat'
  }
}
