# Release process

1. Update `VERSION`, package manifests, webOS `appinfo.json` and `APP_VERSION`,
   Android `versionName`/`versionCode`, the changelog, and versioned release notes.
2. Run `npm run check`, `npm run test:bridge`, and the webOS browser tests.
   Android CI must pass unit tests and APK assembly.
3. Run `gitleaks git --redact --log-opts="--all"`. The checked-in configuration
   allows only a known public storage-key literal, not arbitrary test secrets.
4. Merge the reviewed release branch into `main` without rewriting history.
5. Tag the verified commit, for example `git tag -a v0.7.0 -m "XtreamlyTV v0.7.0"`,
   then push that specific tag. Do not push unrelated tags or use a mirror push.
6. The release workflow tests and builds the webOS IPK and Android debug APK,
   generates SHA-256 checksums, and uploads them to a **draft** GitHub release.
   Review the successful workflow, asset checksums, and curated notes before
   publishing. Notes are selected automatically from the version tag.

## Android signing

The current Android artifact is explicitly a debug APK for sideloading and
testing. CI-generated debug certificates are not a stable production update
identity. A differently signed installation requires an uninstall, which
removes local data. Do not represent this APK as production-signed or suitable
for Google Play.

Production distribution requires a privately backed-up signing key provided
through CI secrets or secure external signing. Never commit that key, its
password, a provider payload, or any account credentials. Preserve the same
production signing identity for subsequent updates.
