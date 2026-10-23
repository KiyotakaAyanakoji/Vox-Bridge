package com.example.voxbridge;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.TranslateRemoteModel;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TranslationManager handles on-device multilingual translation using Google ML Kit.
 * Manages translator instances, language model verification, automated downloading,
 * network connectivity validation, and safe lifecycle disposal.
 */
public class TranslationManager {

    private static final String TAG = "TranslationManager";

    public static class LanguageItem {
        private final String code;
        private final String displayName;

        public LanguageItem(String code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }

        public String getCode() {
            return code;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public interface TranslationCallback {
        void onStatusUpdate(String statusMessage);
        void onSuccess(String translatedText);
        void onError(String errorMessage);
    }

    private static final List<LanguageItem> SUPPORTED_LANGUAGES;
    private static final Map<String, String> LANGUAGE_NAME_MAP;

    static {
        List<LanguageItem> languages = new ArrayList<>();
        languages.add(new LanguageItem(TranslateLanguage.ENGLISH, "English"));
        languages.add(new LanguageItem(TranslateLanguage.HINDI, "Hindi (हिन्दी)"));
        languages.add(new LanguageItem(TranslateLanguage.SPANISH, "Spanish (Español)"));
        languages.add(new LanguageItem(TranslateLanguage.FRENCH, "French (Français)"));
        languages.add(new LanguageItem(TranslateLanguage.GERMAN, "German (Deutsch)"));
        languages.add(new LanguageItem(TranslateLanguage.CHINESE, "Chinese (中文)"));
        languages.add(new LanguageItem(TranslateLanguage.JAPANESE, "Japanese (日本語)"));
        languages.add(new LanguageItem(TranslateLanguage.ARABIC, "Arabic (العربية)"));
        languages.add(new LanguageItem(TranslateLanguage.RUSSIAN, "Russian (Русский)"));
        languages.add(new LanguageItem(TranslateLanguage.PORTUGUESE, "Portuguese (Português)"));
        SUPPORTED_LANGUAGES = Collections.unmodifiableList(languages);

        Map<String, String> map = new HashMap<>();
        for (LanguageItem item : languages) {
            map.put(item.getCode(), item.getDisplayName());
        }
        LANGUAGE_NAME_MAP = Collections.unmodifiableMap(map);
    }

    private final Context appContext;
    private final Handler mainHandler;
    private final RemoteModelManager modelManager;

    private Translator activeTranslator;
    private String activeSourceCode;
    private String activeTargetCode;

    private long currentRequestId = 0;
    private boolean isTranslating = false;

    public TranslationManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.modelManager = RemoteModelManager.getInstance();
    }

    /**
     * Returns list of supported language options for UI spinners.
     */
    public List<LanguageItem> getSupportedLanguages() {
        return SUPPORTED_LANGUAGES;
    }

    /**
     * Helper to get user-friendly display name from language code.
     */
    public String getLanguageName(String code) {
        String name = LANGUAGE_NAME_MAP.get(code);
        return (name != null) ? name : code;
    }

    /**
     * Checks if device currently has active network connectivity.
     */
    public boolean isNetworkAvailable() {
        try {
            ConnectivityManager cm = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            return capabilities != null && (
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            );
        } catch (Exception e) {
            Log.e(TAG, "Error checking network connectivity", e);
            return false;
        }
    }

