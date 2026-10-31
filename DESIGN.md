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
- Text #E9E4DA, secondary #A39C8F, muted #7A7368, done #57524A.
- Amber #FFB000 = earned time, rewards, habit checkboxes, progress.
- Signal red #FF4A3D = locked state, the one primary action per screen, "ignored N×".
- Tasks use neutral text-colored checkboxes; amber only when they carry a reward.
- Light variant: paper #F2EFE8, surface #FFFFFF, line #D6D0C4, text #121110; amber → #9A6A00, red → #C9281C.

## Type (SIL OFL, bundle in res/font)
- Chakra Petch 500/600 for UI and headings (compact: body 13–14sp, section headings 12sp semibold
  in secondary color, screen titles 18sp, time bank 22sp).
- IBM Plex Mono 500 only for numbers/times/counters (11sp), normal case.
- Wordmark: "earnd" lowercase, Chakra Petch 600, amber, no cursor.

## Shapes & layout
- Chamfered corners (7dp, top-left + bottom-right) on the time-bank card, buttons and the lock badge
  only; list rows are flat with 1dp dividers (no per-row cards).
- Checkbox: square outline 14dp; filled muted when done. Row is the button (≥48dp touch target).
- Thin 3dp progress bar under the time bank.
- Today: wordmark + time → time-bank card (state, balance, Unlock) → progress → "Do now" (habits,
  most ignored first) → "Tasks today".
- Lock screen: red chamfered badge "<App> locked", "Not yet.", earnable rows, quiet "Back to home".
- Widgets, notifications accent and launcher icon follow this palette (icon: amber chamfered mark on
  graphite).
