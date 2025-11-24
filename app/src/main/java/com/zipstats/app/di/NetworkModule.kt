package com.zipstats.app.di

import com.zipstats.app.BuildConfig
import com.zipstats.app.network.CloudinaryApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // URL base de Cloudinary. Usamos el Cloud Name desde BuildConfig.
    // Si no tienes BuildConfig configurado aún, puedes poner el string directo temporalmente.
    private const val CLOUDINARY_BASE_URL = "https://api.cloudinary.com/v1_1/${BuildConfig.CLOUDINARY_CLOUD_NAME}/"

    @Provides
    @Singleton
    @Named("CloudinaryRetrofit") // Usamos Named por si tienes otro Retrofit para otra API
    fun provideCloudinaryRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(CLOUDINARY_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideCloudinaryApi(@Named("CloudinaryRetrofit") retrofit: Retrofit): CloudinaryApi {
        return retrofit.create(CloudinaryApi::class.java)
    }
    
    // Si ya tienes otros métodos aquí (como provideFirebaseFirestore, etc.),
    // déjalos tal cual. Solo añade los de arriba.
}