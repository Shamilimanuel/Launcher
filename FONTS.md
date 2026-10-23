# Fonts in Seelie

All fonts live in `app/src/main/res/font/` as static .ttf files, one file per weight
(Regular 400, Medium 500, SemiBold 600, Bold 700 where the family has them). Downloaded from
Google Fonts on 23 Oct 2026. They are wired up in `ui/theme/LauncherLook.kt` (Nunito Sans)
and `ui/theme/Fonts.kt` (the rest).

Every family here uses the **SIL Open Font License 1.1 (OFL)**: free to use and bundle in an
app, including a paid one. The fonts may not be sold on their own. Full licence text:
https://openfontlicense.org (each family's own copy is in the google/fonts repo, `ofl/<name>/OFL.txt`).

Gaegu and Zen Maru Gothic are cut down to Latin letters only (Google's own `text=` subset),
because the full Korean/Japanese files were 3-4 MB per weight. The OFL allows that.

| Family | Weights | Licence | Source |
|---|---|---|---|
| Nunito Sans | 400, 500, 600, 700 | OFL 1.1 | https://fonts.google.com/specimen/Nunito+Sans |
| Josefin Sans ("Niagara Neo") | 400, 500, 600, 700 | OFL 1.1 | https://fonts.google.com/specimen/Josefin+Sans |
| Cinzel | 400, 500, 600, 700 | OFL 1.1 | https://fonts.google.com/specimen/Cinzel |
| Cormorant Garamond | 400, 500, 600, 700 | OFL 1.1 | https://fonts.google.com/specimen/Cormorant+Garamond |
| Fredoka | 400, 500, 600, 700 | OFL 1.1 | https://fonts.google.com/specimen/Fredoka |
| Fuzzy Bubbles | 400, 700 | OFL 1.1 | https://fonts.google.com/specimen/Fuzzy+Bubbles |
| Gaegu (Latin only) | 400, 700 | OFL 1.1 | https://fonts.google.com/specimen/Gaegu |
| Kalam | 400, 700 | OFL 1.1 | https://fonts.google.com/specimen/Kalam |
| Lexend | 400, 500, 600, 700 | OFL 1.1 | https://fonts.google.com/specimen/Lexend |
| Manrope | 400, 500, 600, 700 | OFL 1.1 | https://fonts.google.com/specimen/Manrope |
| Merienda | 400, 500, 600, 700 | OFL 1.1 | https://fonts.google.com/specimen/Merienda |
| Montserrat | 400, 500, 600, 700 | OFL 1.1 | https://fonts.google.com/specimen/Montserrat |
| Nothing You Could Do | 400 | OFL 1.1 | https://fonts.google.com/specimen/Nothing+You+Could+Do |
| Nunito | 400, 500, 600, 700 | OFL 1.1 | https://fonts.google.com/specimen/Nunito |
| Oswald | 400, 500, 600, 700 | OFL 1.1 | https://fonts.google.com/specimen/Oswald |
| Pangolin | 400 | OFL 1.1 | https://fonts.google.com/specimen/Pangolin |
| Zen Maru Gothic (Latin only) | 400, 500, 700 | OFL 1.1 | https://fonts.google.com/specimen/Zen+Maru+Gothic |
