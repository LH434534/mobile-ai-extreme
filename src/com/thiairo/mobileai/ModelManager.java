package com.thiairo.mobileai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Gerenciador de modelos.
 *
 * Regras que este modulo respeita:
 * - nunca baixa sem consentimento explicito (a UI mostra tamanho antes);
 * - valida tamanho e hash apos o download;
 * - grava em arquivo temporario e so promove ao final;
 * - download interrompido nao vira arquivo corrompido.
 *
 * Observacao honesta: baixar e verificar e tudo que este modulo faz.
 * Executar GGUF exige um runtime nativo (llama.cpp/ONNX/MNN) que nao pode
 * ser compilado neste ambiente sem NDK. A execucao local acontece via
 * servidor local (Ollama/LM Studio), que e privado e funciona offline.
 */
public final class ModelManager {

    public interface Progresso {
        void onProgresso(long feito, long total, int pct);
        void onFim(File arquivo, String sha256);
        void onErro(String msg);
    }

    private final Context ctx;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<String> cancelados = new ArrayList<String>();

    public ModelManager(Context c) { ctx = c.getApplicationContext(); }

    public File pasta() {
        File d = new File(ctx.getFilesDir(), "models");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public long espacoLivre() {
        try {
            return ctx.getFilesDir().getUsableSpace();
        } catch (Exception e) {
            return -1;
        }
    }

    public void cancelar(String id) {
        synchronized (cancelados) { if (!cancelados.contains(id)) cancelados.add(id); }
    }

    private boolean cancelado(String id) {
        synchronized (cancelados) { return cancelados.contains(id); }
    }

    /** Consulta o tamanho remoto antes de pedir consentimento. */
    public void consultar(final String url, final TamanhoCb cb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection c = null;
                try {
                    c = (HttpURLConnection) new URL(url).openConnection();
                    c.setRequestMethod("HEAD");
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    int code = c.getResponseCode();
                    final long tam = code < 400 ? tamanhoRemoto(c) : -1;
                    final String tipo = c.getContentType();
                    final int cd = code;
                    ui.post(new Runnable() {
                        @Override public void run() { cb.onFim(cd, tam, tipo); }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        @Override public void run() { cb.onFim(-1, -1, e.getMessage()); }
                    });
                } finally {
                    if (c != null) c.disconnect();
                }
            }
        }).start();
    }

    public interface TamanhoCb {
        void onFim(int code, long tamanho, String tipo);
    }

    private long tamanhoRemoto(HttpURLConnection c) {
        String v = c.getHeaderField("Content-Length");
        if (v != null) {
            try { return Long.parseLong(v.trim()); } catch (Exception e) { /* segue */ }
        }
        return -1;
    }

    /**
     * Download com retomada.
     * Usa Range quando o arquivo parcial ja existe.
     */
    public void baixar(final String id, final String url, final String nomeArquivo,
                       final Progresso cb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection c = null;
                File tmp = null;
                try {
                    File destino = new File(pasta(), nomeArquivo);
                    tmp = new File(pasta(), nomeArquivo + ".part");

                    long jaTem = tmp.exists() ? tmp.length() : 0;

                    c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(30000);
                    if (jaTem > 0) c.setRequestProperty("Range", "bytes=" + jaTem + "-");

                    int code = c.getResponseCode();
                    boolean parcial = code == 206;
                    if (code != 200 && code != 206) {
                        final int cd = code;
                        ui.post(new Runnable() {
                            @Override public void run() {
                                cb.onErro("Servidor respondeu HTTP " + cd);
                            }
                        });
                        return;
                    }

                    long total = tamanhoRemoto(c);
                    if (total < 0) total = -1;
                    long totalAbsoluto = total >= 0 ? total + (parcial ? jaTem : 0) : -1;

                    if (!parcial && jaTem > 0) {
                        tmp.delete();            // servidor nao aceita Range
                        jaTem = 0;
                    }

                    long livre = espacoLivre();
                    if (totalAbsoluto > 0 && livre > 0 && totalAbsoluto > livre) {
                        ui.post(new Runnable() {
                            @Override public void run() {
                                cb.onErro("Espaço insuficiente. Cancelado antes de começar.");
                            }
                        });
                        return;
                    }

                    InputStream in = c.getInputStream();
                    RandomAccessFile out = new RandomAccessFile(tmp, "rw");
                    out.seek(jaTem);

                    byte[] buf = new byte[65536];
                    int n;
                    long feito = jaTem;
                    long ultimoAviso = 0;

                    while ((n = in.read(buf)) > 0) {
                        if (cancelado(id)) {
                            out.close(); in.close();
                            ui.post(new Runnable() {
                                @Override public void run() { cb.onErro("Cancelado."); }
                            });
                            synchronized (cancelados) { cancelados.remove(id); }
                            return;
                        }
                        out.write(buf, 0, n);
                        feito += n;

                        long agora = System.currentTimeMillis();
                        if (agora - ultimoAviso > 120) {
                            ultimoAviso = agora;
                            final long f = feito, t = totalAbsoluto;
                            final int pct = t > 0 ? (int) (f * 100 / t) : -1;
                            ui.post(new Runnable() {
                                @Override public void run() { cb.onProgresso(f, t, pct); }
                            });
                        }
                    }
                    out.close();
                    in.close();

                    // valida tamanho quando conhecido
                    if (totalAbsoluto > 0 && tmp.length() != totalAbsoluto) {
                        ui.post(new Runnable() {
                            @Override public void run() {
                                cb.onErro("Download incompleto. Tente continuar.");
                            }
                        });
                        return;
                    }

                    // promove de .part para o nome final
                    if (destino.exists()) destino.delete();
                    if (!tmp.renameTo(destino)) {
                        ui.post(new Runnable() {
                            @Override public void run() {
                                cb.onErro("Falha ao finalizar o arquivo.");
                            }
                        });
                        return;
                    }

                    final String sha = sha256(destino);
                    final File fim = destino;
                    ui.post(new Runnable() {
                        @Override public void run() { cb.onFim(fim, sha); }
                    });

                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        @Override public void run() {
                            cb.onErro("Falha: " + e.getMessage());
                        }
                    });
                } finally {
                    if (c != null) c.disconnect();
                }
            }
        }).start();
    }

    public static String sha256(File f) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            InputStream in = new java.io.FileInputStream(f);
            byte[] buf = new byte[131072];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            in.close();
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static long tamanhoPasta(File d) {
        long t = 0;
        File[] fs = d.listFiles();
        if (fs == null) return 0;
        for (File f : fs) if (f.isFile()) t += f.length();
        return t;
    }
}
