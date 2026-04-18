package com.luddy.bloomington_transit.data.repository;

import com.luddy.bloomington_transit.data.api.GtfsRealtimeApi;
import com.luddy.bloomington_transit.data.api.GtfsStaticParser;
import com.luddy.bloomington_transit.data.local.AppDatabase;
import com.luddy.bloomington_transit.data.local.UserPreferencesDataStore;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation"
})
public final class TransitRepositoryImpl_Factory implements Factory<TransitRepositoryImpl> {
  private final Provider<AppDatabase> dbProvider;

  private final Provider<GtfsRealtimeApi> realtimeApiProvider;

  private final Provider<GtfsStaticParser> staticParserProvider;

  private final Provider<UserPreferencesDataStore> prefsProvider;

  public TransitRepositoryImpl_Factory(Provider<AppDatabase> dbProvider,
      Provider<GtfsRealtimeApi> realtimeApiProvider,
      Provider<GtfsStaticParser> staticParserProvider,
      Provider<UserPreferencesDataStore> prefsProvider) {
    this.dbProvider = dbProvider;
    this.realtimeApiProvider = realtimeApiProvider;
    this.staticParserProvider = staticParserProvider;
    this.prefsProvider = prefsProvider;
  }

  @Override
  public TransitRepositoryImpl get() {
    return newInstance(dbProvider.get(), realtimeApiProvider.get(), staticParserProvider.get(), prefsProvider.get());
  }

  public static TransitRepositoryImpl_Factory create(Provider<AppDatabase> dbProvider,
      Provider<GtfsRealtimeApi> realtimeApiProvider,
      Provider<GtfsStaticParser> staticParserProvider,
      Provider<UserPreferencesDataStore> prefsProvider) {
    return new TransitRepositoryImpl_Factory(dbProvider, realtimeApiProvider, staticParserProvider, prefsProvider);
  }

  public static TransitRepositoryImpl newInstance(AppDatabase db, GtfsRealtimeApi realtimeApi,
      GtfsStaticParser staticParser, UserPreferencesDataStore prefs) {
    return new TransitRepositoryImpl(db, realtimeApi, staticParser, prefs);
  }
}
