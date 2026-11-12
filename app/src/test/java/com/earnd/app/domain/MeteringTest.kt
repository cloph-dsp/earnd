package com.earnd.app.domain

import com.earnd.app.FakeClock
import com.earnd.app.FakeLedgerDao
import com.earnd.app.FakeSessionDao
import com.earnd.app.FakeSettingDao
import com.earnd.app.data.SettingsRepository
import com.earnd.app.data.entity.AppRule
import com.earnd.app.data.entity.AppRuleEntity
import com.earnd.app.data.entity.CheckInEntity
import com.earnd.app.data.entity.HabitEntity
import com.earnd.app.data.entity.LedgerKind
import com.earnd.app.data.entity.OwnerKind
import com.earnd.app.data.entity.ResetPeriod
import com.earnd.app.ui.settings.SettingsChanges
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class MeteringTest {
    private val clock = FakeClock.at(LocalDateTime.of(2026, 3, 4, 10, 0))
    private val ledger = FakeLedgerDao()
    private val sessions = FakeSessionDao()
    private val settings = SettingsRepository(FakeSettingDao(), clock)
    private val bank = TimeBank(ledger, sessions, settings, clock, ChangeBus())
    private var carry = 0L
    private val engine = MeterEngine({ bank.debitMetered(it) }, { carry }, { carry = it })

    private fun act(pct: Int?) = pct?.let { MeterActive("k", "p", it, clock.now()) }

    private suspend fun earn(m: Int, src: Long) = bank.earn(m, LedgerKind.EARN_HABIT, OwnerKind.HABIT, src)

    /** Foreground at [pct] for [seconds], in 30 s ticks like the guard. Returns minutes debited. */
    private suspend fun use(pct: Int?, seconds: Int): Int {
        engine.switchTo(act(pct), clock.now())
        var debited = 0
        repeat(seconds / 30) {
            clock.nowMs += 30_000
            debited += engine.advance(clock.now())
        }
        return debited
    }

    // ---- math ----

    @Test fun accrueRates() {
        // 60 s of foreground at each rate = 60/30/15/0 s charged
        assertEquals(MeterMath.Step(1, 0), MeterMath.accrue(0, 60_000, 100))
        assertEquals(MeterMath.Step(0, 30_000L * 100), MeterMath.accrue(0, 60_000, 50))
        assertEquals(MeterMath.Step(0, 15_000L * 100), MeterMath.accrue(0, 60_000, 25))
        assertEquals(MeterMath.Step(0, 0), MeterMath.accrue(0, 60_000, 0))
    }

    @Test fun carryAccumulatesFractionalSeconds() {
        var c = 0L
        var units = 0
        repeat(8) { // 8 x 15 s at 50% = 60 s charged
            val s = MeterMath.accrue(c, 15_000, 50)
            c = s.carry; units += s.units
        }
        assertEquals(1, units)
        assertEquals(0L, c)
        assertEquals(30, MeterMath.carrySeconds(MeterMath.accrue(0, 60_000, 50).carry))
    }

    @Test fun oneStepIsCappedAgainstLongGaps() {
        assertEquals(MeterMath.accrue(0, MeterMath.MAX_STEP_MS, 100), MeterMath.accrue(0, 3_600_000, 100))
        assertEquals(MeterMath.Step(0, 0), MeterMath.accrue(0, -5, 100))
    }

    @Test fun strictnessOrdering() {
        assertFalse(Charging.loosens(null, 100))
        assertEquals(100, Charging.effective(null))
        assertTrue(Charging.loosens(50, 25))
        assertFalse(Charging.loosens(50, 50))
        assertFalse(Charging.loosens(50, 100))
        assertFalse(Charging.loosens(25, null))
        assertEquals(Charging.Rate(100), Charging.parse("FULL"))
        assertEquals(Charging.Rate(50), Charging.parse("50%"))
        assertEquals(Charging.Rate(75), Charging.parse("75%"))
        assertTrue(Charging.loosens(100, 75))
        assertFalse(Charging.loosens(75, 100))
        assertTrue(Charging.loosens(75, 50))
        assertNull(Charging.parse("30"))
        assertNull(Charging.parse(""))
        assertEquals("TIDAL · 50%", Charging.note("TIDAL", 50))
    }

    // ---- engine + bank ----

    @Test fun halfRateChargesOneMinutePerTwoMinutes() = runTest {
        earn(10, 1)
        assertEquals(0, use(50, 90))   // 45 s charged, nothing yet
        assertEquals(10, bank.balance().minutes)
        assertEquals(1, use(50, 30))   // 60 s charged in total
        assertEquals(9, bank.balance().minutes)
        use(50, 120)
        assertEquals(8, bank.balance().minutes)
    }

    @Test fun fullRateAndQuarterRate() = runTest {
        earn(30, 1)
        use(100, 180)
        assertEquals(27, bank.balance().minutes)
        use(25, 240)
        assertEquals(26, bank.balance().minutes)
    }

    @Test fun zeroRateIsFreeAndNoRateCharges() = runTest {
        earn(5, 1)
        use(0, 600)
        use(null, 600)
        assertEquals(5, bank.balance().minutes)
    }

    @Test fun carrySurvivesSwitchingApps() = runTest {
        earn(10, 1)
        use(50, 60)    // 30 s charged
        use(null, 300) // other app
        use(50, 60)    // another 30 s
        assertEquals(9, bank.balance().minutes)
        assertEquals(0L, carry)
    }

    @Test fun carryIsPersistedAndReloaded() = runTest {
        earn(10, 1)
        use(50, 60)
        assertEquals(30_000L * 100, carry)
        val fresh = MeterEngine({ bank.debitMetered(it) }, { carry }, { carry = it })
        fresh.switchTo(act(50), clock.now())
        clock.nowMs += 60_000
        assertEquals(1, fresh.advance(clock.now()))
    }

    @Test fun screenOffChargesNothing() = runTest {
        earn(10, 1)
        engine.switchTo(act(100), clock.now())
        clock.nowMs += 60_000
        assertEquals(0, engine.advance(clock.now(), interactive = false))
        assertEquals(10, bank.balance().minutes)
    }

    @Test fun stopChargesUpToNowThenNothing() = runTest {
        earn(10, 1)
        engine.switchTo(act(100), clock.now())
        clock.nowMs += 60_000
        assertEquals(1, engine.stop(clock.now()))
        clock.nowMs += 60_000
        assertEquals(0, engine.advance(clock.now()))
        assertEquals(9, bank.balance().minutes)
    }

    @Test fun debitIsFifoAcrossLots() = runTest {
        earn(10, 1)
        clock.advanceMin(60)
        earn(5, 2)
        assertEquals(12, bank.debitMetered(12))
        assertEquals(3, bank.balance().minutes)
        // the oldest lot is consumed first: when it would have expired (24 h after the first earn) only 3 min remain anyway
        val rows = ledger.rows.value
        assertEquals(listOf(0, 3), rows.filter { it.kind == LedgerKind.EARN_HABIT }.map { it.remaining })
        val meter = rows.single { it.kind == LedgerKind.METER }
        assertEquals(-12, meter.minutes)
        assertNull(meter.expiresAt)
    }

    @Test fun fifoMeansOldMinutesGoFirstAndNewOnesSurviveExpiry() = runTest {
        earn(4, 1)
        clock.advanceMin(23 * 60)
        earn(6, 2)
        bank.debitMetered(4)           // takes the old lot (about to expire)
        clock.advanceMin(2 * 60)       // old lot would have expired by now
        assertEquals(6, bank.balance().minutes)
    }

    @Test fun zeroBalanceDebitsNothingAndForgivesShortfall() = runTest {
        assertEquals(0, bank.debitMetered(3))
        assertTrue(ledger.rows.value.isEmpty())
        earn(2, 1)
        assertEquals(2, bank.debitMetered(5))
        assertEquals(0, bank.balance().minutes)
        assertEquals(0, bank.debitMetered(1))
    }

    @Test fun debitedBalanceHitsZeroThenStaysThere() = runTest {
        earn(1, 1)
        assertEquals(1, use(100, 60))
        assertEquals(0, bank.balance().minutes)
        assertEquals(0, use(100, 120))
    }

    @Test fun debitWorksInDailyMode() = runTest {
        settings.update { it.copy(resetPeriod = ResetPeriod.DAILY) }
        earn(10, 1)
        assertEquals(4, bank.debitMetered(4))
        assertEquals(6, bank.balance().minutes)
        assertEquals(6, bank.debitMetered(100))
        assertEquals(0, bank.balance().minutes)
    }

    @Test fun meteringDoesNotTouchSessionsOrRefunds() = runTest {
        earn(20, 1)
        val started = bank.spend(10) as SpendResult.Started
        bank.debitMetered(3)
        clock.advanceMin(5)
        bank.endSession()
        // 20 - 10 spent - 3 metered + 5 unused minutes refunded
        assertEquals(12, bank.balance().minutes)
        assertEquals(started.session.id, sessions.rows.value.single().id)
    }

    // ---- block decision ----

    private val locked = BlockFacts(
        blockingEnabled = true, onVacation = false, essential = false, leisure = true,
        inBedtime = false, bedtimeEndsAt = null, graceActive = false, sessionActive = false,
        allDoneUnlock = false, nothingScheduled = false,
    )

    @Test fun meteredAppOpensOnlyWithBalance() {
        assertEquals(BlockDecision.Metered(50), BlockRules.decide(locked.copy(meteredPct = 50, balancePositive = true)))
        assertEquals(BlockDecision.Block(BlockCause.NO_BALANCE, null), BlockRules.decide(locked.copy(meteredPct = 50, balancePositive = false)))
        // A 0 % rule is free: no gate and no balance needed.
        assertEquals(BlockDecision.Allow, BlockRules.decide(locked.copy(meteredPct = 0, balancePositive = false)))
        assertEquals(BlockDecision.Block(BlockCause.NO_BALANCE, null), BlockRules.decide(locked.copy(balancePositive = true)))
    }

    @Test fun sessionCoversMeteredAppsWithoutDoubleCharging() {
        // A running session decides first: plain Allow, so the guard sets no meter rate.
        assertEquals(BlockDecision.Allow, BlockRules.decide(locked.copy(sessionActive = true, meteredPct = 50, balancePositive = true)))
    }

    @Test fun bedtimeStillBlocksMeteredApps() {
        val d = BlockRules.decide(locked.copy(inBedtime = true, bedtimeEndsAt = 7L, meteredPct = 100, balancePositive = true))
        assertEquals(BlockDecision.Block(BlockCause.BEDTIME, 7L), d)
        assertEquals(BlockDecision.Allow, BlockRules.decide(locked.copy(onVacation = true, meteredPct = 50, balancePositive = true)))
    }

    @Test fun meterRateOnlyForBlockedPackages() {
        val rules = listOf(
            AppRuleEntity("com.aspiro.tidal", AppRule.LEISURE, label = "TIDAL", meterPct = 50),
            AppRuleEntity("com.full", AppRule.LEISURE, label = "Full"),
            AppRuleEntity("com.allowed", AppRule.ALLOWED, label = "A", meterPct = 25),
            AppRuleEntity("site.com", AppRule.LEISURE, isWebsite = true, label = "S", meterPct = 25),
        )
        assertEquals(50, BlockRules.meterRate(rules, "com.aspiro.tidal"))
        assertEquals(100, BlockRules.meterRate(rules, "com.full")) // legacy FULL row = default 100
        assertNull(BlockRules.meterRate(rules, "com.allowed"))
        assertNull(BlockRules.meterRate(rules, "site.com"))
    }

    // ---- flags ----

    @Test fun rollingExpiryFifteenHoursAndTightening() {
        val old = com.earnd.app.data.Settings(rollingExpiryHours = 24)
        assertFalse(SettingsChanges.isWeakening(old, old.copy(rollingExpiryHours = 15)))
        assertTrue(SettingsChanges.isWeakening(old, old.copy(rollingExpiryHours = 36)))
    }

    @Test fun fifteenHourExpiryApplies() = runTest {
        settings.update { it.copy(rollingExpiryHours = 15) }
        earn(10, 1)
        clock.advanceMin(15 * 60 - 1)
        assertEquals(10, bank.balance().minutes)
        clock.advanceMin(1)
        assertEquals(0, bank.balance().minutes)
    }

    private fun habit(times: Int, over: Boolean) = HabitEntity(
        id = 1, name = "h", iconKey = "x", colorArgb = 0, timesPerDay = times, anchorEpochDay = 0, createdAt = 0,
        overTargetReward = over,
    )
    private fun ci(n: Int, minutes: Int) = (1..n).map { CheckInEntity(id = it.toLong(), habitId = 1, epochDay = 0, at = it.toLong(), minutesEarned = minutes) }

    @Test fun overTargetRewardDefaultsOnWithFiftyPercentCap() {
        assertTrue(HabitEntity(name = "h", iconKey = "x", colorArgb = 0, anchorEpochDay = 0, createdAt = 0).overTargetReward)
        val h = habit(2, true)
        assertEquals(10, HabitProgress.checkInReward(h, ci(2, 10), 10)) // cap = 2*10/2 = 10
        assertEquals(0, HabitProgress.checkInReward(h, ci(2, 10) + CheckInEntity(3, 1, 0, 3, null, null, 10), 10))
    }

    @Test fun overTargetRewardOffEarnsNothingBeyondTarget() {
        val h = habit(2, false)
        assertEquals(10, HabitProgress.checkInReward(h, emptyList(), 10))
        assertEquals(10, HabitProgress.checkInReward(h, ci(1, 10), 10))
        assertEquals(0, HabitProgress.checkInReward(h, ci(2, 10), 10))
        assertEquals(0, HabitProgress.checkInReward(h, ci(5, 10), 10))
    }
}
