
# Apache RAT (Release Audit Tool) Gradle Plugin

[![CI](https://github.com/eskatos/creadur-rat-gradle/actions/workflows/gradle-build-pr.yml/badge.svg)](https://github.com/eskatos/creadur-rat-gradle/actions/workflows/gradle-build-pr.yml)
[![Apache License 2](http://img.shields.io/badge/license-ASF2-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)

This plugin allows to run the [Apache RAT](https://creadur.apache.org/rat/) release audit tool, focused on licenses.

## Installation

In your `build.gradle(.kts)` file:

```kotlin
plugins {
    id("org.nosphere.apache.rat") version "0.8.0"
}
```

## Usage

The plugin registers a task named `rat` that you can configure in your `build.gradle(.kts)` file:

<details open>
<summary>Kotlin DSL</summary>

```kotlin
tasks.rat {

    // Use the default RAT license header matchers, defaults to `true`
    addDefaultMatchers.set(false)

    // Add custom substring license header matchers
    substringMatcher("family", "category", "pattern-1", "pattern-2")

    // Declare approved license families, if used, any non-declared family won't be approved
    approvedLicense("MIT")

    // Input directory, defaults to '.'
    inputDir.set("some/path")

    // List of Gradle exclude directives, defaults to ['**/.gradle/**']
    excludes.add("**/build/**")

    // RatTask 0.5.0+ implements PatternFilterable
    exclude { it.file in configurations.someConf.files }

    // Rat excludes file, one directive per line
    excludeFile.set(layout.projectDirectory.file(".rat-excludes.txt"))

    // XML, TXT and HTML reports directory, defaults to 'build/reports/rat'
    reportDir.set(file("some/other/path"))

    // Custom XSL stylesheet for the HTML report
    stylesheet.set(file("custom/rat-html-stylesheet.xsl"))

    // Fail the build on rat errors, defaults to true
    failOnError.set(false)
}
```

</details>
<details>
<summary>Groovy DSL</summary>

```groovy
rat {

    // Input directory, defaults to '.'
    inputDir.set(file("some/path"))

    // List of Gradle exclude directives, defaults to ['**/.gradle/**']
    excludes.add("**/build/**")

    // Rat excludes file, one directive per line
    excludeFile.set(layout.projectDirectory.file(".rat-excludes.txt"))

    // XML, TXT and HTML reports directory, defaults to 'build/reports/rat'
    reportDir.set(file("some/other/path"))

    // Custom XSL stylesheet for the HTML report
    stylesheet.set(file("custom/rat-html-stylesheet.xsl"))

    // Fail the build on rat errors, defaults to true
    failOnError.set(false)

    // Prints the list of files with unapproved licences to the console, defaults to false
    verbose.set(true)
}
```

</details>


You can run the `rat` task by invoking Gradle:

```
gradle rat
```

If the project has a `check` task, then the `rat` task is automatically registered as dependent on the former.

When a Rat audit fails, a clickable URL of the HTML report will be printed out:

```
FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':rat'.
> Apache Rat audit failure - 35 unapproved licenses
    See file:///path/to/build/reports/rat/index.html
```

![Apache Rat HTML Report](src/docs/resources/html_report_header.png "Apache Rat HTML Report")


## Compatibility matrix

| Plugin  | Min Java | Min Gradle | Max Gradle | [Configuration Cache](https://docs.gradle.org/current/userguide/configuration_cache.html) | [Build Cache](https://docs.gradle.org/current/userguide/build_cache.html)
|---------| --- | --- | --- | --- | ---
| `0.8.0` | `1.8` | `6.0` | `7.x` | 🟢 | 🟢
| `0.7.1` | `1.8` | `6.0` | `7.x` | 🟢 | 🟢
| `0.7.0` | `1.8` | `6.0` | `7.x` | 🟢 | 🟢
| `0.6.0` | `1.6` | `4.7` | `6.x` | 🟢 | 🟢
| `0.5.3` | `1.6` | `4.7` | `6.x` | 🔴 | 🟢
| `0.5.2` | `1.6` | `4.7` | `6.x` | 🔴 | 🟢
| `0.5.1` | `1.6` | `4.7` | `5.x` | 🔴 | 🟢
| `0.5.0` | `1.6` | `4.7` | `5.x` | 🔴 | 🟢
| `0.4.0` | `1.6` | `4.7` | `5.x` | 🔴 | 🟢
| `0.3.1` | `1.6` | `2.14` | `4.x` | 🔴 | 🟢
| `0.3.0` | `1.6` | `2.14` | `4.x` | 🔴 | 🟢
| `0.2.0` | `1.6` | `2.14` | `4.x` | 🔴 | 🟢
| `0.1.0` | `1.6` | `2.14` | `4.x` | 🔴  | 🟢
