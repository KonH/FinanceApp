package com.konhit.financeapp.drive

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.drive.DriveScopes

class DriveAuthManager(private val context: Context) {

    val signInClient: GoogleSignInClient by lazy {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(com.google.android.gms.common.api.Scope(DriveScopes.DRIVE_FILE))
            .build()
        GoogleSignIn.getClient(context, options)
    }

    fun getSignedInAccount(): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    fun getCredential(): GoogleAccountCredential? {
        val account = getSignedInAccount() ?: return null
        return GoogleAccountCredential
            .usingOAuth2(context, listOf(DriveScopes.DRIVE_FILE))
            .also { it.selectedAccount = account.account }
    }

    fun isSignedIn(): Boolean = getSignedInAccount() != null
}
