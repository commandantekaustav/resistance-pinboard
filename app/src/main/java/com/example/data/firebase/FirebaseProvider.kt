package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

object FirebaseProvider {
    private const val TAG = "FirebaseProvider"
    @Volatile
    private var configured = false

    fun getFirestore(context: Context): FirebaseFirestore {
        val databaseId = context.applicationContext.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        if (!configured) {
            synchronized(this) {
                if (!configured) {
                    try {
                        val settings = FirebaseFirestoreSettings.Builder()
                            .setLocalCacheSettings(
                                PersistentCacheSettings.newBuilder()
                                    .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                                    .build()
                            )
                            .build()
                        db.firestoreSettings = settings
                    } catch (e: IllegalStateException) {
                        // Settings already applied on this Firestore instance
                        Log.d(TAG, "Firestore settings already initialized: ${e.message}")
                    }
                    configured = true
                }
            }
        }
        return db
    }
}
