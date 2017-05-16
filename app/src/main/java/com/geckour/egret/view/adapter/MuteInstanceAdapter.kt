package com.geckour.egret.view.adapter

import android.databinding.DataBindingUtil
import android.support.v7.widget.RecyclerView
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import com.geckour.egret.R
import com.geckour.egret.databinding.ItemRecycleMuteInstanceBinding
import com.geckour.egret.model.MuteInstance

class MuteInstanceAdapter: RecyclerView.Adapter<MuteInstanceAdapter.ViewHolder>() {

    private val items: ArrayList<MuteInstance> = ArrayList()

    inner class ViewHolder(val binding: ItemRecycleMuteInstanceBinding): RecyclerView.ViewHolder(binding.root) {
        fun bindData(item: MuteInstance) {
            binding.item = item
            binding.itemInstance.addTextChangedListener(object: TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    s?.let { item.instance = it.toString() }
                }

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder {
        val binding: ItemRecycleMuteInstanceBinding =
                DataBindingUtil.inflate(LayoutInflater.from(parent?.context), R.layout.item_recycle_mute_instance, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder?, position: Int) {
        val item = this.items[position]
        holder?.bindData(item)
    }

    override fun getItemCount(): Int = this.items.size

    fun getItems(): List<MuteInstance> = this.items

    fun addItem(item: MuteInstance) {
        this.items.add(item)
        notifyItemInserted(this.items.lastIndex)
    }

    fun addAllItems(items: List<MuteInstance>) {
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

    fun resetItems(items: List<MuteInstance>) {
        clearItems()
        addAllItems(items)
    }
}
