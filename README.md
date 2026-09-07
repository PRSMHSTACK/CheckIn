# Check In — Android MVP

A small offline checklist that regularly asks you to review unfinished tasks.

## Status
Native Kotlin/Compose implementation provided as source. **Not compiled or device-tested in the authoring environment:** no Android SDK or Gradle was installed, and network access to download them was blocked. No APK is included. Scheduling unit tests are included but have not run. This is an MVP development handoff, not a validated production release.

## Build and install
1. Install Android Studio with Android SDK Platform 36, compatible build tools, and JDK 17.
2. Install Gradle **8.13** (AGP 8.13.2 requires it). From this directory run `gradle wrapper --gradle-version 8.13`. This creates the official wrapper scripts and JAR; they are not bundled because their download was unavailable.
3. Open this directory in Android Studio and sync. If needed create `local.properties` containing `sdk.dir=<your Android SDK path>`.
4. Run `./gradlew testDebugUnitTest assembleDebug` (Windows: `gradlew.bat testDebugUnitTest assembleDebug`).
5. Run on a phone/emulator using Android Studio, or install `app/build/outputs/apk/debug/app-debug.apk`.
6. Allow notifications. Under Reminders → Sound, vibration & pop-ups, allow heads-up/pop-up display if your phone offers it.

## Screen flow
- Today → Add task → enter title → Save. Date defaults to the selected day.
- Today / Tomorrow / Pick date → checklist; Upcoming opens a scheduled date.
- Checkbox → immediate local completion; finished rows fade and sort last.
- Edit → title, date, optional time, notes, priority, move to tomorrow or confirmed delete.
- Reminder notification → full in-app checklist → check tasks → snooze / dismiss / task list.
- Reminders → intensity, custom times, quiet hours, notification settings, test saved settings.
- Past unfinished tasks appear in a separate small carry-over section. Keep them there, move to today/tomorrow, review, complete or delete. No automatic destructive rollover.

## Architecture
`MainActivity.kt`: Material 3 Compose UI, date picker, task/settings/snooze dialogs, permission request, lifecycle refresh.
`TaskViewModel.kt`: StateFlow state, Room observation, coroutine mutations followed by schedule reconciliation.
`Data.kt`: Room Task entity/DAO/database; local durable settings and snooze timestamps. No account, network permission, analytics, ads or cloud backup.
`Schedule.kt`: pure local-time scheduling logic, quiet hours, custom times, optional task times, DST-aware date construction.
`Reminders.kt`: single inexact AlarmManager alarm, high-importance notification channel, direct task-completion action, durable snooze, boot/time/package-change restoration and WorkManager recovery.

Room is the source of truth. The UI filters tasks by calendar date, so future tasks appear on the right day without a copying job. WorkManager reconciles every 15 minutes as an approximate recovery fallback; it does not itself promise precise timing. A process-wide mutex serializes receiver and scheduler updates. Alarm and worker delivery recheck today's unfinished tasks, quiet hours and snooze. Notification content is refreshed silently after in-app changes and cancelled when today's list is finished. Future task reminders remain scheduled.

Gentle: 10:00 and 18:00. Normal: 09:00, 13:00, 17:00, 21:00. Persistent: every two hours from 08:00 through 22:00. Custom: comma-separated 24-hour times. Quiet hours default to 23:00–08:00 and override regular, task-time and snooze reminders. Snoozes support 15/30/60 minutes or 1–1440 custom minutes. Priority sorts tasks first; system channel settings determine sound for all reminders.

## Android limitations and design decision
A general to-do app should not assume eligibility for full-screen intents. Android 14+ limits default access mainly to calling/alarm apps; Play eligibility and declarations would need review for any future alarm-focused variant. This MVP does not declare USE_FULL_SCREEN_INTENT, exact alarm permission or overlay permission, launch activities unsolicited from the background, or attempt to bypass security.

The supported experience is a high-importance notification with checklist summary, review, snooze and a direct completion action for the first unfinished task. Tapping review opens a large interactive checklist. Dismissal never traps the user; swiping a notification does not cancel later scheduled check-ins. Heads-up banners, sound, DND and lock-screen visibility remain controlled by Android and the user. Task content is private on the lock screen by default; unlocking may be needed to open the checklist.

Inexact `setAndAllowWhileIdle` avoids exact-alarm special access and is suitable for periodic check-ins, but delivery can be delayed by Doze, battery restrictions and vendor policies. Optional task times are approximate too. Boot restoration occurs after first unlock. Ordinary process death does not erase Room, preferences or system alarms. **Force-stop is different:** Android can suppress alarms/work until the user reopens the app. Clearing app data/uninstalling deletes local tasks; cloud backup is disabled. No software implementation can guarantee reminders if the user disables notifications or the OS suppresses them.

If a snooze crosses midnight, delivery reviews the new day's tasks, not yesterday's. Yesterday's tasks stay in carry-over until explicitly moved. Recurrence, widget, automatic end-of-day prompt and backup/import are deliberately deferred beyond this MVP.

Official references checked during development:
- https://developer.android.com/about/versions/14/behavior-changes-14
- https://developer.android.com/develop/background-work/services/alarms
- https://developer.android.com/about/versions/14/changes/schedule-exact-alarms
- https://developer.android.com/build/releases/agp-8-13-0-release-notes

## Validation required before release
Run the included unit tests and build, then follow DEVICE_TESTS.md. Do not treat source-level checks as a substitute for compilation, emulator tests or physical-device tests. A Play Store submission also needs signing, store assets and policy review.
