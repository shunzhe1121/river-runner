# River Runner design system

A classic card room: green felt, walnut rail, brass chips on a green-black room.
The table is the one bold element; everything around it stays quiet.

## Colour

| Token | Hex | Used for |
|---|---|---|
| Room | `#0C1310` | App background, dark text on gold |
| Panel / raised | `#141E19` / `#1E2A24` | Panels, cards, inactive controls |
| Text / muted | `#EEF1EA` / `#9DAAA2` | Words (muted text is 7:1 on panels) |
| Felt | `#1E7048` → `#0C3A25` | The table, the lobby's start area, the setup summary |
| Rail | `#241810` | Table and felt-panel borders |
| **Brass** | `#E6B54F` | Money (stacks, pot, bets, winnings), the primary action, the current selection, your turn |
| Green / Red | `#4FC88A` / `#F06A63` | Results; call `#1F7A50` and fold `#A83A33` buttons |

Gold fills are reserved for one primary action per screen (开始游戏, 开始, raise) and selected options; everything else uses gold only as text or a thin outline.

Achievement chips keep their own category colours (cyan start, violet skill, rose big hands, brass tournaments, green cash, orange just-for-fun, blue study).

## Type

- **Barlow Condensed** (SemiBold / Bold, SIL OFL, `res/font`) for every number a player reads at a glance: stacks, pot, bet chips, button amounts, stats, the wordmark. Condensed figures fit narrow seat pods and read like a scoreboard.
- System sans (Roboto / Noto Sans CJK) for all words.
- Scale (sp): 11 caption · 12–13 body · 14–16 subhead · 18–20 screen title · 24–30 display numbers. Nothing on the table is below 11sp.

## Shape and spacing

- Seat pods 118 × 48dp, radius 12. Panels radius 14–16. Pills fully rounded.
- Spacing steps: 4, 6, 8, 10, 12, 14dp.
- Touch targets ≥ 40dp in the dense landscape table panel, ≥ 46dp for primary actions.

## Motion

Motion answers the game, never decorates it: cards deal from the centre and flip, bets slide out of a seat and into the pot, the pot slides to the winner, the acting seat pulses with a thinking bar, your cards lift on your turn. Achievement unlocks slide down once with a chime.

## Achievement badges

Drawn as casino chips: the rim colour is the category (cyan start, violet skill, rose big hands, brass tournaments, green cash, blue study), six white edge inserts, a dashed inner ring and a short glyph. Locked chips are slate with a cyan progress arc.

## Copy

- Buttons name the action: "离开牌桌 / 继续打", "删除存档 / 保留", "清空记录 / 保留" — never "确定 / 取消" for destructive choices.
- Destructive dialogs say exactly what is lost and that it can't be undone.
- Empty states say what will appear and how to get it.
