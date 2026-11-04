# Earnd architecture

Single Gradle module (`:app`), package `com.earnd.app`. Behavioral source of truth: `REQUIREMENTS.md`.
Everything here is an original design; feature agents implement against these contracts and may
add (not rename) members. Times are epoch millis (`Long`) unless noted; local dates are
`java.time.LocalDate` stored as `epochDay: Long`; minutes are `Int`.

## 1. Package layout

| Package | Responsibility |
|---|---|
| `com.earnd.app` | `EarndApp` (Application), `ServiceLocator` |
| `data` | Room `EarndDatabase`, entities (`data.entity`), DAOs (`data.dao`), `Converters`, `SettingsRepository` |
| `domain` | Pure-ish business logic: `TimeBank`, `HabitService`, `TaskService`, `Recurrence`, `BlockPolicy`, `ChangeBus`, `Clock` |
| `blocking` | `GuardAccessibilityService`, `LockedActivity`, `EssentialApps`, `UrlExtractor` |
| `alarms` | `AlarmPlanner`, `NagEngine`, `AlarmReceiver`, `BootReceiver`, `PendingNotifier`, `RingingAlarmActivity`, channels |
| `ui` | `MainActivity`, navigation graph, screens per feature (`ui.habits`, `ui.tasks`, `ui.home`, `ui.settings`, `ui.apps`), `ui.theme` |
| `widgets` | Glance `HabitWidget` (1x1), `TasksWidget` (4x2), receivers, `WidgetRefresher` |
| `remote` | `RemoteCommandProvider` / receiver reachable from adb shell only, JSON codec |
| `photo` | CameraX capture screen, `PhotoStore` (app-private `files/photos/`) |
| `focus` | Timed focus habits: `FocusManager` (start/cancel/complete/restore), `FocusMath`/`FocusRules` (pure), `FocusActivity`, `FocusNotifier`, `FocusChime`, `FocusReceiver` |

`tools/` (repo root): Linux CLI wrapper `earnd-cli` and agent skill.

## 2. Data layer (Room, `EarndDatabase`, version 1, `exportSchema = true`)

Enums are stored as `String` names via `Converters`.

