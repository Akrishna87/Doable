# ✅ Doable for Android

Doable is a **to-do app**: Todoist-style tasks with Quick Add, projects, labels, reminders
and repeating tasks. Everything stays on your phone: there are no accounts, no ads and no
internet access.

It is one of three separate apps that work alike: **Doable** (tasks),
[**Dayline**](https://github.com/Akrishna87/dayline) (calendar) and
[**Jotter**](https://github.com/Akrishna87/jotter) (notes).

## Installing it

1. On your Android phone, open the
   [latest build](https://github.com/Akrishna87/doable/releases/tag/doable-latest)
   and tap **Doable.apk** to download it.
2. Open the downloaded file. Android will ask you to allow installing apps
   from your browser (or Files app). Allow it, then tap **Install**.
   Google Play Protect may warn that it doesn't recognise the app, because
   it isn't from the Play Store. Tap **More details → Install anyway**.
3. Open **Doable**.

To update, install a newer `Doable.apk` the same way. It installs over the old one and
keeps everything.

## What it does

- **Quick Add**: tap **+** and type a task in plain words. Doable highlights what it
  understands as you type:

  | Type | For |
  |---|---|
  | `today`, `tomorrow`, `friday`, `next week`, `this weekend`, `in 3 days`, `dec 25`, `2026-12-25` | the due date |
  | `9am`, `6:30pm`, `21:00`, `at 5`, `noon`, `tonight` | the time (and a reminder) |
  | `p1` `p2` `p3` | priority (red, orange, blue) |
  | `#Work` | an existing project |
  | `@errands` | labels (new ones are made as you go) |
  | `every day`, `every weekday`, `every monday`, `weekly`, `every month`, `yearly` | repeating tasks |

  For example: `Pay rent friday 9am p1 #Home @bills every month`. The chips under the
  text set the same things by hand, and the panel stays open for the next task.
- **Today** shows overdue tasks (with **Reschedule** to move them all to today), then
  today's tasks, and a progress bar for the day.
- **Upcoming** lists the next four weeks day by day, with a week strip to jump around and
  a **+** on each day.
- **Inbox and projects**: tasks without a project go to the Inbox. Make projects with a
  colour in **Browse**. **Labels** collect tasks across projects.
- **Task details**: tap a task to change its title, description, date, time, repeat,
  priority, project and labels, switch its reminder on or off, and add **sub-tasks**.
- **Swipe** a task right to complete it or left to delete it, with **Undo** either way.
  Completing a repeating task moves it to its next date. **Completed** in Browse keeps a
  history you can bring tasks back from.
- **Reminders** notify you at a task's time, with a **Complete** button on the
  notification.
- **Search**, **light and dark themes**, and a **home screen widget** ("Doable: Today")
  for today's and overdue tasks.
- **Backup**: Settings → Export backup saves everything to a JSON file, and Restore from
  backup reads one back. A **Daybook** backup restores too: Doable takes its tasks and
  projects and leaves the notes.

## How it's built

Kotlin and Jetpack Compose with Material 3, and no libraries beyond AndroidX.

- `model/` holds the data and the logic. It is plain Kotlin (no Android imports) with
  unit tests, so a web version can reuse it later: `QuickAdd.kt` reads plain-word tasks,
  and `Dates.kt` handles due dates, repeats and task order.
- `Store.kt` keeps everything in memory and saves it to one JSON file, `Reminders.kt`
  schedules notifications, and `Backup.kt` exports and restores.
- `ui/` has the screens. `widget/TodayWidget.kt` is the home screen widget.
- `ci/smoke-test.sh` runs on an Android emulator for every build. It adds tasks with
  Quick Add, completes one, adds a sub-task and a project, searches, and switches to the
  dark theme. Screenshots from that run are attached to each release.

Builds are made by the [Doable APK workflow](.github/workflows/doable-apk.yml) on
every push to this repo. Doable, Dayline and Jotter are signed with the same sideload key
(`signing/sideload.keystore`), so they can share data with each other later on.

Doable started from the task half of [Daybook](https://github.com/Akrishna87/daybook).
The two apps are independent and keep their data separately.
