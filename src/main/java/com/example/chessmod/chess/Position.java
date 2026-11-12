package com.example.chessmod.chess;

import java.nio.charset.StandardCharsets;

/**
 * A chess position with full rules: castling, en passant, promotion.
 * Squares are indexed 0..63 as rank * 8 + file (a1 = 0, h1 = 7, a8 = 56, h8 = 63).
 * Pieces: positive = white, negative = black. 1=P 2=N 3=B 4=R 5=Q 6=K.
 * A move is an int: from | to << 6 | promotionPiece << 12.
 */
public final class Position {
    public static final int P = 1, N = 2, B = 3, R = 4, Q = 5, K = 6;
    public static final int WK = 1, WQ = 2, BK = 4, BQ = 8;
    private static final int[] PROMOS = {Q, R, B, N};

    private static final int[][] KNIGHT = {{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}};
    private static final int[][] KING = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
    private static final int[][] DIAG = {{1, 1}, {-1, 1}, {-1, -1}, {1, -1}};
    private static final int[][] ORTHO = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public final byte[] b = new byte[64];
    public boolean white = true;
    public int castling = 15;
    public int ep = -1;
    public int half = 0;
    public int full = 1;

    public static Position start() {
        Position p = new Position();
        int[] back = {R, N, B, Q, K, B, N, R};
        for (int f = 0; f < 8; f++) {
            p.b[f] = (byte) back[f];
            p.b[8 + f] = P;
            p.b[48 + f] = -P;
            p.b[56 + f] = (byte) -back[f];
        }
        return p;
    }

    public Position copy() {
        Position p = new Position();
        System.arraycopy(b, 0, p.b, 0, 64);
        p.white = white;
        p.castling = castling;
        p.ep = ep;
        p.half = half;
        p.full = full;
        return p;
    }

    // ---- move encoding helpers ----
    public static int from(int m) { return m & 63; }
    public static int to(int m) { return (m >> 6) & 63; }
    public static int promo(int m) { return (m >> 12) & 7; }

    public static String squareName(int sq) {
        return "" + (char) ('a' + (sq & 7)) + (char) ('1' + (sq >> 3));
    }

    public static String moveToString(int m) {
        String s = squareName(from(m)) + squareName(to(m));
        int pr = promo(m);
        if (pr != 0) s += "??nbrq".charAt(pr);
        return s;
    }

    // ---- attacks ----
    public int kingSquare(boolean w) {
        int k = w ? K : -K;
        for (int i = 0; i < 64; i++) if (b[i] == k) return i;
        return -1;
    }

    public boolean isAttacked(int sq, boolean byWhite) {
        int f = sq & 7, r = sq >> 3;
        int s = byWhite ? 1 : -1;
        int pr = byWhite ? r - 1 : r + 1;
        if (pr >= 0 && pr < 8) {
            if (f > 0 && b[pr * 8 + f - 1] == s * P) return true;
            if (f < 7 && b[pr * 8 + f + 1] == s * P) return true;
        }
        for (int[] d : KNIGHT) {
            int nf = f + d[0], nr = r + d[1];
            if (nf >= 0 && nf < 8 && nr >= 0 && nr < 8 && b[nr * 8 + nf] == s * N) return true;
        }
        for (int[] d : KING) {
            int nf = f + d[0], nr = r + d[1];
            if (nf >= 0 && nf < 8 && nr >= 0 && nr < 8 && b[nr * 8 + nf] == s * K) return true;
        }
        for (int[] d : DIAG) {
            int nf = f + d[0], nr = r + d[1];
            while (nf >= 0 && nf < 8 && nr >= 0 && nr < 8) {
                int t = b[nr * 8 + nf];
                if (t != 0) {
                    if (t == s * B || t == s * Q) return true;
                    break;
                }
                nf += d[0];
                nr += d[1];
            }
        }
        for (int[] d : ORTHO) {
            int nf = f + d[0], nr = r + d[1];
            while (nf >= 0 && nf < 8 && nr >= 0 && nr < 8) {
                int t = b[nr * 8 + nf];
                if (t != 0) {
                    if (t == s * R || t == s * Q) return true;
                    break;
                }
                nf += d[0];
                nr += d[1];
            }
        }
        return false;
    }

    public boolean inCheck() {
        return isAttacked(kingSquare(white), !white);
    }

    // ---- move generation ----
    private static int add(int[] out, int n, int from, int to) {
        out[n] = from | (to << 6);
        return n + 1;
    }

    private static int addPromo(int[] out, int n, int from, int to) {
        for (int pr : PROMOS) out[n++] = from | (to << 6) | (pr << 12);
        return n;
    }

