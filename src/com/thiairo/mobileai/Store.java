package com.thiairo.mobileai;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistencia local.
 *
 * SQLite do framework — Room viria de dependencia externa, incompativel
 * com este build (sem acesso a Maven). Migrations explicitas por versao:
 * nunca apaga dado do usuario em silencio.
 */
public final class Store extends SQLiteOpenHelper {

    private static final String DB = "mobileai.db";
    private static final int VERSAO = 3;

    public Store(Context c) { super(c, DB, null, VERSAO); }

    @Override
    public void onCreate(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE conversations (" +
                "id TEXT PRIMARY KEY, title TEXT, project TEXT, " +
                "created INTEGER, updated INTEGER, pinned INTEGER DEFAULT 0)");
        d.execSQL("CREATE TABLE messages (" +
                "id TEXT PRIMARY KEY, conv TEXT, role TEXT, content TEXT, " +
                "provider TEXT, model TEXT, created INTEGER, tokens INTEGER)");
        d.execSQL("CREATE TABLE memory (" +
                "id TEXT PRIMARY KEY, category TEXT, key TEXT, value TEXT, updated INTEGER)");
        d.execSQL("CREATE TABLE projects (" +
                "id TEXT PRIMARY KEY, name TEXT, note TEXT, created INTEGER)");
        d.execSQL("CREATE TABLE models (" +
                "id TEXT PRIMARY KEY, name TEXT, path TEXT, size INTEGER, " +
                "format TEXT, quant TEXT, context INTEGER, hash TEXT, " +
                "status TEXT, added INTEGER, task TEXT)");
        d.execSQL("CREATE TABLE kv (" +
                "k TEXT PRIMARY KEY, v TEXT)");
        d.execSQL("CREATE INDEX idx_msg_conv ON messages(conv)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase d, int antiga, int nova) {
        // migracao incremental: cada passo preserva o que ja existe
        if (antiga < 2) {
            d.execSQL("CREATE TABLE IF NOT EXISTS memory (" +
                    "id TEXT PRIMARY KEY, category TEXT, key TEXT, value TEXT, updated INTEGER)");
        }
        if (antiga < 3) {
            d.execSQL("CREATE TABLE IF NOT EXISTS models (" +
                    "id TEXT PRIMARY KEY, name TEXT, path TEXT, size INTEGER, " +
                    "format TEXT, quant TEXT, context INTEGER, hash TEXT, " +
                    "status TEXT, added INTEGER, task TEXT)");
        }
    }

    /* ------------------------------------------------------------------ *
     * Preferencias
     * ------------------------------------------------------------------ */

    public void set(String k, String v) {
        SQLiteDatabase d = getWritableDatabase();
        ContentValues c = new ContentValues();
        c.put("k", k);
        c.put("v", v == null ? "" : v);
        d.insertWithOnConflict("kv", null, c, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public String get(String k, String padrao) {
        SQLiteDatabase d = getReadableDatabase();
        Cursor c = d.query("kv", new String[]{"v"}, "k=?", new String[]{k},
                null, null, null);
        String v = padrao;
        if (c.moveToFirst()) v = c.getString(0);
        c.close();
        return v;
    }

    public boolean bool(String k, boolean padrao) {
        return Boolean.parseBoolean(get(k, String.valueOf(padrao)));
    }

    public void setBool(String k, boolean v) { set(k, String.valueOf(v)); }

    /* ------------------------------------------------------------------ *
     * Conversas
     * ------------------------------------------------------------------ */

    public static final class Conv {
        public String id, title, project;
        public long created, updated;
        public boolean pinned;
    }

    public void novaConversa(String id, String titulo, String projeto) {
        SQLiteDatabase d = getWritableDatabase();
        ContentValues c = new ContentValues();
        long agora = System.currentTimeMillis();
        c.put("id", id);
        c.put("title", titulo);
        c.put("project", projeto == null ? "" : projeto);
        c.put("created", agora);
        c.put("updated", agora);
        c.put("pinned", 0);
        d.insertWithOnConflict("conversations", null, c, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void titulo(String id, String titulo) {
        SQLiteDatabase d = getWritableDatabase();
        ContentValues c = new ContentValues();
        c.put("title", titulo);
        c.put("updated", System.currentTimeMillis());
        d.update("conversations", c, "id=?", new String[]{id});
    }

    public void toque(String id) {
        SQLiteDatabase d = getWritableDatabase();
        ContentValues c = new ContentValues();
        c.put("updated", System.currentTimeMillis());
        d.update("conversations", c, "id=?", new String[]{id});
    }

    public void fixar(String id, boolean v) {
        SQLiteDatabase d = getWritableDatabase();
        ContentValues c = new ContentValues();
        c.put("pinned", v ? 1 : 0);
        d.update("conversations", c, "id=?", new String[]{id});
    }

    public void apagarConversa(String id) {
        SQLiteDatabase d = getWritableDatabase();
        d.delete("messages", "conv=?", new String[]{id});
        d.delete("conversations", "id=?", new String[]{id});
    }

    public List<Conv> conversas() {
        List<Conv> out = new ArrayList<Conv>();
        SQLiteDatabase d = getReadableDatabase();
        Cursor c = d.query("conversations", null, null, null, null, null,
                "pinned DESC, updated DESC");
        while (c.moveToNext()) {
            Conv k = new Conv();
            k.id = c.getString(c.getColumnIndexOrThrow("id"));
            k.title = c.getString(c.getColumnIndexOrThrow("title"));
            k.project = c.getString(c.getColumnIndexOrThrow("project"));
            k.created = c.getLong(c.getColumnIndexOrThrow("created"));
            k.updated = c.getLong(c.getColumnIndexOrThrow("updated"));
            k.pinned = c.getInt(c.getColumnIndexOrThrow("pinned")) == 1;
            out.add(k);
        }
        c.close();
        return out;
    }

    /* ------------------------------------------------------------------ *
     * Mensagens
     * ------------------------------------------------------------------ */

    public static final class Msg {
        public String id, conv, role, content, provider, model;
        public long created;
        public int tokens;
    }

    public void mensagem(String conv, String role, String content,
                         String provider, String model, int tokens) {
        SQLiteDatabase d = getWritableDatabase();
        ContentValues c = new ContentValues();
        c.put("id", java.util.UUID.randomUUID().toString());
        c.put("conv", conv);
        c.put("role", role);
        c.put("content", content);
        c.put("provider", provider == null ? "" : provider);
        c.put("model", model == null ? "" : model);
        c.put("created", System.currentTimeMillis());
        c.put("tokens", tokens);
        d.insert("messages", null, c);
        toque(conv);
    }

    public List<Msg> mensagens(String conv) {
        List<Msg> out = new ArrayList<Msg>();
        SQLiteDatabase d = getReadableDatabase();
        Cursor c = d.query("messages", null, "conv=?", new String[]{conv},
                null, null, "created ASC", null);
        while (c.moveToNext()) {
            Msg m = new Msg();
            m.id = c.getString(c.getColumnIndexOrThrow("id"));
            m.conv = conv;
            m.role = c.getString(c.getColumnIndexOrThrow("role"));
            m.content = c.getString(c.getColumnIndexOrThrow("content"));
            m.provider = c.getString(c.getColumnIndexOrThrow("provider"));
            m.model = c.getString(c.getColumnIndexOrThrow("model"));
            m.created = c.getLong(c.getColumnIndexOrThrow("created"));
            m.tokens = c.getInt(c.getColumnIndexOrThrow("tokens"));
            out.add(m);
        }
        c.close();
        return out;
    }

    public int contarMensagens(String conv) {
        SQLiteDatabase d = getReadableDatabase();
        Cursor c = d.rawQuery("SELECT COUNT(*) FROM messages WHERE conv=?",
                new String[]{conv});
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n;
    }

    public void limparMensagens(String conv) {
        getWritableDatabase().delete("messages", "conv=?", new String[]{conv});
    }

    /* ------------------------------------------------------------------ *
     * Memoria
     * ------------------------------------------------------------------ */

    public static final class Mem {
        public String id, cat, key, value;
        public long updated;
    }

    public void memorizar(String cat, String key, String value) {
        SQLiteDatabase d = getWritableDatabase();
        ContentValues c = new ContentValues();
        c.put("id", java.util.UUID.randomUUID().toString());
        c.put("category", cat);
        c.put("key", key);
        c.put("value", value);
        c.put("updated", System.currentTimeMillis());
        d.insert("memory", null, c);
    }

    public List<Mem> memoria() {
        List<Mem> out = new ArrayList<Mem>();
        Cursor c = getReadableDatabase().query("memory", null, null, null,
                null, null, "updated DESC");
        while (c.moveToNext()) {
            Mem m = new Mem();
            m.id = c.getString(c.getColumnIndexOrThrow("id"));
            m.cat = c.getString(c.getColumnIndexOrThrow("category"));
            m.key = c.getString(c.getColumnIndexOrThrow("key"));
            m.value = c.getString(c.getColumnIndexOrThrow("value"));
            m.updated = c.getLong(c.getColumnIndexOrThrow("updated"));
            out.add(m);
        }
        c.close();
        return out;
    }

    public void esquecer(String id) {
        getWritableDatabase().delete("memory", "id=?", new String[]{id});
    }

    public void limparMemoria() {
        getWritableDatabase().delete("memory", null, null);
    }

    /** Texto da memoria para injetar como contexto do sistema. */
    public String memoriaComoTexto() {
        List<Mem> ms = memoria();
        if (ms.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Mem m : ms) {
            sb.append("- [").append(m.cat).append("] ").append(m.key)
              .append(": ").append(m.value).append("\n");
        }
        return sb.toString();
    }

    /* ------------------------------------------------------------------ *
     * Modelos
     * ------------------------------------------------------------------ */

    public static final class Modelo {
        public String id, name, path, format, quant, hash, status, task;
        public long size;
        public int context;
        public long added;
    }

    public void salvarModelo(Modelo m) {
        SQLiteDatabase d = getWritableDatabase();
        ContentValues c = new ContentValues();
        c.put("id", m.id);
        c.put("name", m.name);
        c.put("path", m.path);
        c.put("size", m.size);
        c.put("format", m.format);
        c.put("quant", m.quant);
        c.put("context", m.context);
        c.put("hash", m.hash);
        c.put("status", m.status);
        c.put("added", m.added);
        c.put("task", m.task == null ? "" : m.task);
        d.insertWithOnConflict("models", null, c, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public List<Modelo> modelos() {
        List<Modelo> out = new ArrayList<Modelo>();
        Cursor c = getReadableDatabase().query("models", null, null, null,
                null, null, "added DESC");
        while (c.moveToNext()) {
            Modelo m = new Modelo();
            m.id = c.getString(c.getColumnIndexOrThrow("id"));
            m.name = c.getString(c.getColumnIndexOrThrow("name"));
            m.path = c.getString(c.getColumnIndexOrThrow("path"));
            m.size = c.getLong(c.getColumnIndexOrThrow("size"));
            m.format = c.getString(c.getColumnIndexOrThrow("format"));
            m.quant = c.getString(c.getColumnIndexOrThrow("quant"));
            m.context = c.getInt(c.getColumnIndexOrThrow("context"));
            m.hash = c.getString(c.getColumnIndexOrThrow("hash"));
            m.status = c.getString(c.getColumnIndexOrThrow("status"));
            m.added = c.getLong(c.getColumnIndexOrThrow("added"));
            m.task = c.getString(c.getColumnIndexOrThrow("task"));
            out.add(m);
        }
        c.close();
        return out;
    }

    public void removerModelo(String id) {
        getWritableDatabase().delete("models", "id=?", new String[]{id});
    }

    /* ------------------------------------------------------------------ *
     * Projetos
     * ------------------------------------------------------------------ */

    public void novoProjeto(String id, String nome, String nota) {
        SQLiteDatabase d = getWritableDatabase();
        ContentValues c = new ContentValues();
        c.put("id", id);
        c.put("name", nome);
        c.put("note", nota == null ? "" : nota);
        c.put("created", System.currentTimeMillis());
        d.insertWithOnConflict("projects", null, c, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public List<String> projetos() {
        List<String> out = new ArrayList<String>();
        Cursor c = getReadableDatabase().query("projects", null, null, null,
                null, null, "created DESC");
        while (c.moveToNext()) out.add(c.getString(c.getColumnIndexOrThrow("name")));
        c.close();
        return out;
    }

    /* ------------------------------------------------------------------ *
     * Diagnostico
     * ------------------------------------------------------------------ */

    public long bytesBanco() {
        try {
            java.io.File f = new java.io.File(
                    getReadableDatabase().getPath());
            return f.exists() ? f.length() : 0;
        } catch (Exception e) {
            return -1;
        }
    }

    public boolean testeDb() {
        try {
            SQLiteDatabase d = getWritableDatabase();
            d.execSQL("CREATE TABLE IF NOT EXISTS _t (x INTEGER)");
            d.execSQL("INSERT INTO _t VALUES (1)");
            Cursor c = d.rawQuery("SELECT COUNT(*) FROM _t", null);
            int n = 0;
            if (c.moveToFirst()) n = c.getInt(0);
            c.close();
            d.execSQL("DROP TABLE _t");
            return n > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
