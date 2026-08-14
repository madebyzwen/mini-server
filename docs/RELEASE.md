# Release Process

Mini Server releases use Semantic Versioning in `MAJOR.MINOR.PATCH` form. Published Git tags matching `vMAJOR.MINOR.PATCH` are the authoritative release-version history.

## Release types

- `patch` increments PATCH: `v1.4.7` becomes `v1.4.8`.
- `minor` increments MINOR and resets PATCH: `v1.4.7` becomes `v1.5.0`.
- `major` increments MAJOR and resets MINOR and PATCH: `v1.4.7` becomes `v2.0.0`.

Tags that do not match the strict semantic tag format are ignored. If no semantic release tag exists, the logical base is `0.0.0`; the first major release is therefore `v1.0.0`.

## Continuous integration

`.github/workflows/build.yml` runs for pushes to `main`, pushes to `review/**`, and manual dispatches. It runs the Java and MiniApi test suites, builds the portable runtime through Maven, validates the distribution contents, and uploads the complete runtime ZIP as the workflow artifact.

## Publishing a release

`.github/workflows/release.yml` runs only through `workflow_dispatch`. Dispatch it from `main` and select exactly one `release_type`: `patch`, `minor`, or `major`.

For a human dispatch:

1. Open the repository's **Actions** page.
2. Select **Release** and **Run workflow**.
3. Select the `main` branch.
4. Choose the semantic release type.
5. Leave `dry_run` disabled for an explicitly approved real release.

The equivalent GitHub CLI dispatch is:

```text
gh workflow run release.yml --ref main -f release_type=patch -f dry_run=false
```

Codex must follow the release-interaction rules in `AGENTS.md`, dispatch the workflow only after the release type is explicit, and monitor the resulting Actions run.

## Versioning and release assets

The workflow fetches complete tag history, finds the highest strict semantic release tag, and calculates the next version. It temporarily applies that non-SNAPSHOT version to the checked-out Maven workspace using the Maven Versions Plugin. The change is neither committed nor pushed.

After all Java tests, MiniApi tests, packaging, and distribution checks pass, publication creates the semantic tag at the exact built `main` commit and publishes a GitHub Release titled `Mini Server vMAJOR.MINOR.PATCH` with:

- `mini-server-MAJOR.MINOR.PATCH.zip`
- `mini-server-MAJOR.MINOR.PATCH.zip.sha256`

The GitHub-built ZIP is authoritative. Do not substitute a locally built or manually uploaded archive.

## Dry runs and failures

Set `dry_run=true` only for workflow verification or maintenance. A dry run performs version calculation, tests, packaging, distribution validation, checksum generation, and Actions artifact upload, but skips tag and GitHub Release creation.

Any failed test, build, version, or distribution check stops the workflow before publication. Do not work around a failed workflow by manually creating a tag, release, or release asset.
