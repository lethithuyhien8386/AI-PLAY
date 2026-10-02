# AI Game Autopilot

## GitHub build

This repository is designed to build directly on GitHub Actions.

**Important:** this version does **not** require `gradlew` and the workflow does **not** run `chmod ./gradlew`.

1. Upload all files in this ZIP to the root of your GitHub repository.
2. Open **Actions**.
3. Select **Build APK**.
4. Click **Run workflow**.
5. When finished, download the artifact **AI-Game-Autopilot-debug**.

If your GitHub repository already contains an older `.github/workflows/build-apk.yml`, replace it with the one in this ZIP. Do not keep an old workflow that contains:

```bash
chmod +x gradlew
./gradlew ...
```

The current workflow installs Gradle 8.13 on the GitHub runner and executes:

```bash
gradle --no-daemon --stacktrace :app:assembleDebug
```

No API key is stored in the repository. Enter the Google AI Studio API key inside the Android app.
