
# Apache RAT (Release Audit Tool) Gradle Plugin

**build.gradle**

    plugins {
        id "org.nosphere.apache.rat" version "0.1.1"
    }

    rat {

        // Input directory, defaults to the project's root directory
        inputDir = project.file( 'some/path' )

        // List of exclude directives, defaults to none
        excludes = [ '**/build/**' ]

        // Fail the build on rat errors, defaults to true
        failOnError = false

    }

**command line**

    gradle rat

XML and HTML reports are outputed in `build/reports/rat`.
If the project has a `check` task, the `rat` task is automatically registered as dependent on the former.
