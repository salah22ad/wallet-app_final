package com.hpp.daftree.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.hpp.daftree.R;
import com.hpp.daftree.adapters.DeviceAdapter;
import com.hpp.daftree.database.DeviceInfo;
import com.hpp.daftree.utils.LicenseManager;

import java.util.ArrayList;
import java.util.List;

public class DeviceManagementDialog extends DialogFragment {

    private static final String ARG_DEVICES = "devices";
    // ⭐ تعديل: متغير جديد لتحديد سبب عرض الديالوج
    private static final String ARG_IS_LIMIT_EXCEEDED = "is_limit_exceeded";

    private List<DeviceInfo> devices;
    private boolean isLimitExceeded;
    private LicenseManager licenseManager;
    private DialogListener dialogListener;
    private Context context;

    public interface DialogListener {
        void onDeviceRemoved();

        //        void onDismissedAsFreeUser();
        void onDismissed();
    }

    // ⭐ تعديل: تحديث دالة newInstance لتقبل المتغير الجديد
    public static DeviceManagementDialog newInstance1(List<DeviceInfo> devices, LicenseManager manager, boolean isLimitExceeded) {
        DeviceManagementDialog fragment = new DeviceManagementDialog();
        Bundle args = new Bundle();
        args.putSerializable(ARG_DEVICES, new ArrayList<>(devices));
        args.putBoolean(ARG_IS_LIMIT_EXCEEDED, isLimitExceeded);
        fragment.setArguments(args);
        fragment.licenseManager = manager;
        return fragment;
    }

    public static DeviceManagementDialog newInstance(List<DeviceInfo> devices, LicenseManager manager, boolean isLimitExceeded) {
        DeviceManagementDialog fragment = new DeviceManagementDialog();
        Bundle args = new Bundle();
        args.putSerializable(ARG_DEVICES, new ArrayList<>(devices));
        args.putBoolean(ARG_IS_LIMIT_EXCEEDED, isLimitExceeded);
        fragment.setArguments(args);

        // ⭐ إصلاح: التأكد من أن manager ليس null
        if (manager != null) {
            fragment.licenseManager = manager;
        }
        return fragment;
    }

    public void setDialogListener(DialogListener listener) {
        this.dialogListener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            devices = (List<DeviceInfo>) getArguments().getSerializable(ARG_DEVICES);
            isLimitExceeded = getArguments().getBoolean(ARG_IS_LIMIT_EXCEEDED, false);
        }
        if (devices == null) devices = new ArrayList<>();

        // ⭐ إصلاح: تهيئة licenseManager إذا كان null
        if (licenseManager == null && getActivity() != null) {
            licenseManager = new LicenseManager(getActivity());
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_device_management, null);

        setupViews(view);
        context = requireContext();
        Dialog dialog = new Dialog(requireContext(), R.style.UnclosableDialogTheme) {
            @Override
            public void onBackPressed() {
                // تجاهل زر الرجوع
            }
        };

