
package com.hpp.daftree.syncmanagers;


import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.hpp.daftree.MyApplication;
import com.hpp.daftree.R;
import com.hpp.daftree.UUIDGenerator;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.AccountType;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.Transaction;
import com.hpp.daftree.utils.SecureLicenseManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class RestoreHelper {
    private static final String TAG = "RestoreHelper";

    private enum SourceType {SOURCE_ONE, SOURCE_TWO, SOURCE_THREE, SOURCE_FOUR, SOURCE_FIVE, UNKNOWN}

    public interface RestoreListener {
        void onRestoreSuccess(int accountsImported, int transactionsImported);

        void onRestoreError(String error);
    }

    private final Context context;
    private final AppDatabase appDb;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String currentUserId;

    private  boolean isGuest = false;
    private final String guestUID ;
    public RestoreHelper(Context context) {
        this.context = context;
        this.appDb = AppDatabase.getDatabase(context);
        this.currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        isGuest = SecureLicenseManager.getInstance(context).isGuest();
        guestUID = SecureLicenseManager.getInstance(context).guestUID();
    }

    public void importDatabase(Uri sourceUri, RestoreListener listener) {
        executor.execute(() -> {
            SQLiteDatabase oldDb = null;
            File tempDbFile = null;
            try {
                tempDbFile = copyUriToTempFile(sourceUri);
                oldDb = SQLiteDatabase.openDatabase(tempDbFile.getPath(), null, SQLiteDatabase.OPEN_READONLY);
                SourceType type = detectSourceType(oldDb);
                if (type == SourceType.UNKNOWN) {
                    throw new Exception("مصدر البيانات غير معروف أو غير مدعوم.");
                }

                final int[] counts = performImport(oldDb, type);
//                handler.post(() -> listener.onRestoreSuccess(counts[0], counts[1]));
                // **توجيه عملية الاستيراد بناءً على النوع**
//                final int[] counts;
//                if (type == SourceType.SOURCE_FOUR) {
//                    counts = performImportForSourceFour(oldDb);
//                } else {
//                    counts = performImport(oldDb, type);
//                }

                handler.post(() -> listener.onRestoreSuccess(counts[0], counts[1]));

            } catch (Exception e) {
                Log.e(TAG, "Import failed", e);
                handler.post(() -> listener.onRestoreError("فشل الاستيراد: " + e.getMessage()));
            } finally {
                if (oldDb != null) oldDb.close();
                if (tempDbFile != null) tempDbFile.delete();
            }
        });
    }

    /**
     * دالة جديدة ومخصصة لاستيراد النسخة الاحتياطية من التطبيق (المصدر الرابع).
     */
    private int[] performImport1(SQLiteDatabase oldDb, SourceType type) {
        final int[] counts = {0, 0}; // [accounts, transactions]
        final int[] accountsImported = {0};
        final int[] transactionsImported = {0};
        appDb.runInTransaction(() -> {
            Map<Integer, String> currencyMap = loadAndMapCurrencies(oldDb, type);
            // **التعديل الأول**: استدعاء الدالة الجديدة التي تربط اسم النوع بالـ firestoreId الجديد
            Map<String, String> accountTypeNameToFirestoreIdMap = loadAndMapAccountTypes(oldDb, type);
            Map<Integer, String> accountTypeNameMap= appDb.accountTypeDao().getAllAccountTypesBlockingRestore()
                    .stream()
                    .collect(Collectors.toMap(accountType -> accountType.id, accountType -> accountType.getName()));
            // **التعديل الثاني**: تمرير الخريطة الجديدة إلى دالة استيراد الحسابات
            Map<Integer, Integer> accountIdMap = importAndMapAccounts(oldDb, type, accountTypeNameToFirestoreIdMap, accountsImported,accountTypeNameMap);
            Map<String, Integer> currencyNameToIdMap = appDb.currencyDao().getAllCurrenciesBlocking()
                    .stream()
                    .collect(Collectors.toMap(c -> c.name, c -> c.id));
            importTransactions(oldDb, type, accountIdMap, currencyMap, transactionsImported, currencyNameToIdMap);

//            Map<String, Integer> currencyNameToIdMap = appDb.currencyDao().getAllCurrenciesBlocking()
//                    .stream()
//                    .collect(Collectors.toMap(c -> c.name, c -> c.id, (v1, v2) -> v1)); // Handle duplicates

        });
        return new int[]{accountsImported[0], transactionsImported[0]};

    }
    private int[] performImport(SQLiteDatabase oldDb, SourceType type) {
        final int[] counts = {0, 0}; // [accounts, transactions]
        final int[] accountsImported = {0};
        final int[] transactionsImported = {0};

        appDb.runInTransaction(() -> {
            Map<Integer, String> currencyMap = loadAndMapCurrencies(oldDb, type);

            // **الإصلاح: استيراد أنواع الحسابات أولاً بشكل منفصل وكامل**
            Map<String, String> accountTypeNameToFirestoreIdMap = loadAndMapAccountTypes(oldDb, type);
            Map<Integer, String> accountTypeNameMap = new HashMap<>(this.accountTypeNameMap); // نسخ البيانات

            // **التأكد من اكتمال استيراد أنواع الحسابات قبل المتابعة**
            Log.d(TAG, "Completed account types import. Total types: " + accountTypeNameToFirestoreIdMap.size());

            // **الآن فقط نبدأ باستيراد الحسابات بعد اكتمال أنواع الحسابات**
            Map<Integer, Integer> accountIdMap = importAndMapAccounts(oldDb, type, accountTypeNameToFirestoreIdMap, accountsImported, accountTypeNameMap);

            Map<String, Integer> currencyNameToIdMap = appDb.currencyDao().getAllCurrenciesBlocking()
                    .stream()
                    .collect(Collectors.toMap(c -> c.name, c -> c.id));

            importTransactions(oldDb, type, accountIdMap, currencyMap, transactionsImported, currencyNameToIdMap);
        });

        return new int[]{accountsImported[0], transactionsImported[0]};
    }
    private SourceType detectSourceType(SQLiteDatabase db) {

        boolean isSourceFive = tableExists(db, "accounts") &&
                tableExists(db, "tableNotifications");
        if (isSourceFive) {
            Log.d(TAG, "Detected Source Type 5 (App Backup)");
            return SourceType.SOURCE_FIVE;
        }
        boolean isSourceFour = tableExists(db, "accounts") &&
                tableExists(db, "transactions") &&
                tableExists(db, "currencies") &&
                tableExists(db, "account_types") &&
                tableExists(db, "user_profile");
        if (isSourceFour) {
            Log.d(TAG, "Detected Source Type 4 (App Backup)");
            return SourceType.SOURCE_FOUR;
        }


        if (tableExists(db, "accounts_tbl") && tableExists(db, "details_tbl")) {
            Log.d(TAG, "Detected Source Type 3");
            return SourceType.SOURCE_THREE;
        }

        if (tableExists(db, "transactions")) {
            try (Cursor c = db.rawQuery("SELECT * FROM transactions LIMIT 1", null)) {
                boolean hasParam2 = c.getColumnIndex("param2") != -1;
                boolean hasCurrId = c.getColumnIndex("curr_id") != -1;

                // القاعدة الأولى تتميز بوجود كلا العمودين
                if (hasParam2 && hasCurrId) {
                    Log.d(TAG, "Detected Source Type 1");
                    return SourceType.SOURCE_ONE;
                }
                // القاعدة الثانية لا تحتوي على curr_id
                if (!hasCurrId) {
                    Log.d(TAG, "Detected Source Type 2");
                    return SourceType.SOURCE_TWO;
                }
            }
        }
        return SourceType.UNKNOWN;
    }


    private Map<Integer, String> loadAndMapCurrencies(SQLiteDatabase oldDb, SourceType type) {
        Map<Integer, String> currencyMap = new HashMap<>();
        String query;

        // تحديد الجدول الصحيح لكل نوع قاعدة بيانات
        switch (type) {
            case SOURCE_FIVE:
                query = "SELECT _id, name FROM `currency` ORDER By _id  ASC";
                break;
            case SOURCE_FOUR:
                query = "SELECT id, name FROM `currencies` ORDER By id  ASC";
                break;
            case SOURCE_THREE:
                query = "SELECT c_id, c_name FROM `Currency_tbl` ORDER By c_id  ASC";
                break;
            case SOURCE_ONE:
                query = "SELECT ID, name FROM `currency` ORDER By ID  ASC";
                break;
            case SOURCE_TWO:
                query = "SELECT ID, name FROM `groups` ORDER By ID  ASC";
                break;
            default:
                return currencyMap;
        }

        // جلب جميع العملات الموجودة محلياً
        List<Currency> existingCurrencies = appDb.currencyDao().getAllCurrency();

        Map<String, Currency> existingCurrencyMap = new HashMap<>();
        Set<String> existingNamesSet = new HashSet<>();

        for (Currency currency : existingCurrencies) {
            existingCurrencyMap.put(currency.getName().toLowerCase(), currency);
            existingNamesSet.add(currency.getName());
        }

        // قائمة الكلمات الرئيسية للعملات المتشابهة
        Map<String, String[]> currencyKeywords = new HashMap<>();
        currencyKeywords.put("دولار", new String[]{"دولار", "دولار أمريكي", "دولار امريكي", "أمريكي", "امريكي"});

// تحسين خاص للريال للتمييز بين الأنواع المختلفة
        currencyKeywords.put("ريال يمني", new String[]{"ريال يمني", "اليمن", "يمني", "الريال اليمني"});
        currencyKeywords.put("ريال سعودي", new String[]{"ريال سعودي", "السعودي", "سعودي", "الريال السعودي", "ريال سعودي"});
        currencyKeywords.put("ريال عماني", new String[]{"ريال عماني", "عماني", "الريال العماني"});
        currencyKeywords.put("ريال قطري", new String[]{"ريال قطري", "قطري", "الريال القطري"});

// العملات الأخرى
        currencyKeywords.put("يورو", new String[]{"يورو", "يورو أوروبي", "يورو اوروبي", "أوروبي", "اوروبي"});
        currencyKeywords.put("درهم", new String[]{"درهم", "درهم إماراتي", "إماراتي", "درهم اماراتي", "اماراتي"});
        currencyKeywords.put("دينار", new String[]{"دينار", "دينار كويتي", "دينار بحريني", "كويتي", "بحريني"});
        currencyKeywords.put("جنيه", new String[]{"جنيه", "جنيه مصري", "جنية مصري", "جنيه استرليني", "جنية استرليني"});
        currencyKeywords.put("جنية", new String[]{"جنية", "جنيه مصري", "جنية مصري", "جنيه استرليني", "جنية استرليني"});
        try (Cursor c = oldDb.rawQuery(query, null)) {
            while (c.moveToNext()) {
                int id = c.getInt(0);
                String name = c.getString(1).replaceAll("\\s+", " ").trim();

                // معالجة الأسماء الخاصة
                if (name.equals(context.getString(R.string.account_type_general))) {
                    name = context.getString(R.string.local_currency);
                }
                if (name.equals(context.getString(R.string.local_currency))) {
                    String localCurrencyName = appDb.currencyDao().getFirstCurrencyById();
                    if (localCurrencyName != null && !localCurrencyName.isEmpty()) {
                        name = localCurrencyName;
                    } else {
                        name = MyApplication.defaultCurrencyName;
                    }
                }
                if (name == null) {
                    name = MyApplication.defaultCurrencyName;
                }

                // البحث عن عملة مطابقة جزئياً
                Currency matchedCurrency = findMatchingCurrency(name, existingCurrencyMap, currencyKeywords);
                
                if(isGuest) {
                    currentUserId = guestUID;
                }
                if (matchedCurrency != null) {
                    // تم العثور على عملة مطابقة
                    String matchedName = matchedCurrency.getName();

                    // التحقق إذا كان اسم العملة المستوردة أكثر دقة وتفصيلاً
                    if (isMoreDetailedName(name, matchedName)) {
                        // تحديث اسم العملة المخزنة إلى الاسم الأكثر تفصيلاً
                        Log.d(TAG, "Updating currency name from '" + matchedName + "' to '" + name + "'");
                        matchedCurrency.setName(name);

                        // ⭐ تحديث code و symbol للعملة المحدثة
                        updateCurrencyCodeAndSymbol(matchedCurrency);

                        matchedCurrency.setLastModified(System.currentTimeMillis());
                        matchedCurrency.setSyncStatus("EDITED");
                        appDb.currencyDao().update(matchedCurrency);

                        // تحديث الخريطة والمجموعة
                        existingCurrencyMap.remove(matchedName.toLowerCase());
                        existingCurrencyMap.put(name.toLowerCase(), matchedCurrency);
                        existingNamesSet.remove(matchedName);
                        existingNamesSet.add(name);

                        currencyMap.put(id, name);
                    } else {
                        // استخدام الاسم الموجود (قد يكون أكثر دقة أو متساوياً)
                        currencyMap.put(id, matchedName);

                        // ⭐ تحديث code و symbol للعملة الموجودة إذا كانت مفقودة
                        if (matchedCurrency.getCode() == null || matchedCurrency.getCode().isEmpty() ||
                                matchedCurrency.getSymbol() == null || matchedCurrency.getSymbol().isEmpty()) {
                            updateCurrencyCodeAndSymbol(matchedCurrency);
                            matchedCurrency.setLastModified(System.currentTimeMillis());
                            matchedCurrency.setSyncStatus("EDITED");
                            appDb.currencyDao().update(matchedCurrency);
                        }
                    }
                } else {
                    // لا توجد عملة مطابقة، إضافة العملة الجديدة
                    currencyMap.put(id, name);

                    if (!existingNamesSet.contains(name)) {
                        Log.d(TAG, "New currency found, adding to local DB: " + name);
                        Currency newCurrency = new Currency(name);
                        newCurrency.setOwnerUID(currentUserId);
                        newCurrency.setFirestoreId(UUIDGenerator.generateSequentialUUID());
                        newCurrency.setSyncStatus("NEW");
                        newCurrency.setLastModified(System.currentTimeMillis());
                        newCurrency.setDefault(false);

                        // ⭐ توليد code و symbol للعملة الجديدة
                        CurrencyCodeSymbol currencyData = generateCurrencyCodeAndSymbol(name);
                        newCurrency.setCode(currencyData.code);
                        newCurrency.setSymbol(currencyData.symbol);

                        try {
                            appDb.currencyDao().insert(newCurrency);
                            existingCurrencyMap.put(name.toLowerCase(), newCurrency);
                            existingNamesSet.add(name);
                            Log.d(TAG, "Currency added successfully: " + name +
                                    " [code: " + currencyData.code + ", symbol: " + currencyData.symbol + "]");
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to add currency: " + name, e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading currencies from backup", e);
        }

        return currencyMap;
    }

    /**
     * تحديث code و symbol للعملة
     */
    private void updateCurrencyCodeAndSymbol(Currency currency) {
        CurrencyCodeSymbol currencyData = generateCurrencyCodeAndSymbol(currency.getName());
        currency.setCode(currencyData.code);
        currency.setSymbol(currencyData.symbol);
        Log.d(TAG, "Updated currency code and symbol: " + currency.getName() +
                " -> code: " + currencyData.code + ", symbol: " + currencyData.symbol);
    }
    /**
     * البحث عن عملة مطابقة جزئياً بناءً على الكلمات الرئيسية
     */
    private Currency findMatchingCurrency1(String importedName, Map<String,
            Currency> existingCurrencyMap, Map<String,
            String[]> currencyKeywords) {
        String importedNameLower = importedName.toLowerCase();

        // البحث المباشر أولاً
        if (existingCurrencyMap.containsKey(importedNameLower)) {
            return existingCurrencyMap.get(importedNameLower);
        }

        // البحث باستخدام الكلمات الرئيسية
        for (Map.Entry<String, String[]> entry : currencyKeywords.entrySet()) {
            String mainKeyword = entry.getKey();
            String[] relatedKeywords = entry.getValue();

            // التحقق إذا كان اسم العملة المستوردة يحتوي على أي من الكلمات الرئيسية
            boolean importedContainsKeyword = false;
            for (String keyword : relatedKeywords) {
                if (importedNameLower.contains(keyword.toLowerCase())) {
                    importedContainsKeyword = true;
                    break;
                }
            }

            if (importedContainsKeyword) {
                // البحث في العملات الموجودة عن عملة تحتوي على أي من الكلمات الرئيسية
                for (Currency existingCurrency : existingCurrencyMap.values()) {
                    String existingNameLower = existingCurrency.getName().toLowerCase();

                    for (String keyword : relatedKeywords) {
                        if (existingNameLower.contains(keyword.toLowerCase())) {
                            return existingCurrency; // وجدنا عملة مطابقة
                        }
                    }
                }
            }
        }

        return null; // لم يتم العثور على عملة مطابقة
    }
    /**
     * البحث عن عملة مطابقة بدقة أعلى مع تمييز بين العملات المتشابهة
     */
    private Currency findMatchingCurrency(String importedName, Map<String, Currency> existingCurrencyMap, Map<String, String[]> currencyKeywords) {
        String importedNameLower = importedName.toLowerCase();

        // 1. البحث المباشر أولاً (التطابق التام)
        if (existingCurrencyMap.containsKey(importedNameLower)) {
            return existingCurrencyMap.get(importedNameLower);
        }

        // 2. البحث باستخدام الكلمات الرئيسية مع الترجيح للأفضل تطابقاً
        Currency bestMatch = null;
        int bestMatchScore = 0;

        for (Currency existingCurrency : existingCurrencyMap.values()) {
            String existingNameLower = existingCurrency.getName().toLowerCase();

            int matchScore = calculateMatchScore(importedNameLower, existingNameLower, currencyKeywords);

            if (matchScore > bestMatchScore) {
                bestMatchScore = matchScore;
                bestMatch = existingCurrency;
            }
        }

        // 3. إرجاع أفضل تطابق فقط إذا تجاوز حداً معيناً
        return bestMatchScore >= 2 ? bestMatch : null;
    }

    /**
     * حساب درجة التطابق بين اسم العملة المستوردة والعملة الموجودة
     */
    private int calculateMatchScore(String importedName, String existingName, Map<String, String[]> currencyKeywords) {
        int score = 0;

        // التطابق التام
        if (importedName.equals(existingName)) {
            return 10;
        }

        // احتواء أحد الأسماء داخل الآخر
        if (importedName.contains(existingName) || existingName.contains(importedName)) {
            score += 3;
        }

        // التحقق من الكلمات الرئيسية المشتركة
        for (Map.Entry<String, String[]> entry : currencyKeywords.entrySet()) {
            String mainKeyword = entry.getKey();
            String[] relatedKeywords = entry.getValue();

            boolean importedHasKeyword = false;
            boolean existingHasKeyword = false;

            // التحقق من وجود الكلمات الرئيسية في الاسم المستورد
            for (String keyword : relatedKeywords) {
                if (importedName.contains(keyword.toLowerCase())) {
                    importedHasKeyword = true;
                    break;
                }
            }

            // التحقق من وجود الكلمات الرئيسية في الاسم الموجود
            for (String keyword : relatedKeywords) {
                if (existingName.contains(keyword.toLowerCase())) {
                    existingHasKeyword = true;
                    break;
                }
            }

            // إذا كان كلا الاسمين يحتويان على نفس مجموعة الكلمات الرئيسية
            if (importedHasKeyword && existingHasKeyword) {
                score += 2;

                // تحسين الدقة: التحقق من أن الأسماء تحتوي على نفس النوع من الريال
                if (mainKeyword.equals("ريال")) {
                    // تحسين التمييز بين أنواع الريال المختلفة
                    if (importedName.contains("يمني") && existingName.contains("يمني")) {
                        score += 3; // تطابق عالي لليمني
                    } else if (importedName.contains("سعودي") && existingName.contains("سعودي")) {
                        score += 3; // تطابق عالي للسعودي
                    } else if (importedName.contains("يمني") && existingName.contains("سعودي")) {
                        score -= 2; // خصم للتطابق الخاطئ بين يمني وسعودي
                    } else if (importedName.contains("سعودي") && existingName.contains("يمني")) {
                        score -= 2; // خصم للتطابق الخاطئ بين سعودي ويمني
                    }
                }
            } else if (importedHasKeyword != existingHasKeyword) {
                // إذا كان أحدهما فقط يحتوي على الكلمة الرئيسية
                score -= 1;
            }
        }

        // تحسين إضافي: مقارنة عدد الكلمات والتشابه
        String[] importedWords = importedName.split("\\s+");
        String[] existingWords = existingName.split("\\s+");

        int commonWords = 0;
        for (String impWord : importedWords) {
            for (String expWord : existingWords) {
                if (impWord.equals(expWord)) {
                    commonWords++;
                    break;
                }
            }
        }

        score += commonWords;

        return score;
    }
    /**
     * التحقق إذا كان الاسم الأول أكثر تفصيلاً من الثاني
     */
    private boolean isMoreDetailedName(String name1, String name2) {
        String name1Lower = name1.toLowerCase();
        String name2Lower = name2.toLowerCase();

        // إذا كان الاسم الأول أطول ويحتوي على الاسم الثاني، فهو أكثر تفصيلاً
        if (name1Lower.contains(name2Lower) && name1.length() > name2.length()) {
            return true;
        }

        // إذا كان الاسم الثاني أطول ويحتوي على الاسم الأول، فهو الأكثر تفصيلاً
        if (name2Lower.contains(name1Lower) && name2.length() > name1.length()) {
            return false;
        }

        // حساب عدد الكلمات (افتراض أن الأسماء ذات الكلمات الأكثر تكون أكثر تفصيلاً)
        int wordCount1 = name1.split("\\s+").length;
        int wordCount2 = name2.split("\\s+").length;

        return wordCount1 > wordCount2;
    }
    private final Map<Integer, String> accountTypeNameMap = new HashMap<>();

    /**
     * !! دالة جديدة مخصصة للمصدر الرابع لإنشاء خريطة من اسم نوع الحساب إلى firestoreId الجديد !!
     *
     * @param oldDb قاعدة البيانات القديمة
     * @return خريطة تربط اسم نوع الحساب بالـ firestoreId الجديد الخاص به في قاعدة البيانات الحالية
     */
    private Map<String, String> loadAndMapAccountTypes55(SQLiteDatabase oldDb, SourceType type) {
        Map<String, String> nameToFirestoreIdMap = new HashMap<>();
        accountTypeNameMap.clear();
        if (type == SourceType.SOURCE_TWO) return nameToFirestoreIdMap;

        String query;
        if (type == SourceType.SOURCE_FOUR) {
            query = "SELECT id , name FROM `account_types` ORDER By id ASC";
        } else if (type == SourceType.SOURCE_FIVE) {
            query = "SELECT _id AS id , title AS name FROM `category` ORDER By _id ASC";

        } else if (type == SourceType.SOURCE_THREE) {
            query = "SELECT ctgry_no, ctgry_name FROM `gategory_tbl` ORDER By ctgry_no ASC";
        } else { // SOURCE_ONE
            query = "SELECT ID, name FROM `groups` ORDER By ID ASC";
        }

        List<String> existingTypeNames = appDb.accountTypeDao().getAllAccountTypeNames();
        Set<String> existingNamesSet = new HashSet<>(existingTypeNames);

        try (Cursor c = oldDb.rawQuery(query, null)) {
            while (c.moveToNext()) {
                int id = c.getInt(0);
                String originalName = c.getString(1).replaceAll("\\s+", " ").trim();
                AccountType existingType;
                if (type == SourceType.SOURCE_FOUR) {
                    originalName = c.getString(c.getColumnIndexOrThrow("name"));

                }
                String nameToCheck = originalName;
                if (originalName.startsWith("ال")) {
                    nameToCheck = originalName.substring(2);
                }

                String matchedExistingName = null;
                for (String existingName : existingNamesSet) {
                    if (existingName.equalsIgnoreCase(nameToCheck) || existingName.equalsIgnoreCase(originalName)) {
                        matchedExistingName = existingName;
                        break;
                    }
                }
                existingType = appDb.accountTypeDao().getAccountTypeByName(matchedExistingName);
                if (existingType != null) {
                    // إذا كان موجودًا، استخدم الـ firestoreId الحالي الخاص به
                    nameToFirestoreIdMap.put(originalName, existingType.getFirestoreId());
                    accountTypeNameMap.put(id, matchedExistingName);
                } else {
                    if(isGuest) {
                        Log.d(TAG, "guestUID: " + guestUID);
                        currentUserId = guestUID;
                    }
                    // لم يتم العثور على تطابق، أضف التصنيف الجديد باسمه الكامل
                    Log.d(TAG, "New account type found, adding: " + originalName);
                    AccountType newType = new AccountType(originalName.replaceAll("\\s+", " ").trim());
                    String newFirestoreId = UUIDGenerator.generateSequentialUUID();
                    newType.setOwnerUID(currentUserId);
                    newType.setFirestoreId(newFirestoreId);
                    newType.setSyncStatus("NEW");
                    newType.setLastModified(System.currentTimeMillis());
                    newType.setDefault(false);
                    appDb.accountTypeDao().insert(newType);
                    existingNamesSet.add(originalName);
                    nameToFirestoreIdMap.put(originalName, newFirestoreId);
                    accountTypeNameMap.put(id, originalName);
                }
            }
        }
        return nameToFirestoreIdMap;
    }

       private Map<Integer, Integer> importAndMapAccounts55(SQLiteDatabase oldDb,
                                                       SourceType type,
                                                       Map<String, String> accountTypeNameToFirestoreIdMap,
                                                       int[] accountsImported,
                                                       Map<Integer, String> accountTypeNameMap) {
        Map<Integer, Integer> accountIdMap = new HashMap<>();
        String query;

        if (type == SourceType.SOURCE_FOUR) {
            query = "SELECT * FROM accounts ORDER BY id ASC";
        } else if (type == SourceType.SOURCE_FIVE) {
            query = "SELECT _id AS account_no, name AS account_name, phone AS mobile, category_id AS ctgry_no FROM accounts ORDER BY account_no ASC";
        } else if (type == SourceType.SOURCE_THREE) {
            query = "SELECT account_no, account_name, mobile, ctgry_no FROM accounts_tbl ORDER BY account_no ASC";
        } else {
            query = "SELECT ID, name, gsm, g_id FROM customers ORDER BY ID ASC";
        }

        try (Cursor c = oldDb.rawQuery(query, null)) {
            while (c.moveToNext()) {
                String name = null;
                int oldId = 0;

                if (type == SourceType.SOURCE_FOUR) {
                    name = c.getString(c.getColumnIndexOrThrow("accountName"));
                    oldId = c.getInt(c.getColumnIndexOrThrow("id"));
                } else {
                    name = c.getString(type == SourceType.SOURCE_THREE || type == SourceType.SOURCE_FIVE ?
                                    c.getColumnIndexOrThrow("account_name") : c.getColumnIndexOrThrow("name"))
                            .replaceAll("\\s+", " ").trim();
                    oldId = c.getInt(type == SourceType.SOURCE_THREE || type == SourceType.SOURCE_FIVE ?
                            c.getColumnIndexOrThrow("account_no") : c.getColumnIndexOrThrow("ID"));
                }

                Account existingAccount = appDb.accountDao().getAccountByNameBlocking(name);
                if (existingAccount == null) {
                    Account newAccount = new Account();
                    newAccount.setOwnerUID(currentUserId);
                    newAccount.setAccountName(name);

                    String accountTypeName = null;
                    String accountTypeFirestoreId = null;

                    if (type == SourceType.SOURCE_FOUR) {
                        // معالجة SOURCE_FOUR
                        accountTypeName = c.getString(c.getColumnIndexOrThrow("accountType"));
                        newAccount.setPhoneNumber(c.getString(c.getColumnIndexOrThrow("phoneNumber")));

                        // البحث عن firestoreId مع تطبيق نفس منطق إزالة "الـ"
                        accountTypeFirestoreId = findMatchingFirestoreId(accountTypeName, accountTypeNameToFirestoreIdMap);
                    } else {
                        // معالجة المصادر الأخرى
                        newAccount.setPhoneNumber(c.getString(type == SourceType.SOURCE_THREE  || type == SourceType.SOURCE_FIVE ?
                                c.getColumnIndexOrThrow("mobile") : c.getColumnIndexOrThrow("gsm")));

                        int typeId = c.getInt(type == SourceType.SOURCE_THREE || type == SourceType.SOURCE_FIVE ?
                                c.getColumnIndexOrThrow("ctgry_no") : c.getColumnIndexOrThrow("g_id"));

                        // الحصول على اسم نوع الحساب من الخريطة
                        accountTypeName = accountTypeNameMap.get(typeId);
                        if (accountTypeName == null) {
                            accountTypeName = "عام"; // قيمة افتراضية
                        }

                        // البحث عن firestoreId مع تطبيق نفس منطق إزالة "الـ"
                        accountTypeFirestoreId = findMatchingFirestoreId(accountTypeName, accountTypeNameToFirestoreIdMap);
                    }

                    // تعيين نوع الحساب و firestoreId
                    newAccount.setAccountType(accountTypeName);

                    if (accountTypeFirestoreId != null) {
                        newAccount.setAcTypeFirestoreId(accountTypeFirestoreId);
                    } else {
                        Log.w(TAG, "Could not find firestoreId for account type: " + accountTypeName);
                        // البحث عن firestoreId للنوع الافتراضي "عام" مع تطبيق منطق إزالة "الـ"
                        String defaultFirestoreId = findMatchingFirestoreId("عام", accountTypeNameToFirestoreIdMap);
                        if (defaultFirestoreId != null) {
                            newAccount.setAcTypeFirestoreId(defaultFirestoreId);
                        } else {
                            // إذا لم يتم العثور على "عام"، نستخدم أول firestoreId متاح
                            if (!accountTypeNameToFirestoreIdMap.isEmpty()) {
                                String firstFirestoreId = accountTypeNameToFirestoreIdMap.values().iterator().next();
                                newAccount.setAcTypeFirestoreId(firstFirestoreId);
                            }
                        }
                    }

                    if(isGuest) {
                        currentUserId = guestUID;
                    }

                    newAccount.setFirestoreId(UUIDGenerator.generateSequentialUUID());
                    newAccount.setSyncStatus("NEW");
                    newAccount.setOwnerUID(currentUserId);
                    newAccount.setLastModified(System.currentTimeMillis());

                    long newId = appDb.accountDao().insert(newAccount);
                    accountIdMap.put(oldId, (int) newId);
                    accountsImported[0]++;

                    Log.d(TAG, "Imported account: " + name + " with type: " + accountTypeName + ", firestoreId: " + accountTypeFirestoreId);
                } else {
                    accountIdMap.put(oldId, existingAccount.getId());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error importing accounts", e);
        }

        return accountIdMap;
    }

    /**
     * دالة مساعدة للبحث عن firestoreId مع تطبيق نفس منطق إزالة "الـ" التعريف
     */
    private String findMatchingFirestoreId(String accountTypeName, Map<String, String> accountTypeNameToFirestoreIdMap) {
        if (accountTypeName == null) {
            return null;
        }

        // البحث المباشر أولاً
        if (accountTypeNameToFirestoreIdMap.containsKey(accountTypeName)) {
            return accountTypeNameToFirestoreIdMap.get(accountTypeName);
        }

        // إذا كان الاسم يبدأ بـ "ال"، نبحث بدونها
        if (accountTypeName.startsWith("ال")) {
            String nameWithoutAl = accountTypeName.substring(2);
            if (accountTypeNameToFirestoreIdMap.containsKey(nameWithoutAl)) {
                return accountTypeNameToFirestoreIdMap.get(nameWithoutAl);
            }

            // البحث بدون "ال" وحروف مشابهة
            String[] possibleVariations = {
                    nameWithoutAl,
                    nameWithoutAl.replace("أ", "ا").replace("إ", "ا"),
                    accountTypeName.replace("أ", "ا").replace("إ", "ا")
            };

            for (String variation : possibleVariations) {
                for (Map.Entry<String, String> entry : accountTypeNameToFirestoreIdMap.entrySet()) {
                    String key = entry.getKey();
                    String keyNormalized = key.replace("أ", "ا").replace("إ", "ا");

                    if (keyNormalized.equalsIgnoreCase(variation) ||
                            key.equalsIgnoreCase(variation)) {
                        return entry.getValue();
                    }
                }
            }
        } else {
            // إذا كان الاسم لا يبدأ بـ "ال"، نبحث مع إضافتها
            String nameWithAl = "ال" + accountTypeName;
            if (accountTypeNameToFirestoreIdMap.containsKey(nameWithAl)) {
                return accountTypeNameToFirestoreIdMap.get(nameWithAl);
            }
        }

        // البحث بأي طريقة مطابقة جزئية
        for (Map.Entry<String, String> entry : accountTypeNameToFirestoreIdMap.entrySet()) {
            String key = entry.getKey();
            String keyNormalized = key.replace("أ", "ا").replace("إ", "ا").replace("ال", "");
            String accountTypeNormalized = accountTypeName.replace("أ", "ا").replace("إ", "ا").replace("ال", "");

            if (keyNormalized.equalsIgnoreCase(accountTypeNormalized)) {
                return entry.getValue();
            }
        }

        return null;
    }

    private void importTransactions(SQLiteDatabase oldDb, SourceType type, Map<Integer, Integer> accountIdMap, Map<Integer, String> currencyMap, int[] transactionsImported, Map<String, Integer> newCurrencyMap) {
        String query;
        switch (type) {
            case SOURCE_FIVE:
                // **تم التعديل: إضافة ID العملية الأصلية**
                query = "SELECT _id AS trans_id, account_id AS account_no, money AS amount,date AS trans_date,note AS details,currency_id AS c_id FROM `transactions` ORDER BY trans_id";
                break;
            case SOURCE_FOUR:
                query = "SELECT * FROM `transactions` ORDER BY id ASC";
                break;
            case SOURCE_ONE:
                // **تم التعديل: إضافة ID العملية الأصلية**
                query = "SELECT ID, cus_id, `in`, `out`, date_, remarks, param2, curr_id FROM `transactions`";
                break;
            case SOURCE_TWO:
                // **تم التعديل: إضافة ID العملية الأصلية**
                query = "SELECT ID, cus_id, `in`, `out`, date_, remarks FROM `transactions`";
                break;
            case SOURCE_THREE:
                // **تم التعديل: إضافة trans_id العملية الأصلية**
                query = "SELECT trans_id, account_no, amount, trans_date, details, c_id FROM `details_tbl`";
                break;
            default:
                return;
        }

        try (Cursor c = oldDb.rawQuery(query, null)) {
            while (c.moveToNext()) {
                // **تم التعديل: جلب رقم العملية الأصلي**
                int oldTransactionId = c.getInt(0); // ID دائمًا هو العمود الأول في استعلاماتنا

                Log.d(TAG, "oldTransactionId: " + oldTransactionId);
                int oldAccountId = 0;
                Integer newAccountId = null;
                if (type == SourceType.SOURCE_FOUR) {
                    oldAccountId = c.getInt(c.getColumnIndexOrThrow("accountId"));
                    Log.d(TAG, "oldAccountId: " + oldAccountId);
                    newAccountId = accountIdMap.get(oldAccountId);
                } else {
                    oldAccountId = c.getInt( (type ==SourceType.SOURCE_THREE  || type ==SourceType.SOURCE_FIVE)
                            ? c.getColumnIndexOrThrow("account_no")
                            : c.getColumnIndexOrThrow("cus_id"));
                    newAccountId = accountIdMap.get(oldAccountId);
                }

                Log.d(TAG, "newAccountId: " + newAccountId);
                if (newAccountId == null) continue;

                double amount;
                int txType;
                int txImportID = 0;
                String txBillType = null;
                Date timestamp;
                String details;
                String currencyFirestoreId;
                String currency = null;
                Integer newCurrencyId = null;
                Integer finalCurrencyId = null;
                if (type == SourceType.SOURCE_FOUR) {
                    amount = c.getDouble(c.getColumnIndexOrThrow("amount"));
                    txType = amount >= 0 ? 1 : -1;
                    amount = Math.abs(amount);
                    txImportID = c.getInt(c.getColumnIndexOrThrow("importID"));
                    txBillType = c.getString(c.getColumnIndexOrThrow("billType"));
                    details = c.getString(c.getColumnIndexOrThrow("details"));
                    timestamp = new Date(c.getLong(c.getColumnIndexOrThrow("timestamp")));

                    boolean hasCurrencyColumn = columnExists(oldDb, "transactions", "currency");
                    if (hasCurrencyColumn) {
                        currency = mapCurrencyFromCustomersTable(oldDb, oldAccountId, currencyMap);
                        if (currency.equals("عام")) {
                            currency = "محلي";
                        }
                        finalCurrencyId = newCurrencyMap.get(currency);
                    } else {
                        finalCurrencyId = c.getInt(c.getColumnIndexOrThrow("currencyId"));
                    }

                    Log.d(TAG, "details: " + (details));
                }
                else if (type == SourceType.SOURCE_THREE || type == SourceType.SOURCE_FIVE) {
                    amount = c.getDouble(c.getColumnIndexOrThrow("amount"));
                    txType = amount >= 0 ? 1 : -1;
                    amount = Math.abs(amount);
//                    timestamp = parseDateTime(c.getString(c.getColumnIndexOrThrow("trans_date")), null);
                    timestamp = convertToDate(c.getString(c.getColumnIndexOrThrow("trans_date")));
                    Log.e("date", "date " + c.getString(c.getColumnIndexOrThrow("trans_date")) + "\n" +
                            " timestamp " + timestamp);
                    details = c.getString(c.getColumnIndexOrThrow("details"));
                    int currencyId = c.getInt(c.getColumnIndexOrThrow("c_id"));
                    currency = currencyMap.getOrDefault(currencyId, MyApplication.defaultCurrencyName);
                    finalCurrencyId = newCurrencyMap.get(currency);
                } else { // SOURCE_ONE or SOURCE_TWO
                    amount = c.getDouble(c.getColumnIndexOrThrow("out"));
                    txType = c.getString(c.getColumnIndexOrThrow("in")).equals("1") ? 1 : -1;
                    details = c.getString(c.getColumnIndexOrThrow("remarks"));
                    // **منطق منفصل لكل نوع**

                    if (type == SourceType.SOURCE_ONE) {
                        String timeStr = c.getString(c.getColumnIndexOrThrow("param2"));
                        timestamp = parseDateTime(c.getString(c.getColumnIndexOrThrow("date_")), timeStr);
                        int currencyId = c.getInt(c.getColumnIndexOrThrow("curr_id"));
                        currency = currencyMap.getOrDefault(currencyId, MyApplication.defaultCurrencyName);
                        finalCurrencyId = newCurrencyMap.get(currency);
                    } else { // SOURCE_TWO
                        timestamp = parseDateTime(c.getString(c.getColumnIndexOrThrow("date_")), null); // لا يوجد وقت
//                        currency = mapCurrencyFromCustomersTable(oldDb, oldAccountId); // جلب العملة من جدول الحسابات
                        currency = mapCurrencyFromCustomersTable(oldDb, oldAccountId, currencyMap);
                        if (currency.equals("عام")) {
                            currency = "محلي";
                        }
                        finalCurrencyId = newCurrencyMap.get(currency);
                    }
                }

                if (finalCurrencyId == null) {
                    Log.w(TAG, "Skipping transaction because currency name '" + currency + "' not found in new DB map.");
                    continue; // تخطي العملية إذا لم يتم العثور على العملة
                }
                currencyFirestoreId = appDb.currencyDao().getCurrencyFirestoreId(finalCurrencyId);
                if (!transactionExists(newAccountId, timestamp, amount, txType, details, oldTransactionId, txBillType)) {
                    insertNewTransaction(newAccountId, amount, txType, details, finalCurrencyId, timestamp, oldTransactionId, txBillType, currencyFirestoreId);
                    transactionsImported[0]++;
                }

            }
        }
    }

    // **تم التعديل: دالة الإضافة الآن تقبل importId**
    private void insertNewTransaction(int accountId, double amount, int type, String details, int currency,
                                      Date timestamp, int importId, String billType, String currencyFirestoreId) {

        if(isGuest) {
            currentUserId = guestUID;
        }
        Account getAccountFirestoreId = appDb.accountDao().getAccountByIdBlocking(accountId);
        Transaction newTransaction = new Transaction();
        newTransaction.setOwnerUID(currentUserId);
        newTransaction.setAccountId(accountId);
        newTransaction.setAmount(amount);
        newTransaction.setType(type);
        newTransaction.setDetails(details);
        newTransaction.setCurrencyFirestoreId(currencyFirestoreId);
        newTransaction.setCurrencyId(currency);
        newTransaction.setTimestamp(timestamp);
        newTransaction.setBillType(billType);
        newTransaction.setImportID(importId); // **<-- حفظ الرقم الأصلي هنا**
        newTransaction.setFirestoreId(UUIDGenerator.generateSequentialUUID());
        newTransaction.setSyncStatus("NEW");
        newTransaction.setAccountFirestoreId(getAccountFirestoreId.getFirestoreId());
        newTransaction.setLastModified(System.currentTimeMillis());
        appDb.transactionDao().insert(newTransaction);
    }


    /**
     * دالة مساعدة خاصة بالقاعدة الثانية، حيث يتم تخزين رقم العملة في جدول الحسابات.
     *
     * @param db           قاعدة البيانات القديمة
     * @param oldAccountId رقم الحساب في قاعدة البيانات القديمة
     * @param currencyMap  خريطة لترجمة رقم العملة إلى اسمها
     * @return اسم العملة
     */
    private String mapCurrencyFromCustomersTable(SQLiteDatabase db, int oldAccountId, Map<Integer, String> currencyMap) {
        // نستخدم try-with-resources لضمان إغلاق الـ Cursor تلقائيًا
        try (Cursor c = db.rawQuery("SELECT g_id FROM `customers` WHERE ID = " + oldAccountId, null)) {
            if (c.moveToFirst()) {
                // g_id هنا يمثل رقم العملة
                int currencyId = c.getInt(c.getColumnIndexOrThrow("g_id"));
                // نستخدم الخريطة لترجمة الرقم إلى اسم، مع قيمة افتراضية "محلي"
                return currencyMap.getOrDefault(currencyId, "محلي");
            }
        }
        // إذا لم يتم العثور على الحساب لسبب ما، نرجع القيمة الافتراضية
        return "محلي";
    }


    private boolean transactionExists(int accountId, Date timestamp, double amount, int type, String details, int id, String billType) {
        List<Transaction> existing = appDb.transactionDao().getImportTransactionsForAccountBlocking(accountId);
        for (Transaction tx : existing) {
            // مقارنة أكثر دقة لتجنب التكرار
            try {
                if (tx.getAmount() == amount && tx.getType() == type &&
                        tx.getTimestamp().getTime() == timestamp.getTime() && tx.getDetails().equals(details) && tx.getImportID() == id && tx.getBillType().equals(billType)) {
                    return true;
                }
            } catch (Exception e) {
                return true;
            }
        }
        return false;
    }

    private File copyUriToTempFile(Uri uri) throws Exception {
        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        File tempFile = new File(context.getCacheDir(), "temp_restore.db");
        try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
        return tempFile;
    }

    private Date parseDateTime(String dateStr, String timeStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return new Date();
        }
        String combinedDateTime = (timeStr != null && !timeStr.trim().isEmpty())
                ? dateStr.trim() + " " + timeStr.trim()
                : dateStr.trim();

        String[] formats = {"dd-MM-yyyy HH:mm", "yyyy-MM-dd HH:mm", "yyyy-MM-dd HH:mm:ss", "dd-MM-yyyy HH:mm:ss", "dd-MM-yyyy", "yyyy-MM-dd"};

        for (String format : formats) {
            try {
                return new SimpleDateFormat(format, Locale.US).parse(combinedDateTime);
            } catch (ParseException e) {
                // تجاهل الخطأ وتجربة التنسيق التالي
            }
        }
        Log.e(TAG, "Could not parse date: '" + combinedDateTime + "'. It did not match any supported format.");
        return new Date();
    }

    private Date convertToDate(String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) {
            return new Date();
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        try {
            return sdf.parse(dateString);
        } catch (ParseException e) {
            // أو يمكنك رمي استثناء حسب الحاجة
        }
        return new Date();
    }


    private boolean tableExists(SQLiteDatabase db, String tableName) {
        try (Cursor c = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", new String[]{tableName})) {
            return c.getCount() > 0;
        }
    }

    private boolean columnExists(SQLiteDatabase db, String tableName, String columnName) {
        try (Cursor c = db.query(tableName, null, null, null, null, null, null)) {
            return c.getColumnIndex(columnName) != -1;
        }
    }

    /**
     * توليد code و symbol للعملة مع التعرف على الأسماء المختلفة
     */
    private CurrencyCodeSymbol generateCurrencyCodeAndSymbol(String currencyName) {
        if (currencyName == null || currencyName.trim().isEmpty()) {
            return new CurrencyCodeSymbol("UNK", "?");
        }

        // أولاً: محاولة التعرف على العملة من خلال الخريطة
        CurrencyInfo recognizedCurrency = findMatchingCurrency(currencyName, createCurrencyMapping());
        if (recognizedCurrency != null) {
            return new CurrencyCodeSymbol(recognizedCurrency.code, recognizedCurrency.symbol);
        }

        // إذا لم يتم التعرف على العملة، نستخدم المنطق المبسط
        String cleanName = currencyName.trim();
        String code;
        String symbol;

        // قراءة اللغة من SharedPreferences
        Locale locale = context.getResources().getConfiguration().locale;
        String savedLanguage = locale.getLanguage();

        if (savedLanguage.equals("ar") && cleanName.length() >= 2) {
            // للعربية: أول حرفين مع نقطة بينهما
            symbol = cleanName.substring(0, 1) + "." + cleanName.substring(1, 2);
            code = cleanName.length() >= 3 ? cleanName.substring(0, 3).toUpperCase() : cleanName.toUpperCase();
        } else {
            // للإنجليزية: أول 3 أحرف كرمز وكود
            code = cleanName.length() >= 3 ? cleanName.substring(0, 3).toUpperCase() : cleanName.toUpperCase();
            symbol = code;
        }

        return new CurrencyCodeSymbol(code, symbol);
    }

    /**
     * خريطة تحتوي على جميع الأسماء المحتملة للعملات مع رموزها وأكوادها
     */
    private Map<String, CurrencyInfo> createCurrencyMapping() {
        Map<String, CurrencyInfo> map = new HashMap<>();
        String currencySymbol = "﷼";
        // العملات العربية
        addCurrencyVariants(map,currencySymbol, "ر.س",
                "ريال سعودي", "ريال السعودي", "سعودي", "ريال سعودي", "الريال السعودي", "sar", "riyal", "saudi");

        addCurrencyVariants(map,currencySymbol, "ر.ي",
                "ريال يمني", "ريال اليمن", "يمني", "ريال يمني", "الريال اليمني", "yer", "yemeni", "yemen", "yemen rial", "yemeni rial");

        addCurrencyVariants(map, "ج.م", "EGP",
                "جنيه مصري", "جنيه مصرى", "مصري", "egp", "egyptian", "egypt");

        addCurrencyVariants(map, "د.إ", "AED",
                "درهم إماراتي", "درهم اماراتي", "درهم", "aed", "uae", "emirati");

        addCurrencyVariants(map, "د.ك", "KWD",
                "دينار كويتي", "دينار", "kwd", "kuwaiti", "kuwait");

        addCurrencyVariants(map, "د.ع", "IQD",
                "دينار عراقي", "عراقي", "iqd", "iraqi", "iraq");

        addCurrencyVariants(map, "ل.س", "SYP",
                "ليرة سورية", "ليرة سوريا", "ليرة", "syp", "syrian", "syria");

        addCurrencyVariants(map, "د.أ", "JOD",
                "دينار أردني", "دينار اردني", "أردني", "jod", "jordanian", "jordan");

        addCurrencyVariants(map,currencySymbol, "ر.ق",
                "ريال قطري", "قطري", "qar", "qatari", "qatar");

        addCurrencyVariants(map, "د.ب", "BHD",
                "دينار بحريني", "بحريني", "bhd", "bahraini", "bahrain");

        addCurrencyVariants(map,currencySymbol, "ر.ع",
                "ريال عماني", "عماني", "omr", "omani", "oman");

        addCurrencyVariants(map, "ل.ل", "LBP",
                "ليرة لبنانية", "لبناني", "lbp", "lebanese", "lebanon");

        // العملات الدولية
        addCurrencyVariants(map, "$", "USD",
                "دولار أمريكي", "دولار امريكي", "دولار", "دولار أمريكان", "دولار امريكان",
                "usd", "dollar", "american", "united states");

        addCurrencyVariants(map, "€", "EUR",
                "يورو", "eur", "euro");

        addCurrencyVariants(map, "£", "GBP",
                "جنيه إسترليني", "جنيه استرليني", "جنيه", "gbp", "pound", "british");

        addCurrencyVariants(map, "¥", "JPY",
                "ين ياباني", "ين", "jpy", "yen", "japanese");

        addCurrencyVariants(map, "₹", "INR",
                "روبية هندية", "روبية", "inr", "rupee", "indian");

        addCurrencyVariants(map, "₺", "TRY",
                "ليرة تركية", "تركي", "try", "turkish", "turkey");

        addCurrencyVariants(map,currencySymbol, "SAR",
                "sar", "saudi riyal", "saudi rial", "riyal"); // إضافة اختصارات إنجليزية

        addCurrencyVariants(map,currencySymbol, "YER",
                "yer", "yemen riyal", "yemeni riyal", "yemeni rial"); // إضافة اختصارات إنجليزية

        return map;
    }

    /**
     * إضافة متغيرات الأسماء للعملة في الخريطة
     */
    private void addCurrencyVariants(Map<String, CurrencyInfo> map, String symbol, String code, String... names) {
        CurrencyInfo info = new CurrencyInfo(symbol, code);
        for (String name : names) {
            map.put(normalizeName(name), info);
        }
    }

    /**
     * تطبيع اسم العملة لإزالة الهمزات والتباينات
     */
    private String normalizeName(String name) {
        if (name == null) return "";

        // إزالة الهمزات والتشكيل
        String normalized = name
                .replaceAll("[إأآا]", "ا")
                .replaceAll("ى", "ي")
                .replaceAll("ة", "ه")
                .replaceAll("ئ", "ي")
                .replaceAll("ؤ", "و")
                .replaceAll("\\p{M}", "") // إزالة التشكيل
                .replaceAll("\\s+", " ") // إزالة المسافات الزائدة
                .trim()
                .toLowerCase();

        return normalized;
    }

    /**
     * البحث عن عملة مطابقة في الخريطة
     */
    private CurrencyInfo findMatchingCurrency(String name, Map<String, CurrencyInfo> currencyMap) {
        if (name == null) return null;

        // محاولة المطابقة المباشرة
        String normalized = normalizeName(name);
        CurrencyInfo exactMatch = currencyMap.get(normalized);
        if (exactMatch != null) {
            return exactMatch;
        }

        // مطابقة جزئية
        for (Map.Entry<String, CurrencyInfo> entry : currencyMap.entrySet()) {
            if (normalized.contains(entry.getKey()) || entry.getKey().contains(normalized)) {
                return entry.getValue();
            }
        }

        return null;
    }
    private Map<String, String> loadAndMapAccountTypes(SQLiteDatabase oldDb, SourceType type) {
        Map<String, String> nameToFirestoreIdMap = new HashMap<>();
        accountTypeNameMap.clear();
        if (type == SourceType.SOURCE_TWO) return nameToFirestoreIdMap;

        String query;
        if (type == SourceType.SOURCE_FOUR) {
            query = "SELECT id, name FROM `account_types` ORDER BY id ASC";
        } else if (type == SourceType.SOURCE_FIVE) {
            query = "SELECT _id AS id, title AS name FROM `category` ORDER By _id ASC";
        } else if (type == SourceType.SOURCE_THREE) {
            query = "SELECT ctgry_no, ctgry_name FROM `gategory_tbl` ORDER By ctgry_no ASC";
        } else { // SOURCE_ONE
            query = "SELECT ID, name FROM `groups` ORDER By ID ASC";
        }

        List<String> existingTypeNames = appDb.accountTypeDao().getAllAccountTypeNames();
        Set<String> existingNamesSet = new HashSet<>(existingTypeNames);

        try (Cursor c = oldDb.rawQuery(query, null)) {
            while (c.moveToNext()) {
                int id = c.getInt(0);
                String originalName = c.getString(1).replaceAll("\\s+", " ").trim();

                // إزالة السطر التالي لأنه يسبب المشكلة:
                // if (type == SourceType.SOURCE_FOUR) {
                //     originalName = c.getString(c.getColumnIndexOrThrow("name"));
                // }

                String nameToCheck = originalName;
                if (originalName.startsWith("ال")) {
                    nameToCheck = originalName.substring(2);
                }

                AccountType existingType = null;
                String matchedExistingName = null;
                for (String existingName : existingNamesSet) {
                    if (existingName.equalsIgnoreCase(nameToCheck) || existingName.equalsIgnoreCase(originalName)) {
                        matchedExistingName = existingName;
                        break;
                    }
                }
                existingType = appDb.accountTypeDao().getAccountTypeByName(matchedExistingName);

                if (existingType != null) {
                    // إذا كان موجودًا، استخدم الـ firestoreId الحالي الخاص به
                    nameToFirestoreIdMap.put(originalName, existingType.getFirestoreId());
                    accountTypeNameMap.put(id, matchedExistingName);
                } else {
                    if(isGuest) {
                        Log.d(TAG, "guestUID: " + guestUID);
                        currentUserId = guestUID;
                    }
                    // لم يتم العثور على تطابق، أضف التصنيف الجديد باسمه الكامل
                    Log.d(TAG, "New account type found, adding: " + originalName);
                    AccountType newType = new AccountType(originalName.replaceAll("\\s+", " ").trim());
                    String newFirestoreId = UUIDGenerator.generateSequentialUUID();
                    newType.setOwnerUID(currentUserId);
                    newType.setFirestoreId(newFirestoreId);
                    newType.setSyncStatus("NEW");
                    newType.setLastModified(System.currentTimeMillis());
                    newType.setDefault(false);
                    appDb.accountTypeDao().insert(newType);
                    existingNamesSet.add(originalName);
                    nameToFirestoreIdMap.put(originalName, newFirestoreId);
                    accountTypeNameMap.put(id, originalName); // هذا هو الإصلاح الرئيسي
                }
            }
        }
        return nameToFirestoreIdMap;
    }
    private Map<Integer, Integer> importAndMapAccounts(SQLiteDatabase oldDb,
                                                       SourceType type,
                                                       Map<String, String> accountTypeNameToFirestoreIdMap,
                                                       int[] accountsImported,
                                                       Map<Integer, String> accountTypeNameMap) {
        Map<Integer, Integer> accountIdMap = new HashMap<>();
        String query;

        if (type == SourceType.SOURCE_FOUR) {
            query = "SELECT * FROM accounts ORDER BY id ASC";
        } else if (type == SourceType.SOURCE_FIVE) {
            query = "SELECT _id AS account_no, name AS account_name, phone AS mobile, category_id AS ctgry_no FROM accounts ORDER BY account_no ASC";
        } else if (type == SourceType.SOURCE_THREE) {
            query = "SELECT account_no, account_name, mobile, ctgry_no FROM accounts_tbl ORDER BY account_no ASC";
        } else {
            query = "SELECT ID, name, gsm, g_id FROM customers ORDER BY ID ASC";
        }

        try (Cursor c = oldDb.rawQuery(query, null)) {
            while (c.moveToNext()) {
                String name = null;
                int oldId = 0;

                if (type == SourceType.SOURCE_FOUR) {
                    name = c.getString(c.getColumnIndexOrThrow("accountName"));
                    oldId = c.getInt(c.getColumnIndexOrThrow("id"));
                } else {
                    name = c.getString(type == SourceType.SOURCE_THREE || type == SourceType.SOURCE_FIVE ?
                                    c.getColumnIndexOrThrow("account_name") : c.getColumnIndexOrThrow("name"))
                            .replaceAll("\\s+", " ").trim();
                    oldId = c.getInt(type == SourceType.SOURCE_THREE || type == SourceType.SOURCE_FIVE ?
                            c.getColumnIndexOrThrow("account_no") : c.getColumnIndexOrThrow("ID"));
                }

                Account existingAccount = appDb.accountDao().getAccountByNameBlocking(name);
                if (existingAccount == null) {
                    Account newAccount = new Account();
                    newAccount.setOwnerUID(currentUserId);
                    newAccount.setAccountName(name);

                    String accountTypeName = null;
                    String accountTypeFirestoreId = null;

                    if (type == SourceType.SOURCE_FOUR) {
                        // SOURCE_FOUR: استخدام accountType مباشرة من الجدول
                        accountTypeName = c.getString(c.getColumnIndexOrThrow("accountType"));
                        newAccount.setPhoneNumber(c.getString(c.getColumnIndexOrThrow("phoneNumber")));

                        // البحث المباشر في الخريطة
                        accountTypeFirestoreId = accountTypeNameToFirestoreIdMap.get(accountTypeName);

                    } else {
                        // المصادر الأخرى: استخدام رقم نوع الحساب
                        newAccount.setPhoneNumber(c.getString(type == SourceType.SOURCE_THREE || type == SourceType.SOURCE_FIVE ?
                                c.getColumnIndexOrThrow("mobile") : c.getColumnIndexOrThrow("gsm")));

                        int typeId = c.getInt(type == SourceType.SOURCE_THREE || type == SourceType.SOURCE_FIVE ?
                                c.getColumnIndexOrThrow("ctgry_no") : c.getColumnIndexOrThrow("g_id"));

                        // الحصول على اسم نوع الحساب من الخريطة
                        accountTypeName = accountTypeNameMap.get(typeId);
                        if (accountTypeName == null) {
                            accountTypeName = "عام";
                        }

                        // البحث في الخريطة باستخدام اسم نوع الحساب
                        accountTypeFirestoreId = accountTypeNameToFirestoreIdMap.get(accountTypeName);
                    }

                    // إذا لم يتم العثور على firestoreId، استخدم النوع الافتراضي
                    if (accountTypeFirestoreId == null) {
                        AccountType defaultType = appDb.accountTypeDao().getAccountTypeByName("عام");
                        if (defaultType != null) {
                            accountTypeFirestoreId = defaultType.getFirestoreId();
                            accountTypeName = "عام";
                        }
                    }

                    // التعيين النهائي
                    newAccount.setAccountType(accountTypeName);
                    newAccount.setAcTypeFirestoreId(accountTypeFirestoreId);

                    if(isGuest) {
                        currentUserId = guestUID;
                    }

                    newAccount.setFirestoreId(UUIDGenerator.generateSequentialUUID());
                    newAccount.setSyncStatus("NEW");
                    newAccount.setOwnerUID(currentUserId);
                    newAccount.setLastModified(System.currentTimeMillis());

                    long newId = appDb.accountDao().insert(newAccount);
                    accountIdMap.put(oldId, (int) newId);
                    accountsImported[0]++;
                } else {
                    accountIdMap.put(oldId, existingAccount.getId());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error importing accounts", e);
        }

        return accountIdMap;
    }
    private String getDefaultAccountTypeFirestoreId() {
        // البحث عن نوع الحساب الافتراضي "عام"
        AccountType defaultType = appDb.accountTypeDao().getAccountTypeByName("عام");
        if (defaultType != null) {
            return defaultType.getFirestoreId();
        }

        // إذا لم يوجد، نرجع أول نوع متاح
        List<AccountType> allTypes = appDb.accountTypeDao().getAllAccountTypesBlockingRestore();
        if (!allTypes.isEmpty()) {
            return allTypes.get(0).getFirestoreId();
        }

        return null;
    }
    /**
     * كلاس مساعد لحفظ معلومات العملة
     */
    private static class CurrencyInfo {
        String symbol;
        String code;

        CurrencyInfo(String symbol, String code) {
            this.symbol = symbol;
            this.code = code;
        }
    }

    /**
     * كلاس مساعد لحفظ code و symbol
     */
    private static class CurrencyCodeSymbol {
        String code;
        String symbol;

        CurrencyCodeSymbol(String code, String symbol) {
            this.code = code;
            this.symbol = symbol;
        }
    }
}