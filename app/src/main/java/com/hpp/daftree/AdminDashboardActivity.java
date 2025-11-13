package com.hpp.daftree;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.hpp.daftree.database.User;
import com.hpp.daftree.databinding.ActivityAdminDashboardBinding;
import com.hpp.daftree.syncmanagers.SyncPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class AdminDashboardActivity extends AppCompatActivity {

    private ActivityAdminDashboardBinding binding;
    private TextView tvTotalUsers, tvNewUsersToday;
    private TextInputEditText etNotificationTitle, etNotificationBody;
    private CircularProgressIndicator statsProgress;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private final String FIREBASE_PROJECT_ID = "hpp-daftree";
    private SyncPreferences sharedPrefsManager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // تهيئة Firebase
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        sharedPrefsManager = new SyncPreferences(this);
        // إعداد الأدوات
        setupToolbar();

        // تهيئة العناصر
        initializeViews();

        // تحميل الإحصائيات
        loadUserStats();

        // إعداد النقرات
        setupClickListeners();
    }

    private void setupToolbar() {
        Toolbar toolbar = binding.adminToolbar;
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_arrow_back);
        }
    }

    private void initializeViews() {
        tvTotalUsers = binding.tvTotalUsers;
        tvNewUsersToday = binding.tvNewUsersToday;
        statsProgress = binding.statsProgress;
        etNotificationTitle = binding.etNotificationTitle;
        etNotificationBody = binding.etNotificationBody;
    }

    private void setupClickListeners() {
        // تحديث الإحصائيات
        binding.btnRefreshStats.setOnClickListener(v -> loadUserStats());

        // إرسال الإشعار
        binding.btnSendNotification.setOnClickListener(v -> sendGeneralNotification());
        binding.btnMakePremum.setOnClickListener(v -> activateUserPremium());
        // تصدير المستخدمين
        binding.btnExportUsers.setOnClickListener(v -> {
            String url = "https://console.firebase.google.com/project/" + FIREBASE_PROJECT_ID + "/firestore";
            openWebPage(url);
        });

        // فتح Mailchimp
        binding.btnOpenMailchimp.setOnClickListener(v -> {
            openWebPage("https://mailchimp.com/");
        });

        // إدارة المستخدمين
        binding.btnUserManagement.setOnClickListener(v -> {
            String url = "https://console.firebase.google.com/project/" + FIREBASE_PROJECT_ID + "/authentication/users";
            openWebPage(url);
        });

        // إعدادات التطبيق
        binding.btnAppSettings.setOnClickListener(v -> {
            Toast.makeText(this, "فتح إعدادات التطبيق", Toast.LENGTH_SHORT).show();
            // يمكنك استبدال هذا بفتح نشاط الإعدادات
        });
    }
    private void activateUserPremium1(){
        String email = binding.emailEditText.getText().toString().trim();

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, getString(R.string.error_invalid_email), Toast.LENGTH_SHORT).show();
            return;
        }
    }
    private void activateUserPremium2() {
        String email = binding.emailEditText.getText().toString().trim();

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, getString(R.string.error_invalid_email), Toast.LENGTH_SHORT).show();
            return;
        }

        // إظهار مؤشر التقدم
        binding.btnMakePremum.setEnabled(false);
        binding.btnMakePremum.setText("جاري التفعيل...");

        // البحث عن المستخدم بواسطة البريد الإلكتروني
        db.collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        // الحصول على أول مستخدم يطابق البريد الإلكتروني
                        String userId = task.getResult().getDocuments().get(0).getId();

                        // تحديث حقل is_premium
                        db.collection("users").document(userId)
                                .update("is_premium", true)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(this, "تم تفعيل البريميوم للمستخدم بنجاح", Toast.LENGTH_SHORT).show();
                                    binding.emailEditText.setText("");

                                    // إعادة تعيين الزر
                                    binding.btnMakePremum.setEnabled(true);
                                    binding.btnMakePremum.setText("تفعيل البريميوم");
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(this, "فشل في التحديث: " + e.getMessage(), Toast.LENGTH_SHORT).show();

                                    // إعادة تعيين الزر
                                    binding.btnMakePremum.setEnabled(true);
                                    binding.btnMakePremum.setText("تفعيل البريميوم");
                                });
                    } else {
                        Toast.makeText(this, "لم يتم العثور على مستخدم بهذا البريد الإلكتروني", Toast.LENGTH_SHORT).show();

                        // إعادة تعيين الزر
                        binding.btnMakePremum.setEnabled(true);
                        binding.btnMakePremum.setText("تفعيل البريميوم");
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "خطأ في البحث: " + e.getMessage(), Toast.LENGTH_SHORT).show();

                    // إعادة تعيين الزر
                    binding.btnMakePremum.setEnabled(true);
                    binding.btnMakePremum.setText("تفعيل البريميوم");
                });
    }

    private void activateUserPremium() {
        String email = binding.emailEditText.getText().toString().trim();

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, getString(R.string.error_invalid_email), Toast.LENGTH_SHORT).show();
            return;
        }
        String userType = sharedPrefsManager.getUserType() != null ? sharedPrefsManager.getUserType() : "user";
        if (!Objects.equals(userType, "admin")){
            Toast.makeText(this, getString(R.string.error_admin_only), Toast.LENGTH_SHORT).show();
            return;
        }
        // إظهار مؤشر التقدم
        binding.btnMakePremum.setEnabled(false);
        binding.btnMakePremum.setText("جاري التفعيل...");

        // البحث عن المستخدم بواسطة البريد الإلكتروني
        db.collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        // الحصول على أول مستخدم يطابق البريد الإلكتروني
                        String userId = task.getResult().getDocuments().get(0).getId();

                        // تحديث حقل is_premium إلى true
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("is_premium", true);
                        updates.put("premium_activated_at", User.getCurrentLocalDateTime());
                        updates.put("premium_activated_by", auth.getCurrentUser().getUid());
                        updates.put("premium_activated_byName", auth.getCurrentUser().getDisplayName());

                        db.collection("users").document(userId)
                                .update(updates)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(this, "تم تفعيل البريميوم للمستخدم بنجاح", Toast.LENGTH_SHORT).show();
                                    binding.emailEditText.setText("");
                                    // إعادة تعيين الزر
                                    binding.btnMakePremum.setEnabled(true);
                                    binding.btnMakePremum.setText("تفعيل البريميوم");
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(this, "فشل في التحديث: " + e.getMessage(), Toast.LENGTH_SHORT).show();

                                    // إعادة تعيين الزر
                                    binding.btnMakePremum.setEnabled(true);
                                    binding.btnMakePremum.setText("تفعيل البريميوم");
                                });
                    } else {
                        Toast.makeText(this, "لم يتم العثور على مستخدم بهذا البريد الإلكتروني", Toast.LENGTH_SHORT).show();

                        // إعادة تعيين الزر
                        binding.btnMakePremum.setEnabled(true);
                        binding.btnMakePremum.setText("تفعيل البريميوم");
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "خطأ في البحث: " + e.getMessage(), Toast.LENGTH_SHORT).show();

                    // إعادة تعيين الزر
                    binding.btnMakePremum.setEnabled(true);
                    binding.btnMakePremum.setText("تفعيل البريميوم");
                });
    }
    private void loadUserStats() {
        statsProgress.setVisibility(View.VISIBLE);
        binding.btnRefreshStats.setEnabled(false);

        // الحصول على إجمالي عدد المستخدمين
        db.collection("users")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        int totalUsers = task.getResult().size();
                        tvTotalUsers.setText(String.valueOf(totalUsers));

                        // الحصول على عدد المستخدمين الجدد اليوم
                        getNewUsersToday(totalUsers);
                    } else {
                        Toast.makeText(this, "فشل في تحميل الإحصائيات", Toast.LENGTH_SHORT).show();
                        statsProgress.setVisibility(View.GONE);
                        binding.btnRefreshStats.setEnabled(true);
                    }
                });
    }

    private void getNewUsersToday1(int totalUsers) {
        // الحصول على بداية اليوم
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date startOfDay = calendar.getTime();

        // استعلام للمستخدمين المنشئين اليوم
        db.collection("users")
                .whereGreaterThanOrEqualTo("created_at", startOfDay)
                .get()
                .addOnCompleteListener(task -> {
                    statsProgress.setVisibility(View.GONE);
                    binding.btnRefreshStats.setEnabled(true);

                    if (task.isSuccessful()) {
                        int newUsersToday = task.getResult().size();
                        tvNewUsersToday.setText(String.valueOf(newUsersToday));

                        // تحديث واجهة الإحصائيات مع رسوم متحركة
                        animateStatistics(totalUsers, newUsersToday);
                    } else {
                        Toast.makeText(this, "فشل في تحميل الإحصائيات اليومية", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void getNewUsersToday(int totalUsers) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date startOfDay = calendar.getTime();
        // تحويل إلى Timestamp للاستعلام
//        com.google.firebase.Timestamp startOfDayTimestamp = new com.google.firebase.Timestamp(startOfDay);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss a z", Locale.US);
        String startOfDayString = sdf.format(startOfDay);
        // استعلام للمستخدمين المنشئين اليوم من collection "users"
        Task<QuerySnapshot> usersTask = db.collection("users")
                .whereGreaterThanOrEqualTo("created_at", startOfDayString)
                .get();

        // استعلام للضيوف المنشئين اليوم من collection "guests"
        Task<QuerySnapshot> guestsTask = db.collection("guests")
                .whereGreaterThanOrEqualTo("created_at", startOfDayString)
                .get();

        // انتظار اكتمال كلا الاستعلامين
        Tasks.whenAll(usersTask, guestsTask).addOnCompleteListener(task -> {
            statsProgress.setVisibility(View.GONE);
            binding.btnRefreshStats.setEnabled(true);

            if (task.isSuccessful()) {
                int totalNewUsersToday = 0;

                if (usersTask.isSuccessful()) {
                    totalNewUsersToday += usersTask.getResult().size();
                } else {
                    Toast.makeText(this, "فشل في تحميل المستخدمين الجدد", Toast.LENGTH_SHORT).show();
                }

                if (guestsTask.isSuccessful()) {
                    totalNewUsersToday += guestsTask.getResult().size();
                } else {
                    Toast.makeText(this, "فشل في تحميل الضيوف الجدد", Toast.LENGTH_SHORT).show();
                }

                tvNewUsersToday.setText(String.valueOf(totalNewUsersToday));
                animateStatistics(totalUsers, totalNewUsersToday);
            } else {
                Toast.makeText(this, "فشل في تحميل الإحصائيات اليومية", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void animateStatistics(int totalUsers, int newUsersToday) {
        // يمكنك إضافة رسوم متحركة هنا لتحديث الأرقام بشكل تدريجي
        // هذا مثال بسيط لتحديث النصوص مباشرة
        tvTotalUsers.setText(String.valueOf(totalUsers));
        tvNewUsersToday.setText(String.valueOf(newUsersToday));
    }

    private void sendGeneralNotification() {
        String title = etNotificationTitle.getText().toString().trim();
        String body = etNotificationBody.getText().toString().trim();

        if (title.isEmpty()) {
            etNotificationTitle.setError("يرجى إدخال عنوان الإشعار");
            etNotificationTitle.requestFocus();
            return;
        }

        if (body.isEmpty()) {
            etNotificationBody.setError("يرجى إدخال محتوى الإشعار");
            etNotificationBody.requestFocus();
            return;
        }

        // إظهار مؤشر التقدم
        binding.btnSendNotification.setEnabled(false);
        binding.btnSendNotification.setText("جاري الإرسال...");

        Map<String, Object> notification = new HashMap<>();
        notification.put("title", title);
        notification.put("message", body);
        notification.put("target", "all_users");
        notification.put("timestamp", FieldValue.serverTimestamp());
        notification.put("sentBy", auth.getCurrentUser().getUid());
        notification.put("sentAt", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));

        db.collection("notifications")
                .add(notification)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(this, "تم إرسال الإشعار بنجاح", Toast.LENGTH_SHORT).show();
                    etNotificationTitle.setText("");
                    etNotificationBody.setText("");

                    // إعادة تعيين الزر
                    binding.btnSendNotification.setEnabled(true);
                    binding.btnSendNotification.setText("إرسال الإشعار للجميع");
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "فشل في إرسال الإشعار: " + e.getMessage(), Toast.LENGTH_SHORT).show();

                    // إعادة تعيين الزر
                    binding.btnSendNotification.setEnabled(true);
                    binding.btnSendNotification.setText("إرسال الإشعار للجميع");
                });
    }

    private void openWebPage(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "لا يمكن فتح الرابط: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.admin_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (id == R.id.action_logout) {
            logout();
            return true;
        } else if (id == R.id.action_settings) {
            openSettings();
            return true;
        } else if (id == R.id.action_help) {
            openHelp();
            return true;
        } else if (id == R.id.action_about) {
            openAbout();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void logout() {
        auth.signOut();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private void openSettings() {
        Toast.makeText(this, "فتح الإعدادات", Toast.LENGTH_SHORT).show();
        // يمكنك استبدال هذا بفتح نشاط الإعدادات
    }

    private void openHelp() {
        String url = "https://support.daftree.com/admin-guide";
        openWebPage(url);
    }

    private void openAbout() {
        Toast.makeText(this, "تطبيق محفظتي الذكية - الإصدار 1.0.0", Toast.LENGTH_SHORT).show();
        // يمكنك استبدال هذا بفتح نشاط عن التطبيق
    }
}