package com.example.chessmod;

import com.example.chessmod.chess.Engine;
import com.example.chessmod.chess.Game;
import com.example.chessmod.chess.Position;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * The chess board screen. Squares are drawn by hand; 64 invisible-under-the-board buttons
 * handle the clicks, so this does not depend on any mouse-event API details.
 */
public class ChessScreen extends Screen {
    // name, search depth, root noise (weakens play), time limit in ms
    private static final String[] LEVEL_NAMES = {"Beginner", "Casual", "Club", "Strong"};
    private static final int[] LEVEL_DEPTH = {1, 2, 3, 4};
    private static final int[] LEVEL_NOISE = {250, 80, 15, 0};
    private static final long[] LEVEL_TIME = {500, 1000, 3000, 5000};

    // State kept between openings of the screen.
    private static final Game GAME = new Game();
    private static boolean playerWhite = true;
    private static int level = 1;

    // Piece silhouettes, 8x8 each. Index = piece type (1=P ... 6=K).
    private static final String[][] SHAPES = {
            null,
            {"........", "...##...", "..####..", "...##...", "..####..", "...##...", "..####..", ".######."},
            {"..##....", ".####...", "######..", "#.####..", "..####..", "..#####.", ".#######", "########"},
            {"...##...", "..####..", ".##.###.", "..####..", "...##...", "..####..", "...##...", ".######."},
            {"##.##.##", "########", ".######.", "..####..", "..####..", "..####..", ".######.", "########"},
            {"#..##..#", "##.##.##", "########", ".######.", "..####..", "..####..", ".######.", "########"},
            {"...##...", "..####..", "...##...", ".######.", "..####..", "..####..", ".######.", "########"},
    };

    private static final int LIGHT = 0xFFF0D9B5;
    private static final int DARK = 0xFFB58863;

    private int cell, boardSize, boardX, boardY, panelX, panelW;
    private ButtonWidget sideBtn, levelBtn;

    private int selected = -1;
    private final int[] legal = new int[256];
    private int legalCount = 0;

    private boolean thinking = false;
    private int token = 0;
    private volatile int[] aiResult = null; // {token, move}

