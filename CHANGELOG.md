# Changelog

## 0.10.0

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

### Unchanged

`verbose`, `failOnError`, `inputDir`, `reportDir`, `addDefaultMatchers`, the include and exclude
patterns, the `substringMatcher` signature, and the report file names and locations all keep working
as before. So do the configuration cache and the build cache.

### Improved

- `verbose = true` prints the unapproved files and the license families in use, with their
  categories, which is what you need when a custom matcher does not behave.
- The task is quiet on a successful audit. RAT's own messages are available through Gradle's
  `--info` and `--debug` instead of always being printed.
- SPDX license tags are now detected, so files marked with an SPDX identifier can be recognized.
- The three reports are produced from a single scan.

## Migration

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
