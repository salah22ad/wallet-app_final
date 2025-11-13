package com.hpp.daftree.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.hpp.daftree.MyApplication;
import com.hpp.daftree.R;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.databinding.ItemAccountBinding;
import com.hpp.daftree.models.AccountWithBalance;
import com.hpp.daftree.ui.AddTransactionActivity;

import java.text.NumberFormat;
import java.util.Locale;


public class AccountsAdapter extends ListAdapter<AccountWithBalance, AccountsAdapter.AccountViewHolder> {

//    private String currentCurrency;
    private final OnAccountInteractionListener mListener;
    private String currentCurrency = MyApplication.defaultCurrencyName;
    /**
     * جديد: تعريف الواجهة (Interface) للتواصل مع الـ Activity.
     * تحتوي على دالتين: واحدة للضغط على العنصر كاملاً، والأخرى لزر الإضافة.
     */
    public interface OnAccountInteractionListener {
        void onAccountClicked(Account account, String currency);
        void onAddTransactionClicked(Account account, String currency);
        void onAccountLongClicked(Account account);
    }

    /**
     * تعديل: المُنشئ الآن يستقبل الـ Listener.
     */
    public AccountsAdapter(OnAccountInteractionListener listener) {
        super(DIFF_CALLBACK);
        this.mListener = listener;
        this.currentCurrency = MyApplication.defaultCurrencyName; // قيمة افتراضية
    }

    private static final DiffUtil.ItemCallback<AccountWithBalance> DIFF_CALLBACK = new DiffUtil.ItemCallback<AccountWithBalance>() {
        @Override
        public boolean areItemsTheSame(@NonNull AccountWithBalance oldItem, @NonNull AccountWithBalance newItem) {
            return oldItem.account.getId() == newItem.account.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull AccountWithBalance oldItem, @NonNull AccountWithBalance newItem) {
            return oldItem.balance == newItem.balance && oldItem.transactionCount == newItem.transactionCount;
        }
    };

    @NonNull
    @Override
    public AccountViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAccountBinding binding = ItemAccountBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new AccountViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull AccountViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    public void setCurrency(String currency) {
        this.currentCurrency = currency;
    }

    class AccountViewHolder extends RecyclerView.ViewHolder {
        private final ItemAccountBinding binding;

        public AccountViewHolder(ItemAccountBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(final AccountWithBalance accountWithBalance) {
            Context context = itemView.getContext();
            binding.accountNameTextView.setText(accountWithBalance.account.getAccountName());
            binding.balanceTextView.setText(NumberFormat.getNumberInstance(Locale.US).format(Math.abs(accountWithBalance.balance)));
            binding.transactionCountTextView.setText(String.valueOf(accountWithBalance.transactionCount));
            binding.accountTypeTextView.setText(accountWithBalance.account.getAccountType());
            if (accountWithBalance.balance > 0) {
                binding.arrowImageView.setImageResource(R.drawable.ic_arrow_down_red);
                binding.balanceTextView.setTextColor(ContextCompat.getColor(context, R.color.red));
            } else {
                binding.arrowImageView.setImageResource(R.drawable.ic_arrow_up_green);
                binding.balanceTextView.setTextColor(ContextCompat.getColor(context, R.color.green));
            }

            itemView.setOnClickListener(v -> {
                if (mListener != null) {
                    mListener.onAccountClicked(accountWithBalance.account, currentCurrency);
                }
            });
           itemView.setOnLongClickListener(v -> {
                if (mListener != null) {
                    mListener.onAccountLongClicked(accountWithBalance.account);
                    return true; // يشير إلى أن الحدث قد تم التعامل معه
                }
                return false;
            });
            // 2. الضغط على زر الإضافة (+)
//            binding.addTransactionButton.setOnClickListener(v -> {
//                if (mListener != null) {
//                    mListener.onAddTransactionClicked(accountWithBalance.account, currentCurrency);
//                }
//            });
            binding.addTransactionButton.setOnClickListener(v -> {
//                Context context = itemView.getContext();
                Intent intent = new Intent(context, AddTransactionActivity.class);
                // تمرير البيانات اللازمة للشاشة التالية
                intent.putExtra("ACCOUNT_ID", accountWithBalance.account.getId());
                intent.putExtra("ACCOUNT_NAME", accountWithBalance.account.getAccountName());
                intent.putExtra("CURRENCY", currentCurrency);
                intent.putExtra("isOld", "true");
                context.startActivity(intent);
            });
        }
    }

}