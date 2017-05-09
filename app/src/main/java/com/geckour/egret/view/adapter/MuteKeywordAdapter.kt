package com.geckour.egret.view.adapter

import android.databinding.DataBindingUtil
import android.support.v7.widget.RecyclerView
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import com.geckour.egret.R
import com.geckour.egret.databinding.ItemRecycleMuteKeywordBinding
import com.geckour.egret.model.MuteKeyword

class MuteKeywordAdapter: RecyclerView.Adapter<MuteKeywordAdapter.ViewHolder>() {

    private val items: ArrayList<MuteKeyword> = ArrayList()

    inner class ViewHolder(val binding: ItemRecycleMuteKeywordBinding): RecyclerView.ViewHolder(binding.root) {
        fun bindData(item: MuteKeyword) {
            binding.item = item
            binding.checkIsRegex.setOnCheckedChangeListener { v, isChecked -> item.isRegex = isChecked }
            binding.itemKeyword.addTextChangedListener(object: TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    s?.let { item.keyword = it.toString() }
                }

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder {
        val binding: ItemRecycleMuteKeywordBinding =
                DataBindingUtil.inflate(LayoutInflater.from(parent?.context), R.layout.item_recycle_mute_keyword, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder?, position: Int) {
        val item = this.items[position]
        holder?.bindData(item)
    }

    override fun getItemCount(): Int {
        return items.size
    }

    fun getItems(): List<MuteKeyword> = this.items

    fun addItem(item: MuteKeyword) {
        this.items.add(item)
        notifyItemInserted(this.items.lastIndex)
    }

    fun addAllItems(items: List<MuteKeyword>) {
        if (items.isNotEmpty()) {
            val size = this.items.size
            this.items.addAll(size, items)
            notifyItemRangeInserted(0, items.size)
        }
    }

    fun clearItems() {
        val size = this.items.size
        this.items.clear()
        notifyItemRangeRemoved(0, size)
    }

    fun resetItems(items: List<MuteKeyword>) {
        clearItems()
        addAllItems(items)
    }
}