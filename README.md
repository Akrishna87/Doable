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

Doable aims to have everything in Todoist Pro except the AI and the things that need a
server (see the end of this list).

- **Quick Add**: tap **+** and type a task in plain words. Doable highlights what it
  understands as you type:

  | Type | For |
  |---|---|
  | `today`, `tomorrow`, `friday`, `next week`, `this weekend`, `in 3 days`, `dec 25`, `2026-12-25` | the due date |
  | `9am`, `6:30pm`, `21:00`, `at 5`, `noon`, `tonight` | the time |
  | `for 30min`, `for 1.5h` | how long it takes |
  | `{friday}`, `{dec 31}` | a deadline |
  | `!30min`, `!1h`, `!8am` | a reminder before the due time, or at a time |
  | `p1` `p2` `p3` | priority (red, orange, blue) |
  | `#Work`, `/Doing` | an existing project, and a section in it |
  | `@errands` | labels (new ones are made as you go) |
  | `every day`, `every weekday`, `every mon, wed`, `every 3 days`, `every 2nd tue`, `every last day`, `every! 2 weeks` (counted from when you complete it) | repeating tasks |

  For example: `Pay rent friday 9am p1 #Home @bills every month`. The chips under the
  text set the same things by hand, and the panel stays open for the next task. You can
  also share text to Doable from any app, use the **Quick Settings tile**, or long-press
  the app icon.
- **Today** shows overdue tasks (with **Reschedule**), then today's, with a progress bar;
  **Upcoming** shows the coming days as a list or a month calendar. Both can be sorted,
  grouped and filtered from their view options.
- **Projects** with sub-projects, **sections**, favourites, colours and archiving, viewed
  as a **list**, a **board** (a column per section) or a **calendar**, each with its own
  sort, grouping and "show completed". Drag to reorder, move tasks between projects and
  sections, and import or export a project as a **Todoist CSV** (which works as a
  template).
- **Labels** and **saved filters** in Todoist's filter language, for example
  `today | overdue`, `#Work & p1`, `@home* & !no date`, `due before: next week`,
  `has: comments`, and comma-separated lists like `today, overdue`.
- **Task details**: description, due date and time, **duration**, **deadline**,
  **repeat** (including a custom repeat dialog), priority, project, section, labels,
  **several reminders**, **sub-tasks**, **comments** with **attachments** and photos,
  duplicate, copy as text, and the task's history. The repeat dialog can also end a
  repeat on a date.
- **Swipe** actions you can choose for each direction (complete, schedule, delete), with **Undo**.
  Completing a repeating task moves it to its next date. **Completed** keeps a history you
  can bring tasks back from, and the **activity log** shows everything you've done.
- **Productivity**: Karma points and levels, daily and weekly goals, streaks, days off
  and vacation mode.
- **Reminders** with **Complete** and **Snooze** on the notification, an automatic
  reminder at the due time if you want one, and an optional daily summary.
- **Search** (plain words or a filter query), **light and dark themes** with accent
  colours, a choice of start page, and a **home screen widget** for today's tasks.
- **Backup**: Settings → Export backup saves everything to a JSON file, and Restore from
  backup reads one back. A **Daybook** backup restores too: Doable takes its tasks and
  projects and leaves the notes.

Left out, because they need an account and a server: syncing between devices, shared
projects, assigning tasks to people, and calendar feeds.

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