        dialog.setContentView(view);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
//        return new MaterialAlertDialogBuilder(requireContext())
//                .setView(view)
//                .setCancelable(!isLimitExceeded) // لا يمكن إلغاء الديالوج في حالة التحذير
//                .create();
    }

    private void setupViews1(View view) {
        RecyclerView recyclerView = view.findViewById(R.id.recycler_devices);
        try {
            String currentDeviceId = licenseManager.getDeviceId();
            if (currentDeviceId == null) {
                return;
            }
            DeviceAdapter adapter = new DeviceAdapter( devices, currentDeviceId, this::confirmRemoveDevice);
            recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            recyclerView.setAdapter(adapter);

            // ⭐ تعديل: تهيئة جميع عناصر الواجهة
            ImageView dialogIcon = view.findViewById(R.id.iv_dialog_icon);
            TextView titleText = view.findViewById(R.id.tv_title);
            TextView messageText = view.findViewById(R.id.tv_message);
            LinearLayout limitExceededButtons = view.findViewById(R.id.layout_limit_exceeded_buttons);
            Button continueButton = view.findViewById(R.id.btn_continue);
            continueButton.setVisibility(View.GONE);
            Button cancelButton = view.findViewById(R.id.btn_cancel);
            Button closeButton = view.findViewById(R.id.btn_close);

            // ⭐ تعديل: التحكم في الواجهة بناءً على سبب العرض
            if (isLimitExceeded) {
                // حالة تجاوز الحد (السلوك القديم)
                dialogIcon.setImageResource(R.drawable.ic_warning);
                dialogIcon.setColorFilter(ContextCompat.getColor(getContext(), R.color.accent_orange));
                titleText.setText(R.string.device_limit_exceeded_title);
                titleText.setTextColor(ContextCompat.getColor(getContext(), R.color.accent_orange));
                messageText.setText(getContext().getString(R.string.device_limit_exceeded_message));//"تم تسجيل الدخول بهذا الحساب على جهازين بالفعل. للمتابعة كمستخدم مميز، يجب إزالة أحد الأجهزة أدناه.");

                limitExceededButtons.setVisibility(View.VISIBLE);
                closeButton.setVisibility(View.GONE);
                cancelButton.setOnClickListener(v -> {
                    // licenseManager.signOutAndClearData();
                    dialogListener.onDismissed();
                    dismiss();
                });
            } else {
                // حالة الإدارة العادية
                dialogIcon.setImageResource(R.drawable.ic_smartphone);
                dialogIcon.setColorFilter(ContextCompat.getColor(getContext(), R.color.primary_blue));
                titleText.setText(R.string.manage_devices);
                titleText.setTextColor(ContextCompat.getColor(getContext(), R.color.primary_blue));
                messageText.setText(R.string.tv_device_message);

                limitExceededButtons.setVisibility(View.GONE);
                closeButton.setVisibility(View.VISIBLE);
                closeButton.setOnClickListener(v -> dismiss());
            }

        } catch (RuntimeException e) {
            Log.e("DeviceManagementDialog", e.toString());
        }
    }

    private void setupViews(View view) {
        RecyclerView recyclerView = view.findViewById(R.id.recycler_devices);
        try {
            // ⭐ إصلاح: التحقق من licenseManager و getDeviceId()
            if (licenseManager == null) {
                Log.e("DeviceManagementDialog", "licenseManager is null - initializing");
                if (getActivity() != null) {
                    licenseManager = new LicenseManager(getActivity());
                } else {
                    Log.e("DeviceManagementDialog", "Cannot initialize licenseManager - activity is null");
                    dismiss();
                    return;
                }
            }

            String currentDeviceId = licenseManager.getDeviceId();
            if (currentDeviceId == null) {
                Log.e("DeviceManagementDialog", "currentDeviceId is null");
                // إعادة المحاولة أو إغلاق الديالوج
                if (getActivity() != null) {
                    currentDeviceId = licenseManager.getDeviceId();
                }
                if (currentDeviceId == null) {
                    Toast.makeText(getContext(), "خطأ في تعريف الجهاز", Toast.LENGTH_SHORT).show();
                    dismiss();
                    return;
                }
            }

            DeviceAdapter adapter = new DeviceAdapter(devices, currentDeviceId, this::confirmRemoveDevice);
            recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            recyclerView.setAdapter(adapter);

            // ⭐ تعديل: تهيئة جميع عناصر الواجهة
            ImageView dialogIcon = view.findViewById(R.id.iv_dialog_icon);
            TextView titleText = view.findViewById(R.id.tv_title);
            TextView messageText = view.findViewById(R.id.tv_message);
            LinearLayout limitExceededButtons = view.findViewById(R.id.layout_limit_exceeded_buttons);
            Button continueButton = view.findViewById(R.id.btn_continue);
            continueButton.setVisibility(View.GONE);
            Button cancelButton = view.findViewById(R.id.btn_cancel);
            Button closeButton = view.findViewById(R.id.btn_close);

            // ⭐ تعديل: التحكم في الواجهة بناءً على سبب العرض
            if (isLimitExceeded) {
                // حالة تجاوز الحد (السلوك القديم)
                dialogIcon.setImageResource(R.drawable.ic_warning);
                dialogIcon.setColorFilter(ContextCompat.getColor(getContext(), R.color.accent_orange));
                titleText.setText(R.string.device_limit_exceeded_title);
                titleText.setTextColor(ContextCompat.getColor(getContext(), R.color.accent_orange));
                messageText.setText(getContext().getString(R.string.device_limit_exceeded_message));//"تم تسجيل الدخول بهذا الحساب على جهازين بالفعل. للمتابعة كمستخدم مميز، يجب إزالة أحد الأجهزة أدناه.");

                limitExceededButtons.setVisibility(View.VISIBLE);
                closeButton.setVisibility(View.GONE);
                cancelButton.setOnClickListener(v -> {
                    // licenseManager.signOutAndClearData();
                    dialogListener.onDismissed();
                    dismiss();
                });
            } else {
                // حالة الإدارة العادية
                dialogIcon.setImageResource(R.drawable.ic_smartphone);
                dialogIcon.setColorFilter(ContextCompat.getColor(getContext(), R.color.primary_blue));
                titleText.setText(R.string.manage_devices);
                titleText.setTextColor(ContextCompat.getColor(getContext(), R.color.primary_blue));
                messageText.setText(R.string.tv_device_message);

                limitExceededButtons.setVisibility(View.GONE);
                closeButton.setVisibility(View.VISIBLE);
                closeButton.setOnClickListener(v -> dismiss());
            }

        } catch (RuntimeException e) {
            Log.e("DeviceManagementDialog", "Error in setupViews: " + e.toString());
            // إغلاق الديالوج بأمان في حالة الخطأ
            try {
                dismiss();
            } catch (Exception ex) {
                // تجاهل أي خطأ أثناء الإغلاق
            }
        }
    }

    private void confirmRemoveDevice(DeviceInfo device) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.confirm_removal_title)
                .setMessage(getString(R.string.confirm_removal_message, device.getDisplayName(), device.getDeviceId().substring(0, 8)))
                .setPositiveButton(R.string.remove, (dialog, which) -> removeDeviceFromFirestore(device))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void removeDeviceFromFirestore(DeviceInfo device) {
        // لا يوجد تغيير هنا، منطق الحذف صحيح
        licenseManager.removeDevice(device.getDeviceId())
                .addOnSuccessListener(aVoid -> {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), R.string.device_removed_success, Toast.LENGTH_SHORT).show();
                        // تحديث الواجهة بعد الحذف
                        devices.remove(device);
                        if (dialogListener != null) {
                            dialogListener.onDeviceRemoved();
                        }
                        // إذا أصبح عدد الأجهزة أقل من الحد المسموح، أغلق الديالوج
                        if (devices.size() < LicenseManager.MAX_DEVICES) {
                            dismiss();
                        } else {
                            // إذا لم يكن كذلك، فقط حدث القائمة
                            RecyclerView recyclerView = getDialog().findViewById(R.id.recycler_devices);
                            if (recyclerView != null && recyclerView.getAdapter() != null) {
                                recyclerView.getAdapter().notifyDataSetChanged();
                            }
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), R.string.device_removal_failed, Toast.LENGTH_SHORT).show();
                    });
                });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // منع إغلاق الديالوج بالسحب
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            );

            // إضافة هذه السطور لمنع إغلاق الديالوج بالسحب
            getDialog().setCanceledOnTouchOutside(false);
            getDialog().getWindow().setDimAmount(0.0f); // إزالة التعتيم الخلفي لمنع السحب
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isLimitExceeded) {
            disableGestures();
        }
        // تعطيل الزر الخلفي
        requireDialog().setOnKeyListener((dialog, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (isLimitExceeded) {
                    if (dialogListener != null) dialogListener.onDismissed();
                    licenseManager.signOutAndClearData();
                    dismiss();
                }
                // تجاهل حدث الزر الخلفي
                return true;
            }
            return false;
        });
    }

    private void disableGestures() {
        if (getView() != null) {
            // منع جميع الإيماءات على العرض الرئيسي
            getView().setOnTouchListener((v, event) -> true);

            // منع الإيماءات على RecyclerView
            RecyclerView recyclerView = getView().findViewById(R.id.recycler_devices);
            if (recyclerView != null) {
                recyclerView.setOnTouchListener((v, event) -> true);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // منع إغلاق الديلوج بالسحب
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            );
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getDialog() != null && getDialog().isShowing()) {
            getDialog().show();
        }
    }

    private OnDismissListener onDismissListener;

    public interface OnDismissListener {
        void onDismiss();
    }

    public void setOnDismissListener(OnDismissListener listener) {
        this.onDismissListener = listener;
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (onDismissListener != null) {
            onDismissListener.onDismiss();
        }
    }
}