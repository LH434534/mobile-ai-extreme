package com.thiairo.mobileai;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Nucleo de IA: provedores, roteador e streaming.
 *
 * Principio: LOCAL > GRATUITO VERIFICADO > FALLBACK LOCAL.
 * Nenhum provedor e obrigatorio. Se tudo cair, o OfflineEngine responde
 * com ferramentas reais em vez de quebrar a tela.
 *
 * Streaming com HttpURLConnection — OkHttp viria de dependencia externa,
 * indisponivel neste build.
 */
public final class AiCore {

    /* ================================================================== *
     * Modelos de dados
     * ================================================================== */

    public static final class Msg {
        public String role;    // system | user | assistant
        public String content;
        public Msg(String r, String c) { role = r; content = c; }
    }

    public interface Callback {
        void onToken(String pedaco);
        void onStatus(String estado);
        void onDone(String providerId, String modelId, long ms, int tokens);
        void onError(String mensagemAmigavel);
    }

    /* ================================================================== *
     * Capacidades
     * ================================================================== */

    public static final int CAP_TEXTO = 1;
    public static final int CAP_VISAO = 2;
    public static final int CAP_AUDIO = 4;
    public static final int CAP_EMBED = 8;

    public static abstract class Provider {
        public String id, nome, baseUrl;
        public boolean requerChave;
        public boolean local;
        public String chave = "";
        public String modelo = "";
        public int caps = CAP_TEXTO;
        public String status = "não verificado";
        public long verificadoEm = 0;
        public long latenciaMs = 0;
        public String ultimoErro = "";

        public abstract void stream(List<Msg> msgs, Callback cb);
        public abstract List<String> modelos() throws Exception;
        public abstract boolean saude() throws Exception;

        public boolean tem(int cap) { return (caps & cap) != 0; }
    }

    /* ================================================================== *
     * Provedor compativel com OpenAI (cobre Groq, OpenRouter, Gemini,
     * Mistral, LM Studio, llama.cpp, vLLM, KoboldCpp, Pollinations)
     * ================================================================== */

    public static class OpenAICompat extends Provider {
        public OpenAICompat(String id, String nome, String base, boolean chave,
                            boolean local, int caps) {
            this.id = id; this.nome = nome; this.baseUrl = base;
            this.requerChave = chave; this.local = local; this.caps = caps;
        }

        private String chatUrl() {
            String b = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
            return b + "chat/completions";
        }

        private String modelsUrl() {
            String b = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
            return b + "models";
        }

