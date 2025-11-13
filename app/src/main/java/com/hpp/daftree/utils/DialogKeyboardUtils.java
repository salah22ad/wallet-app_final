package com.hpp.daftree.utils;

import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class DialogKeyboardUtils {

    public static void setupKeyboardHandling(Window window, View dialogView) {
        if (window == null || dialogView == null) return;

        // تمكين أسلوب إدخال المرونة للنوافذ
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        // التعامل مع IME (لوحة المفاتيح) يظهر / يخفي
        ViewCompat.setOnApplyWindowInsetsListener(dialogView, (v, insets) -> {
            int keyboardHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int systemBarsHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;

            // حساب الارتفاع الفعلي للوحة المفاتيح فقط
            int imeBottom = keyboardHeight;
            int navBarBottom = systemBarsHeight;

            // تحديث padding للديالوج بناءً على ظهور لوحة المفاتيح
            if (keyboardHeight > 0) {
                int bottomPadding = Math.max(imeBottom - navBarBottom, 0);
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottomPadding);
            } else {
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), 0);
            }

            return insets;
        });

        // طلب من ViewCompat تطبيق WindowInsets
        ViewCompat.requestApplyInsets(dialogView);
    }

    /**
     * دالة مساعدة لتعديل padding للتأكد من ظهور الديالوج بوضوح
     */
    public static void adjustDialogPadding(View dialogView) {
        if (dialogView == null) return;

        // إضافة padding إضافي للديالوج لتجنب تغطيته من قبل شريط التنقل
        int additionalPadding = (int) (24 * dialogView.getContext().getResources().getDisplayMetrics().density);
        dialogView.setPadding(
                dialogView.getPaddingLeft() + additionalPadding,
                dialogView.getPaddingTop() + additionalPadding,
                dialogView.getPaddingRight() + additionalPadding,
                dialogView.getPaddingBottom() + additionalPadding
        );
    }

    /**
     * دالة مساعدة لعرض لوحة المفاتيح
     */
    public static void showKeyboard(View view) {
        if (view == null) return;
        view.requestFocus();
        view.post(() -> {
            if (view.getWindowToken() != null) {
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) view.getContext()
                                .getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            }
        });
    }

    /**
     * دالة مساعدة لإخفاء لوحة المفاتيح
     */
    public static void hideKeyboard(View view) {
        if (view == null) return;
        view.clearFocus();
        if (view.getWindowToken() != null) {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) view.getContext()
                            .getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    /**
     * دالة مساعدة للتحقق من ظهور لوحة المفاتيح
     */
    public static boolean isKeyboardVisible(View view) {
        if (view == null || view.getWindowToken() == null) return false;

        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) view.getContext()
                        .getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            return imm.isActive(view);
        }
        return false;
    }
}