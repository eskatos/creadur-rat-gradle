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
import org.nosphere.honker.gradle.HonkerCheckTask
import org.nosphere.honker.gradle.HonkerGenDependenciesTask
import org.nosphere.honker.gradle.HonkerGenLicenseTask
import org.nosphere.honker.gradle.HonkerGenNoticeTask

plugins {
    `kotlin-dsl`
    `maven-publish`
    id("com.gradle.plugin-publish") version "1.2.1"
    id("org.nosphere.apache.rat") version "0.8.0"
    id("org.nosphere.honker") version "0.4.0"
}

group = "org.nosphere.apache"
version = "0.8.1-SNAPSHOT"

gradlePlugin {
    website = "https://github.com/eskatos/creadur-rat-gradle"
    vcsUrl = "https://github.com/eskatos/creadur-rat-gradle"
    plugins {
        all {
            description = "Apache RAT (Release Audit Tool) Gradle Plugin"
            tags = listOf("apache", "release-audit", "license")
        }
        named("org.nosphere.apache.rat-base") {
            displayName = "Apache RAT Base Gradle Plugin"
        }
        named("org.nosphere.apache.rat") {
            displayName = "Apache RAT Gradle Plugin"
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.apache.rat:apache-rat:0.15")

    testImplementation("junit:junit:4.13.2")
    testImplementation(gradleTestKit())
}

tasks.validatePlugins {
    failOnWarning = true
    enableStricterValidation = true
}

listOf(
    HonkerCheckTask::class,
    HonkerGenDependenciesTask::class,
    HonkerGenLicenseTask::class,
    HonkerGenNoticeTask::class
).forEach { honkerTaskType ->
    tasks.withType(honkerTaskType).configureEach {
        notCompatibleWithConfigurationCache("https://github.com/eskatos/honker-gradle/issues/1")
    }
}

sourceSets {
    main {
        output.dir(tasks.honkerGenDependencies.map { it.outputDir }, "builtBy" to tasks.honkerGenDependencies)
        output.dir(tasks.honkerGenLicense.map { it.outputDir }, "builtBy" to tasks.honkerGenLicense)
        output.dir(tasks.honkerGenNotice.map { it.outputDir }, "builtBy" to tasks.honkerGenNotice)
    }
}
honker.license = "Apache 2"
tasks.honkerGenNotice {
    footer = "This product includes software developed at\nThe Apache Software Foundation (http://www.apache.org/).\n"
}
tasks.check { dependsOn(tasks.honkerCheck) }

tasks.rat {
    verbose = true
    exclude(
        "README.md", "CODE_OF_CONDUCT.md",
        ".gradletasknamecache", "gradle/wrapper/**", "gradlew*", "build/**", // Gradle
        ".nb-gradle/**", "*.iml", "*.ipr", "*.iws", "*.idea/**", ".editorconfig" // IDEs
    )
    notCompatibleWithConfigurationCache("https://github.com/eskatos/creadur-rat-gradle/issues/23")
}