```kotlin
// data.entity
enum class DaySegment { NONE, MORNING, AFTERNOON, EVENING }
enum class ScheduleKind { DAILY, WEEKDAYS, EVERY_N_DAYS }
enum class CheckInMode { TAP, PHOTO }
enum class Priority { NONE, LOW, MEDIUM, HIGH }
enum class AlarmStyle { SILENT, NOTIFICATION, FULL_SCREEN }
enum class NagMode { OFF, PROGRESSIVE, FIXED }
enum class OwnerKind { TASK, HABIT }
enum class LedgerKind { EARN_HABIT, EARN_TASK, UNDO, SPEND, RESET, ADJUST }
enum class ResetPeriod { DAILY, WEEKLY }
enum class AppRule { LEISURE, ALLOWED }   // user-chosen; essentials are hardcoded, never stored

@Entity(tableName = "habit")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconKey: String,            // key into an app-owned icon set
    val colorArgb: Int,
    val timesPerDay: Int = 1,
    val scheduleKind: ScheduleKind = ScheduleKind.DAILY,
    val weekdayMask: Int = 0b1111111, // bit0 = Monday .. bit6 = Sunday
    val everyNDays: Int = 1,
    val anchorEpochDay: Long,       // start date for EVERY_N_DAYS
    val segment: DaySegment = DaySegment.NONE,
    val targetQuantity: Double? = null,
    val unit: String? = null,
    val minutesPerCheckIn: Int = 5,
    val checkInMode: CheckInMode = CheckInMode.TAP,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long,
)

@Entity(tableName = "habit_checkin", indices = [Index("habitId", "epochDay")],
    foreignKeys = [ForeignKey(HabitEntity::class, ["id"], ["habitId"], onDelete = CASCADE)])
data class CheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val epochDay: Long,
    val at: Long,
    val quantity: Double? = null,
    val photoPath: String? = null,  // relative to files/photos
    val minutesEarned: Int,
)

@Entity(tableName = "task", indices = [Index("dueEpochDay"), Index("parentId")])
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentId: Long? = null,      // non-null => subtask (one level deep)
    val title: String,
    val notes: String = "",
    val dueEpochDay: Long? = null,
    val dueMinuteOfDay: Int? = null, // null => all-day
    val priority: Priority = Priority.NONE,
    val repeatRule: String? = null,  // RRULE subset (domain.RRule), null = once; schema v4
    val repeatFromCompletion: Boolean = false,
    val repeatAnchorEpochDay: Long? = null, // series start (first due day)
    val repeatOccurrence: Int = 1,   // 1-based, for COUNT
    val rewardMinutes: Int? = null,  // null => earns nothing
    val completedAt: Long? = null,
    val seriesId: Long? = null,      // shared by all occurrences of a recurring task
    val sortOrder: Int = 0,
    val createdAt: Long,
)

@Entity(tableName = "alarm_config", primaryKeys = ["ownerKind", "ownerId"])
data class AlarmConfigEntity(
    val ownerKind: OwnerKind,
    val ownerId: Long,
    val atDue: Boolean = true,
    val offsetsBeforeMin: String = "",   // CSV of minutes, e.g. "60,15"
    val habitReminderMinuteOfDay: Int? = null,
    val style: AlarmStyle = AlarmStyle.NOTIFICATION,
    val nagMode: NagMode = NagMode.PROGRESSIVE,
    val nagEveryMin: Int = 30,
    val nagMaxCount: Int? = null,
    val soundUri: String? = null,
    val vibrate: Boolean = true,
    val snoozeMaxCount: Int = 2,
)

@Entity(tableName = "nag_state", primaryKeys = ["ownerKind", "ownerId"])
data class NagStateEntity(
    val ownerKind: OwnerKind,
    val ownerId: Long,
    val dueAt: Long,
    val nagCount: Int = 0,
    val lastNagAt: Long? = null,
    val nextNagAt: Long? = null,
    val snoozeCount: Int = 0,
)

@Entity(tableName = "ledger", indices = [Index("at")])
data class LedgerEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val kind: LedgerKind,
    val minutes: Int,                // signed: + earned, - spent/reset/undo
    val sourceKind: OwnerKind? = null,
    val sourceId: Long? = null,      // check-in id or task id
    val periodKey: Long,             // epochDay of the period start this entry belongs to
)

@Entity(tableName = "unlock_session")
data class UnlockSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val plannedEndAt: Long,
    val endedAt: Long? = null,
    val reason: String,              // "spend", "all_done", "bedtime_grace"
)

@Entity(tableName = "app_rule")
data class AppRuleEntity(@PrimaryKey val key: String, val rule: AppRule, val isWebsite: Boolean = false, val label: String)

@Entity(tableName = "setting")
data class SettingEntity(@PrimaryKey val key: String, val value: String)
```

DAOs (`data.dao`), all `suspend` or `Flow`:

```kotlin
@Dao interface HabitDao {
  fun observeActive(): Flow<List<HabitEntity>>
  suspend fun get(id: Long): HabitEntity?
  suspend fun all(): List<HabitEntity>
  @Upsert suspend fun upsert(h: HabitEntity): Long
  suspend fun archive(id: Long)
  @Delete suspend fun delete(h: HabitEntity)
}
@Dao interface CheckInDao {
  fun observeForDay(epochDay: Long): Flow<List<CheckInEntity>>
  suspend fun forHabitBetween(habitId: Long, fromDay: Long, toDay: Long): List<CheckInEntity>
  suspend fun countFor(habitId: Long, epochDay: Long): Int
  @Insert suspend fun insert(c: CheckInEntity): Long
  suspend fun get(id: Long): CheckInEntity?
  suspend fun deleteById(id: Long)
}
@Dao interface TaskDao {
  fun observeOpen(): Flow<List<TaskEntity>>             // top-level, not completed
  fun observeCompleted(limit: Int): Flow<List<TaskEntity>>
  fun observeSubtasks(parentId: Long): Flow<List<TaskEntity>>
  suspend fun get(id: Long): TaskEntity?
  suspend fun openRecurringDueBefore(epochDay: Long): List<TaskEntity>
  @Upsert suspend fun upsert(t: TaskEntity): Long
  suspend fun deleteById(id: Long)                       // cascades subtasks in code
}
@Dao interface AlarmConfigDao { suspend fun get(kind: OwnerKind, id: Long): AlarmConfigEntity?; @Upsert suspend fun upsert(c: AlarmConfigEntity); suspend fun delete(kind: OwnerKind, id: Long) }
@Dao interface NagStateDao { fun observeAll(): Flow<List<NagStateEntity>>; suspend fun all(): List<NagStateEntity>; @Upsert suspend fun upsert(s: NagStateEntity); suspend fun delete(kind: OwnerKind, id: Long) }
@Dao interface LedgerDao { fun observeSum(periodKey: Long): Flow<Int?>; suspend fun sum(periodKey: Long): Int; @Insert suspend fun insert(e: LedgerEntryEntity): Long; suspend fun forSource(kind: OwnerKind, id: Long): List<LedgerEntryEntity> }
@Dao interface SessionDao { fun observeActive(): Flow<UnlockSessionEntity?>; suspend fun active(): UnlockSessionEntity?; @Insert suspend fun insert(s: UnlockSessionEntity): Long; @Update suspend fun update(s: UnlockSessionEntity) }
@Dao interface AppRuleDao { fun observeAll(): Flow<List<AppRuleEntity>>; suspend fun all(): List<AppRuleEntity>; @Upsert suspend fun upsert(r: AppRuleEntity); suspend fun delete(key: String) }
@Dao interface SettingDao { fun observeAll(): Flow<List<SettingEntity>>; suspend fun get(key: String): String?; @Upsert suspend fun put(s: SettingEntity) }
```

