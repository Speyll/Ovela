# GitHub repository maintenance

The public repository is https://github.com/joshcoburn/XtreamlyTV.
`main` contains the released source; release work is prepared on `release/v0.7.0`.
The Gitea remote may be retained separately for development and backup.

Before publishing, run the checks in [Releasing](releasing.md), inspect the diff,
and scan the complete history with Gitleaks. Never put credentials in remote
URLs, logs, issue reports, screenshots, sample provider payloads, or commits.
Use a credential helper or GitHub CLI authentication backed by a scoped token.

Repository administrators should enable private vulnerability reporting,
Dependabot alerts, and secret scanning/push protection where available. Protect
`main` against force pushes and deletion. Review dependency updates with the
Android toolchain constraints in mind; do not blindly merge major upgrades.

The release workflow stages a draft containing built packages and checksums.
Publish it only after all checks pass and its assets and notes are reviewed.
See [0.7.0 notes](release-notes/0.7.0.md) for the Android debug-build limitation.
