package com.thiairo.mobileai;

import android.content.Context;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import java.io.InputStream;

/**
 * Inteligencia de arquivos.
 *
 * O tipo e detectado por conteudo (magic bytes), nunca pela extensao:
 * extensao mente, conteudo nao.
 */
public final class FileIntel {

    public static final class Info {
        public String nome;
        public String tipo;        // detectado
        public String mime;
        public long tamanho;
        public String texto = "";  // quando extraivel localmente
        public String detalhe = "";
        public boolean textoExtraivel;
    }

    private static String extensao(String nome) {
        int i = nome.lastIndexOf('.');
        return i < 0 ? "" : nome.substring(i + 1).toLowerCase();
    }

    public static Info analisar(Context c, Uri uri, String nome) {
        Info info = new Info();
        info.nome = nome == null ? "arquivo" : nome;

        long tam = -1;
        String mime = "";
        try {
            mime = c.getContentResolver().getType(uri);
        } catch (Exception e) { /* segue */ }
        if (mime == null) mime = "";

        try {
            android.database.Cursor cur = c.getContentResolver()
                    .query(uri, null, null, null, null);
            if (cur != null) {
                int idx = cur.getColumnIndex(
                        android.provider.OpenableColumns.SIZE);
                if (idx >= 0 && cur.moveToFirst()) tam = cur.getLong(idx);
                cur.close();
            }
        } catch (Exception e) { /* segue */ }

        info.tamanho = tam;
        info.mime = mime;

        byte[] cabeca = new byte[0];
        try {
            InputStream in = c.getContentResolver().openInputStream(uri);
            if (in != null) {
                byte[] buf = new byte[64];
                int n = in.read(buf);
                if (n > 0) {
                    cabeca = new byte[n];
                    System.arraycopy(buf, 0, cabeca, 0, n);
                }
                in.close();
            }
        } catch (Exception e) { /* segue */ }

        String magico = magia(cabeca);
        info.tipo = magico != null ? magico : (mime.isEmpty() ? extensao(info.nome) : mime);

        // extracao de texto: apenas formatos que da para ler sem biblioteca
        String ext = extensao(info.nome);
        if ("pdf".equals(magico) || "pdf".equals(ext)) {
            info = pdf(c, uri, info);
        } else if (eTexto(info.tipo, ext, mime)) {
            try {
                InputStream in = c.getContentResolver().openInputStream(uri);
                String t = Tools.ler(in);
                info.texto = t.length() > 200000 ? t.substring(0, 200000) : t;
                info.textoExtraivel = true;
                info.detalhe = info.texto.split("\n").length + " linhas · "
                        + info.texto.split("\\s+").length + " palavras";
            } catch (Exception e) {
                info.textoExtraivel = false;
                info.detalhe = "Falha ao ler: " + e.getMessage();
            }
        } else {
            info.textoExtraivel = false;
            info.detalhe = "Extração de texto não suportada para este formato "
                    + "sem biblioteca adicional.";
        }
        return info;
    }

    private static boolean eTexto(String tipo, String ext, String mime) {
        if (mime.startsWith("text/")) return true;
        if (ext.equals("txt") || ext.equals("md") || ext.equals("markdown")
                || ext.equals("csv") || ext.equals("json") || ext.equals("xml")
                || ext.equals("yml") || ext.equals("yaml") || ext.equals("html")
                || ext.equals("css") || ext.equals("js") || ext.equals("ts")
                || ext.equals("kt") || ext.equals("java") || ext.equals("py")
                || ext.equals("c") || ext.equals("h") || ext.equals("cpp")
                || ext.equals("rs") || ext.equals("go") || ext.equals("sql")
                || ext.equals("sh") || ext.equals("log") || ext.equals("ini")
                || ext.equals("properties") || ext.equals("gradle")) return true;
        return "texto".equals(tipo);
    }

    /** Assinaturas binarias conhecidas. */
    private static String magia(byte[] b) {
        if (b.length >= 4 && b[0] == 0x25 && b[1] == 0x50
                && b[2] == 0x44 && b[3] == 0x46) return "pdf";
        if (b.length >= 8 && b[0] == (byte) 0x89 && b[1] == 0x50
                && b[2] == 0x4E && b[3] == 0x47) return "png";
        if (b.length >= 3 && b[0] == (byte) 0xFF && b[1] == (byte) 0xD8
                && b[2] == (byte) 0xFF) return "jpeg";
        if (b.length >= 6 && (b[0] == 'G' && b[1] == 'I' && b[2] == 'F')) return "gif";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F'
                && b[3] == 'F' && b[8] == 'W' && b[9] == 'E') return "webp";
        if (b.length >= 4 && b[0] == 'P' && b[1] == 'K' && b[3] == 0x03) return "zip";
        if (b.length >= 4 && b[0] == 'G' && b[1] == 'G' && b[2] == 'U'
                && b[3] == 'F') return "gguf";
        if (b.length >= 4 && b[0] == 0x7F && b[1] == 'E' && b[2] == 'L'
                && b[3] == 'F') return "elf";
        return null;
    }

    /**
     * PDF: PdfRenderer da contagem de paginas e renderiza, mas NAO extrai
     * texto — isso exige biblioteca de parsing (PDFBox/iText), fora do
     * escopo deste build sem dependencias. Dizer isso e melhor que fingir.
     */
    private static Info pdf(Context c, Uri uri, Info info) {
        info.textoExtraivel = false;
        if (Build.VERSION.SDK_INT < 21) {
            info.detalhe = "PDF requer Android 5+ para inspeção.";
            return info;
        }
        ParcelFileDescriptor pfd = null;
        PdfRenderer r = null;
        try {
            pfd = c.getContentResolver().openFileDescriptor(uri, "r");
            if (pfd == null) {
                info.detalhe = "Não foi possível abrir o descritor.";
                return info;
            }
            r = new PdfRenderer(pfd);
            int paginas = r.getPageCount();
            info.detalhe = paginas + " páginas\n"
                    + "Extração de texto: não suportada sem biblioteca "
                    + "adicional (PDFBox/iText).";
            info.texto = "[PDF de " + paginas + " páginas — texto não extraído]";
        } catch (Exception e) {
            info.detalhe = "Falha ao inspecionar PDF: " + e.getMessage();
        } finally {
            try { if (r != null) r.close(); } catch (Exception e) { /* ignora */ }
            try { if (pfd != null) pfd.close(); } catch (Exception e) { /* ignora */ }
        }
        return info;
    }

    /** Recorte de contexto: prioriza inicio e mantem o limite. */
    public static String recortar(String texto, int maxChars) {
        if (texto == null) return "";
        if (texto.length() <= maxChars) return texto;
        int cabeca = (int) (maxChars * 0.7);
        int cauda = maxChars - cabeca;
        return texto.substring(0, cabeca)
                + "\n\n… [trecho omitido] …\n\n"
                + texto.substring(texto.length() - cauda);
    }
}
