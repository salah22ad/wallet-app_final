package com.hpp.daftree.models;

import static com.hpp.daftree.helpers.LanguageHelper.isRTL;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.Log;

import com.hpp.daftree.MonthlySummary;
import com.hpp.daftree.R;
import com.hpp.daftree.TransactionWithAccount;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.AccountType;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.Transaction;
import com.hpp.daftree.database.User;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import java.io.File;
import java.io.FileOutputStream;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReportGenerator {
    private final Context context;
    private final DaftreeRepository repository;
    private final User userProfile;
    private final boolean isRTL;
    private final Locale currentLocale;
    private final Context localizedContext;

    private Font titleFont,titleFont2, blackFont, totalsFont, subTitleFont, tableHeaderFont, normalFont, boldFont, footerFont, redFont, greenFont;
    private final BaseColor headerBgColor = new BaseColor(230, 232, 235);
    private final BaseColor primaryColor = new BaseColor(0, 77, 153);
    private final BaseColor lightCyan = new BaseColor(224, 247, 250);
    private final BaseColor evenRowColor = new BaseColor(248, 249, 250);

    public ReportGenerator(Context context, User userProfile, String language) {
        this.context = context;
        this.repository = new DaftreeRepository((Application) context.getApplicationContext());
        this.userProfile = userProfile;

        this.currentLocale = new Locale(language);
        this.isRTL = isRTL(context);
        this.localizedContext = createLocalizedContext(context, currentLocale);

        initializeFonts();
    }

    private boolean isRTLanguage(String language) {
        return "ar".equals(language) || "fa".equals(language) || "he".equals(language);
    }

    private Context createLocalizedContext(Context context, Locale locale) {
        Resources resources = context.getResources();
        Configuration configuration = new Configuration(resources.getConfiguration());
        configuration.setLocale(locale);
        return context.createConfigurationContext(configuration);
    }

    private void initializeFonts() {
        try {
            BaseFont baseFont;
            if (isRTL) {
                baseFont = BaseFont.createFont("assets/Cairo.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } else {
//                baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.EMBEDDED);
                baseFont = BaseFont.createFont("assets/Cairo.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            }

            this.titleFont = new Font(baseFont, 16, Font.BOLD, primaryColor);
            this.titleFont2 = new Font(baseFont, 12, Font.BOLD, BaseColor.RED);
            this.subTitleFont = new Font(baseFont, 10, Font.NORMAL, BaseColor.DARK_GRAY);
            this.tableHeaderFont = new Font(baseFont, 10, Font.BOLD, BaseColor.BLUE);
            this.normalFont = new Font(baseFont, 9, Font.NORMAL, BaseColor.BLACK);
            this.totalsFont = new Font(baseFont, 10, Font.BOLD, BaseColor.BLACK);
            this.blackFont = new Font(baseFont, 9, Font.NORMAL, BaseColor.BLACK);
            this.footerFont = new Font(baseFont, 8, Font.ITALIC, BaseColor.GRAY);
            this.redFont = new Font(baseFont, 9, Font.NORMAL, BaseColor.RED);
            this.greenFont = new Font(baseFont, 9, Font.NORMAL, new BaseColor(0, 128, 0));
            this.boldFont = new Font(baseFont, 10, Font.BOLD, BaseColor.BLACK);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize fonts", e);
        }
    }

    private String getString(int resId) {
        try {
            return localizedContext.getString(resId);
        } catch (Exception e) {
            return context.getString(resId);
        }
    }

    private Document createDocument(File file) throws Exception {
        Document document = new Document(PageSize.A4, 36, 36, 120, 40);
        PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(file));

        if (userProfile != null) {
            writer.setPageEvent(new ReportHeaderFooter(localizedContext, userProfile, currentLocale, isRTL));
        }

        writer.setRunDirection(isRTL ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);
        return document;
    }

    // ===== الدوال الرئيسية لتوليد التقارير =====

    // 1. كشف حساب تفصيلي لحساب معين
    public File generateDetailedAccountStatement(Account account, int currency, long startDate, long endDate, boolean useDateRange) throws Exception {
        File file = new File(context.getCacheDir(), "detailed_statement_" + System.currentTimeMillis() + ".pdf");
        Document document = createDocument(file);
        document.open();

        String currencyName = repository.getCurrencyNameById(currency);
        addTitleAndPeriod(document,
                getString(R.string.report_title_detailed_statement),
                getString(R.string.report_label_account) + ": " + account.getAccountName(),
                currencyName, startDate, endDate, useDateRange);

        List<AccountBalanceSummary> summaries = repository.getAccountBalances(account.getId(), currency, startDate, endDate);
        document.add(createBalancesTable(summaries));
        document.close();
        return file;
    }

    //  2. كشف حركة تفصيلي لحساب معين عملة معينة
    public File generateDetailedMovementAccountStatement(Account account, int currency, long startDate, long endDate, boolean useDateRange) throws Exception {
        File file = new File(context.getCacheDir(), "detailed_movement_" + System.currentTimeMillis() + ".pdf");
        Document document = createDocument(file);
        document.open();

        String currencyName = repository.getCurrencyNameById(currency);
        addTitleAndPeriod(document,
                getString(R.string.report_title_detailed_statement),
                getString(R.string.report_label_account) + ": " + account.getAccountName(),
                currencyName, startDate, endDate, useDateRange);

        List<Transaction> transactions = repository.getTransactionsForAccountBlocking(account.getId(), currency);
        document.add(createDetailedMovementTable(transactions, account.getId(), currency, startDate, useDateRange));
        document.close();
        return file;
    }
    //  2.2 كشف حركة تفصيلي لحساب معين متعدد العملات
    public File generateDetailedMovementAccountStatementAllCurrencies(Account account, int currency, long startDate, long endDate, boolean useDateRange) throws Exception {
        File file = new File(context.getCacheDir(), "detailed_movement_" + System.currentTimeMillis() + ".pdf");
        Document document = createDocument(file);
        document.open();


        addTitleAndPeriod(document,
                getString(R.string.report_title_detailed_statement),
                getString(R.string.report_label_account) + ": " + account.getAccountName(),
                getString(R.string.report_label_all_currencies), startDate, endDate, useDateRange);
        List<Currency> allCurrencies = repository.getAllCurrenciesBlocking();
        boolean hasAnyData = false;

        for (Currency curr : allCurrencies) {
              List<Transaction> transactions = repository.getTransactionsForAccountBlocking(account.getId(), curr.id);
            if (transactions.isEmpty()) continue;
            hasAnyData = true;
            addSectionTitle(document, getString(R.string.report_label_currency) + ": " + curr.name);
            document.add(createDetailedMovementTable(transactions, account.getId(), currency, startDate, useDateRange));
            document.add(Chunk.NEWLINE);
        }

        if (!hasAnyData) {
//            document.add(new Paragraph(getString(R.string.report_no_data), normalFont));
//            addSectionTitle(document, getString(R.string.report_no_data));

        }
        document.close();
        return file;
    }

    // 3. كشف حساب متعدد العملات
    public File generateDetailedAccountStatementAllCurrencies(Account account, int currency, long startDate, long endDate, boolean useDateRange) throws Exception {
        File file = new File(context.getCacheDir(), "multi_currency_statement_" + System.currentTimeMillis() + ".pdf");
        Document document = createDocument(file);
        document.open();

        addTitleAndPeriod(document,
                getString(R.string.report_title_monthly_summary),
                getString(R.string.report_label_account) + ": " + account.getAccountName(),
                getString(R.string.report_label_all_currencies), startDate, endDate, useDateRange);

        List<Currency> allCurrencies = repository.getAllCurrenciesBlocking();
        boolean hasAnyData = false;

        for (Currency curr : allCurrencies) {
            List<AccountBalanceSummary> summaries = repository.getAccountBalances(account.getId(), curr.getId(), startDate, endDate);
            if (summaries.isEmpty()) continue;

            hasAnyData = true;
            addSectionTitle(document, getString(R.string.report_label_currency) + ": " + curr.name);
            document.add(createBalancesTable(summaries));
            document.add(Chunk.NEWLINE);
        }

        if (!hasAnyData) {
//            document.add(new Paragraph(getString(R.string.report_no_data), normalFont));
//            addSectionTitle(document, getString(R.string.report_no_data));
        }

        document.close();
        return file;
    }

    // 4. كشف حساب شهري لحساب معين
    public File generateMonthlyAccountSummary(Account account, int currency) throws Exception {
        File file = new File(context.getCacheDir(), "monthly_summary_" + System.currentTimeMillis() + ".pdf");
        Document document = createDocument(file);
        document.open();

        String currencyName = repository.getCurrencyNameById(currency);
        addTitleAndPeriod(document,
                getString(R.string.report_title_monthly_summary),
                getString(R.string.report_label_account) + ": " + account.getAccountName(),
                currencyName, 0, 0, false);

        List<MonthlySummary> summaries = repository.getMonthlySummaryForAccountAllCurrencies(account.getId(), currency);
        document.add(createMonthlySummaryTable(summaries, true, currencyName));
        document.close();
        return file;
    }

    // 5. كشف حساب شهري متعدد العملات
    public File generateMonthlyAccountSummaryAllCurrency(Account account, int currency) throws Exception {
        File file = new File(context.getCacheDir(), "monthly_summary_all_currency_" + System.currentTimeMillis() + ".pdf");
        Document document = createDocument(file);
        document.open();

        addTitleAndPeriod(document,
                getString(R.string.report_title_monthly_summary),
                getString(R.string.report_label_account) + ": " + account.getAccountName(),
                getString(R.string.report_label_all_currencies), 0, 0, false);

        List<Currency> allCurrencies = repository.getAllCurrenciesBlocking();
        boolean hasAnyData = false;

        for (Currency curr : allCurrencies) {
            List<MonthlySummary> summaries = repository.getMonthlySummaryForAccountAllCurrencies(account.getId(), curr.id);
            if (summaries.isEmpty()) continue;

            hasAnyData = true;
            addSectionTitle(document, getString(R.string.report_label_currency) + ": " + curr.name);
            document.add(createMonthlySummaryTable(summaries, true, curr.name));
            document.add(Chunk.NEWLINE);
        }

        if (!hasAnyData) {
//            document.add(new Paragraph(getString(R.string.report_no_data), normalFont));
//            addSectionTitle(document, getString(R.string.report_no_data));
        }

        document.close();
        return file;
    }

    // 6. الحركة الشهرية الإجمالية لنوع الحساب
    public File generateMonthlySummaryByAccountType(String acTypeFirestoreId, int currency) throws Exception {
        File file = new File(context.getCacheDir(), "monthly_type_summary_" + System.currentTimeMillis() + ".pdf");
        Document document = createDocument(file);
        document.open();

        String currencyName = repository.getCurrencyNameById(currency);
        String accountTypeName = repository.getAccountTypeNameByFirestoreId(acTypeFirestoreId);
        addTitleAndPeriod(document,
                getString(R.string.report_title_monthly_summary),
                getString(R.string.report_label_account_type) + ": " + accountTypeName,
                currencyName, 0, 0, false);

        List<MonthlySummary> summaries = repository.getMonthlySummaryByAccountTypeByCurrency(acTypeFirestoreId, currency);
        document.add(createMonthlySummaryTable(summaries, false, currencyName));
        document.close();
        return file;
    }
    // 6.6 الحركة الشهرية الإجمالية لنوع الحساب
    public File generateMonthlySummaryByAccountTypeAllCurrency(String acTypeFirestoreId) throws Exception {
        File file = new File(context.getCacheDir(), "monthly_type_summary_" + System.currentTimeMillis() + ".pdf");
        Document document = createDocument(file);
        document.open();
        String accountTypeName = repository.getAccountTypeNameByFirestoreId(acTypeFirestoreId);
        addTitleAndPeriod(document,
                getString(R.string.report_title_monthly_summary),
                getString(R.string.report_label_account_type) + ": " + accountTypeName,
                getString(R.string.report_label_all_currencies), 0, 0, false);

        List<Currency> allCurrencies = repository.getAllCurrenciesBlocking();
            boolean hasDataForType = false;

            for (Currency curr : allCurrencies) {
                List<MonthlySummary> summaries = repository.getMonthlySummaryByAccountTypeByCurrency(acTypeFirestoreId, curr.getId());
                if (summaries.isEmpty()) continue;

                hasDataForType = true;
                addSectionTitle(document, getString(R.string.report_label_account_type) + ": " + accountTypeName + " - " + getString(R.string.report_label_currency) + ": " + curr.name);
                document.add(createMonthlySummaryTable(summaries, false, curr.name));
                document.add(Chunk.NEWLINE);
            }

            if (!hasDataForType) {
//                addSectionTitle(document, getString(R.string.report_label_account_type) + ": " + accountTypeName + " - " + getString(R.string.report_no_data));
//                document.add(Chunk.NEWLINE);
            }
        document.close();
        return file;
    }

    // 7. تقرير الأرصدة المجمع عمله معينة
    public File generateConsolidatedBalancesReport(String acTypeFirestoreId, int currency, long startDate, long endDate, boolean useDateRange) throws Exception {
        File file = new File(context.getCacheDir(), "consolidated_balances_" + System.currentTimeMillis() + ".pdf");
        Document document = createDocument(file);
        document.open();
        String accountTypeName = repository.getAccountTypeNameByFirestoreId(acTypeFirestoreId);

        String currencyName = repository.getCurrencyNameById(currency);
        addTitleAndPeriod(document,
                getString(R.string.report_title_consolidated_balances),
                getString(R.string.report_label_account_type) + ": " + accountTypeName,
                currencyName, startDate, endDate, useDateRange);

        List<AccountBalanceSummary> summaries = repository.getAccountBalancesByType(acTypeFirestoreId, currency, startDate, endDate);
        document.add(createBalancesByAccountTypeTable(summaries, currencyName));
        document.close();
        return file;
    }

    // 7.7 تقرير الأرصدة المجمع متعدد العملات
    public File generateConsolidatedBalancesReportAllCurrency(String acTypeFirestoreId, long startDate, long endDate) throws Exception {
        File file = new File(context.getCacheDir(), "consolidated_balances_" + System.currentTimeMillis() + ".pdf");
        Document document = createDocument(file);
        document.open();


        String accountTypeName = repository.getAccountTypeNameByFirestoreId(acTypeFirestoreId);
        addTitleAndPeriod(document,
                getString(R.string.report_title_consolidated_balances),
                getString(R.string.report_label_account_type) + ": " + accountTypeName,
                getString(R.string.report_label_all_currencies), startDate, endDate, true);

        List<Currency> allCurrencies = repository.getAllCurrenciesBlocking();
        boolean hasDataForType = false;

        for (Currency curr : allCurrencies) {
            List<AccountBalanceSummary> summaries = repository.getAccountBalancesByType(acTypeFirestoreId, curr.id, startDate, endDate);

            if (summaries.isEmpty()) continue;

            hasDataForType = true;
            addSectionTitle(document, getString(R.string.report_label_account_type) + ": " + accountTypeName + " - " + getString(R.string.report_label_currency) + ": " + curr.name);
            document.add(createBalancesByAccountTypeTable(summaries, curr.name));
            document.add(Chunk.NEWLINE);
        }

        if (!hasDataForType) {
            addSectionTitle(document, getString(R.string.report_label_account_type) + ": " + accountTypeName + " - " + getString(R.string.report_no_data));
            document.add(Chunk.NEWLINE);
        }
        document.close();
        return file;
    }



    // 8. كشف حركة مجمع تفصيلي
    public File generateConsolidatedMovementReport(String acTypeFirestoreId, int currency, long startDate, long endDate, boolean useDateRange) throws Exception {
        File file = new File(context.getCacheDir(), "consolidated_movement_" + System.currentTimeMillis() + ".pdf");
        Document document = createDocument(file);
        document.open();
        String currencyName = repository.getCurrencyNameById(currency);
        String accountTypeName = repository.getAccountTypeNameByFirestoreId(acTypeFirestoreId);
        addTitleAndPeriod(document,
                getString(R.string.report_title_consolidated_movement),
                getString(R.string.report_label_account_type) + ": " + accountTypeName,
                currencyName, startDate, endDate, useDateRange);
        List<TransactionWithAccount> transactions = repository.getConsolidatedMovementAllCurrencies(acTypeFirestoreId,currency , startDate, endDate);
        document.add(createConsolidatedMovementTable(transactions, acTypeFirestoreId, startDate, useDateRange));
        document.close();
        return file;
    }

    // 8.8 كشف حركة مجمع تفصيلي متعدد العملات
    public File generateConsolidatedMovementReportAllCurrencies(String acTypeFirestoreId, long startDate, long endDate, boolean useDateRange) throws Exception {
        File file = new File(context.getCacheDir(), "consolidated_movement_" + System.currentTimeMillis() + ".pdf");
        Document document = createDocument(file);
        document.open();

        String accountTypeName = repository.getAccountTypeNameByFirestoreId(acTypeFirestoreId);
        addTitleAndPeriod(document,
                getString(R.string.report_title_consolidated_movement),
                getString(R.string.report_label_account_type) + ": " + accountTypeName,
                getString(R.string.report_label_all_currencies), startDate, endDate, useDateRange);

        List<Currency> allCurrencies = repository.getAllCurrenciesBlocking();
        boolean hasDataForType = false;

        for (Currency curr : allCurrencies) {
            List<TransactionWithAccount> transactions = repository.getConsolidatedMovementAllCurrencies(acTypeFirestoreId, curr.id, startDate, endDate);

            if (transactions.isEmpty()) continue;

            hasDataForType = true;
            addSectionTitle(document, getString(R.string.report_label_account_type) + ": " + accountTypeName + " - " + getString(R.string.report_label_currency) + ": " + curr.name);
            document.add(createConsolidatedMovementTable(transactions, acTypeFirestoreId, startDate, useDateRange));
            document.add(Chunk.NEWLINE);
        }

        if (!hasDataForType) {
//            addSectionTitle(document, getString(R.string.report_label_account_type) + ": " + accountTypeName + " - " + getString(R.string.report_no_data));
//            document.add(Chunk.NEWLINE);
        }
        document.close();
        return file;
    }


    // 9. كشف حركة مجمع متعدد العملات
    public File generateConsolidatedMovementReportAllCurrencies(int currency, long startDate, long endDate, boolean useDateRange) throws Exception {
        File file = new File(context.getCacheDir(), "consolidated_movement_all_currencies_" + System.currentTimeMillis() + ".pdf");
        Document document = createDocument(file);
        document.open();

        List<AccountType> types = repository.getAllAccountTypesBlockingReport();
        String currencyName = repository.getCurrencyNameById(currency);

        addTitleAndPeriod(document,
                getString(R.string.report_title_consolidated_movement),
                getString(R.string.report_label_all_accounts),
                currencyName, startDate, endDate, useDateRange);

        for (AccountType type : types) {
            List<TransactionWithAccount> transactions = repository.getConsolidatedMovementAllCurrencies(type.name, currency, startDate, endDate);
            if (transactions.isEmpty()) continue;

            addSectionTitle(document, getString(R.string.report_label_account_type) + ": " + type.name);
            document.add(createConsolidatedMovementTable(transactions, type.name, startDate, useDateRange));
            document.add(Chunk.NEWLINE);
        }

        document.close();
        return file;
    }
    // 7.7-2 تقرير الأرصدة المجمع متعدد العملات
    public File generateConsolidatedBalancesReportAllCurrencyAllTypes( long startDate, long endDate, boolean useDateRange) throws Exception {
        File file = new File(context.getCacheDir(), "consolidated_balances_" + System.currentTimeMillis() + ".pdf");
        Document document = createDocument(file);
        document.open();


        List<AccountType> types = repository.getAllAccountTypesBlockingReport();
        List<Currency> allCurrencies = repository.getAllCurrenciesBlocking();

        addTitleAndPeriod(document,
                getString(R.string.report_title_consolidated_movement),
                getString(R.string.report_label_all_accounts_types),
                getString(R.string.report_label_all_currencies), startDate, endDate, useDateRange);


        for (AccountType type : types) {
            boolean hasDataForType = false;
            String accountTypeName = repository.getAccountTypeNameByFirestoreId(type.getFirestoreId());

            for (Currency curr : allCurrencies) {
                List<AccountBalanceSummary> summaries = repository.getAccountBalancesByType(type.getFirestoreId(), curr.id, startDate, endDate);
                if (summaries.isEmpty()) continue;

                hasDataForType = true;
                addSectionTitle(document, getString(R.string.report_label_account_type) + ": " + accountTypeName + " - " + getString(R.string.report_label_currency) + ": " + curr.name);
                document.add(createBalancesByAccountTypeTable(summaries, curr.name));
                document.add(Chunk.NEWLINE);
            }

            if (!hasDataForType) {
//                addSectionTitle(document, getString(R.string.report_label_account_type) + ": " + accountTypeName + " - " + getString(R.string.report_no_data));
//                document.add(Chunk.NEWLINE);
            }
        }
        document.close();
        return file;
    }
    public File generateConsolidatedBalancesReportAllTypes( int currency, long startDate, long endDate, boolean useDateRange) throws Exception {
        File file = new File(context.getCacheDir(), "consolidated_balances_" + System.currentTimeMillis() + ".pdf");
        Document document = createDocument(file);
        document.open();


        List<AccountType> types = repository.getAllAccountTypesBlockingReport();
        List<Currency> allCurrencies = repository.getAllCurrenciesBlocking();
        String currencyName = repository.getCurrencyNameById(currency);
        addTitleAndPeriod(document,
                getString(R.string.report_title_consolidated_movement),
                getString(R.string.report_label_all_accounts_types),
                currencyName, startDate, endDate, useDateRange);

        boolean hasDataForType = false;
        for (AccountType type : types) {

            String accountTypeName = repository.getAccountTypeNameByFirestoreId(type.getFirestoreId());


                List<AccountBalanceSummary> summaries = repository.getAccountBalancesByType(type.getFirestoreId(), currency, startDate, endDate);
                if (summaries.isEmpty()) continue;

                hasDataForType = true;
                addSectionTitle(document, getString(R.string.report_label_account_type) + ": " + accountTypeName + " - " + getString(R.string.report_label_currency) + ": " + currencyName);
                document.add(createBalancesByAccountTypeTable(summaries, currencyName));
                document.add(Chunk.NEWLINE);



        }
        if (!hasDataForType) {
//                addSectionTitle(document, getString(R.string.report_label_account_type) + ": " + accountTypeName + " - " + getString(R.string.report_no_data));
//                document.add(Chunk.NEWLINE);
        }
        document.close();
        return file;
    }

    // 10. كشف حركة مجمع لجميع الأنواع والعملات
    public File generateConsolidatedMovementReportAllCurrenciesAllTypes( long startDate, long endDate, boolean useDateRange) throws Exception {
        File file = new File(context.getCacheDir(), "consolidated_movement_all_" + System.currentTimeMillis() + ".pdf");
        Document document = createDocument(file);
        document.open();

        List<AccountType> types = repository.getAllAccountTypesBlockingReport();
        List<Currency> allCurrencies = repository.getAllCurrenciesBlocking();

        addTitleAndPeriod(document,
                getString(R.string.report_title_consolidated_movement),
                getString(R.string.report_label_all_accounts_types),
                getString(R.string.report_label_all_currencies), startDate, endDate, useDateRange);

        for (AccountType type : types) {
            boolean hasDataForType = false;

            for (Currency curr : allCurrencies) {
                List<TransactionWithAccount> transactions = repository.getConsolidatedMovementAllCurrencies(type.getFirestoreId(), curr.id, startDate, endDate);
                if (transactions.isEmpty()) continue;

                hasDataForType = true;
                addSectionTitle(document, getString(R.string.report_label_account_type) + ": " + type.name + " - " + getString(R.string.report_label_currency) + ": " + curr.name);
                document.add(createConsolidatedMovementTable(transactions, type.name, startDate, useDateRange));
                document.add(Chunk.NEWLINE);
            }

            if (!hasDataForType) {
//                addSectionTitle(document, getString(R.string.report_label_account_type) + ": " + type.name + " - " + getString(R.string.report_no_data));
//                document.add(Chunk.NEWLINE);
            }
        }

        document.close();
        return file;
    }

    // 10. كشف حركة مجمع لجميع الأنواع والعملات
    public File generateConsolidatedMovementReportAllTypes( int currency, long startDate, long endDate, boolean useDateRange) throws Exception {
        File file = new File(context.getCacheDir(), "consolidated_movement_all_" + System.currentTimeMillis() + ".pdf");
        Document document = createDocument(file);
        document.open();
        List<AccountType> types = repository.getAllAccountTypesBlockingReport();
        String currencyName = repository.getCurrencyNameById(currency);
        addTitleAndPeriod(document,
                getString(R.string.report_title_consolidated_movement),
                getString(R.string.report_label_all_accounts_types),
                currencyName, startDate, endDate, useDateRange);

        boolean hasDataForType = false;
        for (AccountType type : types) {
                List<TransactionWithAccount> transactions = repository.getConsolidatedMovementAllCurrencies(type.getFirestoreId(), currency, startDate, endDate);
                if (transactions.isEmpty()) continue;

                hasDataForType = true;
                addSectionTitle(document, getString(R.string.report_label_account_type) + ": " + type.name + " - " + getString(R.string.report_label_currency) + ": " + currencyName);
                document.add(createConsolidatedMovementTable(transactions, type.name, startDate, useDateRange));
                document.add(Chunk.NEWLINE);



        }
        if (!hasDataForType) {
                addSectionTitle(document, getString(R.string.message_no_transactions_for_account));
//                document.add(Chunk.NEWLINE);
        }
        document.close();
        return file;
    }

    // 11. التقرير الشهري لجميع الأنواع
    public File generateMonthlySummaryByAccountAllTypes(String acTypeFirestoreId, int currency) throws Exception {
        File file = new File(context.getCacheDir(), "monthly_all_types_" + System.currentTimeMillis() + ".pdf");
        Document document = createDocument(file);
        document.open();

        List<AccountType> types = repository.getAllAccountTypesBlockingReport();
        List<Currency> allCurrencies = repository.getAllCurrenciesBlocking();
        String currencyName = repository.getCurrencyNameById(currency);
        addTitleAndPeriod(document,
                getString(R.string.report_title_monthly_summary),
                getString(R.string.report_label_all_accounts_types),
                currencyName, 0, 0, false);
        boolean hasDataForType = false;
        for (AccountType type : types) {
                List<MonthlySummary> summaries = repository.getMonthlySummaryByAccountTypeByCurrency(type.getFirestoreId(), currency);
                if (summaries.isEmpty()) continue;

                hasDataForType = true;
                addSectionTitle(document, getString(R.string.report_label_account_type) + ": " + type.name + " - " + getString(R.string.report_label_currency) + ": " + currencyName);
                document.add(createMonthlySummaryTable(summaries, false, currencyName));
                document.add(Chunk.NEWLINE);
            }

            if (!hasDataForType) {
//                addSectionTitle(document, getString(R.string.report_label_account_type) + ": " + type.name + " - " + getString(R.string.report_no_data));
//                document.add(Chunk.NEWLINE);

        }

        document.close();
        return file;
    }

    public File generateMonthlySummaryByAccountAllCurrenciesAllTypes(String acTypeFirestoreId, int currency) throws Exception {
        File file = new File(context.getCacheDir(), "monthly_all_types_" + System.currentTimeMillis() + ".pdf");
        Document document = createDocument(file);
        document.open();

        List<AccountType> types = repository.getAllAccountTypesBlockingReport();
        List<Currency> allCurrencies = repository.getAllCurrenciesBlocking();

        addTitleAndPeriod(document,
                getString(R.string.report_title_monthly_summary),
                getString(R.string.report_label_all_accounts_types),
                getString(R.string.report_label_all_currencies), 0, 0, false);

        for (AccountType type : types) {
            boolean hasDataForType = false;

            for (Currency curr : allCurrencies) {
                List<MonthlySummary> summaries = repository.getMonthlySummaryByAccountTypeByCurrency(type.getFirestoreId(), curr.getId());
                if (summaries.isEmpty()) continue;

                hasDataForType = true;
                addSectionTitle(document, getString(R.string.report_label_account_type) + ": " + type.name + " - " + getString(R.string.report_label_currency) + ": " + curr.name);
                document.add(createMonthlySummaryTable(summaries, false, curr.name));
                document.add(Chunk.NEWLINE);
            }

            if (!hasDataForType) {
//                addSectionTitle(document, getString(R.string.report_label_account_type) + ": " + type.name + " - " + getString(R.string.report_no_data));
//                document.add(Chunk.NEWLINE);
            }
        }

        document.close();
        return file;
    }

    // ===== الدوال المساعدة لإنشاء الجداول =====

    private void addTitleAndPeriod(Document doc, String title, String subTitle, String currency, long start, long end, boolean useDateRange) throws DocumentException {
        PdfPTable titleTable = new PdfPTable(1);
        titleTable.setWidthPercentage(100);
        titleTable.setRunDirection(isRTL ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);

        PdfPCell titleCell = createCell(title, titleFont, Element.ALIGN_CENTER);
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setPaddingBottom(8);
        titleTable.addCell(titleCell);

        StringBuilder subTitleBuilder = new StringBuilder();
        subTitleBuilder.append(getString(R.string.report_label_currency)).append(": ").append(currency).append(" | ");

        if (useDateRange) {
            subTitleBuilder.append(getString(R.string.report_label_period)).append(": ")
                    .append(getString(R.string.report_label_from)).append(" ").append(formatDate(start))
                    .append(" ").append(getString(R.string.report_label_to)).append(" ").append(formatDate(end));
        } else {
            subTitleBuilder.append(getString(R.string.report_label_all_periods));
        }

        subTitleBuilder.append(" | ").append(subTitle);

        PdfPCell subTitleCell = createCell(subTitleBuilder.toString(), subTitleFont, Element.ALIGN_CENTER);
        subTitleCell.setBorder(Rectangle.NO_BORDER);
        subTitleCell.setPaddingBottom(16);
        titleTable.addCell(subTitleCell);

        doc.add(titleTable);
    }

    private void addSectionTitle(Document document, String title) throws DocumentException {
        document.add(Chunk.NEWLINE);
        PdfPTable titleTable = new PdfPTable(1);
        titleTable.setWidthPercentage(100);
        titleTable.setRunDirection(isRTL ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);

        PdfPCell empCell = createCell("", titleFont2, Element.ALIGN_LEFT);
        empCell.setBorder(Rectangle.NO_BORDER);
        titleTable.addCell(empCell);
        document.add(empCell)
        ;
        PdfPCell titleCell = createCell(title, titleFont2, Element.ALIGN_LEFT);
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setPaddingBottom(8);
        titleTable.addCell(titleCell);
        document.add(titleTable);
//        Paragraph p = new Paragraph(title, titleFont);
//        p.setSpacingAfter(8f);
//        p.setAlignment(isRTL ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
//        document.add(p);
    }

    private PdfPTable createBalancesTable(List<AccountBalanceSummary> summaries) throws DocumentException {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setRunDirection(isRTL ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);
        table.setWidths(new float[]{4f, 2f, 2f, 2f});
        table.setHeaderRows(1);
        table.setSpacingBefore(10f);

        addHeaderCell(table, getString(R.string.report_label_account_name));
        addHeaderCell(table, getString(R.string.report_label_balance_type));
        addHeaderCell(table, getString(R.string.report_label_balance));
        addHeaderCell(table, getString(R.string.report_label_last_transaction_date));

        double totalBalance = 0;
        int rowCounter = 0;

        for(AccountBalanceSummary summary : summaries) {
            boolean isEvenRow = rowCounter % 2 == 0;
            totalBalance += summary.balance;

            addDataCell(table, summary.accountName, Element.ALIGN_CENTER, normalFont, isEvenRow);

            String balanceType = summary.balance >= 0 ?
                    getString(R.string.report_balance_type_them) :
                    getString(R.string.report_balance_type_you);
            addDataCell(table, balanceType, Element.ALIGN_CENTER, normalFont, isEvenRow);

            addDataCell(table, formatCurrency(Math.abs(summary.balance)), Element.ALIGN_CENTER,
                    summary.balance >= 0 ? redFont : greenFont, isEvenRow);

            addDataCell(table, formatDate(summary.lastTransactionDate.getTime()), Element.ALIGN_CENTER, normalFont, isEvenRow);
            rowCounter++;
        }

        addSingleTotalRow(table, getString(R.string.report_label_totals), totalBalance, 4);
        return table;
    }

    private PdfPTable createDetailedMovementTable(List<Transaction> items, int accountId, int currency, long startDate, boolean useDateRange) throws DocumentException {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setRunDirection(isRTL ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);
        table.setWidths(isRTL ? new float[]{2f, 2f, 2f, 4f, 2.3f} : new float[]{2.3f, 4f, 2f, 2f, 2f});
        table.setHeaderRows(1);

        addHeaderCell(table, getString(R.string.report_label_date));
        addHeaderCell(table, getString(R.string.report_label_details));
        addHeaderCell(table, getString(R.string.header_debit_you));
        addHeaderCell(table, getString(R.string.header_credit_you));
        addHeaderCell(table, getString(R.string.report_label_balance));

        double runningBalance = 0;
        double totalDebit = 0;
        double totalCredit = 0;
        int rowCounter = 0;

        if (useDateRange) {
            Double previousBalance = repository.getBalanceBeforeDate(accountId, startDate);
            if (previousBalance != null && previousBalance != 0) {
                runningBalance = previousBalance;
                addDataRow(table, formatDate(startDate), getString(R.string.report_label_previous_balance),
                        runningBalance > 0 ? formatCurrency(runningBalance) : "0",
                        runningBalance < 0 ? formatCurrency(Math.abs(runningBalance)) : "0",
                        formatCurrency(runningBalance), false, rowCounter);
                rowCounter++;
            }
        }

        for (Transaction tx : items) {
            if (useDateRange && tx.getTimestamp().getTime() < startDate) continue;

            runningBalance += (tx.getAmount() * tx.getType());
            double debit = tx.getType() == 1 ? tx.getAmount() : 0;
            double credit = tx.getType() == -1 ? tx.getAmount() : 0;
            totalDebit += debit;
            totalCredit += credit;

            addDataRow(table, formatDate(tx.getTimestamp().getTime()), tx.getDetails(),
                    debit > 0 ? formatCurrency(debit) : "0",
                    credit > 0 ? formatCurrency(credit) : "0",
                    formatCurrency(runningBalance), true, rowCounter);
            rowCounter++;
        }

        addTotalsRow(table, totalCredit, totalDebit, runningBalance, true);
        return table;
    }

    private PdfPTable createMonthlySummaryTable(List<MonthlySummary> summaries, boolean includeCumulative, String currencyName) throws DocumentException {
        int columns = includeCumulative ? 5 : 4;
        PdfPTable table = new PdfPTable(columns);
        table.setRunDirection(isRTL ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);
        table.setWidthPercentage(100);
        float[] aA= new float[]{2.5f, 2.5f, 2.5f, 2.5f, 2f};
        float[] aB= new float[]{2.5f, 2.5f, 2.5f, 2f};

        float[] bA= new float[]{2f, 2.5f, 2.5f, 2.5f, 2.5f};
        float[] bB= new float[]{2f, 2.5f, 2.5f, 2.5f};
//        table.setWidths(includeCumulative ?  aA : aB);
        table.setWidths(includeCumulative ?  (!isRTL ? aA : bA ) : (!isRTL ? aB :bB));
        addHeaderCell(table, getString(R.string.report_label_month));
        addHeaderCell(table, getString(R.string.report_label_debit));
        addHeaderCell(table, getString(R.string.report_label_credit));
        addHeaderCell(table, getString(R.string.report_label_net_movement));
        if (includeCumulative) addHeaderCell(table, getString(R.string.report_label_final_balance));

        double totalDebit = 0;
        double totalCredit = 0;
        int rowCounter = 0;
        double runningBalance = 0;

        for(MonthlySummary summary : summaries) {
            totalDebit += summary.totalDebit;
            totalCredit += summary.totalCredit;
            boolean isEvenRow = rowCounter % 2 == 0;
            double net = summary.totalDebit - summary.totalCredit;
            runningBalance += net;

            addDataCell(table, summary.yearMonth, Element.ALIGN_CENTER, normalFont, isEvenRow);
            addDataCell(table, formatCurrency(summary.totalDebit), Element.ALIGN_CENTER, redFont, isEvenRow);
            addDataCell(table, formatCurrency(summary.totalCredit), Element.ALIGN_CENTER, greenFont, isEvenRow);
            addDataCell(table, formatCurrency(net), Element.ALIGN_CENTER, normalFont, isEvenRow);
            if (includeCumulative) addDataCell(table, formatCurrency(runningBalance), Element.ALIGN_CENTER, blackFont, isEvenRow);
            rowCounter++;
        }

        addTotalsAndFinalBalanceToTable(table, totalCredit, totalDebit, runningBalance, includeCumulative ? 5 : 4, currencyName);
        return table;
    }

    private PdfPTable createConsolidatedMovementTable(List<TransactionWithAccount> transactions, String acTypeFirestoreId, long startDate, boolean useDateRange) throws DocumentException {
        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setRunDirection(isRTL ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);
        table.setWidths( isRTL ? new float[]{2f, 2f, 2f, 4f, 3.5f, 2.3f} : new float[]{2.3f, 3.5f, 4f, 2f, 2f, 2f});

        addHeaderCell(table, getString(R.string.report_label_date));
        addHeaderCell(table, getString(R.string.report_label_account_name));
        addHeaderCell(table, getString(R.string.report_label_details));
        addHeaderCell(table, getString(R.string.report_label_debit));
        addHeaderCell(table, getString(R.string.report_label_credit));
        addHeaderCell(table, getString(R.string.report_label_cumulative_balance));

        double runningBalance = 0;
        double totalDebit = 0;
        double totalCredit = 0;
        int rowCounter = 0;

        if (useDateRange) {
            Double previousBalance = repository.getBalanceByAccountTypeBeforeDate(acTypeFirestoreId, startDate);
            if (previousBalance != null && previousBalance != 0) {
                runningBalance = previousBalance;
                addDataRowConsolidated(table, formatDate(startDate), getString(R.string.report_label_previous_balance),
                        getString(R.string.report_label_previous_balance),
                        runningBalance > 0 ? formatCurrency(runningBalance) : "0",
                        runningBalance < 0 ? formatCurrency(Math.abs(runningBalance)) : "0",
                        formatCurrency(runningBalance), false, rowCounter);
                rowCounter++;
            }
        }

        for(TransactionWithAccount twa : transactions) {
            double amount = twa.transaction.getAmount();
            int type = twa.transaction.getType();
            if(type == 1) totalDebit += amount;
            else totalCredit += amount;

            boolean isEvenRow = rowCounter % 2 == 0;
            runningBalance += (amount * type);

            addDataRowConsolidated(table, formatDate(twa.transaction.getTimestamp().getTime()),
                    twa.accountName, twa.transaction.getDetails(),
                    type == 1 ? formatCurrency(amount) : "0",
                    type == -1 ? formatCurrency(amount) : "0",
                    formatCurrency(runningBalance), true, rowCounter);
            rowCounter++;
        }

        addTotalsRowConsolidated(table, totalCredit, totalDebit, runningBalance);
        return table;
    }

    private PdfPTable createBalancesByAccountTypeTable(List<AccountBalanceSummary> summaries, String currencyName) throws DocumentException {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setRunDirection(isRTL ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);
        table.setWidths(!isRTL ? new float[]{4f, 2f, 2f, 2f} : new float[]{2f, 2f, 2f, 4f});
        table.setHeaderRows(1);
        table.setSpacingBefore(10f);

        addHeaderCell(table, getString(R.string.report_label_account_name));
        addHeaderCell(table, getString(R.string.report_label_balance_type));
        addHeaderCell(table, getString(R.string.report_label_balance));
        addHeaderCell(table, getString(R.string.report_label_last_transaction_date));

        double totalBalance = 0;
        int rowCounter = 0;

        for(AccountBalanceSummary summary : summaries) {
            boolean isEvenRow = rowCounter % 2 == 0;
            totalBalance += summary.balance;

            addDataCell(table, summary.accountName, Element.ALIGN_CENTER, normalFont, isEvenRow);

            String balanceType = summary.balance >= 0 ?
                    getString(R.string.report_balance_type_them) :
                    getString(R.string.report_balance_type_you);
            addDataCell(table, balanceType, Element.ALIGN_CENTER, normalFont, isEvenRow);

            addDataCell(table, formatCurrency(Math.abs(summary.balance)), Element.ALIGN_CENTER,
                    summary.balance >= 0 ? redFont : greenFont, isEvenRow);

            addDataCell(table, formatDate(summary.lastTransactionDate.getTime()), Element.ALIGN_CENTER, normalFont, isEvenRow);
            rowCounter++;
        }

        String label = totalBalance >= 0 ?
                getString(R.string.report_label_final_balance_them) :
                getString(R.string.report_label_final_balance_you);
        addSingleTotalRow(table, label + " - " + getString(R.string.report_label_currency) + ": " + currencyName, totalBalance, 4);
        return table;
    }

    // ===== الدوال المساعدة للخلايا والصفوف =====
    private PdfPCell createCell(String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBorder(Rectangle.NO_BORDER);
        return cell;
    }

    private void addHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, tableHeaderFont));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBackgroundColor(headerBgColor);
        cell.setBorderColor(BaseColor.GRAY);
        cell.setPadding(8);
        cell.setMinimumHeight(20);
        table.addCell(cell);
    }

    private void addDataCell(PdfPTable table, String text, int alignment, Font font, boolean isEvenRow) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        if (isEvenRow) {
            cell.setBackgroundColor(evenRowColor);
        }

        cell.setBorderColor(BaseColor.LIGHT_GRAY);
        cell.setPadding(6);
        cell.setMinimumHeight(18);
        table.addCell(cell);
    }

    private void addDataRow(PdfPTable table, String date, String details, String debit, String credit, String balance, boolean isTransaction, int rowCounter) {
        boolean isEvenRow = rowCounter % 2 == 0;
        Font dataFont = isTransaction ? normalFont : totalsFont;

        addDataCell(table, date, Element.ALIGN_CENTER, dataFont, isEvenRow);
        addDataCell(table, details, Element.ALIGN_CENTER, dataFont, isEvenRow);
        addDataCell(table, debit, Element.ALIGN_CENTER, isTransaction ? redFont : totalsFont, isEvenRow);
        addDataCell(table, credit, Element.ALIGN_CENTER, isTransaction ? greenFont : totalsFont, isEvenRow);
        addDataCell(table, balance, Element.ALIGN_CENTER, isTransaction ? blackFont : totalsFont, isEvenRow);
    }

    private void addDataRowConsolidated(PdfPTable table, String date, String accountName, String details, String debit, String credit, String balance, boolean isTransaction, int rowCounter) {
        boolean isEvenRow = rowCounter % 2 == 0;
        Font dataFont = isTransaction ? normalFont : totalsFont;

        addDataCell(table, date, Element.ALIGN_CENTER, dataFont, isEvenRow);
        addDataCell(table, accountName, Element.ALIGN_CENTER, dataFont, isEvenRow);
        addDataCell(table, details, Element.ALIGN_CENTER, dataFont, isEvenRow);
        addDataCell(table, debit, Element.ALIGN_CENTER, isTransaction ? redFont : totalsFont, isEvenRow);
        addDataCell(table, credit, Element.ALIGN_CENTER, isTransaction ? greenFont : totalsFont, isEvenRow);
        addDataCell(table, balance, Element.ALIGN_CENTER, isTransaction ? blackFont : totalsFont, isEvenRow);
    }

    private void addTotalsRow(PdfPTable table, double totalCredit, double totalDebit, double finalBalance, boolean isSingle) {
        PdfPCell totalsLabelCell = new PdfPCell(new Phrase(getString(R.string.report_label_totals), totalsFont));
        totalsLabelCell.setColspan(2);
        totalsLabelCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        totalsLabelCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        totalsLabelCell.setPadding(8);
        totalsLabelCell.setBackgroundColor(lightCyan);
        table.addCell(totalsLabelCell);

        addDataCell(table, formatCurrency(totalDebit), Element.ALIGN_CENTER, redFont, false);
        addDataCell(table, formatCurrency(totalCredit), Element.ALIGN_CENTER, greenFont, false);
        table.addCell(createCell("", normalFont, Element.ALIGN_CENTER));

        String finalBalanceText = (finalBalance >= 0 ?
                getString(R.string.summary_final_balance_on_you) :
                getString(R.string.summary_final_balance_for_you));

        PdfPCell finalBalanceLabelCell = new PdfPCell(new Phrase(finalBalanceText, totalsFont));
        finalBalanceLabelCell.setColspan(4);
        finalBalanceLabelCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        finalBalanceLabelCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        finalBalanceLabelCell.setPadding(8);
        table.addCell(finalBalanceLabelCell);

        PdfPCell finalBalanceCell = new PdfPCell(new Phrase(formatCurrency(Math.abs(finalBalance)), totalsFont));
        finalBalanceCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        finalBalanceCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        finalBalanceCell.setBackgroundColor(BaseColor.YELLOW);
        finalBalanceCell.setPadding(8);
        table.addCell(finalBalanceCell);
    }

    private void addTotalsRowConsolidated(PdfPTable table, double totalCredit, double totalDebit, double finalBalance) {
        PdfPCell totalsLabelCell = new PdfPCell(new Phrase(getString(R.string.report_label_totals), totalsFont));
        totalsLabelCell.setColspan(3);
        totalsLabelCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        totalsLabelCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        totalsLabelCell.setPadding(8);
        totalsLabelCell.setBackgroundColor(lightCyan);
        table.addCell(totalsLabelCell);

        addDataCell(table, formatCurrency(totalDebit), Element.ALIGN_CENTER, redFont, false);
        addDataCell(table, formatCurrency(totalCredit), Element.ALIGN_CENTER, greenFont, false);
        table.addCell(createCell("", normalFont, Element.ALIGN_CENTER));

        String finalBalanceText = (finalBalance >= 0 ?
                getString(R.string.report_label_final_balance_them) :
                getString(R.string.report_label_final_balance_you));

        PdfPCell finalBalanceLabelCell = new PdfPCell(new Phrase(finalBalanceText, totalsFont));
        finalBalanceLabelCell.setColspan(5);
        finalBalanceLabelCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        finalBalanceLabelCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        finalBalanceLabelCell.setPadding(8);
        table.addCell(finalBalanceLabelCell);

        PdfPCell finalBalanceCell = new PdfPCell(new Phrase(formatCurrency(Math.abs(finalBalance)), totalsFont));
        finalBalanceCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        finalBalanceCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        finalBalanceCell.setBackgroundColor(BaseColor.YELLOW);
        finalBalanceCell.setPadding(8);
        table.addCell(finalBalanceCell);
    }

    private void addSingleTotalRow(PdfPTable table, String label, double total, int totalColumns) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, totalsFont));
        labelCell.setColspan(totalColumns - 1);
        labelCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        labelCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        labelCell.setPadding(8);
        labelCell.setBackgroundColor(lightCyan);
        table.addCell(labelCell);

        PdfPCell totalCell = new PdfPCell(new Phrase(formatCurrency(Math.abs(total)), totalsFont));
        totalCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        totalCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        totalCell.setBackgroundColor(lightCyan);
        totalCell.setPadding(8);
        table.addCell(totalCell);
    }

    private void addTotalsAndFinalBalanceToTable(PdfPTable table, double totalCredit, double totalDebit, double finalBalance, int totalColumns, String currencyName) {
        PdfPCell totalsLabelCell = new PdfPCell(new Phrase(getString(R.string.report_label_totals), totalsFont));
        totalsLabelCell.setColspan(1);
        totalsLabelCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        totalsLabelCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        totalsLabelCell.setPadding(8);
        totalsLabelCell.setBackgroundColor(lightCyan);
        table.addCell(totalsLabelCell);

        addDataCell(table, formatCurrency(totalDebit), Element.ALIGN_CENTER, redFont, false);
        addDataCell(table, formatCurrency(totalCredit), Element.ALIGN_CENTER, greenFont, false);

        if(totalColumns == 5) {
            table.addCell(createCell("", normalFont, Element.ALIGN_CENTER));
            table.addCell(createCell("", normalFont, Element.ALIGN_CENTER));
        } else {
            table.addCell(createCell("", normalFont, Element.ALIGN_CENTER));
        }

        String finalBalanceText = (finalBalance >= 0 ?
                getString(R.string.report_label_final_balance_them) :
                getString(R.string.report_label_final_balance_you));

        PdfPCell finalBalanceLabelCell = new PdfPCell(new Phrase(finalBalanceText + " - " + getString(R.string.report_label_currency) + ": " + currencyName, totalsFont));
        finalBalanceLabelCell.setColspan(totalColumns - 1);
        finalBalanceLabelCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        finalBalanceLabelCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        finalBalanceLabelCell.setPadding(8);
        table.addCell(finalBalanceLabelCell);

        PdfPCell finalBalanceCell = new PdfPCell(new Phrase(formatCurrency(Math.abs(finalBalance)), totalsFont));
        finalBalanceCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        finalBalanceCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        finalBalanceCell.setBackgroundColor(BaseColor.YELLOW);
        finalBalanceCell.setPadding(8);
        table.addCell(finalBalanceCell);
    }

    private String formatCurrency(double amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(amount);
    }

    private String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return sdf.format(new Date(timestamp));
    }
}