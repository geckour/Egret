package com.geckour.egret.view.fragment

import android.os.Bundle

class SearchResultFragment: BaseFragment() {

    enum class Category {
        All,
        Account,
        Status,
        HashTag
    }

    companion object {
        private val ARGS_KEY_CATEGORY = "category"

        fun newInstance(category: Category = Category.All): SearchResultFragment {
            val fragment = SearchResultFragment()
            val args = Bundle()
            args.putInt(ARGS_KEY_CATEGORY, category.ordinal)
            fragment.arguments = args

            return fragment
        }
    }
}