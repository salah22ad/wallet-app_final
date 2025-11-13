package com.hpp.daftree.dialogs;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.hpp.daftree.R;
import com.hpp.daftree.utils.LicenseManager;

import java.util.Arrays;
import java.util.List;

public class PurchaseCodeDialog extends DialogFragment {

    private LicenseManager licenseManager;
    private String generatedCode;
    private BillingClient billingClient;
    private ProductDetails productDetails;

    // استبدل هذا بـ product ID الخاص بك من Google Play Console
    private static final String PRODUCT_ID = "4974744472638463351";
    private static final String TAG = "PurchaseCodeDialog";

    public static PurchaseCodeDialog newInstance() {
        return new PurchaseCodeDialog();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        licenseManager = new LicenseManager(requireContext());
        setupBillingClient();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_purchase_code, null);

        TextView instructionsTextView = view.findViewById(R.id.tv_instructions);
        TextView codeTextView = view.findViewById(R.id.tv_purchase_code);
        Button copyButton = view.findViewById(R.id.btn_copy_code);
        Button whatsappButton = view.findViewById(R.id.btn_whatsapp);
        Button closeButton = view.findViewById(R.id.btn_close);
        Button btnGooglePlay = view.findViewById(R.id.btnGooglePlay);

        // إخفاء زر نسخ الكود كما طلبت
        copyButton.setVisibility(View.GONE);

        generatedCode = licenseManager.generateUniquePurchaseCode();
        if (generatedCode != null) {
            codeTextView.setText(generatedCode);
        } else {
            codeTextView.setText(R.string.error_generating_code);
            whatsappButton.setEnabled(false);
            btnGooglePlay.setEnabled(false);
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String email = user != null ? user.getEmail() : getString(R.string.not_specified);
        String instructions = getString(R.string.purchase_code_instructions_part1) + "\n\n" +
                getString(R.string.account_email_label, email) + "\n\n" +
                getString(R.string.purchase_instructions_title) + "\n" +
                getString(R.string.purchase_instruction_step1) + "\n" +
                getString(R.string.purchase_instruction_step2) + "\n" +
                getString(R.string.purchase_instruction_step3) + "\n" +
                getString(R.string.purchase_instruction_step4) + "\n\n" +
                getString(R.string.license_price_lifetime) + "\n" +
                getString(R.string.device_usage_limit) + "\n\n" +
                getString(R.string.note_auto_logout);

        instructionsTextView.setText(instructions);

        copyButton.setOnClickListener(v -> copyCodeToClipboard());
        whatsappButton.setOnClickListener(v -> sendToWhatsApp());

        btnGooglePlay.setOnClickListener(v -> {
            Toast.makeText(requireContext(), requireContext().getString(R.string.under_devloping), Toast.LENGTH_SHORT).show();
//            initiatePurchase();
        });

        closeButton.setOnClickListener(v -> dismiss());

        return new MaterialAlertDialogBuilder(requireContext())
                .setView(view)
                .setCancelable(true)
                .create();
    }

