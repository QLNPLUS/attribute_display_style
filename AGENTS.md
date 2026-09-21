# Attribute Display Style branch matrix

| Branch | Worktree | Loader | Minecraft | JDK | Gradle/plugin |
|---|---|---|---|---|---|
| forge-1.20.1 | D:\projects\attribute_display_style\forge-1.20.1 | Forge | 1.20.1 | 17 | Gradle 8.8, ForgeGradle 6.0.x |
| neoforge-1.20.4 | D:\projects\attribute_display_style\neoforge-1.20.4 | NeoForge | 1.20.4 | 17 | Gradle 8.8, NeoGradle userdev 7.0.97 |
| neoforge-1.21.1 | D:\projects\attribute_display_style\neoforge-1.21.1 | NeoForge | 1.21.1 | 21 | Gradle 8.8, NeoGradle userdev 7.x |
| neoforge-26.1.2 | D:\projects\attribute_display_style\neoforge-26.1.2 | NeoForge | 26.1.2 | 25 | Gradle 8.8, ModDevGradle 2.0.141 |

The primary branch and main worktree are forge-1.20.1. Each other branch has exactly one linked worktree.

Cross-version changes are propagated only after the source branch is committed and verified, using git cherry-pick -x. Do not hand-rewrite a portable change on another branch. Applicability must be reported as applies, does not apply because it is platform-specific, or needs adaptation because of an API rename. Fixes from a non-primary branch return to the primary branch before moving onward.

Known platform gaps are the 1.20.x tooltip marker mixin, 1.21+ Data Components attribute storage, and 26.1 GuiGraphicsExtractor rendering. These are version-specific adapter points.

Release tags use v<version>-<loader>-<mcversion>, for example v1.0.0-neoforge-1.21.1.

