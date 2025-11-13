package com.hpp.daftree.utils;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
import com.google.api.services.drive.DriveScopes;
import com.hpp.daftree.LockScreenActivity;
import com.hpp.daftree.LoginActivity;
import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.database.User;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class GoogleDriveHelper {

    private static final String TAG = "GoogleDriveHelper";
    private final LockScreenActivity activity;
    private final DaftreeRepository repository;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final ActivityResultLauncher<Intent> signInLauncher;
    private GoogleSignInClient googleSignInClient;

    public GoogleDriveHelper(LockScreenActivity activity) {
       this.activity = activity;
        this.repository = new DaftreeRepository(activity.getApplication());

        signInLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        handleSignInResult(task);
                    } else {
                        activity.onPasswordUploaded(false);
                    }
                }
        );
    }

    public void startSignInAndUpload() {
        GoogleSignInOptions signInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(DriveScopes.DRIVE_FILE))
                .build();
        googleSignInClient = GoogleSignIn.getClient(activity, signInOptions);

        googleSignInClient.silentSignIn()
                .addOnSuccessListener(this::handleSignInSuccess)
                .addOnFailureListener(e -> {
                    Intent signInIntent = googleSignInClient.getSignInIntent();
                    activity.requestSignIn(signInIntent, signInLauncher);
                });
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult();
            handleSignInSuccess(account);
        } catch (Exception e) {
            Log.w(TAG, "signInResult:failed code=" + e.getMessage());
            activity.onPasswordUploaded(false);
        }
    }

    private void handleSignInSuccess(GoogleSignInAccount account) {
        executor.execute(() -> {
            try {
                User user = repository.getUserDao().getUserProfileBlocking();
                if (user == null || user.getPassword() == null || user.getPassword().isEmpty()) {
                    throw new Exception("لم يتم العثور على كلمة مرور محفوظة.");
                }
                String password = user.getPassword();
                String accessToken = GoogleAuthUtil.getToken(activity, account.getAccount(), "oauth2:" + DriveScopes.DRIVE_FILE);

                // **تم التعديل: استخدام الاسم المطلوب**
                uploadFileToDrive(accessToken, "كلمة المرور في محفظتي الذكية.txt", password);

                activity.runOnUiThread(() -> activity.onPasswordUploaded(true));

            } catch (Exception e) {
                Log.e(TAG, "Upload failed", e);
                activity.runOnUiThread(() -> activity.onPasswordUploaded(false));
            }
        });
    }

    // **هذه هي الدالة الجديدة والمحسّنة التي تضمن حفظ الملف بالاسم الصحيح**
    private void uploadFileToDrive(String accessToken, String fileName, String content) throws Exception {
        // الخطوة 1: البحث عن الملف لمعرفة إذا كان موجودًا
        String fileId = findFileIdByName(accessToken, fileName);

        if (fileId == null) {
            // إذا لم يكن موجودًا، قم بإنشائه أولاً
            fileId = createFile(accessToken, fileName);
        }

        // الخطوة 2: تحديث محتوى الملف (سواء كان جديدًا أو قديمًا)
        updateFileContent(accessToken, fileId, content);
    }

    private String findFileIdByName(String accessToken, String fileName) throws Exception {
        URL url = new URL("https://www.googleapis.com/drive/v3/files?q=name='" + fileName + "' and trashed=false&spaces=drive&fields=files(id)");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + accessToken);

        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
            String response = readResponse(connection);
            JSONObject jsonObject = new JSONObject(response);
            JSONArray files = jsonObject.getJSONArray("files");
            if (files.length() > 0) {
                return files.getJSONObject(0).getString("id");
            }
        }
        return null;
    }

    private String createFile(String accessToken, String fileName) throws Exception {
        JSONObject metadata = new JSONObject();
        metadata.put("name", fileName);
        metadata.put("mimeType", "text/plain");

        URL url = new URL("https://www.googleapis.com/drive/v3/files");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setDoOutput(true);

        try (OutputStream os = connection.getOutputStream()) {
            os.write(metadata.toString().getBytes(StandardCharsets.UTF_8));
        }

        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
            String response = readResponse(connection);
            JSONObject jsonObject = new JSONObject(response);
            return jsonObject.getString("id");
        } else {
            throw new Exception("Failed to create file. Response code: " + connection.getResponseCode());
        }
    }

    private void updateFileContent(String accessToken, String fileId, String content) throws Exception {
        URL url = new URL("https://www.googleapis.com/upload/drive/v3/files/" + fileId + "?uploadType=media");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("PATCH"); // PATCH is for update
        connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        connection.setRequestProperty("Content-Type", "text/plain");
        connection.setDoOutput(true);

        try (OutputStream os = connection.getOutputStream()) {
            os.write(content.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new Exception("Failed to update file content. Response code: " + responseCode);
        }
        Log.d(TAG, "File content updated successfully.");
    }

    private String readResponse(HttpURLConnection connection) throws Exception {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            return response.toString();
        }
    }
}