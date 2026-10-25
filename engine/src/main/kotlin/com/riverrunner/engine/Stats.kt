package com.riverrunner.engine

data class HeroStats(
    val hands: Int,
    /** voluntarily put money in pot preflop */
    val vpip: Double,
    /** preflop raise */
    val pfr: Double,
    /** 3-bet when facing exactly one raise */
    val threeBet: Double,
    /** went to showdown when seeing the flop */
    val wtsd: Double,
    /** won money at showdown */
    val wsd: Double,
    /** net result in big blinds per 100 hands */
    val bbPer100: Double,
    /** total net result in big blinds */
    val netBB: Double,
    val byPosition: Map<String, Double>,
)

object Stats {
    fun compute(records: List<HandRecord>): HeroStats {
        var vpip = 0; var pfr = 0; var tbOpp = 0; var tb = 0
        var sawFlop = 0; var showdowns = 0; var wonSd = 0
        var netBB = 0.0
        val posNet = HashMap<String, Double>()
        for (r in records) {
            val hero = r.heroSeat
            val pre = r.actions.filter { it.street == Street.PREFLOP && !it.type.isPost }
            val mine = pre.filter { it.seat == hero }
            if (mine.any { it.type == ActionType.CALL || it.type.isAggressive }) vpip++
            if (mine.any { it.type.isAggressive }) pfr++
            // 3-bet opportunity: hero's first decision faced exactly one raise
            val firstIdx = pre.indexOfFirst { it.seat == hero }
            if (firstIdx >= 0) {
                val before = pre.subList(0, firstIdx)
                if (before.count { it.type.isAggressive } == 1) {
                    tbOpp++
                    if (pre[firstIdx].type.isAggressive) tb++
                }
            }
            val folded = r.actions.firstOrNull { it.seat == hero && it.type == ActionType.FOLD }
            val sawF = r.board.size >= 3 && (folded == null || folded.street != Street.PREFLOP)
            if (sawF) {
                sawFlop++
                val livePlayers = r.players.map { it.seat }.filter { s -> r.actions.none { it.seat == s && it.type == ActionType.FOLD } }
                if (folded == null && livePlayers.size >= 2) {
                    showdowns++
                    if (r.heroNet > 0) wonSd++
                }
            }
            val bbNet = r.heroNet.toDouble() / r.bigBlind
            netBB += bbNet
            val pos = positionOf(r)
            posNet[pos] = (posNet[pos] ?: 0.0) + bbNet
        }
        val n = records.size
        fun pct(a: Int, b: Int) = if (b == 0) 0.0 else a.toDouble() / b
        return HeroStats(
            hands = n, vpip = pct(vpip, n), pfr = pct(pfr, n), threeBet = pct(tb, tbOpp),
            wtsd = pct(showdowns, sawFlop), wsd = pct(wonSd, showdowns),
            bbPer100 = if (n == 0) 0.0 else netBB / n * 100, netBB = netBB, byPosition = posNet,
        )
    }

    fun positionOf(r: HandRecord): String {
        val seats = r.players.map { it.seat }.sorted()
        val btnIdx = seats.indexOf(r.buttonSeat)
        val order = List(seats.size) { seats[(btnIdx + 1 + it) % seats.size] }
        return HandEngine.positionName(order.indexOf(r.heroSeat), seats.size)
    }
}
