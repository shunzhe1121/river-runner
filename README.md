# River Runner ♠

A personal, fully offline Texas Hold'em trainer for Android — drill your ranges, sharpen your decisions, and run it to the river.

单机德州扑克练习 App（安卓，横屏，开源）。

**[⬇ Download the latest APK / 下载最新 APK](https://github.com/shunzhe1121/river-runner/releases/tag/latest)**

## Features / 功能

| | |
|---|---|
| Tables / 牌桌 | Heads-up, 5, 6, 7, 8 players / 单挑、5–8 人桌 |
| Modes / 模式 | Cash (play until one player is left or you leave; standings by chips) and single-table tournament (blinds rise every N hands, ICM payouts) / 筹码局 + 单桌锦标赛 |
| Stacks / 筹码 | Starting stack set in big blinds / 以大盲数设置起始筹码 |
| Bots / NPC | **GTO table** (every bot plays approximate GTO) or **mixed table** (TAG, LAG, calling station, nit, maniac, GTO) / GTO 桌与混合桌 |
| Practice mode / 练习模式 | Optional pot odds, required equity and SPR during your decision — never your equity / 可选显示底池赔率、所需胜率、SPR（不显示胜率）|
| Replay / 复盘 | Step through any hand with **all hole cards shown**, exact equity on every street, and a **GTO suggestion** with a deviation flag for each of your decisions / 显示所有底牌、每条街胜率、GTO 建议与偏差标记 |
| History / 记录 | Last 1000 hands kept automatically (~400 bytes each, under 0.5 MB total); star hands to keep them forever / 自动保留最近 1000 手，收藏永久保存 |
| Stats / 统计 | VPIP, PFR, 3-Bet, WTSD, W$SD, BB/100, results by position, career totals / 入池率、加注率、摊牌率等 |
| Language / 语言 | 中文 / English, switchable in-app |

## How the bots decide / NPC 如何决策

True GTO solutions only exist for heads-up play; no one can solve 5–8 handed games exactly. The bots use an approximation built from published solver output:

- **Preflop**: position-based raise-first-in, 3-bet, 4-bet and calling ranges (e.g. BTN ≈ 44 %, CO ≈ 28 %, UTG 6-max ≈ 17 % opens) on a 169-hand ordering that blends simulated equity with playability. Borderline hands are played as mixed strategies.
- **Short stacks**: Nash-style push/fold widths by stack depth and players left to act; calls against all-ins compare equity vs. the shover's range to pot odds — or to **ICM** required equity in tournaments.
- **Postflop**: Monte Carlo equity against each opponent's range (narrowed by their actions), board-texture-based bet sizing, value/bluff frequencies, pot odds with equity realisation, and minimum-defence guards.

Styles (TAG, LAG, station, nit, maniac) shift range widths, aggression, bluffing and call-down thresholds so a mixed table has exploitable opponents.

真正的 GTO 只对单挑存在可靠解，多人局使用近似解：翻前按位置的范围表 + 混合策略；短筹码使用推/弃与 ICM；翻后根据对手范围的蒙特卡洛胜率、牌面结构、底池赔率做混合决策。

## Build / 构建

GitHub Actions builds a signed APK on every push to `main` and publishes it as the `latest` release.

```bash
./gradlew :engine:test          # rules, side pots, ICM, replay tests
./gradlew :app:assembleRelease  # APK in app/build/outputs/apk/release/
```

Project layout:

- `engine/` — pure Kotlin poker engine (no Android dependency): hand evaluator, NLHE state machine with side pots, bots, equity, ICM, hand records and replay, stats.
- `app/` — Jetpack Compose UI (landscape).

The signing key in `keystore/` is intentionally public so that every CI build can update the previous install. Don't reuse it for anything else.

## License

MIT
