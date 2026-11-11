package com.wallpaperswitcher;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.res.ColorStateList;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String ACTION_CHANGE_WALLPAPER = "com.wallpaperswitcher.ACTION_CHANGE_WALLPAPER";
    private static final String[] CATEGORIES = {
            "Natureza", "Carros", "Espaço", "Abstratos", "Cidades", "Animais",
            "Anime", "Tecnologia", "Arquitetura", "Praias", "Flores", "Motos",
            "Futebol", "Montanhas"
    };

    private android.content.SharedPreferences prefs;
    private ExecutorService executor;
    private LinearLayout root;
    private TextView status;
    private ProgressBar progress;
    private Button changeButton;
    private ImageView loadingIcon;
    private TextView validationStatus;
    private Button saveButton;
    private final Map<String, CheckBox> categoryChecks = new HashMap<>();
    private EditText pixabayField, pexelsField, unsplashField;
    private boolean shortcutMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("wallpaper_settings", MODE_PRIVATE);
        executor = Executors.newSingleThreadExecutor();
        shortcutMode = ACTION_CHANGE_WALLPAPER.equals(getIntent().getAction());
        if (shortcutMode) {
            buildShortcutScreen();
            changeWallpaper();
        } else {
            buildHome();
        }
        if (!shortcutMode && !prefs.getBoolean("configured", false)) {
            status.setText("Configure suas fontes e categorias. Depois, use Mudar papel de parede.");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdown();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (ACTION_CHANGE_WALLPAPER.equals(intent.getAction())) {
            shortcutMode = true;
            buildShortcutScreen();
            changeWallpaper();
        }
    }

    private void buildShortcutScreen() {
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setGravity(Gravity.CENTER);
        screen.setPadding(dp(24), dp(24), dp(24), dp(24));
        screen.setBackgroundColor(0xFF101827);

        status = new TextView(this);
        status.setText("Preparando a troca do papel de parede...");
        status.setTextColor(0xFFFFFFFF);
        status.setTextSize(16);
        status.setGravity(Gravity.CENTER);
        screen.addView(status, full());

        progress = new ProgressBar(this);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(48), dp(48));
        p.gravity = Gravity.CENTER;
        p.topMargin = dp(20);
        screen.addView(progress, p);
        loadingIcon = null;
        changeButton = null;
        setContentView(screen);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams full() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private void buildHome() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        root.setBackgroundColor(0xFF101827);
        scroll.addView(root);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("WALLPAPER SWITCHER");
        title.setTextSize(23);
        title.setTextColor(0xFFFFFFFF);
        title.setGravity(Gravity.CENTER);
        root.addView(title, full());

        TextView subtitle = new TextView(this);
        subtitle.setText("Um toque. Um novo papel de parede.");
        subtitle.setTextSize(14);
        subtitle.setTextColor(0xFFB8C4D8);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(8), 0, dp(18));
        root.addView(subtitle, full());

        status = new TextView(this);
        status.setText("Pronto para trocar seu papel de parede.");
        status.setTextSize(16);
        status.setTextColor(0xFFFFFFFF);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(14), 0, dp(14));
        root.addView(status, full());

        loadingIcon = new ImageView(this);
        loadingIcon.setImageResource(R.drawable.ic_shortcut_change);
        loadingIcon.setVisibility(View.GONE);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(58), dp(58));
        iconParams.gravity = Gravity.CENTER;
        root.addView(loadingIcon, iconParams);

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = full();
        progressParams.gravity = Gravity.CENTER;
        root.addView(progress, progressParams);

        changeButton = new Button(this);
        changeButton.setText("MUDAR PAPEL DE PAREDE");
        changeButton.setOnClickListener(v -> changeWallpaper());
        root.addView(changeButton, full());

        Button settings = new Button(this);
        settings.setText("Configurar fontes e categorias");
        settings.setOnClickListener(v -> showSettings());
        root.addView(settings, full());

        Button shortcut = new Button(this);
        shortcut.setText("Criar atalho: Trocar papel de parede");
        shortcut.setOnClickListener(v -> createChangeShortcut());
        root.addView(shortcut, full());

        TextView note = new TextView(this);
        note.setText("O aplicativo não troca a imagem ao ser aberto nem ao salvar configurações. Use o botão MUDAR PAPEL DE PAREDE ou crie um atalho na tela inicial para executar a troca diretamente.");
        note.setTextSize(12);
        note.setTextColor(0xFFB8C4D8);
        note.setPadding(0, dp(16), 0, 0);
        root.addView(note, full());
    }

    private void setLoading(boolean loading) {
        if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loadingIcon != null) {
            if (loading) {
                loadingIcon.setVisibility(View.VISIBLE);
                RotateAnimation rotate = new RotateAnimation(
                        0f, 360f,
                        Animation.RELATIVE_TO_SELF, 0.5f,
                        Animation.RELATIVE_TO_SELF, 0.5f);
                rotate.setDuration(850);
                rotate.setRepeatCount(Animation.INFINITE);
                rotate.setInterpolator(new LinearInterpolator());
                loadingIcon.startAnimation(rotate);
            } else {
                loadingIcon.clearAnimation();
                loadingIcon.setVisibility(View.GONE);
            }
        }
        if (changeButton != null) {
            changeButton.setEnabled(!loading);
            changeButton.setText(loading ? "ATUALIZANDO PAPEL DE PAREDE..." : "MUDAR PAPEL DE PAREDE");
        }
    }

    private void addSection(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(13);
        label.setTextColor(0xFF53C7A5);
        label.setPadding(0, dp(18), 0, dp(8));
        root.addView(label, full());
    }

    private EditText addKeyField(String hint, String prefKey) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setSingleLine(true);
        field.setText(prefs.getString(prefKey, ""));
        field.setTextColor(0xFFFFFFFF);
        field.setHintTextColor(0xFF9BA9BD);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(field, full());
        return field;
    }

    private void showSettings() {
        root.removeAllViews();

        TextView settingsTitle = new TextView(this);
        settingsTitle.setText("CONFIGURAÇÕES");
        settingsTitle.setTextSize(22);
        settingsTitle.setTextColor(0xFFFFFFFF);
        settingsTitle.setGravity(Gravity.CENTER);
        root.addView(settingsTitle, full());

        status = new TextView(this);
        status.setText("Escolha as categorias e configure suas chaves de API.");
        status.setTextSize(14);
        status.setTextColor(0xFFB8C4D8);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(10), 0, dp(18));
        root.addView(status, full());
        setLoading(false);
        categoryChecks.clear();

        addSection("CATEGORIAS");
        for (String category : CATEGORIES) {
            CheckBox cb = new CheckBox(this);
            cb.setText(category);
            cb.setTextSize(15);
            cb.setTextColor(0xFFFFFFFF);
            cb.setButtonTintList(ColorStateList.valueOf(0xFF53C7A5));
            cb.setChecked(prefs.getBoolean("cat_" + category, "Natureza".equals(category)));
            categoryChecks.put(category, cb);
            root.addView(cb, full());
        }

        addSection("CHAVES DAS FONTES DE IMAGENS");
        pixabayField = addKeyField("Chave API Pixabay", "pixabay_key");
        pexelsField = addKeyField("Chave API Pexels", "pexels_key");
        unsplashField = addKeyField("Access Key Unsplash", "unsplash_key");

        TextView help = new TextView(this);
        help.setText("As APIs oficiais exigem chaves gratuitas. Cadastre suas chaves nos sites Pixabay, Pexels e Unsplash e cole aqui. O aplicativo consulta somente as fontes configuradas.");
        help.setTextSize(12);
        help.setTextColor(0xFFB8C4D8);
        help.setPadding(0, dp(8), 0, dp(12));
        root.addView(help, full());

        validationStatus = new TextView(this);
        validationStatus.setText("Depois de salvar, o aplicativo testará cada chave preenchida e mostrará o resultado aqui.");
        validationStatus.setTextSize(13);
        validationStatus.setTextColor(0xFFB8C4D8);
        validationStatus.setPadding(0, dp(8), 0, dp(12));
        root.addView(validationStatus, full());

        saveButton = new Button(this);
        saveButton.setText("Salvar e testar APIs");
        saveButton.setOnClickListener(v -> {
            if (!saveSettings()) {
                validationStatus.setText("Não foi possível salvar as configurações no aparelho.");
                return;
            }
            saveButton.setEnabled(false);
            validationStatus.setText("Configurações salvas no aparelho. Testando as APIs preenchidas...");
            status.setText("Verificando as chaves das fontes de imagens. Nenhum papel de parede será alterado.");
            setLoading(true);
            validateSavedApis();
        });
        root.addView(saveButton, full());

        Button back = new Button(this);
        back.setText("Voltar");
        back.setOnClickListener(v -> buildHome());
        root.addView(back, full());
    }

    private void createChangeShortcut() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(this, "Atalhos fixados exigem Android 8.0 ou superior.", Toast.LENGTH_LONG).show();
            return;
        }
        ShortcutManager shortcutManager = getSystemService(ShortcutManager.class);
        if (shortcutManager == null || !shortcutManager.isRequestPinShortcutSupported()) {
            Toast.makeText(this, "Seu launcher não permite criar atalhos fixados.", Toast.LENGTH_LONG).show();
            return;
        }

        Intent shortcutIntent = new Intent(this, MainActivity.class);
        shortcutIntent.setAction(ACTION_CHANGE_WALLPAPER);
        shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        ShortcutInfo shortcutInfo = new ShortcutInfo.Builder(this, "change_wallpaper_shortcut")
                .setShortLabel("Trocar papel")
                .setLongLabel("Trocar papel de parede agora")
                .setIcon(Icon.createWithResource(this, R.drawable.ic_shortcut_change))
                .setIntent(shortcutIntent)
                .build();

        shortcutManager.requestPinShortcut(shortcutInfo, null);
        Toast.makeText(this, "Solicitação de atalho enviada. Confirme na tela inicial.", Toast.LENGTH_LONG).show();
    }

    private boolean saveSettings() {
        android.content.SharedPreferences.Editor editor = prefs.edit();
        for (String category : CATEGORIES) {
            CheckBox cb = categoryChecks.get(category);
            editor.putBoolean("cat_" + category, cb != null && cb.isChecked());
        }
        editor.putString("pixabay_key", pixabayField.getText().toString().trim());
        editor.putString("pexels_key", pexelsField.getText().toString().trim());
        editor.putString("unsplash_key", unsplashField.getText().toString().trim());
        editor.putBoolean("configured", true);
        return editor.commit();
    }

    private void validateSavedApis() {
        final String pix = prefs.getString("pixabay_key", "");
        final String pex = prefs.getString("pexels_key", "");
        final String uns = prefs.getString("unsplash_key", "");

        executor.execute(() -> {
            StringBuilder report = new StringBuilder("RESULTADO DOS TESTES\n");
            int configuredCount = 0;
            int successCount = 0;

            if (pix.isEmpty()) {
                report.append("Pixabay: não configurada\n");
            } else {
                configuredCount++;
                try {
                    int count = searchPixabay(pix, "Natureza").size();
                    report.append("Pixabay: CONEXÃO OK — chave aceita ( ").append(count).append(" imagens encontradas )\n");
                    successCount++;
                } catch (Exception e) {
                    report.append("Pixabay: FALHOU — ").append(safeError(e)).append("\n");
                }
            }

            if (pex.isEmpty()) {
                report.append("Pexels: não configurada\n");
            } else {
                configuredCount++;
                try {
                    int count = searchPexels(pex, "Natureza").size();
                    report.append("Pexels: CONEXÃO OK — chave aceita ( ").append(count).append(" imagens encontradas )\n");
                    successCount++;
                } catch (Exception e) {
                    report.append("Pexels: FALHOU — ").append(safeError(e)).append("\n");
                }
            }

            if (uns.isEmpty()) {
                report.append("Unsplash: não configurada\n");
            } else {
                configuredCount++;
                try {
                    int count = searchUnsplash(uns, "Natureza").size();
                    report.append("Unsplash: CONEXÃO OK — chave aceita ( ").append(count).append(" imagens encontradas )\n");
                    successCount++;
                } catch (Exception e) {
                    report.append("Unsplash: FALHOU — ").append(safeError(e)).append("\n");
                }
            }

            if (configuredCount == 0) {
                report.append("Nenhuma chave de API foi preenchida.");
            } else {
                report.append("\nResumo: ").append(successCount).append(" de ").append(configuredCount).append(" APIs responderam corretamente.");
            }

            final String finalReport = report.toString();
            final int finalSuccessCount = successCount;
            final int finalConfiguredCount = configuredCount;
            runOnUiThread(() -> {
                setLoading(false);
                if (validationStatus != null) validationStatus.setText(finalReport);
                if (saveButton != null) saveButton.setEnabled(true);
                status.setText(finalConfiguredCount == 0
                        ? "Configurações salvas, mas nenhuma API foi informada."
                        : "Teste das APIs concluído. Consulte o resultado abaixo das chaves.");
                Toast.makeText(this,
                        finalConfiguredCount > 0 && finalSuccessCount == finalConfiguredCount
                                ? "Configurações salvas. Todas as APIs configuradas responderam!"
                                : "Configurações salvas. Confira o resultado de cada API.",
                        Toast.LENGTH_LONG).show();
            });
        });
    }

    private String safeError(Exception e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) return "erro de conexão ou resposta inválida";
        message = message.replace('\n', ' ').replace('\r', ' ');
        return message.length() > 110 ? message.substring(0, 110) + "..." : message;
    }

    private static class ImageCandidate {
        final String url;
        final String source;
        ImageCandidate(String url, String source) {
            this.url = url;
            this.source = source;
        }
    }

    private void changeWallpaper() {
        final String pix = prefs.getString("pixabay_key", "");
        final String pex = prefs.getString("pexels_key", "");
        final String uns = prefs.getString("unsplash_key", "");

        if (pix.isEmpty() && pex.isEmpty() && uns.isEmpty()) {
            status.setText("Configure pelo menos uma chave de API.");
            showSettings();
            return;
        }

        final List<String> selected = new ArrayList<>();
        for (String category : CATEGORIES) {
            if (prefs.getBoolean("cat_" + category, "Natureza".equals(category))) {
                selected.add(category);
            }
        }
        if (selected.isEmpty()) selected.add("Natureza");
        Collections.shuffle(selected);
        final String category = selected.get(0);

        status.setText("Pesquisando imagens de: " + category);
        setLoading(true);

        executor.execute(() -> {
            try {
                List<ImageCandidate> candidates = new ArrayList<>();
                if (!pix.isEmpty()) {
                    try { candidates.addAll(searchPixabay(pix, category)); } catch (Exception ignored) {}
                }
                if (!pex.isEmpty()) {
                    try { candidates.addAll(searchPexels(pex, category)); } catch (Exception ignored) {}
                }
                if (!uns.isEmpty()) {
                    try { candidates.addAll(searchUnsplash(uns, category)); } catch (Exception ignored) {}
                }
                if (candidates.isEmpty()) {
                    throw new Exception("Nenhuma imagem encontrada. Confira as chaves e a conexão.");
                }
                Collections.shuffle(candidates);
                ImageCandidate chosen = candidates.get(0);
                Bitmap bitmap = downloadBitmap(chosen.url);

                WallpaperManager manager = WallpaperManager.getInstance(this);
                int flags = WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK;
                manager.setBitmap(bitmap, null, true, flags);

                runOnUiThread(() -> {
                    setLoading(false);
                    status.setText("Papel de parede atualizado!\nCategoria: " + category + "\nFonte: " + chosen.source);
                    Toast.makeText(this, "Papel de parede atualizado!", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setLoading(false);
                    String message = "Não foi possível trocar a imagem: " +
                            (e.getMessage() == null ? "erro desconhecido" : e.getMessage());
                    if (shortcutMode) {
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        finish();
                    } else if (status != null) {
                        status.setText(message);
                    }
                });
            }
        });
    }

    private JSONObject getJsonObject(String address, Map<String, String> headers) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestMethod("GET");
        for (Map.Entry<String, String> header : headers.entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
        }
        try {
            int code = connection.getResponseCode();
            java.io.InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            StringBuilder body = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
            }
            if (code < 200 || code >= 300) {
                throw new Exception("API HTTP " + code + ": " + body.substring(0, Math.min(160, body.length())));
            }
            return new JSONObject(body.toString());
        } finally {
            connection.disconnect();
        }
    }

    private List<ImageCandidate> searchPixabay(String key, String category) throws Exception {
        String q = URLEncoder.encode(category, "UTF-8");
        JSONObject json = getJsonObject(
                "https://pixabay.com/api/?key=" + key + "&q=" + q +
                        "&image_type=photo&orientation=vertical&safesearch=true&per_page=40",
                new HashMap<>());
        JSONArray hits = json.getJSONArray("hits");
        List<ImageCandidate> result = new ArrayList<>();
        for (int i = 0; i < hits.length(); i++) {
            String url = hits.getJSONObject(i).optString("largeImageURL");
            if (url.startsWith("https://")) result.add(new ImageCandidate(url, "Pixabay"));
        }
        return result;
    }

    private List<ImageCandidate> searchPexels(String key, String category) throws Exception {
        String q = URLEncoder.encode(category, "UTF-8");
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", key);
        JSONObject json = getJsonObject(
                "https://api.pexels.com/v1/search?query=" + q + "&orientation=portrait&per_page=40",
                headers);
        JSONArray photos = json.getJSONArray("photos");
        List<ImageCandidate> result = new ArrayList<>();
        for (int i = 0; i < photos.length(); i++) {
            JSONObject src = photos.getJSONObject(i).getJSONObject("src");
            String url = src.optString("large2x");
            if (url.startsWith("https://")) result.add(new ImageCandidate(url, "Pexels"));
        }
        return result;
    }

    private List<ImageCandidate> searchUnsplash(String key, String category) throws Exception {
        String q = URLEncoder.encode(category, "UTF-8");
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Client-ID " + key);
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "https://api.unsplash.com/photos/random?query=" + q + "&orientation=portrait&count=10"
        ).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("Authorization", headers.get("Authorization"));
        try {
            int code = connection.getResponseCode();
            java.io.InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            StringBuilder body = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
            }
            if (code < 200 || code >= 300) throw new Exception("Unsplash HTTP " + code);
            JSONArray array = new JSONArray(body.toString());
            List<ImageCandidate> result = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                String url = array.getJSONObject(i).getJSONObject("urls").optString("regular");
                if (url.startsWith("https://")) result.add(new ImageCandidate(url, "Unsplash"));
            }
            return result;
        } finally {
            connection.disconnect();
        }
    }

    private Bitmap downloadBitmap(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(25000);
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("Falha ao baixar imagem.");
            byte[] bytes;
            try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream());
                 java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    if (output.size() > 20 * 1024 * 1024) throw new Exception("Imagem muito grande.");
                }
                bytes = output.toByteArray();
            }
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) throw new Exception("Formato de imagem inválido.");
            return bitmap;
        } finally {
            connection.disconnect();
        }
    }
                      }
