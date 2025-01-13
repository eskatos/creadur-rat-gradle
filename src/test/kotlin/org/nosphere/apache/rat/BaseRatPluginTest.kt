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

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class BaseRatPluginTest(testMatrix: TestMatrix) : AbstractPluginTest(testMatrix) {

    @Test
    fun `do not create rat task `() {

        withBuildScript(
            """
            plugins {
                id("org.nosphere.apache.rat-base")
            }
            task("assertion") {
                def hasRat = project.tasks.findByName('rat') != null
                doLast {
                  assert !hasRat
                }
            }
            """
        )

        build("assertion")
    }

    @Test
    fun `allow creation of arbitrary rat tasks `() {

        withBuildScript(
            """
            plugins {
                id("org.nosphere.apache.rat-base")
            }
            task someRat(type: org.nosphere.apache.rat.RatTask) {
                verbose.set(true)
                inputDir.set(rootDir)
                excludes = [
                    'build.gradle', 'settings.gradle', 'build/**', '.gradle/**', '.gradle-test-kit/**'
                ]
            }
            """
        )

        build("someRat")
    }
}
