package com.luddy.bloomington_transit.di

import android.content.Context
import androidx.room.Room
import com.luddy.bloomington_transit.data.api.GtfsRealtimeApi
import com.luddy.bloomington_transit.data.local.AppDatabase
import com.luddy.bloomington_transit.data.repository.TransitRepositoryImpl
import com.luddy.bloomington_transit.domain.repository.TransitRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()

    @Provides
    @Singleton
    fun provideGtfsRealtimeApi(okHttpClient: OkHttpClient): GtfsRealtimeApi =
        Retrofit.Builder()
            .baseUrl("https://s3.amazonaws.com/etatransit.gtfs/bloomingtontransit.etaspot.net/")
            .client(okHttpClient)
            .build()
            .create(GtfsRealtimeApi::class.java)

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "bt_transit.db")
            .fallbackToDestructiveMigration()
            .build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTransitRepository(impl: TransitRepositoryImpl): TransitRepository
}
