package com.geckour.egret.view.adapter

import android.databinding.DataBindingUtil
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.geckour.egret.R
import com.geckour.egret.api.model.Account
import com.geckour.egret.databinding.ItemRecycleMuteAccountBinding

class MuteAccountAdapter: RecyclerView.Adapter<MuteAccountAdapter.ViewHolder>() {

    private val items: ArrayList<Account> = ArrayList()

    inner class ViewHolder(val binding: ItemRecycleMuteAccountBinding): RecyclerView.ViewHolder(binding.root) {
        fun bindData(item: Account) {
            binding.item = item
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder {
        val binding: ItemRecycleMuteAccountBinding =
                DataBindingUtil.inflate(LayoutInflater.from(parent?.context), R.layout.item_recycle_mute_account, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder?, position: Int) {
        val item = this.items[position]
        holder?.bindData(item)
    }

    override fun getItemCount(): Int = this.items.size

    fun getItems(): List<Account> = this.items

    fun addItem(item: Account) {
        this.items.add(item)
        notifyItemInserted(this.items.lastIndex)
    }

    fun addAllItems(items: List<Account>) {
        if (items.isNotEmpty()) {
            val size = this.items.size
            this.items.addAll(size, items)
            notifyItemRangeInserted(0, items.size)
        }
    }

    fun removeItemsByIndex(index: Int) {
        items.removeAt(index)
        notifyItemRemoved(index)
    }

    fun clearItems() {
        val size = this.items.size
        this.items.clear()
        notifyItemRangeRemoved(0, size)
    }

    fun resetItems(items: List<Account>) {
        clearItems()
        addAllItems(items)
    }
}