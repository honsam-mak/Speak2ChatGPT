package com.uncle.Speak2ChatGPT;

import android.app.Activity;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ChatGPTApi {
    private static final String API_URL = "https://api.openai.com/v1/completions";
    private static final String TAG = "ChatGPTAPI";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient mClient = new OkHttpClient.Builder()
                                                         .connectTimeout(30, TimeUnit.SECONDS)
                                                         .readTimeout(30, TimeUnit.SECONDS)
                                                         .build();
    private Activity activity;
    private StringBuilder responseTextBuilder = new StringBuilder();

    public ChatGPTApi(Activity activity) {
        this.activity = activity;
    }

    public String generateResponse(String prompt) throws IOException {
        responseTextBuilder.setLength(0);

        JSONObject json = new JSONObject();
        try {
            json.put("model", "text-davinci-003");
            json.put("prompt", prompt);
            json.put("temperature", 0.5);
            json.put("n", 1);
            json.put("max_tokens", 2048);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        RequestBody body = RequestBody.create(json.toString(), JSON);

        //String promptJson = "{\"model\":\"text-davinci-003\", \"prompt\":\"" + prompt + "\",\"temperature\":0.5,\"n\":1,\"max_tokens\":2048}";
        Request request = new Request.Builder()
                .url(API_URL)
                .header("Authorization", "Bearer " + ((MainActivity) activity).getAPIKey())
                .header("Content-Type", "application/json")
                //.post(okhttp3.RequestBody.create(promptJson, okhttp3.MediaType.parse("application/json")))
                .post(RequestBody.create(json.toString(), JSON))
                .build();

        mClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

                if (response.isSuccessful()) {
                    String responseText = response.body().string();
                    Log.i(TAG, "responseText: " +responseText);

                    // Check if response has multiple parts
                    boolean hasMultipleParts = checkIfResponseHasMultipleParts(responseText);

                    if (hasMultipleParts) {
                        // If response has multiple parts, extract each part and append to responseTextBuilder
                        List<String> responseParts = extractResponseParts(extractResponseText(responseText));
                        for (String part : responseParts) {
                            responseTextBuilder.append(part);
                        }
                    } else {
                        // If response is a single part, append to responseTextBuilder
                        responseTextBuilder.append(extractResponseText(responseText));
                    }
                }

                ((MainActivity) activity).changeText(responseTextBuilder.toString());
            }
        });

        return responseTextBuilder.toString();
    }

    private boolean checkIfResponseHasMultipleParts(String responseText) {
        // Check if response has multiple parts by checking for a separator
        return responseText.contains("-----");
    }

    private List<String> extractResponseParts(String responseText) {
        List<String> parts = new ArrayList<>();

        // Split response by separator
        String[] splitResponse = responseText.split("-----");

        // Remove empty strings from split response
        for (String part : splitResponse) {
            if (!part.trim().isEmpty()) {
                parts.add(part.trim());
            }
        }

        return parts;
    }

    private String extractResponseText(String responseData) {
        String responseText = "";
        String updatedText = "";
        try {
            responseText = responseData.split("\"text\":\"")[1].split("\",\"")[0];
            updatedText = responseText.trim().replace("\\n\\n", "");
        } catch (Exception e) {
            Log.e("TAG", "提取回應文字出錯：" + e.getMessage());
        }
        return updatedText;
    }
}
