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

/**
 * Singleton wrapper around the Google Gemini generative AI client.
 * <p>
 * Use {@link #getInstance()} to obtain the shared instance, then call
 * {@link #sendTextPrompt} for text-only queries or
 * {@link #sendTextWIthPhotoPrompt} for multimodal (text + image) queries.
 * All network calls are dispatched on a dedicated background thread; results
 * are delivered via {@link GeminiCallBack}.
 * </p>
 */
public class GeminiManager {
    private static GeminiManager instance;
    private final GenerativeModelFutures model;
    private final Executor executor = Executors.newSingleThreadExecutor();

    /** Private constructor — use {@link #getInstance()}. */
    private GeminiManager() {
        GenerativeModel gm = new GenerativeModel(
            "gemini-2.5-flash",
            BuildConfig.Gemini_API_Key
        );
        model = GenerativeModelFutures.from(gm);
    }

    /**
     * Returns the application-wide singleton instance, creating it on first call.
     *
     * @return the shared {@code GeminiManager}
     */
    public static synchronized GeminiManager getInstance() {
        if (instance == null) {
            instance = new GeminiManager();
        }
        return instance;
    }

    /**
     * Sends a text-only prompt to the Gemini model and delivers the response asynchronously.
     *
     * @param prompt   the text prompt to send
     * @param callBack receives {@link GeminiCallBack#onSuccess} with the model's text,
     *                 or {@link GeminiCallBack#onFailure} on error
     */
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

    /**
     * Sends a multimodal prompt (text + image) to the Gemini model and delivers the response asynchronously.
     *
     * @param prompt   the text portion of the prompt
     * @param bitmap   the image to include alongside the prompt
     * @param callBack receives {@link GeminiCallBack#onSuccess} with the model's text,
     *                 or {@link GeminiCallBack#onFailure} on error
     */
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
