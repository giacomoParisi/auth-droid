package com.giacomoparisi.authdroid.rx.firebase

import android.net.Uri
import com.giacomoparisi.authdroid.core.AuthError
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import io.reactivex.Single
import io.reactivex.SingleEmitter


fun getFirebaseToken(): Single<String> =
        Single.create {
            when (val user = FirebaseAuth.getInstance().currentUser) {
                null -> it.onError(AuthError.UnknownFirebaseError)
                else -> user.getIdToken(true).bindTask(it) { result ->
                    when (val token = result.token) {
                        null -> it.onError(AuthError.UnknownFirebaseError)
                        else -> it.onSuccess(token)
                    }
                }
            }
        }

fun getFirebaseId(): String? =
        firebaseAuth().currentUser?.uid

fun getCurrentFirebaseUser() =
        getFirebaseToken()
                .map { firebaseAuth().currentUser?.toSocialAuthUser(it) }

fun updateFirebaseProfile(displayName: String? = null, photoUrl: String? = null): Single<Unit> =
        Single.create {
            when (val user = FirebaseAuth.getInstance().currentUser) {
                null -> it.onError(AuthError.UnknownFirebaseError)
                else -> {
                    val request = UserProfileChangeRequest.Builder()
                            .also { builder ->
                                displayName?.let { name -> builder.setDisplayName(name) }
                            }
                            .also { builder ->
                                photoUrl?.let { url -> builder.setPhotoUri(Uri.parse(url)) }
                            }
                            .build()

                    user.updateProfile(request).bindTask(it) { Unit }
                }
            }
        }

fun updateFirebasePassword(password: String): Single<Unit> =
        Single.create {
            when (val user = FirebaseAuth.getInstance().currentUser) {
                null -> it.onError(AuthError.UnknownFirebaseError)
                else -> user.updatePassword(password).bindTask(it) { Unit }

            }
        }

fun resetFirebasePassword(email: String): Single<Unit> =
        Single.create {
            FirebaseAuth.getInstance()
                    .also { firebaseAuth -> firebaseAuth.useAppLanguage() }
                    .sendPasswordResetEmail(email)
                    .bindTask(it) { Unit }
        }

fun firebaseSignOut() {
    FirebaseAuth.getInstance().signOut()
}

internal fun firebaseCredentialSignIn(
        credential: AuthCredential,
        emitter: SingleEmitter<AuthResult>) {
    firebaseAuth().signInWithCredential(credential)
            .bindTask(emitter) { emitter.onSuccess(it) }
}

internal fun <F, T> Task<F>.bindTask(
        emitter: SingleEmitter<T>,
        onSuccess: (F) -> Unit
) {
    this.addOnSuccessListener {
        if (emitter.isDisposed.not()) {
            onSuccess(it)
        }
    }.addOnCanceledListener {
        if (emitter.isDisposed.not()) {
            emitter.onError(AuthError.Cancelled)
        }
    }.addOnFailureListener {
        if (emitter.isDisposed.not()) {
            emitter.onError(it)
        }
    }
}

internal fun firebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()