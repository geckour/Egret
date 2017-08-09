package com.geckour.egret.view.fragment

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import com.geckour.egret.R
import com.geckour.egret.api.MastodonClient
import com.geckour.egret.api.model.InstanceAccess
import com.geckour.egret.databinding.FragmentLoginInstanceBinding
import com.geckour.egret.model.AccessToken
import com.geckour.egret.util.Common
import com.geckour.egret.util.OrmaProvider
import com.geckour.egret.view.activity.BaseActivity
import com.geckour.egret.view.activity.LoginActivity
import com.trello.rxlifecycle2.components.support.RxFragment
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

class AuthAppFragment: RxFragment() {

    companion object {
        fun newInstance(): AuthAppFragment {
            val fragment = AuthAppFragment()
            return fragment
        }
    }

    private lateinit var binding: FragmentLoginInstanceBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.password.setOnEditorActionListener { textView, id, keyEvent ->
            if (id == R.id.login || id == EditorInfo.IME_NULL) {
                attemptLogin()
                true
            } else false
        }

        binding.emailSignInButton.setOnClickListener { attemptLogin() }
        focusToEmail()
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_login_instance, container, false)
        return binding.root
    }

    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid email, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    private fun attemptLogin() {
        // Reset errors.
        binding.email.error = null
        binding.password.error = null

        // Store values at the time of the login attempt.
        val email = binding.email.text.toString()
        val password = binding.password.text.toString()

        var cancel = false
        var focusView: View? = null

        // Check for a valid password, if the user entered one.
        if (TextUtils.isEmpty(password)) {
            binding.password.error = getString(R.string.error_field_required)
            focusView = binding.password
            cancel = true
        }

        // Check for a valid email address.
        if (TextUtils.isEmpty(email)) {
            binding.email.error = getString(R.string.error_field_required)
            focusView = binding.email
            cancel = true
        } else if (!isEmailValid(email)) {
            binding.email.error = getString(R.string.error_invalid_email)
            focusView = binding.email
            cancel = true
        }

        if (cancel && focusView != null) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView.requestFocus()
        } else {
            // Show a progress spinner, and kick off a background task to
            // perform the user login attempt.
            (activity as LoginActivity).showProgress(true)
            requestAuth(email, password)
        }
    }

    private fun isEmailValid(email: String): Boolean {
        return email.matches(Regex(".+@.+\\..+"))
    }

    private fun focusToEmail() {
        Common.showSoftKeyBoardOnFocusEditText(binding.email)
    }

    fun requestAuth(email: String, password: String) {
        (activity as LoginActivity).showProgress(true)

        val appInfo = OrmaProvider.db.selectFromInstanceAuthInfo().orderBy("createdAt DESC").first()
        MastodonClient(appInfo.instance).authUser(appInfo.clientId, appInfo.clientSecret, email, password)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe( { value ->
                    storeAccessToken(appInfo.id, value)
                }, { throwable ->
                    (activity as LoginActivity).showProgress(false)
                    binding.password.error = getString(R.string.error_incorrect_password)
                    Timber.e(throwable)
                } )
    }

    fun storeAccessToken(instanceId: Long, value: InstanceAccess) {
        OrmaProvider.db.updateAccessToken().isCurrentEq(true).isCurrent(false).executeAsSingle()
                .flatMap { OrmaProvider.db.relationOfAccessToken().upsertAsSingle(createAccessToken(instanceId, value)) }
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe({
                    Timber.d("token: ${it.token}")

                    Common.hasCertified(it, { hasCertified, accountId ->
                        if (hasCertified) {
                            OrmaProvider.db.updateAccessToken().isCurrentEq(true).accountId(accountId).executeAsSingle()
                                    .subscribeOn(Schedulers.newThread())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .compose(bindToLifecycle())
                                    .subscribe({
                                        (activity as LoginActivity).showProgress(false)
                                        (activity as LoginActivity).showMainActivity()
                                    }, Throwable::printStackTrace)
                        }
                    })
                }, Throwable::printStackTrace)
    }

    fun createAccessToken(instanceId: Long, value: InstanceAccess): AccessToken {
        return AccessToken(-1L, value.accessToken, instanceId, -1L, true)
    }
}