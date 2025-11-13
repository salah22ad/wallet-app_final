package com.hpp.daftree.ui;

import android.app.Application;
import androidx.lifecycle.LiveData;

import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.AccountDao;
import com.hpp.daftree.database.AppDatabase; // سنقوم بإنشاء هذا الكلاس في الخطوة التالية

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository لإدارة بيانات الحسابات.
 * يعمل كوسيط بين ViewModel ومصادر البيانات (قاعدة بيانات Room في هذه الحالة).
 * هذا يسمح بفصل منطق جلب البيانات عن واجهة المستخدم.
 */
public class AccountRepository {

    private AccountDao accountDao;
    private LiveData<List<Account>> allAccounts;

    // ExecutorService لتشغيل عمليات قاعدة البيانات في خيط خلفي.
    // هذا ضروري لمنع تجميد واجهة المستخدم.
    private final ExecutorService databaseWriteExecutor = Executors.newSingleThreadExecutor();

    /**
     * المُنشئ الخاص بالمستودع.
     * @param application يستخدم للحصول على سياق التطبيق لتهيئة قاعدة البيانات.
     */
    public AccountRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        accountDao = db.accountDao();
        allAccounts = accountDao.getAllAccount();
    }

    /**
     * دالة عامة لجلب جميع الحسابات.
     * تقوم بإرجاع LiveData مباشرة من DAO.
     * Room يعتني بتشغيل هذا الاستعلام في خيط منفصل تلقائيًا عند استخدام LiveData.
     * @return LiveData تحتوي على قائمة بجميع الحسابات.
     */
    public LiveData<List<Account>> getAllAccounts() {
        return allAccounts;
    }

    /**
     * دالة لإدراج حساب جديد.
     * تستخدم ExecutorService لتشغيل عملية الإدراج في خيط خلفي.
     * @param account الحساب المراد إدراجه.
     */
    public void insert(Account account) {
        databaseWriteExecutor.execute(() -> {
            accountDao.insert(account);
        });
    }

    /**
     * دالة لتحديث حساب موجود.
     * تستخدم ExecutorService لتشغيل عملية التحديث في خيط خلفي.
     * @param account الحساب المراد تحديثه.
     */
    public void update(Account account) {
        databaseWriteExecutor.execute(() -> {
            accountDao.update(account);
        });
    }
}

