package com.riverrunner.engine

import kotlin.random.Random

/** Counter keys stored in a save slot. Keys starting with "max_" keep the largest value seen. */
object Progress {
    const val HANDS = "hands"
    const val POTS_WON = "pots_won"
    const val SHOWDOWN_WINS = "showdown_wins"
    const val ALLIN_WINS = "allin_wins"
    const val SUCKOUTS = "suckouts"
    const val STEALS = "steals"
    const val RIVER_BLUFFS = "river_bluffs"
    const val MAX_WIN_BB = "max_win_bb"
    const val DEALT_AA = "dealt_aa"
    const val WON_72 = "won_72"
    const val WON_FULL_HOUSE = "won_full_house"
    const val WON_QUADS = "won_quads"
    const val WON_STRAIGHT_FLUSH = "won_straight_flush"
    const val WON_ROYAL = "won_royal"

    const val CASH_SESSIONS = "cash_sessions"
    const val CASH_PROFIT_CENTI_BB = "cash_profit_centi_bb"
    const val MAX_CASH_PROFIT_BB = "max_cash_profit_bb"
    const val CASH_DOUBLE_UPS = "cash_double_ups"
    const val TOURNEYS = "tourneys"
    const val TOURNEY_WINS = "tourney_wins"
    const val TOURNEY_ITM = "tourney_itm"
    const val TOURNEY_POINTS = "tourney_points"
    const val HU_TITLES = "hu_titles"
    const val FULL_TABLE_TITLES = "full_table_titles"

    // funny ones
    const val LOST_WITH_AA = "lost_with_aa"
    const val ALLIN_LOST_FAVOURITE = "allin_lost_favourite"
    const val BLUFF_CAUGHT = "bluff_caught"
    const val KICKER_LOSSES = "kicker_losses"
    /** current run of hands folded preflop; 1 extends the run, 0 ends it */
    const val FOLD_STREAK = "fold_streak"
    const val MAX_FOLD_STREAK = "max_fold_streak"

    const val GUIDE_OPENED = "guide_opened"
    const val REPLAYS = "replays"
    const val STARS = "stars"

    /** Merges [delta] into [counters]: max_ keys keep the maximum, others are summed. */
    fun apply(counters: MutableMap<String, Long>, delta: Map<String, Long>) {
        for ((k, v) in delta) {
            val old = counters[k] ?: 0L
            counters[k] = when {
                k == FOLD_STREAK -> if (v > 0) old + v else 0L
                k.startsWith("max_") -> maxOf(old, v)
                else -> old + v
            }
        }
        if (FOLD_STREAK in delta) {
            counters[MAX_FOLD_STREAK] = maxOf(counters[MAX_FOLD_STREAK] ?: 0L, counters[FOLD_STREAK] ?: 0L)
        }
    }
}

enum class AchCategory { START, SKILL, HANDS, TOURNAMENT, CASH, FUN, STUDY }

data class AchievementDef(
    val id: String,
    val category: AchCategory,
    /** short glyph drawn in the middle of the badge */
    val glyph: String,
    val counter: String,
    val target: Long,
    val nameZh: String,
    val descZh: String,
    val nameEn: String,
    val descEn: String,
)

object Achievements {
    private fun a(id: String, cat: AchCategory, glyph: String, counter: String, target: Long, nz: String, dz: String, ne: String, de: String) =
        AchievementDef(id, cat, glyph, counter, target, nz, dz, ne, de)

