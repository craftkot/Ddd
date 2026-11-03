# EHTID P2P Chat — permanent signing setup

## Why this change is necessary

Android accepts an app update only when its application ID and signing certificate
match the installed version. Previous APKs (0.2 / 0.3 / 0.4) were signed by
different temporary GitHub Actions debug keys, so they cannot be upgraded
without reinstalling.

From version **0.5** onward, use the *same permanent keystore* with the fixed
certificate fingerprint:

`EF:AB:EF:78:A3:C7:DC:1C:F3:BE:83:3D:83:E2:E0:2D:22:16:85:B9:0C:12:0B:10:18:C6:B5:E2:49:AC:7C:77`

The private signing key is stored in this public repository **only as a
password-encrypted file** at `ci-signing/permanent-release.p12.enc.b64`.
The 64-character signing password is not committed.

## ONE-TIME owner action to activate it

1. Open **https://github.com/craftkot/Ddd/settings/secrets/actions**.
2. Click **New repository secret**.
3. Name: `EHTID_SIGNING_PASSWORD`.
4. Value: the **64-character secret delivered privately with the signing setup**.
   Never paste it into an issue, commit, gist, or pull request.
5. Click **Add secret**.
6. Open **https://github.com/craftkot/Ddd/actions/workflows/build-p2p-chat.yml**.
7. Choose **Run workflow** on branch `feature/p2p-chat`.
8. The workflow decrypts the keystore, checks it, runs tests, builds a signed
   v0.5 APK, verifies its signer certificate, then publishes:
   `https://github.com/craftkot/Ddd/releases/tag/ehtid-p2p-chat-v0.5`.

If the secret has not been configured, the workflow **fails deliberately**
instead of publishing yet another un-upgradeable temporary debug build.

## Future versions

- Always keep `applicationId 'com.ehtid.p2pchat'`.
- Never change the permanent signing file or the signing secret, or old installs
  will no longer accept updates.
- The GitHub Actions `GITHUB_RUN_NUMBER` becomes the monotonic versionCode
  (with a large offset) so each published build can replace earlier builds.
- Update the displayed versionName/release tag when publishing a new version.
- Keep the password somewhere safe. The encrypted keystore file without the
  secret cannot recover the private key.
- The current installed 0.4 APK still has an incompatible old certificate.
  **You will have to uninstall it once before installing the first 0.5 APK.**
  All later updates can install over 0.5 if the secret and package name remain
  the same. Uninstalling may erase old on-device chat history.

## Security

- The GitHub Actions workflow never prints the password.
- Decrypted `.p12` files are ignored by Git and exist only on the CI runner
  during a build.
- The encrypted public backup is not a way to recover the password.
- This protects the **distribution signing identity**, not the messenger
  protocol, privacy or uptime. Never treat the beta as a high-security app.
