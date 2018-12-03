
# Apache RAT (Release Audit Tool) Gradle Plugin

**build.gradle(.kts)**

    plugins {
        id("org.codeartisans.rat") version "0.4.0"
    }

    tasks.rat {

        // Input directory, defaults to '.'
        inputDir.set("some/path")

        // List of Gradle exclude directives, defaults to ['**/.gradle/**']
        excludes.add("**/build/**")

        // Rat excludes file, one directive per line
        excludeFile.set(layout.projectDirectory.file(".rat-excludes.txt"))

        // XML, TXT and HTML reports directory, defaults to 'build/reports/rat'
        reportDir.set(file("some/other/path"))

        // Custom XSL stylesheet for the HTML report
        stylesheet.set(file("custom/rat-html-stylesheet.xsl")

        // Fail the build on rat errors, defaults to true
        failOnError.set(false)
    }

**command line**

    gradle rat

If the project has a `check` task, the `rat` task is automatically registered as dependent on the former.


**compatibility matrix**

    Plugin version | Minimum | Maximum Gradle version
             0.4.0 |    4.6  | 5.x
             0.3.1 |   2.14  | 4.x
             0.3.0 |   2.14  | 4.x
             0.2.0 |   2.14  | 4.x
             0.1.0 |   2.14  | 4.x
