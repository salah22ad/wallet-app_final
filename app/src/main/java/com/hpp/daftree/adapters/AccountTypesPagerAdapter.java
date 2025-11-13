package com.hpp.daftree.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.hpp.daftree.database.AccountType;
import com.hpp.daftree.ui.AccountListFragment;

import java.util.List;

public class AccountTypesPagerAdapter extends FragmentStateAdapter {

    private final List<AccountType> accountTypes;
    private static final int EXTRA_PAGES = 2;

    public AccountTypesPagerAdapter(@NonNull FragmentActivity fragmentActivity, List<AccountType> accountTypes) {
        super(fragmentActivity);
        this.accountTypes = accountTypes;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        // **!! التعديل الجوهري هنا !!**
        // الآن نمرر firestoreId بدلاً من الاسم
        if (position == 0) {
            // الصفحة الوهمية الأولى (نسخة من آخر نوع حساب حقيقي)
            return AccountListFragment.newInstance(accountTypes.get(accountTypes.size() - 1).getFirestoreId());
        } else if (position == getItemCount() - 1) {
            // الصفحة الوهمية الأخيرة (نسخة من "كل الحسابات")
            return AccountListFragment.newInstance(null);
        } else if (position == 1) {
            // الصفحة الحقيقية الأولى: "كل الحسابات" (الفلتر null)
            return AccountListFragment.newInstance(null);
        } else {
            // باقي الصفحات الحقيقية: أنواع الحسابات
            // نمرر الـ firestoreId الخاص بالنوع
            return AccountListFragment.newInstance(accountTypes.get(position - 2).getFirestoreId());
        }
    }

    @Override
    public int getItemCount() {
        if (accountTypes == null) return 0;
        return accountTypes.size() + 1 + EXTRA_PAGES;
    }

    public int getRealPosition(int position) {
        if (accountTypes == null || accountTypes.isEmpty()) return 0;
        int realItemCount = accountTypes.size() + 1;

        if (position == 0) {
            return realItemCount - 1;
        } else if (position == getItemCount() - 1) {
            return 0;
        } else {
            return position - 1;
        }
    }
}