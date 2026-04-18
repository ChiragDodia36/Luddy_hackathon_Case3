package com.luddy.bloomington_transit.service;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class BusNotificationHelper_Factory implements Factory<BusNotificationHelper> {
  private final Provider<Context> contextProvider;

  public BusNotificationHelper_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public BusNotificationHelper get() {
    return newInstance(contextProvider.get());
  }

  public static BusNotificationHelper_Factory create(Provider<Context> contextProvider) {
    return new BusNotificationHelper_Factory(contextProvider);
  }

  public static BusNotificationHelper newInstance(Context context) {
    return new BusNotificationHelper(context);
  }
}