### Settings (`data.SettingsRepository`, typed facade over `setting` table)

```kotlin
data class Settings(
  val resetPeriod: ResetPeriod = ResetPeriod.DAILY,
  val weekStartsOn: DayOfWeek = DayOfWeek.MONDAY,
  val allDoneUnlock: Boolean = true,
  val hardMode: Boolean = false,
  val weakenCooldownMin: Int = 10,
  val bedtimeEnabled: Boolean = false,
  val bedtimeStartMin: Int = 23 * 60, val bedtimeEndMin: Int = 7 * 60,
  val bedtimeGraceMin: Int = 2,
  val vacationFromDay: Long? = null, val vacationToDay: Long? = null,
  val defaultTaskRewardMin: Int? = 5,
  val quietStartMin: Int = 0, val quietEndMin: Int = 8 * 60,
  val blockingEnabled: Boolean = true,
)
class SettingsRepository(dao: SettingDao) {
  val settings: Flow<Settings>
  suspend fun current(): Settings
  suspend fun update(transform: (Settings) -> Settings)
  // hard-mode cooldown: a weakening change is queued and applied after weakenCooldownMin
  suspend fun requestWeakening(transform: (Settings) -> Settings): Long /* appliesAt */
}
```

## 3. Domain services (`domain`)

```kotlin
interface Clock { fun now(): Long; fun zone(): ZoneId }   // SystemClock impl; fake in tests

data class Balance(val minutes: Int, val periodKey: Long, val resetsAt: Long)
sealed interface SpendResult { data class Started(val session: UnlockSessionEntity) : SpendResult
  data class Refused(val reason: String) : SpendResult }

class TimeBank(ledger: LedgerDao, sessions: SessionDao, settings: SettingsRepository, clock: Clock, bus: ChangeBus) {
  fun observeBalance(): Flow<Balance>
  suspend fun balance(now: Long = clock.now()): Balance
  suspend fun earn(minutes: Int, kind: LedgerKind, sourceKind: OwnerKind?, sourceId: Long?): Long
  suspend fun reverse(sourceKind: OwnerKind, sourceId: Long)       // undo of a check-in / task
  suspend fun spend(minutes: Int): SpendResult                    // refused in hard mode / no balance
  suspend fun startSession(minutes: Int, reason: String): SpendResult
  suspend fun endSession(now: Long = clock.now())                 // refunds unused minutes when ended early
  fun observeActiveSession(): Flow<UnlockSessionEntity?>
  suspend fun resetIfPeriodRolled(now: Long = clock.now())        // writes RESET entry zeroing balance
}

data class HabitDay(val habit: HabitEntity, val done: Int, val required: Int)
class HabitService(habits: HabitDao, checkIns: CheckInDao, bank: TimeBank, clock: Clock, bus: ChangeBus) {
  fun isScheduled(h: HabitEntity, day: LocalDate): Boolean
  fun observeToday(): Flow<List<HabitDay>>
  suspend fun checkIn(habitId: Long, quantity: Double? = null, photoPath: String? = null): CheckInEntity
  suspend fun undoCheckIn(checkInId: Long)
  suspend fun allDone(day: LocalDate): Boolean        // false when nothing scheduled
  suspend fun anythingScheduled(day: LocalDate): Boolean
  suspend fun streak(habitId: Long, today: LocalDate): Int   // vacation days skipped
  suspend fun save(h: HabitEntity): Long
  suspend fun delete(habitId: Long)
}

enum class TaskBucket { OVERDUE, TODAY, UPCOMING, NO_DATE, COMPLETED }
data class RRule(...)   // FREQ/INTERVAL/BYDAY(+ordinals)/BYMONTHDAY/BYMONTH/COUNT/UNTIL; parse()/toString() lossless;
                        // next(start, after), occursOn, afterCompletion, summary(). Month-end and Feb 29 clamp.
object Recurrence { fun next(t, afterDay); fun following(t, on); fun rolledForward(t, today); fun summary(t) }
class TaskService(tasks: TaskDao, bank: TimeBank, settings: SettingsRepository, clock: Clock, bus: ChangeBus) {
  fun observeBucket(bucket: TaskBucket): Flow<List<TaskEntity>>
  suspend fun save(t: TaskEntity): Long                 // applies default reward when null & new
  suspend fun complete(taskId: Long): TaskEntity?       // returns spawned next occurrence, if any
  suspend fun uncomplete(taskId: Long)
  suspend fun rollForward(today: LocalDate): Int        // moves stale recurring occurrences; returns count
  suspend fun delete(taskId: Long)
}

sealed interface BlockDecision {
  data object Allow : BlockDecision
  data class Block(val cause: BlockCause, val until: Long?) : BlockDecision
}
enum class BlockCause { NO_BALANCE, BEDTIME, HARD_MODE }
class BlockPolicy(rules: AppRuleDao, sessions: SessionDao, habits: HabitService, settings: SettingsRepository, essentials: EssentialApps) {
  // pkg = foreground package; url = host from browser bar or null
  suspend fun decide(pkg: String, url: String?, now: Long): BlockDecision
}
```
Decision order: blocking disabled / vacation -> Allow; essential pkg -> Allow; not leisure (pkg nor url host) -> Allow;
bedtime window (outside one-off grace) -> Block(BEDTIME); active session -> Allow; all-done unlock -> Allow;
nothing scheduled today -> Allow; otherwise Block(NO_BALANCE).
Metering (default model for every leisure app/site): `AppRuleEntity.meterPct` (0/25/50/100, null = 100; db v10
migrates FULL rows to 100). `BlockRules.leisureRule` matches the rule (site host preferred, then package);
free unlocks (session, all-done, segment, nothing scheduled) are checked before metering; metered with balance > 0
gives `BlockDecision.Metered(rate, key)`; hard mode disables it. `GuardAccessibilityService.recheck` (events:
window state/content/windows-changed, screen on/off, user present, change bus; plus a 1 s ticker while a leisure
target is in front) decides: Block -> lock screen; Metered without `Grants.active(key)` -> gate (`LockedActivity`
cause CONFIRM, `gateIntent`; accepting calls `Grants.grant`, the guard calls `enter`/`leave` so a grant survives
2 min after leaving); Metered with grant -> counted. `CountRules` (pure) picks what counts: foreground target plus
visible leisure windows from `getWindows()` (PiP / split-screen), highest rate wins, and `CountRules.active`
(screen interactive, keyguard not locked, no call by telephony state or audio mode). `MeterEngine` (`MeterActive`
key/pkg/rate/since) charges `advance(now, allowed)`; `MeterMath` keeps elapsed x rate as a carry (raw setting
`meter.carry`) and `msUntilZero` predicts the zero time, which the guard turns into an exact `delay` job
(min with the next lot expiry) that re-checks and shows the lock. Debits go through `TimeBank.debitMetered`
(FIFO over lots, ledger kind METER). `MeterOverlay` is a plain View in a TYPE_ACCESSIBILITY_OVERLAY window
(NOT_FOCUSABLE | NOT_TOUCH_MODAL); formatting/snap/vibration thresholds are pure in `OverlayMath`; positions per
package in SharedPreferences `meter_overlay`. `Charging` holds rates and parsing; `PendingRuleChanges.setCharging`
applies stricter changes at once and queues loosening ones behind the hard-mode cooldown. The remote `status`
includes `counting`.
Debug build (DUMP-protected receivers): `DebugChargingReceiver` sets a rate; `DebugMeterReceiver`
(`DEBUG_METER_STATE`) prints the meter state (counting target, rate, balance, remaining, carry, grants, overlay).
`HabitEntity.overTargetReward` (v9, default true) makes `HabitProgress.checkInReward` return 0 beyond `timesPerDay`.

