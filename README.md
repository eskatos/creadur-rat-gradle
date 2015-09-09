
# Apache RAT (Release Audit Tool) Gradle Plugin

**build.gradle**

    plugins {
        id "org.nosphere.apache.rat" version "0.2.0"
    }

    rat {

        // Input directory, defaults to '.'
        inputDir = 'some/path'

        // XML and HTML reports directory, defaults to project.buildDir + '/reports/rat'
        reportDir = project.file( 'some/other/path' )

        // List of exclude directives, defaults to ['**/.gradle/**']
        excludes = [ '**/build/**' ]

        // Fail the build on rat errors, defaults to true
        failOnError = false

        // Enable XML RAT output, defaults to true
        xmlOutput = true

        // Enable HTML RAT output, defaults to true
        htmlOutput = true

        // Enable plain text RAT output, defaults to false
        // Please note that if xml or html output is enabled too,
        // then two RAT runs will be needed to produce all reports.
        plainOutput = false

    }

**command line**

    gradle rat

If the project has a `check` task, the `rat` task is automatically registered as dependent on the former.