    val all: List<AchievementDef> = listOf(
        a("first_hand", AchCategory.START, "♠", Progress.HANDS, 1, "初次落座", "打完第一手牌", "First seat", "Play your first hand"),
        a("first_pot", AchCategory.START, "$", Progress.POTS_WON, 1, "第一桶金", "赢下第一个底池", "First pot", "Win your first pot"),
        a("hands_100", AchCategory.START, "100", Progress.HANDS, 100, "百手新人", "累计打 100 手牌", "Hundred hands", "Play 100 hands"),
        a("hands_1000", AchCategory.START, "1K", Progress.HANDS, 1000, "千手老兵", "累计打 1000 手牌", "Thousand hands", "Play 1,000 hands"),
        a("hands_5000", AchCategory.START, "5K", Progress.HANDS, 5000, "牌桌常客", "累计打 5000 手牌", "Regular", "Play 5,000 hands"),
        a("pots_100", AchCategory.START, "$$", Progress.POTS_WON, 100, "百池赢家", "累计赢下 100 个底池", "Pot collector", "Win 100 pots"),

        a("steal_10", AchCategory.SKILL, "偷", Progress.STEALS, 10, "偷盲贼", "在 CO、按钮或小盲开池加注，其他人全部弃牌，累计 10 次", "Blind thief", "Raise first from CO, button or small blind and take the blinds 10 times"),
        a("river_bluff", AchCategory.SKILL, "唬", Progress.RIVER_BLUFFS, 1, "河牌诈唬", "河牌只有高牌，下注或加注把对手打跑", "River bluff", "Bet or raise the river with only high card and win without showdown"),
        a("bluff_10", AchCategory.SKILL, "演", Progress.RIVER_BLUFFS, 10, "诈唬大师", "河牌诈唬成功 10 次", "Bluff master", "Win 10 pots with river bluffs"),
        a("showdown_50", AchCategory.SKILL, "亮", Progress.SHOWDOWN_WINS, 50, "摊牌达人", "摊牌赢下 50 个底池", "Showdown pro", "Win 50 showdowns"),
        a("allin_win", AchCategory.SKILL, "全", Progress.ALLIN_WINS, 1, "孤注一掷", "全下后摊牌获胜", "All in", "Win a showdown after an all-in"),
        a("suckout", AchCategory.SKILL, "逆", Progress.SUCKOUTS, 1, "逆天改命", "全下时胜率不到 35%，最后却赢了", "Lucky river", "Win an all-in where you had under 35% equity"),
        a("big_win", AchCategory.SKILL, "100", Progress.MAX_WIN_BB, 100, "一手百 BB", "一手牌净赢 100 BB 以上", "Monster pot", "Win 100 BB or more in a single hand"),

        a("pocket_aces", AchCategory.HANDS, "AA", Progress.DEALT_AA, 1, "口袋火箭", "拿到一对 A", "Pocket rockets", "Get dealt AA"),
        a("won_72", AchCategory.HANDS, "72", Progress.WON_72, 1, "七二奇迹", "用最差的起手牌 7-2 不同花赢下底池", "The hammer", "Win a pot with 7-2 offsuit, the worst starting hand"),
        a("full_house", AchCategory.HANDS, "葫", Progress.WON_FULL_HOUSE, 1, "满堂红", "用葫芦赢下摊牌", "Full house", "Win a showdown with a full house"),
        a("quads", AchCategory.HANDS, "四", Progress.WON_QUADS, 1, "四条", "用四条赢下摊牌", "Quads", "Win a showdown with four of a kind"),
        a("straight_flush", AchCategory.HANDS, "顺", Progress.WON_STRAIGHT_FLUSH, 1, "同花顺", "用同花顺赢下摊牌", "Straight flush", "Win a showdown with a straight flush"),
        a("royal", AchCategory.HANDS, "皇", Progress.WON_ROYAL, 1, "皇家同花顺", "用皇家同花顺赢下摊牌", "Royal flush", "Win a showdown with a royal flush"),

        a("first_itm", AchCategory.TOURNAMENT, "圈", Progress.TOURNEY_ITM, 1, "进入奖励圈", "锦标赛进入有奖名次", "In the money", "Finish a tournament in a paid place"),
        a("first_title", AchCategory.TOURNAMENT, "冠", Progress.TOURNEY_WINS, 1, "首个冠军", "赢得一场锦标赛", "Champion", "Win a tournament"),
        a("titles_5", AchCategory.TOURNAMENT, "5", Progress.TOURNEY_WINS, 5, "五冠王", "赢得 5 场锦标赛", "Five titles", "Win 5 tournaments"),
        a("hu_champ", AchCategory.TOURNAMENT, "1v1", Progress.HU_TITLES, 1, "单挑之王", "赢得一场 2 人锦标赛", "Heads-up king", "Win a 2-player tournament"),
        a("full_table", AchCategory.TOURNAMENT, "8", Progress.FULL_TABLE_TITLES, 1, "满桌通吃", "赢得一场 8 人锦标赛", "Full table", "Win an 8-player tournament"),

        a("cash_up_50", AchCategory.CASH, "+50", Progress.MAX_CASH_PROFIT_BB, 50, "满载而归", "一场筹码局结束时赢 50 BB 以上", "Good session", "Finish a cash session up 50 BB or more"),
        a("double_up", AchCategory.CASH, "×2", Progress.CASH_DOUBLE_UPS, 1, "翻倍", "一场筹码局结束时筹码翻倍", "Double up", "Finish a cash session with double your starting stack"),
        a("cash_10", AchCategory.CASH, "10", Progress.CASH_SESSIONS, 10, "常驻现金桌", "打完 10 场筹码局", "Cash regular", "Finish 10 cash sessions"),

        a("rocket_crash", AchCategory.FUN, "坠", Progress.LOST_WITH_AA, 1, "火箭坠毁", "拿着 AA 打到摊牌，结果输了", "Rockets crashed", "Lose a showdown holding pocket aces"),
        a("duck_flew", AchCategory.FUN, "鸭", Progress.ALLIN_LOST_FAVOURITE, 1, "煮熟的鸭子飞了", "全下时胜率超过 80%，最后还是输了", "So close", "Lose an all-in where you had over 80% equity"),
        a("bluff_caught", AchCategory.FUN, "抓", Progress.BLUFF_CAUGHT, 1, "诈唬被抓", "河牌只有高牌还下注，被跟注后输掉", "Caught bluffing", "Bet the river with only high card, get called and lose"),
        a("kicker_pain", AchCategory.FUN, "踢", Progress.KICKER_LOSSES, 1, "输在踢脚", "和对手牌型一样，却输在踢脚上", "Out-kicked", "Lose a showdown with the same hand as your opponent, decided by the kicker"),
        a("zen_fold", AchCategory.FUN, "禅", Progress.MAX_FOLD_STREAK, 25, "老僧入定", "连续 25 手翻牌前直接弃牌", "Zen master", "Fold preflop 25 hands in a row"),

        a("guide", AchCategory.STUDY, "书", Progress.GUIDE_OPENED, 1, "好学生", "打开新手指南", "Good student", "Open the beginner guide"),
        a("replay_10", AchCategory.STUDY, "复", Progress.REPLAYS, 10, "复盘狂人", "复盘 10 手牌", "Reviewer", "Replay 10 hands"),
        a("star_5", AchCategory.STUDY, "★", Progress.STARS, 5, "收藏家", "收藏 5 手牌", "Collector", "Star 5 hands"),
    )

