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

    private static final int SIZE = ReversyGame.SIZE;
    private Button[][] cells = new Button[SIZE][SIZE];
    private ReversyGame game;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(SIZE);
        grid.setRowCount(SIZE);

        game = new ReversyGame();

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
        game.initBoard();
    }

    // -------------------------
    // 人間の手
    // -------------------------
    private void onHumanMove(int x, int y) {
        if (game.getCurrentPlayer() != 1) return;

        if (!game.canPlace(x, y, 1)) {
            Toast.makeText(this, "そこには置けません", Toast.LENGTH_SHORT).show();
            return;
        }

        game.placeStone(x, y, 1);
        updateBoardUI();

        game.setCurrentPlayer(2);
        handleTurn();
    }

    // -------------------------
    // ターン処理（パス判定 → CPU）
    // -------------------------
    private void handleTurn() {
        // 次のプレイヤーが置けるか？
        if (!game.hasValidMove(game.getCurrentPlayer())) {
            int cp = game.getCurrentPlayer();
            Toast.makeText(this,
                    (cp == 1 ? "黒" : "白") + "は置ける場所がありません（パス）",
                    Toast.LENGTH_SHORT).show();

            game.setCurrentPlayer((cp == 1) ? 2 : 1);

            // 両者置けない → 終了
            if (!game.hasValidMove(game.getCurrentPlayer())) {
                showGameResult();
                return;
            }
        }

        // CPU の番なら CPU を動かす
        if (game.getCurrentPlayer() == 2) {
            cpuMove();
        }
    }

    // -------------------------
    // CPU の手（貪欲法）
    // -------------------------
    private void cpuMove() {
        int[] best = game.findBestMove(2);
        if (best != null) {
            game.placeStone(best[0], best[1], 2);
            Toast.makeText(this, "CPU が置きました", Toast.LENGTH_SHORT).show();
        }

        // currentPlayer を先に戻してから UI を更新する（重要）
        game.setCurrentPlayer(1);
        updateBoardUI();

        handleTurn();
    }

    // -------------------------
    // UI 更新（丸石）
    // -------------------------
    private void updateBoardUI() {
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                Button btn = cells[y][x];
                int v = game.getBoard()[y][x];

                if (v == 0) {
                    // 空セルは緑（ボード）
                    btn.setBackgroundColor(Color.parseColor("#006400")); // 濃い緑
                    btn.setEnabled(game.getCurrentPlayer() == 1 && game.canPlace(x, y, 1)); // 人間が置ける場所だけ有効に
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
        int[][] b = game.getBoard();
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (b[y][x] == 1) black++;
                else if (b[y][x] == 2) white++;
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
        game.setCurrentPlayer(0);
    }
}
