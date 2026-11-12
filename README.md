# Chess for Minecraft (Fabric 1.21.11, client-side)

Play chess against the computer in a Minecraft GUI

- Press **K** (rebindable under Controls > Chess) or type **/chess** to open the board.
- Click a piece, then a highlighted square. Pawns promote to queens automatically.
- Buttons: New Game, Play as White/Black, Level (Beginner / Casual / Club / Strong), Undo, Close.
- Your game is kept when you close the board and reopen it (until you quit Minecraft).
- Chess Rules: castling, en passant, promotion, check/checkmate/stalemate, 50-move, repetition, insufficient material.

## Build on GitHub

1. Open the repo's **Actions** tab. The "Build mod" workflow runs automatically
2. When it finishes, open the run and download the **chessmod** artifact. Unzip it and drop `chessmod-1.0.0.jar` into `.minecraft/mods`.

For future reference: [For a permanent download link, tag a release: `git tag v1.0.0 && git push --tags`. The jar is attached to the Release page.]

## Install

You need Fabric Loader 0.18.1+ and the Fabric API mod for Minecraft 1.21.11.