    fun byId(id: String): AchievementDef? = all.firstOrNull { it.id == id }

    fun isDone(def: AchievementDef, counters: Map<String, Long>) = (counters[def.counter] ?: 0L) >= def.target

    fun newlyUnlocked(counters: Map<String, Long>, unlocked: Set<String>): List<AchievementDef> =
        all.filter { it.id !in unlocked && isDone(it, counters) }

    fun progress(def: AchievementDef, counters: Map<String, Long>): Double =
        ((counters[def.counter] ?: 0L).toDouble() / def.target).coerceIn(0.0, 1.0)
}

/** Turns finished hands and sessions into counter increments. */
object Facts {

    private val STEAL_SEATS = setOf("CO", "BTN", "SB")

    fun forHand(r: HandRecord): Map<String, Long> {
        val d = HashMap<String, Long>()
        d[Progress.HANDS] = 1
        val hero = r.hero ?: return d
        val h0 = Cards.rank(hero.hole[0])
        val h1 = Cards.rank(hero.hole[1])
        val aces = h0 == 12 && h1 == 12
        if (aces) d[Progress.DEALT_AA] = 1

        // preflop fold streak: folding as your first decision extends it, playing ends it
        val pre = r.actions.filter { it.street == Street.PREFLOP && !it.type.isPost }
        val heroFirst = pre.indexOfFirst { it.seat == hero.seat }
        if (heroFirst >= 0) d[Progress.FOLD_STREAK] = if (pre[heroFirst].type == ActionType.FOLD) 1L else 0L

        val folded = r.actions.filter { it.type == ActionType.FOLD }.map { it.seat }.toSet()
        val live = r.players.filter { it.seat !in folded }
        val showdown = live.size >= 2 && r.board.size == 5 && hero.seat !in folded
        val boardList = r.board.toList()
        val heroScore = if (r.board.size == 5) HandEvaluator.evaluate((boardList + hero.hole.toList()).toIntArray()) else 0
        val allInPot = r.actions.any { it.allIn && it.seat in live.map { p -> p.seat } }
        val net = r.heroNet

        fun heroEquityAtAllIn(): Double? {
            val last = r.actions.lastOrNull { !it.type.isPost }
            val boardAtAllIn = r.board.take(last?.street?.boardCount ?: 0)
            if (boardAtAllIn.size >= 5) return null
            val eq = Equity.showdown(live.map { it.hole }, boardAtAllIn, Random(r.id), 4000)
            return eq[live.indexOfFirst { it.seat == hero.seat }]
        }

        val lastHero = r.actions.lastOrNull { it.seat == hero.seat }
        val heroBetRiver = r.board.size == 5 && lastHero != null && lastHero.street == Street.RIVER && lastHero.type.isAggressive

        if (net <= 0) {
            if (showdown && net < 0) {
                if (aces) d[Progress.LOST_WITH_AA] = 1
                if (heroBetRiver && HandEvaluator.category(heroScore) == HandEvaluator.HIGH_CARD) d[Progress.BLUFF_CAUGHT] = 1
                val best = live.filter { it.seat != hero.seat }
                    .maxOfOrNull { HandEvaluator.evaluate((boardList + it.hole.toList()).toIntArray()) } ?: 0
                // same category and same main rank(s): the kicker decided it
                val cat = HandEvaluator.category(heroScore)
                val shift = if (cat == HandEvaluator.TWO_PAIR) 12 else 16
                if (best > heroScore && cat <= HandEvaluator.TWO_PAIR && (best ushr shift) == (heroScore ushr shift))
                    d[Progress.KICKER_LOSSES] = 1
                if (allInPot) heroEquityAtAllIn()?.let { if (it > 0.8) d[Progress.ALLIN_LOST_FAVOURITE] = 1 }
            }
            return d
        }

        d[Progress.POTS_WON] = 1
        d[Progress.MAX_WIN_BB] = net.toLong() / r.bigBlind
        val suited = Cards.suit(hero.hole[0]) == Cards.suit(hero.hole[1])
        if (!suited && setOf(h0, h1) == setOf(5, 0)) d[Progress.WON_72] = 1

        if (showdown) {
            d[Progress.SHOWDOWN_WINS] = 1
            when (HandEvaluator.category(heroScore)) {
                HandEvaluator.FULL_HOUSE -> d[Progress.WON_FULL_HOUSE] = 1
                HandEvaluator.QUADS -> d[Progress.WON_QUADS] = 1
                HandEvaluator.STRAIGHT_FLUSH -> {
                    if (((heroScore ushr 16) and 0xF) == 12) d[Progress.WON_ROYAL] = 1
                    else d[Progress.WON_STRAIGHT_FLUSH] = 1
                }
            }
            if (allInPot) {
                d[Progress.ALLIN_WINS] = 1
                heroEquityAtAllIn()?.let { if (it < 0.35) d[Progress.SUCKOUTS] = 1 }
            }
        } else {
            // won without showdown
            if (r.board.isEmpty() && heroFirst >= 0 && pre[heroFirst].type.isAggressive &&
                pre.subList(0, heroFirst).all { it.type == ActionType.FOLD } &&
                Stats.positionOf(r) in STEAL_SEATS
            ) d[Progress.STEALS] = 1
            if (heroBetRiver) {
                val cat = HandEvaluator.category(HandEvaluator.evaluate((boardList + hero.hole.toList()).toIntArray()))
                if (cat == HandEvaluator.HIGH_CARD) d[Progress.RIVER_BLUFFS] = 1
            }
        }
        return d
    }

    fun forSession(s: SessionSummary, config: SessionConfig): Map<String, Long> {
        val d = HashMap<String, Long>()
        when (s.mode) {
            GameMode.CASH -> {
                d[Progress.CASH_SESSIONS] = 1
                d[Progress.CASH_PROFIT_CENTI_BB] = Math.round(s.heroProfit * 100)
                if (s.heroProfit > 0) d[Progress.MAX_CASH_PROFIT_BB] = s.heroProfit.toLong()
                if (s.heroProfit >= config.startingBB) d[Progress.CASH_DOUBLE_UPS] = 1
            }
            GameMode.TOURNAMENT -> {
                d[Progress.TOURNEYS] = 1
                d[Progress.TOURNEY_POINTS] = Math.round(s.heroProfit)
                val paid = Icm.payoutsFor(config.tableSize).size
                if (s.heroPlace in 1..paid) d[Progress.TOURNEY_ITM] = 1
                if (s.heroPlace == 1) {
                    d[Progress.TOURNEY_WINS] = 1
                    if (config.tableSize == 2) d[Progress.HU_TITLES] = 1
                    if (config.tableSize == 8) d[Progress.FULL_TABLE_TITLES] = 1
                }
            }
        }
        return d
    }
}
