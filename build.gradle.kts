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

plugins {
    `kotlin-dsl`
    `maven-publish`
    id("com.gradle.plugin-publish") version "0.12.0"
    id("org.nosphere.apache.rat") version "0.7.0"
    id("org.nosphere.honker") version "0.3.0"
}

group = "org.nosphere.apache"
version = "0.8.0-SNAPSHOT"

pluginBundle {
    website = "https://github.com/eskatos/creadur-rat-gradle"
    vcsUrl = "https://github.com/eskatos/creadur-rat-gradle"
    description = "Apache RAT (Release Audit Tool) Gradle Plugin"
    tags = listOf("apache", "release-audit", "license")
    plugins {
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
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

kotlinDslPluginOptions {
    experimentalWarning.set(false)
    jvmTarget.set("1.8")
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.apache.rat:apache-rat:0.13")

    testImplementation("junit:junit:4.12")
    testImplementation(gradleTestKit())
}

tasks.validateTaskProperties {
    failOnWarning = true
    enableStricterValidation = true
}

val sourcesJar by tasks.registering(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    description = "Assembles sources JAR"
    archiveClassifier.set("sources")
    from(sourceSets.main.map { it.allSource })
    from(layout.buildDirectory.dir("generated-sources/kotlin-dsl-plugins/kotlin"))
}

publishing {
    publications {
        register<MavenPublication>("pluginMaven") {
            artifact(sourcesJar.get())
        }
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
tasks.honkerGenNotice { footer = "This product includes software developed at\nThe Apache Software Foundation (http://www.apache.org/).\n" }
tasks.check { dependsOn(tasks.honkerCheck) }

tasks.rat {
    verbose.set(true)
    exclude(
        "README.md", "CODE_OF_CONDUCT.md",
        ".gradletasknamecache", "gradle/wrapper/**", "gradlew*", "build/**", // Gradle
        ".nb-gradle/**", "*.iml", "*.ipr", "*.iws", "*.idea/**", ".editorconfig" // IDEs
    )
}
tasks.check { dependsOn(tasks.rat) }
