package net.ralphpina.drawwithme;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private ArrayList<String> history;

    public HistoryAdapter() {
        history = new ArrayList<>();
    }

    @Override
    public HistoryAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // Create View
        View v = LayoutInflater.from(parent.getContext())
                               .inflate(R.layout.history_row,
                                        parent,
                                        false);
        return new ViewHolder(v);
    }

    public void add(String data) {
        if (history.size() == 0 || !data.equals(history.get(history.size() - 1))) {
            history.add(data);
            notifyItemChanged(getItemCount() - 1);
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.mTextView.setText(history.get(position));
    }

    @Override
    public int getItemCount() {
        return history.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView mTextView;

        public ViewHolder(View v) {
            super(v);
            mTextView = (TextView) v.findViewById(R.id.row_text);
        }
    }
}