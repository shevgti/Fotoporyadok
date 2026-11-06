# Android build and release security boundary

Updated: 2026-11-06

## Trust boundaries

- Pull requests run only the `validate-pr` job with `contents: read`, no signing secret, no release permission, and no persisted checkout credentials.
- Pushes and manual builds use a separate `build` job with `contents: read`.
- Only a push to `main` can enter the signed release step.
- The signing password is exposed only to the signed-release build step. The encrypted and decrypted keystore files are placed in the runner temporary directory and removed by an EXIT trap.
- The build job cannot publish releases. It uploads a short-lived artifact bundle containing the APK, SHA-256 checksum and build provenance.
- A separate `publish` job has `contents: write`, downloads the artifact from the same workflow run, checks the checksum, source commit, run ID and version metadata, and only then creates a GitHub Release.
- Existing release tags are not overwritten. A new version is required for a new published APK.
- Third-party actions are pinned to full commit SHAs and annotated with the verified upstream release tag.

## Action pins

| Action | Version | Commit |
|---|---|---|
| actions/checkout | v4.2.2 | 11bd71901bbe5b1630ceea73d27597364c9af683 |
| actions/setup-java | v4.7.1 | c5195efecf7bdfc987ee8bae7a71cb8b11521c00 |
| gradle/actions/setup-gradle | v4.4.2 | 017a9effdb900e5b5b2fddfb590a105619dca3c3 |
| actions/upload-artifact | v4.6.2 | ea165f8d65b6e75b540449e92b4886f43607fa02 |
| actions/download-artifact | v4.3.0 | d3f86a106a0bac45b974a628896c90dbdf5c8093 |

The Gradle action tag object for v4.4.2 was dereferenced to commit 017a9eff...; GitHub reports the annotated tag signature as valid.

## Validation still required

- Run GitHub Actions on the draft pull request and inspect every job.
- Confirm the pull_request job can build debug APK without access to signing secrets.
- Confirm only main push can run signed release and publish jobs.
- Verify checksum/provenance failure blocks publication.
- Run actionlint and YAML parsing.
- Review Gradle/plugin/dependency lock and verification coverage separately (#25).

## Limitations

- SHA-256 verifies artifact integrity against the uploaded checksum; it does not independently establish that the source or build runner is trustworthy.
- The workflow still downloads theme photographs from fixed PxHere URLs during the build. Their provenance and checksum pinning should be reviewed separately.
- The workflow does not run ClamAV or independent APK signature intake yet (#24).
- No workflow was manually dispatched, no release was published, no signing secret was read or changed, and no APK was executed during this change.
