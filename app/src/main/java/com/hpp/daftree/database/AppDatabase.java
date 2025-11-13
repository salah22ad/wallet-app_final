
package com.hpp.daftree.database;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.google.firebase.auth.FirebaseAuth;
import com.hpp.daftree.R;
import com.hpp.daftree.UUIDGenerator;
import com.hpp.daftree.helpers.LanguageHelper;
import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.syncmanagers.SyncCoordinator;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(
        entities = {
                Account.class,
                Transaction.class,
                DeletionLog.class,
                User.class,
                Currency.class,
                AccountType.class,
                MigrationFlag.class}
                , version = 7, exportSchema = false)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {
    // DAOs
    public abstract DeletionLogDao deletionLogDao();

    public abstract AccountDao accountDao();

    public abstract TransactionDao transactionDao();

    public abstract UserDao userDao();

    public abstract CurrencyDao currencyDao() ;

    private static final Currency newCurrency = new Currency();
    private static final AccountType newAccountType = new AccountType();
    private static DaftreeRepository repository;
    public abstract AccountTypeDao accountTypeDao();
    public abstract MigrationFlagDao migrationFlagDao();
    private static volatile AppDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;
   public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);
    private static final String DATABASE_NAME = "daftree_database";

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, DATABASE_NAME)
                            .addMigrations(MIGRATION_1_2,
                                    createMigration2To3(context),
                                    MIGRATION_3_4,MIGRATION_4_5,
                                    MIGRATION_5_6,MIGRATION_6_7,MIGRATION_7_8)
                            .addCallback(new Callback() {
                                @Override
                                public void onCreate(@NonNull SupportSQLiteDatabase db) {
                                    super.onCreate(db);
                                    db.execSQL("PRAGMA foreign_keys = ON;");
                                    Executors.newSingleThreadExecutor().execute(() -> {
                                        AppDatabase database = AppDatabase.getDatabase(context);
                                        database.currencyDao().updateAllSyncStatus("NEW", true);
                                        Integer pending = database.migrationFlagDao().getFlagValue("migration_6_7_pending");
                                        if (pending != null && pending == 1) {
                                            Log.i("triggerSync","START ِAfter MIGARITION");
                                            SyncCoordinator.triggerSyncWithCallback(context, () -> {
                                                Executors.newSingleThreadExecutor().execute(() -> {
                                                    database.migrationFlagDao().setFlag("migration_6_7_pending", 0);
                                                });
                                            });
                                        }
                                    });

                                }
                            })
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `user_profile` (" +
                    "`id` INTEGER NOT NULL, `name` TEXT, `email` TEXT, `address` TEXT, `phone` TEXT, " +
                    "`password` TEXT, `syncStatus` TEXT, `lastModified` INTEGER NOT NULL, `createdAt` INTEGER, " +
                    "PRIMARY KEY(`id`))");
        }
    };
    static Migration createMigration2To3(final Context context) {
        return new Migration(2, 3) {
            @Override
            public void migrate(@NonNull SupportSQLiteDatabase database) {
                database.execSQL("ALTER TABLE user_profile ADD COLUMN company TEXT");
                database.execSQL("ALTER TABLE user_profile ADD COLUMN profileImageUri TEXT");
                database.execSQL("CREATE TABLE IF NOT EXISTS `currencies` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, `firestoreId` TEXT, " +
                        "`ownerUID` TEXT, `lastModified` INTEGER NOT NULL, `syncStatus` TEXT)");
                database.execSQL("CREATE TABLE IF NOT EXISTS `account_types` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, `firestoreId` TEXT, " +
                        "`ownerUID` TEXT, `lastModified` INTEGER NOT NULL, `syncStatus` TEXT)");

                String uid = FirebaseAuth.getInstance().getUid() != null ? FirebaseAuth.getInstance().getUid() : "";

                addDefaultCurrencies(database, uid, context);
                addDefaultAccountTypes(database, uid, context);
            }

            private void addDefaultCurrencies(SupportSQLiteDatabase db, String uid, Context ctx) {
                Resources localizedResources = LanguageHelper.getLocalizedResources(ctx);

                String[] currencies = {
                        localizedResources.getString(R.string.currency_local),
                        localizedResources.getString(R.string.currency_saudi),
                        localizedResources.getString(R.string.currency_dollar)
                };

                for (String currency : currencies) {
                    String firestoreId = UUIDGenerator.generateSequentialUUID();
                    long lastModified = System.currentTimeMillis();
                    String sql = "INSERT INTO currencies (name, firestoreId, ownerUID, lastModified, syncStatus) VALUES (" +
                            DatabaseUtils.sqlEscapeString(currency) + ", " +
                            DatabaseUtils.sqlEscapeString(firestoreId) + ", " +
                            DatabaseUtils.sqlEscapeString(uid) + ", " +
                            lastModified + ", " +
                            DatabaseUtils.sqlEscapeString("SYNCED") + ")";
                    db.execSQL(sql);
                }
            }

            private void addDefaultAccountTypes(SupportSQLiteDatabase db, String uid, Context ctx) {
                Resources localizedResources = LanguageHelper.getLocalizedResources(ctx);

                String[] accountTypes = {
                        localizedResources.getString(R.string.account_type_customer),
                        localizedResources.getString(R.string.account_type_supplier),
                        localizedResources.getString(R.string.account_type_general)
                };

                for (String type : accountTypes) {
                    String firestoreId = UUIDGenerator.generateSequentialUUID();
                    long lastModified = System.currentTimeMillis();
                    String sql = "INSERT INTO account_types (name, firestoreId, ownerUID, lastModified, syncStatus) VALUES (" +
                            DatabaseUtils.sqlEscapeString(type) + ", " +
                            DatabaseUtils.sqlEscapeString(firestoreId) + ", " +
                            DatabaseUtils.sqlEscapeString(uid) + ", " +
                            lastModified + ", " +
                            DatabaseUtils.sqlEscapeString("SYNCED") + ")";
                    db.execSQL(sql);
                }
            }
        };
    }
    private static void insertCurrency(AppDatabase db, String name, String uid, long lastmodifid) {
        Currency currency = new Currency();
        currency.name = name;
        currency.setOwnerUID(uid);
        currency.setFirestoreId(UUIDGenerator.generateSequentialUUID());
        currency.setSyncStatus("SYNCED");
        currency.setLastModified(lastmodifid);
        db.currencyDao().insert(currency);
    }

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // إضافة الحقل الجديد مع قيمة افتراضية 0
            database.execSQL("ALTER TABLE transactions ADD COLUMN importID INTEGER NOT NULL DEFAULT 0");
        }
    };
    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // إضافة الحقل الجديد مع قيمة افتراضية 0
            database.execSQL("ALTER TABLE user_profile ADD COLUMN ownerUID TEXT ");
        }
    };
    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // إضافة الحقل الجديد
            database.execSQL("ALTER TABLE transactions ADD COLUMN billType TEXT");
        }
    };
    private static void insertAccountType(AppDatabase db, String name, String uid) {
        AccountType accountType = new AccountType();
//        accountType.id = id;
        accountType.name = name;
        accountType.setOwnerUID(uid);
        accountType.setFirestoreId(UUIDGenerator.generateSequentialUUID());
        accountType.setSyncStatus("SYNCED");
        accountType.setLastModified(System.currentTimeMillis());
        db.accountTypeDao().insert(accountType);
    }
    public static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            Log.e("MIGRATION", "MIGRATION_6_7 Start");
            db.execSQL(
                    "INSERT INTO currencies (name, firestoreId, ownerUID, lastModified, syncStatus) " +
                            "SELECT 'محلي', '', '', strftime('%s','now')*1000, 'SYNCED' " +
                            "WHERE NOT EXISTS (SELECT 1 FROM currencies WHERE name='محلي')"

            );
            // ----- 1) انشاء جدول transactions_new مطابقًا لتعريف الـ Entity الحالي -----
            db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `transactions_new` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`accountId` INTEGER NOT NULL, " +
                            "`type` INTEGER NOT NULL, " +
                            "`amount` REAL NOT NULL, " +
                            "`details` TEXT, " +
                            "`currencyId` INTEGER NOT NULL, " +
                            "`timestamp` INTEGER, " +
                            "`firestoreId` TEXT, " +
                            "`syncStatus` TEXT, " +
                            "`lastModified` INTEGER NOT NULL, " +
                            "`importID` INTEGER NOT NULL, " +
                            "`billType` TEXT, " +
                            "`currencyFirestoreId` TEXT NOT NULL, " +
                            "`accountFirestoreId` TEXT NOT NULL, " +
                            "`ownerUID` TEXT NOT NULL, " +
                            "FOREIGN KEY(`currencyId`) REFERENCES `currencies`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT" +
                            ")"
            );

            // ----- 2) نسخ البيانات من الجدول القديم مع ملأ accountFirestoreId و ownerUID من جدول accounts -----
            // استخدم LEFT JOIN مع accounts لإحضار firestoreId و ownerUID
            db.execSQL(
                    "INSERT INTO `transactions_new` (" +
                            "`accountId`,`type`,`amount`,`details`,`currencyId`,`timestamp`," +
                            "`firestoreId`,`syncStatus`,`lastModified`,`importID`,`billType`,`currencyFirestoreId`,`accountFirestoreId`,`ownerUID`" +
                            ") " +
                            "SELECT " +
                            "T.`accountId`, IFNULL(T.`type`,0), IFNULL(T.`amount`,0.0), T.`details`, " +
                            // انضم للعملات المحلية بالاسم إن وُجد، وإلا استخدم id العملة الاحتياطية 'محلي'
                            "IFNULL(C.`id`, (SELECT id FROM currencies WHERE name='محلي' LIMIT 1)), " +
                            "T.`timestamp`, T.`firestoreId`, 'EDITED' as syncStatus, IFNULL(T.`lastModified`,0), " +
                            "IFNULL(T.`importID`,0), T.`billType`, " +
                            "IFNULL(C.`firestoreId`, (SELECT firestoreId FROM currencies WHERE name='محلي' LIMIT 1)), " +
                            "COALESCE(A.`firestoreId`, ''), COALESCE(A.`ownerUID`, '') " +
                            "FROM `transactions` AS T " +
                            "LEFT JOIN `currencies` AS C ON C.`name` = T.`currency` " +
                            "LEFT JOIN `accounts` AS A ON A.`id` = T.`accountId` ORDER BY T.`id`"
            );

            // ----- 3) استبدال الجدول القديم بالجديد -----
            db.execSQL("DROP TABLE IF EXISTS `transactions`");
            db.execSQL("ALTER TABLE `transactions_new` RENAME TO `transactions`");

            // ----- 4) إنشاء الفهارس المطلوبة -----
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_accountId` ON `transactions` (`accountId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_currencyId` ON `transactions` (`currencyId`)");

            db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `accounts_new` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`firestoreId` TEXT NOT NULL, " +
                            "`syncStatus` TEXT, " +
                            "`lastModified` INTEGER NOT NULL, " +
                            "`ownerUID` TEXT NOT NULL, " +
                            "`accountName` TEXT, " +
                            "`phoneNumber` TEXT, " +
                            "`accountType` TEXT, " +
                            "`acTypeFirestoreId` TEXT NOT NULL" + // تم تغيير ترتيبه ليتوافق مع الكلاس
                            ")"
            );
            db.execSQL(
                    "INSERT INTO `accounts_new` (id, firestoreId, syncStatus, lastModified, ownerUID, accountName, phoneNumber, accountType, acTypeFirestoreId) " +
                            "SELECT acc.id, COALESCE(acc.firestoreId, ''), 'EDITED', IFNULL(acc.lastModified, 0), COALESCE(acc.ownerUID, ''), " +
                            "acc.accountName, acc.phoneNumber, acc.accountType, " +
                            // جملة COALESCE هنا تضمن عدم إدخال قيمة NULL أبداً
                            "COALESCE((SELECT AcT.firestoreId FROM account_types AS AcT WHERE AcT.name = acc.accountType), '') " +
                            "FROM `accounts` AS acc"
            );
            db.execSQL("DROP TABLE `accounts`");
            db.execSQL("ALTER TABLE `accounts_new` RENAME TO `accounts`");



            // ----- 5) أنشئ جدول بسيط لتخزين علم أن الترقية 6->7 تمت وتنتظر مزامنة أولية في الخلفية -----
//            db.execSQL("CREATE TABLE IF NOT EXISTS `migration_flags` (key TEXT PRIMARY KEY NOT NULL, value INTEGER NOT NULL)");
            db.execSQL("CREATE TABLE IF NOT EXISTS `migration_flags` (" +
                    "`flag_key` TEXT PRIMARY KEY NOT NULL, " +
                    "`value` INTEGER NOT NULL)");
            // ضع العلم = 1 إذا لم يكن موجودًا
//            db.execSQL(
//                    "INSERT OR REPLACE INTO migration_flags (key, value) VALUES ('migration_6_7_pending', 1)"
//            );
            db.execSQL("INSERT OR REPLACE INTO migration_flags (flag_key, value) VALUES ('migration_6_7_pending', 1)");
            // ملاحظة: لا ننفذ أي شبكة هنا (لا استدعاء فايرستور من الميجريشن) — المزامنة ستتم لاحقًا بواسطة SyncCoordinator بعد فحص هذا العلم.
            db.execSQL("ALTER TABLE currencies ADD COLUMN isDefault INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE account_types ADD COLUMN isDefault INTEGER NOT NULL DEFAULT 0");
            db.execSQL("UPDATE account_types SET isDefault = 1 , syncStatus = 'NEW'");
            db.execSQL("UPDATE currencies SET isDefault = 1, syncStatus = 'NEW'");

//            db.execSQL("ALTER TABLE user_profile ADD COLUMN max_devices INTEGER NOT NULL DEFAULT 2");
//            db.execSQL("ALTER TABLE user_profile ADD COLUMN transactions_count INTEGER NOT NULL DEFAULT 0");
//            db.execSQL("ALTER TABLE user_profile ADD COLUMN ad_rewards INTEGER NOT NULL DEFAULT 0");
//            db.execSQL("ALTER TABLE user_profile ADD COLUMN referral_rewards INTEGER NOT NULL DEFAULT 0");
//            db.execSQL("ALTER TABLE user_profile ADD COLUMN max_transactions INTEGER NOT NULL DEFAULT 100");
//            db.execSQL("ALTER TABLE user_profile ADD COLUMN is_premium INTEGER NOT NULL DEFAULT 0");
        }
    };

    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            Log.e("MIGRATION", "MIGRATION_7_8 Start - Adding currency codes and symbols");

            // إضافة الحقول الجديدة
            database.execSQL("ALTER TABLE currencies ADD COLUMN code TEXT");
            database.execSQL("ALTER TABLE currencies ADD COLUMN symbol TEXT");

            // تحديث العملات الموجودة بناءً على أسمائها
            updateExistingCurrencies(database);
        }

        private void updateExistingCurrencies(SupportSQLiteDatabase db) {
            // خريطة تحتوي على جميع الأسماء المحتملة للعملات مع رموزها وأكوادها
            Map<String, CurrencyInfo> currencyMap = createCurrencyMapping();

            // جلب جميع العملات الحالية وتحديثها
            Cursor cursor = db.query("SELECT id, name FROM currencies");
            try {
                while (cursor.moveToNext()) {
                    int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                    String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));

                    CurrencyInfo currencyInfo = findMatchingCurrency(name, currencyMap);
                    if (currencyInfo != null) {
                        updateCurrency(db, id, currencyInfo);
                    } else {
                        // إذا لم نجد تطابق، نستخدم الاسم كرمز وكرمز افتراضي
                        updateCurrencyWithDefault(db, id, name);
                    }
                }
            } finally {
                cursor.close();
            }
        }

        private Map<String, CurrencyInfo> createCurrencyMapping() {
            Map<String, CurrencyInfo> map = new HashMap<>();
            String currencySymbol = "﷼";
            // العملات العربية
            addCurrencyVariants(map, currencySymbol, "ر.س",
                    "ريال سعودي", "ريال السعودي", "سعودي", "ريال سعودي", "الريال السعودي", "sar", "riyal", "saudi");

            addCurrencyVariants(map, currencySymbol, "ر.ي",
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

            addCurrencyVariants(map, currencySymbol, "ر.ق",
                    "ريال قطري", "قطري", "qar", "qatari", "qatar");

            addCurrencyVariants(map, "د.ب", "BHD",
                    "دينار بحريني", "بحريني", "bhd", "bahraini", "bahrain");

            addCurrencyVariants(map, currencySymbol, "ر.ع",
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

            addCurrencyVariants(map, currencySymbol, "SAR",
                    "sar", "saudi riyal", "saudi rial", "riyal"); // إضافة اختصارات إنجليزية

            addCurrencyVariants(map, currencySymbol, "YER",
                    "yer", "yemen riyal", "yemeni riyal", "yemeni rial"); // إضافة اختصارات إنجليزية

            return map;
        }

        private void addCurrencyVariants(Map<String, CurrencyInfo> map, String symbol, String code, String... names) {
            CurrencyInfo info = new CurrencyInfo(symbol, code);
            for (String name : names) {
                map.put(normalizeName(name), info);
            }
        }

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

        private void updateCurrency(SupportSQLiteDatabase db, int id, CurrencyInfo currencyInfo) {
            String sql = "UPDATE currencies SET code = " + DatabaseUtils.sqlEscapeString(currencyInfo.code) +
                    ", symbol = " + DatabaseUtils.sqlEscapeString(currencyInfo.symbol) +
                    ", syncStatus = 'EDITED'" +
                    " WHERE id = " + id;
            db.execSQL(sql);
            Log.d("MIGRATION", "Updated currency ID " + id + " to " + currencyInfo.code + " (" + currencyInfo.symbol + ")");
        }

        private void updateCurrencyWithDefault(SupportSQLiteDatabase db, int id, String name) {
            // استخدام أول حرفين من الاسم كرمز افتراضي
            String defaultSymbol = name.length() >= 2 ? name.substring(0, 2) + "." : name + ".";
            String defaultCode = name.length() >= 3 ? name.substring(0, 3).toUpperCase() : name.toUpperCase();

            String sql = "UPDATE currencies SET code = " + DatabaseUtils.sqlEscapeString(defaultCode) +
                    ", symbol = " + DatabaseUtils.sqlEscapeString(defaultSymbol) +
                    ", syncStatus = 'EDITED'" +
                    " WHERE id = " + id;
            db.execSQL(sql);
            Log.d("MIGRATION", "Updated currency ID " + id + " with default: " + defaultCode + " (" + defaultSymbol + ")");
        }

        class CurrencyInfo {
            String symbol;
            String code;

            CurrencyInfo(String symbol, String code) {
                this.symbol = symbol;
                this.code = code;
            }
        }
    };
}


