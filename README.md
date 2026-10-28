# English Practice

A personal Android app for 5-minute daily English spelling and grammar practice, with Hebrew prompts.

- 32 rules (16 spelling, 16 grammar) and 560 practice items in `app/src/main/assets/curriculum.txt`
- Three exercise types: translate from Hebrew, listen and spell, fix the mistake
- Spaced repetition per item, an estimated score per rule, and an average grade
- Home screen widget with streak and average grade, and a daily reminder
- Every answer is logged; Settings > Export answer log shares it as a JSON file

## Installing

Every push to `main` builds the app on GitHub Actions and publishes it as a release.
On the phone, open the repository's **Releases** page, download `english-practice.apk` from the
latest release and open it. The first time, Android asks to allow installing apps from the browser.
New builds install over the old one and keep progress.

## Structure

- `core/`: plain Kotlin practice logic (curriculum parser, answer checking, scheduling, scores),
  with tests. Runs without Android: `./gradlew :core:test`, or `gradle -p core test` without the Android SDK.
- `app/`: the Android app (Jetpack Compose), widget and reminder.

The curriculum test checks that every rule has at least 12 items and that every accepted answer is accepted
by the checker.
