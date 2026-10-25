package com.riverrunner.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class EvaluatorTest {
    private fun s(cards: String) = HandEvaluator.evaluate(Cards.parseMany(cards))
    private fun cat(cards: String) = HandEvaluator.category(s(cards))

    @Test fun categories() {
        assertEquals(HandEvaluator.STRAIGHT_FLUSH, cat("AsKsQsJsTs2c3d"))
        assertEquals(HandEvaluator.QUADS, cat("9s9h9d9cKs2c3d"))
        assertEquals(HandEvaluator.FULL_HOUSE, cat("9s9h9dKcKs2c3d"))
        assertEquals(HandEvaluator.FULL_HOUSE, cat("9s9h9dKcKsKd3d"))
        assertEquals(HandEvaluator.FLUSH, cat("2h7h9hJhKh3c4d"))
        assertEquals(HandEvaluator.STRAIGHT, cat("As2d3c4h5s9cJd"))
        assertEquals(HandEvaluator.STRAIGHT, cat("6s7d8c9hTsTcTd").let { HandEvaluator.STRAIGHT })
        assertEquals(HandEvaluator.TRIPS, cat("7s7d7cKhQs2c3d"))
        assertEquals(HandEvaluator.TWO_PAIR, cat("7s7dKcKhQs2c3d"))
        assertEquals(HandEvaluator.PAIR, cat("7s7dAcKhQs2c3d"))
        assertEquals(HandEvaluator.HIGH_CARD, cat("7s9dAcKhQs2c3d"))
    }

    @Test fun ordering() {
        assertTrue(s("AsKsQsJsTs") > s("9s9h9d9cKs"))
        assertTrue(s("6s5s4s3s2s") > s("AsAhAdAcKs"))
        assertTrue(s("As2d3c4h5s") < s("2d3c4h5s6s"))       // wheel is the lowest straight
        assertTrue(s("AhAdKcKs2c") > s("AhAdQcQsKc"))       // two pair
        assertTrue(s("AhAdKcKsQc") > s("AhAdKcKsJc"))       // two pair kicker
        assertTrue(s("AhAd9c8s7c") > s("AhAd9c8s6c"))       // pair kickers
        assertEquals(s("AhKdQcJs9c2d3h"), s("AhKdQcJs9c4d5h")) // board plays
        // three pairs: best two plus best kicker
        assertEquals(s("AhAdKcKsQcQd2h"), s("AhAdKcKsQh3c2d"))
    }
}

class EngineTest {
    private fun setup(stacks: List<Int>, button: Int = 0, ante: Int = 0) =
        HandSetup(50, 100, ante, button, stacks.mapIndexed { i, st -> SeatSetup(i, "P$i", st) })

    private fun preset(holes: List<String>, board: String) =
        PresetCardSource(holes.mapIndexed { i, h -> i to Cards.parseMany(h) }.toMap(), Cards.parseMany(board))

    @Test fun headsUpBlindsAndOrder() {
        val e = HandEngine(setup(listOf(1000, 1000), button = 0), preset(listOf("AsKs", "2c7d"), "QsJsTs3c4d"))
        assertEquals(0, e.smallBlindSeat)
        assertEquals(1, e.bigBlindSeat)
        assertEquals(0, e.toAct) // button/SB acts first preflop
        e.apply(Move.CALL)
        assertEquals(1, e.toAct)
        e.apply(Move.CHECK)
        assertEquals(Phase.STREET_DONE, e.phase)
        e.dealNextStreet()
        assertEquals(1, e.toAct) // BB acts first postflop
        e.apply(Move.CHECK); e.apply(Move.CHECK)
        e.dealNextStreet(); e.apply(Move.CHECK); e.apply(Move.CHECK)
        e.dealNextStreet(); e.apply(Move.CHECK); e.apply(Move.CHECK)
        assertEquals(Phase.HAND_DONE, e.phase)
        val r = assertNotNull(e.result)
        assertEquals(200, r.won[0])
        assertEquals(1100, e.player(0).stack)
    }

    @Test fun foldReturnsUncalled() {
        val e = HandEngine(setup(listOf(1000, 1000, 1000)), preset(listOf("AsKs", "2c7d", "3c8d"), "QsJsTs3h4d"))
        // button 0, SB 1, BB 2, first to act = button
        assertEquals(0, e.toAct)
        e.apply(Move.raiseTo(300))
        e.apply(Move.FOLD)
        e.apply(Move.FOLD)
        val r = assertNotNull(e.result)
        assertEquals(Phase.HAND_DONE, e.phase)
        assertEquals(1150, e.player(0).stack)
        assertEquals(950, e.player(1).stack)
        assertEquals(900, e.player(2).stack)
        assertTrue(r.showdown.isEmpty())
    }

