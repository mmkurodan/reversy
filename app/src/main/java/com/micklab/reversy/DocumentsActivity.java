package com.micklab.reversy;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class DocumentsActivity extends Activity {
    private static final int APP_BACKGROUND_COLOR = 0xFF000000;
    private static final int APP_TEXT_COLOR = 0xFF00FF00;
    private static final int APP_MUTED_TEXT_COLOR = 0xFF66FF66;
    private static final int APP_BORDER_COLOR = 0xFF00AA00;
    private static final int BUTTON_BACKGROUND_COLOR = 0xFF0F4A0F;
    private static final int DOCUMENT_FIELD_COLOR = 0x70000000;

    private TextView documentTitleView;
    private TextView documentContentView;
    private Document[] documents;

    private static class Document {
        final String title;
        final String content;

        Document(String title, String content) {
            this.title = title;
            this.content = content;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawable(new ColorDrawable(APP_BACKGROUND_COLOR));

        documents = buildDocuments();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(APP_BACKGROUND_COLOR);
        int outerPad = dp(12);
        root.setPadding(outerPad, outerPad, outerPad, outerPad);

        TextView headerView = new TextView(this);
        headerView.setText("ドキュメント");
        headerView.setTextColor(APP_TEXT_COLOR);
        headerView.setTextSize(20f);
        root.addView(headerView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView selectorLabelView = new TextView(this);
        selectorLabelView.setText("表示するドキュメント");
        selectorLabelView.setTextColor(APP_MUTED_TEXT_COLOR);
        selectorLabelView.setPadding(0, dp(10), 0, dp(6));
        root.addView(selectorLabelView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        Spinner documentSpinner = new Spinner(this, Spinner.MODE_DROPDOWN);
        String[] titles = new String[documents.length];
        for (int i = 0; i < documents.length; i++) {
            titles[i] = documents[i].title;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, titles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        documentSpinner.setAdapter(adapter);
        root.addView(documentSpinner, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        documentTitleView = new TextView(this);
        documentTitleView.setTextColor(APP_TEXT_COLOR);
        documentTitleView.setTextSize(18f);
        documentTitleView.setPadding(0, dp(12), 0, dp(8));
        root.addView(documentTitleView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView contentScrollView = new ScrollView(this);
        contentScrollView.setFillViewport(true);
        contentScrollView.setBackground(createOutlinedBackground(DOCUMENT_FIELD_COLOR, 1, 10f, APP_BORDER_COLOR));
        LinearLayout.LayoutParams contentScrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f);

        documentContentView = new TextView(this);
        documentContentView.setTextColor(APP_TEXT_COLOR);
        documentContentView.setTextIsSelectable(true);
        documentContentView.setLineSpacing(0f, 1.15f);
        documentContentView.setPadding(dp(12), dp(12), dp(12), dp(12));
        contentScrollView.addView(documentContentView, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(contentScrollView, contentScrollParams);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setPadding(0, dp(10), 0, 0);

        Button copyButton = new Button(this);
        copyButton.setText("コピー");
        applyDarkButton(copyButton);
        copyButton.setOnClickListener(v -> copyCurrentDocument());
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f);
        copyParams.rightMargin = dp(8);
        actionRow.addView(copyButton, copyParams);

        Button backButton = new Button(this);
        backButton.setText("戻る");
        applyDarkButton(backButton);
        backButton.setOnClickListener(v -> finish());
        actionRow.addView(backButton, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));

        root.addView(actionRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        documentSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                showDocument(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        if (documents.length > 0) {
            showDocument(0);
        }

        setContentView(root);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable createOutlinedBackground(int fillColor, int strokeWidthDp, float radiusDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(strokeWidthDp), strokeColor);
        return drawable;
    }

    private void applyDarkButton(Button button) {
        button.setTextColor(APP_TEXT_COLOR);
        button.setBackground(createOutlinedBackground(BUTTON_BACKGROUND_COLOR, 1, 8f, APP_BORDER_COLOR));
    }

    private void showDocument(int index) {
        if (index < 0 || index >= documents.length) {
            return;
        }
        Document document = documents[index];
        documentTitleView.setText(document.title);
        documentContentView.setText(document.content);
    }

    private void copyCurrentDocument() {
        CharSequence title = documentTitleView.getText();
        CharSequence content = documentContentView.getText();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(title, content));
        Toast toast = Toast.makeText(this, "ドキュメントをコピーしました", Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();
    }

    private Document[] buildDocuments() {
        return new Document[] {
                new Document("権限について", buildPermissionsText()),
                new Document("プライバシーポリシー", buildPrivacyText()),
                new Document("操作マニュアル", buildManualText())
        };
    }

    private String buildPermissionsText() {
        StringBuilder sb = new StringBuilder();
        sb.append("権限について\n\n");
        sb.append("本アプリが要求する権限は次の 1 件のみです。\n\n");
        sb.append("1. INTERNET\n");
        sb.append("   - AI モードで設定した API サーバーへ接続するために使用します。\n");
        sb.append("   - 黒/白プレイヤーの AI 設定で URL を指定した場合、その宛先へ盤面情報、合法手候補、プロンプト、AI の応答が送信されることがあります。\n");
        sb.append("   - 初期値は http://127.0.0.1:11434 で、端末内のローカルサーバー向けです。\n\n");
        sb.append("本アプリは以下の権限を要求しません。\n");
        sb.append("- 連絡先\n");
        sb.append("- 位置情報\n");
        sb.append("- カメラ\n");
        sb.append("- マイク\n");
        sb.append("- ストレージ読み書き\n\n");
        sb.append("AI 機能を使わない場合でも権限定義は含まれますが、通信は AI モードで実際に接続したときだけ発生します。");
        return sb.toString();
    }

    private String buildPrivacyText() {
        StringBuilder sb = new StringBuilder();
        sb.append("プライバシーポリシー\n\n");
        sb.append("1. 取得する情報\n");
        sb.append("本アプリは、氏名、メールアドレス、連絡先、位置情報などの個人情報をアプリ内で登録させません。\n\n");
        sb.append("2. 通信について\n");
        sb.append("AI モードを利用した場合のみ、対局中の盤面情報、合法手候補、AI 用プロンプト、AI 応答、設定した接続先 URL に応じた通信が発生します。\n");
        sb.append("接続先が 127.0.0.1 などのローカルホストであれば、通信は端末内に留まります。\n");
        sb.append("接続先を外部サーバーへ変更した場合、そのサーバーの運用者によるデータ取扱いは当該サーバーのポリシーに従います。\n\n");
        sb.append("3. 端末内で扱う情報\n");
        sb.append("黒設定・白設定、AI の応答、対局コメントはアプリ実行中の画面表示に使用されます。\n");
        sb.append("本バージョンでは、これらの内容をユーザーアカウントへ送信したり、広告目的で第三者へ提供したりしません。\n\n");
        sb.append("4. 外部送信・第三者提供\n");
        sb.append("広告 SDK、解析 SDK、クラウド認証機能は含みません。\n");
        sb.append("ただし、ユーザーが AI 接続先として外部サービスを指定した場合、その通信はユーザーの操作によって行われます。\n\n");
        sb.append("5. 改定\n");
        sb.append("プライバシーポリシーは、機能追加や権限変更に応じてアプリ内文書を更新する形で改定されることがあります。");
        return sb.toString();
    }

    private String buildManualText() {
        StringBuilder sb = new StringBuilder();
        sb.append("操作マニュアル\n\n");
        sb.append("1. 対局準備\n");
        sb.append("- 画面上部の「黒」「白」ボタンで、それぞれの操作モードを ユーザ / CPU / AI の順に切り替えます。\n");
        sb.append("- 「黒設定」「白設定」では、CPU の深さや AI 接続先 URL、モデル名、プロンプト、タイムアウト、再試行回数を調整できます。\n\n");
        sb.append("2. 対局開始と進行\n");
        sb.append("- 「対局開始」で新しい対局を開始します。\n");
        sb.append("- ユーザ操作の手番では、盤面の置けるマスをタップして石を置きます。\n");
        sb.append("- 「リセット」で初期配置に戻します。\n\n");
        sb.append("3. コメント欄\n");
        sb.append("- 画面下部のコメント欄には、AI や CPU の着手理由が新しい順で表示されます。\n");
        sb.append("- コメント本文はスクロールできます。\n");
        sb.append("- コメントが無いときは案内文が表示されます。\n\n");
        sb.append("4. AI モード\n");
        sb.append("- AI モードでは、設定した URL の API サーバーへ盤面情報とプロンプトを送信して着手候補を取得します。\n");
        sb.append("- 応答は 1 行目に座標、2 行目に理由を返す形式を想定しています。\n");
        sb.append("- 各設定画面の「AI通信ログ」で、送受信の履歴を確認・コピーできます。\n\n");
        sb.append("5. ドキュメント\n");
        sb.append("- 「ドキュメント」ボタンから、権限説明、プライバシーポリシー、操作マニュアルをいつでも参照できます。\n");
        sb.append("- 必要に応じて文書をコピーできます。\n\n");
        sb.append("6. 注意事項\n");
        sb.append("- 外部 AI サーバーを使う場合は、送信先 URL の安全性と利用規約を確認してください。\n");
        sb.append("- 通信できない場合や応答形式が不正な場合、AI の着手は失敗することがあります。");
        return sb.toString();
    }
}
