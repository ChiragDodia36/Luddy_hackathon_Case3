package com.luddy.bloomington_transit.di;

import com.luddy.bloomington_transit.data.api.GtfsRealtimeApi;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import okhttp3.OkHttpClient;

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
public final class AppModule_ProvideGtfsRealtimeApiFactory implements Factory<GtfsRealtimeApi> {
  private final Provider<OkHttpClient> okHttpClientProvider;

  public AppModule_ProvideGtfsRealtimeApiFactory(Provider<OkHttpClient> okHttpClientProvider) {
    this.okHttpClientProvider = okHttpClientProvider;
  }

  @Override
  public GtfsRealtimeApi get() {
    return provideGtfsRealtimeApi(okHttpClientProvider.get());
  }

  public static AppModule_ProvideGtfsRealtimeApiFactory create(
      Provider<OkHttpClient> okHttpClientProvider) {
    return new AppModule_ProvideGtfsRealtimeApiFactory(okHttpClientProvider);
  }

  public static GtfsRealtimeApi provideGtfsRealtimeApi(OkHttpClient okHttpClient) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideGtfsRealtimeApi(okHttpClient));
  }
}
