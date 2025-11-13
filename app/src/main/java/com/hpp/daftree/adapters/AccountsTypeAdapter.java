package com.hpp.daftree.adapters;
/*
AccountsTypeAdapter
 
public class AccountsTypeAdapter {
}
*/

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hpp.daftree.R;
import com.hpp.daftree.database.AccountType;

import java.util.List;

public class AccountsTypeAdapter extends RecyclerView.Adapter<AccountsTypeAdapter.AccountsTypeViewHolder> {

    private List<AccountType> accountsType;
    private final OnItemInteractionListener mListener; // **إضافة جديدة**

    public interface OnItemInteractionListener {
        void onAccountTypeLongClicked(AccountType accountsType);
        void onEditeAccountType(AccountType accountsType);
        void onDeleteAccountType(AccountType accountsType);
    }
    public AccountsTypeAdapter(List<AccountType> accountsType, OnItemInteractionListener listener) {

        this.accountsType = accountsType;
        this.mListener = listener;
    }

    @NonNull
    @Override
    public AccountsTypeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_account_type, parent, false);
        return new AccountsTypeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AccountsTypeViewHolder holder, int position) {
        AccountType accountsTypes = accountsType.get(position);

        if (accountsTypes.isDefault()) {
            holder.nameTextView.setText(getTranslatedDefaultName(accountsTypes.name, holder.itemView.getContext()));
            holder.defaultLabel.setVisibility(View.VISIBLE);
            holder.deleteButton.setVisibility(View.GONE);
        } else {
            holder.defaultLabel.setVisibility(View.GONE);
            holder.deleteButton.setVisibility(View.VISIBLE);
            holder.nameTextView.setText(accountsTypes.name);
        }
        holder.editButton.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onEditeAccountType(accountsTypes);
            }
        });
        holder.deleteButton.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onDeleteAccountType(accountsTypes);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (mListener != null) {
                mListener.onAccountTypeLongClicked(accountsTypes);
            }
            return true;
        });
    }
    /**
     * ترجمة أسماء الأنواع الافتراضية بناءً على لغة التطبيق
     */
    private String getTranslatedDefaultName(String originalName, Context context) {
        switch (originalName) {
            case "عملاء":
                return context.getString(R.string.account_type_customer);
            case "موردين":
                return context.getString(R.string.account_type_supplier);
            case "عام":
                return context.getString(R.string.account_type_general);
            default:
                return originalName;
        }
    }
    @Override
    public int getItemCount() {
        return accountsType.size();
    }

    public void setAccountsType(List<AccountType> accountsType) {
        this.accountsType = accountsType;
        notifyDataSetChanged();
    }
    public List<AccountType> getAccountTypes(List<AccountType> accountsType) {
        this.accountsType = accountsType;
//        notifyDataSetChanged();
        return accountsType;
    }
    static class AccountsTypeViewHolder extends RecyclerView.ViewHolder {
        TextView nameTextView;
        TextView defaultLabel;
        ImageView editButton;
        ImageView deleteButton;
        AccountsTypeViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.text1);

            defaultLabel = itemView.findViewById(R.id.text_default_label);
            editButton = itemView.findViewById(R.id.btn_edit);
            deleteButton = itemView.findViewById(R.id.btn_delete);
        }
    }
}
