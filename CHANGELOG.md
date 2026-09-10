# Changelog

## 0.11.0

- Upgrades the bundled Apache RAT from `0.17` to `0.18`.
- RAT `0.18` needs Java `17`. Gradle itself may still run on Java `8`: the plugin runs RAT in a separate
  worker process and picks the JVM for it. A Java `17` must be findable by Gradle, or the build fails.
- New `javaLauncher` task property to pick the JVM RAT runs on. See the README's "Java for RAT" section.
- The minimum Gradle version changes from `6.0` to `7.0` because of the JVM toolchains requirement.

### Notable changes in RAT

- PDF files are treated as binary and no longer license checked. RAT `0.17` audited PDFs that were
  mostly text. RAT `0.15` and earlier treated them as binary too.
- The `encoding` column of the reports may change for some files: RAT now detects the charset from
  the first 256 bytes instead of 12 000.
- `verbose = true` prints fewer lines from RAT: RAT moved its exclusion messages from INFO to DEBUG.
- Tika `3.2.3` replaces `2.9.4`, which closes CVE-2025-66516. The resolved artifacts keep the same
  names, with new versions.

## 0.10.0 - 2026-09-10

Upgrades the bundled Apache RAT from `0.15` to `0.17`.

Gradle and Java requirements are unchanged: Gradle `6.0` to `9.x`, Java `8` or later.

Two properties are removed and some existing configuration values are no longer accepted. Your build
will tell you: every removal and every rejected value fails the build with a message naming what to fix.
Read "Migration" below before upgrading.

### Removed

**`stylesheet`**. RAT changed the XML report schema, so custom XSL stylesheets written for `0.9.0`
no longer match anything. They would not fail. They would render a report with every count at zero,
which looks like a passing audit. The property is removed so this cannot happen silently.

If you need custom rendering, transform `rat-report.xml` yourself in a follow-up task. RAT's schema
is `rat-report.xsd`, shipped inside the RAT jar.

**`excludeFile`**. RAT changed the pattern language. Lines in a `.rat-excludes.txt` file used to be
Java regular expressions matched against the file name. RAT now reads them as glob patterns matched
against the path. Some lines would silently start excluding more files than before, shrinking your
audit without failing. The property is removed.

Use the task's Gradle `exclude(...)` patterns instead:

| Old `.rat-excludes.txt` line | Replacement |
|------------------------------|-------------|
| `foo.txt` | `exclude("**/foo.txt")` |
| `.*\.txt` | `exclude("**/*.txt")`, or `exclude { spec }` for real regular expressions |
| `*.txt` | `exclude("*.txt")` |
| `sub/**` | `exclude("sub/**")` |
| the whole file | `exclude(file(".rat-excludes.txt").readLines())` |

The last row reads your file as Ant patterns, not as regular expressions. Check the result.

### Changed

**`approvedLicenses` values.** RAT renamed and merged its default license families. Values that no
longer match a family fail the build, with the valid families listed.

| `0.9.0` value | `0.10.0` value |
|---------------|----------------|
| `Apache License Version 2.0` | `Apache License`, or `AL` |
| `GNU General Public License, version 1/2/3` | `GNU General Public License family`, or `GPL` |
| `Modified BSD License` | `BSD 3 clause`, or `BSD-3` |
| `The MIT License` | unchanged |
| `OASIS Open License`, `W3C Software Copyright`, `W3C Document Copyright` | unchanged |

You can now use either the family name or the family category. `approvedLicense("MIT")` used to
approve nothing at all. It now approves the MIT family, which is what it always looked like it did.
If your build relied on that no-op, it will now approve more files than before.

**`substringMatcher` first argument.** The first argument is the license family category. RAT stores
categories as exactly five characters, so a longer value gets cut down silently. A cut down value can
land on another family and inherit its approval. The build now fails if:

- the category is longer than 5 characters, or
- the category matches an existing license family that has a different name.

```kotlin
// Fails: "BSD-3-Clause" is cut to "BSD-3", an existing approved family
substringMatcher("BSD-3-Clause", "My BSD License", "...")

// Fails: "MIT" is an existing family with a different name
substringMatcher("MIT", "My Own License", "...")

// Works: short, and clashes with nothing
substringMatcher("MYBSD", "My BSD License", "...")

// Works: same category and same name as a known family, on purpose
substringMatcher("MIT", "The MIT License", "...")
```

**More files are audited.** RAT decides differently which files are text and which are binary. Files
it used to skip may now be read and license checked. Examples are a `.txt` file containing non-UTF-8
characters, or a `.pdf` that is mostly text. Your build may fail on files that were never checked
before. Fix their headers, or exclude them.

**`failOnError` covers the audit only.** `failOnError = false` still turns an audit failure into a
log message. It does not suppress configuration errors: an unknown license family or an invalid
`substringMatcher` category always fails the build.

**`addDefaultMatchers = false` needs a `substringMatcher`.** RAT `0.17` refuses to run with no license
defined. Setting `addDefaultMatchers` to `false` without declaring at least one `substringMatcher` is now a
configuration error that fails the build, whatever `failOnError` is set to. `0.9.0` ran that configuration
and reported every file as unapproved.

**`rat-report.txt`** is now RAT's own plain text report. It has more detail than before, including
counters, per-category and per-license breakdowns, and a per-file section. The layout is different.
If a script greps this file, check that your check still matches. A pattern that stops matching does
not fail. It just stops finding anything.

**`index.html`** has been redesigned. The "headers of non-compliant files" section is gone. RAT no
longer produces the file header text it was built from, so it cannot be restored.