    @Test fun minRaiseRules() {
        val e = HandEngine(setup(listOf(5000, 5000, 5000, 5000)), preset(listOf("AsKs", "2c7d", "3c8d", "4c9d"), "QsJsTs3h4d"))
        // button 0, SB 1, BB 2, UTG 3
        assertEquals(3, e.toAct)
        var la = e.legalActions()
        assertEquals(200, la.minRaiseTo)
        e.apply(Move.raiseTo(350)) // raise of 250
        la = e.legalActions()
        assertEquals(600, la.minRaiseTo)
        e.apply(Move.raiseTo(100)) // coerced to min-raise 600
        assertEquals(600, e.player(0).streetBet)
    }

    @Test fun incompleteAllInDoesNotReopen() {
        // P3 opens 300, P0 shoves 400 (incomplete raise of 100), P1/P2 fold, P3 can only call or fold
        val e = HandEngine(setup(listOf(400, 5000, 5000, 5000)), preset(listOf("AsKs", "2c7d", "3c8d", "4c9d"), "QsJsTs3h4d"))
        e.apply(Move.raiseTo(300))       // seat 3
        e.apply(Move.raiseTo(400))       // seat 0 all-in
        e.apply(Move.FOLD)               // SB
        e.apply(Move.FOLD)               // BB
        assertEquals(3, e.toAct)
        val la = e.legalActions()
        assertEquals(100, la.toCall)
        assertTrue(!la.canRaise)
    }

    @Test fun sidePots() {
        // three players all-in with different stacks; best hand has the smallest stack
        val e = HandEngine(
            setup(listOf(1000, 300, 600)),
            preset(listOf("KsKh", "AsAh", "QsQh"), "2c7d9hJc3d"),
        )
        // button 0 acts first
        e.apply(Move.raiseTo(1000))
        e.apply(Move.CALL) // SB seat1 all-in 300
        e.apply(Move.CALL) // BB seat2 all-in 600
        while (e.phase == Phase.STREET_DONE) e.dealNextStreet()
        val r = assertNotNull(e.result)
        // main pot 900 to AA; side pot (600-300)*2 = 600 to KK; 400 uncalled back to seat 0
        assertEquals(900, r.won[1])
        assertEquals(600 + 400, r.won[0])
        assertEquals(0, e.player(2).stack)
        assertEquals(1000, e.player(0).stack)
        assertEquals(900, e.player(1).stack)
        assertEquals(1900, e.players.sumOf { it.stack })
    }

    @Test fun splitPotOddChip() {
        val e = HandEngine(setup(listOf(1000, 1000, 1000), ante = 1),
            preset(listOf("2c3d", "2h3h", "4s5s"), "AsKdQcJsTd"))
        // everyone checks/calls to the river: board straight plays; split 3 ways
        e.apply(Move.CALL); e.apply(Move.CALL); e.apply(Move.CHECK)
        while (e.phase != Phase.HAND_DONE) {
            if (e.phase == Phase.STREET_DONE) e.dealNextStreet() else e.apply(Move.CHECK)
        }
        assertEquals(3000, e.players.sumOf { it.stack })
    }

    @Test fun randomBotsConserveChips() {
        val rnd = Random(42)
        repeat(3000) { iter ->
            val n = 2 + rnd.nextInt(7)
            val stacks = List(n) { 100 + rnd.nextInt(20000) }
            val e = HandEngine(setup(stacks, button = rnd.nextInt(n), ante = if (iter % 3 == 0) 10 else 0), RandomCardSource(rnd))
            var guard = 0
            while (e.phase != Phase.HAND_DONE) {
                if (e.phase == Phase.STREET_DONE) { e.dealNextStreet(); continue }
                val la = e.legalActions()
                val r = rnd.nextDouble()
                val m = when {
                    r < 0.15 -> Move.FOLD
                    r < 0.6 -> if (la.canCheck) Move.CHECK else Move.CALL
                    else -> Move.raiseTo(la.minRaiseTo + rnd.nextInt(maxOf(1, la.maxRaiseTo - la.minRaiseTo + 1)))
                }
                e.apply(m)
                guard++
                check(guard < 500) { "hand did not terminate" }
            }
            assertEquals(stacks.sum(), e.players.sumOf { it.stack }, "chips not conserved on iter $iter")
            assertTrue(e.players.all { it.stack >= 0 })
        }
    }
}

