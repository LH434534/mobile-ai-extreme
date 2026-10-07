package com.thiairo.mobileai;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/**
 * Activity unica com navegacao por barra inferior.
 *
 * Uma Activity e substituicao de fragments evita FragmentManager sem
 * dependencias externas e mantem o estado de conversa simples.
 */
public class MainActivity extends Activity {

    private static final int REQ_PERM = 20;
    private static final int REQ_ARQUIVO = 21;

    private Store db;
    private AiCore ai;
    private ModelManager mm;
    private Voice voz;
    private Screens telas;

    private FrameLayout palco;
    private LinearLayout barra;
    private String telaAtual = "home";

    public String arquivoAtual = "";
    public String textoArquivo = "";
    private TextView saidaStream;
    private TextView statusStream;
    private String convStream = "";
    private String perguntaStream = "";
    private boolean gerando = false;

    private static final String[] ABAS = {
            "home", "chat", "models", "tools", "docs", "memory", "settings", "diag"
    };
    private static final String[] ROTULOS = {
            "Início", "Conversa", "Modelos", "Tools", "Docs", "Memória", "Ajustes", "Diag"
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        db = new Store(this);
        aplicarTema();

        ai = new AiCore(this, db);
        mm = new ModelManager(this);
        voz = new Voice(this);
        voz.iniciarTts();

        telas = new Screens(this, db, ai, mm, voz);

        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        raiz.setBackgroundColor(Design.bg);

        palco = new FrameLayout(this);
        palco.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        raiz.addView(palco);

        barra = new LinearLayout(this);
        barra.setOrientation(LinearLayout.HORIZONTAL);
        barra.setBackgroundColor(Design.superficie);
        barra.setPadding(0, Design.dp(this, 4), 0, Design.dp(this, 4));
        for (int i = 0; i < ABAS.length; i++) {
            final String alvo = ABAS[i];
            TextView t = new TextView(this);
            t.setText(ROTULOS[i]);
            t.setTextSize(10.5f);
            t.setGravity(android.view.Gravity.CENTER);
            t.setTextColor(Design.texto3);
            t.setPadding(Design.dp(this, 2), Design.dp(this, 6),
                    Design.dp(this, 2), Design.dp(this, 6));
            t.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { ir(alvo); }
            });
            t.setContentDescription(ROTULOS[i]);
            barra.addView(t);
        }
        raiz.addView(barra);

        setContentView(raiz);

        pedirPermissoes();
        tratarEntrada(getIntent());
        ir("home");
    }

    private void pedirPermissoes() {
        if (Build.VERSION.SDK_INT < 23) return;
        java.util.List<String> falta = new java.util.ArrayList<String>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            falta.add(Manifest.permission.RECORD_AUDIO);
        }
        if (!falta.isEmpty()) {
            requestPermissions(falta.toArray(new String[0]), REQ_PERM);
        }
    }

    /* ------------------------------------------------------------------ *
     * Navegacao
     * ------------------------------------------------------------------ */

    public void ir(String alvo) {
        telaAtual = alvo;
        palco.removeAllViews();
        View v;
        if ("chat".equals(alvo)) v = telas.conversa();
        else if ("models".equals(alvo)) v = telas.modelos();
        else if ("tools".equals(alvo)) v = telas.ferramentas();
        else if ("docs".equals(alvo)) v = telas.documentos();
        else if ("memory".equals(alvo)) v = telas.memoria();
        else if ("settings".equals(alvo)) v = telas.ajustes();
        else if ("diag".equals(alvo)) v = telas.diagnostico();
        else v = telas.inicio();

        palco.addView(v);

        for (int i = 0; i < barra.getChildCount(); i++) {
            TextView t = (TextView) barra.getChildAt(i);
            boolean on = ABAS[i].equals(alvo);
            t.setTextColor(on ? Design.acento : Design.texto3);
            t.setTypeface(null, on ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
    }

    public void recarregar() { ir(telaAtual); }

    public void aplicarTema() {
        int t = Integer.parseInt(db.get("tema", "0"));
        if (t == 3) {
            // seguir sistema
            int ui = getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            t = (ui == android.content.res.Configuration.UI_MODE_NIGHT_YES)
                    ? Design.TEMA_AMOLED : Design.TEMA_CLARO;
        }
        Design.aplicar(t);
        Design.barra(this, Design.bg);
    }

    /* ------------------------------------------------------------------ *
     * Geracao
     * ------------------------------------------------------------------ */

    public void gerar(List<AiCore.Msg> msgs, String pergunta, String conv,
                      TextView saida, TextView status) {
        saidaStream = saida;
        statusStream = status;
        convStream = conv;
        perguntaStream = pergunta;
        gerando = true;

        final AiCore.Decisao d = ai.rotear(AiCore.CAP_TEXTO);
        if (status != null) status.setText(d.provider.nome + " — " + d.motivo);

        final StringBuilder acumulado = new StringBuilder();
        final long[] t0 = {System.currentTimeMillis()};

        d.provider.stream(msgs, new AiCore.Callback() {
            @Override
            public void onToken(String pedaco) {
                if (saidaStream == null) return;
                acumulado.append(pedaco);
                saidaStream.setText(acumulado.toString());
            }

            @Override
            public void onStatus(String estado) {
                if (statusStream != null) statusStream.setText(estado);
            }

            @Override
            public void onDone(String providerId, String modelId, long ms, int tokens) {
                gerando = false;
                String txt = acumulado.toString();
                if (!txt.trim().isEmpty()) {
                    db.mensagem(convStream, "assistant", txt, providerId, modelId, tokens);
                }
                if (statusStream != null) {
                    statusStream.setText(providerId
                            + (modelId.isEmpty() ? "" : " · " + modelId)
                            + " · " + tokens + " tokens"
                            + " · " + String.format(java.util.Locale.US, "%.1f", ms / 1000.0) + " s");
                }
                // aprende apenas o que o usuario escreveu explicitamente como fato?
                // nao: memoria e manual por padrao, para nao gravar sem consentimento
            }

            @Override
            public void onError(String mensagemAmigavel) {
                gerando = false;
                if (statusStream != null) statusStream.setText("Erro: " + mensagemAmigavel);

                // fallback: provedor local e, por fim, motor offline
                AiCore.Decisao alt = ai.rotear(AiCore.CAP_TEXTO);
                if (alt.provider != null && !alt.provider.id.equals("offline")
                        && !alt.provider.id.equals(d.provider.id)) {
                    if (statusStream != null) statusStream.setText("Tentando outra opção…");
                    acumulado.setLength(0);
                    alt.provider.stream(msgs, this);
                    return;
                }
                if (!"offline".equals(d.provider.id)) {
                    if (statusStream != null) statusStream.setText("Usando processamento local.");
                    acumulado.setLength(0);
                    ai.offline.stream(msgs, this);
                    return;
                }
                if (saidaStream != null) saidaStream.setText("Falhou: " + mensagemAmigavel);
            }
        });
    }

    public void pararGeracao() {
        gerando = false;
        if (statusStream != null) statusStream.setText("Interrompido.");
    }

    public void enviarParaChat(String texto) {
        telas.enviar(texto);
    }

    /* ------------------------------------------------------------------ *
     * Arquivos
     * ------------------------------------------------------------------ */

    public void escolherArquivo() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(i, REQ_ARQUIVO);
        } catch (Exception e) {
            Toast.makeText(this, "Nenhum gerenciador de arquivos disponível.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_ARQUIVO && res == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            try {
                int flags = data.getFlags()
                        & (Intent.FLAG_GRANT_READ_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(uri, flags);
            } catch (Exception e) { /* nem todo provider permite */ }

            String nome = nomeDe(uri);
            FileIntel.Info info = FileIntel.analisar(this, uri, nome);
            arquivoAtual = nome + " (" + info.tipo + ", " + Tools.bytes(info.tamanho) + ")";
            textoArquivo = info.textoExtraivel ? info.texto : "";
            ir("docs");
        }
    }

    private String nomeDe(Uri uri) {
        String n = null;
        try {
            android.database.Cursor c = getContentResolver()
                    .query(uri, null, null, null, null);
            if (c != null) {
                int i = c.getColumnIndex(
                        android.provider.OpenableColumns.DISPLAY_NAME);
                if (i >= 0 && c.moveToFirst()) n = c.getString(i);
                c.close();
            }
        } catch (Exception e) { /* segue */ }
        if (n == null) n = uri.getLastPathSegment();
        return n == null ? "arquivo" : n;
    }

    /* ------------------------------------------------------------------ *
     * Voz
     * ------------------------------------------------------------------ */

    public void ouvirVoz(final EditText destino) {
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_PERM);
            return;
        }
        voz.ouvir(new Voice.VozCb() {
            @Override public void onResultado(String texto) {
                destino.setText(texto);
                destino.setSelection(texto.length());
            }
            @Override public void onParcial(String texto) {
                destino.setHint(texto);
            }
            @Override public void onErro(String msg) {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
            @Override public void onFimDeFala() { }
        });
    }

    /* ------------------------------------------------------------------ *
     * Entrada externa (compartilhamento)
     * ------------------------------------------------------------------ */

    private void tratarEntrada(Intent i) {
        if (i == null) return;
        String acao = i.getAction();
        String tipo = i.getType();
        if (Intent.ACTION_SEND.equals(acao) && tipo != null) {
            if ("text/plain".equals(tipo)) {
                String compartilhado = i.getStringExtra(Intent.EXTRA_TEXT);
                if (compartilhado != null && !compartilhado.isEmpty()) {
                    telas.convAtual = java.util.UUID.randomUUID().toString();
                    db.novaConversa(telas.convAtual, "Compartilhado", "");
                    telaAtual = "chat";
                    pendente = compartilhado;
                }
            } else if (tipo.startsWith("image/")) {
                Uri uri = i.getParcelableExtra(Intent.EXTRA_STREAM);
                if (uri != null) {
                    Toast.makeText(this,
                            "Imagem recebida. Análise de imagem depende de um "
                                    + "provedor multimodal configurado.",
                            Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private String pendente = "";

    @Override
    protected void onResume() {
        super.onResume();
        if (!pendente.isEmpty()) {
            String p = pendente;
            pendente = "";
            ir("chat");
            enviarParaChat(p);
        }
    }

    @Override
    protected void onDestroy() {
        voz.liberar();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (!"home".equals(telaAtual)) ir("home");
        else super.onBackPressed();
    }
}
