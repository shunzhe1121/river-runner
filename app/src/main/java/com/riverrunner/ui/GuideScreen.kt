package com.riverrunner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riverrunner.engine.Cards
import com.riverrunner.i18n.LocalStrings
import kotlin.math.cos
import kotlin.math.sin

enum class GuideTab { BASICS, RANKINGS, POSITIONS, GLOSSARY, TIPS, APP }

@Composable
fun GuideScreen(vm: AppViewModel) {
    BackHandler { vm.screen = Screen.Home }
    GuideContent(onClose = { vm.screen = Screen.Home })
}

@Composable
fun GuideContent(onClose: () -> Unit, startTab: GuideTab = GuideTab.BASICS) {
    val st = LocalStrings.current
    val zh = st.isZh
    var tab by remember { mutableStateOf(startTab) }
    val scroll = rememberScrollState()
    LaunchedEffect(tab) { scroll.scrollTo(0) }
    Row(Modifier.fillMaxSize().background(Palette.Bg).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.width(150.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmallButton("←", onClose)
                Spacer(Modifier.width(8.dp))
                Text(st.guide, color = Palette.Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            for (t in GuideTab.entries) {
                val sel = t == tab
                Box(
                    Modifier.fillMaxWidth().pressable { tab = t }.clip(RoundedCornerShape(8.dp))
                        .background(if (sel) Palette.GoldDim else Palette.Panel)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) { Text(tabTitle(t, zh), color = Palette.Text, fontSize = 13.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) }
            }
        }
        Column(
            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(Palette.Panel)
                .padding(14.dp).verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (tab) {
                GuideTab.BASICS -> Basics(zh)
                GuideTab.RANKINGS -> Rankings(zh)
                GuideTab.POSITIONS -> Positions(zh)
                GuideTab.GLOSSARY -> Glossary(zh)
                GuideTab.TIPS -> Paragraphs(if (zh) TIPS_ZH else TIPS_EN)
                GuideTab.APP -> Paragraphs(if (zh) APP_ZH else APP_EN)
            }
        }
    }
}

private fun tabTitle(t: GuideTab, zh: Boolean) = when (t) {
    GuideTab.BASICS -> if (zh) "① 基本玩法" else "① How to play"
    GuideTab.RANKINGS -> if (zh) "② 牌型大小" else "② Hand rankings"
    GuideTab.POSITIONS -> if (zh) "③ 位置" else "③ Positions"
    GuideTab.GLOSSARY -> if (zh) "④ 术语表" else "④ Glossary"
    GuideTab.TIPS -> if (zh) "⑤ 新手建议" else "⑤ Beginner tips"
    GuideTab.APP -> if (zh) "⑥ 软件说明" else "⑥ Using the app"
}

@Composable
private fun H(text: String) = Text(text, color = Palette.Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold)

@Composable
private fun P(text: String) = Text(text, color = Palette.Text, fontSize = 13.sp, lineHeight = 20.sp)

@Composable
private fun Paragraphs(items: List<Pair<String, String>>) {
    for ((title, body) in items) {
        H(title)
        P(body)
        Spacer(Modifier.height(2.dp))
    }
}

// ----------------------------------------------------------------- basics

@Composable
private fun Basics(zh: Boolean) {
    if (zh) {
        H("目标")
        P("每人发 2 张只有自己能看的底牌，桌面中央再陆续发出 5 张所有人共用的公共牌。用你的 2 张底牌加 5 张公共牌，任选 5 张组成最大的牌型。要么在摊牌时牌最大，要么通过下注让别人全部弃牌，就能赢得底池。")
        H("一手牌的流程")
        Steps(listOf(
            "盲注" to "庄家按钮(D)左边两位强制下注：小盲(SB)下半个大盲，大盲(BB)下一个大盲。锦标赛后期每人还要交前注(Ante)。",
            "翻牌前" to "每人拿到 2 张底牌。从大盲左边(UTG)开始顺时针行动。",
            "翻牌" to "发出 3 张公共牌，进行第二轮下注。从这一轮起，由按钮左边第一位还在牌局里的玩家先行动。",
            "转牌" to "发出第 4 张公共牌，第三轮下注。",
            "河牌" to "发出第 5 张公共牌，最后一轮下注。",
            "摊牌" to "还剩两人以上时亮牌比大小，牌型最大者赢得底池；一样大则平分。",
        ))
        H("可以做的动作")
        Steps(listOf(
            "弃牌" to "放弃这手牌，已经下注的筹码留在底池里。",
            "过牌" to "本轮还没人下注时，不下注，把行动交给下一位。",
            "跟注" to "补齐到当前最高下注额。",
            "下注 / 加注" to "主动下注，或在别人下注的基础上加码。无限注德州里，加注额至少要等于上一次的加注幅度，最多可以全下。",
            "全下(All-in)" to "把所有筹码推进去。筹码不够跟注时也可以全下，这时会形成边池：你只能赢到你投入部分对应的底池。",
        ))
        H("按钮和盲注每手轮转")
        P("每打完一手，庄家按钮顺时针移动一位，所以每个人都会轮流坐到每个位置。")
    } else {
        H("Goal")
        P("Each player gets 2 private hole cards; 5 shared community cards are dealt in the middle. Make the best 5-card hand from your 2 cards plus the 5 on the board. You win the pot either by having the best hand at showdown or by betting so that everyone else folds.")
        H("How a hand goes")
        Steps(listOf(
            "Blinds" to "The two players left of the dealer button (D) post forced bets: small blind (half a big blind) and big blind. Later in tournaments everyone also posts an ante.",
            "Preflop" to "Everyone gets 2 cards. Action starts left of the big blind (UTG) and goes clockwise.",
            "Flop" to "3 community cards, then a betting round. From now on the first active player left of the button acts first.",
            "Turn" to "The 4th community card and a betting round.",
            "River" to "The 5th community card and the last betting round.",
            "Showdown" to "If two or more players remain, hands are shown; the best hand wins, ties split the pot.",
        ))
        H("Actions")
        Steps(listOf(
            "Fold" to "Give up the hand; chips already bet stay in the pot.",
            "Check" to "Pass the action when nobody has bet this round.",
            "Call" to "Match the current highest bet.",
            "Bet / Raise" to "Put chips in first, or increase someone's bet. In no-limit a raise must be at least the size of the previous raise; the maximum is all-in.",
            "All-in" to "Push all your chips. If you can't cover a bet you can still go all-in; a side pot is created and you can only win as much from each player as you put in.",
        ))
        H("The button moves every hand")
        P("After each hand the dealer button moves one seat clockwise, so everyone takes turns in every position.")
    }
}

@Composable
private fun Steps(items: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((i, it) in items.withIndex()) {
            Row {
                Box(Modifier.size(20.dp).clip(CircleShape).background(Palette.GoldDim), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", color = Palette.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(it.first, color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(it.second, color = Palette.TextDim, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        }
    }
}

// ----------------------------------------------------------------- rankings

private data class RankRow(val zh: String, val en: String, val cards: String, val zhDesc: String, val enDesc: String)

private val RANKS = listOf(
    RankRow("皇家同花顺", "Royal flush", "AsKsQsJsTs", "同花色的 A-K-Q-J-10，最大的牌", "A-K-Q-J-10 of one suit"),
    RankRow("同花顺", "Straight flush", "9h8h7h6h5h", "同花色的五张连牌", "Five in a row, same suit"),
    RankRow("四条", "Four of a kind", "QcQdQhQs7d", "四张点数相同", "Four cards of one rank"),
    RankRow("葫芦", "Full house", "KcKdKh4s4d", "三条 + 一对", "Three of a kind plus a pair"),
    RankRow("同花", "Flush", "AdJd8d5d2d", "五张同花色（不连）", "Five of one suit"),
    RankRow("顺子", "Straight", "Tc9d8s7h6c", "五张连续点数；A-2-3-4-5 是最小的顺子", "Five in a row; A-2-3-4-5 is the lowest"),
    RankRow("三条", "Three of a kind", "8c8h8sKdQc", "三张点数相同", "Three cards of one rank"),
    RankRow("两对", "Two pair", "JcJdTs Th4c".replace(" ", ""), "两个对子", "Two different pairs"),
    RankRow("一对", "One pair", "AhAc9s6d3c", "一个对子", "Two cards of one rank"),
    RankRow("高牌", "High card", "AsQd9c6h2s", "以上都不是，比最大的单张", "None of the above; highest card plays"),
)

@Composable
private fun Rankings(zh: Boolean) {
    H(if (zh) "从大到小" else "Strongest to weakest")
    for ((i, r) in RANKS.withIndex()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Palette.PanelHi).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${i + 1}", color = Palette.Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
            Column(Modifier.width(118.dp)) {
                Text(if (zh) r.zh else r.en, color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(if (zh) r.zhDesc else r.enDesc, color = Palette.TextDim, fontSize = 10.sp, lineHeight = 13.sp)
            }
            Spacer(Modifier.width(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (c in Cards.parseMany(r.cards)) PlayingCard(c, 26.dp)
            }
        }
    }
    H(if (zh) "比大小的细节" else "Tie-breaks")
    P(
        if (zh) "• 花色不分大小。\n• 牌型相同时比点数，再比踢脚(Kicker)：比如都是一对A，就比剩下三张里最大的。\n• 只比最好的5张：如果最好的5张全在公共牌上，所有人平分。\n• 同点数的顺子、同花平分底池。"
        else "• Suits never matter.\n• Same category: compare ranks, then kickers (e.g. two players with a pair of aces compare their next highest cards).\n• Only the best five cards count — if the board is the best hand, everyone still in splits.\n• Identical straights or flushes split the pot."
    )
}

// ----------------------------------------------------------------- positions

@Composable
private fun Positions(zh: Boolean) {
    H(if (zh) "8人桌的位置" else "Positions at an 8-handed table")
    BoxWithConstraints(Modifier.fillMaxWidth().height(190.dp)) {
        val w = maxWidth.value
        val h = maxHeight.value
        val labels = listOf("BTN", "SB", "BB", "UTG", "UTG+1", "UTG+2", "HJ", "CO")
        Canvas(Modifier.fillMaxSize()) {
            val d = density
            val rx = w * 0.30f * d
            val ry = h * 0.32f * d
            val c = Offset(w / 2 * d, h / 2 * d)
            drawOval(Palette.Rail, Offset(c.x - rx - 8 * d, c.y - ry - 8 * d), Size((rx + 8 * d) * 2, (ry + 8 * d) * 2))
            drawOval(Brush.radialGradient(listOf(Palette.FeltCenter, Palette.FeltEdge), c, rx), Offset(c.x - rx, c.y - ry), Size(rx * 2, ry * 2))
        }
        Text(
            if (zh) "顺时针行动 ↻" else "Action moves clockwise ↻", color = Palette.Text.copy(alpha = 0.8f), fontSize = 11.sp,
            modifier = Modifier.align(Alignment.Center),
        )
        for ((i, l) in labels.withIndex()) {
            val ang = Math.toRadians(90.0 + i * 45.0)
            val x = w / 2 + w * 0.40f * cos(ang).toFloat()
            val y = h / 2 + h * 0.42f * sin(ang).toFloat()
            val color = when (l) {
                "BTN", "CO" -> Palette.Green
                "SB", "BB" -> Palette.Blue
                "HJ" -> Palette.Gold
                else -> Palette.Red
            }
            Box(
                Modifier.offset((x - 30).dp, (y - 11).dp).width(60.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.85f))
                    .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) { Text(l, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }
    }
    val rows = if (zh) listOf(
        "BTN · Button 按钮位/庄位" to "翻牌后永远最后行动，信息最多，是最赚钱的位置，可以玩最宽的范围（约40%+的手牌）。",
        "SB · Small Blind 小盲" to "已经强制下了半个大盲，但翻牌后最先行动，位置最差。",
        "BB · Big Blind 大盲" to "已经下了一个大盲，别人加注时可以用较宽的范围防守，因为跟注价格便宜。",
        "UTG · Under The Gun 枪口位" to "翻牌前第一个行动，后面还有很多人没说话，要打得最紧（约12–17%）。",
        "UTG+1 / UTG+2 · 枪口位左边 1、2 位" to "前位，人多的桌子才有，仍然要紧。",
        "HJ · Hijack 劫持位" to "中位，范围比前位宽一些（约20%）。",
        "CO · Cutoff 关煞位" to "按钮右边，后位，可以打得比较宽（约28%）。",
    ) else listOf(
        "BTN · Button" to "Acts last after the flop — the best seat. You can play the widest range (40%+ of hands).",
        "SB · Small Blind" to "Already paid half a blind but acts first after the flop: the worst position.",
        "BB · Big Blind" to "Already paid a full blind, so can defend widely against raises at a cheap price.",
        "UTG · Under The Gun" to "First to act preflop with many players behind: play the tightest (about 12–17%).",
        "UTG+1 / UTG+2 · one / two seats left of UTG" to "Early positions at bigger tables; still tight.",
        "HJ · Hijack" to "Middle position, a bit wider (about 20%).",
        "CO · Cutoff" to "Right of the button, late position, fairly wide (about 28%).",
    )
    for ((t, d) in rows) {
        Text(t, color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(d, color = Palette.TextDim, fontSize = 12.sp, lineHeight = 18.sp)
    }
    H(if (zh) "为什么位置重要" else "Why position matters")
    P(
        if (zh) "后行动的人能先看到前面的人怎么做，再决定自己怎么打。同样一手牌，在按钮位置比在枪口位置值钱得多。人越少，每个位置的范围越宽；单挑时按钮(也是小盲)可以玩 80% 左右的牌。"
        else "Acting later means you see what others do before deciding. The same hand is worth much more on the button than under the gun. With fewer players every range widens; heads-up the button (who is also the small blind) plays around 80% of hands."
    )
}

// ----------------------------------------------------------------- glossary

/** [full]: the full English name of an abbreviation (shown in both languages). */
private data class Term(val zh: String, val zhDesc: String, val en: String, val enDesc: String, val full: String = "")

private val TERMS = listOf(
    Term("BB / 大盲", "大盲注。本软件用多少个大盲来衡量筹码和下注，比如 100 BB 表示 100 个大盲。锦标赛涨盲后，同样的筹码折合的 BB 数会变少。", "BB", "The big blind. Stacks and bets are measured in big blinds, e.g. 100 BB. When tournament blinds rise, the same chips are worth fewer BB.", "Big Blind"),
    Term("SB / 小盲", "小盲注，等于半个大盲。", "SB", "The small blind: half of a big blind.", "Small Blind"),
    Term("BTN / 按钮位", "庄家按钮所在位置，翻牌后最后行动。", "BTN", "The dealer button seat; acts last after the flop.", "Button"),
    Term("UTG / 枪口位", "翻牌前第一个行动的位置，就像\"在枪口下\"。UTG+1、UTG+2 是它左边的位置。", "UTG", "First to act preflop. UTG+1 and UTG+2 are the seats to its left.", "Under The Gun"),
    Term("HJ / 劫持位", "关煞位右边的中间位置。", "HJ", "The middle-position seat right of the cutoff.", "Hijack"),
    Term("CO / 关煞位", "按钮右边的位置，后位。", "CO", "The seat right of the button; late position.", "Cutoff"),
    Term("前注 Ante", "每人每手都要交的小额强制下注，让底池更大，常见于锦标赛后期。", "Ante", "A small forced bet from every player each hand, common later in tournaments."),
    Term("底池 Pot", "本手所有人已经投入的筹码总和。", "Pot", "All chips put in so far this hand."),
    Term("全下 All-in", "把剩下的筹码全部推进去。", "All-in", "Putting all your remaining chips in the pot."),
    Term("边池 Side pot", "有人全下但筹码比别人少时，超出部分单独形成边池，全下的人赢不到边池。", "Side pot", "When a player is all-in for less, the extra chips form a side pot that player can't win."),
    Term("底池赔率 Pot odds", "跟注额和跟注后底池的比例。例：底池 4.7BB，需跟 2BB，赔率是 1:2.4。", "Pot odds", "The price of a call relative to the pot, e.g. calling 2 BB into 4.7 BB is 1 : 2.4."),
    Term("所需胜率", "跟注不亏所需的最低胜率 = 跟注额 ÷ (底池 + 跟注额)。你的胜率高于它，长期跟注就是赚的。", "Equity needed", "Minimum winning chance for a call to break even = call ÷ (pot + call)."),
    Term("胜率 Equity", "如果现在全部亮牌并发完公共牌，你赢下底池的概率（复盘里能看到）。", "Equity", "Your share of the pot if all cards were shown and the board dealt out (shown in replays)."),
    Term("SPR / 筹码底池比", "= 有效筹码 ÷ 底池。SPR 越小，越容易全下；SPR 很大时要更谨慎地用一对这样的牌投入全部筹码。", "SPR", "Effective stack ÷ pot. Low SPR means you are close to all-in.", "Stack-to-Pot Ratio"),
    Term("有效筹码", "两人中筹码较少的一方的筹码量，决定了最多能输赢多少。", "Effective stack", "The smaller of two stacks: the most either can win or lose."),
    Term("范围 Range", "对手在某种行动下可能持有的所有手牌集合。高手思考的是范围，而不是猜一手具体的牌。", "Range", "All the hands a player could have given their actions. Think in ranges, not single hands."),
    Term("GTO / 博弈论最优", "一种无法被剥削的平衡打法。真正的 GTO 只在单挑有可靠解，多人桌使用近似解。", "GTO", "A balanced strategy that can't be exploited. Exact only heads-up; multi-way bots use approximations.", "Game Theory Optimal"),
    Term("剥削 Exploit", "针对对手的漏洞调整打法，比如对跟注站少诈唬、多价值下注。", "Exploit", "Deviating to attack an opponent's leaks, e.g. bluff less and value-bet more against a calling station."),
    Term("RFI / 开池加注", "前面的人都弃牌时第一个加注入池，也叫 Open。", "RFI / Open", "The first raise when everyone before you folded.", "Raise First In"),
    Term("溜入 Limp", "翻牌前只跟注大盲而不加注入池，通常不推荐。", "Limp", "Just calling the big blind preflop instead of raising; usually not recommended."),
    Term("3-Bet / 4-Bet", "第一次加注算 2-Bet（大盲算第 1 注），对加注再加注叫 3-Bet，对 3-Bet 再加注叫 4-Bet。", "3-Bet / 4-Bet", "The open is the 2nd bet (the big blind is the 1st); re-raising it is a 3-bet; re-raising that is a 4-bet.", "Third bet / Fourth bet"),
    Term("C-bet / 持续下注", "翻牌前加注的人在翻牌继续下注。", "C-bet", "The preflop raiser bets again on the flop.", "Continuation Bet"),
    Term("价值下注 Value bet", "用你认为领先的牌下注，希望更差的牌跟注。", "Value bet", "Betting a hand you think is best so worse hands call."),
    Term("诈唬 Bluff", "用很弱的牌下注，希望更好的牌弃牌。半诈唬(Semi-bluff)是用听牌诈唬，被跟注后还有机会成牌。", "Bluff", "Betting a weak hand to make better hands fold. A semi-bluff has a draw that can still improve."),
    Term("听牌 Draw", "还差一张就能成同花或顺子的牌，比如四张同花。", "Draw", "A hand that needs one more card to make a flush or straight."),
    Term("出张 Outs", "能让你成牌的剩余张数。同花听牌有 9 张，两头顺子听牌有 8 张。", "Outs", "Cards that complete your hand: 9 for a flush draw, 8 for an open-ended straight draw."),
    Term("2/4 法则", "粗算成牌概率：翻牌圈 出张×4%（看两张），转牌圈 出张×2%（看一张）。", "Rule of 2 and 4", "Quick odds: outs × 4% on the flop (two cards to come), outs × 2% on the turn."),
    Term("踢脚 Kicker", "牌型相同时用来比大小的剩余单张。", "Kicker", "The side card that breaks ties between equal hands."),
    Term("坚果 Nuts", "当前公共牌下最大的牌。", "Nuts", "The best possible hand on the current board."),
    Term("顶对 / 超对 / 暗三条", "顶对 Top pair：用底牌配上公共牌最大的那张。超对 Overpair：口袋对子比所有公共牌都大。暗三条 Set：口袋对子配中公共牌。", "Top pair / Overpair / Set", "Top pair: pairing the highest board card. Overpair: a pocket pair above every board card. Set: a pocket pair that hits the board."),
    Term("翻牌 / 转牌 / 河牌", "第 3、4、5 张公共牌出现的三个阶段（翻牌一次发三张）。", "Flop / Turn / River", "The 3-card, 4th and 5th community card stages."),
    Term("摊牌 Showdown", "最后一轮下注结束后亮牌比大小。", "Showdown", "Showing hands after the last betting round."),
    Term("ICM / 独立筹码模型", "锦标赛里把筹码换算成奖金价值的模型。临近奖励圈时，输光的代价更大，需要更紧。", "ICM", "Converts tournament chips into prize value. Near the money, busting costs more, so play tighter.", "Independent Chip Model"),
    Term("泡沫 Bubble", "离进入奖励圈只差一个人出局的阶段。", "Bubble", "When one more elimination puts everyone left in the money."),
    Term("ITM / 进入奖励圈", "锦标赛中进入有奖名次。", "ITM", "Finishing in a paid place in a tournament.", "In The Money"),
    Term("推/弃 Push/Fold", "筹码很短（约 15BB 以下）时，只选择全下或弃牌的打法。", "Push/fold", "With a short stack (under ~15 BB) you either go all-in or fold."),
    Term("NLHE / 无限注德州", "下注上限只受自己筹码限制的德州扑克，本软件就是这种。", "NLHE", "Hold'em where the only betting limit is your stack — what this app plays.", "No-Limit Hold'em"),
    Term("VPIP / 主动入池率", "翻牌前自愿投入筹码（跟注或加注，不含盲注）的比例。常规玩家 6 人桌约 20–28%。", "VPIP", "How often you put chips in preflop by choice (blinds don't count). Typical 6-max regulars: about 20–28%.", "Voluntarily Put money In Pot"),
    Term("PFR / 翻前加注率", "翻牌前加注的比例。和 VPIP 越接近，说明越少溜入/跟注，打法越主动。", "PFR", "How often you raise preflop. The closer to VPIP, the more aggressive.", "Pre-Flop Raise"),
    Term("WTSD / 摊牌率", "看到翻牌后一路打到摊牌的比例。", "WTSD", "How often you reach showdown after seeing the flop.", "Went To ShowDown"),
    Term("W\$SD / 摊牌胜率", "打到摊牌时赢钱的比例。", "W\$SD", "How often you win money when you reach showdown.", "Won \$ at ShowDown"),
    Term("BB/100", "每 100 手赢多少个大盲，衡量胜率的标准指标。", "BB/100", "Big blinds won per 100 hands — the standard win-rate measure.", "Big Blinds per 100 hands"),
    Term("TAG / 紧凶", "玩得少，但一旦入池就主动下注加注。", "TAG", "Plays few hands but bets and raises them.", "Tight-Aggressive"),
    Term("LAG / 松凶", "玩得多且主动。", "LAG", "Plays many hands aggressively.", "Loose-Aggressive"),
    Term("跟注站 / 紧弱 / 疯子", "跟注站 Calling station：什么都跟很少弃牌；紧弱 Nit：只玩好牌且被动；疯子 Maniac：疯狂加注诈唬。", "Station / Nit / Maniac", "A calling station calls too much; a nit plays only premium hands passively; a maniac raises and bluffs constantly."),
)

@Composable
private fun Glossary(zh: Boolean) {
    H(if (zh) "常用术语" else "Common terms")
    for (t in TERMS) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Palette.PanelHi).padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (zh) t.zh else t.en, color = Palette.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                if (t.full.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(t.full, color = Palette.Blue, fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                }
            }
            Text(if (zh) t.zhDesc else t.enDesc, color = Palette.Text, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

// ----------------------------------------------------------------- tips & app

private val TIPS_ZH = listOf(
    "1. 先打紧一点" to "新手最常见的错误是玩太多牌。前位只玩大对子(AA–TT)、AK、AQ、AJs、KQs 这类强牌；越靠近按钮可以越宽。练习模式里的\"起手牌 · 前 X%\"能帮你判断这手牌有多强。",
    "2. 要么加注，要么弃牌" to "前面没人入池时，尽量用加注（约 2.5 个大盲）入池，而不是只跟注大盲(Limp)。",
    "3. 用底池赔率决定跟注" to "打开练习模式，看\"所需胜率\"。比如需要 25% 胜率，而你有同花听牌（翻牌圈约 36%），跟注就是对的。",
    "4. 重视位置" to "同样的牌，在按钮位比在前位好打得多。位置差时少玩边缘牌。",
    "5. 不要爱上一对" to "顶对在多人底池、湿润牌面、对手大额加注时经常已经落后。对手很被动却突然加注，通常代表强牌。",
    "6. 针对对手调整" to "在混合桌：对跟注站少诈唬、多价值下注；对紧弱玩家多偷盲；对疯子用好牌让他来诈唬你。",
    "7. 每局复盘" to "去\"牌局记录\"打开输得多的手牌，看每条街的胜率和 GTO 建议，标记\"偏离\"的地方就是要改进的地方。",
    "8. 锦标赛：筹码短就推/弃" to "低于 15 个大盲时，宁可全下或弃牌，也不要小额加注后被迫弃牌。临近奖励圈时谨慎跟注别人的全下。",
)

private val TIPS_EN = listOf(
    "1. Start tight" to "The most common beginner mistake is playing too many hands. From early position stick to big pairs (AA–TT), AK, AQ, AJs, KQs; widen as you get closer to the button. Practice mode shows \"top X%\" for your starting hand.",
    "2. Raise or fold" to "When nobody has entered the pot, come in with a raise (about 2.5 BB) rather than limping.",
    "3. Use pot odds" to "Turn on practice mode and look at \"equity needed\". If you need 25% and hold a flush draw on the flop (~36%), calling is right.",
    "4. Respect position" to "The same hand is much easier to play on the button. Play fewer marginal hands out of position.",
    "5. Don't marry one pair" to "Top pair is often behind in multi-way pots, on wet boards or against big raises. A passive player who suddenly raises usually has it.",
    "6. Adjust to opponents" to "On a mixed table: value-bet stations and bluff them less, steal blinds from nits, let maniacs bluff into your strong hands.",
    "7. Review every session" to "Open your biggest losing hands in History. Check equity on each street and the GTO suggestion; every \"deviation\" is something to work on.",
    "8. Tournaments: short stack = push/fold" to "Below ~15 BB prefer all-in or fold over small raises you'll have to fold to a shove. Near the money, call all-ins carefully.",
)

private val APP_ZH = listOf(
    "模式" to "筹码局：盲注固定，NPC 输光离场，打到只剩一人或你退出时按筹码结算排名。锦标赛：每 N 手涨盲，后期加前注，奖励按名次分配，NPC 会考虑 ICM。注意：涨盲后所有人的筹码没有变，但用大盲(BB)衡量的数字会越来越小——这正是锦标赛逼大家行动的方式。",
    "牌桌" to "GTO桌：所有 NPC 使用近似 GTO 策略，适合练习\"不被剥削\"。混合桌：随机分配紧凶、松凶、跟注站、紧弱、疯子、GTO 等风格，更像真实牌局，适合练习剥削。",
    "音效" to "牌桌右上角 🔊 可以随时静音，设置里也能关闭。快进时自动静音。",
    "练习模式" to "牌局中随时开关。打开后在你行动时显示底池、需跟注、底池赔率、所需胜率、SPR 和你当前的牌型，但不会显示胜率。",
    "本手结束" to "每手结束后会亮出摊牌玩家的底牌、高亮赢家的 5 张牌，并在右侧显示结果。点\"下一手\"才会继续。你弃牌后可以点\"快进本手\"。",
    "复盘" to "\"牌局记录\"保存最近 1000 手，点开可以一步步回放，显示所有人底牌、每条街的胜率，并在你的每个决策点给出 GTO 建议和\"符合/偏离\"标记。点 ☆ 可以永久收藏。",
    "统计" to "VPIP、PFR、3-Bet、摊牌率、BB/100、各位置输赢以及生涯成绩。",
)

private val APP_EN = listOf(
    "Modes" to "Cash: blinds stay fixed; busted bots leave; play until one player remains or you leave, standings by chips. Tournament: blinds rise every N hands, antes later, prizes by finishing place, bots use ICM. When blinds rise nobody loses chips, but stacks measured in big blinds (BB) shrink — that pressure is how tournaments force action.",
    "Tables" to "GTO table: every bot plays approximate GTO — practice not being exploited. Mixed table: random TAG, LAG, station, nit, maniac and GTO bots — closer to a real game, practice exploiting.",
    "Sound" to "Tap 🔊 at the top right of the table to mute; also in Settings. Sounds are muted while skipping.",
    "Practice mode" to "Toggle any time. On your turn it shows pot, amount to call, pot odds, equity needed, SPR and your current hand — never your equity.",
    "End of a hand" to "Showdown hands are revealed, the winning five cards are highlighted and the result is shown on the right. Nothing continues until you tap \"Next hand\". After folding you can tap \"Skip hand\".",
    "Replay" to "History keeps the last 1000 hands. Step through any of them with every hole card shown, equity per street, and a GTO suggestion with an \"in line / deviation\" flag for each of your decisions. Tap ☆ to keep a hand forever.",
    "Stats" to "VPIP, PFR, 3-Bet, showdown stats, BB/100, results by position and career totals.",
)
