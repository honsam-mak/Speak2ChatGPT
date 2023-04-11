package com.uncle.Speak2ChatGPT;

import android.content.pm.PackageManager;
import android.Manifest;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final String TAG = "MediaRecording";

    // GUI Variables
    private Button recordButton;
    private TextView resultTextView;
    private Switch switchButton;

    // Control Variables
    private String API_KEY;
    private MediaRecorder recorder;
    private String outputFilePath;
    private boolean isRecording = false;

    // API Variables
    private WhisperApi whisperApi;
    private ChatGPTApi chatGPTApi;

    // Requesting permission to RECORD_AUDIO
    private boolean permissionToRecordAccepted = false;
    private String [] permissions = {Manifest.permission.RECORD_AUDIO,Manifest.permission.MODIFY_AUDIO_SETTINGS,Manifest.permission.WRITE_EXTERNAL_STORAGE};

    // Speech Variables
    private TextToSpeech mTts;
    private boolean mIsTtsInitialized = false;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case REQUEST_RECORD_AUDIO_PERMISSION:
                permissionToRecordAccepted  = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (!permissionToRecordAccepted ) finish();

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        API_KEY = getResources().getString(R.string.API_KEY);

        // Record to the external cache directory for visibility
        outputFilePath = getExternalCacheDir().getAbsolutePath();
        outputFilePath += "/GPTAudio.mp3";

        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);

        // Initialize Record Button
        recordButton = findViewById(R.id.recordButton);
        recordButton.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startRecording();
                    break;
                case MotionEvent.ACTION_UP:
                    stopRecording();
                    break;
            }
            return true;
        });

        // Initialize Result Text View
        resultTextView = findViewById(R.id.resultTextView);

        // Initialize Switch Button
        switchButton = findViewById(R.id.switch_button);
        switchButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // 開啟某些功能
                } else {
                    // 關閉某些功能
                }
            }
        });

        whisperApi = new WhisperApi(this);
        chatGPTApi = new ChatGPTApi(this);

        mTts = new TextToSpeech(this, this);
    }

    public String getAPIKey() { return API_KEY; }

    private void startRecording() {
        if (isRecording) return;

        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setOutputFile(outputFilePath);

        try {
            recorder.prepare();
            recorder.start();
            isRecording = true;
            Toast.makeText(this, "錄音開始", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopRecording() {
        if (!isRecording) return;

        recorder.stop();
        recorder.release();
        recorder = null;
        isRecording = false;

        Toast.makeText(this, "錄音結束", Toast.LENGTH_SHORT).show();

        // 讀取音頻文件並獲取 byte[] 數據
        byte[] audioData;
        try {
            File audioFile = new File(outputFilePath);
            audioData = Files.readAllBytes(audioFile.toPath());

            // 使用 audioData
            // 首先將其傳遞給 WhisperApi 進行語音識別
            new TranscribeTask().execute(audioData);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void changeText(final String response) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (response != null) {
                    // 在此處使用 ChatGPT 的回應
                    // 例如，您可以將其顯示在 UI 中的 TextView 中
                    resultTextView.setText(response);

                    if ( switchButton.isChecked()) {
                        speakResponse(response);
                    }
                } else {
                    // 處理錯誤
                    Toast.makeText(MainActivity.this, "錯誤：無法獲取 ChatGPT 回應", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void speakResponse(String responseText) {
        if (mIsTtsInitialized) {
            mTts.setSpeechRate(1.2f);
            mTts.speak("chatGPT的回答是" + responseText, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = mTts.setLanguage(Locale.getDefault());
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TAG", "不支持當前語言");
            } else {
                mIsTtsInitialized = true;
            }
        } else {
            Log.e("TAG", "TTS初始化失敗");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mTts != null) {
            mTts.stop();
            mTts.shutdown();
        }
    }

    private class TranscribeTask extends AsyncTask<byte[], Void, String> {
        @Override
        protected String doInBackground(byte[]... audioData) {
            try {
                return whisperApi.transcribe(audioData[0]);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(String transcription) {
            if (transcription != null) {
                // 使用識別後的文字內容，將其傳遞給 ChatGPTApi
                new ChatGPTTask().execute(transcription);
            } else {
                // 處理錯誤
                Toast.makeText(MainActivity.this, "錯誤：無法獲取語音識別結果", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class ChatGPTTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            String prompt = params[0];
            try {
                return chatGPTApi.generateResponse(prompt);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
    }
}


/*
public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {
    private EditText mPromptEditText;
    private TextView mResponseTextView;
    private Button mGenerateButton;
    private TextToSpeech mTts;
    private boolean mIsTtsInitialized = false;

    private final OkHttpClient mClient = new OkHttpClient.Builder()
                                             .connectTimeout(30, TimeUnit.SECONDS)
                                             .readTimeout(30, TimeUnit.SECONDS)
                                             .build();
    private static final String API_KEY = "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";
    private static final String API_URL = "https://api.openai.com/v1/completions";

    private StringBuilder responseTextBuilder = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPromptEditText = findViewById(R.id.promptEditText);
        mResponseTextView = findViewById(R.id.responseTextView);
        mGenerateButton = findViewById(R.id.generateButton);

        mGenerateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                responseTextBuilder.setLength(0);
                generateResponse();
            }
        });

        mTts = new TextToSpeech(this, this);
    }

    private void generateResponse() {
        String prompt = mPromptEditText.getText().toString().trim();
        if (prompt.isEmpty()) {
            mResponseTextView.setText("請輸入一個生成的提示");
            return;
        }

        String accessToken = "Bearer " + API_KEY;
        String promptJson = "{\"model\":\"text-davinci-003\", \"prompt\":\"" + prompt + "\",\"temperature\":0.5,\"n\":1,\"max_tokens\":2048}";
        Request request = new Request.Builder()
                .url(API_URL)
                .post(okhttp3.RequestBody.create(promptJson, okhttp3.MediaType.parse("application/json")))
                .addHeader("Authorization", accessToken)
                .addHeader("Content-Type", "application/json")
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
                    System.out.println(responseText);

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

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String fullResponseText = responseTextBuilder.toString();

                        mResponseTextView.setText(fullResponseText);
                        speakResponse(fullResponseText);
                    }
                });
            }
        });
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

    private void speakResponse(String responseText) {
        if (mIsTtsInitialized) {
            mTts.setSpeechRate(1.2f);
            mTts.speak("chatGPT的回答是" + responseText, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = mTts.setLanguage(Locale.getDefault());
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TAG", "不支持當前語言");
            } else {
                mIsTtsInitialized = true;
            }
        } else {
            Log.e("TAG", "TTS初始化失敗");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mTts != null) {
            mTts.stop();
            mTts.shutdown();
        }
    }
}
*/