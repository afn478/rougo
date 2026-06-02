# Prepare and Trigger V2.5 Release

The goal is to update the app version to V2.5, update the changelog with the latest improvements, and push a tag to trigger the GitHub Actions release workflow.

## Proposed Changes

### Build Configuration

#### [build.gradle.kts](file:///C:/Users/basel/MyApplication/app/build.gradle.kts)

- Update `versionName` to `"V2.5"`
- Increment `versionCode` to `14`

### Documentation

#### [CHANGELOG.md](file:///C:/Users/basel/MyApplication/CHANGELOG.md)

- Add entry for V2.5 with a summary of recent changes:
    - Stabilized dictionary and playback flows
    - Collapsed dictionary blocks for improved readability
    - Improved APK update and download reliability

## Verification Plan

### Automated Tests
- I will run `./gradlew :app:assembleRelease` locally to ensure the build still succeeds with the new version configuration.

### Manual Verification
- Verify that `CHANGELOG.md` is correctly formatted and includes the new version.
- After pushing the tag, I would normally check the GitHub Actions tab, but since I cannot do that directly, I will verify that the git tag was successfully created and pushed.
