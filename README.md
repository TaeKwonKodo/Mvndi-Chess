# Chess for Minecraft (Fabric 1.21.11, client-side)

Play chess against the computer in a Minecraft GUI. Client-only: nothing needs to be installed on a server.

- Press **K** (rebindable under Controls > Chess) or type **/chess** to open the board.
- Click a piece, then a highlighted square. Pawns promote to queens automatically.
- Buttons: New Game, Play as White/Black, Level (Beginner / Casual / Club / Strong), Undo, Close.
- Your game is kept when you close the board and reopen it (until you quit Minecraft).
- Rules: castling, en passant, promotion, check/checkmate/stalemate, 50-move, repetition, insufficient material.

## Build on GitHub (no setup)

1. Create a new GitHub repo and upload everything in this folder (including the hidden `.github` folder).
2. Open the repo's **Actions** tab. The "Build mod" workflow runs on every push.
3. When it finishes, open the run and download the **chessmod** artifact. Unzip it and drop `chessmod-1.0.0.jar` into `.minecraft/mods`.

For a permanent download link, tag a release: `git tag v1.0.0 && git push --tags`. The jar is attached to the Release page.

## Install

You need Fabric Loader 0.18.1+ and the Fabric API mod for Minecraft 1.21.11.

## Build locally

Install JDK 21 and Gradle, then run `gradle build`. The jar is in `build/libs/`.
(Or run `gradle wrapper` once to generate `./gradlew`.)

## If the build fails on a version

Open `gradle.properties` and use the versions shown for 1.21.11 on https://fabricmc.net/develop.
