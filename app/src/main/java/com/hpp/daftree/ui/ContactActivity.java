package com.hpp.daftree.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.hpp.daftree.databinding.ActivityContactBinding;
import com.hpp.daftree.utils.EdgeToEdgeUtils;

public class ContactActivity extends BaseActivity {

    private ActivityContactBinding binding;
    private final String PHONE_NUMBER = "734249712";
    private final String EMAIL_ADDRESS = "salah22app@gmail.com";
    private final String WHATSAPP_NUMBER = "+967734249712"; // مع رمز الدولة وبدون أصفار

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityContactBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        EdgeToEdgeUtils.applyEdgeToEdge(this, binding.toolbarContact);

        binding.toolbarContact.setNavigationOnClickListener(v -> finish());

        setupListeners();
    }

    private void setupListeners() {
        binding.layoutCall.setOnClickListener(v -> openDialer());
        binding.layoutEmail.setOnClickListener(v -> openEmailClient(this));
        binding.layoutWhatsapp.setOnClickListener(v -> openWhatsApp(this));
    }

    private void openDialer() {
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(Uri.parse("tel:" + PHONE_NUMBER));
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "لا يوجد تطبيق اتصال مثبت", Toast.LENGTH_SHORT).show();
        }
    }

    public void openEmailClient(Context context) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:" + EMAIL_ADDRESS));
        intent.putExtra(Intent.EXTRA_SUBJECT, "استفسار بخصوص تطبيق Daftree");
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "لا يوجد تطبيق بريد إلكتروني مثبت", Toast.LENGTH_SHORT).show();
        }
    }

    public void openWhatsApp(Context context) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://api.whatsapp.com/send?phone=" + WHATSAPP_NUMBER));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "تطبيق واتساب غير مثبت", Toast.LENGTH_SHORT).show();
        }
    }
}