```kotlin
// alarms
data class PlannedAlarm(val ownerKind: OwnerKind, val ownerId: Long, val at: Long, val style: AlarmStyle)
class AlarmPlanner(context: Context, tasks: TaskDao, habits: HabitDao, configs: AlarmConfigDao, nag: NagEngine, clock: Clock) {
  suspend fun plan(now: Long): List<PlannedAlarm>     // pure computation
  suspend fun rearmAll()                              // cancel + set exact alarms (boot, update, tz change, bus)
  suspend fun rearm(ownerKind: OwnerKind, ownerId: Long)
}
data class PendingItem(val ownerKind: OwnerKind, val ownerId: Long, val label: String, val nagCount: Int)
class NagEngine(nags: NagStateDao, configs: AlarmConfigDao, settings: SettingsRepository, clock: Clock) {
  fun nextNagAt(state: NagStateEntity, cfg: AlarmConfigEntity?, now: Long): Long?   // honours quiet hours, digest
  suspend fun onDue(ownerKind: OwnerKind, ownerId: Long, dueAt: Long)
  suspend fun onFired(now: Long): List<PendingItem>   // bumps counts, returns sorted most-nagged first
  suspend fun resolve(ownerKind: OwnerKind, ownerId: Long)
  suspend fun pending(now: Long): List<PendingItem>
}
```
Default progressive gaps (minutes after due): `[10, 15, 25, 40, 60, 90, 120, 180, 240]`, then 240 repeating;
items overdue > 7 days collapse into one daily digest. Quiet hours shift a nag to quiet end.

