# GitHub publication

## Repository boundaries

| Repository | Source archive | Project license |
| --- | --- | --- |
| Launcher | `build/WMDroid-launcher-sources.zip` | GPL-3.0-or-later |
| SDK and documentation | `sdk/build/WMDroid-sdk-sources.zip` | Apache-2.0 |
| Example apps | `build/WMDroid-examples-sources.zip` | WTFPL v2 |
| Resource extractor | `build/WMDroid-resource-extractor-sources.zip` | See its `LICENSE` |

Third-party files retain their own licenses. The launcher and examples consume
the published SDK AAR; they do not copy its Java implementation. Keep generated
resource packs outside all four repositories.

## Source staging

Run from the development workspace. Create the selected-source archive:

```sh
python3 tools/export_public_sources.py --self-test
python3 tools/prepare_github_sources.py --self-test
python3 tools/export_public_sources.py
python3 tools/export_launcher_sources.py --output build/github-launcher-source
python3 tools/export_examples_sources.py --output build/github-examples-source
```

Output directories must not exist. The exporters verify archive integrity,
SHA256 and staged bytes. They do not initialize Git, commit, push or create a
GitHub repository. Use each repository's staged directory as its root. Do not
publish the development workspace or combined `WMDroid-public-sources.zip` as
the launcher repository.

Build the SDK with `tools/build_sdk.py` before staging its source ZIP. Its ZIP
root contains `sdk/`, `tools/` and notices; preserve that layout. Stage the
extractor from its separate reviewed source archive.

The staged trees exclude ROM/ISO files, extracted resource packs, original
analysis fixtures, signing keys, device logs and build artifacts. The combined
archive's `SOURCE-BOUNDARY.md` records its boundary. `.gitignore` prevents common
local build and signing files from being added later; it does not audit new files.

Before publishing:

- Review the staged files and their source provenance.
- Confirm the launcher/SDK license scope in `LICENSES.md`.
- Review the packaged dependency sources, notices and documented build inputs
  in [DEPENDENCIES.md](DEPENDENCIES.md) and `third_party/dependencies.json`.
  Keep the established correspondence and remaining limits distinct.
- Rebuild from the staged sources with the documented SDK/JDK and pinned
  dependencies. Run the release, resource and language checks.
- Inspect `git diff --cached` before each push. Keep imported packs and original
  media outside the public repository.

The dependency source ZIP contains the identified implementation snapshot,
official MultiDex source JAR, conversion sources and R8 preparation checkout.
That checkout includes the wrapper rewriter, JSON converter, packaging scripts
and external-input acquisition declarations. The project can be rebuilt from
its staged sources with the pinned binary build inputs. These are separate
checks from rebuilding those dependency binaries from their upstream sources.

The dependency comparison establishes the bounded source/class relations
described in [DEPENDENCIES.md](DEPENDENCIES.md). The exact historical publication
checkout and compiler, full upstream pipeline execution and two anonymous class
metadata differences remain unconfirmed. Historical build identity is not
treated as an additional release requirement merely because it is unconfirmed.
Preserve these limits when describing the package.

`gpl_release_ready` remains false: these technical checks are not a whole-project
rights or GPL compliance determination. Do not describe archive validation or
the local candidate as legal clearance or complete Windows Mobile parity.

Distribute the resource extractor separately. Do not attach generated Microsoft
or OEM resource packs to GitHub releases. The included Gulim font retains OFL
1.1 and its adjacent copyright/license files.

## Publish after the remaining checks

Create an empty GitHub repository without an initial README or license. From
the reviewed staged directory, initialize Git if needed, inspect the index,
then create the first commit:

```sh
cd build/github-launcher-source
git init -b main
git add .
git diff --cached --check
git diff --cached --stat
git diff --cached
git commit -m "chore: prepare initial public source"
```

Repeat for the other reviewed repository roots. Add each repository URL only
after confirming its owner and visibility:

```sh
git remote add origin <repository-url>
git push -u origin main
```

Do not force-push over an existing repository. Release APKs and SDK artifacts
need their own final checks; pushing source does not mark a release complete.
