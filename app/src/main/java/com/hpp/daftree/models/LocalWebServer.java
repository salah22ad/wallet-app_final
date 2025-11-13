package com.hpp.daftree.models;

import android.content.Context;
import android.content.res.AssetManager;

import com.hpp.daftree.MonthlySummary;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.AccountType;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.Transaction;
import com.hpp.daftree.utils.SecureAssetLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class LocalWebServer extends NanoHTTPD {

    private final Context context;
    private final DaftreeRepository repository;

    public LocalWebServer(int port, Context context) {
        super(port);
        this.context = context;
        this.repository = new DaftreeRepository((android.app.Application) context.getApplicationContext());
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();

        if (uri.equals("/styles.css")) {
            // الملف غير مشفر، يتم تحميله مباشرة
            return serveAsset("styles.css", "text/css");
        } else if (uri.startsWith("/account-details")) {
            Map<String, String> params = session.getParms();
            int accountId = Integer.parseInt(params.get("id"));
            return serveAccountDetailsPage(accountId);
        } else {
            return serveHomePage();
        }
    }

    /** ✅ الصفحة الرئيسية (index.html) بعد فك التشفير */
    private Response serveHomePage() {
        try {
            // فك التشفير مباشرة
            String template = SecureAssetLoader.loadDecryptedHtml(context, "index.html");

            if (template == null) {
                return newFixedLengthResponse("Error loading or decrypting index.html");
            }

            String accountTablesHtml = generateAccountTablesHtml();
            String monthlyTotalsHtml = generateMonthlyTotalsHtml();

            template = template.replace("%%ACCOUNT_TABLES%%", accountTablesHtml);
            template = template.replace("%%MONTHLY_TOTALS%%", monthlyTotalsHtml);

            return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", template);
        } catch (Exception e) {
            return newFixedLengthResponse("Error: " + e.getMessage());
        }
    }

    /** ✅ صفحة تفاصيل الحساب (account_details.html) بعد فك التشفير */
    private Response serveAccountDetailsPage(int accountId) {
        try {
            String template = SecureAssetLoader.loadDecryptedHtml(context, "account_details.html");
            if (template == null) {
                return newFixedLengthResponse("Error loading or decrypting account_details.html");
            }

            Account account = repository.getAccountDao().getAccountByIdBlocking(accountId);
            if (account == null)
                return newFixedLengthResponse("Error: Account not found.");

            String totalsTableHtml = generateAccountTotalsTable(accountId);
            String transactionTablesHtml = generateTransactionTables(accountId);

            template = template.replace("%%ACCOUNT_NAME%%", account.getAccountName());
            template = template.replace("%%TRANSACTION_TABLES%%", transactionTablesHtml);
            template = template.replace("%%TOTALS_TABLE%%", totalsTableHtml);

            return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", template);
        } catch (Exception e) {
            return newFixedLengthResponse("Error: " + e.getMessage());
        }
    }

    /** ⚙️ دوال مساعدة */

    private String generateAccountTablesHtml() {
        StringBuilder html = new StringBuilder();
        List<AccountType> types = repository.getAllAccountTypesBlockingReport();
        List<Currency> currencies = repository.getAllCurrenciesBlocking();

        for (AccountType type : types) {
            html.append("<h2>").append(type.name).append("</h2>");
            html.append("<table>");
            html.append("<thead><tr><th>اسم الحساب</th><th>العملة</th><th>نوع الرصيد</th><th>الرصيد</th></tr></thead>");
            html.append("<tbody>");

            boolean hasAccounts = false;
            for (Currency currency : currencies) {
                List<AccountBalanceSummary> summaries =
                        repository.getAccountBalancesBynameType(type.name, currency.id, 0L, Long.MAX_VALUE);
                for (AccountBalanceSummary summary : summaries) {
                    if (summary.balance != 0) {
                        hasAccounts = true;
                        String balanceType = summary.balance >= 0 ? "عليه" : "له";
                        String balanceClass = summary.balance >= 0 ? "debit" : "credit";
                        html.append("<tr>");
                        html.append("<td><a href='/account-details?id=").append(summary.accountId).append("'>")
                                .append(summary.accountName).append("</a></td>");
                        html.append("<td>").append(currency.name).append("</td>");
                        html.append("<td>").append(balanceType).append("</td>");
                        html.append("<td class='").append(balanceClass).append("'>")
                                .append(String.format("%,.2f", Math.abs(summary.balance))).append("</td>");
                        html.append("</tr>");
                    }
                }
            }
            if (!hasAccounts) {
                html.append("<tr><td colspan='4'>لا توجد حسابات ذات رصيد لهذا النوع.</td></tr>");
            }
            html.append("</tbody></table>");
        }
        return html.toString();
    }

    private String generateMonthlyTotalsHtml() {
        StringBuilder html = new StringBuilder();
        List<Currency> currencies = repository.getAllCurrenciesBlocking();

        for (Currency currency : currencies) {
            List<MonthlySummary> summaries = repository.getGlobalMonthlySummary(currency.id);
            if (summaries.isEmpty()) continue;
            html.append("<h3>الإجماليات بعملة: ").append(currency.name).append("</h3>");
            html.append("<table>");
            html.append("<thead><tr><th>الشهر</th><th>إجمالي الواصل (عليكم)</th><th>إجمالي الصادر (لكم)</th><th>صافي الحركة</th></tr></thead>");
            html.append("<tbody>");

            for (MonthlySummary summary : summaries) {
                double netChange = summary.totalDebit - summary.totalCredit;
                String netChangeClass = netChange >= 0 ? "debit" : "credit";
                html.append("<tr>");
                html.append("<td>").append(summary.yearMonth).append("</td>");
                html.append("<td class='credit'>").append(formatCurrency(summary.totalCredit)).append("</td>");
                html.append("<td class='debit'>").append(formatCurrency(summary.totalDebit)).append("</td>");
                html.append("<td class='").append(netChangeClass).append("'>")
                        .append(formatCurrency(Math.abs(netChange))).append("</td>");
                html.append("</tr>");
            }
            html.append("</tbody></table>");
        }
        return html.toString();
    }

    private String generateAccountTotalsTable(int accountId) {
        List<AccountTotalsByCurrency> totals = repository.getAccountTotalsByCurrency(accountId);
        if (totals.isEmpty()) return "<p>لا توجد أرصدة لهذا الحساب.</p>";

        StringBuilder html = new StringBuilder();
        html.append("<table>");
        html.append("<thead><tr><th>العملة</th><th>إجمالي عليكم</th><th>إجمالي لكم</th><th>الرصيد النهائي</th></tr></thead>");
        html.append("<tbody>");
        for (AccountTotalsByCurrency total : totals) {
            double finalBalance = total.totalDebit - total.totalCredit;
            String balanceType = finalBalance >= 0 ? "عليكم" : "لكم";
            String balanceClass = finalBalance >= 0 ? "debit" : "credit";
            html.append("<tr>");
            html.append("<td>").append(total.currency).append("</td>");
            html.append("<td class='debit'>").append(formatCurrency(total.totalDebit)).append("</td>");
            html.append("<td class='credit'>").append(formatCurrency(total.totalCredit)).append("</td>");
            html.append("<td class='").append(balanceClass).append("'>")
                    .append(formatCurrency(Math.abs(finalBalance))).append(" (").append(balanceType).append(")</td>");
            html.append("</tr>");
        }
        html.append("</tbody></table>");
        return html.toString();
    }

    private String generateTransactionTables(int accountId) {
        StringBuilder html = new StringBuilder();
        List<Currency> currencies = repository.getAllCurrenciesBlocking();
        for (Currency currency : currencies) {
            List<Transaction> transactions = repository.getTransactionsForAccountBlocking(accountId, currency.id);
            if (transactions.isEmpty()) continue;

            html.append("<h3>الحركات بعملة: ").append(currency.name).append("</h3>");
            html.append("<table>");
            html.append("<thead><tr><th>التاريخ</th><th>التفاصيل</th><th>عليكم</th><th>لكم</th><th>الرصيد</th></tr></thead>");
            html.append("<tbody>");

            double runningBalance = 0;
            for (Transaction tx : transactions) {
                runningBalance += (tx.getAmount() * tx.getType());
                String type = tx.getType() == 1 ? "debit" : "credit";
                html.append("<tr>");
                html.append("<td>").append(new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        .format(tx.getTimestamp())).append("</td>");
                html.append("<td>").append(tx.getDetails()).append("</td>");
                html.append("<td class='debit'>").append(tx.getType() == 1 ? formatCurrency(tx.getAmount()) : "0").append("</td>");
                html.append("<td class='credit'>").append(tx.getType() == -1 ? formatCurrency(tx.getAmount()) : "0").append("</td>");
                html.append("<td class='").append(type).append("'>").append(formatCurrency(runningBalance)).append("</td>");
                html.append("</tr>");
            }
            html.append("</tbody></table>");
        }
        return html.toString();
    }

    private String formatCurrency(double amount) {
        return NumberFormat.getNumberInstance(Locale.US).format(amount);
    }

    private Response serveAsset(String fileName, String mimeType) {
        try {
            return newFixedLengthResponse(Response.Status.OK, mimeType, readAssetFile(fileName));
        } catch (IOException e) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "File not found.");
        }
    }

    private String readAssetFile(String fileName) throws IOException {
        AssetManager assetManager = context.getAssets();
        try (InputStream is = assetManager.open(fileName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }
            return builder.toString();
        }
    }
}
