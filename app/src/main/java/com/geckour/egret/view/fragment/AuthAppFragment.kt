package com.geckour.egret.view.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.databinding.DataBindingUtil
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.support.design.widget.Snackbar
import android.support.v4.content.ContextCompat.checkSelfPermission
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import com.geckour.egret.R
import com.geckour.egret.api.MastodonClient
import com.geckour.egret.api.model.InstanceAccess
import com.geckour.egret.databinding.FragmentLoginInstanceBinding
import com.geckour.egret.model.AccessToken
import com.geckour.egret.util.OrmaProvider
import com.geckour.egret.view.activity.LoginActivity
import com.trello.rxlifecycle2.components.support.RxFragment
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.ArrayList

class AuthAppFragment: RxFragment() {

    companion object {
        fun newInstance(): AuthAppFragment {
            val fragment = AuthAppFragment()
            return fragment
        }

        /**
         * Id to identity READ_CONTACTS permission request.
         */
        private val REQUEST_READ_CONTACTS = 0
    }

    private lateinit var binding: FragmentLoginInstanceBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        // Set up the login form.
        populateAutoComplete()

        binding.password.setOnEditorActionListener { textView, id, keyEvent ->
            if (id == R.id.login || id == EditorInfo.IME_NULL) {
                attemptLogin()
                true
            } else false
        }

        binding.emailSignInButton.setOnClickListener { attemptLogin() }
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_login_instance, container, false)
        return binding.root
    }

    private fun populateAutoComplete() {
        if (!mayRequestContacts()) {
            return
        }

        addEmailsToAutoComplete(getEmailsFromContact())
    }

    private fun mayRequestContacts(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        if (checkSelfPermission(activity, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            return true
        }
        if (shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
            Snackbar.make(binding.email, R.string.permission_rationale, Snackbar.LENGTH_INDEFINITE)
                    .setAction(android.R.string.ok, { requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQUEST_READ_CONTACTS) })
        } else {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQUEST_READ_CONTACTS)
        }
        return false
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        if (requestCode == REQUEST_READ_CONTACTS) {
            if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                populateAutoComplete()
            }
        }
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
        //TODO: Replace this with your own logic
        return email.contains("@")
    }

    private fun addEmailsToAutoComplete(emailAddressCollection: List<String>) {
        //Create adapter to tell the AutoCompleteTextView what to show in its dropdown list.
        val adapter = ArrayAdapter(activity,
                android.R.layout.simple_dropdown_item_1line, emailAddressCollection)

        binding.email.setAdapter(adapter)
    }


    private interface ProfileQuery {
        companion object {
            val PROJECTION = arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS, ContactsContract.CommonDataKinds.Email.IS_PRIMARY)

            val ADDRESS = 0
            val IS_PRIMARY = 1
        }
    }

    fun requestAuth(email: String, password: String) {
        (activity as LoginActivity).showProgress(true)

        val appInfo = OrmaProvider.db.selectFromInstanceAuthInfo().last()
        MastodonClient(appInfo.instance).authUser(appInfo.clientId, appInfo.clientSecret, email, password)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe( { value ->
                    (activity as LoginActivity).showProgress(false)
                    storeAccessToken(value)
                }, { throwable ->
                    (activity as LoginActivity).showProgress(false)
                    binding.password.error = getString(R.string.error_incorrect_password)
                    Timber.e(throwable)
                } )
    }

    fun storeAccessToken(value: InstanceAccess) {
        OrmaProvider.db.relationOfAccessToken().upsertAsSingle(createAccessToken(value))
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe( { value ->
                    Timber.d("$value")
                }, Throwable::printStackTrace )
    }

    fun createAccessToken(value: InstanceAccess): AccessToken {
        return AccessToken(-1L, value.accessToken)
    }

    fun getEmailsFromContact(): List<String> {
        val emails: ArrayList<String> = ArrayList()

        val cursor = activity.contentResolver.query(
                // Retrieve data rows for the device user's 'profile' contact.
                Uri.withAppendedPath(
                        ContactsContract.Profile.CONTENT_URI,
                        ContactsContract.Contacts.Data.CONTENT_DIRECTORY),
                ProfileQuery.PROJECTION,

                // Select only email addresses.
                ContactsContract.Contacts.Data.MIMETYPE + " = ?",
                arrayOf(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE),

                // Show primary email addresses first. Note that there won't be
                // a primary email address if the user hasn't specified one.
                ContactsContract.Contacts.Data.IS_PRIMARY + " DESC")

        while (cursor.moveToNext()) {
            emails.add(cursor.getString(ProfileQuery.ADDRESS))
        }

        return emails
    }
}