**Dependencies.** The plugin now resolves `org.apache.rat:apache-rat-core` and its dependencies
instead of the single `org.apache.rat:apache-rat` jar. This is smaller overall (381 KB instead of
7.4 MB), but it resolves more artifacts. If you use a curated artifact mirror, allow:

```
org.apache.rat:apache-rat-core
org.apache.commons:commons-collections4, commons-lang3, commons-compress, commons-text
commons-io:commons-io
commons-cli:commons-cli
org.apache.tika:tika-core, tika-parser-text-module
org.slf4j:slf4j-api
```

### Improved

- `verbose = true` prints the unapproved files and the license families in use, with their
  categories, which is what you need when a custom matcher does not behave.
- The task is quiet on a successful audit. RAT's own messages are available through Gradle's
  `--info` and `--debug` instead of always being printed.
- SPDX license tags are now detected, so files marked with an SPDX identifier can be recognized.
- The three reports are produced from a single scan.
- A file RAT cannot read fails the build. `0.9.0` logged it and skipped every file after it, so the
  audit could pass on a partial file set.
- A `substringMatcher` with no substring, or a blank one, fails at configuration time. It used to
  match nothing and every file ended up unapproved with no hint why.
- The report directory is never audited, even when it sits inside `inputDir`.
- Reports list files in sorted order, so they are the same on every machine.

### Migration

1. Update the plugin version to `0.10.0`.
2. Remove `stylesheet` if you set it. Move custom rendering to your own task reading `rat-report.xml`.
3. Remove `excludeFile` if you set it, and convert its lines to `exclude(...)` patterns using the
   table above.
4. Update `approvedLicenses` and `approvedLicense` values using the table above.
5. Check every `substringMatcher` call: the first argument must be at most 5 characters and must not
   clash with a known family. If you set `addDefaultMatchers` to `false`, declare at least one.
6. Run `gradle rat`. If it fails on files that used to pass, they are either newly audited files or
   files whose license family is no longer approved. Fix headers, adjust `approvedLicenses`, or
   exclude the files.
7. If any script reads `rat-report.txt` or `index.html`, check it still works.
8. If you use a curated artifact mirror, allow the artifacts listed above.

## 0.9.0 - 2026-09-09

- Plugin rewritten from Kotlin to Java. No change to the DSL.

## 0.8.2 - 2026-09-07

- RAT is downloaded through the buildscript repositories, the plugin portal by default. No project
  repository is needed any more.
- No more deprecation warning on Gradle 9.
- Supports Gradle up to `9.x`.

## 0.8.1 - 2023-09-03

- Fix a configuration cache failure on Gradle `8.1` and later.
- Supports Gradle up to `8.x`.

## 0.8.0 - 2022-09-21

- Upgrades the bundled RAT from `0.13` to `0.15`.

## 0.7.1 - 2022-04-05

- Supports Gradle up to `7.x`.

## 0.7.0 - 2020-06-06

- Requires Java `8` and Gradle `6.0`.
- Works with the configuration cache.

## 0.6.0 - 2020-01-11

- Custom license matching: `addDefaultMatchers`, `substringMatcher(...)` and `approvedLicense(...)`.
- `verbose` lists unapproved files in sorted order.
- Supports Gradle up to `6.x`.

## 0.5.3 - 2019-12-13

- `verbose` prints the list of files with unapproved licenses instead of the whole plain text report.

## 0.5.2 - 2019-08-19

- The audit failure message ends with a clickable link to the HTML report.

## 0.5.1 - 2019-08-19

- No more deprecation warnings on Gradle `5.6`.

## 0.5.0 - 2019-07-23

- The task implements `PatternFilterable`: `include(...)` and `exclude(...)` with patterns, specs or
  closures.

## 0.4.0 - 2019-01-14

Complete rewrite. Breaking changes.

- The `rat { }` extension is gone. Configure the task: `tasks.rat { }`. Properties are lazy:
  `inputDir.set(...)`, `reportDir.set(...)`, `failOnError.set(...)`.
- `xmlOutput`, `htmlOutput` and `plainOutput` are gone. One RAT run always produces the XML, plain
  text and HTML reports.
- New `excludeFile` for a RAT excludes file, `stylesheet` for a custom HTML stylesheet, `verbose` to
  print the plain text report.
- The task is cacheable and runs RAT in a worker.
- Upgrades the bundled RAT from `0.12` to `0.13`.
- Requires Java `6` and Gradle `4.7`.

## 0.3.1 - 2017-07-16

- Supports Gradle up to `4.x`.

## 0.3.0 - 2016-12-21

- New `org.nosphere.apache.rat-base` plugin. It registers no task, so you can declare your own
  `RatTask` tasks.

## 0.2.2 - 2016-09-11

- Fix the up-to-date check of the `rat` task.
- Supports Gradle `3.x`.

## 0.2.1 - 2016-06-13

- Upgrades the bundled RAT from `0.11` to `0.12`.

## 0.2.0 - 2015-09-09

- New `xmlOutput`, `htmlOutput` and `plainOutput` options.

## 0.1.3 - 2015-06-28

- Default `reportDir` under the project build directory.
- Excludes are task inputs, so changing them reruns the task.

## 0.1.2 - 2015-06-21

- Fix `reportDir` handling.

## 0.1.1 - 2015-06-18

- Requires Java `5` instead of `7`.
- The audit failure message names the HTML report.

## 0.1.0 - 2015-06-18

First release. `rat` task with `inputDir`, `reportDir`, `excludes` and `failOnError`, XML and HTML
reports, bundled RAT `0.11`. `check` depends on `rat` when present.
