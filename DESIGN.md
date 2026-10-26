# River Runner design system

A night card room by a river: deep ink surfaces, a river-teal felt, brass chips.
The table is the one bold element; everything around it stays quiet.

## Colour has a job

| Token | Hex | Used for — and only for |
|---|---|---|
| Ink | `#0C141C` | App background, dark text on light fills |
| Slate / Slate raised | `#142231` / `#1C2E40` | Panels, cards, inactive controls |
| Chalk | `#EAF0F4` | Primary text and headings |
| Mist | `#9FB2C3` | Secondary text (7.4:1 on Slate) |
| River teal felt | `#137A72` → `#09393A` | The table only |
| **Brass** | `#E3B04B` | **Money**: stacks, pot, bets, winnings, tournament titles |
| **River cyan** | `#46D3E6` | **You and interaction**: your turn, selection, sliders, primary button, focus |
| Green / Red | `#3FCB86` / `#F06A63` | Results (won / lost) and the call / fold buttons |
| Raise orange | `#E08A2E` | Raise button (always with Ink text, 6.9:1) |

If something is gold, it is money. If it is cyan, it is you or it reacts to a tap.

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
