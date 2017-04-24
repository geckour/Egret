package com.geckour.egret.view.activity

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.TargetApi
import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.databinding.DataBindingUtil
import android.os.Build
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import com.geckour.egret.R
import com.geckour.egret.databinding.ActivityLoginBinding
import com.geckour.egret.view.fragment.AccessInstanceFragment
import com.geckour.egret.view.fragment.AuthAppFragment
import com.trello.rxlifecycle2.components.support.RxAppCompatActivity

class LoginActivity : BaseActivity() {

    companion object {
        fun getIntent(context: Context): Intent {
            val intent = Intent(context, LoginActivity::class.java)
            return intent
        }
    }

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_login)

        supportFragmentManager.beginTransaction()
                .replace(R.id.login_form, AccessInstanceFragment.newInstance())
                .commit()
    }

    fun showAuthAppFragment() {
        supportFragmentManager.beginTransaction()
                .replace(R.id.login_form, AuthAppFragment.newInstance())
                .commit()
    }

    fun showMainActivity() {
        startActivity(MainActivity.getIntent(this))
    }

    /**
     * Shows the progress UI and hides the login form.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    fun showProgress(show: Boolean) {
        // On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
        // for very easy animations. If available, use these APIs to fade-in
        // the progress spinner.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            val shortAnimTime = resources.getInteger(android.R.integer.config_shortAnimTime)

            binding.loginForm.visibility = if (show) View.GONE else View.VISIBLE
            binding.loginForm.animate().setDuration(shortAnimTime.toLong()).alpha(
                    (if (show) 0 else 1).toFloat()).setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.loginForm.visibility = if (show) View.GONE else View.VISIBLE
                }
            })

            binding.loginProgress.visibility = if (show) View.VISIBLE else View.GONE
            binding.loginProgress.animate().setDuration(shortAnimTime.toLong()).alpha(
                    (if (show) 1 else 0).toFloat()).setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.loginProgress.visibility = if (show) View.VISIBLE else View.GONE
                }
            })
        } else {
            // The ViewPropertyAnimator APIs are not available, so simply show
            // and hide the relevant UI components.
            binding.loginProgress.visibility = if (show) View.VISIBLE else View.GONE
            binding.loginForm.visibility = if (show) View.GONE else View.VISIBLE
        }
    }
}
