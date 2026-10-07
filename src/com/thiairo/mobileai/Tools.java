package com.thiairo.mobileai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ferramentas locais — funcionam sem rede, sem modelo e sem API.
 *
 * org.json faz parte do framework Android, portanto nao e dependencia
 * externa. O avaliador de expressoes e shunting-yard proprio: chamada a
 * biblioteca de scripts ou eval seria executar codigo arbitrario.
 */
public final class Tools {

    /* ================================================================== *
     * Calculadora
     * ================================================================== */

    public static String calcular(String expr) {
        try {
            double v = avaliar(expr);
            if (Double.isNaN(v) || Double.isInfinite(v)) return "Resultado inválido";
            if (v == Math.rint(v) && Math.abs(v) < 1e15) {
                return "= " + (long) v;
            }
            return "= " + String.format(Locale.US, "%.10f", v)
                    .replaceAll("0+$", "").replaceAll("\\.$", "");
        } catch (ArithmeticException e) {
            return "Erro: " + e.getMessage();
        } catch (Exception e) {
            return "Não consegui interpretar a expressão.";
        }
    }

    private static double avaliar(String e) {
        List<String> tokens = tokenizar(e);
        if (tokens.isEmpty()) throw new IllegalArgumentException("vazio");

        // shunting-yard
        java.util.List<String> saida = new ArrayList<String>();
        Stack<String> ops = new Stack<String>();

        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);
            if (eNumero(t)) {
                saida.add(t);
            } else if (t.equals("(")) {
                ops.push(t);
            } else if (t.equals(")")) {
                while (!ops.isEmpty() && !ops.peek().equals("(")) saida.add(ops.pop());
                if (ops.isEmpty()) throw new IllegalArgumentException("parêntese");
                ops.pop();
            } else if (precedencia(t) > 0) {
                // unario: '-' no inicio ou apos operador/abertura
                if (t.equals("-") && (i == 0 || eAbreContexto(tokens.get(i - 1)))) {
                    saida.add("0");
                }
                while (!ops.isEmpty() && !ops.peek().equals("(")
                        && precedencia(ops.peek()) >= precedencia(t)) {
                    saida.add(ops.pop());
                }
                ops.push(t);
            } else {
                throw new IllegalArgumentException("token " + t);
            }
        }
        while (!ops.isEmpty()) {
            String o = ops.pop();
            if (o.equals("(")) throw new IllegalArgumentException("parêntese");
            saida.add(o);
        }

        Stack<Double> p = new Stack<Double>();
        for (String t : saida) {
            if (eNumero(t)) {
                p.push(Double.parseDouble(t));
            } else {
                if (p.size() < 2) throw new IllegalArgumentException("operandos");
                double b = p.pop(), a = p.pop();
                p.push(aplicar(a, b, t));
            }
        }
        if (p.size() != 1) throw new IllegalArgumentException("expressão");
        return p.pop();
    }

    private static boolean eAbreContexto(String anterior) {
        return anterior.equals("(") || precedencia(anterior) > 0;
    }

    private static double aplicar(double a, double b, String op) {
        switch (op) {
            case "+": return a + b;
            case "-": return a - b;
            case "*": return a * b;
            case "/":
                if (b == 0) throw new ArithmeticException("divisão por zero");
                return a / b;
            case "%":
                if (b == 0) throw new ArithmeticException("módulo por zero");
                return a % b;
            case "^": return Math.pow(a, b);
            default: throw new IllegalArgumentException("op " + op);
        }
    }

    private static int precedencia(String op) {
        if (op.equals("+") || op.equals("-")) return 1;
        if (op.equals("*") || op.equals("/") || op.equals("%")) return 2;
        if (op.equals("^")) return 3;
        return 0;
    }

    private static boolean eNumero(String s) {
        try { Double.parseDouble(s); return true; }
        catch (Exception e) { return false; }
    }

    private static List<String> tokenizar(String e) {
        List<String> out = new ArrayList<String>();
        String limpo = e.replace(",", ".").replace(" ", "").replace("×", "*")
                .replace("÷", "/").replace("x", "*");
        int i = 0;
        while (i < limpo.length()) {
            char c = limpo.charAt(i);
            if (Character.isDigit(c) || c == '.') {
                int j = i;
                while (j < limpo.length()
                        && (Character.isDigit(limpo.charAt(j)) || limpo.charAt(j) == '.')) j++;
                out.add(limpo.substring(i, j));
                i = j;
            } else if ("+-*/%^()".indexOf(c) >= 0) {
                out.add(String.valueOf(c));
                i++;
            } else {
                i++; // ignora caracteres desconhecidos em vez de quebrar
            }
        }
        return out;
    }

    /* ================================================================== *
     * JSON
     * ================================================================== */

    public static String jsonFormatar(String s) {
        try {
            s = s.trim();
            if (s.startsWith("{")) {
                return new JSONObject(s).toString(2);
            }
            if (s.startsWith("[")) {
                return new JSONArray(s).toString(2);
            }
            return new JSONObject(s).toString(2);
        } catch (Exception e) {
            return "JSON inválido: " + e.getMessage();
        }
    }

    public static String jsonMinificar(String s) {
        try {
            s = s.trim();
            if (s.startsWith("[")) return new JSONArray(s).toString();
            return new JSONObject(s).toString();
        } catch (Exception e) {
            return "JSON inválido: " + e.getMessage();
        }
    }

    public static String jsonValidar(String s) {
        try {
            s = s.trim();
            if (s.startsWith("[")) {
                JSONArray a = new JSONArray(s);
                return "Válido — array com " + a.length() + " elementos.";
            }
            JSONObject o = new JSONObject(s);
            return "Válido — objeto com " + o.length() + " chaves.";
        } catch (Exception e) {
            return "Inválido: " + e.getMessage();
        }
    }

    /* ================================================================== *
     * Texto
     * ================================================================== */

    public static String estatisticas(String s) {
        int chars = s.length();
        int semEspaco = s.replaceAll("\\s", "").length();
        String[] palavras = s.trim().isEmpty() ? new String[0] : s.trim().split("\\s+");
        String[] linhas = s.split("\n");
        String[] frases = s.split("[.!?]+");
        int paragrafos = s.trim().isEmpty() ? 0 : s.trim().split("\n\\s*\n").length;

        long tempoLeitura = Math.round(palavras.length / 200.0);
        if (tempoLeitura < 1 && palavras.length > 0) tempoLeitura = 1;

        return "Caracteres: " + chars + "\n"
                + "Sem espaços: " + semEspaco + "\n"
                + "Palavras: " + palavras.length + "\n"
                + "Linhas: " + linhas.length + "\n"
                + "Parágrafos: " + paragrafos + "\n"
                + "Frases: " + frases.length + "\n"
                + "Leitura: ~" + tempoLeitura + " min";
    }

    public static String transformar(String s, String modo) {
        if (modo.equals("maiusculas")) return s.toUpperCase();
        if (modo.equals("minusculas")) return s.toLowerCase();
        if (modo.equals("titulo")) {
            String[] ps = s.toLowerCase().split(" ");
            StringBuilder sb = new StringBuilder();
            for (String p : ps) {
                if (p.length() > 0) {
                    sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
                }
                sb.append(" ");
            }
            return sb.toString().trim();
        }
        if (modo.equals("inverter")) return new StringBuilder(s).reverse().toString();
        if (modo.equals("limpar")) {
            return s.replaceAll("[ \\t]+", " ")
                    .replaceAll("\\n{3,}", "\n\n").trim();
        }
        if (modo.equals("sem_acento")) return semAcento(s);
        if (modo.equals("lista")) {
            String[] ls = s.split("\n");
            StringBuilder sb = new StringBuilder();
            for (String l : ls) if (!l.trim().isEmpty()) sb.append("- ").append(l.trim()).append("\n");
            return sb.toString().trim();
        }
        return s;
    }

    public static String semAcento(String s) {
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD);
        return n.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    public static String base64Codificar(String s) {
        try {
            return android.util.Base64.encodeToString(
                    s.getBytes("UTF-8"), android.util.Base64.NO_WRAP);
        } catch (Exception e) {
            return "erro";
        }
    }

    public static String base64Decodificar(String s) {
        try {
            byte[] b = android.util.Base64.decode(s.trim(), android.util.Base64.DEFAULT);
            return new String(b, "UTF-8");
        } catch (Exception e) {
            return "Base64 inválido: " + e.getMessage();
        }
    }

    public static String urlCodificar(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return "erro"; }
    }

    public static String urlDecodificar(String s) {
        try { return java.net.URLDecoder.decode(s, "UTF-8"); }
        catch (Exception e) { return "erro"; }
    }

    public static String md5(String s) { return hash(s, "MD5"); }

    public static String sha256(String s) { return hash(s, "SHA-256"); }

    private static String hash(String s, String algo) {
        try {
            java.security.MessageDigest md =
                    java.security.MessageDigest.getInstance(algo);
            byte[] d = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "erro: " + e.getMessage();
        }
    }

    /** Regex com limite de tempo real — Pattern sem backtracking infinito. */
    public static String regex(String texto, String padrao) {
        try {
            Pattern p = Pattern.compile(padrao);
            Matcher m = p.matcher(texto);
            StringBuilder sb = new StringBuilder();
            int n = 0;
            while (m.find() && n < 200) {
                sb.append("[").append(m.start()).append("-").append(m.end()).append("] ");
                sb.append(m.group()).append("\n");
                n++;
            }
            if (n == 0) return "Nenhuma correspondência.";
            return n + " correspondência(s):\n" + sb.toString();
        } catch (Exception e) {
            return "Padrão inválido: " + e.getMessage();
        }
    }

    /* ================================================================== *
     * Data e hora
     * ================================================================== */

    public static String agoraCompleto() {
        long t = System.currentTimeMillis();
        SimpleDateFormat d = new SimpleDateFormat("dd 'de' MMMM 'de' yyyy", new Locale("pt", "BR"));
        SimpleDateFormat h = new SimpleDateFormat("HH:mm:ss", new Locale("pt", "BR"));
        SimpleDateFormat s = new SimpleDateFormat("EEEE", new Locale("pt", "BR"));
        return s.format(new Date(t)) + ", " + d.format(new Date(t)) + "\n"
                + h.format(new Date(t)) + "\n"
                + "Timestamp: " + t + "\n"
                + "UTC: " + new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                        .format(new Date(t));
    }

    /** Converte epoch em milissegundos para data legivel. */
    public static String deEpoch(String v) {
        try {
            long ms = Long.parseLong(v.trim());
            if (ms < 100000000000L) ms *= 1000; // segundos
            return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", new Locale("pt", "BR"))
                    .format(new Date(ms));
        } catch (Exception e) {
            return "Não é um timestamp válido.";
        }
    }

    /* ================================================================== *
     * Diff de linhas (LCS) — suficiente para comparar textos curtos
     * ================================================================== */

    public static String diff(String a, String b) {
        String[] la = a.split("\n");
        String[] lb = b.split("\n");
        int n = la.length, m = lb.length;
        int[][] d = new int[n + 1][m + 1];
        for (int i = 1; i <= n; i++)
            for (int j = 1; j <= m; j++)
                d[i][j] = la[i - 1].equals(lb[j - 1])
                        ? d[i - 1][j - 1] + 1
                        : Math.max(d[i - 1][j], d[i][j - 1]);

        StringBuilder sb = new StringBuilder();
        int i = n, j = m;
        java.util.List<String> linhas = new ArrayList<String>();
        while (i > 0 && j > 0) {
            if (la[i - 1].equals(lb[j - 1])) {
                linhas.add("  " + la[i - 1]); i--; j--;
            } else if (d[i - 1][j] >= d[i][j - 1]) {
                linhas.add("- " + la[i - 1]); i--;
            } else {
                linhas.add("+ " + lb[j - 1]); j--;
            }
        }
        while (i > 0) { linhas.add("- " + la[i - 1]); i--; }
        while (j > 0) { linhas.add("+ " + lb[j - 1]); j--; }

        for (int k = linhas.size() - 1; k >= 0; k--) sb.append(linhas.get(k)).append("\n");
        return sb.toString().trim();
    }

    /* ================================================================== *
     * Formatacao
     * ================================================================== */

    public static String bytes(long b) {
        if (b < 0) return "—";
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return String.format(Locale.US, "%.1f KB", b / 1024.0);
        if (b < 1024L * 1024 * 1024) return String.format(Locale.US, "%.1f MB", b / 1048576.0);
        return String.format(Locale.US, "%.2f GB", b / 1073741824.0);
    }

    public static String ler(InputStream in) {
        try {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
            in.close();
            return new String(o.toByteArray(), "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }
}
