# Device acceptance checks (not yet executed)

Use Android 13 and Android 16 emulators plus a physical Samsung device. Test light/dark themes, large font, portrait/landscape and keyboard visibility.

1. Add, edit, delete and complete tasks. Relaunch and verify state. Set tomorrow and another date; confirm Upcoming counts and correct day filtering.
2. Grant, deny and revoke notification permission. Confirm checklist remains usable and settings link enables recovery. Disable the channel separately and confirm Android settings exposes it.
3. Test saved reminder settings with unfinished tasks, all tasks complete, quiet hours enabled, and reminders disabled. No notification in the latter three cases. Notification review must show the current checklist.
4. Use notification Done action. Confirm exactly that task completes, remaining notification content updates, and final completion removes notification. Replaying the same action must not toggle a completed task back.
5. Snooze 15, 30, 60 and custom minutes. Relaunch app; snooze persists. Verify no earlier regular check-in interrupts the snooze. Test quiet-hour deferral and crossing midnight.
6. Schedule a custom reminder several minutes ahead. Test screen on/off and Doze (`adb shell dumpsys deviceidle force-idle`; undo with `unforce`). Record actual delays; inexact timing is expected.
7. Kill the background process (not force-stop), reboot, change timezone and change clock. Confirm reminders are restored and no duplicate burst appears. Force-stop then reopen; confirm reconciliation resumes.
8. Leave app open across midnight. Today's date/list refreshes. Past incomplete tasks appear in carry-over; moving to tomorrow is durable. No silent deletion or rollover.
9. Dismiss a reminder, leave a task unfinished and wait for the next configured time. Swiping must not disable future reminders.
10. Test locked screen privacy, DND, muted channel, battery restriction and denied permission. Do not promise a banner where system settings prohibit one.
11. Exercise DST transitions, daytime/overnight quiet periods and equal start/end rejection. Run `testDebugUnitTest` for scheduling boundaries.