    /**
     * Performs translation from source language to target language.
     * Validates identical languages, verifies model availability, downloads model if needed,
     * and performs on-device translation asynchronously.
     */
    public void translate(@NonNull String text, @NonNull String sourceCode, @NonNull String targetCode, @NonNull TranslationCallback callback) {
        final long requestId = ++currentRequestId;
        isTranslating = true;

        final String trimmedText = text.trim();
        if (trimmedText.isEmpty()) {
            isTranslating = false;
            postError(callback, requestId, "Please enter some text to translate.");
            return;
        }

        // Handle identical source and target languages immediately
        if (sourceCode.equalsIgnoreCase(targetCode)) {
            isTranslating = false;
            postStatus(callback, requestId, "Source and target languages are identical.");
            postSuccess(callback, requestId, trimmedText);
            return;
        }

        postStatus(callback, requestId, "Verifying language models…");

        // Obtain or recreate translator client for the given language pair
        Translator translator = getOrCreateTranslator(sourceCode, targetCode);

        // Check if target model is already downloaded
        TranslateRemoteModel targetModel = new TranslateRemoteModel.Builder(targetCode).build();
        modelManager.isModelDownloaded(targetModel)
                .addOnSuccessListener(isDownloaded -> {
                    if (requestId != currentRequestId) return;

                    if (Boolean.TRUE.equals(isDownloaded)) {
                        // Model is ready on-device! Translate offline
                        executeTranslation(translator, trimmedText, callback, requestId);
                    } else {
                        // Model needs to be downloaded. Check internet availability first
                        if (!isNetworkAvailable()) {
                            isTranslating = false;
                            String targetName = getLanguageName(targetCode);
                            postError(callback, requestId, "Language model for " + targetName +
                                    " is not downloaded. Please connect to internet once to download it (~30MB) for offline use.");
                            return;
                        }

                        String targetName = getLanguageName(targetCode);
                        postStatus(callback, requestId, "Downloading " + targetName + " model (~30MB). Please wait…");

                        DownloadConditions conditions = new DownloadConditions.Builder().build();
                        translator.downloadModelIfNeeded(conditions)
                                .addOnSuccessListener(unused -> {
                                    if (requestId != currentRequestId) return;
                                    postStatus(callback, requestId, "Model ready. Translating on-device…");
                                    executeTranslation(translator, trimmedText, callback, requestId);
                                })
                                .addOnFailureListener(e -> {
                                    if (requestId != currentRequestId) return;
                                    isTranslating = false;
                                    Log.e(TAG, "Model download failure", e);
                                    postError(callback, requestId, "Failed to download language model. Please verify your internet connection.");
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    if (requestId != currentRequestId) return;
                    // Fallback to translator.downloadModelIfNeeded directly
                    postStatus(callback, requestId, "Preparing translation models…");
                    DownloadConditions conditions = new DownloadConditions.Builder().build();
                    translator.downloadModelIfNeeded(conditions)
                            .addOnSuccessListener(unused -> {
                                if (requestId != currentRequestId) return;
                                executeTranslation(translator, trimmedText, callback, requestId);
                            })
                            .addOnFailureListener(ex -> {
                                if (requestId != currentRequestId) return;
                                isTranslating = false;
                                Log.e(TAG, "Model preparation failure", ex);
                                postError(callback, requestId, "Could not prepare translation models: " + ex.getLocalizedMessage());
                            });
                });
    }

    private void executeTranslation(Translator translator, String text, TranslationCallback callback, long requestId) {
        postStatus(callback, requestId, "Translating text on-device…");

        translator.translate(text)
                .addOnSuccessListener(translatedText -> {
                    if (requestId != currentRequestId) return;
                    isTranslating = false;
                    postStatus(callback, requestId, "Translation complete (On-Device)");
                    postSuccess(callback, requestId, translatedText);
                })
                .addOnFailureListener(e -> {
                    if (requestId != currentRequestId) return;
                    isTranslating = false;
                    Log.e(TAG, "Translation execution failed", e);
                    postError(callback, requestId, "Translation error: " + e.getLocalizedMessage());
                });
    }

    private synchronized Translator getOrCreateTranslator(String sourceCode, String targetCode) {
        if (activeTranslator != null &&
                sourceCode.equalsIgnoreCase(activeSourceCode) &&
                targetCode.equalsIgnoreCase(activeTargetCode)) {
            return activeTranslator;
        }

        // Close previous translator to prevent resource leak
        if (activeTranslator != null) {
            try {
                activeTranslator.close();
            } catch (Exception e) {
                Log.w(TAG, "Error closing previous translator", e);
            }
            activeTranslator = null;
        }

        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(sourceCode)
                .setTargetLanguage(targetCode)
                .build();

        activeTranslator = Translation.getClient(options);
        activeSourceCode = sourceCode;
        activeTargetCode = targetCode;
        return activeTranslator;
    }

    public boolean isTranslating() {
        return isTranslating;
    }

    /**
     * Safely closes translator instance and invalidates in-flight requests.
     * Must be called in Activity onDestroy().
     */
    public synchronized void destroy() {
        currentRequestId++;
        isTranslating = false;
        if (activeTranslator != null) {
            try {
                activeTranslator.close();
            } catch (Exception e) {
                Log.w(TAG, "Error closing translator on destroy", e);
            }
            activeTranslator = null;
        }
    }

    private void postStatus(TranslationCallback callback, long requestId, String message) {
        mainHandler.post(() -> {
            if (requestId == currentRequestId && callback != null) {
                callback.onStatusUpdate(message);
            }
        });
    }

    private void postSuccess(TranslationCallback callback, long requestId, String result) {
        mainHandler.post(() -> {
            if (requestId == currentRequestId && callback != null) {
                callback.onSuccess(result);
            }
        });
    }

    private void postError(TranslationCallback callback, long requestId, String error) {
        mainHandler.post(() -> {
            if (requestId == currentRequestId && callback != null) {
                callback.onError(error);
            }
        });
    }
}
