package com.example.voxbridge;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.Locale;

/**
 * TtsManager handles the Text-To-Speech engine lifecycle, playback controls,
 * speed adjustment, multilingual voice switching, and lifecycle cleanup.
 */
public class TtsManager implements TextToSpeech.OnInitListener {

    private static final String TAG = "TtsManager";
    private static final String UTTERANCE_ID = "VoxBridgeSpeech";

    public interface TtsCallback {
        void onTtsReady();
        void onTtsError(String message);
        void onSpeechStarted();
        void onSpeechCompleted();
        void onSpeechStopped();
    }

    private TextToSpeech textToSpeech;
    private final Handler mainHandler;
    private final TtsCallback callback;
    private boolean isInitialized = false;
    private boolean isSpeaking = false;
    private Locale defaultLocale;

    public TtsManager(Context context, TtsCallback callback) {
        this.callback = callback;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.defaultLocale = Locale.getDefault();
        this.textToSpeech = new TextToSpeech(context.getApplicationContext(), this);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            Locale sysLocale = Locale.getDefault();
            int result = textToSpeech.setLanguage(sysLocale);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback to US English if default locale is not supported
                result = textToSpeech.setLanguage(Locale.US);
                defaultLocale = Locale.US;
            } else {
                defaultLocale = sysLocale;
            }

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Selected TTS language is not supported or missing data.");
                notifyError("Default language voice is not supported on this device.");
            } else {
                isInitialized = true;
                setupProgressListener();
                notifyReady();
            }
        } else {
            Log.e(TAG, "Failed to initialize TextToSpeech engine, status: " + status);
            notifyError("Speech engine failed to initialize.");
        }
    }

    private void setupProgressListener() {
        if (textToSpeech == null) return;

        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                isSpeaking = true;
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onSpeechStarted();
                    }
                });
            }

            @Override
            public void onDone(String utteranceId) {
                isSpeaking = false;
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onSpeechCompleted();
                    }
                });
            }

            @Override
            @Deprecated
            public void onError(String utteranceId) {
                isSpeaking = false;
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onSpeechCompleted();
                    }
                });
            }

            @Override
            public void onError(String utteranceId, int errorCode) {
                isSpeaking = false;
                Log.e(TAG, "TTS utterance error code: " + errorCode);
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onSpeechCompleted();
                    }
                });
            }
        });
    }

    /**
     * Speaks the given text using default language voice and speech rate.
     *
     * @param text The text message to speak.
     * @param speechRate Float speech rate (e.g., 1.0f for normal).
     * @return true if speech request was queued successfully, false otherwise.
     */
    public boolean speak(String text, float speechRate) {
        return speak(text, speechRate, null);
    }

    /**
     * Speaks the given text using the specified language code (e.g., "es", "hi", "fr")
     * if supported by the installed TTS engine. Falls back gracefully to default voice.
     *
     * @param text The text message to speak.
     * @param speechRate Float speech rate (e.g., 1.0f for normal).
     * @param languageCode ISO language code (e.g., "es", "hi", "en"), or null for default.
     * @return true if speech request was queued successfully, false otherwise.
     */
    public boolean speak(String text, float speechRate, String languageCode) {
        if (!isInitialized || textToSpeech == null) {
            notifyError("Speech engine is not ready yet.");
            return false;
        }

        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        if (languageCode != null && !languageCode.isEmpty()) {
            Locale targetLocale = getLocaleForCode(languageCode);
            int availability = textToSpeech.isLanguageAvailable(targetLocale);
            if (availability >= TextToSpeech.LANG_AVAILABLE) {
                textToSpeech.setLanguage(targetLocale);
            } else {
                Log.w(TAG, "Target language voice for '" + languageCode + "' is not supported by device TTS. Using default voice.");
                if (defaultLocale != null) {
                    textToSpeech.setLanguage(defaultLocale);
                }
            }
        } else {
            // Restore default locale for standard speech
            if (defaultLocale != null) {
                textToSpeech.setLanguage(defaultLocale);
            }
        }

        textToSpeech.setSpeechRate(speechRate);

        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID);

        int result = textToSpeech.speak(text.trim(), TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID);
        return result == TextToSpeech.SUCCESS;
    }

    /**
     * Checks if the TTS engine supports a given language code.
     */
    public boolean isLanguageSupported(String languageCode) {
        if (!isInitialized || textToSpeech == null || languageCode == null) {
            return false;
        }
        Locale locale = getLocaleForCode(languageCode);
        int availability = textToSpeech.isLanguageAvailable(locale);
        return availability >= TextToSpeech.LANG_AVAILABLE;
    }

    /**
     * Helper to map language codes to proper Locale instances.
     */
    public static Locale getLocaleForCode(String code) {
        if (code == null || code.isEmpty()) {
            return Locale.getDefault();
        }
        switch (code.toLowerCase(Locale.ROOT)) {
            case "en": return Locale.US;
            case "hi": return new Locale("hi", "IN");
            case "es": return new Locale("es", "ES");
            case "fr": return Locale.FRANCE;
            case "de": return Locale.GERMANY;
            case "zh": return Locale.SIMPLIFIED_CHINESE;
            case "ja": return Locale.JAPAN;
            case "ar": return new Locale("ar");
            case "ru": return new Locale("ru", "RU");
            case "pt": return new Locale("pt", "BR");
            default: return Locale.forLanguageTag(code);
        }
    }

    /**
     * Stops any ongoing speech playback.
     */
    public void stop() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            isSpeaking = false;
            if (callback != null) {
                callback.onSpeechStopped();
            }
        }
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public boolean isSpeaking() {
        return isSpeaking;
    }

    /**
     * Releases TextToSpeech resources. Must be called in Activity onDestroy().
     */
    public void shutdown() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
            isInitialized = false;
            isSpeaking = false;
        }
    }

    private void notifyReady() {
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onTtsReady();
            }
        });
    }

    private void notifyError(String message) {
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onTtsError(message);
            }
        });
    }
}
