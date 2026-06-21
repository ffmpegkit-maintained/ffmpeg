# Contributing

Thanks for helping keep FFmpegKit alive and up to date. This project is maintained by volunteers in their spare time, so clear, focused contributions are the most likely to get merged quickly.

## Before you start

- For anything beyond a small fix, open an issue first describing the problem and your proposed approach. This avoids wasted work on changes that don't fit the project's direction.
- Check existing issues and pull requests to avoid duplicating work.

## Reporting bugs

Include:

- FFmpegKit variant (`full`, `audio`, `video`, `https`) and version
- Android API level and device/emulator architecture (arm64-v8a, armeabi-v7a, x86, x86_64)
- Minimal reproduction steps or sample project
- Relevant logs (`adb logcat`)

## Submitting changes

1. Fork the repository and create a branch from `main`.
2. Keep pull requests focused on a single change; unrelated cleanups should be separate PRs.
3. Follow the existing code style of the file you're editing.
4. If you change native build scripts (`android.sh`, Android.mk/CMake files), explain what you tested it on (host OS, NDK version, target ABIs).
5. Update relevant documentation ([README.md](README.md), [docs/BUILD.md](docs/BUILD.md)) when behavior, requirements or build steps change.
6. Make sure the CI workflow passes before requesting review.

## Native library changes

Changes that touch the bundled FFmpeg sources or third-party libraries require:

- The exact upstream version/commit being updated to or patched
- A summary of why the change is needed (security fix, SDK 35 compatibility, 16 KB page alignment, etc.)
- Confirmation that the license of any new/updated dependency is compatible with LGPL-3.0 distribution

## Code of conduct

Be respectful and constructive. This is a community-run continuation of an abandoned project — assume good faith, and keep discussions focused on technical merit.
