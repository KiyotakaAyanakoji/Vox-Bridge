package com.example.voxbridge;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements TtsManager.TtsCallback {

    // Speech & Translation Managers
    private TtsManager ttsManager;
    private TranslationManager translationManager;

    // UI Elements
    private NestedScrollView scrollView;
    private EditText etMessage;
    private TextView tvCharCount;
    private View btnClear;
    private View btnSpeak;
    private View btnStop;
    private TextView tvSpeechRate;
    private SeekBar sbSpeechRate;
    private LinearLayout layoutQuickPhrasesContainer;

    // Status UI
    private View viewStatusDot;
    private TextView tvStatus;

    // Translation UI
    private Spinner spSourceLang;
    private Spinner spTargetLang;
    private View btnTranslate;
    private TextView tvTranslatedText;
    private View btnSpeakTranslation;
    private TextView tvTranslationNotice;

    // Current State
    private float currentSpeechRate = 1.0f;

    // Predefined Quick Phrases as required
    private static final List<String> QUICK_PHRASES = Arrays.asList(
            "I need help.",
            "I need water.",
            "Please call my family.",
            "I cannot speak. Please read this message.",
            "Where is the nearest hospital?",
            "Thank you.",
            "Yes.",
            "No.",
            "Please speak slowly.",
            "I do not understand."
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Adjust window insets for system bars
        View mainView = findViewById(R.id.main);
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        initViews();
        setupManagers();
        setupInputListeners();
        setupSpeechControls();
        setupQuickPhrases();
        setupTranslationSection();
    }

    private void initViews() {
        scrollView = findViewById(R.id.scrollView);
        etMessage = findViewById(R.id.etMessage);
        tvCharCount = findViewById(R.id.tvCharCount);
        btnClear = findViewById(R.id.btnClear);
        btnSpeak = findViewById(R.id.btnSpeak);
        btnStop = findViewById(R.id.btnStop);
        tvSpeechRate = findViewById(R.id.tvSpeechRate);
        sbSpeechRate = findViewById(R.id.sbSpeechRate);
        layoutQuickPhrasesContainer = findViewById(R.id.layoutQuickPhrasesContainer);

        viewStatusDot = findViewById(R.id.viewStatusDot);
        tvStatus = findViewById(R.id.tvStatus);

        spSourceLang = findViewById(R.id.spSourceLang);
        spTargetLang = findViewById(R.id.spTargetLang);
        btnTranslate = findViewById(R.id.btnTranslate);
        tvTranslatedText = findViewById(R.id.tvTranslatedText);
        btnSpeakTranslation = findViewById(R.id.btnSpeakTranslation);
        tvTranslationNotice = findViewById(R.id.tvTranslationNotice);
    }

    private void setupManagers() {
        updateStatus(getString(R.string.status_tts_initializing), R.color.status_phase);
        ttsManager = new TtsManager(this, this);
        translationManager = new TranslationManager(this);
    }

    private void setupInputListeners() {
        // Character counter listener
        etMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Not needed
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int count, int after) {
                int length = (s != null) ? s.length() : 0;
                tvCharCount.setText(String.format(Locale.getDefault(), getString(R.string.char_count_format), length));
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Not needed
            }
        });

        // Clear button
        btnClear.setOnClickListener(v -> {
            etMessage.setText("");
            tvTranslatedText.setText(R.string.translated_text_placeholder);
            tvTranslatedText.setTypeface(null, Typeface.ITALIC);
            tvTranslatedText.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.text_muted));
            btnSpeakTranslation.setEnabled(false);
            btnSpeakTranslation.setAlpha(0.5f);
            if (ttsManager != null && ttsManager.isSpeaking()) {
                ttsManager.stop();
            }
            updateStatus(getString(R.string.status_cleared), R.color.status_ready);
        });
    }

    private void setupSpeechControls() {
        // Speak button
        btnSpeak.setOnClickListener(v -> {
            String text = etMessage.getText().toString().trim();
            if (text.isEmpty()) {
                updateStatus(getString(R.string.status_empty_warning), R.color.status_phase);
                Toast.makeText(MainActivity.this, R.string.status_empty_warning, Toast.LENGTH_SHORT).show();
                return;
            }

            if (ttsManager != null) {
                boolean queued = ttsManager.speak(text, currentSpeechRate);
                if (!queued) {
                    updateStatus("Speech could not be started", R.color.status_stopped);
                }
            }
        });

        // Stop speech button
        btnStop.setOnClickListener(v -> {
            if (ttsManager != null) {
                ttsManager.stop();
            }
        });

        // Speech rate seekbar (0 to 15 -> 0.5x to 2.0x, step 0.1x, default progress 5 = 1.0x)
        sbSpeechRate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentSpeechRate = 0.5f + (progress * 0.1f);
                String speedCategory;
                if (currentSpeechRate < 0.9f) {
                    speedCategory = getString(R.string.speed_slow);
                } else if (currentSpeechRate <= 1.1f) {
                    speedCategory = getString(R.string.speed_normal);
                } else if (currentSpeechRate <= 1.5f) {
                    speedCategory = getString(R.string.speed_fast);
                } else {
                    speedCategory = getString(R.string.speed_very_fast);
                }
                tvSpeechRate.setText(String.format(Locale.getDefault(), getString(R.string.speech_rate_label_format), currentSpeechRate, speedCategory));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void setupQuickPhrases() {
        layoutQuickPhrasesContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (final String phrase : QUICK_PHRASES) {
            View phraseView = inflater.inflate(R.layout.item_quick_phrase, layoutQuickPhrasesContainer, false);
            TextView tvPhrase = phraseView.findViewById(R.id.tvPhraseText);
            tvPhrase.setText(phrase);

            phraseView.setOnClickListener(v -> {
                // Populate main text input
                etMessage.setText(phrase);
                etMessage.setSelection(phrase.length());

                // Smoothly scroll to the text box so user has direct visibility
                scrollView.smoothScrollTo(0, etMessage.getTop());

                updateStatus("Ready to speak: \"" + phrase + "\"", R.color.status_ready);
            });

            layoutQuickPhrasesContainer.addView(phraseView);
        }
    }

    private void setupTranslationSection() {
        List<TranslationManager.LanguageItem> languages = translationManager.getSupportedLanguages();
        ArrayAdapter<TranslationManager.LanguageItem> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                languages
        );

        spSourceLang.setAdapter(adapter);
        spTargetLang.setAdapter(adapter);

        // Set defaults: English (0) to Hindi (1) or Spanish (2)
        if (languages.size() > 2) {
            spSourceLang.setSelection(0); // English
            spTargetLang.setSelection(1); // Hindi
        }

        // Translate button using real ML Kit on-device translation
        btnTranslate.setOnClickListener(v -> {
            String text = etMessage.getText().toString().trim();
            if (text.isEmpty()) {
                updateStatus("Enter text above before requesting translation", R.color.status_phase);
                Toast.makeText(MainActivity.this, "Please type a message first", Toast.LENGTH_SHORT).show();
                return;
            }

            TranslationManager.LanguageItem source = (TranslationManager.LanguageItem) spSourceLang.getSelectedItem();
            TranslationManager.LanguageItem target = (TranslationManager.LanguageItem) spTargetLang.getSelectedItem();

            String sourceCode = (source != null) ? source.getCode() : "en";
            String targetCode = (target != null) ? target.getCode() : "hi";

            // Disable speak button until translation completes
            btnSpeakTranslation.setEnabled(false);
            btnSpeakTranslation.setAlpha(0.5f);

            translationManager.translate(text, sourceCode, targetCode, new TranslationManager.TranslationCallback() {
                @Override
                public void onStatusUpdate(String statusMessage) {
                    updateStatus(statusMessage, R.color.status_phase);
                    if (tvTranslationNotice != null) {
                        tvTranslationNotice.setText(statusMessage);
                    }
                }

                @Override
                public void onSuccess(String translatedText) {
                    tvTranslatedText.setText(translatedText);
                    tvTranslatedText.setTypeface(null, Typeface.NORMAL);
                    tvTranslatedText.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.text_primary));
                    btnSpeakTranslation.setAlpha(1.0f);
                    btnSpeakTranslation.setEnabled(true);
                    updateStatus(getString(R.string.translation_success), R.color.status_ready);
                    if (tvTranslationNotice != null) {
                        tvTranslationNotice.setText(R.string.translation_status_info);
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    updateStatus(errorMessage, R.color.status_stopped);
                    if (tvTranslationNotice != null) {
                        tvTranslationNotice.setText(errorMessage);
                    }
                    Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                }
            });
        });

        // Speak translated text button (speaks in target language when voice is available)
        btnSpeakTranslation.setEnabled(false);
        btnSpeakTranslation.setAlpha(0.5f);
        btnSpeakTranslation.setOnClickListener(v -> {
            CharSequence translated = tvTranslatedText.getText();
            if (translated != null && !translated.toString().equals(getString(R.string.translated_text_placeholder)) && !translated.toString().trim().isEmpty()) {
                TranslationManager.LanguageItem target = (TranslationManager.LanguageItem) spTargetLang.getSelectedItem();
                String targetCode = (target != null) ? target.getCode() : null;
                if (ttsManager != null) {
                    ttsManager.speak(translated.toString(), currentSpeechRate, targetCode);
                }
            } else {
                Toast.makeText(MainActivity.this, "No translated text available to speak", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateStatus(String statusText, int colorResId) {
        if (tvStatus != null) {
            tvStatus.setText(statusText);
        }
        if (viewStatusDot != null) {
            viewStatusDot.setBackgroundTintList(ContextCompat.getColorStateList(this, colorResId));
        }
    }

    // --- TtsManager.TtsCallback Implementations ---

    @Override
    public void onTtsReady() {
        updateStatus(getString(R.string.status_ready), R.color.status_ready);
    }

    @Override
    public void onTtsError(String message) {
        updateStatus(message, R.color.status_stopped);
    }

    @Override
    public void onSpeechStarted() {
        updateStatus(getString(R.string.status_speaking), R.color.status_speaking);
    }

    @Override
    public void onSpeechCompleted() {
        updateStatus(getString(R.string.status_ready), R.color.status_ready);
    }

    @Override
    public void onSpeechStopped() {
        updateStatus(getString(R.string.status_stopped), R.color.status_stopped);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ttsManager != null) {
            ttsManager.shutdown();
        }
        if (translationManager != null) {
            translationManager.destroy();
        }
    }
}