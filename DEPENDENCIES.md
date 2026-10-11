# Dependency sources

APK builds use the pinned artifacts in `tools/cache/**/dependencies.json`.
The builder checks their SHA256. It does not download them.
Licenses and notices are in [third_party/dependencies.json](third_party/dependencies.json).

## desugar_jdk_libs 2.1.5

The JAR in the [official R8 release archive](https://storage.googleapis.com/r8-releases/raw/desugar_jdk_libs/2.1.5/desugar_jdk_libs_jdk11.zip)
is byte-identical to the project's dependency.

[R8 release-preparation commit c331a820](https://r8.googlesource.com/r8.git/+/c331a820ddae7dea41f41a04375b784486d0f705%5E%21/)
references input archive `e4c206c7bdfe1415cd2716a1d59d699a80bc63dd`.
That archive identifies [upstream source revision 73170c34](https://github.com/google/desugar_jdk_libs/tree/73170c345e6a762fc6a1f0301bb15218850023ef).
`tools/export_dependency_sources.py` packages that unmodified source snapshot.

The preparation pipeline removes `DesugarDateTimeFormatterBuilder.java`, builds
`maven_release_jdk11` with Bazel, then runs R8's
`DesugaredLibraryJDK11Undesugarer`. The snapshot ZIP does not perform those steps.
The publication job's exact R8 checkout, toolchain and dependency closure remain
unverified. The preparation commit is not asserted to be the publication revision.

## desugar_jdk_libs_configuration 2.1.5

The pinned configuration JAR includes conversion and adapter classes. L8 receives
it as program input; 14 classes are defined in the APK’s `classes2.dex`. Its
embedded BSD-3-Clause license is included in both source archives and APK assets.
The inspected candidate R8 JSON uses configuration format 101. The JAR contains
format 200 with a package map and transformed flags. Those JSON documents are
not structurally identical; this comparison does not prove an incorrect input.
The release-preparation revision above contains 17 conversion Java sources and
50 supporting stubs. The upstream build compiles the stubs, compiles conversion
classes against them, then rewrites static `wrap_convert` calls to wrapper
`convert` calls.

Compiling those sources with JDK21 `--release 8` and applying the documented
symbolic call rewrite to `javap` listings matches declarations and instructions
for 27 of the JAR's 29 classes. The comparison preserves branch targets,
exception tables, descriptors and comment literals; it ignores constant-pool
indices, display spacing and the SourceFile display line. Two anonymous classes
have matching constructor descriptors and normalized method instructions.
Their constructor display differs because the candidate carries a `Signature`
attribute. The class `ACC_FINAL` and `InnerClasses` entry `ACC_STATIC` flags
still differ; these are real metadata differences, not display normalization.
The upstream compiler is JDK8; this check does not rebuild the transformed class
files or establish byte-identical artifacts. The publication source revision,
original toolchain and configuration JSON transformation remain unverified.

The candidate checkout pins the official custom-conversion input archive
`06f011cdb1f21ce902e1b653c3784c28ddfe28ed` in the `r8-deps` bucket.
Its `library_desugar_conversions.jar` contains the same 29 class entries as the
published configuration JAR; every class entry is byte-identical. This binds the
cached conversion input to the published classes. It does not establish that the
candidate Java sources produced that cache or identify the publication checkout.

## MultiDex 2.0.1

The source candidate includes the [official versioned source JAR](https://dl.google.com/dl/android/maven2/androidx/multidex/multidex/2.0.1/multidex-2.0.1-sources.jar).
Its original comments remain unchanged. Apache-2.0 license and NOTICE accompany
the source and runtime library.

## Build inputs and order

The following paths are inside `sources/r8-configuration-build-candidate.tar.gz`.
They describe the preparation checkout; this pipeline has not been executed here.

| Step | Source entry point | Inputs and result |
| --- | --- | --- |
| Implementation | `tools/archive_desugar_jdk_libs.py`, `BuildDesugaredLibrary` | Uses the upstream desugar snapshot, Android32 JAR, checked-in JDK and Bazel. Removes the named formatter source in a working copy, builds `maven_release_jdk11`, then runs `DesugaredLibraryJDK11Undesugarer`. Keep the distributed source snapshot unmodified. |
| Conversion classes | `d8_r8/library_desugar/build.gradle.kts`; `src/test/java/com/android/tools/r8/desugar/desugaredlibrary/customconversion/GenerateCustomConversionTest.java` | Compiles supporting stubs and conversion sources with checked-in JDK8, then applies `GenerateCustomConversion` and `CustomConversionAsmRewriteDescription`. Packaging consumes the pinned custom-conversion JAR. |
| Machine JSON | `tools/create_maven_release.py`, `convert_desugar_configuration` | Uses source JSON, implementation JAR, conversion JAR and Android33 JAR to generate format200 JSON with `DesugaredLibraryConverter`. |
| Configuration JAR | `tools/create_maven_release.py`, `generate_jar_with_desugar_configuration` | Includes conversion classes, machine JSON, LICENSE and lint data generated with Android34 JAR. |

External inputs are declared in the checkout's
`d8_r8/commonBuildSrc/src/main/kotlin/DependenciesPlugin.kt` and `tools/utils.py`.
The included `third_party/**/*.tar.gz.sha1` files pin the corresponding archives,
including Android32/33/34, JDK8/11, Bazel and the custom-conversion input.
Android32/33/34 JARs, JDK8/11, Bazel and resolved Gradle/Bazel dependencies are not
vendored in this source ZIP. Use the upstream acquisition declarations and pins;
do not substitute WMDroid's Android35/JDK17 launcher build settings for this pipeline.
The ZIP's `sources.json` gives the source archive URLs, revisions and hashes.
Exact historical configuration checkout and full pipeline rebuild remain unverified.

## Distribution

`build/WMDroid-dependency-sources.zip` contains the pinned source snapshots and
collected notices, including the unmodified 67-file configuration source candidate
and full R8 preparation checkout. The latter contains wrapper and JSON conversion
code, packaging scripts, LICENSE and AUTHORS; external build dependencies remain
outside the bundle. Its manifest contains upstream URLs, revisions and hashes;
it does not contain the extraction machine's paths or time.

This is a source-distribution candidate. Corresponding-source verification and
the whole-project source-provenance review remain incomplete.
`gpl_release_ready` remains false.

[GNU FAQ](https://www.gnu.org/licenses/gpl-faq.en.html#MustSourceBuildToMatchExactHashOfBinary)
distinguishes corresponding source from a byte-identical rebuild. Publication
commit and toolchain research above are verification routes, not independent
GPL requirements. The source used for the distributed binaries must still be
identified; a matching version label alone does not establish that.
