package com.hpp.daftree.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hpp.daftree.R;
import com.hpp.daftree.database.Currency;

import java.util.List;

public class CurrenciesAdapter extends RecyclerView.Adapter<CurrenciesAdapter.CurrencyViewHolder> {

    private List<Currency> currencies;
    private final OnItemInteractionListener mListener;

    public interface OnItemInteractionListener {
        void onCurrencyLongClicked(Currency currency);
        void onEditCurrency(Currency currency);
        void onDeleteCurrency(Currency currency);
    }

    public CurrenciesAdapter(List<Currency> currencies, OnItemInteractionListener listener) {
        this.currencies = currencies;
        this.mListener = listener;
    }

    @NonNull
    @Override
    public CurrencyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_currency, parent, false);
        return new CurrencyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CurrencyViewHolder holder, int position) {
        Currency currency = currencies.get(position);

        // تعيين البيانات
        holder.nameTextView.setText(currency.name);
        holder.symbolTextView.setText(currency.symbol);
        holder.codeTextView.setText(currency.code);

        // إدارة عرض العناصر الافتراضية
        if (currency.isDefault()) {
            holder.defaultLabel.setVisibility(View.VISIBLE);
            holder.deleteButton.setVisibility(View.GONE);
        } else {
            holder.defaultLabel.setVisibility(View.GONE);
            holder.deleteButton.setVisibility(View.VISIBLE);
        }

        // معالجة النقرات
        holder.editButton.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onEditCurrency(currency);
            }
        });

        holder.deleteButton.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onDeleteCurrency(currency);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (mListener != null) {
                mListener.onCurrencyLongClicked(currency);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return currencies.size();
    }

    public void setCurrencies(List<Currency> currencies) {
        this.currencies = currencies;
        notifyDataSetChanged();
    }

    public List<Currency> getCurrencies() {
        return currencies;
    }

    static class CurrencyViewHolder extends RecyclerView.ViewHolder {
        TextView nameTextView;
        TextView symbolTextView;
        TextView codeTextView;
        TextView defaultLabel;
        ImageView editButton;
        ImageView deleteButton;

        CurrencyViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.text_currency_name);
            symbolTextView = itemView.findViewById(R.id.text_currency_symbol);
            codeTextView = itemView.findViewById(R.id.text_currency_code);
            defaultLabel = itemView.findViewById(R.id.text_default_label);
            editButton = itemView.findViewById(R.id.btn_edit);
            deleteButton = itemView.findViewById(R.id.btn_delete);
        }
    }
}