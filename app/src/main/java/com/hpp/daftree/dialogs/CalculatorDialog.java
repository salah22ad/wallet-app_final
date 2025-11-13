
package com.hpp.daftree.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import com.hpp.daftree.databinding.DialogCalculatorBinding;
import org.mozilla.javascript.Scriptable; // تأكد من وجود هذه المكتبة

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

import com.hpp.daftree.R;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// تعديل 1: الكلاس الآن يرث من DialogFragment
public class CalculatorDialog extends DialogFragment {

    private DialogCalculatorBinding binding;
    private OnCalculationCompleteListener listener;
    private String initialValue = "0";
    private String currentExpression = "";

    private boolean newOperation = true; // Flag to indicate if a new number should start


    public void setOnCalculationCompleteListener(OnCalculationCompleteListener listener) {
        this.listener = listener;
    }

    // واجهة للتواصل مع الـ Activity
    public interface OnCalculationCompleteListener {
        void onCalculationComplete(String result);
    }

    // تعديل 2: استخدام نمط newInstance لتمرير البيانات بشكل آمن
    public static CalculatorDialog newInstance(String initialValue) {
        CalculatorDialog dialog = new CalculatorDialog();
        Bundle args = new Bundle();
        args.putString("initialValue", initialValue);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        // تعديل 3: ربط הـ Listener مع הـ Activity المضيف
        try {
            listener = (OnCalculationCompleteListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context + " must implement OnCalculationCompleteListener");
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            initialValue = getArguments().getString("initialValue", "0");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogCalculatorBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // نقل منطق الإعداد إلى هنا
        if (!initialValue.isEmpty() && !initialValue.equals("0")) {
            currentExpression = initialValue;
//            binding.textViewDisplay.setText(initialValue);
            binding.textViewDisplay.setText(formatNumber(currentExpression));
        } else {
            binding.textViewDisplay.setText("0");
        }
        setupNumberButtons();
        setupOperatorButtons();
        setupControlButtons();
        binding.buttonOk.setOnClickListener(v -> {
            if (listener != null) {
                try {
                    String result = evaluateExpression(currentExpression);
                    listener.onCalculationComplete(result);
                } catch (Exception e) {
                    listener.onCalculationComplete(currentExpression); // Return current expression if error
                    Toast.makeText(getContext(), getContext().getString(R.string.error_calculation), Toast.LENGTH_SHORT).show();
                    e.printStackTrace(); // Log the error for debugging
                }
            }
            dismiss();
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog(); // الآن هذه الدالة ستعمل بدون مشاكل
        if (dialog != null) {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                window.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
            }
        }
    } private String formatNumber(String text) {
        if (text.isEmpty() || text.equals("Error")) return text;
        try {
            String cleanString = text.replaceAll("[,]", "");
            double parsed = Double.parseDouble(cleanString);
            NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.US);
            return numberFormat.format(parsed);
        } catch (NumberFormatException e) {
            return text; // إرجاع النص كما هو إذا كان يحتوي على عوامل حسابية
        }
    }
    private void setupNumberButtons() {
        int[] numberButtonIds = {
                R.id.button_0, R.id.button_1, R.id.button_2, R.id.button_3,
                R.id.button_4, R.id.button_5, R.id.button_6, R.id.button_7,
                R.id.button_8, R.id.button_9
        };

        for (int id : numberButtonIds) {
            binding.getRoot().findViewById(id).setOnClickListener(v -> onNumberClick(((Button) v).getText().toString()));
        }

        binding.buttonDot.setOnClickListener(v -> onDecimalClick());
    }

    private void setupOperatorButtons() {
        int[] operatorButtonIds = {
                R.id.button_plus, R.id.button_minus, R.id.button_multiply, R.id.button_divide
        };

        for (int id : operatorButtonIds) {
            binding.getRoot().findViewById(id).setOnClickListener(v -> onOperatorClick(((Button) v).getText().toString()));
        }

        binding.buttonEquals.setOnClickListener(v -> onEqualsClick());
        binding.buttonOk.setOnClickListener(v -> onOkClick());
    }
    private void onOkClick() {
        if (listener != null) {
            String resultText = binding.textViewDisplay.getText().toString();
            if (!resultText.equalsIgnoreCase("Error")) {
                listener.onCalculationComplete(resultText);
            }
        }
        dismiss();
    }
    private void setupControlButtons() {
        binding.buttonClear.setOnClickListener(v -> onClearClick());
        binding.buttonBackspace.setOnClickListener(v -> onBackspaceClick());
    }

    private void onNumberClick(String number) {
        if (newOperation) {
            currentExpression = number;
            newOperation = false;
        } else {
            // Prevent multiple leading zeros (e.g., 005)
            if (currentExpression.equals("0") && !number.equals(".")) {
                currentExpression = number;
            } else {
                currentExpression += number;
            }
        }
//        binding.textViewDisplay.setText(currentExpression);
        binding.textViewDisplay.setText(formatNumber(currentExpression));
    }

    private void onDecimalClick() {
        if (newOperation) {
            currentExpression = "0.";
            newOperation = false;
        } else if (!currentExpression.contains(".")) {
            currentExpression += ".";
        }
//        binding.textViewDisplay.setText(currentExpression);
        binding.textViewDisplay.setText(formatNumber(currentExpression));
    }

    private void onOperatorClick(String operator) {
        if (currentExpression.isEmpty()) {
            // Allow only '-' as the first character for negative numbers
            if (operator.equals("-")) {
                currentExpression = "-";
                newOperation = false;
            }
            return;
        }

        char lastChar = currentExpression.charAt(currentExpression.length() - 1);
        if (isOperator(lastChar)) {
            // Replace last operator if consecutive operators (e.g., "5++" becomes "5+")
            // Except for "5*-" or "5/-"
            if ((lastChar == '*' || lastChar == '/') && operator.equals("-")) {
                currentExpression += operator; // Allow negative number after multiply/divide
            } else {
                currentExpression = currentExpression.substring(0, currentExpression.length() - 1) + operator;
            }
        } else {
            currentExpression += operator;
        }
        newOperation = false; // Allow adding more numbers after operator
//        binding.textViewDisplay.setText(currentExpression);
        binding.textViewDisplay.setText(formatNumber(currentExpression));
    }

    private boolean isOperator(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/';
    }

    private void onEqualsClick() {
        try {
            String expression = currentExpression.replace("×", "*").replace("÷", "/").replaceAll(",", "");
            org.mozilla.javascript.Context rhino = org.mozilla.javascript.Context.enter();
            rhino.setOptimizationLevel(-1);
            Scriptable scope = rhino.initStandardObjects();
            Object result = rhino.evaluateString(scope, expression, "JavaScript", 1, null);
            currentExpression = org.mozilla.javascript.Context.toString(result);
            binding.textViewDisplay.setText(formatNumber(currentExpression));
        } catch (Exception e) {
            binding.textViewDisplay.setText("Error");
        } finally {
            org.mozilla.javascript.Context.exit();
        }
    }
    private void onClearClick() {
        currentExpression = "";
        binding.textViewDisplay.setText("0");
        newOperation = true;
    }

    private void onBackspaceClick() {
        if (!currentExpression.isEmpty()) {
            currentExpression = currentExpression.substring(0, currentExpression.length() - 1);
            if (currentExpression.isEmpty()) {
                binding.textViewDisplay.setText("0");
                newOperation = true;
            } else {
//                binding.textViewDisplay.setText(currentExpression);
                binding.textViewDisplay.setText(formatNumber(currentExpression));
            }
        }
    }

    // A more robust (though still simplified) evaluation using Shunting-yard like approach
    private String evaluateExpression(String expression) throws Exception {
        if (expression.isEmpty()) return "0";

        // Ensure expression ends with a number for evaluation
        char lastChar = expression.charAt(expression.length() - 1);
        if (isOperator(lastChar) && lastChar != '-') { // If it ends with an operator (not a negative sign)
            expression = expression.substring(0, expression.length() - 1); // Remove trailing operator
        }
        if (expression.isEmpty()) return "0"; // After removing operator, if empty, return 0

        // Use a simple regex to split numbers and operators, handling negative numbers
        Pattern pattern = Pattern.compile("(-?\\d+\\.?\\d*)|([+\\-*/])");
        Matcher matcher = pattern.matcher(expression);

        Stack<BigDecimal> numbers = new Stack<>();
        Stack<Character> operators = new Stack<>();

        while (matcher.find()) {
            String token = matcher.group();
            if (token.matches("-?\\d+\\.?\\d*")) { // It's a number
                numbers.push(new BigDecimal(token));
            } else { // It's an operator
                char op = token.charAt(0);
                while (!operators.isEmpty() && hasPrecedence(operators.peek(), op)) {
                    performOperation(numbers, operators.pop());
                }
                operators.push(op);
            }
        }

        while (!operators.isEmpty()) {
            performOperation(numbers, operators.pop());
        }

        if (numbers.isEmpty()) {
            throw new IllegalArgumentException("Invalid expression");
        }
        return numbers.pop().stripTrailingZeros().toPlainString();
    }

    private boolean hasPrecedence(char op1, char op2) {
        if (op2 == '(' || op2 == ')') return false;
        return (op1 == '*' || op1 == '/') && (op2 == '+' || op2 == '-');
    }

    private void performOperation(Stack<BigDecimal> numbers, char operator) {
        if (numbers.size() < 2 && operator != '-') { // For unary minus, numbers.size() could be 1
            throw new IllegalArgumentException("Invalid expression: not enough operands for " + operator);
        }

        BigDecimal b = numbers.pop();
        BigDecimal a = numbers.pop();

        switch (operator) {
            case '+':
                numbers.push(a.add(b));
                break;
            case '-':
                numbers.push(a.subtract(b));
                break;
            case '*':
                numbers.push(a.multiply(b));
                break;
            case '/':
                if (b.compareTo(BigDecimal.ZERO) == 0) {
                    throw new ArithmeticException("Division by zero");
                }
                numbers.push(a.divide(b, 10, RoundingMode.HALF_UP)); // 10 decimal places for division
                break;
            default:
                throw new IllegalArgumentException("Unknown operator: " + operator);
        }
    }

    public interface OnCalculationCompleteListener1 {
        void onCalculationComplete(String result);
    }
}
