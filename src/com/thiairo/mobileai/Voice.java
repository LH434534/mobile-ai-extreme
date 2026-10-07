package com.thiairo.mobileai;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Voz: reconhecimento e sintese.
 *
 * Ambos sao framework Android (SpeechRecognizer / TextToSpeech).
 * O reconhecimento pode funcionar offline se o pacote de lingua estiver
 * instalado no aparelho — o app nao controla isso, e diz isso.
 */
public final class Voice implements TextToSpeech.OnInitListener {

    public interface VozCb {
        void onResultado(String texto);
        void onParcial(String texto);
        void onErro(String msg);
        void onFimDeFala();
    }

    private final Context ctx;
    private SpeechRecognizer sr;
    private TextToSpeech tts;
    private boolean ttsPronto = false;
    private VozCb cb;
    private final Locale locale = new Locale("pt", "BR");

    public Voice(Context c) {
        ctx = c.getApplicationContext();
    }

    public boolean reconhecimentoDisponivel() {
        return SpeechRecognizer.isRecognitionAvailable(ctx);
    }

    public void iniciarTts() {
        try {
            tts = new TextToSpeech(ctx, this);
        } catch (Exception e) {
            ttsPronto = false;
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS && tts != null) {
            int r = tts.setLanguage(locale);
            if (r == TextToSpeech.LANG_MISSING_DATA
                    || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.US);
            }
            ttsPronto = true;
            try {
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String u) { }
                    @Override public void onDone(String u) { }
                    @Override public void onError(String u) { }
                });
            } catch (Exception e) { /* ignora */ }
        } else {
            ttsPronto = false;
        }
    }

    public boolean ttsDisponivel() { return ttsPronto; }

    public void falar(String texto) {
        if (!ttsPronto || tts == null) return;
        try {
            Bundle p = new Bundle();
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                tts.speak(texto, TextToSpeech.QUEUE_FLUSH, p, "maie");
            } else {
                tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null);
            }
        } catch (Exception e) { /* ignora */ }
    }

    public void pararFala() {
        try { if (tts != null) tts.stop(); } catch (Exception e) { /* ignora */ }
    }

    public void ouvir(final VozCb callback) {
        if (!reconhecimentoDisponivel()) {
            callback.onErro("Reconhecimento de voz não disponível neste aparelho.");
            return;
        }
        cb = callback;
        final android.os.Handler h = new android.os.Handler(
                android.os.Looper.getMainLooper());
        h.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (sr != null) { sr.destroy(); sr = null; }
                    sr = SpeechRecognizer.createSpeechRecognizer(ctx);
                    sr.setRecognitionListener(new RecognitionListener() {
                        @Override public void onReadyForSpeech(Bundle b) {
                            if (cb != null) cb.onParcial("Ouvindo…");
                        }
                        @Override public void onBeginningOfSpeech() { }
                        @Override public void onRmsChanged(float r) { }
                        @Override public void onBufferReceived(byte[] b) { }
                        @Override public void onEndOfSpeech() {
                            if (cb != null) cb.onFimDeFala();
                        }
                        @Override public void onError(int erro) {
                            if (cb != null) cb.onErro(mensagemErro(erro));
                        }
                        @Override public void onResults(Bundle b) {
                            ArrayList<String> r = b.getStringArrayList(
                                    SpeechRecognizer.RESULTS_RECOGNITION);
                            if (r != null && !r.isEmpty() && cb != null) {
                                cb.onResultado(r.get(0));
                            } else if (cb != null) {
                                cb.onErro("Não entendi.");
                            }
                        }
                        @Override public void onPartialResults(Bundle b) {
                            ArrayList<String> r = b.getStringArrayList(
                                    SpeechRecognizer.RESULTS_RECOGNITION);
                            if (r != null && !r.isEmpty() && cb != null) {
                                cb.onParcial(r.get(0));
                            }
                        }
                        @Override public void onEvent(int t, Bundle b) { }
                    });

                    Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR");
                    i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                    i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
                    sr.startListening(i);
                } catch (Exception e) {
                    if (cb != null) cb.onErro("Falha: " + e.getMessage());
                }
            }
        });
    }

    public void pararEscuta() {
        try { if (sr != null) sr.stopListening(); } catch (Exception e) { /* ignora */ }
    }

    private String mensagemErro(int e) {
        switch (e) {
            case SpeechRecognizer.ERROR_AUDIO: return "Falha na captura de áudio.";
            case SpeechRecognizer.ERROR_CLIENT: return "Erro no cliente de voz.";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "Permissão de microfone necessária.";
            case SpeechRecognizer.ERROR_NETWORK:
                return "Reconhecimento requer rede neste aparelho.";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "Tempo esgotado na rede.";
            case SpeechRecognizer.ERROR_NO_MATCH: return "Não entendi. Tente novamente.";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "Reconhecedor ocupado.";
            case SpeechRecognizer.ERROR_SERVER: return "Erro no serviço de voz.";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "Nada foi falado.";
            default: return "Erro de reconhecimento (" + e + ").";
        }
    }

    public void liberar() {
        try { if (sr != null) sr.destroy(); } catch (Exception e) { /* ignora */ }
        try { if (tts != null) { tts.stop(); tts.shutdown(); } } catch (Exception e) { /* ignora */ }
        sr = null; tts = null;
    }
}