## 4. ServiceLocator and ChangeBus

`ServiceLocator` (object) holds `appContext` and lazily-built singletons: `db`, `settings`, `clock`,
`changeBus`, `timeBank`, `habitService`, `taskService`, `blockPolicy`, `alarmPlanner`, `nagEngine`,
`photoStore`. Each feature adds its own `val x by lazy { ... }`. No DI framework.

`ChangeBus` (`domain.ChangeBus`) is a process-wide `SharedFlow<ChangeKind>`; every service emits after a
write (`HABITS, CHECKINS, TASKS, BALANCE, SESSION, SETTINGS, BLOCKLIST, ALARMS`). `EarndApp` starts one
collector per consumer: widgets (`WidgetRefresher.updateAll`), alarms (`AlarmPlanner.rearmAll`, debounced),
blocking service (refresh cached rules). UI observes Room flows directly.

### Focus sessions (`focus`, DB v5)
`habit.focusMinutes` (nullable; migration 4->5 adds it and the single-row `focus_session` table: habitId,
startedAt, durationMs, state ACTIVE/DONE/CANCELLED). `FocusManager.start` refuses when one is running
(remote never calls it: `focus` is a forbidden remote root). State changes use a conditional
`UPDATE ... WHERE state='ACTIVE'`, so exactly one caller completes or cancels. Completion triggers: exact
alarm (`FocusReceiver`), the timer screen tick, the guard's 30 s tick and `restore()` (app start, boot,
package replaced). Completion = `HabitService.checkIn` + chime + notice. `BlockPolicy` takes a
`focusActive` lambda: while true it ignores sessions/vacation and applies `FocusRules.allowed`
(own package, user ALLOWED, essentials minus launcher and Settings/installer); a block has
`BlockCause.FOCUS` and the guard shows `FocusActivity` instead of `LockedActivity`. Emits `ChangeKind.FOCUS`.

DB v6 (migration 5->6, additive): `habit.focusOptions` ("min:reward,..." reward optional), `habit.stopwatch`,
`stopwatchRate`, `stopwatchMinMin`, `stopwatchCapMin`; `focus_session.stopwatch` and `rewardMinutes`
(chosen option). Pure rules in `domain/TimedHabit` (options, linear default reward, stopwatch reward,
max rate). `FocusManager.start(habitId, minutes?)` returns `FocusStart.Choose` when several options exist;
`FocusPicker`/`FocusPickerHost` (at the nav root) show the picker. Stopwatch sessions have no alarm,
`FocusMath.isRunning` treats them as running while ACTIVE, and `finishStopwatch()` records the elapsed
time (`durationMs`) with a conditional update, then checks in with `rewardOverride`.

### Comeback bonus (`domain/Comeback`)
Pure: `missedOccasions(habit, today, activeDays, skip)` and `bonus(base, n)`. `HabitService.checkInWithBonus`
(also `rewardOverride` for focus options/stopwatch) earns `LedgerKind.COMEBACK` on the same HABIT source id,
so `TimeBank.reverse` on undo removes both. `HabitDay.comebackMissed` feeds the row badge. Setting
`comebackBonus` (tighten-only remotely). Remote habit updates may only tighten timed fields:
`RemoteDispatcher.timedWeakening` (no higher earned-per-minute rate, at most 1.0 when the habit was not
timed; no lower minimum, no raised/removed cap, no turning timing off).

