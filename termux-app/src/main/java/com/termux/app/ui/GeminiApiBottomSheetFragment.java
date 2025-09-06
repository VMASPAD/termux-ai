package com.termux.app.ui;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputEditText;
import com.termux.R;
import com.termux.app.TermuxConstants;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap; // Using LinkedHashMap to preserve insertion order for the spinner
import java.util.Map;
import java.util.ArrayList;

public class GeminiApiBottomSheetFragment extends BottomSheetDialogFragment {

    private TextInputEditText apiKeyEditText;
    private TextInputEditText promptEditText;
    private Button sendButton;
    private ProgressBar loadingProgressBar;
    private TextView loadingTextView;
    private TextView responseTextView;
    private Spinner modelSpinner;

    // Using LinkedHashMap to maintain insertion order for Spinner display
    private final Map<String, String> geminiModels = new LinkedHashMap<>();

    private static final String GEMINI_API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String GEMINI_API_ACTION = ":generateContent";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Initialize models (Display Name -> API Model ID)
        // Clarification: Using "1.5" as "2.5" models are not yet standard for this API endpoint format.
        // Adjust API Model IDs if you have specific ones for Lite/Preview.
        geminiModels.put("Gemini 1.5 Pro", "gemini-1.5-pro-latest");
        geminiModels.put("Gemini 1.5 Flash", "gemini-1.5-flash-latest");
        geminiModels.put("Gemini 1.5 Flash-Lite", "gemini-1.5-flash-latest"); // Placeholder ID
        geminiModels.put("Gemini 1.5 Flash Preview", "gemini-1.5-flash-latest"); // Placeholder ID
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_gemini_api, container, false);

        apiKeyEditText = view.findViewById(R.id.edit_text_api_key);
        promptEditText = view.findViewById(R.id.edit_text_prompt);
        sendButton = view.findViewById(R.id.button_send_prompt);
        loadingProgressBar = view.findViewById(R.id.progress_bar_loading);
        loadingTextView = view.findViewById(R.id.text_view_loading);
        responseTextView = view.findViewById(R.id.text_view_response);
        modelSpinner = view.findViewById(R.id.spinner_gemini_models);

        // Populate Spinner
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, new ArrayList<>(geminiModels.keySet()));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modelSpinner.setAdapter(adapter);

        // Text is already selectable via XML (android:textIsSelectable="true")
        // responseTextView.setTextIsSelectable(true); // Redundant if XML is set, but doesn't hurt

        sendButton.setOnClickListener(v -> {
            final String currentApiKey;
            if (apiKeyEditText.getText() != null) {
                currentApiKey = apiKeyEditText.getText().toString().trim();
            } else {
                currentApiKey = "";
            }

            final String currentPrompt;
            if (promptEditText.getText() != null) {
                currentPrompt = promptEditText.getText().toString().trim();
            } else {
                currentPrompt = "";
            }

            if (currentApiKey.isEmpty()) {
                responseTextView.setText("API Key cannot be empty.");
                responseTextView.setVisibility(View.VISIBLE);
                return;
            }

            if (currentPrompt.isEmpty()) {
                responseTextView.setText("Prompt cannot be empty.");
                responseTextView.setVisibility(View.VISIBLE);
                return;
            }

            String selectedModelDisplayName = modelSpinner.getSelectedItem().toString();
            final String selectedModelApiId = geminiModels.get(selectedModelDisplayName);

            if (selectedModelApiId == null) {
                responseTextView.setText("Invalid model selected.");
                responseTextView.setVisibility(View.VISIBLE);
                return;
            }

            Context context = getContext();
            if (context != null && getView() != null) {
                InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);
                }
            }

            Log.d(TermuxConstants.LOG_TAG, "API Key: " + currentApiKey + ", Model: " + selectedModelApiId + ", Prompt: " + currentPrompt);

            loadingProgressBar.setVisibility(View.VISIBLE);
            loadingTextView.setVisibility(View.VISIBLE);
            responseTextView.setVisibility(View.GONE);

            new Thread(() -> {
                String result = performGeminiApiCall(currentApiKey, currentPrompt, selectedModelApiId);
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (getContext() == null || !isAdded()) return;
                    loadingProgressBar.setVisibility(View.GONE);
                    loadingTextView.setVisibility(View.GONE);
                    responseTextView.setText(result);
                    responseTextView.setVisibility(View.VISIBLE);
                });
            }).start();
        });

        return view;
    }

    private String performGeminiApiCall(String apiKey, String prompt, String modelId) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(GEMINI_API_BASE_URL + modelId + GEMINI_API_ACTION);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("X-goog-api-key", apiKey);
            connection.setDoOutput(true);

            JSONObject textPart = new JSONObject();
            textPart.put("text", "No markdown response: " + prompt);
            JSONArray partsArray = new JSONArray();
            partsArray.put(textPart);
            JSONObject content = new JSONObject();
            content.put("parts", partsArray);
            JSONArray contentsArray = new JSONArray();
            contentsArray.put(content);
            JSONObject payload = new JSONObject();
            payload.put("contents", contentsArray);

            String jsonPayload = payload.toString();
            Log.d(TermuxConstants.LOG_TAG, "Request URL: " + url.toString());
            Log.d(TermuxConstants.LOG_TAG, "Request Payload: " + jsonPayload);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            Log.d(TermuxConstants.LOG_TAG, "Response Code: " + responseCode);

            StringBuilder response = new StringBuilder();
            BufferedReader reader;

            if (responseCode >= 200 && responseCode < 300) {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            } else {
                reader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8));
            }
            
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line.trim());
            }
            reader.close();

            Log.d(TermuxConstants.LOG_TAG, "Raw Response: " + response.toString());

            if (responseCode >= 200 && responseCode < 300) {
                return parseSuccessResponse(response.toString());
            } else {
                return "Error (HTTP " + responseCode + "): " + parseErrorResponse(response.toString());
            }

        } catch (Exception e) {
            Log.e(TermuxConstants.LOG_TAG, "Error during API call", e);
            return "Exception: " + e.getMessage();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String parseSuccessResponse(String jsonResponse) {
        try {
            JSONObject responseObject = new JSONObject(jsonResponse);
            JSONArray candidates = responseObject.getJSONArray("candidates");
            if (candidates.length() > 0) {
                JSONObject firstCandidate = candidates.getJSONObject(0);
                JSONObject content = firstCandidate.getJSONObject("content");
                JSONArray parts = content.getJSONArray("parts");
                if (parts.length() > 0) {
                    JSONObject firstPart = parts.getJSONObject(0);
                    return firstPart.getString("text");
                }
            }
            return "Error: Could not parse generated text from response.";
        } catch (JSONException e) {
            Log.e(TermuxConstants.LOG_TAG, "Error parsing success JSON response", e);
            return "Error parsing response: " + e.getMessage() + "\nResponse: " + jsonResponse;
        }
    }

    private String parseErrorResponse(String jsonErrorResponse) {
        try {
            JSONObject errorObject = new JSONObject(jsonErrorResponse);
            JSONObject error = errorObject.getJSONObject("error");
            String message = error.getString("message");
            return message;
        } catch (JSONException e) {
            Log.e(TermuxConstants.LOG_TAG, "Error parsing error JSON", e);
            return jsonErrorResponse;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }
}
