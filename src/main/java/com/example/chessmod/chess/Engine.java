package com.example.chessmod.chess;

import java.util.Random;

/** Small alpha-beta engine: iterative deepening, quiescence, piece-square tables. */
public final class Engine {
    private static final int MATE = 100000;
    private static final int INF = 1000000;
    private static final int[] VALUE = {0, 100, 320, 330, 500, 900, 0};

    // Piece-square tables, written from White's point of view with rank 8 on the top row.
    private static final int[] PAWN = {
            0, 0, 0, 0, 0, 0, 0, 0,
            50, 50, 50, 50, 50, 50, 50, 50,
            10, 10, 20, 30, 30, 20, 10, 10,
            5, 5, 10, 25, 25, 10, 5, 5,
            0, 0, 0, 20, 20, 0, 0, 0,
            5, -5, -10, 0, 0, -10, -5, 5,
            5, 10, 10, -20, -20, 10, 10, 5,
            0, 0, 0, 0, 0, 0, 0, 0};
    private static final int[] KNIGHT = {
            -50, -40, -30, -30, -30, -30, -40, -50,
            -40, -20, 0, 0, 0, 0, -20, -40,
            -30, 0, 10, 15, 15, 10, 0, -30,
            -30, 5, 15, 20, 20, 15, 5, -30,
            -30, 0, 15, 20, 20, 15, 0, -30,
            -30, 5, 10, 15, 15, 10, 5, -30,
            -40, -20, 0, 5, 5, 0, -20, -40,
            -50, -40, -30, -30, -30, -30, -40, -50};
    private static final int[] BISHOP = {
            -20, -10, -10, -10, -10, -10, -10, -20,
            -10, 0, 0, 0, 0, 0, 0, -10,
            -10, 0, 5, 10, 10, 5, 0, -10,
            -10, 5, 5, 10, 10, 5, 5, -10,
            -10, 0, 10, 10, 10, 10, 0, -10,
            -10, 10, 10, 10, 10, 10, 10, -10,
            -10, 5, 0, 0, 0, 0, 5, -10,
            -20, -10, -10, -10, -10, -10, -10, -20};
    private static final int[] ROOK = {
            0, 0, 0, 0, 0, 0, 0, 0,
            5, 10, 10, 10, 10, 10, 10, 5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            0, 0, 0, 5, 5, 0, 0, 0};
    private static final int[] QUEEN = {
            -20, -10, -10, -5, -5, -10, -10, -20,
            -10, 0, 0, 0, 0, 0, 0, -10,
            -10, 0, 5, 5, 5, 5, 0, -10,
            -5, 0, 5, 5, 5, 5, 0, -5,
            0, 0, 5, 5, 5, 5, 0, -5,
            -10, 5, 5, 5, 5, 5, 0, -10,
            -10, 0, 5, 0, 0, 0, 0, -10,
            -20, -10, -10, -5, -5, -10, -10, -20};
    private static final int[] KINGT = {
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -20, -30, -30, -40, -40, -30, -30, -20,
            -10, -20, -20, -20, -20, -20, -20, -10,
            20, 20, 0, 0, 0, 0, 20, 20,
            20, 30, 10, 0, 0, 10, 30, 20};
    private static final int[][] PST = {null, PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KINGT};

    private final Random rng = new Random();
    private long deadline;
    private boolean aborted;
    private long nodes;

