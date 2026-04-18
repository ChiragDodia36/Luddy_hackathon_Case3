package com.luddy.bloomington_transit.data.api;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import okhttp3.OkHttpClient;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class GtfsStaticParser_Factory implements Factory<GtfsStaticParser> {
  private final Provider<Context> contextProvider;

  private final Provider<OkHttpClient> okHttpClientProvider;

  public GtfsStaticParser_Factory(Provider<Context> contextProvider,
      Provider<OkHttpClient> okHttpClientProvider) {
    this.contextProvider = contextProvider;
    this.okHttpClientProvider = okHttpClientProvider;
  }

  @Override
  public GtfsStaticParser get() {
    return newInstance(contextProvider.get(), okHttpClientProvider.get());
  }

  public static GtfsStaticParser_Factory create(Provider<Context> contextProvider,
      Provider<OkHttpClient> okHttpClientProvider) {
    return new GtfsStaticParser_Factory(contextProvider, okHttpClientProvider);
  }

  public static GtfsStaticParser newInstance(Context context, OkHttpClient okHttpClient) {
    return new GtfsStaticParser(context, okHttpClient);
  }
}
