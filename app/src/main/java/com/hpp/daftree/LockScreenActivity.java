package com.hpp.daftree;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.hpp.daftree.databinding.ActivityLockScreenBinding;
import com.hpp.daftree.databinding.DialogChangePasswordBinding;
import com.hpp.daftree.models.AppLockManager;
import com.hpp.daftree.utils.GoogleDriveHelper;

import java.util.concurrent.Executor;

public class LockScreenActivity extends AppCompatActivity {

    private ActivityLockScreenBinding binding;
    private AppLockManager lockManager;
    private ProfileViewModel viewModel; // لاستدعاء بيانات المستخدم
    private Executor executor;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;
    private GoogleDriveHelper googleDriveHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLockScreenBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        lockManager = new AppLockManager(this);
        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        executor = ContextCompat.getMainExecutor(this);
        googleDriveHelper = new GoogleDriveHelper(this);

        String lockType = lockManager.getLockType();

        viewModel.getUserProfile().observe(this, user -> {
            if (user != null && binding.passwordEditText.getText().toString().equals(user.getPassword())) {
                navigateToMain();
            }
        });
        if ("biometric".equals(lockType)) {
            setupBiometricPrompt();
            // إظهار واجهة البصمة مباشرة
            biometricPrompt.authenticate(promptInfo);
            setupPasswordScreen();
        } else if ("password".equals(lockType)) {
            // إظهار واجهة كلمة المرور
            setupPasswordScreen();
        } else {
            // لا يوجد قفل، انتقل للشاشة الرئيسية (حالة احتياطية)
            navigateToMain();
        }
    }

    private void setupBiometricPrompt() {
        biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                navigateToMain();
            }

            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                // إذا ألغى المستخدم العملية، أغلق التطبيق
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    finish();
                }
            }
        });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.lock_title))
                .setSubtitle(getString(R.string.use_biometric))
                .setNegativeButtonText(getString(R.string.close))
                .build();
    }


    private void verifyPassword() {
        String enteredPassword = binding.passwordEditText.getText().toString();
        if (TextUtils.isEmpty(enteredPassword)) {
            binding.passwordLayout.setError(getString(R.string.lock_error_empty));
            return;
        }

        viewModel.getUserProfile().observe(this, user -> {
            if (user != null && enteredPassword.equals(user.getPassword())) {
                navigateToMain();
            } else {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.error_text))
                        .setMessage(getString(R.string.lock_error_incorrect))
                        .setPositiveButton(getString(R.string.ok), null)
                        .show();
                binding.passwordEditText.setText("");
            }
        });
    }
    private void setupPasswordScreen() {
        binding.passwordEditText.setVisibility(View.VISIBLE);
        binding.passwordEditText.setText("");

        // إعداد المستمع للتغيير التلقائي (يفتح عند إدخال الكلمة الصحيحة)
        binding.passwordEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String enteredPassword = s.toString().trim();
                if (!enteredPassword.isEmpty()) {
                    verifyPasswordAutomatically(enteredPassword);
                }
            }
        });

        // زر الدخول اليدوي
        binding.btnEnter.setOnClickListener(v -> {
            String enteredPassword = binding.passwordEditText.getText().toString().trim();
            verifyPasswordManually(enteredPassword);
        });

        binding.tvChangePassword.setOnClickListener(v -> showChangePasswordDialog());
        binding.tvForgotPassword.setOnClickListener(v -> handleForgotPassword());
    }
    private void verifyPasswordManually(String enteredPassword) {
        // استخدام AppLockManager للتحقق من كلمة المرور بدلاً من ViewModel
        viewModel.getUserProfile().observe(this, user -> {
            if (user != null && enteredPassword.equals(user.getPassword())) {
                Log.d("TAG", "Password verified automatically - opening app");
                navigateToMain();
            } else {
                Log.d("TAG", "Password verification failed");
                new MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.error_text))
                        .setMessage(getString(R.string.lock_error_incorrect))
                        .setPositiveButton(getString(R.string.ok), null)
                        .show();
                binding.passwordEditText.setText("");
            }
        });

    }
    private void verifyPasswordAutomatically(String enteredPassword) {
        // استخدام AppLockManager للتحقق من كلمة المرور بدلاً من ViewModel
           viewModel.getUserProfile().observe(this, user -> {
            if (user != null && enteredPassword.equals(user.getPassword())) {
                     Log.d("TAG", "Password verified automatically - opening app");
                    navigateToMain();
                } else {
                    Log.d("TAG", "Password verification failed");

            }
        });

    }


    private void verifyPassword1() {
        String enteredPassword = binding.passwordEditText.getText().toString();
        if (TextUtils.isEmpty(enteredPassword)) {
            binding.passwordLayout.setError("يرجى إدخال كلمة المرور");
            return;
        }

        viewModel.getUserProfile().observe(this, user -> {
            if (user != null && enteredPassword.equals(user.getPassword())) {
                navigateToMain();
            } else {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("خطأ")
                        .setMessage("كلمة المرور التي أدخلتها غير صحيحة.")
                        .setPositiveButton("موافق", null)
                        .show();
                binding.passwordEditText.setText("");
            }
        });
    }

    private void showChangePasswordDialog() {
        DialogChangePasswordBinding dialogBinding = DialogChangePasswordBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.change_password_title))
                .setView(dialogBinding.getRoot())
                .setPositiveButton(getString(R.string.change_password_confirm), null)
                .setNegativeButton(getString(R.string.change_password_cancel), null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String oldPass = dialogBinding.oldPasswordEdit.getText().toString();
                String newPass = dialogBinding.newPasswordEdit.getText().toString();
                String confirmPass = dialogBinding.confirmPasswordEdit.getText().toString();

                // التحقق من صحة المدخلات
                if (TextUtils.isEmpty(oldPass) || TextUtils.isEmpty(newPass) || TextUtils.isEmpty(confirmPass)) {
                    Toast.makeText(this, getString(R.string.change_password_error_fields), Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!newPass.equals(confirmPass)) {
                    dialogBinding.confirmPasswordLayout.setError(getString(R.string.change_password_error_mismatch));
                    return;
                }

                // التحقق من كلمة المرور القديمة وحفظ الجديدة
                viewModel.getUserProfile().observe(this, user -> {
                    if (user != null && oldPass.equals(user.getPassword())) {
                        user.setPassword(newPass);
                        viewModel.updateUserProfile(user);
                        Toast.makeText(this, getString(R.string.change_password_success), Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    } else {
                        dialogBinding.oldPasswordLayout.setError(getString(R.string.change_password_error_old_incorrect));
                    }
                });
            });
        });
        dialog.show();
    }

    private void handleForgotPassword() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.forgot_password_lock_title))
                .setMessage(getString(R.string.forgot_password_lock_message))
                .setPositiveButton(getString(R.string.forgot_password_lock_yes), (d, w) -> {
                    googleDriveHelper.startSignInAndUpload();
                })
                .setNegativeButton(getString(R.string.forgot_password_lock_cancel), null)
                .show();

    }
    private void showChangePasswordDialog1() {
        DialogChangePasswordBinding dialogBinding = DialogChangePasswordBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("تغيير كلمة المرور")
                .setView(dialogBinding.getRoot())
                .setPositiveButton("تأكيد التغيير", null)
                .setNegativeButton("إلغاء", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String oldPass = dialogBinding.oldPasswordEdit.getText().toString();
                String newPass = dialogBinding.newPasswordEdit.getText().toString();
                String confirmPass = dialogBinding.confirmPasswordEdit.getText().toString();

                // التحقق من صحة المدخلات
                if (TextUtils.isEmpty(oldPass) || TextUtils.isEmpty(newPass) || TextUtils.isEmpty(confirmPass)) {
                    Toast.makeText(this, "يرجى ملء جميع الحقول", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!newPass.equals(confirmPass)) {
                    dialogBinding.confirmPasswordLayout.setError("كلمة المرور الجديدة غير متطابقة");
                    return;
                }

                // التحقق من كلمة المرور القديمة وحفظ الجديدة
                viewModel.getUserProfile().observe(this, user -> {
                    if (user != null && oldPass.equals(user.getPassword())) {
                        user.setPassword(newPass);
                        viewModel.updateUserProfile(user);
                        Toast.makeText(this, "تم تغيير كلمة المرور بنجاح", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    } else {
                        dialogBinding.oldPasswordLayout.setError("كلمة المرور القديمة غير صحيحة");
                    }
                });
            });
        });
        dialog.show();
    }

    private void handleForgotPassword1() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("نسيت كلمة المرور؟")
                .setMessage("سيتم الآن محاولة رفع ملف نصي يحتوي على كلمة المرور إلى حسابك في جوجل درايف. هل تريد المتابعة؟")
                .setPositiveButton("نعم، متابعة", (d, w) -> {
                    // استدعاء المساعد الخاص بجوجل درايف
                    googleDriveHelper.startSignInAndUpload();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    // هذه الدالة يتم استدعاؤها من GoogleDriveHelper بعد انتهاء عملية الرفع
    public void onPasswordUploaded1(boolean success) {
        if (success) {
            Toast.makeText(this, "تم رفع كلمة المرور إلى جوجل درايف بنجاح.", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "فشل رفع كلمة المرور. يرجى التحقق من اتصالك بالإنترنت والمحاولة مرة أخرى.", Toast.LENGTH_LONG).show();
        }
    }
    public void onPasswordUploaded(boolean success) {
        if (success) {
            Toast.makeText(this, getString(R.string.password_upload_success), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, getString(R.string.password_upload_failed), Toast.LENGTH_LONG).show();
        }
    }

    // هذه الدالة يتم استدعاؤها من GoogleDriveHelper عند الحاجة لتسجيل الدخول
    public void requestSignIn(Intent signInIntent, ActivityResultLauncher<Intent> launcher) {
        launcher.launch(signInIntent);
    }

    private void navigateToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}