package com.wallpaperswitcher;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.res.ColorStateList;
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
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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
    private final Map<String, CheckBox> categoryChecks = new HashMap<>();
    private EditText pixabayField, pexelsField, unsplashField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("wallpaper_settings", MODE_PRIVATE);
        executor = Executors.newSingleThreadExecutor();
        buildHome();
        if (prefs.getBoolean("configured", false)) {
            status.setText("Procurando uma nova imagem...");
            setLoading(true);
            changeWallpaper();
        } else {
            showSettings();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdown();
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

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = full();
        progressParams.gravity = Gravity.CENTER;
        root.addView(progress, progressParams);

        Button settings = new Button(this);
        settings.setText("Configurar fontes e categorias");
        settings.setOnClickListener(v -> showSettings());
        root.addView(settings, full());

        TextView note = new TextView(this);
        note.setText("Ao abrir pelo ícone da tela inicial, o aplicativo pesquisa outra imagem e aplica na tela inicial e na tela de bloqueio. A primeira utilização exige configuração.");
        note.setTextSize(12);
        note.setTextColor(0xFFB8C4D8);
        note.setPadding(0, dp(16), 0, 0);
        root.addView(note, full());
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
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
        if (root.getChildCount() > 4) root.removeViews(4, root.getChildCount() - 4);
        status.setText("Escolha as categorias e configure suas chaves de API.");
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

        Button save = new Button(this);
        save.setText("Salvar e trocar papel de parede");
        save.setOnClickListener(v -> {
            saveSettings();
            changeWallpaper();
        });
        root.addView(save, full());

        Button back = new Button(this);
        back.setText("Voltar");
        back.setOnClickListener(v -> buildHome());
        root.addView(back, full());
    }

    private void saveSettings() {
        android.content.SharedPreferences.Editor editor = prefs.edit();
        for (String category : CATEGORIES) {
            CheckBox cb = categoryChecks.get(category);
            editor.putBoolean("cat_" + category, cb != null && cb.isChecked());
        }
        editor.putString("pixabay_key", pixabayField.getText().toString().trim());
        editor.putString("pexels_key", pexelsField.getText().toString().trim());
        editor.putString("unsplash_key", unsplashField.getText().toString().trim());
        editor.putBoolean("configured", true);
        editor.apply();
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
                    status.setText("Não foi possível trocar a imagem: " +
                            (e.getMessage() == null ? "erro desconhecido" : e.getMessage()));
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