    /**
     * Picks a move.
     *
     * @param maxDepth search depth in plies
     * @param noise    random score added to root moves (weaker, more varied play); 0 = best move
     * @param timeMs   time budget; deeper iterations are dropped if they run out of time
     * @return an encoded move, or -1 if there are no legal moves
     */
    public int pickMove(Position root, int maxDepth, int noise, long timeMs) {
        int[] moves = new int[256];
        int n = root.legalMoves(moves);
        if (n == 0) return -1;
        if (n == 1) return moves[0];
        deadline = System.nanoTime() + timeMs * 1_000_000L;
        aborted = false;
        nodes = 0;
        order(root, moves, n);
        int best = moves[0];
        for (int depth = 1; depth <= maxDepth; depth++) {
            int bestScore = -INF;
            int iterBest = -1;
            int alpha = -INF;
            for (int i = 0; i < n; i++) {
                Position child = root.make(moves[i]);
                int a = noise > 0 ? -INF : alpha;
                int score = -negamax(child, depth - 1, -INF, -a, 1);
                if (aborted) break;
                if (noise > 0) score += rng.nextInt(noise + 1);
                if (score > bestScore) {
                    bestScore = score;
                    iterBest = moves[i];
                }
                if (noise == 0 && score > alpha) alpha = score;
            }
            if (aborted || iterBest < 0) break;
            best = iterBest;
            // search the best move first on the next iteration
            for (int i = 0; i < n; i++) {
                if (moves[i] == iterBest) {
                    System.arraycopy(moves, 0, moves, 1, i);
                    moves[0] = iterBest;
                    break;
                }
            }
        }
        return best;
    }

    private int negamax(Position p, int depth, int alpha, int beta, int ply) {
        if ((++nodes & 1023) == 0 && System.nanoTime() > deadline) aborted = true;
        if (aborted) return 0;
        if (p.half >= 100 || p.insufficientMaterial()) return 0;
        if (depth <= 0) return quiesce(p, alpha, beta, 0);
        int[] moves = new int[256];
        int n = p.legalMoves(moves);
        if (n == 0) return p.inCheck() ? -MATE + ply : 0;
        order(p, moves, n);
        for (int i = 0; i < n; i++) {
            Position c = p.make(moves[i]);
            int s = -negamax(c, depth - 1, -beta, -alpha, ply + 1);
            if (aborted) return 0;
            if (s >= beta) return beta;
            if (s > alpha) alpha = s;
        }
        return alpha;
    }

    private int quiesce(Position p, int alpha, int beta, int qd) {
        int stand = evaluate(p);
        if (stand >= beta) return beta;
        if (stand > alpha) alpha = stand;
        if (qd >= 6) return alpha;
        int[] moves = new int[256];
        int n = p.genMoves(moves, true);
        order(p, moves, n);
        for (int i = 0; i < n; i++) {
            Position c = p.make(moves[i]);
            if (c.isAttacked(c.kingSquare(p.white), !p.white)) continue;
            int s = -quiesce(c, -beta, -alpha, qd + 1);
            if (s >= beta) return beta;
            if (s > alpha) alpha = s;
        }
        return alpha;
    }

    /** Static evaluation from the side to move's point of view. */
    private int evaluate(Position p) {
        int score = 0;
        for (int sq = 0; sq < 64; sq++) {
            int pc = p.b[sq];
            if (pc == 0) continue;
            int type = Math.abs(pc);
            boolean w = pc > 0;
            int r = sq >> 3, f = sq & 7;
            int idx = w ? (7 - r) * 8 + f : r * 8 + f;
            int v = VALUE[type] + PST[type][idx];
            score += w ? v : -v;
        }
        return p.white ? score : -score;
    }

    /** Orders moves: captures (most valuable victim first), then promotions. */
    private void order(Position p, int[] moves, int n) {
        int[] sc = new int[n];
        for (int i = 0; i < n; i++) {
            int m = moves[i];
            int victim = Math.abs(p.b[Position.to(m)]);
            int attacker = Math.abs(p.b[Position.from(m)]);
            int s = 0;
            if (victim != 0) s = 1000 + 10 * VALUE[victim] - VALUE[attacker];
            int pr = Position.promo(m);
            if (pr != 0) s += VALUE[pr];
            sc[i] = s;
        }
        for (int i = 1; i < n; i++) {
            int m = moves[i], s = sc[i], j = i - 1;
            while (j >= 0 && sc[j] < s) {
                moves[j + 1] = moves[j];
                sc[j + 1] = sc[j];
                j--;
            }
            moves[j + 1] = m;
            sc[j + 1] = s;
        }
    }
}
