THEME ANTICHEAT 1.0.0  (Paper 1.21.11, Java 21)

BUILD KAISE KARE (jar banana)
Option A - GitHub (sabse aasan, kuch install nahi karna):
  1. github.com par free account banao, New repository banao.
  2. Is zip ke andar ki saari files/folders (pom.xml, src, .github) repo me upload karo.
  3. Repo me "Actions" tab -> "Build Theme Anticheat" -> run hone do (1-2 min).
  4. Run khulne par neeche "Artifacts" me ThemeAnticheat download karo -> zip ke andar ThemeAnticheat.jar.

Option B - PC par:
  Java 21 JDK + Maven install karo, folder me terminal kholo: mvn package
  Jar milega: target/ThemeAnticheat.jar

INSTALL
  1. Vulcan aur Intave dono ki jar + folder plugins/ se hata do.
  2. ThemeAnticheat.jar plugins/ me daalo, server RESTART karo.
  3. LiteBans/EssentialsX ka tempban command zaruri hai.
  4. plugins/ThemeAnticheat/config.yml me settings badal sakte ho, /theme reload.

COMMANDS: /theme alerts | /theme vl <player> | /theme reload
PERMISSIONS: theme.admin, theme.alerts (OP), theme.bypass (kisi ko default nahi, OP ko bhi nahi)
