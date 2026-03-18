package com.example.myfridge;

import com.google.ai.client.generativeai.GenerativeModel;

public class GeminiManager {
    private static GeminiManager instance;
    private GenerativeModel gemini;

    private GeminiManager() {
        gemini = new GenerativeModel(
            "gemini-2.0-flash",
            BuildConfig.Gemini_API_Key
        );
    }

    public static GeminiManager getInstance() {
        if (instance == null) {
            instance = new GeminiManager();
        }
        return instance;
    }
}