        @Override
        public void stream(final List<Msg> msgs, final Callback cb) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    HttpURLConnection c = null;
                    try {
                        postar(cb, "Conectando…");
                        URL u = new URL(chatUrl());
                        c = (HttpURLConnection) u.openConnection();
                        c.setRequestMethod("POST");
                        c.setRequestProperty("Content-Type", "application/json");
                        if (!chave.isEmpty()) {
                            c.setRequestProperty("Authorization", "Bearer " + chave);
                        }
                        c.setConnectTimeout(6000);
                        c.setReadTimeout(60000);
                        c.setDoOutput(true);

                        String corpo = jsonCorpo(msgs, modelo, true);
                        OutputStream os = c.getOutputStream();
                        os.write(corpo.getBytes("UTF-8"));
                        os.close();

                        int code = c.getResponseCode();
                        if (code >= 400) {
                            String err = Tools.ler(c.getErrorStream());
                            throw new java.io.IOException(
                                    mensagemDe(code, err));
                        }

                        postar(cb, "Gerando…");
                        long t0 = System.currentTimeMillis();
                        final StringBuilder acc = new StringBuilder();
                        final int[] ntok = {0};

                        BufferedReader r = new BufferedReader(
                                new InputStreamReader(c.getInputStream(), "UTF-8"));
                        String linha;
                        while ((linha = r.readLine()) != null) {
                            if (!linha.startsWith("data:")) continue;
                            String d = linha.substring(5).trim();
                            if (d.isEmpty()) continue;
                            if (d.equals("[DONE]")) break;
                            String pedaco = extrairDelta(d);
                            if (pedaco != null && !pedaco.isEmpty()) {
                                acc.append(pedaco);
                                ntok[0]++;
                                final String p = pedaco;
                                post(new Runnable() {
                                    @Override public void run() { cb.onToken(p); }
                                });
                            }
                        }
                        r.close();

                        final long ms = System.currentTimeMillis() - t0;
                        final int nt = ntok[0];
                        post(new Runnable() {
                            @Override public void run() {
                                cb.onDone(id, modelo, ms, nt);
                            }
                        });
                    } catch (final Exception e) {
                        ultimoErro = String.valueOf(e.getMessage());
                        post(new Runnable() {
                            @Override public void run() {
                                cb.onError(ultimoErro);
                            }
                        });
                    } finally {
                        if (c != null) c.disconnect();
                    }
                }
            }).start();
        }

        private String extrairDelta(String json) {
            try {
                org.json.JSONObject o = new org.json.JSONObject(json);
                org.json.JSONArray escolhas = o.optJSONArray("choices");
                if (escolhas == null || escolhas.length() == 0) return null;
                org.json.JSONObject ch = escolhas.getJSONObject(0);
                org.json.JSONObject delta = ch.optJSONObject("delta");
                if (delta != null) return delta.optString("content", null);
                org.json.JSONObject msg = ch.optJSONObject("message");
                if (msg != null) return msg.optString("content", null);
                return null;
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public List<String> modelos() throws Exception {
            List<String> out = new ArrayList<String>();
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(modelsUrl()).openConnection();
                c.setRequestProperty("Authorization", "Bearer " + chave);
                c.setConnectTimeout(5000);
                c.setReadTimeout(8000);
                int code = c.getResponseCode();
                if (code >= 400) throw new java.io.IOException("HTTP " + code);
                String s = Tools.ler(c.getInputStream());
                org.json.JSONObject o = new org.json.JSONObject(s);
                org.json.JSONArray d = o.optJSONArray("data");
                if (d == null) return out;
                for (int i = 0; i < d.length(); i++) {
                    String m = d.getJSONObject(i).optString("id", "");
                    if (!m.isEmpty()) out.add(m);
                }
            } finally {
                if (c != null) c.disconnect();
            }
            return out;
        }

        @Override
        public boolean saude() throws Exception {
            long t0 = System.currentTimeMillis();
            try {
                modelos();
                latenciaMs = System.currentTimeMillis() - t0;
                status = "disponível";
                verificadoEm = System.currentTimeMillis();
                return true;
            } catch (Exception e) {
                status = "indisponível";
                ultimoErro = String.valueOf(e.getMessage());
                return false;
            }
        }
    }

    /* ================================================================== *
     * Ollama (protocolo proprio, NDJSON)
     * ================================================================== */

    public static final class Ollama extends Provider {

        public Ollama(String base) {
            this.id = "ollama"; this.nome = "Ollama (local)"; this.baseUrl = base;
            this.requerChave = false; this.local = true; this.caps = CAP_TEXTO;
        }

        @Override
        public void stream(final List<Msg> msgs, final Callback cb) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    HttpURLConnection c = null;
                    try {
                        postar(cb, "Conectando ao servidor local…");
                        c = (HttpURLConnection) new URL(baseUrl + "/api/chat").openConnection();
                        c.setRequestMethod("POST");
                        c.setRequestProperty("Content-Type", "application/json");
                        c.setConnectTimeout(4000);
                        c.setReadTimeout(120000);
                        c.setDoOutput(true);

                        org.json.JSONObject corpo = new org.json.JSONObject();
                        corpo.put("model", modelo);
                        corpo.put("stream", true);
                        org.json.JSONArray arr = new org.json.JSONArray();
                        for (Msg m : msgs) {
                            org.json.JSONObject o = new org.json.JSONObject();
                            o.put("role", m.role);
                            o.put("content", m.content);
                            arr.put(o);
                        }
                        corpo.put("messages", arr);

                        OutputStream os = c.getOutputStream();
                        os.write(corpo.toString().getBytes("UTF-8"));
                        os.close();

                        if (c.getResponseCode() >= 400) {
                            throw new java.io.IOException(
                                    "Servidor local respondeu " + c.getResponseCode());
                        }

                        postar(cb, "Gerando…");
                        long t0 = System.currentTimeMillis();
                        final int[] ntok = {0};

                        BufferedReader r = new BufferedReader(
                                new InputStreamReader(c.getInputStream(), "UTF-8"));
                        String linha;
                        while ((linha = r.readLine()) != null) {
                            if (linha.trim().isEmpty()) continue;
                            org.json.JSONObject o = new org.json.JSONObject(linha);
                            org.json.JSONObject msg = o.optJSONObject("message");
                            if (msg != null) {
                                final String p = msg.optString("content", "");
                                if (!p.isEmpty()) {
                                    ntok[0]++;
                                    post(new Runnable() {
                                        @Override public void run() { cb.onToken(p); }
                                    });
                                }
                            }
                            if (o.optBoolean("done", false)) break;
                        }
                        r.close();

                        final long ms = System.currentTimeMillis() - t0;
                        final int nt = ntok[0];
                        post(new Runnable() {
                            @Override public void run() { cb.onDone(id, modelo, ms, nt); }
                        });
                    } catch (final Exception e) {
                        ultimoErro = String.valueOf(e.getMessage());
                        post(new Runnable() {
                            @Override public void run() { cb.onError(ultimoErro); }
                        });
                    } finally {
                        if (c != null) c.disconnect();
                    }
                }
            }).start();
        }

        @Override
        public List<String> modelos() throws Exception {
            List<String> out = new ArrayList<String>();
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(baseUrl + "/api/tags").openConnection();
                c.setConnectTimeout(4000);
                c.setReadTimeout(8000);
                if (c.getResponseCode() >= 400) throw new java.io.IOException("HTTP " + c.getResponseCode());
                org.json.JSONObject o = new org.json.JSONObject(Tools.ler(c.getInputStream()));
                org.json.JSONArray ms = o.optJSONArray("models");
                if (ms == null) return out;
                for (int i = 0; i < ms.length(); i++) {
                    String n = ms.getJSONObject(i).optString("name", "");
                    if (n.isEmpty()) n = ms.getJSONObject(i).optString("model", "");
                    if (!n.isEmpty()) out.add(n);
                }
            } finally {
                if (c != null) c.disconnect();
            }
            return out;
        }

        @Override
        public boolean saude() throws Exception {
            long t0 = System.currentTimeMillis();
            try {
                modelos();
                latenciaMs = System.currentTimeMillis() - t0;
                status = "disponível";
                verificadoEm = System.currentTimeMillis();
                return true;
            } catch (Exception e) {
                status = "indisponível";
                ultimoErro = String.valueOf(e.getMessage());
                return false;
            }
        }
    }

    /* ================================================================== *
     * Motor offline — deterministico, sem modelo e sem rede
     * ================================================================== */

    public static final class OfflineEngine extends Provider {
        public OfflineEngine() {
            this.id = "offline";
            this.nome = "Motor local offline";
            this.local = true;
            this.requerChave = false;
            this.caps = CAP_TEXTO;
            this.status = "sempre disponível";
        }

        @Override
        public void stream(final List<Msg> msgs, final Callback cb) {
            final String pergunta = ultima(msgs);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        postar(cb, "Processando localmente…");
                        Thread.sleep(160); // evita flash; nao e latencia artificial de rede
                        final String r = responder(pergunta);
                        final int n = r.split("\\s+").length;
                        post(new Runnable() {
                            @Override public void run() {
                                cb.onToken(r);
                                cb.onDone(id, "regras+ferramentas", 0, n);
                            }
                        });
                    } catch (final Exception e) {
                        post(new Runnable() {
                            @Override public void run() { cb.onError(String.valueOf(e.getMessage())); }
                        });
                    }
                }
            }).start();
        }

        private String ultima(List<Msg> msgs) {
            for (int i = msgs.size() - 1; i >= 0; i--) {
                if ("user".equals(msgs.get(i).role)) return msgs.get(i).content;
            }
            return "";
        }

        /** Respostas reais: calculo, data, transformacoes de texto. */
        private String responder(String p) {
            String q = p.trim();

            // calculadora: expressao com digitos e operadores
            if (q.matches(".*\\d.*[+\\-*/^%].*\\d.*") && q.length() < 120) {
                String r = Tools.calcular(q);
                if (r.startsWith("= ")) return "Calculado localmente.\n\n" + r
                        + "\n\n_Sem rede e sem modelo — aritmética precisa._";
            }
            if (q.toLowerCase().contains("que horas") || q.toLowerCase().contains("data de hoje")
                    || q.toLowerCase().contains("dia é hoje")) {
                return "Data e hora do sistema:\n\n" + Tools.agoraCompleto()
                        + "\n\n_Ferramenta local._";
            }

            return "Não há modelo de linguagem disponível agora.\n\n"
                    + "Este motor offline resolve o que é determinístico: conta, "
                    + "data, transformações de texto, JSON, hash e ferramentas "
                    + "em **Ferramentas**.\n\n"
                    + "Para respostas de linguagem, em **Modelos** conecte um "
                    + "servidor local (Ollama, LM Studio, llama.cpp) ou configure "
                    + "um provedor gratuito em **Ajustes → Provedores**.";
        }

        @Override
        public List<String> modelos() {
            List<String> o = new ArrayList<String>();
            o.add("regras+ferramentas");
            return o;
        }

        @Override
        public boolean saude() {
            status = "sempre disponível";
            return true;
        }
    }

    /* ================================================================== *
     * Registro e roteador
     * ================================================================== */

    private final Context ctx;
    private final Store db;
    public final List<Provider> provedores = new ArrayList<Provider>();
    public final OfflineEngine offline = new OfflineEngine();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private static final Handler UI = new Handler(Looper.getMainLooper());

    public AiCore(Context c, Store db) {
        this.ctx = c.getApplicationContext();
        this.db = db;
        registroPadrao();
        carregarChaves();
    }

    /** Endpoints publicos e documentados. Nenhum e inventado. */
    private void registroPadrao() {
        provedores.add(new Ollama("http://localhost:11434"));
        provedores.add(new OpenAICompat("lmstudio", "LM Studio (local)",
                "http://localhost:1234/v1", false, true, CAP_TEXTO));
        provedores.add(new OpenAICompat("llamacpp", "llama.cpp (local)",
                "http://localhost:8080/v1", false, true, CAP_TEXTO));
        provedores.add(new OpenAICompat("kobold", "KoboldCpp (local)",
                "http://localhost:5001/v1", false, true, CAP_TEXTO));
        provedores.add(new OpenAICompat("vllm", "vLLM (local)",
                "http://localhost:8000/v1", false, true, CAP_TEXTO));
        provedores.add(new OpenAICompat("groq", "Groq (free tier)",
                "https://api.groq.com/openai/v1", true, false, CAP_TEXTO));
        provedores.add(new OpenAICompat("openrouter", "OpenRouter (free tier)",
                "https://openrouter.ai/api/v1", true, false, CAP_TEXTO));
        provedores.add(new OpenAICompat("gemini", "Gemini (free tier)",
                "https://generativelanguage.googleapis.com/v1beta/openai",
                true, false, CAP_TEXTO | CAP_VISAO));
        provedores.add(new OpenAICompat("mistral", "Mistral (free tier)",
                "https://api.mistral.ai/v1", true, false, CAP_TEXTO));
        provedores.add(new OpenAICompat("pollinations", "Pollinations (sem chave)",
                "https://text.pollinations.ai/openai", false, false, CAP_TEXTO));
    }

    private void carregarChaves() {
        for (Provider p : provedores) {
            p.chave = db.get("chave." + p.id, "");
            p.modelo = db.get("modelo." + p.id, "");
            String st = db.get("status." + p.id, "");
            if (!st.isEmpty()) p.status = st;
        }
    }

    public void salvarChave(String id, String chave) {
        db.set("chave." + id, chave);
        for (Provider p : provedores) if (p.id.equals(id)) p.chave = chave;
    }

    public void salvarModelo(String id, String modelo) {
        db.set("modelo." + id, modelo);
        for (Provider p : provedores) if (p.id.equals(id)) p.modelo = modelo;
    }

    public Provider porId(String id) {
        for (Provider p : provedores) if (p.id.equals(id)) return p;
        return null;
    }

    /* ------------------------------------------------------------------ *
     * Estado do ambiente
     * ------------------------------------------------------------------ */

    public boolean online() {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkInfo n = cm.getActiveNetworkInfo();
            return n != null && n.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    public String modoPrivacidade() {
        return db.get("privacidade", "auto"); // auto | local | rede | perguntar
    }

    /* ------------------------------------------------------------------ *
     * Roteador
     * ------------------------------------------------------------------ */

    public static final class Decisao {
        public Provider provider;
        public String motivo;
    }

    /**
     * Escolhe o motor: capacidade, rede, privacidade e disponibilidade.
     * Nunca retorna nulo — o OfflineEngine e o ultimo recurso.
     */
    public Decisao rotear(int capacidade) {
        Decisao d = new Decisao();
        boolean net = online();
        String modo = modoPrivacidade();

        String preferido = db.get("provider.preferido", "");
        if (!preferido.isEmpty() && !modo.equals("local")) {
            Provider p = porId(preferido);
            if (p != null && p.tem(capacidade) && (!p.requerChave || !p.chave.isEmpty())) {
                if (p.local || net) {
                    d.provider = p;
                    d.motivo = "Provedor preferido configurado.";
                    return d;
                }
            }
        }

        // 1. local sempre primeiro (privado, funciona sem rede)
        for (Provider p : provedores) {
            if (p.local && p.tem(capacidade)
                    && (!p.requerChave || !p.chave.isEmpty())
                    && !p.modelo.isEmpty()) {
                d.provider = p;
                d.motivo = "Servidor local — privado e sem custo.";
                return d;
            }
        }

        // 2. rede, se permitido
        if (net && !modo.equals("local")) {
            for (Provider p : provedores) {
                if (p.local) continue;
                if (!p.tem(capacidade)) continue;
                if (p.requerChave && p.chave.isEmpty()) continue;
                d.provider = p;
                d.motivo = "Provedor gratuito configurado.";
                return d;
            }
        }

        // 3. fallback
        d.provider = offline;
        d.motivo = net
                ? "Nenhum provedor configurado. Usando motor local."
                : "Sem rede. Usando motor local.";
        return d;
    }

    /* ------------------------------------------------------------------ *
     * Health check sob demanda (nada de chamada constante)
     * ------------------------------------------------------------------ */

    public interface HealthCb {
        void onFim();
    }

    public void verificarTodos(final HealthCb cb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (Provider p : provedores) {
                    if (p.requerChave && p.chave.isEmpty()) {
                        p.status = "chave não configurada";
                        continue;
                    }
                    try { p.saude(); }
                    catch (Exception e) { p.status = "indisponível"; }
                    db.set("status." + p.id, p.status);
                }
                post(new Runnable() {
                    @Override public void run() { if (cb != null) cb.onFim(); }
                });
            }
        }).start();
    }

    /* ------------------------------------------------------------------ *
     * Utilidades
     * ------------------------------------------------------------------ */

    public static String jsonCorpo(List<Msg> msgs, String modelo, boolean stream) {
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("stream", stream);
            if (modelo != null && !modelo.isEmpty()) o.put("model", modelo);
            org.json.JSONArray arr = new org.json.JSONArray();
            for (Msg m : msgs) {
                org.json.JSONObject x = new org.json.JSONObject();
                x.put("role", m.role);
                x.put("content", m.content);
                arr.put(x);
            }
            o.put("messages", arr);
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    public static String mensagemDe(int code, String corpo) {
        switch (code) {
            case 401:
            case 403:
                return "Credencial recusada. Confira a chave em Ajustes → Provedores.";
            case 404:
                return "Endpoint não encontrado neste provedor.";
            case 429:
                return "Este serviço atingiu o limite temporário. Vou tentar outra opção gratuita ou usar o processamento local.";
            case 500:
            case 502:
            case 503:
                return "Serviço indisponível no momento. Tentando outra opção.";
            default:
                String c = corpo == null ? "" : corpo;
                if (c.length() > 120) c = c.substring(0, 120);
                return "Erro HTTP " + code + (c.isEmpty() ? "" : " — " + c);
        }
    }

    private void main(Runnable r) { ui.post(r); }

    /** Postagem estatica: usada dentro de classes aninhadas static. */
    static void post(Runnable r) { UI.post(r); }

    /** Status sempre na thread principal: o callback toca a UI. */
    private static void postar(final Callback cb, final String estado) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() { cb.onStatus(estado); }
        });
    }

    public Store db() { return db; }
}