    private void setupBillingClient() {
        billingClient = BillingClient.newBuilder(requireContext())
                .setListener(new PurchasesUpdatedListener() {
                    @Override
                    public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                            for (Purchase purchase : purchases) {
                                handlePurchase(purchase);

                            }
                        } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
                            Toast.makeText(requireContext(), requireContext().getString(R.string.purchase_canceled), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(requireContext(), requireContext().getString(R.string.purchase_failed), Toast.LENGTH_SHORT).show();
                       Log.e(TAG, "onPurchasesUpdated: " + billingResult.getResponseCode());
                        }
                    }
                })
                .enablePendingPurchases() // Expected 1 argument but found 0
                .build();

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    queryProductDetails();
                } else {
                    Toast.makeText(requireContext(), requireContext().getString(R.string.purchase_failed) , Toast.LENGTH_SHORT).show();
                Log.e(TAG, "onBillingSetupFinished: " + billingResult.getResponseCode());
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                // يمكن محاولة إعادة الاتصال بعد فترة
            }
        });
    }

    private void queryProductDetails() {
        QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build();

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Arrays.asList(product))
                .build();

        billingClient.queryProductDetailsAsync(
                params,
                (billingResult, productDetailsList) -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && productDetailsList != null) {
                        for (ProductDetails details : productDetailsList) {
                            if (details.getProductId().equals(PRODUCT_ID)) {
                                productDetails = details;
                                break;
                            }
                        }
                    } else {
                        Toast.makeText(requireContext(), requireContext().getString(R.string.app_details_failed) , Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "queryProductDetails: " + billingResult.getResponseCode());
                    }
                }
        );
    }

    private void initiatePurchase() {
        if (productDetails == null) {
            Toast.makeText(requireContext(), "المنتج غير متاح حالياً", Toast.LENGTH_SHORT).show();
            queryProductDetails();
            return;
        }

        List<BillingFlowParams.ProductDetailsParams> productDetailsParamsList =
                Arrays.asList(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(productDetails)
                                .build()
                );

        BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
                .build();

        BillingResult billingResult = billingClient.launchBillingFlow(requireActivity(), billingFlowParams);

        if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            Toast.makeText(requireContext(), requireContext().getString(R.string.purchase_failed_start) , Toast.LENGTH_SHORT).show();
       Log.e(TAG, "initiatePurchase: " + billingResult.getResponseCode());
        }
    }

    private void handlePurchase(Purchase purchase) {
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged()) {
                AcknowledgePurchaseParams acknowledgePurchaseParams =
                        AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(purchase.getPurchaseToken())
                                .build();

                billingClient.acknowledgePurchase(acknowledgePurchaseParams, new AcknowledgePurchaseResponseListener() {
                    @Override
                    public void onAcknowledgePurchaseResponse(BillingResult billingResult) {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                            activateLicense();
                        }
                    }
                });
            } else {
                activateLicense();
            }
        }
    }

    private void activateLicense() {
        // استخدام الدالة الموجودة في LicenseManager أو البديل
        if (licenseManager != null) {
            // إذا كان لديك دالة activateLicense في LicenseManager، استخدمها
            // licenseManager.activateLicense();

            // بدلاً من ذلك، استخدم الدالة البديلة
            saveLicenseActivation();
        }

        Toast.makeText(requireContext(), requireContext().getString(R.string.license_activated) , Toast.LENGTH_LONG).show();
        dismiss();
    }

    private void saveLicenseActivation() {
        // حفظ حالة التفعيل في SharedPreferences
        android.content.SharedPreferences prefs = requireContext().getSharedPreferences("license_prefs", Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("is_licensed", true);
        editor.putLong("license_activation_time", System.currentTimeMillis());
        editor.apply();

        // إذا كان LicenseManager يدير حالة الترخيص، قم بتحديثه أيضاً
        if (licenseManager != null) {
            // يمكنك إضافة دالة setLicenseActivated إذا لزم الأمر
        }
    }

    private void copyCodeToClipboard() {
        if (generatedCode == null) return;

        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(getString(R.string.purchase_code_label), generatedCode);
        clipboard.setPrimaryClip(clip);

        Toast.makeText(getContext(), R.string.code_copied_success, Toast.LENGTH_SHORT).show();
        dismiss();
    }

    private void sendToWhatsApp() {
        if (generatedCode == null) return;

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String email = user != null ? user.getEmail() : getString(R.string.not_specified);

        String message = getString(R.string.whatsapp_greeting) + "\n\n" +
                getString(R.string.whatsapp_purchase_request) + "\n\n" +
                getString(R.string.account_email_label, email) + "\n" +
                getString(R.string.purchase_code_label_whatsapp, generatedCode) + "\n\n" +
                getString(R.string.whatsapp_payment_details_request) + "\n\n" +
                getString(R.string.whatsapp_thank_you);

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://wa.me/967734249712?text=" + Uri.encode(message)));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(getContext(), R.string.whatsapp_not_found, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (billingClient != null) {
            billingClient.endConnection();
        }
    }
}