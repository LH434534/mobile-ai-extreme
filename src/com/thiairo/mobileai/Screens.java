package com.thiairo.mobileai;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Telas. Construidas em codigo: sem XML de layout, porque o tema muda em
 * tempo de execucao e reconstruir a arvore e mais confiavel do que
 * recolorir cada view.
 */
public final class Screens {

    private final MainActivity a;
    private final Store db;
    private final AiCore ai;
    private final ModelManager mm;
    private final Voice voz;

    public String convAtual = "";
    private TextView saidaChat;
    private TextView statusChat;
    private TextView badgeChat;
    private LinearLayout listaChat;
    private ScrollView scrollChat;

    public Screens(MainActivity act, Store db, AiCore ai, ModelManager mm, Voice voz) {
        this.a = act; this.db = db; this.ai = ai; this.mm = mm; this.voz = voz;
    }

    /* ================================================================== *
     * Utilidades de UI
     * ================================================================== */

    private LinearLayout coluna() {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return l;
    }

    private ScrollView pagina(LinearLayout conteudo) {
        ScrollView s = new ScrollView(a);
        s.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        s.addView(conteudo);
        s.setPadding(Design.dp(a, 16), Design.dp(a, 12), Design.dp(a, 16), Design.dp(a, 24));
        return s;
    }

    private EditText campo(String dica) {
        EditText e = new EditText(a);
        e.setHint(dica);
        e.setHintTextColor(Design.texto3);
        e.setTextColor(Design.texto1);
        e.setTextSize(14);
        e.setBackground(null);
        e.setPadding(Design.dp(a, 12), Design.dp(a, 10), Design.dp(a, 12), Design.dp(a, 10));
        Design.fundo(e, Design.superficie, Design.dp(a, 12), Design.linha);
        return e;
    }

    private TextView resultado(String inicial) {
        TextView t = new TextView(a);
        t.setText(inicial);
        t.setTextSize(13);
        t.setTextColor(Design.texto1);
        t.setTypeface(Typeface.MONOSPACE);
        t.setPadding(Design.dp(a, 12), Design.dp(a, 12), Design.dp(a, 12), Design.dp(a, 12));
        Design.fundo(t, Design.bg, Design.dp(a, 12), Design.linhaSuave);
        t.setTextIsSelectable(true);
        return t;
    }

    private void aviso(String m) {
        Toast.makeText(a, m, Toast.LENGTH_SHORT).show();
    }

    /* ================================================================== *
     * INICIO
     * ================================================================== */