    public ChessScreen() {
        super(Text.literal("Chess"));
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        cell = Math.max(12, Math.min(40, (this.height - 50) / 8));
        boardSize = cell * 8;
        panelW = 110;
        int total = 12 + boardSize + 12 + panelW;
        boardX = (this.width - total) / 2 + 12;
        boardY = (this.height - boardSize) / 2 - 4;
        panelX = boardX + boardSize + 12;

        // One transparent click target per square (the board is drawn on top of these).
        for (int c = 0; c < 8; c++) {
            for (int r = 0; r < 8; r++) {
                final int dc = c, dr = r;
                this.addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> onSquare(sqAt(dc, dr)))
                        .dimensions(boardX + c * cell, boardY + r * cell, cell, cell).build());
            }
        }

        int by = boardY + 26;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("New Game"), b -> newGame())
                .dimensions(panelX, by, panelW, 20).build());
        sideBtn = this.addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
            playerWhite = !playerWhite;
            newGame();
        }).dimensions(panelX, by + 24, panelW, 20).build());
        levelBtn = this.addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
            level = (level + 1) % LEVEL_NAMES.length;
            updateLabels();
        }).dimensions(panelX, by + 48, panelW, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Undo"), b -> undo())
                .dimensions(panelX, by + 72, panelW, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> this.close())
                .dimensions(panelX, by + 96, panelW, 20).build());
        updateLabels();

        refreshLegal();
        maybeStartAi();
    }

    private void updateLabels() {
        sideBtn.setMessage(Text.literal("Play as: " + (playerWhite ? "White" : "Black")));
        levelBtn.setMessage(Text.literal("Level: " + LEVEL_NAMES[level]));
    }

    /** Board square index for a display cell (column 0..7, row 0..7 from the top). */
    private int sqAt(int col, int row) {
        int file = playerWhite ? col : 7 - col;
        int rank = playerWhite ? 7 - row : row;
        return rank * 8 + file;
    }

    // ------------------------------------------------------------------ game flow

    private void refreshLegal() {
        legalCount = GAME.pos().legalMoves(legal);
    }

    private boolean playerToMove() {
        return GAME.pos().white == playerWhite;
    }

    private boolean gameOver() {
        return GAME.status() != Game.Status.PLAYING;
    }

    private void newGame() {
        token++;
        thinking = false;
        selected = -1;
        GAME.reset();
        refreshLegal();
        updateLabels();
        maybeStartAi();
    }

    private void undo() {
        if (GAME.plies() == 0) return;
        token++;
        boolean wasThinking = thinking;
        thinking = false;
        selected = -1;
        // If it is the player's turn (or game over), take back the computer's reply and the player's move.
        // If the computer was still thinking, only the player's last move is taken back.
        GAME.undo(wasThinking ? 1 : (playerToMove() ? 2 : 1));
        refreshLegal();
        maybeStartAi();
    }

    private void onSquare(int sq) {
        if (thinking || gameOver() || !playerToMove()) return;
        Position p = GAME.pos();
        int piece = p.b[sq];
        boolean own = piece != 0 && (piece > 0) == playerWhite;
        if (selected >= 0) {
            int m = findMove(selected, sq);
            if (m >= 0) {
                selected = -1;
                GAME.play(m);
                refreshLegal();
                maybeStartAi();
                return;
            }
        }
        selected = own ? sq : -1;
    }

    /** Finds a legal move from->to; prefers queen promotion. Returns -1 if none. */
    private int findMove(int from, int to) {
        int found = -1;
        for (int i = 0; i < legalCount; i++) {
            int m = legal[i];
            if (Position.from(m) == from && Position.to(m) == to) {
                int pr = Position.promo(m);
                if (pr == 0 || pr == Position.Q) return m;
                found = m;
            }
        }
        return found;
    }

    private void maybeStartAi() {
        if (thinking || gameOver() || playerToMove()) return;
        thinking = true;
        final int myToken = ++token;
        final Position snapshot = GAME.pos().copy();
        final int depth = LEVEL_DEPTH[level];
        final int noise = LEVEL_NOISE[level];
        final long time = LEVEL_TIME[level];
        Thread t = new Thread(() -> {
            int m = -1;
            try {
                m = new Engine().pickMove(snapshot, depth, noise, time);
            } catch (Throwable ignored) {
            }
            aiResult = new int[]{myToken, m};
        }, "chess-ai");
        t.setDaemon(true);
        t.start();
    }

    private void pollAi() {
        int[] r = aiResult;
        if (r == null) return;
        aiResult = null;
        if (r[0] != token) return; // stale result (new game / undo happened)
        thinking = false;
        if (r[1] >= 0) {
            GAME.play(r[1]);
            refreshLegal();
        }
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        pollAi();

        Position pos = GAME.pos();
        int last = GAME.lastMove();
        int checkSq = pos.inCheck() ? pos.kingSquare(pos.white) : -1;

        ctx.fill(boardX - 3, boardY - 3, boardX + boardSize + 3, boardY + boardSize + 3, 0xFF202020);

        for (int c = 0; c < 8; c++) {
            for (int r = 0; r < 8; r++) {
                int sq = sqAt(c, r);
                int x = boardX + c * cell, y = boardY + r * cell;
                boolean lightSq = ((sq >> 3) + (sq & 7)) % 2 == 1;
                ctx.fill(x, y, x + cell, y + cell, lightSq ? LIGHT : DARK);
                if (last >= 0 && (sq == Position.from(last) || sq == Position.to(last))) {
                    ctx.fill(x, y, x + cell, y + cell, 0x66E8E04A);
                }
                if (sq == checkSq) ctx.fill(x, y, x + cell, y + cell, 0x99FF2020);
                if (sq == selected) ctx.fill(x, y, x + cell, y + cell, 0xAAF6F669);
            }
        }

        // pieces
        for (int c = 0; c < 8; c++) {
            for (int r = 0; r < 8; r++) {
                int piece = pos.b[sqAt(c, r)];
                if (piece != 0) drawPiece(ctx, piece, boardX + c * cell, boardY + r * cell);
            }
        }

        // legal move hints for the selected piece
        if (selected >= 0) {
            for (int i = 0; i < legalCount; i++) {
                int m = legal[i];
                if (Position.from(m) != selected) continue;
                int to = Position.to(m);
                int c = playerWhite ? (to & 7) : 7 - (to & 7);
                int rr = playerWhite ? 7 - (to >> 3) : (to >> 3);
                int x = boardX + c * cell, y = boardY + rr * cell;
                boolean capture = pos.b[to] != 0 || (Position.to(m) == pos.ep && Math.abs(pos.b[selected]) == Position.P);
                if (capture) {
                    int t = Math.max(2, cell / 10);
                    ctx.fill(x, y, x + cell, y + t, 0xCCD03030);
                    ctx.fill(x, y + cell - t, x + cell, y + cell, 0xCCD03030);
                    ctx.fill(x, y, x + t, y + cell, 0xCCD03030);
                    ctx.fill(x + cell - t, y, x + cell, y + cell, 0xCCD03030);
                } else {
                    int d = Math.max(4, cell / 4);
                    int cx = x + cell / 2, cy = y + cell / 2;
                    ctx.fill(cx - d / 2, cy - d / 2, cx + d / 2, cy + d / 2, 0xAA2E7D32);
                }
            }
        }

        // coordinates
        for (int i = 0; i < 8; i++) {
            int file = playerWhite ? i : 7 - i;
            String fl = String.valueOf((char) ('a' + file));
            ctx.drawText(this.textRenderer, fl, boardX + i * cell + cell / 2 - 2, boardY + boardSize + 5, 0xFFCCCCCC, true);
            int rank = playerWhite ? 7 - i : i;
            String rk = String.valueOf((char) ('1' + rank));
            ctx.drawText(this.textRenderer, rk, boardX - 11, boardY + i * cell + cell / 2 - 4, 0xFFCCCCCC, true);
        }

        // status text
        String[] lines = statusLines();
        ctx.drawText(this.textRenderer, lines[0], panelX, boardY, 0xFFFFFFFF, true);
        ctx.drawText(this.textRenderer, lines[1], panelX, boardY + 11, 0xFFFFD27F, true);

        // move list
        int listY = boardY + 26 + 96 + 28;
        int rows = Math.max(0, (boardY + boardSize - listY) / 10);
        int totalRows = (GAME.plies() + 1) / 2;
        int startRow = Math.max(0, totalRows - rows);
        for (int row = startRow; row < totalRows; row++) {
            StringBuilder sb = new StringBuilder();
            sb.append(row + 1).append(". ").append(Position.moveToString(GAME.moveAt(row * 2)));
            if (row * 2 + 1 < GAME.plies()) sb.append("  ").append(Position.moveToString(GAME.moveAt(row * 2 + 1)));
            ctx.drawText(this.textRenderer, sb.toString(), panelX, listY + (row - startRow) * 10, 0xFFBBBBBB, true);
        }
    }

    private String[] statusLines() {
        Game.Status st = GAME.status();
        Position p = GAME.pos();
        switch (st) {
            case CHECKMATE:
                boolean playerWon = p.white != playerWhite; // side to move is mated
                return new String[]{"Checkmate!", playerWon ? "You win" : "Computer wins"};
            case STALEMATE:
                return new String[]{"Draw", "Stalemate"};
            case DRAW_50:
                return new String[]{"Draw", "50-move rule"};
            case DRAW_MATERIAL:
                return new String[]{"Draw", "Not enough material"};
            case DRAW_REPEAT:
                return new String[]{"Draw", "Repetition"};
            default:
                break;
        }
        if (thinking || !playerToMove()) return new String[]{"Computer thinking...", ""};
        return new String[]{"Your move", p.inCheck() ? "Check!" : ""};
    }

    private void drawPiece(DrawContext ctx, int piece, int x, int y) {
        int type = Math.abs(piece);
        boolean w = piece > 0;
        String[] shape = SHAPES[type];
        int area = cell * 7 / 8;
        int ox = x + (cell - area) / 2, oy = y + (cell - area) / 2;
        int fill = w ? 0xFFFFFFFF : 0xFF1A1A1A;
        int outline = w ? 0xFF000000 : 0xFFD8D8D8;
        int o = Math.max(1, cell / 24);

        for (int pass = 0; pass < 2; pass++) {
            for (int ry = 0; ry < 8; ry++) {
                int y1 = oy + ry * area / 8, y2 = oy + (ry + 1) * area / 8;
                int rx = 0;
                while (rx < 8) {
                    if (shape[ry].charAt(rx) != '#') {
                        rx++;
                        continue;
                    }
                    int start = rx;
                    while (rx < 8 && shape[ry].charAt(rx) == '#') rx++;
                    int x1 = ox + start * area / 8, x2 = ox + rx * area / 8;
                    if (pass == 0) {
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dy = -1; dy <= 1; dy++) {
                                if (dx == 0 && dy == 0) continue;
                                ctx.fill(x1 + dx * o, y1 + dy * o, x2 + dx * o, y2 + dy * o, outline);
                            }
                        }
                    } else {
                        ctx.fill(x1, y1, x2, y2, fill);
                    }
                }
            }
        }
    }
}
