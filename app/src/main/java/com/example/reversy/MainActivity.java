package com.example.reversy;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int SIZE = 8;
    private Button[][] cells = new Button[SIZE][SIZE];
    private int[][] board = new int[SIZE][SIZE]; // 0=空, 1=黒(人間), 2=白(CPU)
    private int currentPlayer = 1; // 黒スタート（人間）

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(SIZE);
        grid.setRowCount(SIZE);

        initBoard();

        // 画面幅をセルサイズに使い、縦横の長さを揃える（正方形マス）
        int cellSize = getResources().getDisplayMetrics().widthPixels / SIZE;

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                Button btn = new Button(this);
                btn.setMinHeight(0);
                btn.setMinimumHeight(0);
                btn.setPadding(0, 0, 0, 0);

                GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                        GridLayout.spec(y), GridLayout.spec(x));
                params.width = cellSize;
                params.height = cellSize;
                params.setMargins(1, 1, 1, 1);
                btn.setLayoutParams(params);

                final int fx = x;
                final int fy = y;

                btn.setOnClickListener(v -> onHumanMove(fx, fy));

                cells[y][x] = btn;
                grid.addView(btn);
            }
        }

        updateBoardUI();
        setContentView(grid);
    }

    private void initBoard() {
        // 初期配置
        board[3][3] = 2;
        board[4][4] = 2;
        board[3][4] = 1;
        board[4][3] = 1;
    }

    // -------------------------
    // 人間の手
    // -------------------------
    private void onHumanMove(int x, int y) {
        if (currentPlayer != 1) return;

        if (!canPlace(x, y, 1)) {
            Toast.makeText(this, "そこには置けません", Toast.LENGTH_SHORT).show();
            return;
        }

        placeStone(x, y, 1);
        updateBoardUI();

        currentPlayer = 2;
        handleTurn();
    }

    // -------------------------
    // ターン処理（パス判定 → CPU）
    // -------------------------
    private void handleTurn() {
        // 次のプレイヤーが置けるか？
        if (!hasValidMove(currentPlayer)) {
            Toast.makeText(this,
                    (currentPlayer == 1 ? "黒" : "白") + "は置ける場所がありません（パス）",
                    Toast.LENGTH_SHORT).show();

            currentPlayer = (currentPlayer == 1) ? 2 : 1;

            // 両者置けない → 終了
            if (!hasValidMove(currentPlayer)) {
                showGameResult();
                return;
            }
        }

        // CPU の番なら CPU を動かす
        if (currentPlayer == 2) {
            cpuMove();
        }
    }

    // -------------------------
    // CPU の手（貪欲法）
    // -------------------------
    private void cpuMove() {
        int bestX = -1, bestY = -1;
        int bestScore = -1;

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (canPlace(x, y, 2)) {
                    int score = countFlips(x, y, 2);
                    if (score > bestScore) {
                        bestScore = score;
                        bestX = x;
                        bestY = y;
                    }
                }
            }
        }

        if (bestX != -1) {
            placeStone(bestX, bestY, 2);
            Toast.makeText(this, "CPU が置きました", Toast.LENGTH_SHORT).show();
        }

        // currentPlayer を先に戻してから UI を更新する（重要）
        currentPlayer = 1;
        updateBoardUI();

        handleTurn();
    }

    // -------------------------
    // 置ける場所があるか？
    // -------------------------
    private boolean hasValidMove(int player) {
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (canPlace(x, y, player)) return true;
            }
        }
        return false;
    }

    // -------------------------
    // 反転できる枚数を数える（CPU 用）
    // -------------------------
    private int countFlips(int x, int y, int player) {
        if (board[y][x] != 0) return 0;

        int opponent = (player == 1) ? 2 : 1;
        int total = 0;

        int[] dx = {-1,0,1,-1,1,-1,0,1};
        int[] dy = {-1,-1,-1,0,0,1,1,1};

        for (int d = 0; d < 8; d++) {
            int cx = x + dx[d];
            int cy = y + dy[d];
            int count = 0;

            while (cx >= 0 && cx < SIZE && cy >= 0 && cy < SIZE) {
                if (board[cy][cx] == opponent) {
                    count++;
                } else if (board[cy][cx] == player) {
                    total += count;
                    break;
                } else break;

                cx += dx[d];
                cy += dy[d];
            }
        }
        return total;
    }

    // -------------------------
    // 置けるか判定
    // -------------------------
    private boolean canPlace(int x, int y, int player) {
        if (board[y][x] != 0) return false;

        int opponent = (player == 1) ? 2 : 1;

        int[] dx = {-1,0,1,-1,1,-1,0,1};
        int[] dy = {-1,-1,-1,0,0,1,1,1};

        for (int d = 0; d < 8; d++) {
            int cx = x + dx[d];
            int cy = y + dy[d];
            boolean foundOpponent = false;

            while (cx >= 0 && cx < SIZE && cy >= 0 && cy < SIZE) {
                if (board[cy][cx] == opponent) {
                    foundOpponent = true;
                } else if (board[cy][cx] == player) {
                    if (foundOpponent) return true;
                    break;
                } else break;

                cx += dx[d];
                cy += dy[d];
            }
        }
        return false;
    }

    // -------------------------
    // 石を置いて反転
    // -------------------------
    private void placeStone(int x, int y, int player) {
        board[y][x] = player;
        int opponent = (player == 1) ? 2 : 1;

        int[] dx = {-1,0,1,-1,1,-1,0,1};
        int[] dy = {-1,-1,-1,0,0,1,1,1};

        for (int d = 0; d < 8; d++) {
            int cx = x + dx[d];
            int cy = y + dy[d];
            boolean foundOpponent = false;

            while (cx >= 0 && cx < SIZE && cy >= 0 && cy < SIZE) {
                if (board[cy][cx] == opponent) {
                    foundOpponent = true;
                } else if (board[cy][cx] == player) {
                    if (foundOpponent) {
                        int rx = x + dx[d];
                        int ry = y + dy[d];
                        while (board[ry][rx] == opponent) {
                            board[ry][rx] = player;
                            rx += dx[d];
                            ry += dy[d];
                        }
                    }
                    break;
                } else break;

                cx += dx[d];
                cy += dy[d];
            }
        }
    }

    // -------------------------
    // UI 更新（丸石）
    // -------------------------
    private void updateBoardUI() {
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                Button btn = cells[y][x];
                int v = board[y][x];

                if (v == 0) {
                    // 空セルは緑（ボード）
                    btn.setBackgroundColor(Color.parseColor("#006400")); // 濃い緑
                    btn.setEnabled(currentPlayer == 1 && canPlace(x, y, 1)); // 人間が置ける場所だけ有効に
                } else {
                    GradientDrawable circle = new GradientDrawable();
                    circle.setShape(GradientDrawable.OVAL);
                    if (v == 1) {
                        circle.setColor(Color.BLACK);
                        circle.setStroke(2, Color.BLACK);
                    } else { // 白
                        circle.setColor(Color.WHITE);
                        circle.setStroke(2, Color.BLACK); // 枠をつけて見えるようにする
                    }
                    btn.setBackground(circle);
                    btn.setEnabled(false);
                }
            }
        }
    }

    // -------------------------
    // ゲーム終了時の勝敗判定と表示
    // -------------------------
    private void showGameResult() {
        int black = 0;
        int white = 0;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (board[y][x] == 1) black++;
                else if (board[y][x] == 2) white++;
            }
        }

        String winner;
        if (black > white) {
            winner = "黒の勝ち！";
        } else if (white > black) {
            winner = "白の勝ち！";
        } else {
            winner = "引き分け";
        }

        String msg = "ゲーム終了: 黒 " + black + " - 白 " + white + " → " + winner;
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();

        // 全ボタンを無効化して入力を止める
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (cells[y][x] != null) cells[y][x].setEnabled(false);
            }
        }

        // currentPlayer を 0 にしてゲーム終了状態を示す
        currentPlayer = 0;
    }
}
