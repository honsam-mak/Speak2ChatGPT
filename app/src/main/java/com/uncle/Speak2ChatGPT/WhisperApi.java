package com.uncle.Speak2ChatGPT;

import android.app.Activity;
import android.util.Log;

import okhttp3.*;
import com.google.gson.Gson;
import java.io.IOException;

public class WhisperApi {
    private static final String URL = "https://api.openai.com/v1/audio/transcriptions";
    private static final String TAG = "WhisperAPI";

    private Activity activity;
    private OkHttpClient client;
    private Gson gson;

    public WhisperApi(Activity activity) {
        this.activity = activity;
        client = new OkHttpClient();
        gson = new Gson();
    }

    public String transcribe(byte[] audioData) throws IOException {
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model","whisper-1")
                .addFormDataPart("file", "GPTAudio.mp3",
                        RequestBody.create(audioData, MediaType.parse("audio/mp3")))
                .build();

        Request request = new Request.Builder()
                .url(URL)
                .header("Authorization", "Bearer " + ((MainActivity) activity).getAPIKey())
                .post(requestBody)
                .build();

        Log.i(TAG, "ready to call Whisper API");
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                Log.i(TAG, "Calling Whisper API is successful");


                String responseBody = response.body().string();
                Log.i(TAG, "responseBody: " + responseBody);
                TranscriptionResult result = gson.fromJson(responseBody, TranscriptionResult.class);

                Log.i(TAG, "result: " +result.text);

                return result.text;
            } else {
                Log.i(TAG, "Calling Whisper API is failed");
                throw new IOException("Unexpected response code: " + response.code());
            }
        }
    }

    private static class TranscriptionResult {
        public String text;
    }
}

