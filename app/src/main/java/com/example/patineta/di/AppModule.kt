package com.example.patineta.di

import android.content.Context
import android.content.SharedPreferences
import com.example.patineta.permission.PermissionManager
import com.example.patineta.repository.SettingsRepository
import com.example.patineta.repository.AuthRepository
import com.example.patineta.repository.RecordRepository
import com.example.patineta.repository.RepairRepository
import com.example.patineta.repository.ScooterRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context {
        return context
    }
    
    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("patineta_prefs", Context.MODE_PRIVATE)
    }
    
    @Provides
    @Singleton
    fun provideFirebaseApp(@ApplicationContext context: Context): FirebaseApp {
        return FirebaseApp.initializeApp(context) ?: throw IllegalStateException("Firebase initialization failed")
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(firebaseApp: FirebaseApp): FirebaseAuth {
        return FirebaseAuth.getInstance(firebaseApp)
    }

    @Provides
    @Singleton
    fun provideFirebaseFirestore(firebaseApp: FirebaseApp): FirebaseFirestore {
        return FirebaseFirestore.getInstance(firebaseApp)
    }

    @Provides
    @Singleton
    fun provideFirebaseStorage(firebaseApp: FirebaseApp): FirebaseStorage {
        return FirebaseStorage.getInstance(firebaseApp)
    }
    
    @Provides
    @Singleton
    fun provideAuthRepository(
        auth: FirebaseAuth,
        sharedPreferences: SharedPreferences
    ): AuthRepository {
        return AuthRepository(auth, sharedPreferences)
    }
    
    @Provides
    @Singleton
    fun provideRecordRepository(
        firestore: FirebaseFirestore,
        auth: FirebaseAuth
    ): RecordRepository {
        return RecordRepository(firestore, auth)
    }
    
    @Provides
    @Singleton
    fun provideScooterRepository(
        firestore: FirebaseFirestore,
        auth: FirebaseAuth
    ): ScooterRepository {
        return ScooterRepository(firestore, auth)
    }

    @Provides
    @Singleton
    fun provideRepairRepository(
        firestore: FirebaseFirestore
    ): RepairRepository {
        return RepairRepository(firestore)
    }

    @Provides
    @Singleton
    fun providePermissionManager(@ApplicationContext context: Context): PermissionManager {
        return PermissionManager(context)
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository {
        return SettingsRepository(context)
    }
} 