    /** Pseudo-legal moves (may leave own king in check). Returns count. */
    public int genMoves(int[] out, boolean capturesOnly) {
        int n = 0;
        for (int sq = 0; sq < 64; sq++) {
            int pc = b[sq];
            if (pc == 0 || (pc > 0) != white) continue;
            int type = Math.abs(pc);
            int f = sq & 7, r = sq >> 3;
            switch (type) {
                case P: {
                    int dir = white ? 1 : -1;
                    int tr = r + dir;
                    if (tr < 0 || tr > 7) break;
                    boolean promo = tr == (white ? 7 : 0);
                    int fwd = tr * 8 + f;
                    if (b[fwd] == 0) {
                        if (promo) {
                            n = addPromo(out, n, sq, fwd);
                        } else if (!capturesOnly) {
                            n = add(out, n, sq, fwd);
                            if (r == (white ? 1 : 6) && b[fwd + dir * 8] == 0) n = add(out, n, sq, fwd + dir * 8);
                        }
                    }
                    for (int df = -1; df <= 1; df += 2) {
                        int tf = f + df;
                        if (tf < 0 || tf > 7) continue;
                        int to = tr * 8 + tf;
                        int t = b[to];
                        if (t != 0 && (t > 0) != white) {
                            n = promo ? addPromo(out, n, sq, to) : add(out, n, sq, to);
                        } else if (t == 0 && to == ep) {
                            n = add(out, n, sq, to);
                        }
                    }
                    break;
                }
                case N:
                case K: {
                    int[][] ds = type == N ? KNIGHT : KING;
                    for (int[] d : ds) {
                        int tf = f + d[0], tr = r + d[1];
                        if (tf < 0 || tf > 7 || tr < 0 || tr > 7) continue;
                        int to = tr * 8 + tf;
                        int t = b[to];
                        if (t != 0 && (t > 0) == white) continue;
                        if (capturesOnly && t == 0) continue;
                        n = add(out, n, sq, to);
                    }
                    if (type == K && !capturesOnly) n = genCastling(out, n, sq);
                    break;
                }
                default: {
                    if (type == B || type == Q) n = slide(out, n, sq, DIAG, capturesOnly);
                    if (type == R || type == Q) n = slide(out, n, sq, ORTHO, capturesOnly);
                    break;
                }
            }
        }
        return n;
    }

    private int slide(int[] out, int n, int sq, int[][] dirs, boolean capturesOnly) {
        int f = sq & 7, r = sq >> 3;
        for (int[] d : dirs) {
            int tf = f + d[0], tr = r + d[1];
            while (tf >= 0 && tf < 8 && tr >= 0 && tr < 8) {
                int to = tr * 8 + tf;
                int t = b[to];
                if (t == 0) {
                    if (!capturesOnly) n = add(out, n, sq, to);
                } else {
                    if ((t > 0) != white) n = add(out, n, sq, to);
                    break;
                }
                tf += d[0];
                tr += d[1];
            }
        }
        return n;
    }

    private int genCastling(int[] out, int n, int sq) {
        if (white) {
            if (sq != 4 || isAttacked(4, false)) return n;
            if ((castling & WK) != 0 && b[5] == 0 && b[6] == 0 && b[7] == R && !isAttacked(5, false)) n = add(out, n, 4, 6);
            if ((castling & WQ) != 0 && b[1] == 0 && b[2] == 0 && b[3] == 0 && b[0] == R && !isAttacked(3, false)) n = add(out, n, 4, 2);
        } else {
            if (sq != 60 || isAttacked(60, true)) return n;
            if ((castling & BK) != 0 && b[61] == 0 && b[62] == 0 && b[63] == -R && !isAttacked(61, true)) n = add(out, n, 60, 62);
            if ((castling & BQ) != 0 && b[57] == 0 && b[58] == 0 && b[59] == 0 && b[56] == -R && !isAttacked(59, true)) n = add(out, n, 60, 58);
        }
        return n;
    }

    /** Fully legal moves. Returns count; `out` needs room for 256. */
    public int legalMoves(int[] out) {
        int[] tmp = new int[256];
        int n = genMoves(tmp, false);
        int c = 0;
        for (int i = 0; i < n; i++) {
            Position p = make(tmp[i]);
            if (!p.isAttacked(p.kingSquare(white), !white)) out[c++] = tmp[i];
        }
        return c;
    }

    /** Returns a new position with the move played (copy-make). */
    public Position make(int m) {
        Position p = copy();
        int from = from(m), to = to(m), promo = promo(m);
        int piece = p.b[from];
        int type = Math.abs(piece);
        int captured = p.b[to];
        p.b[from] = 0;
        p.b[to] = (byte) piece;
        if (type == P && to == ep && captured == 0 && (from & 7) != (to & 7)) {
            p.b[white ? to - 8 : to + 8] = 0;
            captured = 1;
        }
        if (promo != 0) p.b[to] = (byte) (white ? promo : -promo);
        if (type == K && Math.abs(to - from) == 2) {
            if (to > from) {
                p.b[to - 1] = p.b[to + 1];
                p.b[to + 1] = 0;
            } else {
                p.b[to + 1] = p.b[to - 2];
                p.b[to - 2] = 0;
            }
        }
        if (type == K) p.castling &= white ? ~3 : ~12;
        if (from == 0 || to == 0) p.castling &= ~WQ;
        if (from == 7 || to == 7) p.castling &= ~WK;
        if (from == 56 || to == 56) p.castling &= ~BQ;
        if (from == 63 || to == 63) p.castling &= ~BK;
        p.ep = (type == P && Math.abs(to - from) == 16) ? (from + to) / 2 : -1;
        p.half = (type == P || captured != 0) ? 0 : half + 1;
        if (!white) p.full = full + 1;
        p.white = !white;
        return p;
    }

    public boolean insufficientMaterial() {
        int minors = 0;
        for (int i = 0; i < 64; i++) {
            int t = Math.abs(b[i]);
            if (t == P || t == R || t == Q) return false;
            if (t == N || t == B) minors++;
        }
        return minors <= 1;
    }

    /** Key identifying the position for repetition detection. */
    public String key() {
        return new String(b, StandardCharsets.ISO_8859_1) + (white ? 'w' : 'b') + castling + ':' + ep;
    }
}
