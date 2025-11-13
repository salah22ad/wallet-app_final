package com.hpp.daftree.models;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.view.Gravity;
import androidx.core.content.ContextCompat;
import com.google.android.material.snackbar.Snackbar;
import com.hpp.daftree.R;

public class SnackbarHelper {

    public enum SnackbarType {
        ERROR, INFO, SUCCESS,WARNING
    }

    // دالة أساسية لعرض Snackbar
    public static void showSnackbar(View view, String message, SnackbarType type) {
        showSnackbar(view, message, type, Snackbar.LENGTH_LONG, null, null);
    }

    // دالة مع مدة مخصصة
    public static void showSnackbar(View view, String message, SnackbarType type, int duration) {
        showSnackbar(view, message, type, duration, null, null);
    }

    // دالة مع زر إجراء
    public static void showSnackbar(View view, String message, SnackbarType type, String actionText, View.OnClickListener actionListener) {
        showSnackbar(view, message, type, Snackbar.LENGTH_LONG, actionText, actionListener);
    }

    // الدالة الرئيسية التي تحتوي على كل التخصيصات
    public static void showSnackbar(View view, String message, SnackbarType type, int duration,
                                    String actionText, View.OnClickListener actionListener) {
        // إنشاء الـ Snackbar الأساسي
        Snackbar snackbar = Snackbar.make(view, message, duration);

        // الحصول على الـ View الخاص بالـ Snackbar
        View snackbarView = snackbar.getView();

        // تحديد الألوان والخصائص حسب النوع
        int backgroundColor;
        int textColor;
        int actionColor;
        int height;

        switch (type) {
            case WARNING:
                backgroundColor = ContextCompat.getColor(view.getContext(), R.color.snackbar_error_background);
                textColor = ContextCompat.getColor(view.getContext(), R.color.snackbar_error_text);
                actionColor = ContextCompat.getColor(view.getContext(), R.color.snackbar_error_action);
                height = view.getResources().getDimensionPixelSize(R.dimen.snackbar_error_height);
                break;
            case ERROR:
                backgroundColor = ContextCompat.getColor(view.getContext(), R.color.snackbar_error_background);
                textColor = ContextCompat.getColor(view.getContext(), R.color.snackbar_error_text);
                actionColor = ContextCompat.getColor(view.getContext(), R.color.snackbar_error_action);
                height = view.getResources().getDimensionPixelSize(R.dimen.snackbar_error_height);
                break;
            case INFO:
                backgroundColor = ContextCompat.getColor(view.getContext(), R.color.snackbar_info_background);
                textColor = ContextCompat.getColor(view.getContext(), R.color.snackbar_info_text);
                actionColor = ContextCompat.getColor(view.getContext(), R.color.snackbar_info_action);
                height = view.getResources().getDimensionPixelSize(R.dimen.snackbar_info_height);
                break;
            case SUCCESS:
                backgroundColor = ContextCompat.getColor(view.getContext(), R.color.snackbar_success_background);
                textColor = ContextCompat.getColor(view.getContext(), R.color.snackbar_success_text);
                actionColor = ContextCompat.getColor(view.getContext(), R.color.snackbar_success_action);
                height = view.getResources().getDimensionPixelSize(R.dimen.snackbar_success_height);
                break;
            default:
                backgroundColor = ContextCompat.getColor(view.getContext(), R.color.snackbar_default_background);
                textColor = ContextCompat.getColor(view.getContext(), R.color.snackbar_default_text);
                actionColor = ContextCompat.getColor(view.getContext(), R.color.snackbar_default_action);
                height = view.getResources().getDimensionPixelSize(R.dimen.snackbar_default_height);
        }

        // تطبيق التخصيصات
        snackbarView.setBackgroundColor(backgroundColor);

        // تعديل الارتفاع
        ViewGroup.LayoutParams params = snackbarView.getLayoutParams();
        params.height = height;
        snackbarView.setLayoutParams(params);

        // تخصيص النص الرئيسي
        TextView textView = snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);
        textView.setTextColor(textColor);
        textView.setTextSize(16);
        textView.setMaxLines(3); // السماح بثلاث أسطر كحد أقصى
        textView.setGravity(Gravity.CENTER);
        // إضافة زر إجراء إذا كان موجوداً
        if (actionText != null && actionListener != null) {
            snackbar.setAction(actionText, actionListener);
            snackbar.setActionTextColor(actionColor);

            // تخصيص نص زر الإجراء
            TextView actionTextView = snackbarView.findViewById(com.google.android.material.R.id.snackbar_action);
            actionTextView.setTextSize(14);
            actionTextView.setAllCaps(false); // إلغاء الأحرف الكبيرة
        }

        // ✅ جعل الـ Snackbar في منتصف الشاشة
        ViewGroup.LayoutParams layoutParams = snackbarView.getLayoutParams();
        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) layoutParams;

            if (marginParams instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams frameParams = (FrameLayout.LayoutParams) marginParams;
                frameParams.gravity = Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL;
                snackbarView.setLayoutParams(frameParams);
            }
        }

        // عرض الـ Snackbar
        snackbar.show();
    }
}
