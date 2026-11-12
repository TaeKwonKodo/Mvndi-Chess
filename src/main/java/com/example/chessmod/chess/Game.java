package com.example.chessmod.chess;

import java.util.ArrayList;
import java.util.List;

/** A game in progress: position history, move list, undo and result detection. */
public final class Game {
    public enum Status { PLAYING, CHECKMATE, STALEMATE, DRAW_50, DRAW_MATERIAL, DRAW_REPEAT }

    private final List<Position> history = new ArrayList<>();
    private final List<String> keys = new ArrayList<>();
    private final List<Integer> moves = new ArrayList<>();

    public Game() {
        reset();
    }

    public void reset() {
        history.clear();
        keys.clear();
        moves.clear();
        Position s = Position.start();
        history.add(s);
        keys.add(s.key());
    }

    public Position pos() {
        return history.get(history.size() - 1);
    }

    public int plies() {
        return moves.size();
    }

    /** The last move played, or -1 if none. */
    public int lastMove() {
        return moves.isEmpty() ? -1 : moves.get(moves.size() - 1);
    }

    public int moveAt(int i) {
        return moves.get(i);
    }

    public void play(int move) {
        Position p = pos().make(move);
        history.add(p);
        keys.add(p.key());
        moves.add(move);
    }

    /** Takes back up to {@code plies} half-moves. */
    public void undo(int plies) {
        while (plies-- > 0 && history.size() > 1) {
            history.remove(history.size() - 1);
            keys.remove(keys.size() - 1);
            moves.remove(moves.size() - 1);
        }
    }

    public Status status() {
        Position p = pos();
        int[] buf = new int[256];
        if (p.legalMoves(buf) == 0) return p.inCheck() ? Status.CHECKMATE : Status.STALEMATE;
        if (p.insufficientMaterial()) return Status.DRAW_MATERIAL;
        if (p.half >= 100) return Status.DRAW_50;
        String k = keys.get(keys.size() - 1);
        int count = 0;
        for (String s : keys) if (s.equals(k)) count++;
        if (count >= 3) return Status.DRAW_REPEAT;
        return Status.PLAYING;
    }
}
