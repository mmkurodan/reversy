package com.example.reversy;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Toast;

import android.widget.TextView;
import android.view.ViewGroup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private TextView turnView;
    private Button aiConfigBtn;
    private Button depthBtn;

    private static final int SIZE = 8;
    private static final int MODE_TWO_PLAYER = 1;
    private static final int MODE_CPU = 2;
    private static final int MODE_AI = 3;
    private static final String DEFAULT_AI_BASE_URL = "http://127.0.0.1:11434";
    private static final String DEFAULT_AI_PROMPT =
            "あなたはオセロ（リバーシ）の指し手選択AIとして動作する。\n\n" +
            "【役割】\n" +
            "- 私が渡す盤面（8行、●=黒、○=白、・=空き）と、合法手候補の一覧を受け取り、\n" +
            "  白（○）として最善と思う手を1つだけ選ぶ。\n\n" +
            "【回答形式】\n" +
            "- 1行目に、選んだ座標を1つだけ書く。\n" +
            "- 2行目に、その選択理由を日本語で簡潔に書く。\n" +
            "- 座標は A1〜H8 の形式にする。\n" +
            "- 余計な前置き、箇条書き、盤面の再表示は書かない。\n\n" +
            "【禁止事項】\n" +
            "- 合法手候補に含まれない座標を出力してはならない。\n" +
            "- 例示された座標を模倣して出力してはならない（候補に無い場合）。\n" +
            "- 2行構成を崩してはならない。\n\n" +
            "【手順】\n" +
            "- まず盤面と合法手候補を受け取る。\n" +
            "- 内部で自由に推論してよいが、最終出力は指定の2行のみ。\n" +
            "- 盤面を受け取るまで何も出力しない。";
    private static final String AI_RESPONSE_FORMAT_PROMPT =
            "\n\n【回答形式の再確認】\n" +
            "- 1行目に選んだ座標を1つだけ書く。\n" +
            "- 2行目にその選択理由を書く。\n" +
            "- 座標は A1〜H8 の形式にする。\n" +
            "- 余計な説明は書かない。";
    private static final Pattern AI_MOVE_PATTERN = Pattern.compile(
            "(?<![A-Z0-9])([A-H][1-8])(?![A-Z0-9])",
            Pattern.CASE_INSENSITIVE);
    private static final int APP_BACKGROUND_COLOR = 0xFF000000;
    private static final int APP_TEXT_COLOR = 0xFF00FF00;
    private static final int APP_MUTED_TEXT_COLOR = 0xFF66FF66;
    private static final int APP_BORDER_COLOR = 0xFF00AA00;
    private static final int BOARD_EMPTY_COLOR = 0xFF006400;
    private Button[][] cells = new Button[SIZE][SIZE];
    private int[][] board = new int[SIZE][SIZE]; // 0=空, 1=黒(人間), 2=白(対戦相手)
    private int currentPlayer = 1; // 黒スタート（人間）

    // game mode & difficulty:
    // 1 = two-player, 2 = vs CPU, 3 = vs AI(Ollama互換API)
    private int gameMode = MODE_CPU;
    private int simDepth = 1; // lookahead depth in plies
    private String aiBaseUrl = DEFAULT_AI_BASE_URL;
    private String aiModel = "default";
    private String aiPrompt = DEFAULT_AI_PROMPT;
    private static final int MAX_AI_LOG_ENTRIES = 25;
    private final List<AiLogEntry> aiLogs = new ArrayList<>();
    private final List<String> aiCommentHistory = new ArrayList<>();
    private LinearLayout aiCommentContainer;
    private EditText aiCommentView;

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable createDarkOutlineBackground(int strokeWidthDp, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(APP_BACKGROUND_COLOR);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(strokeWidthDp), APP_BORDER_COLOR);
        return drawable;
    }

    private void applyDarkSurface(View view) {
        if (view != null) {
            view.setBackgroundColor(APP_BACKGROUND_COLOR);
        }
    }

    private void applyDarkLabel(TextView view) {
        if (view == null) return;
        view.setTextColor(APP_TEXT_COLOR);
        view.setBackgroundColor(APP_BACKGROUND_COLOR);
    }

    private void applyDarkButton(Button button) {
        if (button == null) return;
        button.setTextColor(APP_TEXT_COLOR);
        button.setBackground(createDarkOutlineBackground(1, 8f));
    }

    private void applyDarkInput(EditText input) {
        if (input == null) return;
        input.setTextColor(APP_TEXT_COLOR);
        input.setHintTextColor(APP_MUTED_TEXT_COLOR);
        input.setBackground(createDarkOutlineBackground(1, 4f));
        input.setPadding(dp(10), dp(10), dp(10), dp(10));
    }

    private void applyDarkSelectableTextView(TextView textView) {
        if (textView == null) return;
        textView.setTextColor(APP_TEXT_COLOR);
        textView.setBackgroundColor(APP_BACKGROUND_COLOR);
        textView.setTextIsSelectable(true);
    }

    private void applyDarkReadOnlyCommentField(EditText commentField) {
        if (commentField == null) return;
        commentField.setTextColor(APP_TEXT_COLOR);
        commentField.setHintTextColor(APP_MUTED_TEXT_COLOR);
        commentField.setBackground(createDarkOutlineBackground(1, 4f));
        commentField.setPadding(dp(10), dp(10), dp(10), dp(10));
        commentField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        commentField.setGravity(Gravity.TOP | Gravity.START);
        commentField.setKeyListener(null);
        commentField.setTextIsSelectable(true);
        commentField.setCursorVisible(false);
        commentField.setShowSoftInputOnFocus(false);
        commentField.setHorizontallyScrolling(false);
        commentField.setLongClickable(true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawable(new ColorDrawable(APP_BACKGROUND_COLOR));

        // Root layout
        ScrollView rootScroll = new ScrollView(this);
        rootScroll.setFillViewport(true);
        applyDarkSurface(rootScroll);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        applyDarkSurface(root);
        rootScroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        // Controls row
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        applyDarkSurface(controls);

        turnView = new TextView(this);
        turnView.setText("ターン: 黒");
        applyDarkLabel(turnView);
        controls.addView(turnView);

        Button modeBtn = new Button(this);
        modeBtn.setText(getModeLabel());
        applyDarkButton(modeBtn);
        modeBtn.setOnClickListener(v -> {
            if (gameMode == MODE_TWO_PLAYER) {
                gameMode = MODE_CPU;
            } else if (gameMode == MODE_CPU) {
                gameMode = MODE_AI;
            } else {
                gameMode = MODE_TWO_PLAYER;
            }
            modeBtn.setText(getModeLabel());
            resetGame();
            updateControlVisibility();
            Toast.makeText(this, getModeLabel() + " に切替", Toast.LENGTH_SHORT).show();
        });

        aiConfigBtn = new Button(this);
        aiConfigBtn.setText("AI設定");
        applyDarkButton(aiConfigBtn);
        aiConfigBtn.setOnClickListener(v -> showAiSettingsDialog());

        depthBtn = new Button(this);
        depthBtn.setText("難易度: " + simDepth);
        applyDarkButton(depthBtn);
        depthBtn.setOnClickListener(v -> {
            simDepth = (simDepth % 10) + 1; // cycle 1..10
            depthBtn.setText("難易度: " + simDepth);
            Toast.makeText(this, "シミュレーション深さ: " + simDepth, Toast.LENGTH_SHORT).show();
        });

        Button resetBtn = new Button(this);
        resetBtn.setText("リセット");
        applyDarkButton(resetBtn);
        resetBtn.setOnClickListener(v -> {
            resetGame();
            Toast.makeText(this, "ゲームをリセットしました", Toast.LENGTH_SHORT).show();
        });

        controls.addView(modeBtn);
        controls.addView(aiConfigBtn);
        controls.addView(depthBtn);
        controls.addView(resetBtn);

        root.addView(controls);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(SIZE);
        grid.setRowCount(SIZE);
        applyDarkSurface(grid);

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

        aiCommentContainer = new LinearLayout(this);
        aiCommentContainer.setOrientation(LinearLayout.VERTICAL);
        applyDarkSurface(aiCommentContainer);
        int commentPad = (int) (12 * getResources().getDisplayMetrics().density);
        aiCommentContainer.setPadding(commentPad, commentPad, commentPad, commentPad);

        TextView aiCommentLabel = new TextView(this);
        aiCommentLabel.setText("AIコメント");
        applyDarkLabel(aiCommentLabel);
        aiCommentContainer.addView(aiCommentLabel);

        LinearLayout.LayoutParams commentScrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (140 * getResources().getDisplayMetrics().density));
        commentScrollParams.topMargin = commentPad / 2;

        aiCommentView = new EditText(this);
        aiCommentView.setText("AIのコメントはまだありません。");
        applyDarkReadOnlyCommentField(aiCommentView);
        aiCommentContainer.addView(aiCommentView, commentScrollParams);
        root.addView(aiCommentContainer);

        updateControlVisibility();
        setContentView(rootScroll);
        updateAiCommentView();
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
        clearAiComments();
        updateBoardUI();
    }

    private void updateControlVisibility() {
        if (aiConfigBtn != null) {
            aiConfigBtn.setVisibility(gameMode == MODE_AI ? View.VISIBLE : View.GONE);
        }
        if (depthBtn != null) {
            depthBtn.setVisibility(gameMode == MODE_CPU ? View.VISIBLE : View.GONE);
        }
        if (aiCommentContainer != null) {
            aiCommentContainer.setVisibility(gameMode == MODE_AI ? View.VISIBLE : View.GONE);
        }
    }

    private String getModeLabel() {
        if (gameMode == MODE_TWO_PLAYER) return "対戦: 2人";
        if (gameMode == MODE_AI) return "対戦: AI";
        return "対戦: CPU";
    }

    private void showAiSettingsDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, pad);
        applyDarkSurface(container);

        TextView urlLabel = new TextView(this);
        urlLabel.setText("URL");
        applyDarkLabel(urlLabel);
        container.addView(urlLabel);

        EditText urlInput = new EditText(this);
        urlInput.setHint(DEFAULT_AI_BASE_URL);
        urlInput.setText(aiBaseUrl);
        applyDarkInput(urlInput);
        container.addView(urlInput);

        final String[] selectedModel = new String[]{aiModel};
        TextView modelView = new TextView(this);
        modelView.setText("モデル: " + selectedModel[0]);
        applyDarkLabel(modelView);
        container.addView(modelView);

        Button modelSelectBtn = new Button(this);
        modelSelectBtn.setText("api/tags から選択");
        applyDarkButton(modelSelectBtn);
        modelSelectBtn.setOnClickListener(v -> {
            String baseUrl = normalizeBaseUrl(urlInput.getText().toString());
            if (baseUrl.isEmpty()) {
                Toast.makeText(this, "URLを入力してください", Toast.LENGTH_SHORT).show();
                return;
            }
            loadModelsAndShowChooser(baseUrl, selectedModel, modelView);
        });
        container.addView(modelSelectBtn);

        TextView promptLabel = new TextView(this);
        promptLabel.setText("プロンプト");
        applyDarkLabel(promptLabel);
        container.addView(promptLabel);

        EditText promptInput = new EditText(this);
        promptInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        promptInput.setMinLines(10);
        promptInput.setGravity(Gravity.TOP | Gravity.START);
        promptInput.setText(aiPrompt);
        applyDarkInput(promptInput);
        container.addView(promptInput);

        Button logBtn = new Button(this);
        logBtn.setText("AIログ");
        applyDarkButton(logBtn);
        logBtn.setOnClickListener(v -> showAiLogDialog());
        container.addView(logBtn);

        ScrollView scrollView = new ScrollView(this);
        applyDarkSurface(scrollView);
        scrollView.addView(container);

        new AlertDialog.Builder(this)
                .setTitle("AI設定")
                .setView(scrollView)
                .setPositiveButton("保存", (dialog, which) -> {
                    aiBaseUrl = normalizeBaseUrl(urlInput.getText().toString());
                    if (aiBaseUrl.isEmpty()) aiBaseUrl = DEFAULT_AI_BASE_URL;
                    String model = selectedModel[0] == null ? "" : selectedModel[0].trim();
                    aiModel = model.isEmpty() ? "default" : model;
                    String prompt = promptInput.getText().toString();
                    aiPrompt = prompt.trim().isEmpty() ? DEFAULT_AI_PROMPT : prompt;
                    Toast.makeText(this, "AI設定を保存しました", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("初期化", (dialog, which) -> {
                    aiBaseUrl = DEFAULT_AI_BASE_URL;
                    aiModel = "default";
                    aiPrompt = DEFAULT_AI_PROMPT;
                    Toast.makeText(this, "AI設定を初期化しました", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void loadModelsAndShowChooser(String baseUrl, String[] selectedModel, TextView modelView) {
        modelView.setText("モデル: 読み込み中...");
        new Thread(() -> {
            try {
                List<String> models = fetchModelNames(baseUrl);
                runOnUiThread(() -> {
                    if (models.isEmpty()) {
                        String current = (selectedModel[0] == null || selectedModel[0].trim().isEmpty())
                                ? "default" : selectedModel[0];
                        modelView.setText("モデル: " + current);
                        Toast.makeText(this, "モデルが見つかりませんでした", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int checked = models.indexOf(selectedModel[0]);
                    if (checked < 0) checked = 0;
                    final int[] picked = new int[]{checked};
                    ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                            this,
                            android.R.layout.simple_list_item_single_choice,
                            models) {
                        @Override
                        public View getView(int position, View convertView, ViewGroup parent) {
                            TextView row = (TextView) super.getView(position, convertView, parent);
                            applyDarkLabel(row);
                            row.setPadding(dp(16), dp(12), dp(16), dp(12));
                            return row;
                        }
                    };
                    ListView listView = new ListView(this);
                    applyDarkSurface(listView);
                    listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
                    listView.setAdapter(adapter);
                    listView.setItemChecked(checked, true);
                    listView.setOnItemClickListener((parent, view, which, id) -> picked[0] = which);

                    new AlertDialog.Builder(this)
                            .setTitle("モデル選択")
                            .setView(listView)
                            .setPositiveButton("選択", (dialog, which) -> {
                                selectedModel[0] = models.get(picked[0]);
                                modelView.setText("モデル: " + selectedModel[0]);
                            })
                            .setNegativeButton("キャンセル", (dialog, which) -> {
                                String current = (selectedModel[0] == null || selectedModel[0].trim().isEmpty())
                                        ? "default" : selectedModel[0];
                                modelView.setText("モデル: " + current);
                            })
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    String current = (selectedModel[0] == null || selectedModel[0].trim().isEmpty())
                            ? "default" : selectedModel[0];
                    modelView.setText("モデル: " + current);
                    Toast.makeText(this, "モデル取得に失敗: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private List<String> fetchModelNames(String baseUrl) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(baseUrl + "/api/tags").openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);

            int code = conn.getResponseCode();
            String body = readStream(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
            if (code >= 400) {
                throw new IllegalStateException("HTTP " + code);
            }

            JSONObject root = new JSONObject(body);
            JSONArray models = root.optJSONArray("models");
            List<String> names = new ArrayList<>();
            if (models != null) {
                for (int i = 0; i < models.length(); i++) {
                    JSONObject model = models.optJSONObject(i);
                    if (model == null) continue;
                    String name = model.optString("name", "").trim();
                    if (name.isEmpty()) {
                        name = model.optString("model", "").trim();
                    }
                    if (!name.isEmpty() && !names.contains(name)) {
                        names.add(name);
                    }
                }
            }
            return names;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // -------------------------
    // 人間の手（2人 or CPU）
    // -------------------------
    private void onHumanMove(int x, int y) {
        if (gameMode == MODE_CPU || gameMode == MODE_AI) {
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
        if (gameMode == MODE_TWO_PLAYER) {
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
            // vs CPU / AI
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
            updateBoardUI();

            // 両者置けない → 終了
            if (!hasValidMove(currentPlayer)) {
                showGameResult();
                return;
            }
        }

        // CPU/AI の番なら動かす
        if (gameMode == MODE_CPU && currentPlayer == 2) {
            cpuMove();
        } else if (gameMode == MODE_AI && currentPlayer == 2) {
            aiMove();
        }
    }

    // -------------------------
    // CPU の手（深さ指定ミニマックス）
    // -------------------------
    private void cpuMove() {
        // 思考中表示
        if (turnView != null) {
            turnView.setText("CPU思考中...");
        }
        updateBoardUI();

        // シミュレーションは別スレッドで実行し、UI更新はメインスレッドで行う
        new Thread(() -> {
            int[] best = findBestMoveSim(2, simDepth);
            runOnUiThread(() -> {
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
            });
        }).start();
    }

    private void aiMove() {
        if (turnView != null) {
            turnView.setText("AI思考中...");
        }
        updateBoardUI();

        final List<String> legalCandidates = getLegalMoveCodes(2);
        if (legalCandidates.isEmpty()) {
            Toast.makeText(this, "AI はパスしました", Toast.LENGTH_SHORT).show();
            currentPlayer = 1;
            updateBoardUI();
            handleTurn();
            return;
        }

        final String baseUrl = normalizeBaseUrl(aiBaseUrl);
        if (baseUrl.isEmpty()) {
            Toast.makeText(this, "AI URLを設定してください", Toast.LENGTH_LONG).show();
            currentPlayer = 1;
            updateBoardUI();
            return;
        }

        final String model = (aiModel == null || aiModel.trim().isEmpty()) ? "default" : aiModel.trim();
        final String basePrompt = (aiPrompt == null || aiPrompt.trim().isEmpty()) ? DEFAULT_AI_PROMPT : aiPrompt;
        final String promptWithBoard = buildPromptWithBoardAndCandidates(basePrompt, board, legalCandidates);
        final Set<String> legalCandidateSet = new HashSet<>(legalCandidates);

        new Thread(() -> {
            AiMoveSelection selectedMove = null;
            String error = null;

            for (int i = 0; i < 10; i++) {
                try {
                    String response = requestAiMoveText(baseUrl, model, promptWithBoard);
                    logAiInteraction(promptWithBoard, response, null);
                    AiMoveSelection parsed = parseAiMove(response);
                    if (parsed == null) continue;
                    if (parsed.isPass()) {
                        continue;
                    }

                    if (legalCandidateSet.contains(parsed.moveCode) && canPlace(parsed.x, parsed.y, 2)) {
                        selectedMove = parsed;
                        break;
                    }
                } catch (Exception e) {
                    String message = e.getMessage();
                    if (message == null || message.trim().isEmpty()) {
                        message = e.toString();
                    }
                    logAiInteraction(promptWithBoard, null, message);
                    error = message;
                    break;
                }
            }

            final AiMoveSelection resultMove = selectedMove;
            final String resultError = error;

            runOnUiThread(() -> {
                if (resultMove != null) {
                    placeStone(resultMove.x, resultMove.y, 2);
                    appendAiComment(resultMove.moveCode, resultMove.reason);
                    Toast.makeText(this, "AI が置きました", Toast.LENGTH_SHORT).show();
                    currentPlayer = 1;
                    updateBoardUI();
                    handleTurn();
                    return;
                }

                String message = (resultError == null || resultError.trim().isEmpty())
                        ? "AI応答エラー: 有効な座標を取得できませんでした"
                        : "AI連携エラー: " + resultError;
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                currentPlayer = 1;
                updateBoardUI();
                handleTurn();
            });
        }).start();
    }

    private String requestAiMoveText(String baseUrl, String model, String prompt) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(baseUrl + "/api/generate").openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(180000);
            conn.setDoOutput(true);

            JSONObject req = new JSONObject();
            req.put("model", model);
            req.put("prompt", prompt);
            req.put("stream", false);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(req.toString().getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            String body = readStream(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
            if (code >= 400) {
                throw new IllegalStateException("HTTP " + code + (body.isEmpty() ? "" : ": " + body));
            }

            JSONObject root = new JSONObject(body);
            String response = root.optString("response", "").trim();
            if (response.isEmpty()) {
                JSONObject message = root.optJSONObject("message");
                if (message != null) {
                    response = message.optString("content", "").trim();
                }
            }
            return response;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private AiMoveSelection parseAiMove(String response) {
        if (response == null) return null;
        String normalized = response.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.isEmpty()) return null;
        if ("PASS".equalsIgnoreCase(normalized)) {
            return AiMoveSelection.pass();
        }

        Matcher matcher = AI_MOVE_PATTERN.matcher(normalized);
        if (!matcher.find()) return null;

        String moveCode = matcher.group(1).toUpperCase(Locale.US);
        int y = moveCode.charAt(0) - 'A';
        int x = moveCode.charAt(1) - '1';

        String afterMatch = normalized.substring(matcher.end());
        String reason = afterMatch.trim();
        int index = 0;
        while (index < afterMatch.length()) {
            char ch = afterMatch.charAt(index);
            if (ch == ' ' || ch == '\t') {
                index++;
                continue;
            }
            break;
        }
        if (index < afterMatch.length() && afterMatch.charAt(index) == '\n') {
            reason = afterMatch.substring(index + 1).trim();
        }

        return new AiMoveSelection(x, y, moveCode, reason, false);
    }

    private String boardToText(int[][] b) {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int v = b[y][x];
                if (v == 1) sb.append('●');
                else if (v == 2) sb.append('○');
                else sb.append('・');
            }
            if (y < SIZE - 1) sb.append('\n');
        }
        return sb.toString();
    }

    private String buildPromptWithBoardAndCandidates(String basePrompt, int[][] b, List<String> candidates) {
        StringBuilder sb = new StringBuilder(basePrompt);
        sb.append("\n\n盤面:\n").append(boardToText(b));
        sb.append("\n\n候補:\n");
        for (int i = 0; i < candidates.size(); i++) {
            sb.append(candidates.get(i));
            if (i < candidates.size() - 1) {
                sb.append('\n');
            }
        }
        sb.append(AI_RESPONSE_FORMAT_PROMPT);
        return sb.toString();
    }

    private List<String> getLegalMoveCodes(int player) {
        List<String> moves = new ArrayList<>();
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (canPlace(x, y, player)) {
                    moves.add(toMoveCode(x, y));
                }
            }
        }
        return moves;
    }

    private String toMoveCode(int x, int y) {
        return String.valueOf((char) ('A' + y)) + (char) ('1' + x);
    }

    private String normalizeBaseUrl(String url) {
        if (url == null) return "";
        String normalized = url.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String readStream(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString().trim();
    }

    private void logAiInteraction(String prompt, String response, String error) {
        if (prompt == null) return;
        synchronized (aiLogs) {
            if (aiLogs.size() >= MAX_AI_LOG_ENTRIES) {
                aiLogs.remove(0);
            }
            aiLogs.add(new AiLogEntry(System.currentTimeMillis(), prompt, response, error));
        }
    }

    private void appendAiComment(String moveCode, String reason) {
        if (moveCode == null || moveCode.trim().isEmpty()) return;
        String normalizedReason = reason == null ? "" : reason.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalizedReason.isEmpty()) {
            normalizedReason = "（理由なし）";
        }
        synchronized (aiCommentHistory) {
            aiCommentHistory.add(0, moveCode + "\n" + normalizedReason);
        }
        updateAiCommentView();
    }

    private void clearAiComments() {
        synchronized (aiCommentHistory) {
            aiCommentHistory.clear();
        }
        updateAiCommentView();
    }

    private void updateAiCommentView() {
        if (aiCommentView != null) {
            aiCommentView.setText(buildAiCommentText());
            aiCommentView.post(() -> aiCommentView.scrollTo(0, 0));
        }
    }

    private String buildAiCommentText() {
        synchronized (aiCommentHistory) {
            if (aiCommentHistory.isEmpty()) {
                return "AIのコメントはまだありません。";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < aiCommentHistory.size(); i++) {
                sb.append(aiCommentHistory.get(i));
                if (i < aiCommentHistory.size() - 1) {
                    sb.append("\n\n");
                }
            }
            return sb.toString();
        }
    }

    private void showAiLogDialog() {
        final TextView content = new TextView(this);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        content.setPadding(pad, pad, pad, pad);
        content.setText(buildAiLogText());
        applyDarkSelectableTextView(content);
        ScrollView scrollView = new ScrollView(this);
        applyDarkSurface(scrollView);
        scrollView.addView(content);

        new AlertDialog.Builder(this)
                .setTitle("AI通信ログ")
                .setView(scrollView)
                .setPositiveButton("閉じる", null)
                .setNeutralButton("クリア", (dialog, which) -> {
                    clearAiLogs();
                    showAiLogDialog();
                })
                .setNegativeButton("コピー", (dialog, which) -> copyAiLogsToClipboard())
                .show();
    }

    private String buildAiLogText() {
        List<AiLogEntry> snapshot;
        synchronized (aiLogs) {
            snapshot = new ArrayList<>(aiLogs);
        }
        if (snapshot.isEmpty()) {
            return "ログはありません。";
        }
        StringBuilder sb = new StringBuilder();
        DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault());
        for (int i = snapshot.size() - 1; i >= 0; i--) {
            AiLogEntry entry = snapshot.get(i);
            sb.append(formatter.format(new Date(entry.timestamp))).append('\n');
            sb.append("プロンプト:\n").append(entry.prompt).append('\n');
            sb.append("レスポンス:\n")
                    .append(entry.response == null ? "（なし）" : entry.response)
                    .append('\n');
            if (entry.error != null) {
                sb.append("エラー: ").append(entry.error).append('\n');
            }
            if (i > 0) sb.append('\n');
        }
        return sb.toString().trim();
    }

    private void clearAiLogs() {
        synchronized (aiLogs) {
            aiLogs.clear();
        }
        Toast.makeText(this, "AIログをクリアしました", Toast.LENGTH_SHORT).show();
    }

    private void copyAiLogsToClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, "クリップボードにアクセスできません", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipData clip = ClipData.newPlainText("AI通信ログ", buildAiLogText());
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "AIログをコピーしました", Toast.LENGTH_SHORT).show();
    }

    private static class AiLogEntry {
        final long timestamp;
        final String prompt;
        final String response;
        final String error;

        AiLogEntry(long timestamp, String prompt, String response, String error) {
            this.timestamp = timestamp;
            this.prompt = prompt;
            this.response = response;
            this.error = error;
        }
    }

    private static class AiMoveSelection {
        final int x;
        final int y;
        final String moveCode;
        final String reason;
        final boolean pass;

        AiMoveSelection(int x, int y, String moveCode, String reason, boolean pass) {
            this.x = x;
            this.y = y;
            this.moveCode = moveCode;
            this.reason = reason;
            this.pass = pass;
        }

        static AiMoveSelection pass() {
            return new AiMoveSelection(-1, -1, "PASS", "", true);
        }

        boolean isPass() {
            return pass;
        }
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
                    btn.setBackgroundColor(BOARD_EMPTY_COLOR); // 濃い緑
                    btn.setEnabled((gameMode == MODE_TWO_PLAYER)
                            ? canPlace(x, y, currentPlayer)
                            : (currentPlayer == 1 && canPlace(x, y, 1)));
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
