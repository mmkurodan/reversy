package com.example.reversy;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView turnView;

    private static final int SIZE = 8;
    private Button[][] cells = new Button[SIZE][SIZE];
    private int[][] board = new int[SIZE][SIZE]; // 0=空, 1=黒(人間), 2=白(CPU)
    private int currentPlayer = 1; // 黒スタート（人間）

    // game mode & difficulty
    // 1 = two-player, 2 = vs CPU
    private int gameMode = 2;
    private int simDepth = 1; // lookahead depth in plies

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Root layout
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // Controls row
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);

        turnView = new TextView(this);
        turnView.setText("ターン: 黒");
        controls.addView(turnView);

        Button modeBtn = new Button(this);
        modeBtn.setText(gameMode == 2 ? "対戦: CPU" : "対戦: 2人");
        modeBtn.setOnClickListener(v -> {
            gameMode = (gameMode == 2) ? 1 : 2;
            modeBtn.setText(gameMode == 2 ? "対戦: CPU" : "対戦: 2人");
            resetGame();
            Toast.makeText(this, gameMode == 2 ? "CPU対戦に切替" : "2人対戦に切替", Toast.LENGTH_SHORT).show();
        });

        Button depthBtn = new Button(this);
        depthBtn.setText("難易度: " + simDepth);
        depthBtn.setOnClickListener(v -> {
            simDepth = (simDepth % 10) + 1; // cycle 1..10
            depthBtn.setText("難易度: " + simDepth);
            Toast.makeText(this, "シミュレーション深さ: " + simDepth, Toast.LENGTH_SHORT).show();
        });

        Button resetBtn = new Button(this);
        resetBtn.setText("リセット");
        resetBtn.setOnClickListener(v -> {
            resetGame();
            Toast.makeText(this, "ゲームをリセットしました", Toast.LENGTH_SHORT).show();
        });

        controls.addView(modeBtn);
        controls.addView(depthBtn);
        controls.addView(resetBtn);

        root.addView(controls);

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
        root.addView(grid);
        setContentView(root);
    }

    private void initBoard() {
        // clear board
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                board[y][x] = 0;
            }
        }
        // initial positions
        board[3][3] = 2;
        board[4][4] = 2;
        board[3][4] = 1;
        board[4][3] = 1;
        currentPlayer = 1;
    }

    private void resetGame() {
        initBoard();
        updateBoardUI();
    }

    // -------------------------
    // 人間の手（2人 or CPU）
    // -------------------------
    private void onHumanMove(int x, int y) {
        if (gameMode == 2) {
            if (currentPlayer != 1) return; // only human (黒) moves
        } else {
            // two-player: both players can tap
        }

        int player = currentPlayer;

        if (!canPlace(x, y, player)) {
            Toast.makeText(this, "そこには置けません", Toast.LENGTH_SHORT).show();
            return;
        }

        placeStone(x, y, player);

        // switch player
        if (gameMode == 1) {
            currentPlayer = (currentPlayer == 1) ? 2 : 1;
            updateBoardUI();
            // pass/endgame handling
            if (!hasValidMove(currentPlayer)) {
                Toast.makeText(this,
                        (currentPlayer == 1 ? "黒" : "白") + "は置ける場所がありません（パス）",
                        Toast.LENGTH_SHORT).show();
                currentPlayer = (currentPlayer == 1) ? 2 : 1;
                updateBoardUI();
                if (!hasValidMove(currentPlayer)) {
                    showGameResult();
                    return;
                }
            }
        } else {
            // vs CPU
            currentPlayer = 2;
            handleTurn();
        }
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

        // CPU の番なら CPU を動かす（ただし CPU モードのみ）
        if (gameMode == 2 && currentPlayer == 2) {
            cpuMove();
        }
    }

    // -------------------------
    // CPU の手（深さ指定ミニマックス）
    // -------------------------
    private void cpuMove() {
        int[] best = findBestMoveSim(2, simDepth);
        if (best != null) {
            placeStone(best[0], best[1], 2);
            Toast.makeText(this, "CPU が置きました", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "CPU はパスしました", Toast.LENGTH_SHORT).show();
        }

        // currentPlayer を先に戻してから UI を更新する（重要）
        currentPlayer = 1;
        updateBoardUI();

        handleTurn();
    }

    // Find best move by simulating up to depth plies (minimax)
    private int[] findBestMoveSim(int player, int depth) {
        int bestX = -1, bestY = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (canPlace(x, y, player)) {
                    int[][] copy = cloneBoard(board);
                    placeStoneOnBoard(copy, x, y, player);
                    int score = minimax(copy, (player == 1) ? 2 : 1, depth - 1, player);
                    if (score > bestScore) {
                        bestScore = score;
                        bestX = x;
                        bestY = y;
                    }
                }
            }
        }
        if (bestX != -1) return new int[]{bestX, bestY};
        return null;
    }

    private int minimax(int[][] b, int player, int depth, int maximizingPlayer) {
        int opponent = (player == 1) ? 2 : 1;

        boolean playerHas = hasValidMoveOnBoard(b, player);
        boolean opponentHas = hasValidMoveOnBoard(b, opponent);

        if (depth <= 0 || (!playerHas && !opponentHas)) {
            return evaluateBoard(b, maximizingPlayer);
        }

        if (!playerHas) {
            // pass turn (consume a ply)
            return minimax(b, opponent, depth - 1, maximizingPlayer);
        }

        if (player == maximizingPlayer) {
            int maxVal = Integer.MIN_VALUE;
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    if (canPlaceOnBoard(b, x, y, player)) {
                        int[][] copy = cloneBoard(b);
                        placeStoneOnBoard(copy, x, y, player);
                        int val = minimax(copy, opponent, depth - 1, maximizingPlayer);
                        if (val > maxVal) maxVal = val;
                    }
                }
            }
            return maxVal;
        } else {
            int minVal = Integer.MAX_VALUE;
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    if (canPlaceOnBoard(b, x, y, player)) {
                        int[][] copy = cloneBoard(b);
                        placeStoneOnBoard(copy, x, y, player);
                        int val = minimax(copy, opponent, depth - 1, maximizingPlayer);
                        if (val < minVal) minVal = val;
                    }
                }
            }
            return minVal;
        }
    }

    private int evaluateBoard(int[][] b, int player) {
        int me = 0, opp = 0;
        int opponent = (player == 1) ? 2 : 1;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (b[y][x] == player) me++;
                else if (b[y][x] == opponent) opp++;
            }
        }
        return me - opp;
    }

    private int[][] cloneBoard(int[][] src) {
        int[][] d = new int[SIZE][SIZE];
        for (int y = 0; y < SIZE; y++) {
            System.arraycopy(src[y], 0, d[y], 0, SIZE);
        }
        return d;
    }

    private boolean hasValidMoveOnBoard(int[][] b, int player) {
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (canPlaceOnBoard(b, x, y, player)) return true;
            }
        }
        return false;
    }

    private boolean canPlaceOnBoard(int[][] b, int x, int y, int player) {
        if (b[y][x] != 0) return false;

        int opponent = (player == 1) ? 2 : 1;

        int[] dx = {-1,0,1,-1,1,-1,0,1};
        int[] dy = {-1,-1,-1,0,0,1,1,1};

        for (int d = 0; d < 8; d++) {
            int cx = x + dx[d];
            int cy = y + dy[d];
            boolean foundOpponent = false;

            while (cx >= 0 && cx < SIZE && cy >= 0 && cy < SIZE) {
                if (b[cy][cx] == opponent) {
                    foundOpponent = true;
                } else if (b[cy][cx] == player) {
                    if (foundOpponent) return true;
                    break;
                } else break;

                cx += dx[d];
                cy += dy[d];
            }
        }
        return false;
    }

    private int countFlipsOnBoard(int[][] b, int x, int y, int player) {
        if (b[y][x] != 0) return 0;

        int opponent = (player == 1) ? 2 : 1;
        int total = 0;

        int[] dx = {-1,0,1,-1,1,-1,0,1};
        int[] dy = {-1,-1,-1,0,0,1,1,1};

        for (int d = 0; d < 8; d++) {
            int cx = x + dx[d];
            int cy = y + dy[d];
            int count = 0;

            while (cx >= 0 && cx < SIZE && cy >= 0 && cy < SIZE) {
                if (b[cy][cx] == opponent) {
                    count++;
                } else if (b[cy][cx] == player) {
                    total += count;
                    break;
                } else break;

                cx += dx[d];
                cy += dy[d];
            }
        }
        return total;
    }

    private void placeStoneOnBoard(int[][] b, int x, int y, int player) {
        b[y][x] = player;
        int opponent = (player == 1) ? 2 : 1;

        int[] dx = {-1,0,1,-1,1,-1,0,1};
        int[] dy = {-1,-1,-1,0,0,1,1,1};

        for (int d = 0; d < 8; d++) {
            int cx = x + dx[d];
            int cy = y + dy[d];
            boolean foundOpponent = false;

            while (cx >= 0 && cx < SIZE && cy >= 0 && cy < SIZE) {
                if (b[cy][cx] == opponent) {
                    foundOpponent = true;
                } else if (b[cy][cx] == player) {
                    if (foundOpponent) {
                        int rx = x + dx[d];
                        int ry = y + dy[d];
                        while (b[ry][rx] == opponent) {
                            b[ry][rx] = player;
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
    // 既存の board 操作
    // -------------------------
    private boolean hasValidMove(int player) {
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (canPlace(x, y, player)) return true;
            }
        }
        return false;
    }

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

    private void updateBoardUI() {
        // ターン表示を更新
        if (turnView != null) {
            if (currentPlayer == 1) {
                turnView.setText("ターン: 黒");
            } else if (currentPlayer == 2) {
                turnView.setText("ターン: 白");
            } else {
                turnView.setText("ゲーム終了");
            }
        }
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                Button btn = cells[y][x];
                int v = board[y][x];

                if (v == 0) {
                    // 空セルは緑（ボード）
                    btn.setBackgroundColor(Color.parseColor("#006400")); // 濃い緑
                    btn.setEnabled((gameMode == 1) ? canPlace(x, y, currentPlayer) : (currentPlayer == 1 && canPlace(x, y, 1))); // 2人対戦時はcurrentPlayerで判定
// 修正: 2人対戦時はcurrentPlayerで判定し、白番でも置けるようにする
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
