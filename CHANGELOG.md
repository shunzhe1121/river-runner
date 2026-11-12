# 更新日志 | Changelog

每个版本一节，最新的在最上面。每条写成 `中文 | English`。
CI 从最上面一节读取版本号（例如 `## 0.4` 会构建出 0.4.x），并把这一节贴进 GitHub 发布说明；
App 内的“更新内容”也读取这个文件。

One section per version, newest first. Each line is `Chinese | English`.
CI takes the version from the top section (`## 0.4` builds 0.4.x) and copies that section into
the GitHub release notes; the in-app "What's new" reads this file too.

## 0.4 · 2026-11-11

### 新增 | Added
- 每手复盘：牌桌侧栏打开“每手复盘”，每手结束后直接进入这手的复盘，看完点“继续下一手” | Review each hand: turn on "Review each hand" at the table and every hand opens in replay when it ends; tap "Next hand" to continue
- 打牌时收藏：牌桌右上角的 ☆ 可以收藏正在打的这一手，打完也能补收藏 | Star a hand while playing with the ☆ at the top of the table, or right after it ends
- 复盘页的收藏按钮更显眼，标题显示第几手，方便在历史里找回来 | A clearer star button in replay, and the hand number in the title, so hands are easy to find in History
- GTO 下注尺寸分五档：轻注、中注、重注、满池、超池；复盘里显示每档的频率，你的尺寸偏离时会提示 | GTO bet sizes in five classes (small, medium, large, pot, overbet); replay shows the mix and flags an unusual size
- 更新内容：升级后首次打开会弹出本次更新，设置页也能查看全部记录 | What's new: shown once after an update, and the full list is in Settings

### 改进 | Changed
- 机器人开池、下注和加注的尺寸会混合使用，大小不再直接暴露牌力 | Bots mix their open, bet and raise sizes, so the size no longer gives away the hand

### 修复 | Fixed
- 修正单挑时机器人几乎不做价值下注、只会诈唬的问题 | Heads-up bots almost never bet for value, only as bluffs

## 0.3 · 2026-11-09

### 改进 | Changed
- 更换了 APK 签名密钥；从 0.2.x 升级需要先卸载旧版本再安装 | New APK signing key; uninstall a 0.2.x build before installing
- 项目主页加入截图、下载入口和安装说明 | Project page with screenshots, a download link and install notes

## 0.2 · 2026-10-30

### 新增 | Added
- 牌面配色设置：四色、四色底、红黑；梅花和黑桃改为矢量图形，小牌上也能分清 | Card colour setting (four-colour, filled, red and black); vector suit shapes so clubs and spades are easy to tell apart
- 6 个存档，每个存档有自己的生涯统计、手牌历史和成就 | Six save slots, each with its own career stats, hand history and achievements
- 35 个成就，分 7 类，包括“趣味”成就（比如拿 AA 输了） | 35 achievements in seven categories, including just-for-fun ones (such as losing with aces)
- 音效和静音开关，盲注上涨时有提示 | Sound effects with a mute button, and a notice when the blinds go up
- 新首页和单独的开局设置页，可以按上次设置快速开始 | New home screen and a separate game setup screen, with quick start using the last settings

### 改进 | Changed
- 绿色牌桌配金色的视觉风格，按钮文字对比度更高 | Green felt and brass look, with higher contrast on buttons
- 下注筹码不再互相遮挡，底池显示在公共牌下方 | Bet chips no longer overlap, and the pot sits below the board
- 练习模式面板更紧凑，不再挡住行动记录 | A more compact practice panel that no longer hides the action log
- 新手指南里的英文术语加上了全称 | Full names for the English terms in the guide

## 0.1 · 2026-10-26

### 新增 | Added
- 单机德州扑克练习：2、5、6、7、8 人桌，现金局和单桌锦标赛（ICM、按手数涨盲） | Offline Hold'em practice: 2, 5, 6, 7 and 8-player tables, cash games and single-table tournaments (ICM, blinds up by hands)
- 近似 GTO 的机器人，以及混合风格牌桌 | Approximate-GTO bots and mixed-style tables
- 练习模式：显示底池赔率、所需胜率、SPR 和当前牌型，不显示胜率 | Practice mode: pot odds, equity needed, SPR and your made hand, never your equity
- 复盘：显示所有底牌、每条街的胜率，以及你每个决策的 GTO 建议 | Replay with every hole card, equity on each street and a GTO suggestion for each of your decisions
- 保留最近 1000 手牌历史，可收藏；统计 VPIP、PFR、3-bet 等数据 | The last 1000 hands kept in History, with stars; stats such as VPIP, PFR and 3-bet
- 新手指南：规则、牌型大小、位置和术语 | Beginner guide: rules, hand rankings, positions and terms
- 牌桌动画、手动进入下一手、行动记录；中英文切换 | Table animations, manual next hand, action log; Chinese and English
