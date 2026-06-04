package com.micklab.reversy;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Switch;
import android.widget.Toast;

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
    private static final int SIZE = 8;
    private static final int PLAYER_BLACK = 1;
    private static final int PLAYER_WHITE = 2;

    private static final int MODE_HUMAN = 1;
    private static final int MODE_CPU = 2;
    private static final int MODE_AI = 3;

    private static final String DEFAULT_AI_BASE_URL = "http://127.0.0.1:11434";
    private static final String DEFAULT_AI_MODEL = "default";
    private static final int MAX_AI_LOG_ENTRIES = 25;

    private static final int APP_BACKGROUND_COLOR = 0xFF000000;
    private static final int APP_TEXT_COLOR = 0xFF00FF00;
    private static final int APP_MUTED_TEXT_COLOR = 0xFF66FF66;
    private static final int APP_BORDER_COLOR = 0xFF00AA00;
    private static final int BOARD_EMPTY_COLOR = 0xFF006400;
    private static final int BOARD_GRID_COLOR = 0xFF000000;
    private static final int BUTTON_BACKGROUND_COLOR = 0xFF0F4A0F;
    private static final int COMMENT_PANEL_COLOR = 0xB8000000;
    private static final int COMMENT_FIELD_COLOR = 0x70000000;
    private static final int ACTIVE_BUTTON_TEXT_COLOR = Color.BLACK;

    private static final int CPU_CORNER_SCORE_WEIGHT = 1000;
    private static final int CPU_MOBILITY_SCORE_WEIGHT = 25;

    private static final float ANIMATION_SLOWDOWN = 1.5f;
    private static final long PLACE_SLIDE_DURATION_MS = (long) (180L * ANIMATION_SLOWDOWN);
    private static final long FLIP_HALF_DURATION_MS = (long) (110L * ANIMATION_SLOWDOWN);
    private static final long FLIP_STAGGER_MS = (long) (70L * ANIMATION_SLOWDOWN);

    private static final String AI_RESPONSE_FORMAT_PROMPT =
            "\n\n【回答形式の再確認】\n" +
            "- 1行目に選んだ座標を1つだけ書く。\n" +
            "- 2行目にその選択理由を書く。\n" +
            "- 座標は「行ラベル + 列番号」の A1〜H8 形式にする。\n" +
            "- 余計な説明は書かない。";

    private static final Pattern AI_MOVE_PATTERN = Pattern.compile(
            "(?<![A-Z0-9])([A-H][1-8])(?![A-Z0-9])",
            Pattern.CASE_INSENSITIVE);

    private final Button[][] cells = new Button[SIZE][SIZE];
    private final int[][] board = new int[SIZE][SIZE];
    private final List<AiLogEntry> aiLogs = new ArrayList<>();
    private final List<String> moveCommentHistory = new ArrayList<>();

    private Button blackModeBtn;
    private Button whiteModeBtn;
    private Button blackSettingsBtn;
    private Button whiteSettingsBtn;
    private Button gameActionBtn;
    private TextView statusView;
    private ProgressBar statusSpinner;
    private TextView commentView;
    private ScrollView commentScrollView;
    private FrameLayout boardFrame;
    private GridLayout boardGrid;

    private int currentPlayer = PLAYER_BLACK;
    private boolean isMoveAnimating;
    private boolean gameStarted = false;
    private int boardLayoutSize = -1;

    private PlayerConfig blackConfig;
    private PlayerConfig whiteConfig;

    private static class PlayerConfig {
        int mode;
        int savedNonAiMode;
        int cpuDepth;
        String aiBaseUrl;
        String aiModel;
        String aiPrompt;
        int aiTimeoutSec;
        int aiMaxRetries;
    }

    private static class AiLogEntry {
        final int player;
        final long timestamp;
        final String prompt;
        final String response;
        final String error;

        AiLogEntry(int player, long timestamp, String prompt, String response, String error) {
            this.player = player;
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

    private static class CellPosition {
        final int x;
        final int y;

        CellPosition(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    private static class MoveResult {
        final int x;
        final int y;
        final int player;
        final List<CellPosition> flipped = new ArrayList<>();

        MoveResult(int x, int y, int player) {
            this.x = x;
            this.y = y;
            this.player = player;
        }
    }

    private static class CpuMoveEvaluation {
        final int x;
        final int y;
        final int score;
        final int flips;
        final int playerPieces;
        final int opponentPieces;
        final int playerCorners;
        final int opponentCorners;
        final int opponentMobility;
        final int futureMobility;
        final int futureMaxFlips;
        final boolean cornerMove;
        final boolean opponentCanTakeCorner;
        final boolean opponentCanWipeOutPlayer;
        final boolean cornerThreatAvailable;

        CpuMoveEvaluation(
                int x,
                int y,
                int score,
                int flips,
                int playerPieces,
                int opponentPieces,
                int playerCorners,
                int opponentCorners,
                int opponentMobility,
                int futureMobility,
                int futureMaxFlips,
                boolean cornerMove,
                boolean opponentCanTakeCorner,
                boolean opponentCanWipeOutPlayer,
                boolean cornerThreatAvailable) {
            this.x = x;
            this.y = y;
            this.score = score;
            this.flips = flips;
            this.playerPieces = playerPieces;
            this.opponentPieces = opponentPieces;
            this.playerCorners = playerCorners;
            this.opponentCorners = opponentCorners;
            this.opponentMobility = opponentMobility;
            this.futureMobility = futureMobility;
            this.futureMaxFlips = futureMaxFlips;
            this.cornerMove = cornerMove;
            this.opponentCanTakeCorner = opponentCanTakeCorner;
            this.opponentCanWipeOutPlayer = opponentCanWipeOutPlayer;
            this.cornerThreatAvailable = cornerThreatAvailable;
        }

        boolean isBetterThan(CpuMoveEvaluation other) {
            if (cornerMove != other.cornerMove) return cornerMove;
            if (opponentCanTakeCorner != other.opponentCanTakeCorner) return !opponentCanTakeCorner;
            if (opponentCanWipeOutPlayer != other.opponentCanWipeOutPlayer) return !opponentCanWipeOutPlayer;
            if (score != other.score) return score > other.score;
            if (futureMobility != other.futureMobility) return futureMobility > other.futureMobility;
            if (futureMaxFlips != other.futureMaxFlips) return futureMaxFlips > other.futureMaxFlips;
            return flips > other.flips;
        }
    }

    private static String buildDefaultAiPromptForPlayer(int player) {
        String playerLabel = player == PLAYER_BLACK ? "黒" : "白";
        String playerStone = player == PLAYER_BLACK ? "●" : "○";
        String opponentLabel = player == PLAYER_BLACK ? "白" : "黒";
        String opponentStone = player == PLAYER_BLACK ? "○" : "●";
        return "あなたはオセロ（リバーシ）の指し手選択AIとして動作する。\n\n" +
                "【役割】\n" +
                "- 私が渡す盤面（8行、●=黒、○=白、・=空き）と、合法手候補の一覧を受け取り、\n" +
                "  " + playerLabel + "（" + playerStone + "）として最善と思う手を1つだけ選ぶ。\n" +
                "- 相手は " + opponentLabel + "（" + opponentStone + "）である。\n\n" +
                "【座標の読み方】\n" +
                "- 行ラベルは上から A〜H、列ラベルは左から 1〜8。\n" +
                "- 座標は「行ラベル + 列番号」で表す。\n" +
                "- A1 は左上、H8 は右下を指す。\n\n" +
                "【戦略】\n" +
                "- 角を取れるなら積極的に取りに行く。\n" +
                "- 相手に角を取らせないことを優先し、角を渡す手はできるだけ避ける。\n" +
                "- 相手の合法手を減らし、自分の次の候補を残す手を優先する。\n" +
                "- 相手の手で自分のコマがゼロになる展開を避ける。\n\n" +
                "【理由の書き方】\n" +
                "- 2行目では、今返せる枚数だけでなく、次に狙える手数・将来返せそうな枚数・相手の選択肢・角への影響をできるだけ具体的に説明する。\n" +
                "- 長くなってもよいが、1段落で簡潔にまとめる。\n\n" +
                "【回答形式】\n" +
                "- 1行目に、選んだ座標を1つだけ書く。\n" +
                "- 2行目に、その選択理由を日本語で書く。\n" +
                "- 座標は「行ラベル + 列番号」の A1〜H8 形式にする。\n" +
                "- 余計な前置き、箇条書き、盤面の再表示は書かない。\n\n" +
                "【禁止事項】\n" +
                "- 合法手候補に含まれない座標を出力してはならない。\n" +
                "- 例示された座標を模倣して出力してはならない（候補に無い場合）。\n" +
                "- 2行構成を崩してはならない。";
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void updateBoardLayout(int availableWidth) {
        if (boardFrame == null || boardGrid == null || availableWidth <= 0) {
            return;
        }

        int cellSize = Math.max(1, (availableWidth - (SIZE * 2)) / SIZE);
        int boardSize = (cellSize * SIZE) + (SIZE * 2);
        if (boardSize == boardLayoutSize) {
            return;
        }
        boardLayoutSize = boardSize;

        ViewGroup.LayoutParams frameParams = boardFrame.getLayoutParams();
        if (frameParams == null) {
            frameParams = new LinearLayout.LayoutParams(boardSize, boardSize);
        } else {
            frameParams.width = boardSize;
            frameParams.height = boardSize;
        }
        boardFrame.setLayoutParams(frameParams);

        ViewGroup.LayoutParams gridParams = boardGrid.getLayoutParams();
        if (gridParams == null) {
            gridParams = new FrameLayout.LayoutParams(boardSize, boardSize);
        } else {
            gridParams.width = boardSize;
            gridParams.height = boardSize;
        }
        boardGrid.setLayoutParams(gridParams);

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                GridLayout.LayoutParams cellParams = (GridLayout.LayoutParams) cells[y][x].getLayoutParams();
                if (cellParams == null) {
                    cellParams = new GridLayout.LayoutParams(
                            GridLayout.spec(y), GridLayout.spec(x));
                }
                cellParams.width = cellSize;
                cellParams.height = cellSize;
                cellParams.setMargins(1, 1, 1, 1);
                cells[y][x].setLayoutParams(cellParams);
            }
        }

        boardFrame.requestLayout();
        boardGrid.requestLayout();
    }

    private GradientDrawable createOutlinedBackground(int fillColor, int strokeWidthDp, float radiusDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(strokeWidthDp), strokeColor);
        return drawable;
    }

    private GradientDrawable createDarkOutlineBackground(int strokeWidthDp, float radiusDp) {
        return createOutlinedBackground(APP_BACKGROUND_COLOR, strokeWidthDp, radiusDp, APP_BORDER_COLOR);
    }

    private GradientDrawable createModeButtonBackground(boolean active) {
        int fill = active ? APP_TEXT_COLOR : APP_BACKGROUND_COLOR;
        int stroke = active ? Color.BLACK : APP_BORDER_COLOR;
        return createOutlinedBackground(fill, 1, 10f, stroke);
    }

    private GradientDrawable createDarkButtonBackground() {
        return createOutlinedBackground(BUTTON_BACKGROUND_COLOR, 1, 8f, APP_BORDER_COLOR);
    }

    private GradientDrawable createCommentOverlayBackground() {
        return createOutlinedBackground(COMMENT_PANEL_COLOR, 1, 14f, APP_BORDER_COLOR);
    }

    private GradientDrawable createCommentFieldBackground() {
        return createOutlinedBackground(COMMENT_FIELD_COLOR, 1, 10f, APP_BORDER_COLOR);
    }

    private void applyDarkSurface(View view) {
        if (view != null) {
            view.setBackgroundColor(APP_BACKGROUND_COLOR);
        }
    }

    private void applyDarkLabel(TextView view) {
        if (view == null) return;
        view.setTextColor(APP_TEXT_COLOR);
        view.setBackgroundColor(Color.TRANSPARENT);
    }

    private void applyDarkButton(Button button) {
        if (button == null) return;
        button.setTextColor(APP_TEXT_COLOR);
        button.setBackground(createDarkButtonBackground());
    }

    private TextView createGreenDialogTitle(String title) {
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(Color.BLACK);
        titleView.setBackgroundColor(APP_TEXT_COLOR);
        titleView.setTextSize(18f);
        titleView.setPadding(dp(12), dp(10), dp(12), dp(10));
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        titleView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return titleView;
    }

    private void applySafeAreaPadding(View view, int baseLeft, int baseTop, int baseRight, int baseBottom, boolean includeHorizontalInsets) {
        view.setPadding(baseLeft, baseTop, baseRight, baseBottom);
        view.setOnApplyWindowInsetsListener((v, insets) -> {
            int left = baseLeft + (includeHorizontalInsets ? insets.getSystemWindowInsetLeft() : 0);
            int top = baseTop + insets.getSystemWindowInsetTop();
            int right = baseRight + (includeHorizontalInsets ? insets.getSystemWindowInsetRight() : 0);
            int bottom = baseBottom + insets.getSystemWindowInsetBottom();
            v.setPadding(left, top, right, bottom);
            return insets;
        });
        view.post(view::requestApplyInsets);
    }

    private void applyModeButtonStyle(Button button, boolean active) {
        if (button == null) return;
        button.setTextColor(active ? ACTIVE_BUTTON_TEXT_COLOR : APP_TEXT_COLOR);
        button.setBackground(createModeButtonBackground(active));
    }

    private void applyDarkStatusView(TextView view) {
        if (view == null) return;
        view.setTextColor(APP_TEXT_COLOR);
        view.setBackground(createDarkOutlineBackground(1, 4f));
        view.setPadding(dp(10), dp(10), dp(10), dp(10));
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

    private void setStatusText(String message) {
        if (statusSpinner != null) {
            statusSpinner.setVisibility(View.GONE);
        }
        if (statusView != null) {
            statusView.setText(message == null ? "" : message);
        }
    }

    private void setThinkingStatus(String message) {
        if (statusView != null) {
            statusView.setText(message == null ? "" : message);
        }
        if (statusSpinner != null) {
            statusSpinner.setVisibility(View.VISIBLE);
        }
    }

    private void showStatusMessage(String message) {
        setStatusText(message);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawable(new ColorDrawable(APP_BACKGROUND_COLOR));

        blackConfig = createDefaultPlayerConfig(PLAYER_BLACK, MODE_HUMAN);
        whiteConfig = createDefaultPlayerConfig(PLAYER_WHITE, MODE_CPU);

        ScrollView rootScroll = new ScrollView(this);
        rootScroll.setFillViewport(true);
        applyDarkSurface(rootScroll);
        applySafeAreaPadding(rootScroll, dp(12), dp(12), dp(12), dp(12), false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        applyDarkSurface(root);
        rootScroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        int outerPad = dp(12);

        LinearLayout controlContainer = new LinearLayout(this);
        controlContainer.setOrientation(LinearLayout.VERTICAL);
        controlContainer.setPadding(outerPad, outerPad, outerPad, dp(8));
        applyDarkSurface(controlContainer);

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        applyDarkSurface(modeRow);

        blackModeBtn = new Button(this);
        blackModeBtn.setOnClickListener(v -> cyclePlayerMode(PLAYER_BLACK));
        LinearLayout.LayoutParams blackModeParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        blackModeParams.rightMargin = dp(8);
        modeRow.addView(blackModeBtn, blackModeParams);

        whiteModeBtn = new Button(this);
        whiteModeBtn.setOnClickListener(v -> cyclePlayerMode(PLAYER_WHITE));
        LinearLayout.LayoutParams whiteModeParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        modeRow.addView(whiteModeBtn, whiteModeParams);

        controlContainer.addView(modeRow);

        LinearLayout settingsRow = new LinearLayout(this);
        settingsRow.setOrientation(LinearLayout.HORIZONTAL);
        settingsRow.setPadding(0, dp(8), 0, 0);
        applyDarkSurface(settingsRow);

        blackSettingsBtn = new Button(this);
        blackSettingsBtn.setText("黒設定");
        applyDarkButton(blackSettingsBtn);
        blackSettingsBtn.setOnClickListener(v -> showPlayerSettingsDialog(PLAYER_BLACK));
        LinearLayout.LayoutParams blackSettingsParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        blackSettingsParams.rightMargin = dp(8);
        settingsRow.addView(blackSettingsBtn, blackSettingsParams);

        whiteSettingsBtn = new Button(this);
        whiteSettingsBtn.setText("白設定");
        applyDarkButton(whiteSettingsBtn);
        whiteSettingsBtn.setOnClickListener(v -> showPlayerSettingsDialog(PLAYER_WHITE));
        LinearLayout.LayoutParams whiteSettingsParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        settingsRow.addView(whiteSettingsBtn, whiteSettingsParams);

        controlContainer.addView(settingsRow);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setPadding(0, dp(8), 0, 0);
        applyDarkSurface(actionRow);

        gameActionBtn = new Button(this);
        gameActionBtn.setText("対局開始");
        applyDarkButton(gameActionBtn);
        gameActionBtn.setOnClickListener(v -> onGameActionButtonClicked());
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        startParams.rightMargin = dp(8);
        actionRow.addView(gameActionBtn, startParams);

        Button documentsBtn = new Button(this);
        documentsBtn.setText("ドキュメント");
        applyDarkButton(documentsBtn);
        documentsBtn.setOnClickListener(v -> startActivity(new Intent(this, DocumentsActivity.class)));
        LinearLayout.LayoutParams documentsParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        actionRow.addView(documentsBtn, documentsParams);

        controlContainer.addView(actionRow);

        root.addView(controlContainer);

        LinearLayout statusContainer = new LinearLayout(this);
        statusContainer.setOrientation(LinearLayout.VERTICAL);
        statusContainer.setPadding(outerPad, dp(4), outerPad, dp(8));
        applyDarkSurface(statusContainer);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(0, 0, 0, 0);
        applyDarkSurface(statusRow);

        statusSpinner = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        statusSpinner.setIndeterminate(true);
        statusSpinner.setVisibility(View.GONE);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(dp(20), dp(20));
        spinnerParams.rightMargin = dp(10);
        statusRow.addView(statusSpinner, spinnerParams);

        statusView = new TextView(this);
        statusView.setText("待機中");
        applyDarkStatusView(statusView);
        LinearLayout.LayoutParams statusViewParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        statusRow.addView(statusView, statusViewParams);

        statusContainer.addView(statusRow);
        root.addView(statusContainer);

        initBoard();

        int screenWidth = getResources().getDisplayMetrics().widthPixels;

        boardFrame = new FrameLayout(this);
        boardFrame.setBackgroundColor(BOARD_EMPTY_COLOR);
        LinearLayout.LayoutParams boardFrameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        boardFrameParams.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(boardFrame, boardFrameParams);

        boardGrid = new GridLayout(this);
        boardGrid.setColumnCount(SIZE);
        boardGrid.setRowCount(SIZE);
        boardGrid.setBackgroundColor(BOARD_GRID_COLOR);
        FrameLayout.LayoutParams gridParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL);
        boardFrame.addView(boardGrid, gridParams);

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                Button btn = new Button(this);
                btn.setMinHeight(0);
                btn.setMinimumHeight(0);
                btn.setPadding(0, 0, 0, 0);
                btn.setText("");

                GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                        GridLayout.spec(y), GridLayout.spec(x));
                params.setMargins(1, 1, 1, 1);
                btn.setLayoutParams(params);

                final int fx = x;
                final int fy = y;
                btn.setOnClickListener(v -> onHumanMove(fx, fy));

                cells[y][x] = btn;
                boardGrid.addView(btn);
            }
        }

        LinearLayout commentOverlay = new LinearLayout(this);
        commentOverlay.setOrientation(LinearLayout.VERTICAL);
        commentOverlay.setBackgroundColor(Color.TRANSPARENT);
        commentOverlay.setPadding(0, 0, 0, 0);

        LinearLayout commentRow = new LinearLayout(this);
        commentRow.setOrientation(LinearLayout.HORIZONTAL);
        commentRow.setGravity(Gravity.TOP);
        commentRow.setPadding(0, 0, 0, 0);

        ImageView avatarView = new ImageView(this);
        avatarView.setImageResource(R.drawable.avator);
        avatarView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatarView.setBackground(createDarkOutlineBackground(1, 10f));
        int avatarSize = Math.round(screenWidth * 0.30f);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(avatarSize, avatarSize);
        avatarParams.rightMargin = dp(10);
        commentRow.addView(avatarView, avatarParams);

        commentScrollView = new ScrollView(this);
        commentScrollView.setBackground(createCommentFieldBackground());
        commentScrollView.setFillViewport(true);
        commentScrollView.setScrollbarFadingEnabled(false);
        commentScrollView.setVerticalScrollBarEnabled(true);
        commentScrollView.setOnTouchListener((v, event) -> {
            rootScroll.requestDisallowInterceptTouchEvent(true);
            if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                rootScroll.requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });
        int commentAreaHeight = avatarSize;
        LinearLayout.LayoutParams commentScrollParams = new LinearLayout.LayoutParams(
                0,
                commentAreaHeight,
                1f);

        commentView = new TextView(this);
        commentView.setTextColor(APP_TEXT_COLOR);
        commentView.setTextSize(13f);
        commentView.setLineSpacing(0f, 1.15f);
        commentView.setTextIsSelectable(true);
        commentView.setPadding(dp(10), dp(10), dp(10), dp(10));
        commentScrollView.addView(commentView, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        commentRow.addView(commentScrollView, commentScrollParams);

        commentOverlay.addView(commentRow);

        LinearLayout.LayoutParams commentContainerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        commentContainerParams.setMargins(outerPad, dp(8), outerPad, outerPad);
        root.addView(commentOverlay, commentContainerParams);

        setContentView(rootScroll);
        root.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if ((right - left) != (oldRight - oldLeft)) {
                updateBoardLayout(right - left);
            }
        });
        root.post(() -> updateBoardLayout(root.getWidth()));
        updateCommentView();
        updateControlPanel();
        updateBoardUI();
        handleTurn();
    }

    private PlayerConfig createDefaultPlayerConfig(int player, int mode) {
        PlayerConfig config = new PlayerConfig();
        config.mode = mode;
        config.savedNonAiMode = (mode != MODE_AI) ? mode : MODE_HUMAN;
        config.cpuDepth = 1;
        config.aiBaseUrl = DEFAULT_AI_BASE_URL;
        config.aiModel = DEFAULT_AI_MODEL;
        config.aiPrompt = buildDefaultAiPromptForPlayer(player);
        config.aiTimeoutSec = 180;
        config.aiMaxRetries = 10;
        return config;
    }

    private PlayerConfig getPlayerConfig(int player) {
        return player == PLAYER_BLACK ? blackConfig : whiteConfig;
    }

    private int getOpponent(int player) {
        return player == PLAYER_BLACK ? PLAYER_WHITE : PLAYER_BLACK;
    }

    private String getPlayerLabel(int player) {
        return player == PLAYER_BLACK ? "黒" : "白";
    }

    private String getModeLabel(int mode) {
        if (mode == MODE_CPU) return "CPU";
        if (mode == MODE_AI) return "AI";
        return "ユーザ";
    }

    private void cyclePlayerMode(int player) {
        PlayerConfig config = getPlayerConfig(player);
        if (config.mode == MODE_HUMAN) {
            config.mode = MODE_CPU;
        } else if (config.mode == MODE_CPU) {
            config.mode = MODE_AI;
        } else {
            config.mode = MODE_HUMAN;
        }
        updateControlPanel();
        resetGame();
        showStatusMessage(getPlayerLabel(player) + "を" + getModeLabel(config.mode) + "に切替");
    }

    private void updateControlPanel() {
        updateModeButton(blackModeBtn, PLAYER_BLACK);
        updateModeButton(whiteModeBtn, PLAYER_WHITE);
        if (blackSettingsBtn != null) {
            blackSettingsBtn.setText("黒設定");
        }
        if (whiteSettingsBtn != null) {
            whiteSettingsBtn.setText("白設定");
        }
        if (gameActionBtn != null) {
            gameActionBtn.setText(gameStarted ? "リセット" : "対局開始");
        }
    }

    private void updateModeButton(Button button, int player) {
        if (button == null) return;
        button.setText(getPlayerLabel(player) + ": " + getModeLabel(getPlayerConfig(player).mode));
        applyModeButtonStyle(button, currentPlayer == player && currentPlayer != 0);
    }

    private void initBoard() {
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                board[y][x] = 0;
            }
        }
        board[3][3] = PLAYER_WHITE;
        board[4][4] = PLAYER_WHITE;
        board[3][4] = PLAYER_BLACK;
        board[4][3] = PLAYER_BLACK;
        currentPlayer = PLAYER_BLACK;
        isMoveAnimating = false;
    }

    private void resetGame() {
        gameStarted = false;
        initBoard();
        clearMoveComments();
        updateControlPanel();
        updateBoardUI();
        handleTurn();
    }

    private void showPlayerSettingsDialog(int player) {
        PlayerConfig config = getPlayerConfig(player);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        container.setPadding(pad, pad, pad, pad);
        applyDarkSurface(container);

        LinearLayout aiSwitchRow = new LinearLayout(this);
        aiSwitchRow.setOrientation(LinearLayout.HORIZONTAL);
        aiSwitchRow.setGravity(Gravity.CENTER_VERTICAL);
        applyDarkSurface(aiSwitchRow);

        Switch aiSwitch = new Switch(this);
        aiSwitch.setText("AI有効");
        aiSwitch.setTextColor(APP_TEXT_COLOR);
        aiSwitch.setChecked(config.mode == MODE_AI);
        aiSwitchRow.addView(aiSwitch, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        container.addView(aiSwitchRow);

        TextView modeLabel = new TextView(this);
        modeLabel.setText("現在モード: " + getModeLabel(config.mode));
        modeLabel.setPadding(0, dp(6), 0, 0);
        applyDarkLabel(modeLabel);
        container.addView(modeLabel);

        TextView depthLabel = new TextView(this);
        depthLabel.setText("CPU深さ");
        depthLabel.setPadding(0, dp(10), 0, 0);
        applyDarkLabel(depthLabel);
        container.addView(depthLabel);

        final int[] selectedDepth = new int[]{config.cpuDepth};
        Button depthBtn = new Button(this);
        depthBtn.setText("深さ: " + selectedDepth[0]);
        applyDarkButton(depthBtn);
        depthBtn.setOnClickListener(v -> {
            selectedDepth[0] = (selectedDepth[0] % 10) + 1;
            depthBtn.setText("深さ: " + selectedDepth[0]);
        });
        container.addView(depthBtn);

        TextView urlLabel = new TextView(this);
        urlLabel.setText("AI URL");
        urlLabel.setPadding(0, dp(10), 0, 0);
        applyDarkLabel(urlLabel);
        container.addView(urlLabel);

        EditText urlInput = new EditText(this);
        urlInput.setHint(DEFAULT_AI_BASE_URL);
        urlInput.setText(config.aiBaseUrl);
        applyDarkInput(urlInput);
        container.addView(urlInput);

        final String[] selectedModel = new String[]{config.aiModel};
        TextView modelView = new TextView(this);
        modelView.setText("モデル: " + selectedModel[0]);
        modelView.setPadding(0, dp(10), 0, 0);
        applyDarkLabel(modelView);
        container.addView(modelView);

        Button modelSelectBtn = new Button(this);
        modelSelectBtn.setText("api/tags から選択");
        applyDarkButton(modelSelectBtn);
        modelSelectBtn.setOnClickListener(v -> {
            String baseUrl = normalizeBaseUrl(urlInput.getText().toString());
            if (baseUrl.isEmpty()) {
                showStatusMessage("URLを入力してください");
                return;
            }
            loadModelsAndShowChooser(baseUrl, selectedModel, modelView);
        });
        container.addView(modelSelectBtn);

        TextView promptLabel = new TextView(this);
        promptLabel.setText("AIプロンプト");
        promptLabel.setPadding(0, dp(10), 0, 0);
        applyDarkLabel(promptLabel);
        container.addView(promptLabel);

        EditText promptInput = new EditText(this);
        promptInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        promptInput.setMinLines(10);
        promptInput.setGravity(Gravity.TOP | Gravity.START);
        promptInput.setText(config.aiPrompt);
        applyDarkInput(promptInput);
        container.addView(promptInput);

        TextView timeoutLabel = new TextView(this);
        timeoutLabel.setText("AIタイムアウト（秒）");
        timeoutLabel.setPadding(0, dp(10), 0, 0);
        applyDarkLabel(timeoutLabel);
        container.addView(timeoutLabel);

        EditText timeoutInput = new EditText(this);
        timeoutInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        timeoutInput.setText(String.valueOf(config.aiTimeoutSec));
        applyDarkInput(timeoutInput);
        container.addView(timeoutInput);

        TextView retriesLabel = new TextView(this);
        retriesLabel.setText("AI繰り返し回数");
        retriesLabel.setPadding(0, dp(10), 0, 0);
        applyDarkLabel(retriesLabel);
        container.addView(retriesLabel);

        EditText retriesInput = new EditText(this);
        retriesInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        retriesInput.setText(String.valueOf(config.aiMaxRetries));
        applyDarkInput(retriesInput);
        container.addView(retriesInput);

        Button logBtn = new Button(this);
        logBtn.setText(getPlayerLabel(player) + "AIログ");
        applyDarkButton(logBtn);
        logBtn.setOnClickListener(v -> showAiLogDialog(player));
        LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        logParams.topMargin = dp(10);
        container.addView(logBtn, logParams);

        ScrollView scrollView = new ScrollView(this);
        applyDarkSurface(scrollView);
        scrollView.addView(container);

        new AlertDialog.Builder(this)
                .setCustomTitle(createGreenDialogTitle(getPlayerLabel(player) + "の設定"))
                .setView(scrollView)
                .setPositiveButton("保存", (dialog, which) -> {
                    if (aiSwitch.isChecked()) {
                        if (config.mode != MODE_AI) {
                            config.savedNonAiMode = config.mode;
                        }
                        config.mode = MODE_AI;
                    } else {
                        if (config.mode == MODE_AI) {
                            config.mode = (config.savedNonAiMode != MODE_AI) ? config.savedNonAiMode : MODE_HUMAN;
                        }
                    }
                    config.cpuDepth = selectedDepth[0];
                    String baseUrl = normalizeBaseUrl(urlInput.getText().toString());
                    config.aiBaseUrl = baseUrl.isEmpty() ? DEFAULT_AI_BASE_URL : baseUrl;
                    String model = selectedModel[0] == null ? "" : selectedModel[0].trim();
                    config.aiModel = model.isEmpty() ? DEFAULT_AI_MODEL : model;
                    String prompt = promptInput.getText().toString();
                    config.aiPrompt = prompt.trim().isEmpty()
                            ? buildDefaultAiPromptForPlayer(player)
                            : prompt;
                    try {
                        int t = Integer.parseInt(timeoutInput.getText().toString().trim());
                        config.aiTimeoutSec = t > 0 ? t : 180;
                    } catch (NumberFormatException e) {
                        config.aiTimeoutSec = 180;
                    }
                    try {
                        int r = Integer.parseInt(retriesInput.getText().toString().trim());
                        config.aiMaxRetries = r > 0 ? r : 10;
                    } catch (NumberFormatException e) {
                        config.aiMaxRetries = 10;
                    }
                    resetGame();
                    showStatusMessage(getPlayerLabel(player) + "の設定を保存しました");
                })
                .setNeutralButton("初期化", (dialog, which) -> {
                    PlayerConfig defaults = createDefaultPlayerConfig(player, config.mode);
                    config.cpuDepth = defaults.cpuDepth;
                    config.aiBaseUrl = defaults.aiBaseUrl;
                    config.aiModel = defaults.aiModel;
                    config.aiPrompt = defaults.aiPrompt;
                    config.aiTimeoutSec = defaults.aiTimeoutSec;
                    config.aiMaxRetries = defaults.aiMaxRetries;
                    showStatusMessage(getPlayerLabel(player) + "の設定を初期化しました");
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
                                ? DEFAULT_AI_MODEL : selectedModel[0];
                        modelView.setText("モデル: " + current);
                        showStatusMessage("モデルが見つかりませんでした");
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
                                        ? DEFAULT_AI_MODEL : selectedModel[0];
                                modelView.setText("モデル: " + current);
                            })
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    String current = (selectedModel[0] == null || selectedModel[0].trim().isEmpty())
                            ? DEFAULT_AI_MODEL : selectedModel[0];
                    modelView.setText("モデル: " + current);
                    showStatusMessage("モデル取得に失敗: " + e.getMessage());
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

    private void onHumanMove(int x, int y) {
        if (currentPlayer == 0 || isMoveAnimating) return;
        if (getPlayerConfig(currentPlayer).mode != MODE_HUMAN) return;
        if (!canPlace(x, y, currentPlayer)) {
            showStatusMessage("そこには置けません");
            return;
        }

        gameStarted = true;

        final int movingPlayer = currentPlayer;
        MoveResult moveResult = placeStone(x, y, movingPlayer);
        playMoveAnimation(moveResult, () -> {
            currentPlayer = getOpponent(movingPlayer);
            updateBoardUI();
            handleTurn();
        });
    }

    private void startGame() {
        gameStarted = true;
        updateControlPanel();
        handleTurn();
    }

    private void onGameActionButtonClicked() {
        if (gameStarted) {
            resetGame();
            showStatusMessage("ゲームをリセットしました");
            return;
        }
        startGame();
    }

    private void handleTurn() {
        if (currentPlayer == 0 || isMoveAnimating) {
            return;
        }

        if (!gameStarted) {
            PlayerConfig config = getPlayerConfig(currentPlayer);
            if (config.mode == MODE_HUMAN) {
                setStatusText(getPlayerLabel(currentPlayer) + "は操作するか「対局開始」を押してください");
            } else {
                setStatusText("「対局開始」を押してください");
            }
            return;
        }

        if (!hasValidMove(currentPlayer)) {
            showStatusMessage(getPlayerLabel(currentPlayer) + "は置ける場所がありません（パス）");
            currentPlayer = getOpponent(currentPlayer);
            updateBoardUI();

            if (!hasValidMove(currentPlayer)) {
                showGameResult();
                return;
            }
        }

        updateBoardUI();

        PlayerConfig config = getPlayerConfig(currentPlayer);
        if (config.mode == MODE_CPU) {
            cpuMove(currentPlayer);
        } else if (config.mode == MODE_AI) {
            aiMove(currentPlayer);
        } else {
            setStatusText(getPlayerLabel(currentPlayer) + "の手番です");
        }
    }

    private void cpuMove(int player) {
        PlayerConfig config = getPlayerConfig(player);
        setThinkingStatus(getPlayerLabel(player) + " CPU 思考中...");
        updateBoardUI();

        new Thread(() -> {
            CpuMoveEvaluation best = findBestMoveEvaluation(player, config.cpuDepth);
            runOnUiThread(() -> {
                if (best != null) {
                    MoveResult moveResult = placeStone(best.x, best.y, player);
                    String moveCode = toMoveCode(best.x, best.y);
                    String reason = buildCpuComment(best, player);
                    playMoveAnimation(moveResult, () -> {
                        appendMoveComment(player, "CPU", moveCode, reason);
                        showStatusMessage(getPlayerLabel(player) + " CPU が置きました");
                        currentPlayer = getOpponent(player);
                        updateBoardUI();
                        handleTurn();
                    });
                } else {
                    showStatusMessage(getPlayerLabel(player) + " CPU はパスしました");
                    currentPlayer = getOpponent(player);
                    updateBoardUI();
                    handleTurn();
                }
            });
        }).start();
    }

    private void aiMove(int player) {
        PlayerConfig config = getPlayerConfig(player);
        final int maxRetries = config.aiMaxRetries;
        setThinkingStatus(getPlayerLabel(player) + " AI 思考中... (試行 1/" + maxRetries + ")");
        updateBoardUI();

        final List<String> legalCandidates = getLegalMoveCodes(player);
        if (legalCandidates.isEmpty()) {
            showStatusMessage(getPlayerLabel(player) + " AI はパスしました");
            currentPlayer = getOpponent(player);
            updateBoardUI();
            handleTurn();
            return;
        }

        final String baseUrl = normalizeBaseUrl(config.aiBaseUrl);
        if (baseUrl.isEmpty()) {
            showStatusMessage(getPlayerLabel(player) + " AI のURLを設定してください");
            currentPlayer = getOpponent(player);
            updateBoardUI();
            handleTurn();
            return;
        }

        final String model = (config.aiModel == null || config.aiModel.trim().isEmpty())
                ? DEFAULT_AI_MODEL
                : config.aiModel.trim();
        final String basePrompt = (config.aiPrompt == null || config.aiPrompt.trim().isEmpty())
                ? buildDefaultAiPromptForPlayer(player)
                : config.aiPrompt;
        final String promptWithBoard = buildPromptWithBoardAndCandidates(basePrompt, player, board, legalCandidates);
        final Set<String> legalCandidateSet = new HashSet<>(legalCandidates);

        new Thread(() -> {
            AiMoveSelection selectedMove = null;
            String error = null;
            boolean connectionErrorOccurred = false;

            for (int i = 0; i < maxRetries; i++) {
                final int attempt = i + 1;
                runOnUiThread(() -> setThinkingStatus(
                        getPlayerLabel(player) + " AI 思考中... (試行 " + attempt + "/" + maxRetries + ")"));
                try {
                    String response = requestAiMoveText(baseUrl, model, promptWithBoard, config.aiTimeoutSec * 1000);
                    logAiInteraction(player, promptWithBoard, response, null);
                    connectionErrorOccurred = false;
                    AiMoveSelection parsed = parseAiMove(response);
                    if (parsed == null || parsed.isPass()) {
                        continue;
                    }
                    if (legalCandidateSet.contains(parsed.moveCode) && canPlace(parsed.x, parsed.y, player)) {
                        selectedMove = parsed;
                        break;
                    }
                } catch (AiNetworkException e) {
                    String message = e.getMessage();
                    if (message == null || message.trim().isEmpty()) {
                        message = e.toString();
                    }
                    logAiInteraction(player, promptWithBoard, null, message);
                    error = message;
                    connectionErrorOccurred = true;
                    continue;
                } catch (Exception e) {
                    String message = e.getMessage();
                    if (message == null || message.trim().isEmpty()) {
                        message = e.toString();
                    }
                    logAiInteraction(player, promptWithBoard, null, message);
                    error = message;
                    break;
                }
            }

            final AiMoveSelection resultMove = selectedMove;
            final String resultError = error;
            final boolean isConnectionError = connectionErrorOccurred;

            runOnUiThread(() -> {
                if (resultMove != null) {
                    MoveResult moveResult = placeStone(resultMove.x, resultMove.y, player);
                    playMoveAnimation(moveResult, () -> {
                        appendMoveComment(player, "AI", resultMove.moveCode, resultMove.reason);
                        showStatusMessage(getPlayerLabel(player) + " AI が置きました");
                        currentPlayer = getOpponent(player);
                        updateBoardUI();
                        handleTurn();
                    });
                    return;
                }

                // 試行回数超過 → ゲーム終了
                currentPlayer = 0;
                updateBoardUI();
                if (isConnectionError && resultError != null && !resultError.trim().isEmpty()) {
                    showStatusMessage(getPlayerLabel(player)
                            + " AI接続エラー: " + resultError + "\nAI試行回数超過。ゲーム終了。");
                } else if (isConnectionError) {
                    showStatusMessage(getPlayerLabel(player) + " AI接続エラー。AI試行回数超過。ゲーム終了。");
                } else {
                    showStatusMessage(getPlayerLabel(player) + " AI試行回数超過。ゲーム終了。");
                }
            });
        }).start();
    }

    private String requestAiMoveText(String baseUrl, String model, String prompt, int readTimeoutMs) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(baseUrl + "/api/generate").openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(readTimeoutMs);
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
                throw new AiNetworkException("HTTP " + code + (body.isEmpty() ? "" : ": " + body));
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
        } catch (AiNetworkException e) {
            throw e;
        } catch (Exception e) {
            throw new AiNetworkException(e);
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
        sb.append("  1 2 3 4 5 6 7 8\n");
        for (int y = 0; y < SIZE; y++) {
            sb.append((char) ('A' + y)).append(' ');
            for (int x = 0; x < SIZE; x++) {
                int v = b[y][x];
                if (v == PLAYER_BLACK) sb.append('●');
                else if (v == PLAYER_WHITE) sb.append('○');
                else sb.append('・');
                if (x < SIZE - 1) sb.append(' ');
            }
            if (y < SIZE - 1) sb.append('\n');
        }
        return sb.toString();
    }

    private String buildPromptWithBoardAndCandidates(String basePrompt, int player, int[][] b, List<String> candidates) {
        StringBuilder sb = new StringBuilder(basePrompt);
        sb.append("\n\n担当色:\n");
        sb.append("- あなたは ").append(getPlayerLabel(player))
                .append(player == PLAYER_BLACK ? "（●）" : "（○）")
                .append(" を担当する\n");
        sb.append("- 相手は ").append(getPlayerLabel(getOpponent(player)))
                .append(player == PLAYER_BLACK ? "（○）" : "（●）")
                .append('\n');
        sb.append("\n盤面:\n").append(boardToText(b));
        sb.append("\n\n合法手候補:\n");
        for (int i = 0; i < candidates.size(); i++) {
            sb.append(candidates.get(i));
            if (i < candidates.size() - 1) {
                sb.append('\n');
            }
        }
        sb.append("\n\n2行目では、返せる枚数・相手の合法手数・次に狙える手や角への影響も可能な範囲で入れること。");
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

    private void logAiInteraction(int player, String prompt, String response, String error) {
        if (prompt == null) return;
        synchronized (aiLogs) {
            if (aiLogs.size() >= MAX_AI_LOG_ENTRIES) {
                aiLogs.remove(0);
            }
            aiLogs.add(new AiLogEntry(player, System.currentTimeMillis(), prompt, response, error));
        }
    }

    private void appendMoveComment(int player, String controllerLabel, String moveCode, String reason) {
        if (moveCode == null || moveCode.trim().isEmpty()) return;
        String normalizedReason = reason == null ? "" : reason.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalizedReason.isEmpty()) {
            normalizedReason = "（理由なし）";
        }
        String entry = "[" + getPlayerLabel(player) + " / " + controllerLabel + "] " + moveCode + "\n" + normalizedReason;
        synchronized (moveCommentHistory) {
            moveCommentHistory.add(0, entry);
        }
        updateCommentView();
    }

    private void clearMoveComments() {
        synchronized (moveCommentHistory) {
            moveCommentHistory.clear();
        }
        updateCommentView();
    }

    private void updateCommentView() {
        if (commentView != null) {
            commentView.setText(buildCommentText());
            if (commentScrollView != null) {
                commentScrollView.post(() -> commentScrollView.scrollTo(0, 0));
            }
        }
    }

    private String buildCommentText() {
        synchronized (moveCommentHistory) {
            if (moveCommentHistory.isEmpty()) {
                return "黒と白の自動対戦コメントはまだありません。";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < moveCommentHistory.size(); i++) {
                sb.append(moveCommentHistory.get(i));
                if (i < moveCommentHistory.size() - 1) {
                    sb.append("\n\n");
                }
            }
            return sb.toString();
        }
    }

    private void showAiLogDialog(int player) {
        final TextView content = new TextView(this);
        int pad = dp(12);
        content.setPadding(pad, pad, pad, pad);
        content.setText(buildAiLogText(player));
        applyDarkSelectableTextView(content);
        ScrollView scrollView = new ScrollView(this);
        applyDarkSurface(scrollView);
        scrollView.addView(content);

        new AlertDialog.Builder(this)
                .setTitle(getPlayerLabel(player) + " AI通信ログ")
                .setView(scrollView)
                .setPositiveButton("閉じる", null)
                .setNeutralButton("クリア", (dialog, which) -> {
                    clearAiLogs(player);
                    showAiLogDialog(player);
                })
                .setNegativeButton("コピー", (dialog, which) -> copyAiLogsToClipboard(player))
                .show();
    }

    private String buildAiLogText(int player) {
        List<AiLogEntry> snapshot = new ArrayList<>();
        synchronized (aiLogs) {
            for (AiLogEntry entry : aiLogs) {
                if (entry.player == player) {
                    snapshot.add(entry);
                }
            }
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

    private void clearAiLogs(int player) {
        synchronized (aiLogs) {
            for (int i = aiLogs.size() - 1; i >= 0; i--) {
                if (aiLogs.get(i).player == player) {
                    aiLogs.remove(i);
                }
            }
        }
        showStatusMessage(getPlayerLabel(player) + "のAIログをクリアしました");
    }

    private void copyAiLogsToClipboard(int player) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            showStatusMessage("クリップボードにアクセスできません");
            return;
        }
        ClipData clip = ClipData.newPlainText(getPlayerLabel(player) + " AI通信ログ", buildAiLogText(player));
        clipboard.setPrimaryClip(clip);
        showStatusMessage(getPlayerLabel(player) + "のAIログをコピーしました");
    }

    private CpuMoveEvaluation findBestMoveEvaluation(int player, int depth) {
        CpuMoveEvaluation best = null;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (canPlace(x, y, player)) {
                    int flips = countFlipsOnBoard(board, x, y, player);
                    int[][] copy = cloneBoard(board);
                    placeStoneOnBoard(copy, x, y, player);
                    int score = minimax(copy, getOpponent(player), depth - 1, player);
                    CpuMoveEvaluation candidate = evaluateCpuMove(copy, x, y, player, score, flips);
                    if (best == null || candidate.isBetterThan(best)) {
                        best = candidate;
                    }
                }
            }
        }
        return best;
    }

    private int minimax(int[][] b, int player, int depth, int maximizingPlayer) {
        int opponent = getOpponent(player);

        boolean playerHas = hasValidMoveOnBoard(b, player);
        boolean opponentHas = hasValidMoveOnBoard(b, opponent);

        if (depth <= 0 || (!playerHas && !opponentHas)) {
            return evaluateBoard(b, maximizingPlayer);
        }

        if (!playerHas) {
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
        }

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

    private boolean isCornerSquare(int x, int y) {
        return (x == 0 || x == SIZE - 1) && (y == 0 || y == SIZE - 1);
    }

    private int countPiecesOnBoard(int[][] b, int player) {
        int count = 0;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (b[y][x] == player) count++;
            }
        }
        return count;
    }

    private int countCornersOnBoard(int[][] b, int player) {
        int count = 0;
        if (b[0][0] == player) count++;
        if (b[0][SIZE - 1] == player) count++;
        if (b[SIZE - 1][0] == player) count++;
        if (b[SIZE - 1][SIZE - 1] == player) count++;
        return count;
    }

    private int countLegalMovesOnBoard(int[][] b, int player) {
        int moves = 0;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (canPlaceOnBoard(b, x, y, player)) moves++;
            }
        }
        return moves;
    }

    private int maxFlipsAvailableOnBoard(int[][] b, int player) {
        int best = 0;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (canPlaceOnBoard(b, x, y, player)) {
                    best = Math.max(best, countFlipsOnBoard(b, x, y, player));
                }
            }
        }
        return best;
    }

    private boolean canTakeAnyCornerOnBoard(int[][] b, int player) {
        return canPlaceOnBoard(b, 0, 0, player)
                || canPlaceOnBoard(b, SIZE - 1, 0, player)
                || canPlaceOnBoard(b, 0, SIZE - 1, player)
                || canPlaceOnBoard(b, SIZE - 1, SIZE - 1, player);
    }

    private boolean canOpponentWipeOutPlayerOnBoard(int[][] b, int opponent, int player) {
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (!canPlaceOnBoard(b, x, y, opponent)) continue;
                int[][] next = cloneBoard(b);
                placeStoneOnBoard(next, x, y, opponent);
                if (countPiecesOnBoard(next, player) == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private int estimateFutureMobility(int[][] afterMove, int player) {
        int opponent = getOpponent(player);
        if (!hasValidMoveOnBoard(afterMove, opponent)) {
            return countLegalMovesOnBoard(afterMove, player);
        }
        int worst = Integer.MAX_VALUE;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (!canPlaceOnBoard(afterMove, x, y, opponent)) continue;
                int[][] next = cloneBoard(afterMove);
                placeStoneOnBoard(next, x, y, opponent);
                worst = Math.min(worst, countLegalMovesOnBoard(next, player));
            }
        }
        return worst == Integer.MAX_VALUE ? 0 : worst;
    }

    private int estimateFutureMaxFlips(int[][] afterMove, int player) {
        int opponent = getOpponent(player);
        if (!hasValidMoveOnBoard(afterMove, opponent)) {
            return maxFlipsAvailableOnBoard(afterMove, player);
        }
        int worst = Integer.MAX_VALUE;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (!canPlaceOnBoard(afterMove, x, y, opponent)) continue;
                int[][] next = cloneBoard(afterMove);
                placeStoneOnBoard(next, x, y, opponent);
                worst = Math.min(worst, maxFlipsAvailableOnBoard(next, player));
            }
        }
        return worst == Integer.MAX_VALUE ? 0 : worst;
    }

    private boolean hasCornerThreatOnNextTurn(int[][] afterMove, int player) {
        int opponent = getOpponent(player);
        if (!hasValidMoveOnBoard(afterMove, opponent)) {
            return canTakeAnyCornerOnBoard(afterMove, player);
        }
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (!canPlaceOnBoard(afterMove, x, y, opponent)) continue;
                int[][] next = cloneBoard(afterMove);
                placeStoneOnBoard(next, x, y, opponent);
                if (canTakeAnyCornerOnBoard(next, player)) {
                    return true;
                }
            }
        }
        return false;
    }

    private CpuMoveEvaluation evaluateCpuMove(int[][] afterMove, int moveX, int moveY, int player, int score, int flips) {
        int opponent = getOpponent(player);
        return new CpuMoveEvaluation(
                moveX,
                moveY,
                score,
                flips,
                countPiecesOnBoard(afterMove, player),
                countPiecesOnBoard(afterMove, opponent),
                countCornersOnBoard(afterMove, player),
                countCornersOnBoard(afterMove, opponent),
                countLegalMovesOnBoard(afterMove, opponent),
                estimateFutureMobility(afterMove, player),
                estimateFutureMaxFlips(afterMove, player),
                isCornerSquare(moveX, moveY),
                canTakeAnyCornerOnBoard(afterMove, opponent),
                canOpponentWipeOutPlayerOnBoard(afterMove, opponent, player),
                hasCornerThreatOnNextTurn(afterMove, player));
    }

    private String buildCpuComment(CpuMoveEvaluation evaluation, int player) {
        int opponent = getOpponent(player);
        List<String> reasons = new ArrayList<>();
        reasons.add("今すぐ" + evaluation.flips + "枚返せて、着手後は"
                + getPlayerLabel(player) + "が" + evaluation.playerPieces + "枚、"
                + getPlayerLabel(opponent) + "が" + evaluation.opponentPieces + "枚になる");

        if (evaluation.cornerMove) {
            reasons.add("角を確保できるので以後ひっくり返されにくい");
        } else if (!evaluation.opponentCanTakeCorner) {
            reasons.add("この形なら相手にすぐ角を渡しにくい");
        } else {
            reasons.add("相手に角を渡す危険は残るが、その中では総合評価が最も高い");
        }

        reasons.add("相手の合法手を" + evaluation.opponentMobility + "手に抑えられる");

        if (evaluation.futureMobility > 0) {
            reasons.add("次の自分の番でも最低" + evaluation.futureMobility + "手は残り、最大"
                    + evaluation.futureMaxFlips + "枚返せる筋を保てる");
        } else {
            reasons.add("次の自分の番で苦しくなりやすい局面でも、他候補より粘りやすい");
        }

        if (evaluation.cornerThreatAvailable && !evaluation.cornerMove) {
            reasons.add("将来の角取りも狙える形を残せる");
        }

        if (!evaluation.opponentCanWipeOutPlayer) {
            reasons.add("全滅の危険も避けられる");
        }

        if (evaluation.playerCorners != evaluation.opponentCorners) {
            reasons.add("角の保有数も " + evaluation.playerCorners + " 対 " + evaluation.opponentCorners + " で有利");
        }

        return joinSentences(reasons);
    }

    private String joinSentences(List<String> sentences) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            if (sentence == null || sentence.trim().isEmpty()) continue;
            if (sb.length() > 0) {
                sb.append('。');
            }
            sb.append(sentence.trim().replaceAll("。+$", ""));
        }
        if (sb.length() > 0) {
            sb.append('。');
        }
        return sb.toString();
    }

    private int evaluateBoard(int[][] b, int player) {
        int opponent = getOpponent(player);
        int me = countPiecesOnBoard(b, player);
        int opp = countPiecesOnBoard(b, opponent);
        if (me == 0) return Integer.MIN_VALUE / 4;
        if (opp == 0) return Integer.MAX_VALUE / 4;

        int cornerDiff = countCornersOnBoard(b, player) - countCornersOnBoard(b, opponent);
        int mobilityDiff = countLegalMovesOnBoard(b, player) - countLegalMovesOnBoard(b, opponent);
        int pieceDiff = me - opp;
        return (cornerDiff * CPU_CORNER_SCORE_WEIGHT)
                + (mobilityDiff * CPU_MOBILITY_SCORE_WEIGHT)
                + pieceDiff;
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

        int opponent = getOpponent(player);
        int[] dx = {-1, 0, 1, -1, 1, -1, 0, 1};
        int[] dy = {-1, -1, -1, 0, 0, 1, 1, 1};

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
                } else {
                    break;
                }
                cx += dx[d];
                cy += dy[d];
            }
        }
        return false;
    }

    private int countFlipsOnBoard(int[][] b, int x, int y, int player) {
        if (b[y][x] != 0) return 0;

        int opponent = getOpponent(player);
        int total = 0;
        int[] dx = {-1, 0, 1, -1, 1, -1, 0, 1};
        int[] dy = {-1, -1, -1, 0, 0, 1, 1, 1};

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
                } else {
                    break;
                }
                cx += dx[d];
                cy += dy[d];
            }
        }
        return total;
    }

    private void placeStoneOnBoard(int[][] b, int x, int y, int player) {
        b[y][x] = player;
        int opponent = getOpponent(player);
        int[] dx = {-1, 0, 1, -1, 1, -1, 0, 1};
        int[] dy = {-1, -1, -1, 0, 0, 1, 1, 1};

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
                } else {
                    break;
                }
                cx += dx[d];
                cy += dy[d];
            }
        }
    }

    private boolean hasValidMove(int player) {
        return hasValidMoveOnBoard(board, player);
    }

    private boolean canPlace(int x, int y, int player) {
        return canPlaceOnBoard(board, x, y, player);
    }

    private MoveResult placeStone(int x, int y, int player) {
        MoveResult result = new MoveResult(x, y, player);
        board[y][x] = player;
        int opponent = getOpponent(player);
        int[] dx = {-1, 0, 1, -1, 1, -1, 0, 1};
        int[] dy = {-1, -1, -1, 0, 0, 1, 1, 1};

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
                            result.flipped.add(new CellPosition(rx, ry));
                            rx += dx[d];
                            ry += dy[d];
                        }
                    }
                    break;
                } else {
                    break;
                }
                cx += dx[d];
                cy += dy[d];
            }
        }
        return result;
    }

    private void updateBoardUI() {
        updateControlPanel();
        boolean humanTurn = currentPlayer != 0 && getPlayerConfig(currentPlayer).mode == MODE_HUMAN;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                Button btn = cells[y][x];
                if (btn == null) continue;

                int v = board[y][x];
                btn.setTranslationY(0f);
                btn.setAlpha(1f);
                btn.setRotationY(0f);

                if (v == 0) {
                    btn.setBackground(createBoardCellBackground(0));
                    btn.setEnabled(!isMoveAnimating && humanTurn && canPlace(x, y, currentPlayer));
                } else {
                    btn.setBackground(createBoardCellBackground(v));
                    btn.setEnabled(false);
                }
            }
        }
    }

    private void playMoveAnimation(MoveResult moveResult, Runnable onComplete) {
        if (moveResult == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        isMoveAnimating = true;
        disableBoardInput();

        Button placedCell = cells[moveResult.y][moveResult.x];
        if (placedCell == null) {
            isMoveAnimating = false;
            if (onComplete != null) onComplete.run();
            return;
        }

        float startOffset = -dp(12);
        placedCell.setBackground(createBoardCellBackground(moveResult.player));
        placedCell.setTranslationY(startOffset);
        placedCell.setAlpha(0.45f);

        AnimatorSet placedStoneAnimation = new AnimatorSet();
        placedStoneAnimation.playTogether(
                ObjectAnimator.ofFloat(placedCell, View.TRANSLATION_Y, startOffset, 0f),
                ObjectAnimator.ofFloat(placedCell, View.ALPHA, 0.45f, 1f));
        placedStoneAnimation.setDuration(PLACE_SLIDE_DURATION_MS);
        placedStoneAnimation.setInterpolator(new DecelerateInterpolator());

        List<Animator> animations = new ArrayList<>();
        animations.add(placedStoneAnimation);

        long baseDelay = PLACE_SLIDE_DURATION_MS / 2;
        for (int i = 0; i < moveResult.flipped.size(); i++) {
            CellPosition flippedCell = moveResult.flipped.get(i);
            Button flippedButton = cells[flippedCell.y][flippedCell.x];
            if (flippedButton == null) continue;
            AnimatorSet flipAnimation = createFlipAnimation(flippedButton, moveResult.player);
            flipAnimation.setStartDelay(baseDelay + (i * FLIP_STAGGER_MS));
            animations.add(flipAnimation);
        }

        AnimatorSet allAnimations = new AnimatorSet();
        allAnimations.playTogether(animations);
        allAnimations.addListener(new AnimatorListenerAdapter() {
            private boolean handled;

            @Override
            public void onAnimationEnd(Animator animation) {
                finish();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                finish();
            }

            private void finish() {
                if (handled) return;
                handled = true;
                isMoveAnimating = false;
                if (onComplete != null) {
                    onComplete.run();
                } else {
                    updateBoardUI();
                }
            }
        });
        allAnimations.start();
    }

    private AnimatorSet createFlipAnimation(Button cell, int player) {
        cell.setCameraDistance(getResources().getDisplayMetrics().density * 6000f);

        ObjectAnimator firstHalf = ObjectAnimator.ofFloat(cell, View.ROTATION_Y, 0f, 90f);
        firstHalf.setDuration(FLIP_HALF_DURATION_MS);
        firstHalf.setInterpolator(new DecelerateInterpolator());
        firstHalf.addListener(new AnimatorListenerAdapter() {
            private boolean swapped;

            @Override
            public void onAnimationEnd(Animator animation) {
                swapPieceFace();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                swapPieceFace();
            }

            private void swapPieceFace() {
                if (swapped) return;
                swapped = true;
                cell.setBackground(createBoardCellBackground(player));
                cell.setRotationY(-90f);
            }
        });

        ObjectAnimator secondHalf = ObjectAnimator.ofFloat(cell, View.ROTATION_Y, -90f, 0f);
        secondHalf.setDuration(FLIP_HALF_DURATION_MS);
        secondHalf.setInterpolator(new DecelerateInterpolator());

        AnimatorSet flipAnimation = new AnimatorSet();
        flipAnimation.playSequentially(firstHalf, secondHalf);
        return flipAnimation;
    }

    private void disableBoardInput() {
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (cells[y][x] != null) {
                    cells[y][x].setEnabled(false);
                }
            }
        }
    }

    private Drawable createBoardCellBackground(int stone) {
        GradientDrawable boardLayer = new GradientDrawable();
        boardLayer.setColor(BOARD_EMPTY_COLOR);

        if (stone == 0) {
            return boardLayer;
        }

        GradientDrawable piece = new GradientDrawable();
        piece.setShape(GradientDrawable.OVAL);
        if (stone == PLAYER_BLACK) {
            piece.setColor(Color.BLACK);
            piece.setStroke(dp(2), Color.BLACK);
        } else {
            piece.setColor(Color.WHITE);
            piece.setStroke(dp(2), Color.BLACK);
        }

        LayerDrawable drawable = new LayerDrawable(new Drawable[]{boardLayer, piece});
        int inset = dp(6);
        drawable.setLayerInset(1, inset, inset, inset, inset);
        return drawable;
    }

    private void showGameResult() {
        int black = 0;
        int white = 0;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (board[y][x] == PLAYER_BLACK) black++;
                else if (board[y][x] == PLAYER_WHITE) white++;
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

        currentPlayer = 0;
        updateBoardUI();
        showStatusMessage("ゲーム終了: 黒 " + black + " - 白 " + white + " → " + winner);
    }
}

class AiNetworkException extends Exception {
    AiNetworkException(String message) {
        super(message);
    }

    AiNetworkException(Throwable cause) {
        super(cause);
    }
}
