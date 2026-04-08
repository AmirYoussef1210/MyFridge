package com.example.myfridge;

/**
 * Callback interface for asynchronous Gemini AI responses.
 * Implemented by callers of {@link GeminiManager#sendTextPrompt} and
 * {@link GeminiManager#sendTextWIthPhotoPrompt}.
 */
public interface GeminiCallBack {

    /**
     * Called when the Gemini model returns a successful text response.
     *
     * @param result the raw text returned by the model; never {@code null}
     */
    void onSuccess(String result);

    /**
     * Called when the request fails for any reason (network error, API error, empty response, etc.).
     *
     * @param error the cause of the failure
     */
    void onFailure(Throwable error);
}