    public View inicio() {
        LinearLayout l = coluna();
        int p = Design.dp(a, 16);
        l.setPadding(p, p, p, p);

        TextView marca = new TextView(a);
        marca.setText("MOBILE AI");
        marca.setTextSize(30);
        marca.setTypeface(null, Typeface.BOLD);
        marca.setTextColor(Design.texto1);
        marca.setLetterSpacing(-0.02f);
        l.addView(marca);

        TextView sub = new TextView(a);
        sub.setText(a.getString(R.string.tagline));
        sub.setTextSize(14);
        sub.setTextColor(Design.texto2);
        sub.setPadding(0, 2, 0, Design.dp(a, 16));
        l.addView(sub);

        // estado do motor
        LinearLayout estado = Design.card(a);
        AiCore.Decisao d = ai.rotear(AiCore.CAP_TEXTO);
        TextView td = Design.rotulo(a, "Motor selecionado");
        estado.addView(td);
        TextView tn = new TextView(a);
        tn.setText(d.provider.nome + (d.provider.modelo.isEmpty() ? "" : " · " + d.provider.modelo));
        tn.setTextSize(15);
        tn.setTextColor(d.provider.local ? Design.ok : Design.acento);
        tn.setTypeface(null, Typeface.BOLD);
        tn.setPadding(0, 4, 0, 2);
        estado.addView(tn);
        TextView tm = Design.rotulo(a, d.motivo);
        estado.addView(tm);
        TextView tr = Design.rotulo(a, "Rede: " + (ai.online() ? "conectada" : "ausente")
                + " · Privacidade: " + ai.modoPrivacidade());
        tr.setTextColor(Design.texto3);
        tr.setPadding(0, 6, 0, 0);
        estado.addView(tr);
        l.addView(estado);

        // entrada rapida
        final EditText pergunta = campo("Pergunte algo…");
        l.addView(pergunta);
        pergunta.setSingleLine(true);
        pergunta.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);
        pergunta.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int acao, android.view.KeyEvent ev) {
                if (acao == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                    enviarRapido(pergunta.getText().toString());
                    return true;
                }
                return false;
            }
        });

        LinearLayout botoes = new LinearLayout(a);
        botoes.setOrientation(LinearLayout.HORIZONTAL);
        botoes.setPadding(0, Design.dp(a, 10), 0, Design.dp(a, 18));

        TextView bEnviar = Design.botao(a, "Enviar", true);
        bEnviar.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bEnviar.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                enviarRapido(pergunta.getText().toString());
            }
        });
        botoes.addView(bEnviar);

        TextView bVoz = Design.botao(a, "🎙 Voz", false);
        bVoz.setPadding(Design.dp(a, 14), Design.dp(a, 10), Design.dp(a, 14), Design.dp(a, 10));
        bVoz.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { a.ouvirVoz(pergunta); }
        });
        botoes.addView(bVoz);
        l.addView(botoes);

        // acoes rapidas
        TextView sAcoes = Design.secao(a, "Ações rápidas");
        sAcoes.setPadding(0, Design.dp(a, 6), 0, Design.dp(a, 8));
        l.addView(sAcoes);

        LinearLayout grade = new LinearLayout(a);
        grade.setOrientation(LinearLayout.VERTICAL);
        String[][] acoes = {
                {"Conversar", "chat"}, {"Ferramentas", "tools"},
                {"Documentos", "docs"}, {"Modelos", "models"},
                {"Memória", "memory"}, {"Diagnóstico", "diag"},
        };
        LinearLayout linha = null;
        for (int i = 0; i < acoes.length; i++) {
            if (i % 2 == 0) {
                linha = new LinearLayout(a);
                linha.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = Design.dp(a, 8);
                linha.setLayoutParams(lp);
                grade.addView(linha);
            }
            final String destino = acoes[i][1];
            TextView t = Design.botao(a, acoes[i][0], false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i % 2 == 0) lp.rightMargin = Design.dp(a, 8);
            t.setLayoutParams(lp);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { a.ir(destino); }
            });
            linha.addView(t);
        }
        l.addView(grade);

        // recentes
        TextView sRec = Design.secao(a, "Continuar");
        sRec.setPadding(0, Design.dp(a, 14), 0, Design.dp(a, 8));
        l.addView(sRec);

        List<Store.Conv> cs = db.conversas();
        if (cs.isEmpty()) {
            LinearLayout vazio = Design.card(a);
            vazio.addView(Design.rotulo(a, "Nenhuma conversa ainda."));
            TextView dica = Design.rotulo(a, "Crie seu primeiro workspace para organizar conversas, arquivos e memórias.");
            dica.setTextColor(Design.texto3);
            dica.setPadding(0, 4, 0, 0);
            vazio.addView(dica);
            l.addView(vazio);
        } else {
            int n = Math.min(cs.size(), 5);
            for (int i = 0; i < n; i++) {
                final Store.Conv c = cs.get(i);
                LinearLayout item = Design.card(a);
                TextView t = new TextView(a);
                t.setText((c.pinned ? "📌 " : "") + c.title);
                t.setTextSize(14.5f);
                t.setTextColor(Design.texto1);
                item.addView(t);
                TextView m = Design.rotulo(a,
                        db.contarMensagens(c.id) + " mensagens · " + hora(c.updated));
                m.setTextColor(Design.texto3);
                item.addView(m);
                item.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        convAtual = c.id;
                        a.ir("chat");
                    }
                });
                l.addView(item);
            }
        }
        return pagina(l);
    }

    private String hora(long ms) {
        java.text.SimpleDateFormat f =
                new java.text.SimpleDateFormat("dd/MM HH:mm", new java.util.Locale("pt", "BR"));
        return f.format(new java.util.Date(ms));
    }

    private void enviarRapido(String txt) {
        if (txt == null || txt.trim().isEmpty()) { aviso("Escreva algo."); return; }
        if (convAtual.isEmpty()) {
            convAtual = java.util.UUID.randomUUID().toString();
            String titulo = txt.length() > 40 ? txt.substring(0, 40) + "…" : txt;
            db.novaConversa(convAtual, titulo, "");
        }
        a.ir("chat");
        a.enviarParaChat(txt);
    }

    /* ================================================================== *
     * CONVERSA
     * ================================================================== */

    public View conversa() {
        LinearLayout l = coluna();

        // cabecalho compacto
        LinearLayout cab = new LinearLayout(a);
        cab.setOrientation(LinearLayout.HORIZONTAL);
        cab.setGravity(Gravity.CENTER_VERTICAL);
        cab.setPadding(Design.dp(a, 16), Design.dp(a, 12), Design.dp(a, 16), Design.dp(a, 8));

        TextView titulo = new TextView(a);
        titulo.setText("Conversa");
        titulo.setTextSize(18);
        titulo.setTypeface(null, Typeface.BOLD);
        titulo.setTextColor(Design.texto1);
        titulo.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        cab.addView(titulo);

        badgeChat = new TextView(a);
        badgeChat.setTextSize(10.5f);
        badgeChat.setTextColor(Design.texto2);
        Design.fundo(badgeChat, Design.superficie, Design.dp(a, 8), Design.linha);
        badgeChat.setPadding(Design.dp(a, 8), Design.dp(a, 4), Design.dp(a, 8), Design.dp(a, 4));
        cab.addView(badgeChat);
        l.addView(cab);

        statusChat = new TextView(a);
        statusChat.setTextSize(11.5f);
        statusChat.setTextColor(Design.texto3);
        statusChat.setPadding(Design.dp(a, 16), 0, Design.dp(a, 16), Design.dp(a, 6));
        l.addView(statusChat);

        // lista
        scrollChat = new ScrollView(a);
        scrollChat.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        listaChat = new LinearLayout(a);
        listaChat.setOrientation(LinearLayout.VERTICAL);
        listaChat.setPadding(Design.dp(a, 12), Design.dp(a, 6), Design.dp(a, 12), Design.dp(a, 12));
        scrollChat.addView(listaChat);
        l.addView(scrollChat);

        // mensagens salvas
        if (!convAtual.isEmpty()) {
            List<Store.Msg> ms = db.mensagens(convAtual);
            for (Store.Msg m : ms) {
                listaChat.addView(bolha(m.role, m.content, m.provider, m.model));
            }
        }
        if (listaChat.getChildCount() == 0) {
            LinearLayout vazio = Design.card(a);
            vazio.addView(Design.rotulo(a, "Nenhuma mensagem ainda."));
            TextView d = Design.rotulo(a, "Escreva abaixo. Se não houver modelo configurado, o motor local responde o que é determinístico.");
            d.setTextColor(Design.texto3);
            vazio.addView(d);
            listaChat.addView(vazio);
        }

        // compositor
        LinearLayout comp = new LinearLayout(a);
        comp.setOrientation(LinearLayout.VERTICAL);
        comp.setPadding(Design.dp(a, 12), Design.dp(a, 8), Design.dp(a, 12), Design.dp(a, 12));
        comp.setBackgroundColor(Design.superficie);

        final EditText entrada = campo("Escreva uma mensagem…");
        entrada.setMaxLines(5);
        comp.addView(entrada);

        LinearLayout linha = new LinearLayout(a);
        linha.setOrientation(LinearLayout.HORIZONTAL);
        linha.setPadding(0, Design.dp(a, 8), 0, 0);

        TextView bVoz = Design.chip(a, "🎙 Voz");
        bVoz.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { a.ouvirVoz(entrada); }
        });
        linha.addView(bVoz);

        TextView bArq = Design.chip(a, "📎 Arquivo");
        bArq.setPadding(Design.dp(a, 13), Design.dp(a, 8), Design.dp(a, 13), Design.dp(a, 8));
        bArq.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { a.escolherArquivo(); }
        });
        linha.addView(bArq);

        final TextView bParar = Design.chip(a, "■ Parar");
        bParar.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { a.pararGeracao(); aviso("Interrompido."); }
        });
        linha.addView(bParar);

        View espaco = new View(a);
        espaco.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        linha.addView(espaco);

        final TextView bEnviar = Design.botao(a, "Enviar", true);
        bEnviar.setPadding(Design.dp(a, 18), Design.dp(a, 8), Design.dp(a, 18), Design.dp(a, 8));
        bEnviar.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String t = entrada.getText().toString().trim();
                if (t.isEmpty()) return;
                entrada.setText("");
                enviar(t);
            }
        });
        linha.addView(bEnviar);
        comp.addView(linha);
        l.addView(comp);

        atualizarBadge();
        rolarFim();
        return l;
    }

    private void atualizarBadge() {
        if (badgeChat == null) return;
        AiCore.Decisao d = ai.rotear(AiCore.CAP_TEXTO);
        boolean local = d.provider.local;
        badgeChat.setText(local ? "LOCAL" : "ONLINE");
        badgeChat.setTextColor(local ? Design.ok : Design.acento);
        if (statusChat != null) {
            statusChat.setText(d.provider.nome
                    + (d.provider.modelo.isEmpty() ? "" : " · " + d.provider.modelo)
                    + " — " + d.motivo);
        }
    }

    private void rolarFim() {
        if (scrollChat == null) return;
        scrollChat.post(new Runnable() {
            @Override public void run() { scrollChat.fullScroll(View.FOCUS_DOWN); }
        });
    }

    private View bolha(String role, String conteudo, String provider, String modelo) {
        LinearLayout ext = new LinearLayout(a);
        ext.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Design.dp(a, 10);
        ext.setLayoutParams(lp);

        boolean meu = "user".equals(role);

        TextView quem = new TextView(a);
        quem.setText(meu ? "VOCÊ" : (provider == null || provider.isEmpty() ? "ASSISTENTE" : provider.toUpperCase()));
        quem.setTextSize(10);
        quem.setTextColor(Design.texto3);
        quem.setLetterSpacing(0.08f);
        quem.setGravity(meu ? Gravity.END : Gravity.START);
        ext.addView(quem);

        LinearLayout caixa = new LinearLayout(a);
        caixa.setOrientation(LinearLayout.VERTICAL);
        Design.fundo(caixa, meu ? Design.superficie : Design.cartao, Design.dp(a, 14),
                meu ? Design.linha : Design.linhaSuave);
        int p = Design.dp(a, 12);
        caixa.setPadding(p, p, p, p);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Design.dp(a, 3);
        if (meu) {
            clp.leftMargin = Design.dp(a, 40);
        } else {
            clp.rightMargin = Design.dp(a, 40);
        }
        caixa.setLayoutParams(clp);

        // blocos de codigo em monospace, resto em texto normal
        String[] partes = conteudo.split("```");
        for (int i = 0; i < partes.length; i++) {
            TextView t = new TextView(a);
            boolean codigo = (i % 2 == 1);
            t.setText(partes[i].trim());
            t.setTextSize(14);
            t.setTextColor(codigo ? Design.acento : Design.texto1);
            if (codigo) {
                t.setTypeface(Typeface.MONOSPACE);
                t.setTextSize(12.5f);
                t.setPadding(Design.dp(a, 10), Design.dp(a, 8), Design.dp(a, 10), Design.dp(a, 8));
                Design.fundo(t, Design.bg, Design.dp(a, 8), Design.linhaSuave);
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                tl.topMargin = Design.dp(a, 6);
                tl.bottomMargin = Design.dp(a, 6);
                t.setLayoutParams(tl);
            }
            t.setTextIsSelectable(true);
            caixa.addView(t);
        }
        ext.addView(caixa);

        if (!meu) {
            final String copia = conteudo;
            TextView copiar = new TextView(a);
            copiar.setText("copiar");
            copiar.setTextSize(11);
            copiar.setTextColor(Design.texto3);
            copiar.setPadding(Design.dp(a, 12), Design.dp(a, 4), 0, 0);
            copiar.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    ClipboardManager cm = (ClipboardManager)
                            a.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText("maie", copia));
                        aviso("Copiado.");
                    }
                }
            });
            ext.addView(copiar);

            if (voz.ttsDisponivel()) {
                TextView ouvir = new TextView(a);
                ouvir.setText("ouvir");
                ouvir.setTextSize(11);
                ouvir.setTextColor(Design.texto3);
                ouvir.setPadding(Design.dp(a, 12), Design.dp(a, 2), 0, 0);
                ouvir.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { voz.falar(copia); }
                });
                ext.addView(ouvir);
            }
        }
        return ext;
    }

    /** Envio publico (usado pelo Inicio e pelo compartilhamento externo). */
    public void enviar(String texto) {
        if (convAtual.isEmpty()) {
            convAtual = java.util.UUID.randomUUID().toString();
            String titulo = texto.length() > 40 ? texto.substring(0, 40) + "…" : texto;
            db.novaConversa(convAtual, titulo, "");
        }
        if (listaChat == null) { a.ir("chat"); }

        if (listaChat != null && listaChat.getChildCount() == 1
                && listaChat.getChildAt(0) instanceof LinearLayout) {
            // remove o estado vazio
            View primeiro = listaChat.getChildAt(0);
            if (primeiro.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                listaChat.removeAllViews();
            }
        }

        db.mensagem(convAtual, "user", texto, "", "", 0);
        if (listaChat != null) listaChat.addView(bolha("user", texto, "", ""));

        // bolha de resposta que sera preenchida por streaming
        saidaChat = new TextView(a);
        saidaChat.setTextSize(14);
        saidaChat.setTextColor(Design.texto1);
        saidaChat.setTextIsSelectable(true);
        final LinearLayout caixa = new LinearLayout(a);
        caixa.setOrientation(LinearLayout.VERTICAL);
        Design.fundo(caixa, Design.cartao, Design.dp(a, 14), Design.linhaSuave);
        int p = Design.dp(a, 12);
        caixa.setPadding(p, p, p, p);
        caixa.addView(saidaChat);

        final LinearLayout ext = new LinearLayout(a);
        ext.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Design.dp(a, 10);
        lp.rightMargin = Design.dp(a, 40);
        ext.setLayoutParams(lp);
        ext.addView(caixa);
        if (listaChat != null) listaChat.addView(ext);

        if (statusChat != null) statusChat.setText("Preparando…");
        rolarFim();

        // contexto: sistema + memoria + historico
        List<AiCore.Msg> msgs = new ArrayList<AiCore.Msg>();
        String sistema = db.get("system.prompt", "");
        StringBuilder sb = new StringBuilder();
        if (!sistema.isEmpty()) sb.append(sistema).append("\n");
        String mem = db.memoriaComoTexto();
        if (!mem.isEmpty() && db.bool("memoria.ativa", true)) {
            sb.append("\nO que você sabe sobre o usuário:\n").append(mem).append("\n");
        }
        if (sb.length() > 0) msgs.add(new AiCore.Msg("system", sb.toString()));

        int limite = Integer.parseInt(db.get("contexto.max", "16"));
        List<Store.Msg> hist = db.mensagens(convAtual);
        int inicio = Math.max(0, hist.size() - limite);
        for (int i = inicio; i < hist.size(); i++) {
            msgs.add(new AiCore.Msg(hist.get(i).role, hist.get(i).content));
        }

        a.gerar(msgs, texto, convAtual, saidaChat, statusChat);
    }

    /* ================================================================== *
     * MODELOS
     * ================================================================== */

    public View modelos() {
        LinearLayout l = coluna();
        l.setPadding(Design.dp(a, 16), Design.dp(a, 12), Design.dp(a, 16), Design.dp(a, 16));

        TextView t = Design.titulo(a, "Modelos");
        l.addView(t);
        TextView s = Design.rotulo(a, "Conecte um servidor local ou baixe um arquivo de modelo por URL.");
        s.setTextColor(Design.texto3);
        s.setPadding(0, 2, 0, Design.dp(a, 14));
        l.addView(s);

        // provedores locais
        TextView sl = Design.secao(a, "Servidores locais");
        sl.setPadding(0, Design.dp(a, 6), 0, Design.dp(a, 8));
        l.addView(sl);

        for (final AiCore.Provider p : ai.provedores) {
            if (!p.local) continue;
            LinearLayout c = Design.card(a);
            TextView n = new TextView(a);
            n.setText(p.nome);
            n.setTextSize(14.5f);
            n.setTextColor(Design.texto1);
            n.setTypeface(null, Typeface.BOLD);
            c.addView(n);
            TextView u = Design.rotulo(a, p.baseUrl);
            u.setTextColor(Design.texto3);
            c.addView(u);
            final TextView st = Design.rotulo(a, "Status: " + p.status
                    + (p.modelo.isEmpty() ? "" : " · modelo: " + p.modelo));
            st.setTextColor(p.status.equals("disponível") ? Design.ok : Design.texto2);
            st.setPadding(0, Design.dp(a, 4), 0, 0);
            c.addView(st);

            final TextView testar = Design.botao(a, "Testar conexão", false);
            testar.setPadding(Design.dp(a, 12), Design.dp(a, 8), Design.dp(a, 12), Design.dp(a, 8));
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tp.topMargin = Design.dp(a, 8);
            testar.setLayoutParams(tp);
            final AiCore.Provider fp = p;
            testar.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    testar.setText("Testando…");
                    new Thread(new Runnable() {
                        @Override public void run() {
                            final boolean[] ok = {false};
                            try { ok[0] = fp.saude(); }
                            catch (Exception e) { ok[0] = false; }
                            db.set("status." + fp.id, fp.status);
                            final List<String> modelosDisp = new ArrayList<String>();
                            if (ok) {
                                try { modelosDisp.addAll(fp.modelos()); }
                                catch (Exception e) { /* mantem vazio */ }
                            }
                            a.runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    testar.setText("Testar conexão");
                                    st.setText("Status: " + fp.status
                                            + " · latência " + fp.latenciaMs + " ms"
                                            + (modelosDisp.isEmpty() ? ""
                                               : " · " + modelosDisp.size() + " modelo(s)"));
                                    st.setTextColor(ok[0] ? Design.ok : Design.erro);
                                    if (ok[0] && !modelosDisp.isEmpty()) {
                                        fp.modelo = modelosDisp.get(0);
                                        ai.salvarModelo(fp.id, fp.modelo);
                                        aviso("Modelo definido: " + fp.modelo);
                                    } else if (!ok[0]) {
                                        aviso("Sem resposta em " + fp.baseUrl);
                                    }
                                }
                            });
                        }
                    }).start();
                }
            });
            c.addView(testar);
            l.addView(c);
        }

        // modelos baixados
        TextView sm = Design.secao(a, "Arquivos de modelo");
        sm.setPadding(0, Design.dp(a, 16), 0, Design.dp(a, 8));
        l.addView(sm);

        List<Store.Modelo> ms = db.modelos();
        if (ms.isEmpty()) {
            LinearLayout vazio = Design.card(a);
            vazio.addView(Design.rotulo(a, "Nenhum modelo baixado."));
            TextView d = Design.rotulo(a, "Baixar é opcional. Sem arquivo local, o app usa servidor local ou motor offline.");
            d.setTextColor(Design.texto3);
            vazio.addView(d);
            l.addView(vazio);
        } else {
            for (final Store.Modelo m : ms) {
                LinearLayout c = Design.card(a);
                TextView n = new TextView(a);
                n.setText(m.name);
                n.setTextSize(14.5f);
                n.setTextColor(Design.texto1);
                n.setTypeface(null, Typeface.BOLD);
                c.addView(n);
                TextView info = Design.rotulo(a, Tools.bytes(m.size) + " · " + m.format
                        + (m.quant.isEmpty() ? "" : " · " + m.quant)
                        + "\n" + (m.hash.isEmpty() ? "sem hash" : m.hash.substring(0, 24) + "…"));
                info.setTextColor(Design.texto2);
                c.addView(info);

                TextView remover = Design.botao(a, "Remover", false);
                remover.setPadding(Design.dp(a, 12), Design.dp(a, 8), Design.dp(a, 12), Design.dp(a, 8));
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rp.topMargin = Design.dp(a, 8);
                remover.setLayoutParams(rp);
                remover.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        File f = new File(m.path);
                        long liberado = f.exists() ? f.length() : 0;
                        if (f.exists()) f.delete();
                        File parte = new File(m.path + ".part");
                        if (parte.exists()) parte.delete();
                        db.removerModelo(m.id);
                        aviso("Removido. " + Tools.bytes(liberado) + " liberados.");
                        a.recarregar();
                    }
                });
                c.addView(remover);
                l.addView(c);
            }
        }

        // adicionar por URL
        TextView sa = Design.secao(a, "Adicionar por URL");
        sa.setPadding(0, Design.dp(a, 16), 0, Design.dp(a, 8));
        l.addView(sa);

        LinearLayout c = Design.card(a);
        final EditText url = campo("https://…/modelo.gguf");
        c.addView(url);

        final TextView info = Design.rotulo(a, "");
        info.setTextColor(Design.texto2);
        info.setPadding(0, Design.dp(a, 8), 0, 0);
        c.addView(info);

        final TextView progresso = Design.rotulo(a, "");
        progresso.setTextColor(Design.acento);
        c.addView(progresso);

        final String[] urlConfirmada = {""};
        final long[] tamanhoRemoto = {-1};

        TextView consultar = Design.botao(a, "Verificar URL", false);
        consultar.setPadding(Design.dp(a, 12), Design.dp(a, 8), Design.dp(a, 12), Design.dp(a, 8));
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vp.topMargin = Design.dp(a, 10);
        consultar.setLayoutParams(vp);
        consultar.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final String u = url.getText().toString().trim();
                if (!u.startsWith("https://") && !u.startsWith("http://")) {
                    aviso("URL deve começar com http:// ou https://");
                    return;
                }
                info.setText("Consultando…");
                mm.consultar(u, new ModelManager.TamanhoCb() {
                    @Override
                    public void onFim(int code, long tamanho, String tipo) {
                        if (code < 0) { info.setText("Falha: " + tipo); return; }
                        urlConfirmada[0] = u;
                        tamanhoRemoto[0] = tamanho;
                        long livre = mm.espacoLivre();
                        info.setText("HTTP " + code + " · " + Tools.bytes(tamanho)
                                + "\nEspaço livre: " + Tools.bytes(livre)
                                + (tamanho > 0 && livre > 0 && tamanho > livre
                                   ? "\n⚠ Espaço insuficiente." : ""));
                    }
                });
            }
        });
        c.addView(consultar);

        final TextView baixar = Design.botao(a, "Baixar", true);
        baixar.setPadding(Design.dp(a, 12), Design.dp(a, 10), Design.dp(a, 12), Design.dp(a, 10));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = Design.dp(a, 8);
        baixar.setLayoutParams(bp);
        baixar.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (urlConfirmada[0].isEmpty()) { aviso("Verifique a URL primeiro."); return; }
                final String nome = nomeDe(urlConfirmada[0]);
                final String id = nome;
                baixar.setEnabled(false);
                mm.baixar(id, urlConfirmada[0], nome, new ModelManager.Progresso() {
                    @Override
                    public void onProgresso(long feito, long total, int pct) {
                        progresso.setText(Tools.bytes(feito) + " / " + Tools.bytes(total)
                                + (pct >= 0 ? "  (" + pct + "%)" : ""));
                    }

                    @Override
                    public void onFim(File arquivo, String sha256) {
                        progresso.setText("Concluído.");
                        Store.Modelo m = new Store.Modelo();
                        m.id = java.util.UUID.randomUUID().toString();
                        m.name = arquivo.getName();
                        m.path = arquivo.getAbsolutePath();
                        m.size = arquivo.length();
                        m.format = "gguf";
                        m.quant = "";
                        m.context = 4096;
                        m.hash = sha256;
                        m.status = "instalado";
                        m.added = System.currentTimeMillis();
                        db.salvarModelo(m);
                        baixar.setEnabled(true);
                        aviso("Modelo salvo.");
                        a.recarregar();
                    }

                    @Override
                    public void onErro(String msg) {
                        progresso.setText("Erro: " + msg);
                        baixar.setEnabled(true);
                    }
                });
            }
        });
        c.addView(baixar);

        TextView nota = Design.rotulo(a,
                "Baixar e verificar é tudo o que este módulo faz. Executar o arquivo "
                        + "exige um runtime nativo (llama.cpp, ONNX, MNN) que não pode ser "
                        + "compilado neste ambiente. Para inferência local, use a aba de "
                        + "servidores locais acima.");
        nota.setTextColor(Design.texto3);
        nota.setPadding(0, Design.dp(a, 8), 0, 0);
        c.addView(nota);
        l.addView(c);

        return pagina(l);
    }

    private String nomeDe(String url) {
        int i = url.lastIndexOf('/');
        String n = i < 0 ? "modelo.bin" : url.substring(i + 1);
        int q = n.indexOf('?');
        if (q > 0) n = n.substring(0, q);
        if (n.isEmpty()) n = "modelo.bin";
        return n.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /* ================================================================== *
     * FERRAMENTAS
     * ================================================================== */

    public View ferramentas() {
        LinearLayout l = coluna();
        l.setPadding(Design.dp(a, 16), Design.dp(a, 12), Design.dp(a, 16), Design.dp(a, 16));
        l.addView(Design.titulo(a, "Ferramentas"));
        TextView s = Design.rotulo(a, "Todas funcionam offline, sem modelo e sem rede.");
        s.setTextColor(Design.texto3);
        s.setPadding(0, 2, 0, Design.dp(a, 14));
        l.addView(s);

        // calculadora
        LinearLayout calc = Design.card(a);
        calc.addView(Design.texto(a, "Calculadora", 14.5f, Design.texto1, true));
        final EditText expr = campo("2 + 2 * (10 / 4)");
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ep.topMargin = Design.dp(a, 8);
        expr.setLayoutParams(ep);
        calc.addView(expr);
        final TextView res = resultado("—");
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = Design.dp(a, 8);
        res.setLayoutParams(rp);
        calc.addView(res);
        TextView bc = Design.botao(a, "Calcular", true);
        bc.setPadding(Design.dp(a, 12), Design.dp(a, 8), Design.dp(a, 12), Design.dp(a, 8));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = Design.dp(a, 8);
        bc.setLayoutParams(bp);
        bc.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                res.setText(Tools.calcular(expr.getText().toString()));
            }
        });
        calc.addView(bc);
        l.addView(calc);

        // JSON
        LinearLayout json = Design.card(a);
        json.addView(Design.texto(a, "JSON", 14.5f, Design.texto1, true));
        final EditText jIn = campo("{\"a\":1}");
        jIn.setSingleLine(false);
        jIn.setMaxLines(6);
        LinearLayout.LayoutParams jp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        jp.topMargin = Design.dp(a, 8);
        jIn.setLayoutParams(jp);
        json.addView(jIn);
        final TextView jOut = resultado("—");
        LinearLayout.LayoutParams jop = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        jop.topMargin = Design.dp(a, 8);
        jOut.setLayoutParams(jop);
        json.addView(jOut);

        LinearLayout linha = new LinearLayout(a);
        linha.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Design.dp(a, 8);
        linha.setLayoutParams(lp);

        final String[] modo = {"formatar"};
        TextView b1 = Design.botao(a, "Formatar", true);
        b1.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        b1.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                modo[0] = "formatar";
                jOut.setText(Tools.jsonFormatar(jIn.getText().toString()));
            }
        });
        linha.addView(b1);
        TextView b2 = Design.botao(a, "Minificar", false);
        b2.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        b2.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                modo[0] = "minificar";
                jOut.setText(Tools.jsonMinificar(jIn.getText().toString()));
            }
        });
        linha.addView(b2);
        TextView b3 = Design.botao(a, "Validar", false);
        b3.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        b3.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                jOut.setText(Tools.jsonValidar(jIn.getText().toString()));
            }
        });
        linha.addView(b3);
        json.addView(linha);
        l.addView(json);

        // texto
        LinearLayout txt = Design.card(a);
        txt.addView(Design.texto(a, "Texto", 14.5f, Design.texto1, true));
        final EditText tIn = campo("Cole um texto");
        tIn.setMaxLines(5);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = Design.dp(a, 8);
        tIn.setLayoutParams(tp);
        txt.addView(tIn);
        final TextView tOut = resultado("—");
        LinearLayout.LayoutParams top = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        top.topMargin = Design.dp(a, 8);
        tOut.setLayoutParams(top);
        txt.addView(tOut);

        String[] acoes = {"Contar", "MAIÚSCULAS", "minúsculas", "Limpar", "Sem acento", "Hash"};
        LinearLayout g1 = null;
        for (int i = 0; i < acoes.length; i++) {
            if (i % 3 == 0) {
                g1 = new LinearLayout(a);
                g1.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                gl.topMargin = Design.dp(a, 8);
                g1.setLayoutParams(gl);
                txt.addView(g1);
            }
            final String acao = acoes[i];
            TextView b = Design.botao(a, acao, false);
            b.setTextSize(12);
            b.setPadding(Design.dp(a, 6), Design.dp(a, 7), Design.dp(a, 6), Design.dp(a, 7));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            bl.rightMargin = Design.dp(a, 4);
            b.setLayoutParams(bl);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    String s = tIn.getText().toString();
                    if (acao.equals("Contar")) tOut.setText(Tools.estatisticas(s));
                    else if (acao.equals("MAIÚSCULAS")) tOut.setText(Tools.transformar(s, "maiusculas"));
                    else if (acao.equals("minúsculas")) tOut.setText(Tools.transformar(s, "minusculas"));
                    else if (acao.equals("Limpar")) tOut.setText(Tools.transformar(s, "limpar"));
                    else if (acao.equals("Sem acento")) tOut.setText(Tools.transformar(s, "sem_acento"));
                    else tOut.setText("MD5: " + Tools.md5(s) + "\nSHA-256: " + Tools.sha256(s));
                }
            });
            g1.addView(b);
        }
        l.addView(txt);

        // data e hora
        LinearLayout dt = Design.card(a);
        dt.addView(Design.texto(a, "Data e hora", 14.5f, Design.texto1, true));
        final TextView dOut = resultado(Tools.agoraCompleto());
        LinearLayout.LayoutParams dop = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dop.topMargin = Design.dp(a, 8);
        dOut.setLayoutParams(dop);
        dt.addView(dOut);
        final EditText epoch = campo("timestamp (ex: 1700000000)");
        epoch.setInputType(InputType.TYPE_CLASS_NUMBER);
        LinearLayout.LayoutParams ecp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ecp.topMargin = Design.dp(a, 8);
        epoch.setLayoutParams(ecp);
        dt.addView(epoch);
        TextView bd = Design.botao(a, "Converter timestamp", false);
        bd.setPadding(Design.dp(a, 12), Design.dp(a, 8), Design.dp(a, 12), Design.dp(a, 8));
        LinearLayout.LayoutParams bdp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bdp.topMargin = Design.dp(a, 8);
        bd.setLayoutParams(bdp);
        bd.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String v2 = epoch.getText().toString();
                dOut.setText(v2.isEmpty() ? Tools.agoraCompleto() : Tools.deEpoch(v2));
            }
        });
        dt.addView(bd);
        l.addView(dt);

        // regex
        LinearLayout rx = Design.card(a);
        rx.addView(Design.texto(a, "Regex", 14.5f, Design.texto1, true));
        final EditText rTexto = campo("texto");
        LinearLayout.LayoutParams rtp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rtp.topMargin = Design.dp(a, 8);
        rTexto.setLayoutParams(rtp);
        rx.addView(rTexto);
        final EditText rPad = campo("\\d+");
        rPad.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams rpp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rpp.topMargin = Design.dp(a, 8);
        rPad.setLayoutParams(rpp);
        rx.addView(rPad);
        final TextView rOut = resultado("—");
        LinearLayout.LayoutParams rop = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rop.topMargin = Design.dp(a, 8);
        rOut.setLayoutParams(rop);
        rx.addView(rOut);
        TextView br = Design.botao(a, "Testar", false);
        br.setPadding(Design.dp(a, 12), Design.dp(a, 8), Design.dp(a, 12), Design.dp(a, 8));
        LinearLayout.LayoutParams brp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        brp.topMargin = Design.dp(a, 8);
        br.setLayoutParams(brp);
        br.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                rOut.setText(Tools.regex(rTexto.getText().toString(),
                        rPad.getText().toString()));
            }
        });
        rx.addView(br);
        l.addView(rx);

        return pagina(l);
    }

    /* ================================================================== *
     * DOCUMENTOS
     * ================================================================== */

    public View documentos() {
        LinearLayout l = coluna();
        l.setPadding(Design.dp(a, 16), Design.dp(a, 12), Design.dp(a, 16), Design.dp(a, 16));
        l.addView(Design.titulo(a, "Documentos"));

        TextView s = Design.rotulo(a,
                "O tipo é detectado pelo conteúdo, nunca pela extensão. "
                        + "Texto de PDF não é extraído sem biblioteca adicional.");
        s.setTextColor(Design.texto3);
        s.setPadding(0, 2, 0, Design.dp(a, 14));
        l.addView(s);

        final TextView saida = resultado("Nenhum arquivo analisado.");
        l.addView(saida);

        TextView b = Design.botao(a, "Escolher arquivo", true);
        b.setPadding(Design.dp(a, 12), Design.dp(a, 10), Design.dp(a, 12), Design.dp(a, 10));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = Design.dp(a, 12);
        b.setLayoutParams(bp);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { a.escolherArquivo(); }
        });
        l.addView(b);

        // contexto de arquivo ativo
        if (!a.arquivoAtual.isEmpty()) {
            LinearLayout c = Design.card(a);
            c.addView(Design.texto(a, "Arquivo em contexto", 14f, Design.texto1, true));
            TextView n = Design.rotulo(a, a.arquivoAtual);
            n.setTextColor(Design.acento);
            n.setPadding(0, 4, 0, 0);
            c.addView(n);
            TextView perguntar = Design.botao(a, "Perguntar sobre este arquivo", false);
            perguntar.setPadding(Design.dp(a, 12), Design.dp(a, 8), Design.dp(a, 12), Design.dp(a, 8));
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            pp.topMargin = Design.dp(a, 10);
            perguntar.setLayoutParams(pp);
            perguntar.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (a.textoArquivo.isEmpty()) {
                        aviso("Texto não disponível para este arquivo.");
                        return;
                    }
                    String recorte = FileIntel.recortar(a.textoArquivo, 12000);
                    String pergunta = "Considere este conteúdo de arquivo:\n\n"
                            + recorte + "\n\nFaça um resumo dos pontos principais.";
                    a.ir("chat");
                    a.enviarParaChat(pergunta);
                }
            });
            c.addView(perguntar);
            l.addView(c);
        }

        return pagina(l);
    }

    public void mostrarArquivo(FileIntel.Info info) {
        aviso("Analisado: " + info.tipo);
        a.recarregar();
    }

    /* ================================================================== *
     * MEMORIA
     * ================================================================== */

    public View memoria() {
        LinearLayout l = coluna();
        l.setPadding(Design.dp(a, 16), Design.dp(a, 12), Design.dp(a, 16), Design.dp(a, 16));
        l.addView(Design.titulo(a, "Memória"));

        TextView s = Design.rotulo(a, "Fatos que a IA deve lembrar. Tudo fica neste aparelho.");
        s.setTextColor(Design.texto3);
        s.setPadding(0, 2, 0, Design.dp(a, 14));
        l.addView(s);

        LinearLayout c = Design.card(a);
        final EditText chave = campo("Assunto (ex: nome, preferência)");
        c.addView(chave);
        final EditText valor = campo("Valor (ex: prefere respostas curtas)");
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vp.topMargin = Design.dp(a, 8);
        valor.setLayoutParams(vp);
        c.addView(valor);
        TextView add = Design.botao(a, "Salvar na memória", true);
        add.setPadding(Design.dp(a, 12), Design.dp(a, 9), Design.dp(a, 12), Design.dp(a, 9));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.topMargin = Design.dp(a, 10);
        add.setLayoutParams(ap);
        add.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String k = chave.getText().toString().trim();
                String val = valor.getText().toString().trim();
                if (k.isEmpty() || val.isEmpty()) { aviso("Preencha os dois campos."); return; }
                db.memorizar("Preferences", k, val);
                chave.setText(""); valor.setText("");
                a.recarregar();
            }
        });
        c.addView(add);
        l.addView(c);

        List<Store.Mem> ms = db.memoria();
        TextView sm = Design.secao(a, "Salvo (" + ms.size() + ")");
        sm.setPadding(0, Design.dp(a, 16), 0, Design.dp(a, 8));
        l.addView(sm);

        if (ms.isEmpty()) {
            LinearLayout vazio = Design.card(a);
            vazio.addView(Design.rotulo(a, "Memória vazia."));
            l.addView(vazio);
        } else {
            for (final Store.Mem m : ms) {
                LinearLayout item = Design.card(a);
                TextView k = new TextView(a);
                k.setText(m.key);
                k.setTextSize(14);
                k.setTextColor(Design.texto1);
                k.setTypeface(null, Typeface.BOLD);
                item.addView(k);
                TextView v = Design.rotulo(a, m.value);
                item.addView(v);
                TextView rm = Design.rotulo(a, "remover");
                rm.setTextColor(Design.erro);
                rm.setPadding(0, Design.dp(a, 6), 0, 0);
                rm.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View vv) {
                        db.esquecer(m.id);
                        a.recarregar();
                    }
                });
                item.addView(rm);
                l.addView(item);
            }
            TextView limpar = Design.botao(a, "Limpar toda a memória", false);
            limpar.setPadding(Design.dp(a, 12), Design.dp(a, 9), Design.dp(a, 12), Design.dp(a, 9));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.topMargin = Design.dp(a, 12);
            limpar.setLayoutParams(cp);
            limpar.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    db.limparMemoria();
                    a.recarregar();
                }
            });
            l.addView(limpar);
        }

        return pagina(l);
    }

    /* ================================================================== *
     * AJUSTES
     * ================================================================== */

    public View ajustes() {
        LinearLayout l = coluna();
        l.setPadding(Design.dp(a, 16), Design.dp(a, 12), Design.dp(a, 16), Design.dp(a, 16));
        l.addView(Design.titulo(a, "Ajustes"));

        // aparencia
        TextView sa = Design.secao(a, "Aparência");
        sa.setPadding(0, Design.dp(a, 14), 0, Design.dp(a, 8));
        l.addView(sa);

        LinearLayout cartaoTema = Design.card(a);
        cartaoTema.addView(Design.rotulo(a, "Tema"));
        final String[] temas = {"Escuro", "AMOLED", "Claro", "Seguir sistema"};
        LinearLayout linha = new LinearLayout(a);
        linha.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = Design.dp(a, 8);
        linha.setLayoutParams(tp);
        for (int i = 0; i < temas.length; i++) {
            final int idx = i;
            TextView b = Design.chip(a, temas[i]);
            b.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            boolean ativo = Integer.parseInt(db.get("tema", "0")) == i;
            if (ativo) {
                Design.fundo(b, Design.misturar(Design.bg, Design.acento, 0.18f),
                        Design.dp(a, 20), Design.acento);
                b.setTextColor(Design.acento);
            }
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    db.set("tema", String.valueOf(idx));
                    a.aplicarTema();
                    a.recarregar();
                }
            });
            linha.addView(b);
        }
        cartaoTema.addView(linha);
        l.addView(cartaoTema);

        // privacidade
        TextView sp = Design.secao(a, "Privacidade");
        sp.setPadding(0, Design.dp(a, 14), 0, Design.dp(a, 8));
        l.addView(sp);

        LinearLayout cartaoPriv = Design.card(a);
        cartaoPriv.addView(Design.rotulo(a, "Uso de rede"));
        final String[] mods = {"auto", "local", "rede"};
        final String[] modsTxt = {"Automático", "Somente local", "Permitir rede"};
        LinearLayout linha2 = new LinearLayout(a);
        linha2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p2.topMargin = Design.dp(a, 8);
        linha2.setLayoutParams(p2);
        for (int i = 0; i < mods.length; i++) {
            final String m = mods[i];
            TextView b = Design.chip(a, modsTxt[i]);
            b.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (ai.modoPrivacidade().equals(m)) {
                Design.fundo(b, Design.misturar(Design.bg, Design.acento, 0.18f),
                        Design.dp(a, 20), Design.acento);
                b.setTextColor(Design.acento);
            }
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    db.set("privacidade", m);
                    a.recarregar();
                }
            });
            linha2.addView(b);
        }
        cartaoPriv.addView(linha2);
        TextView nota = Design.rotulo(a, "“Somente local” bloqueia qualquer provedor externo, mesmo com chave configurada.");
        nota.setTextColor(Design.texto3);
        nota.setPadding(0, Design.dp(a, 8), 0, 0);
        cartaoPriv.addView(nota);
        l.addView(cartaoPriv);

        // provedores online
        TextView spr = Design.secao(a, "Provedores (chave opcional)");
        spr.setPadding(0, Design.dp(a, 14), 0, Design.dp(a, 8));
        l.addView(spr);

        for (final AiCore.Provider p : ai.provedores) {
            if (p.local) continue;
            LinearLayout c = Design.card(a);
            TextView n = new TextView(a);
            n.setText(p.nome);
            n.setTextSize(14);
            n.setTextColor(Design.texto1);
            n.setTypeface(null, Typeface.BOLD);
            c.addView(n);
            TextView u = Design.rotulo(a, p.baseUrl);
            u.setTextColor(Design.texto3);
            c.addView(u);

            final TextView st = Design.rotulo(a, "Status: " + p.status
                    + (p.requerChave ? " · requer chave" : " · sem chave"));
            st.setTextColor(Design.texto2);
            st.setPadding(0, Design.dp(a, 4), 0, 0);
            c.addView(st);

            final EditText campoChave = campo("chave (opcional)");
            campoChave.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            campoChave.setText(p.chave);
            LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            kp.topMargin = Design.dp(a, 8);
            campoChave.setLayoutParams(kp);
            c.addView(campoChave);

            TextView salvar = Design.botao(a, "Salvar chave", false);
            salvar.setPadding(Design.dp(a, 12), Design.dp(a, 8), Design.dp(a, 12), Design.dp(a, 8));
            LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sp2.topMargin = Design.dp(a, 8);
            salvar.setLayoutParams(sp2);
            salvar.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    ai.salvarChave(p.id, campoChave.getText().toString().trim());
                    aviso("Chave salva apenas neste aparelho.");
                }
            });
            c.addView(salvar);

            TextView testar = Design.botao(a, "Testar", false);
            testar.setPadding(Design.dp(a, 12), Design.dp(a, 8), Design.dp(a, 12), Design.dp(a, 8));
            LinearLayout.LayoutParams tp2 = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tp2.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            tp2.topMargin = Design.dp(a, 6);
            testar.setLayoutParams(tp2);
            testar.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    st.setText("Testando…");
                    new Thread(new Runnable() {
                        @Override public void run() {
                            final boolean[] ok = {false};
                            try { ok[0] = p.saude(); } catch (Exception e) { ok[0] = false; }
                            db.set("status." + p.id, p.status);
                            a.runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    st.setText("Status: " + p.status
                                            + " · " + p.latenciaMs + " ms"
                                            + (p.ultimoErro.isEmpty() ? ""
                                               : " · " + p.ultimoErro));
                                    st.setTextColor(ok[0] ? Design.ok : Design.erro);
                                }
                            });
                        }
                    }).start();
                }
            });
            c.addView(testar);
            l.addView(c);
        }

        TextView avisoProv = Design.rotulo(a,
                "Nenhuma chave é obrigatória. Sem chave, o app usa servidor local "
                        + "ou o motor offline. Chaves ficam no armazenamento privado do app.");
        avisoProv.setTextColor(Design.texto3);
        avisoProv.setPadding(0, Design.dp(a, 4), 0, 0);
        l.addView(avisoProv);

        // geracao
        TextView sg = Design.secao(a, "Geração");
        sg.setPadding(0, Design.dp(a, 14), 0, Design.dp(a, 8));
        l.addView(sg);

        LinearLayout cg = Design.card(a);
        cg.addView(Design.rotulo(a, "Instrução de sistema"));
        final EditText sys = campo("vazio = sem persona");
        sys.setText(db.get("system.prompt", ""));
        sys.setMaxLines(5);
        LinearLayout.LayoutParams syp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        syp.topMargin = Design.dp(a, 8);
        sys.setLayoutParams(syp);
        cg.addView(sys);
        TextView salvarSys = Design.botao(a, "Salvar", false);
        salvarSys.setPadding(Design.dp(a, 12), Design.dp(a, 8), Design.dp(a, 12), Design.dp(a, 8));
        LinearLayout.LayoutParams ssp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ssp.topMargin = Design.dp(a, 8);
        salvarSys.setLayoutParams(ssp);
        salvarSys.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                db.set("system.prompt", sys.getText().toString());
                aviso("Salvo.");
            }
        });
        cg.addView(salvarSys);

        cg.addView(Design.rotulo(a, "Mensagens de contexto (padrão 16)"));
        final EditText ctx = campo("16");
        ctx.setInputType(InputType.TYPE_CLASS_NUMBER);
        ctx.setText(db.get("contexto.max", "16"));
        LinearLayout.LayoutParams cp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp2.topMargin = Design.dp(a, 6);
        ctx.setLayoutParams(cp2);
        cg.addView(ctx);
        TextView salvarCtx = Design.botao(a, "Salvar", false);
        salvarCtx.setPadding(Design.dp(a, 12), Design.dp(a, 8), Design.dp(a, 12), Design.dp(a, 8));
        LinearLayout.LayoutParams scp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scp.topMargin = Design.dp(a, 8);
        salvarCtx.setLayoutParams(scp);
        salvarCtx.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                db.set("contexto.max", ctx.getText().toString().trim());
                aviso("Salvo.");
            }
        });
        cg.addView(salvarCtx);
        l.addView(cg);

        return pagina(l);
    }

    /* ================================================================== *
     * DIAGNOSTICO
     * ================================================================== */

    public View diagnostico() {
        LinearLayout l = coluna();
        l.setPadding(Design.dp(a, 16), Design.dp(a, 12), Design.dp(a, 16), Design.dp(a, 16));
        l.addView(Design.titulo(a, "Diagnóstico"));

        TextView s = Design.rotulo(a, "Testes reais executados neste aparelho.");
        s.setTextColor(Design.texto3);
        s.setPadding(0, 2, 0, Design.dp(a, 14));
        l.addView(s);

        final TextView saida = resultado("Toque em executar.");
        l.addView(saida);

        TextView b = Design.botao(a, "Executar testes", true);
        b.setPadding(Design.dp(a, 12), Design.dp(a, 10), Design.dp(a, 12), Design.dp(a, 10));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = Design.dp(a, 12);
        b.setLayoutParams(bp);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                saida.setText("Executando…");
                final StringBuilder sb = new StringBuilder();
                sb.append("=== DISPOSITIVO ===\n");
                sb.append("Modelo: ").append(android.os.Build.MODEL).append("\n");
                sb.append("Fabricante: ").append(android.os.Build.MANUFACTURER).append("\n");
                sb.append("Android: ").append(android.os.Build.VERSION.RELEASE)
                        .append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n");
                sb.append("ABI: ").append(java.util.Arrays.toString(
                        android.os.Build.SUPPORTED_ABIS)).append("\n");

                sb.append("\n=== MEMORIA ===\n");
                android.app.ActivityManager am = (android.app.ActivityManager)
                        a.getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    android.app.ActivityManager.MemoryInfo mi =
                            new android.app.ActivityManager.MemoryInfo();
                    am.getMemoryInfo(mi);
                    sb.append("RAM total: ").append(Tools.bytes(mi.totalMem)).append("\n");
                    sb.append("RAM livre: ").append(Tools.bytes(mi.availMem)).append("\n");
                    sb.append("Limite por app: ").append(Tools.bytes(am.getMemoryClass() * 1048576L)).append("\n");
                }
                sb.append("Heap usado: ").append(Tools.bytes(
                        Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()))
                        .append(" / ").append(Tools.bytes(Runtime.getRuntime().maxMemory())).append("\n");

                sb.append("\n=== ARMAZENAMENTO ===\n");
                sb.append("Livre: ").append(Tools.bytes(mm.espacoLivre())).append("\n");
                sb.append("Modelos: ").append(Tools.bytes(ModelManager.tamanhoPasta(mm.pasta()))).append("\n");

                sb.append("\n=== REDE ===\n");
                sb.append("Conectado: ").append(ai.online() ? "sim" : "não").append("\n");

                sb.append("\n=== BANCO ===\n");
                sb.append("Teste de escrita: ").append(db.testeDb() ? "OK" : "FALHOU").append("\n");
                sb.append("Tamanho: ").append(Tools.bytes(db.bytesBanco())).append("\n");
                sb.append("Conversas: ").append(db.conversas().size()).append("\n");
                sb.append("Memórias: ").append(db.memoria().size()).append("\n");

                sb.append("\n=== VOZ ===\n");
                sb.append("Reconhecimento disponível: ")
                        .append(voz.reconhecimentoDisponivel() ? "sim" : "não").append("\n");
                sb.append("Síntese pronta: ").append(voz.ttsDisponivel() ? "sim" : "não").append("\n");

                sb.append("\n=== MOTOR ===\n");
                AiCore.Decisao d = ai.rotear(AiCore.CAP_TEXTO);
                sb.append("Escolhido: ").append(d.provider.nome).append("\n");
                sb.append("Motivo: ").append(d.motivo).append("\n");

                saida.setText(sb.toString());

                // provedores em background (pode demorar)
                ai.verificarTodos(new AiCore.HealthCb() {
                    @Override public void onFim() {
                        StringBuilder s2 = new StringBuilder(saida.getText().toString());
                        s2.append("\n=== PROVEDORES ===\n");
                        for (AiCore.Provider p : ai.provedores) {
                            s2.append("• ").append(p.nome).append(": ").append(p.status);
                            if (p.latenciaMs > 0) s2.append(" (").append(p.latenciaMs).append(" ms)");
                            s2.append("\n");
                        }
                        saida.setText(s2.toString());
                    }
                });
            }
        });
        l.addView(b);

        TextView nota = Design.rotulo(a,
                "Resultados são medidos neste aparelho. Nada é estimado nem inventado.");
        nota.setTextColor(Design.texto3);
        nota.setPadding(0, Design.dp(a, 10), 0, 0);
        l.addView(nota);

        return pagina(l);
    }
}
