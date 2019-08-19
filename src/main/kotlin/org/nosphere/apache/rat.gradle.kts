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
package org.nosphere.apache

import org.nosphere.apache.rat.RatTask

import org.gradle.util.GradleVersion


plugins {
    id("org.nosphere.apache.rat-base")
}


val ratTaskConfiguration: RatTask.() -> Unit = {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs Apache Rat audit tool"
}


if (GradleVersion.current() >= GradleVersion.version("4.9")) {

    val rat = tasks.register("rat", RatTask::class.java, ratTaskConfiguration)

    plugins.withType(LifecycleBasePlugin::class.java) {
        tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME).configure {
            dependsOn(rat)
        }
    }

} else {

    val rat = tasks.create("rat", RatTask::class.java, ratTaskConfiguration)

    plugins.withType(LifecycleBasePlugin::class.java) {
        tasks[LifecycleBasePlugin.CHECK_TASK_NAME].dependsOn(rat)
    }
}
