package com.riverrunner.engine

data class RecordPlayer(
    val seat: Int,
    val name: String,
    val style: BotStyle,
    val startStack: Int,
    val hole: IntArray,
)

/**
 * Everything needed to replay a hand exactly: starting stacks, all hole cards,
 * the dealt board and every action. Encoded as one compact text line
 * (~300-500 bytes per hand).
 */
data class HandRecord(
    val id: Long,
    val time: Long,
    val mode: GameMode,
    val handNumber: Int,
    val smallBlind: Int,
    val bigBlind: Int,
    val ante: Int,
    val buttonSeat: Int,
    val heroSeat: Int,
    val tableSize: Int,
    val players: List<RecordPlayer>,
    val board: IntArray,
    val actions: List<Action>,
    val won: Map<Int, Int>,
    val favorite: Boolean = false,
) {
    val hero: RecordPlayer? get() = players.firstOrNull { it.seat == heroSeat }

    /** Net result of the hero in chips. */
    val heroNet: Int get() {
        val committed = actions.filter { it.seat == heroSeat }.sumOf { it.amount }
        return (won[heroSeat] ?: 0) - committed
    }

    fun encode(): String {
        val sb = StringBuilder()
        sb.append("H1|").append(id).append('|').append(time).append('|').append(mode.code).append('|')
            .append(handNumber).append('|').append(smallBlind).append('|').append(bigBlind).append('|')
            .append(ante).append('|').append(buttonSeat).append('|').append(heroSeat).append('|')
            .append(tableSize).append('|').append(if (favorite) 1 else 0).append('|')
        sb.append(players.joinToString(";") { "${it.seat},${clean(it.name)},${it.style.code},${it.startStack},${Cards.format(it.hole)}" })
        sb.append('|').append(Cards.format(board)).append('|')
        sb.append(actions.joinToString(";") {
            "${it.street.ordinal},${it.seat},${it.type.ordinal},${it.amount},${it.streetTotal},${if (it.allIn) 1 else 0}"
        })
        sb.append('|')
        sb.append(won.entries.joinToString(";") { "${it.key},${it.value}" })
        return sb.toString()
    }

    companion object {
        private fun clean(s: String) = s.replace(Regex("[|;,\\n]"), " ")

        fun decode(line: String): HandRecord? = try {
            val f = line.split('|')
            require(f[0] == "H1")
            val players = f[12].split(';').filter { it.isNotEmpty() }.map {
                val p = it.split(',')
                RecordPlayer(p[0].toInt(), p[1], BotStyle.fromCode(p[2]), p[3].toInt(), Cards.parseMany(p[4]))
            }
            val actions = if (f[14].isEmpty()) emptyList() else f[14].split(';').map {
                val a = it.split(',')
                Action(Street.entries[a[0].toInt()], a[1].toInt(), ActionType.entries[a[2].toInt()],
                    a[3].toInt(), a[4].toInt(), a[5] == "1")
            }
            val won = if (f.size <= 15 || f[15].isEmpty()) emptyMap() else f[15].split(';').associate {
                val w = it.split(','); w[0].toInt() to w[1].toInt()
            }
            HandRecord(
                id = f[1].toLong(), time = f[2].toLong(), mode = GameMode.fromCode(f[3]),
                handNumber = f[4].toInt(), smallBlind = f[5].toInt(), bigBlind = f[6].toInt(),
                ante = f[7].toInt(), buttonSeat = f[8].toInt(), heroSeat = f[9].toInt(),
                tableSize = f[10].toInt(), favorite = f[11] == "1",
                players = players,
                board = if (f[13].isEmpty()) IntArray(0) else Cards.parseMany(f[13]),
                actions = actions, won = won,
            )
        } catch (e: Exception) { null }

        fun from(engine: HandEngine, mode: GameMode, handNumber: Int, heroSeat: Int, tableSize: Int, styles: Map<Int, BotStyle>): HandRecord {
            val now = System.currentTimeMillis()
            return HandRecord(
                id = now * 1000 + (handNumber % 1000),
                time = now,
                mode = mode,
                handNumber = handNumber,
                smallBlind = engine.setup.smallBlind,
                bigBlind = engine.setup.bigBlind,
                ante = engine.setup.ante,
                buttonSeat = engine.buttonSeat,
                heroSeat = heroSeat,
                tableSize = tableSize,
                players = engine.players.map { RecordPlayer(it.seat, it.name, styles[it.seat] ?: BotStyle.GTO, it.startStack, it.hole) },
                board = engine.board.toIntArray(),
                actions = engine.actions.toList(),
                won = engine.result?.won ?: emptyMap(),
            )
        }
    }

    override fun equals(other: Any?): Boolean = other is HandRecord && other.id == id && other.favorite == favorite
    override fun hashCode(): Int = id.hashCode()
}