## 5. Manifest fragments

`app/src/main/AndroidManifest.xml` holds only the application + `MainActivity`. Each feature writes
`manifest/<area>.xml` (`blocking.xml`, `alarms.xml`, `widgets.xml`, `remote.xml`, `photo.xml`) containing
a full `<manifest>` with just its `<uses-permission>` and `<application>` children. The integrator merges
them into the base manifest. Never add `INTERNET`. Remote access is protected by a custom
`signature|privileged` permission plus a `Binder.getCallingUid() == SHELL_UID` (2000) check.

## 6. Conventions
- Small files, one public type per file; Compose screens take state + lambdas, ViewModels via `viewModel { }` factories reading `ServiceLocator`.
- All DB writes go through domain services so the ledger and ChangeBus stay consistent.
- Copy is calm and non-gamified ("You have 25 minutes saved"), never shaming.

## 7. Implementation notes (data + domain, as built)

Additions to the contracts above (no renames):
- Enums use Room's built-in by-name enum mapping; there is no `Converters` class. Schemas in `app/schemas` are committed.
- Extra DAO members: `HabitDao.active()`, `CheckInDao.forDay()`, `TaskDao.subtasks()/open()/nextOpenInSeries()`,
  `AlarmConfigDao.all()`, `NagStateDao.get()`, `LedgerDao.observeRecent()`, `SessionDao.lastWithReason()`,
  `SettingDao.all()/delete()`. `SessionDao.active()` returns the un-ended session even if past `plannedEndAt`;
  use `TimeBank.activeSession(now)` for an expiry-aware read.
- `SessionReason` constants (`data.entity`): `SPEND`, `ALL_DONE`, `BEDTIME_GRACE`.
- `Settings` is in `data/Settings.kt` and has `onVacation(epochDay)`. `SettingsRepository(dao, clock = SystemTimeClock, bus = null)`
  also has `pendingWeakeningAt()`, `cancelPendingWeakening()`, `applyDueWeakening(now)` (also run by `current()`),
  and `rawGet/rawPut` for internal values. The queued weakening is stored durably under `pending.*` keys.
- `domain.Clock` with `SystemTimeClock` and extensions `today()`, `localDate(at)`, `startOfDay(day)`, `minuteOfDay(at)`.
- Pure helpers: `Periods`, `DailyWindow` (wrapping minute-of-day window for bedtime and quiet hours), `HabitSchedule`
  (`isScheduled`, `streak`), `Recurrence`, `BlockRules` (`decide(BlockFacts)`, `isLeisure`, `isUserAllowed`), `alarms.NagSchedule`.
- `TimeBank` adds `startAllDoneUnlock()`, `startBedtimeGrace()` (one per bedtime night), `activeSession(now)`,
  `periodKey(settings, now)`, `TimeBank.bedtimeWindow(settings)`, and `RefuseReason` constants. A spend session ended
  early refunds the unused whole minutes (ADJUST) when still in the same period.
- `HabitService(..., settings: SettingsRepository? = null)`; `undoCheckIn` returns the photo path so the caller can delete
  the file. It also adds `history()` and `archive()`, and `HabitDay.complete`. Check-ins beyond `timesPerDay` earn 0.
- `TaskService` adds `observeSubtasks`, `get` and `TaskService.bucketOf(task, todayEpochDay)`. Completing a recurring
  task spawns the next occurrence after max(due, today), with copies of its subtasks. `uncomplete` deletes that occurrence.
- `BlockPolicy(..., clock: Clock = SystemTimeClock)`. Packages the user marks `ALLOWED` count as essential.
  `blocking.EssentialApps` is an interface; `CoreEssentials` lists fixed packages, and `SystemEssentialApps(context)`
  resolves the default dialer, SMS app, HOME launcher, geo handler and DIAL handler (`refresh()` after package changes).
  The blocking manifest fragment must declare `<queries>` for the HOME, `geo:` and DIAL intents.
- `NagEngine(..., labels: suspend (OwnerKind, Long) -> String)` adds `snooze(kind, id, now)` (10 min, capped by
  `snoozeMaxCount`) and `earliestNagAt()`. Progressive gaps are the intervals between successive nags. Items more than
  7 days overdue share one daily digest slot at quiet-hours end.
