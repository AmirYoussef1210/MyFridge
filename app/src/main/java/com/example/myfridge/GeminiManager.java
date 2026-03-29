package com.example.myfridge;

import android.graphics.Bitmap;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class GeminiManager {
    private static GeminiManager instance;
    private final GenerativeModelFutures model;
    private final Executor executor = Executors.newSingleThreadExecutor();

    private GeminiManager() {
        GenerativeModel gm = new GenerativeModel(
            "gemini-2.5-flash",
            BuildConfig.Gemini_API_Key
        );
        model = GenerativeModelFutures.from(gm);
    }

    public static synchronized GeminiManager getInstance() {
        if (instance == null) {
            instance = new GeminiManager();
        }
        return instance;
    }

    public void sendTextPrompt(String prompt, GeminiCallBack callBack) {
        Content content = new Content.Builder()
                .addText(prompt)
                .build();
        Futures.addCallback(model.generateContent(content), new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                String text = result.getText();
                if (text != null) callBack.onSuccess(text);
                else callBack.onFailure(new Exception("No text in response"));
            }
            @Override
            public void onFailure(Throwable t) { callBack.onFailure(t); }
        }, executor);
    }

    public void sendTextWIthPhotoPrompt(String prompt, Bitmap bitmap, GeminiCallBack callBack) {
        Content content = new Content.Builder()
                .addText(prompt)
                .addImage(bitmap)
                .build();

        ListenableFuture<GenerateContentResponse> response = model.generateContent(content);

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                String text = result.getText();
                if (text != null) {
                    callBack.onSuccess(text);
                } else {
                    callBack.onFailure(new Exception("No text in response"));
                }
            }

            @Override
            public void onFailure(Throwable t) {
                callBack.onFailure(t);
            }
        }, executor);
    }
}
