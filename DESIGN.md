# Earnd visual design — "Cyberpunk, sodium amber, compact" (2026-10-31; supersedes earlier drafts)

Cyberpunk via sodium-streetlight amber, signal red and warm graphite (amber CRT / rain-at-night
city), deliberately avoiding the recognizable "AI slop" palette (neon cyan on blue-black, purple/
magenta gradients, glows).

## Anti-slop rules (hard)
- No cyan/teal neon accent, no purple/violet, no magenta, no gradients (incl. gradient text), no glows,
  glassmorphism, blurred halos, grid/stripe backgrounds.
- No blinking cursors or pulsing dots on static content; no bounce easing.
- No colored side-stripe borders on cards/rows; no tiny mono tags above every heading; no icon tiles.
- No nested cards; vary spacing (tight within groups, wider between).
- Uppercase only for very short labels; sentence case elsewhere. No em-dash-heavy or hype copy.
- Not Inter/Geist.

## Palette (dark default) — 5 roles
- Graphite bg #121110, raised #1D1B18, line #2B2824 / divider #24211E (warm, not blue-black).
- Text #E9E4DA, secondary #A39C8F, muted #8F887B, done #6F695F, edge #7A7368 (borders on quiet controls).
- Amber #FFB000 = earned time, rewards, habit checkboxes, progress. Large filled amber areas use
  amberFill #E9A412 (slightly less saturated); amber stays on numerals and text.
- Signal red #FF4A3D = locked state, the one primary action per screen, "ignored N×". Nothing else.
- Selection (chips, weekday pickers, unlock amounts) is neutral: raised #1D1B18 fill plus a 1dp text
  border when chosen, a 1dp edge border when not. Amber never means "selected".
- Interactive state is neutral too: switches, nav selection, tab indicators, sliders and text-field
  focus draw in text/edge. Material's `primary` is text, not amber, so stock components cannot leak it.
- Tasks use neutral text-colored checkboxes; amber only when they carry a reward. Priority "High" is
  secondary text, never red.
- Empty drain segments use drainEmpty #3F392E.
- Light variant: paper #F2EFE8, surface #FFFFFF, line #C0B9A8, divider #CCC5B6, text #121110; amber → #855C00 (≥4.5:1 on paper and surface), done → #6F695D, edge → #7D7669, red → #C9281C. All light tokens checked with a WCAG script: body text ≥4.5:1, control outlines ≥3:1, hairlines and empty progress tracks ~1.7-2:1 so structure stays visible on cream.

## Type (SIL OFL, bundle in res/font)
- Chakra Petch 500/600 for UI and headings (compact: body 13–14sp, section headings 12sp semibold
  in secondary color, screen titles 18sp).
- IBM Plex Mono 500 only for numbers/times/counters (11sp), normal case. The balance is the one hero
  number: 64sp mono, amber when positive.
- Wordmark: "earnd" lowercase, Chakra Petch 600, amber, no cursor.

## Shapes & layout
- Chamfered corners (7dp, top-left + bottom-right) on the time-bank card, filled buttons and the lock
  badge only; list rows are flat with 1dp dividers (no per-row cards). Quiet header controls (off-day
  chip, add) are plain squares so they never read as the primary action.
- Checkbox: 20dp square outline; when done it fills and draws a check glyph in text color. Row is the
  button (≥48dp touch target).
- Thin 3dp progress bar under the time bank.
- Today: wordmark + time + off-day chip + add → time-bank card (state, balance, Unlock) → habits
  done ("N of M habits") → "Next" (one hero habit, most ignored first) → "Later today" → "Tasks today".
- Lock screen: red chamfered badge "<App> locked", "Not yet.", earnable rows, quiet "Back to home".
- A ruler always carries its scale caption (Today: the day reference; lock: the minutes needed), so
  the same shape never means two quantities. Earn rows draw the same outline checkbox as the task
  lists; the amber "+N" numeral says what it pays. Row rewards stay bare numerals in a narrow
  column; the hero balance and start labels carry the "min" unit for the whole screen.
- A picker with a single option is not drawn: the unlock button already states the amount, and one
  full-width chip reads as a focused input field.
- Row-end start actions ("Start N min") are outlined quiet chips, not bare text: the affordance has
  to be visible for the one action that runs a timer.
- Settings category rows are bodyMedium under the 18sp screen title; navigation never matches the
  weight of the title it sits under. The bottom bar boundary uses the stronger line hairline, not
  the list divider.
- A form's save action ends the form: inline at the foot of a short form's content (the ~32dp above
  it is section spacing), pinned to the screen foot on long forms where inline would fall below the
  fold.
- Widgets, notifications accent and launcher icon follow this palette (icon: amber chamfered mark on
  graphite).
