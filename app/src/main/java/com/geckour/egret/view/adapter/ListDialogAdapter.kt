package com.geckour.egret.view.adapter

import android.content.Context
import android.databinding.DataBindingUtil
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.geckour.egret.databinding.ItemListDialogBinding

class ListDialogAdapter(context: Context, val layoutResId: Int, val items: List<Pair<Int, String>>, val listener: OnItemClickListener?): ArrayAdapter<Pair<Int, String>>(context, layoutResId, items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val binding: ItemListDialogBinding =
                if (convertView == null)
                    DataBindingUtil.inflate(
                            context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater,
                            layoutResId,
                            null,
                            false)
                else DataBindingUtil.bind(convertView)
        binding.itemText = items[position].second
        binding.textWrap.setOnClickListener { listener?.onClick(items[position].first) }
        return binding.root
    }

    interface OnItemClickListener {
        fun onClick(resId: Int)
    }
}