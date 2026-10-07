package com.thiairo.mobileai;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Sistema de design.
 *
 * Tokens centralizados: cor, espaco (multiplos de 4), raio, tipografia.
 * Tres temas: claro, escuro e AMOLED (preto verdadeiro, sem elevacao por
 * cinza — usa borda).
 */
public final class Design {

    public static final int TEMA_ESCURO = 0;
    public static final int TEMA_AMOLED = 1;
    public static final int TEMA_CLARO = 2;

    public static int bg, superficie, cartao, linha, linhaSuave;
    public static int texto1, texto2, texto3;
    public static int acento, acento2, ok, aviso, erro;
    public static int temaAtual = TEMA_ESCURO;

    public static int temaAtual() { return temaAtual; }

    public static void aplicar(int t) {
        temaAtual = t;
        switch (t) {
            case TEMA_CLARO:
                bg = 0xFFF6F8FB; superficie = 0xFFFFFFFF; cartao = 0xFFFFFFFF;
                linha = 0xFFE3E8F0; linhaSuave = 0xFFEDF1F7;
                texto1 = 0xFF0D131C; texto2 = 0xFF4E5A6B; texto3 = 0xFF7A8699;
                acento = 0xFF0091B8; acento2 = 0xFF6A54E8;
                ok = 0xFF129A5B; aviso = 0xFFB07A10; erro = 0xFFC43636;
                break;
            case TEMA_AMOLED:
                bg = 0xFF000000; superficie = 0xFF080808; cartao = 0xFF111111;
                linha = 0xFF212121; linhaSuave = 0xFF171717;
                texto1 = 0xFFF2F5F9; texto2 = 0xFFA2AEBE; texto3 = 0xFF6E7A8A;
                acento = 0xFF6EE7F9; acento2 = 0xFF8B7CFF;
                ok = 0xFF5BE49B; aviso = 0xFFF5C86B; erro = 0xFFFF7A7A;
                break;
            default:
                bg = 0xFF0A0E14; superficie = 0xFF141A24; cartao = 0xFF1A222E;
                linha = 0xFF232C3A; linhaSuave = 0xFF1B2330;
                texto1 = 0xFFEEF3F9; texto2 = 0xFFA2AEC0; texto3 = 0xFF6E7A8D;
                acento = 0xFF6EE7F9; acento2 = 0xFF8B7CFF;
                ok = 0xFF5BE49B; aviso = 0xFFF5C86B; erro = 0xFFF87171;
                break;
        }
    }

    /* ------------------------------------------------------------------ */

    public static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }

    public static int sp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                c.getResources().getDisplayMetrics());
    }

    public static GradientDrawable canto(int corFundo, float raioDp, int corBorda, int espBorda) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(corFundo);
        g.setCornerRadius(raioDp);
        if (espBorda > 0) g.setStroke(espBorda, corBorda);
        return g;
    }

    public static void fundo(View v, int cor, float raio, int borda) {
        GradientDrawable g = canto(cor, raio, borda, borda == 0 ? 0 : 1);
        if (Build.VERSION.SDK_INT >= 16) v.setBackground(g);
        else v.setBackgroundDrawable(g);
    }

    /* ------------------------------------------------------------------ *
     * Fabrica de views — evita repeticao e mantem o visual consistente
     * ------------------------------------------------------------------ */

    public static TextView texto(Context c, String s, float tamanho, int cor, boolean negrito) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(tamanho);
        t.setTextColor(cor);
        if (negrito) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    public static TextView titulo(Context c, String s) {
        return texto(c, s, 20, texto1, true);
    }

    public static TextView secao(Context c, String s) {
        TextView t = texto(c, s.toUpperCase(), 11, texto3, true);
        t.setLetterSpacing(0.09f);
        return t;
    }

    public static TextView rotulo(Context c, String s) {
        return texto(c, s, 12.5f, texto2, false);
    }

    /** Cartao: superficie elevada por camada, nao por sombra pesada. */
    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        fundo(l, cartao, dp(c, 14), linha);
        int p = dp(c, 14);
        l.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 10);
        l.setLayoutParams(lp);
        return l;
    }

    public static TextView botao(Context c, String s, boolean primario) {
        TextView b = new TextView(c);
        b.setText(s);
        b.setTextSize(14);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 10));
        if (primario) {
            GradientDrawable g = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR, new int[]{acento, acento2});
            g.setCornerRadius(dp(c, 12));
            if (Build.VERSION.SDK_INT >= 16) b.setBackground(g);
            else b.setBackgroundDrawable(g);
            b.setTextColor(temaAtual == TEMA_CLARO ? 0xFFFFFFFF : 0xFF04141B);
            b.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            fundo(b, superficie, dp(c, 12), linha);
            b.setTextColor(texto1);
        }
        return b;
    }

    public static TextView chip(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(12.5f);
        t.setTextColor(texto2);
        fundo(t, superficie, dp(c, 20), linha);
        int h = dp(c, 8), w = dp(c, 13);
        t.setPadding(w, h, w, h);
        return t;
    }

    public static View divisor(Context c) {
        View v = new View(c);
        v.setBackgroundColor(linhaSuave);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return v;
    }

    public static void barra(Activity a, int cor) {
        if (Build.VERSION.SDK_INT >= 21) a.getWindow().setStatusBarColor(cor);
        if (Build.VERSION.SDK_INT >= 21) a.getWindow().setNavigationBarColor(cor);
        if (Build.VERSION.SDK_INT >= 23 && temaAtual == TEMA_CLARO) {
            a.getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    public static int misturar(int a, int b, float f) {
        return Color.argb(255,
                (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * f),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * f),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f));
    }
}