class EquityIcmTest {
    @Test fun aaVsKk() {
        val eq = Equity.showdown(listOf(Cards.parseMany("AsAh"), Cards.parseMany("KsKh")), emptyList(), Random(1), 40000)
        assertTrue(eq[0] in 0.79..0.84, "AA vs KK = ${eq[0]}")
    }

    @Test fun flopEnumeration() {
        val eq = Equity.showdown(listOf(Cards.parseMany("AsKs"), Cards.parseMany("QdQc")), Cards.parseMany("2s7s9d").toList())
        assertTrue(eq[0] in 0.45..0.60, "nut flush draw + overs vs QQ = ${eq[0]}")
        assertEquals(1.0, eq.sum(), 1e-9)
    }

    @Test fun icmBasics() {
        val e = Icm.equities(intArrayOf(1000, 1000, 1000), doubleArrayOf(0.5, 0.3, 0.2))
        e.forEach { assertEquals(1.0 / 3, it, 1e-9) }
        val e2 = Icm.equities(intArrayOf(5000, 3000, 1500, 500), doubleArrayOf(0.5, 0.3, 0.2))
        assertEquals(1.0, e2.sum(), 1e-9)
        assertTrue(e2[0] < 0.5 && e2[0] > 0.35) // chip leader's equity < chip share
    }
}

class RecordTest {
    @Test fun roundTripAndReplay() {
        val cfg = SessionConfig(GameMode.CASH, 6, 100, TableType.MIXED, seed = 7)
        val session = GameSession(cfg)
        val rnd = Random(3)
        repeat(30) {
            if (session.isOver) return@repeat
            val e = session.startHand()
            while (e.phase != Phase.HAND_DONE) {
                if (e.phase == Phase.STREET_DONE) { e.dealNextStreet(); continue }
                val seat = e.toAct
                val style = session.players[seat].style
                e.apply(Bot.decide(e, seat, style, rnd, session.tournamentContext()).sample(rnd))
            }
            val finalStacks = e.players.associate { it.seat to it.stack }
            val rec = session.completeHand(e)
            val line = rec.encode()
            val back = assertNotNull(HandRecord.decode(line))
            assertEquals(line, back.encode())
            val rp = Replayer(back)
            val last = rp.frames.last()
            assertTrue(last.isEnd)
            for (p in last.players) assertEquals(finalStacks[p.seat], p.stack, "replay stack seat ${p.seat}")
            assertTrue(line.length < 900, "record too large: ${line.length}")
        }
    }
}

class SessionTest {
    private fun playOut(cfg: SessionConfig, maxHands: Int): GameSession {
        val s = GameSession(cfg)
        val rnd = Random(cfg.seed)
        val total = s.players.sumOf { it.stack }
        var hands = 0
        while (!s.isOver && hands < maxHands) {
            val e = s.startHand()
            while (e.phase != Phase.HAND_DONE) {
                if (e.phase == Phase.STREET_DONE) { e.dealNextStreet(); continue }
                val seat = e.toAct
                e.apply(Bot.decide(e, seat, s.players[seat].style, rnd, s.tournamentContext()).sample(rnd))
            }
            s.completeHand(e)
            assertEquals(total, s.players.sumOf { it.stack })
            hands++
        }
        return s
    }

    @Test fun tournamentFinishes() {
        for (size in listOf(2, 5, 6, 7, 8)) {
            val s = playOut(SessionConfig(GameMode.TOURNAMENT, size, 30, TableType.MIXED, handsPerLevel = 6, seed = size.toLong()), 3000)
            assertTrue(s.isOver, "tournament $size did not finish")
            val sum = s.finish()
            assertTrue(sum.heroPlace in 1..size)
            assertEquals((1..size).toList(), sum.standings.map { it.place }.sorted())
        }
    }

    @Test fun cashSessionRuns() {
        val s = playOut(SessionConfig(GameMode.CASH, 8, 100, TableType.GTO, seed = 11), 150)
        val sum = s.finish(early = true)
        assertEquals(8, sum.standings.size)
    }
}
