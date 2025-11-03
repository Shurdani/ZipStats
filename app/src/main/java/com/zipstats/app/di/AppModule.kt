package com.zipstats.app.di

import android.content.Context
import android.content.SharedPreferences
import com.zipstats.app.permission.PermissionManager
import com.zipstats.app.repository.SettingsRepository
import com.zipstats.app.repository.AuthRepository
import com.zipstats.app.repository.RecordRepository
import com.zipstats.app.repository.RepairRepository
import com.zipstats.app.repository.RouteRepository
import com.zipstats.app.repository.VehicleRepository
import com.zipstats.app.utils.PreferencesManager
import com.zipstats.app.utils.OnboardingManager
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
    fun provideVehicleRepository(
        firestore: FirebaseFirestore,
        auth: FirebaseAuth
    ): VehicleRepository {
        return VehicleRepository(firestore, auth)
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
    fun provideRouteRepository(
        firestore: FirebaseFirestore,
        auth: FirebaseAuth
    ): RouteRepository {
        return RouteRepository(firestore, auth)
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
    
    @Provides
    @Singleton
    fun providePreferencesManager(@ApplicationContext context: Context): PreferencesManager {
        return PreferencesManager(context)
    }
    
    @Provides
    @Singleton
    fun provideOnboardingManager(sharedPreferences: SharedPreferences): OnboardingManager {
        return OnboardingManager(sharedPreferences)
    }
    
} 