/** A replay position: the table after a given number of steps. */
data class ReplayFrame(
    val index: Int,
    /** the action just taken (null for the start frame or a dealing frame) */
    val action: Action?,
    val street: Street,
    val players: List<PlayerState>,
    val board: List<Int>,
    val pot: Int,
    /** seat to act next, -1 if none */
    val toAct: Int,
    val isEnd: Boolean,
)

/** Rebuilds a recorded hand step by step. */
class Replayer(val record: HandRecord) {
    private val voluntary = record.actions.filter { !it.type.isPost }

    private fun newEngine(): HandEngine {
        val setup = HandSetup(
            record.smallBlind, record.bigBlind, record.ante, record.buttonSeat,
            record.players.map { SeatSetup(it.seat, it.name, it.startStack) },
        )
        val board = IntArray(5) { i -> record.board.getOrElse(i) { -1 } }
        return HandEngine(setup, PresetCardSource(record.players.associate { it.seat to it.hole }, board))
    }

    private fun frame(e: HandEngine, idx: Int, a: Action?, end: Boolean) = ReplayFrame(
        idx, a, e.street, e.players, e.board.toList(), e.pot,
        if (e.phase == Phase.BETTING) e.toAct else -1, end,
    )

    /** All frames: start, then one per action / street deal, ending at showdown. */
    val frames: List<ReplayFrame> by lazy {
        val out = ArrayList<ReplayFrame>()
        val e = newEngine()
        out += frame(e, 0, null, false)
        for (a in voluntary) {
            while (e.phase == Phase.STREET_DONE && e.street.ordinal < a.street.ordinal) {
                e.dealNextStreet()
                out += frame(e, out.size, null, false)
            }
            if (e.phase != Phase.BETTING) break
            e.apply(toMove(a))
            out += frame(e, out.size, a, e.phase == Phase.HAND_DONE)
        }
        while (e.phase == Phase.STREET_DONE && e.board.size < record.board.size) {
            e.dealNextStreet()
            out += frame(e, out.size, null, e.phase == Phase.HAND_DONE)
        }
        if (out.isNotEmpty() && !out.last().isEnd) out[out.size - 1] = out.last().copy(isEnd = true)
        out
    }

    /** An engine positioned just before voluntary action number [actionIndex] (for "what would GTO do"). */
    fun engineBeforeAction(actionIndex: Int): HandEngine {
        val e = newEngine()
        for (i in 0 until actionIndex) {
            val a = voluntary[i]
            while (e.phase == Phase.STREET_DONE) e.dealNextStreet()
            e.apply(toMove(a))
        }
        while (e.phase == Phase.STREET_DONE) e.dealNextStreet()
        return e
    }

    /** Index into the voluntary action list for a frame's action, or -1. */
    fun actionIndexOf(frame: ReplayFrame): Int {
        var count = 0
        for (f in frames) {
            if (f.action != null) {
                if (f.index == frame.index) return count
                count++
            }
        }
        return -1
    }

    private fun toMove(a: Action): Move = when (a.type) {
        ActionType.FOLD -> Move.FOLD
        ActionType.CHECK -> Move.CHECK
        ActionType.CALL -> Move.CALL
        ActionType.BET, ActionType.RAISE -> Move.raiseTo(a.streetTotal)
        else -> error("post")
    }
}
