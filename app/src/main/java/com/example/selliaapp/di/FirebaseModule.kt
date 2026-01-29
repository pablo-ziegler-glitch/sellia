package com.example.selliaapp.di

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.functions.FirebaseFunctions
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseApp(@ApplicationContext context: Context): FirebaseApp {
        // [NUEVO] Garantiza inicialización antes de inyectar Firestore.
        // Si falta google-services.json / plugin, initializeApp devuelve null.
        return FirebaseApp.initializeApp(context)
            ?: throw IllegalStateException(
                "Firebase no está configurado. " +
                        "Agregá app/google-services.json y aplicá el plugin com.google.gms.google-services."
            )
    }

    @Provides
    @Singleton
    fun provideFirestore(firebaseApp: FirebaseApp): FirebaseFirestore {
        // [NUEVO] Usa el FirebaseApp ya inicializado (evita Default FirebaseApp not initialized)
        return FirebaseFirestore.getInstance(firebaseApp).apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                // .setPersistenceEnabled(true) // si querés cache offline
                .build()
        }
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(firebaseApp: FirebaseApp): FirebaseAuth =
        FirebaseAuth.getInstance(firebaseApp)

    @Provides
    @Singleton
    fun provideFirebaseFunctions(firebaseApp: FirebaseApp): FirebaseFunctions =
        FirebaseFunctions.getInstance(firebaseApp)
}
