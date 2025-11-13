package com.hpp.daftree.models;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * كلاس احترافي لتنسيق المبالغ المالية بطريقة موحدة في كل أجزاء التطبيق.
 *
 * الوظائف:
 *  - تنسيق المبلغ أثناء الكتابة في EditText.
 *  - إزالة الفواصل وتحويل النص إلى double للحفظ.
 *  - تنسيق المبلغ للعرض في TextView أو RecyclerView.
 *
 * اللغة المعتمدة: الإنجليزية (Locale.US)
 */
public class FormatingAmount {

    private static final Locale LOCALE = Locale.US;
    private static final DecimalFormat decimalFormat;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(LOCALE);
        symbols.setGroupingSeparator(',');
        symbols.setDecimalSeparator('.');
        decimalFormat = new DecimalFormat("#,##0.##", symbols);
        decimalFormat.setGroupingUsed(true);
    }

    /**
     * يطبق تنسيق المبالغ أثناء الكتابة في EditText.
     */
    public static void applyTo(EditText editText) {
        editText.addTextChangedListener(new TextWatcher() {
            private boolean editing = false;
            private String current = "";

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (editing) return;
                editing = true;

                try {
                    String input = s.toString();

                    if (input.isEmpty()) {
                        current = "";
                        editing = false;
                        return;
                    }

                    // إزالة الفواصل السابقة
                    String cleanInput = input.replaceAll(",", "");

                    // السماح بفاصلة عشرية واحدة فقط
                    int dotCount = cleanInput.length() - cleanInput.replace(".", "").length();
                    if (dotCount > 1) {
                        editText.setText(current);
                        editText.setSelection(current.length());
                        editing = false;
                        return;
                    }

                    // حالة المستخدم كتب فقط نقطة
                    if (cleanInput.equals(".")) {
                        editText.setText("");
                        editing = false;
                        return;
                    }
                    // تقليص الكسور إلى رقمين فقط بعد الفاصلة
                    if (cleanInput.contains(".")) {
                        int index = cleanInput.indexOf(".");
                        String beforeDot = cleanInput.substring(0, index);
                        String afterDot = cleanInput.substring(index + 1);
                        if (afterDot.length() > 2) {
                            afterDot = afterDot.substring(0, 2);
                        }
                        cleanInput = beforeDot + "." + afterDot;
                    }

                    String formatted;

                    if (cleanInput.endsWith(".")) {
                        // المستخدم كتب فاصلة عشرية ولم يُدخل بعدها رقم
                        double parsed = Double.parseDouble(cleanInput.replace(".", ""));
                        formatted = decimalFormat.format(parsed) + ".";
                    } else if (cleanInput.contains(".")) {
                        String[] parts = cleanInput.split("\\.");
                        String intPart = parts[0];
                        String decPart = parts.length > 1 ? parts[1] : "";

                        if (decPart.length() > 2)
                            decPart = decPart.substring(0, 2);

                        String formattedInt = decimalFormat.format(Double.parseDouble(intPart.isEmpty() ? "0" : intPart));
                        formatted = formattedInt + (decPart.isEmpty() ? "" : "." + decPart);
                    } else {
                        double parsed = Double.parseDouble(cleanInput);
                        formatted = decimalFormat.format(parsed);
                    }

                    current = formatted;
                    editText.setText(formatted);
                    editText.setSelection(formatted.length());
                } catch (Exception ignored) {}

                editing = false;
            }
        });
    }
    /**
     * ربط EditText لتطبيق تنسيق الأرقام أثناء الكتابة
     * @param editText الحقل المطلوب تطبيق التنسيق عليه
     */
    public static void applyTo2(final EditText editText) {
        editText.addTextChangedListener(new TextWatcher() {
            private boolean editing;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (editing) return;
                editing = true;

                try {
                    String input = s.toString();

                    // إزالة الفواصل القديمة
                    String cleanString = input.replace(",", "").trim();

                    // لا نسمح بأكثر من فاصلة عشرية واحدة
                    if (cleanString.chars().filter(ch -> ch == '.').count() > 1) {
                        int lastDot = cleanString.lastIndexOf('.');
                        cleanString = cleanString.substring(0, lastDot);
                    }

                    // تقليص الكسور إلى رقمين فقط بعد الفاصلة
                    if (cleanString.contains(".")) {
                        int index = cleanString.indexOf(".");
                        String beforeDot = cleanString.substring(0, index);
                        String afterDot = cleanString.substring(index + 1);
                        if (afterDot.length() > 2) {
                            afterDot = afterDot.substring(0, 2);
                        }
                        cleanString = beforeDot + "." + afterDot;
                    }

                    // تحويل النص إلى رقم
                    double parsed = 0;
                    if (!cleanString.isEmpty() && !cleanString.equals(".")) {
                        parsed = Double.parseDouble(cleanString);
                    }

                    // تنسيق الرقم
                    DecimalFormat formatter = new DecimalFormat("#,###.##", new DecimalFormatSymbols(Locale.US));
                    String formatted = formatter.format(parsed);

                    // حفظ موضع المؤشر الآمن
                    int cursorPos = formatted.length();
                    editText.removeTextChangedListener(this);
                    editText.setText(formatted);

                    // التأكد أن المؤشر لا يتجاوز الطول الجديد
                    if (cursorPos > formatted.length()) cursorPos = formatted.length();
                    editText.setSelection(cursorPos);
                    editText.addTextChangedListener(this);

                } catch (Exception e) {
                    e.printStackTrace();
                }

                editing = false;
            }
        });
    }
    /**
     * يحوّل النص من EditText إلى double (بدون فواصل).
     */
    public static double getNumericValue(EditText editText) {
        try {
            String clean = editText.getText().toString().replaceAll(",", "");
            if (clean.isEmpty()) return 0.0;
            return Double.parseDouble(clean);
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * تنسيق رقم double ليُعرض في TextView أو RecyclerView.
     * مثال: 2500.0 → "2,500" و 2500.5 → "2,500.5"
     */
    public static String formatForDisplay1(double value) {
        try {
            return decimalFormat.format(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    /**
     * ينسق المبلغ ويعرضه مباشرة داخل TextView.
     */
    public static void setFormattedText(TextView textView, double amount) {
        textView.setText(formatForDisplay(amount));
    }
    public static String formatForDisplay2(double amount) {
        // إذا كانت القيمة عدد صحيح (مثل 2500.0) نعرضها بدون كسور
        if (amount == Math.floor(amount)) {
            // تنسيق بدون كسور عشرية
            DecimalFormat noDecimalFormat = new DecimalFormat("#,###");
            return noDecimalFormat.format(amount);
        } else {
            // نستخدم DecimalFormat لإظهار رقمين بعد الفاصلة دائمًا
            DecimalFormat twoDecimalFormat = new DecimalFormat("#,###.00");
            return twoDecimalFormat.format(amount);
        }
    }
    /**
     * تنسيق المبلغ للعرض في TextView أو RecyclerView عند تمرير القيمة كـ String
     * @param amountStr القيمة كـ String (قد تحتوي على فواصل أو لا)
     * @return المبلغ منسق بنفس القواعد السابقة
     */
    public static String formatForDisplay1(String amountStr) {
        if (amountStr == null || amountStr.trim().isEmpty()) {
            return "";
        }

        try {
            // إزالة أي فواصل ألف موجودة في النص
            String clean = amountStr.replace(",", "").trim();

            double value = Double.parseDouble(clean);
            return formatForDisplay(value);

        } catch (NumberFormatException e) {
            e.printStackTrace();
            return amountStr; // في حال فشل التحويل نعيد النص الأصلي بدون كسر التطبيق
        }
    }
    /**
     * تنسيق المبلغ للعرض (في TextView أو RecyclerView)
     * @param amount قيمة المبلغ كـ double
     * @return تنسيق احترافي حسب الشروط
     */
    public static String formatForDisplay(double amount) {
        // تقليص الكسور إلى رقمين كحد أقصى (دون تقريب)
        amount = Math.floor(amount * 100) / 100.0;

        if (amount == Math.floor(amount)) {
            // بدون كسور عشرية
            DecimalFormat noDecimalFormat = (DecimalFormat) NumberFormat.getNumberInstance(Locale.US);
            noDecimalFormat.applyPattern("#,###");
            return noDecimalFormat.format(amount);
        } else {
            // عرض رقمين بعد الفاصلة
            DecimalFormat twoDecimalFormat = (DecimalFormat) NumberFormat.getNumberInstance(Locale.US);
            twoDecimalFormat.applyPattern("#,###.00");
            return twoDecimalFormat.format(amount);
        }
    }

    /**
     * تنسيق المبلغ للعرض عند تمرير القيمة كنص
     * @param amountStr القيمة كنص
     */
    public static String formatForDisplay(String amountStr) {
        if (amountStr == null || amountStr.trim().isEmpty()) return "";
        try {
            String clean = amountStr.replace(",", "").trim();
            double value = Double.parseDouble(clean);
            value = Math.floor(value * 100) / 100.0; // قص الزائد بعد الفاصلة
            return formatForDisplay(value);
        } catch (NumberFormatException e) {
            return amountStr;
        }
    }

    /**
     * تحويل النص المنسق إلى double آمن (للاستخدام عند الحفظ في قاعدة البيانات)
     * @param formattedAmount النص المنسق (مثلاً "2,500.50")
     * @return القيمة الرقمية 2500.5
     */
    public static double parseAmount(String formattedAmount) {
        if (formattedAmount == null || formattedAmount.trim().isEmpty()) return 0.0;
        try {
            String clean = formattedAmount.replace(",", "").trim();
            if (clean.endsWith(".")) clean = clean.substring(0, clean.length() - 1);
            double value = Double.parseDouble(clean);
            // قص الزائد بعد الفاصلة
            value = Math.floor(value * 100) / 100.0;
            return value;
        } catch (Exception e) {
            return 0.0;
        }
    }
    /**
     * تنسيق المبلغ مع رمز العملة
     * @param amount المبلغ كـ double
     * @param currencySymbol رمز العملة (مثل "$" أو "﷼")
     * @param symbolBeforeNumber true لو تريد وضع الرمز قبل الرقم، false لو بعد الرقم
     * @return نص منسق مع العملة
     */
    public static String formatWithCurrency(double amount, String currencySymbol, boolean symbolBeforeNumber) {
        String formattedAmount = formatForDisplay(amount);
        if (symbolBeforeNumber) {
            return currencySymbol + formattedAmount;
        } else {
            return formattedAmount + " " + currencySymbol;
        }
    }

    /**
     * نسخة تقبل نص كنصوص مدخلة مسبقًا
     */
    public static String formatWithCurrency(String amountStr, String currencySymbol, boolean symbolBeforeNumber) {
        String formattedAmount = formatForDisplay(amountStr);
        if (symbolBeforeNumber) {
            return currencySymbol + formattedAmount;
        } else {
            return formattedAmount + " " + currencySymbol;
        }
    }

}

