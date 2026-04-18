package com.luddy.bloomington_transit.service;

import com.luddy.bloomington_transit.domain.repository.TransitRepository;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class BusTrackingService_MembersInjector implements MembersInjector<BusTrackingService> {
  private final Provider<TransitRepository> repositoryProvider;

  private final Provider<BusNotificationHelper> notificationHelperProvider;

  public BusTrackingService_MembersInjector(Provider<TransitRepository> repositoryProvider,
      Provider<BusNotificationHelper> notificationHelperProvider) {
    this.repositoryProvider = repositoryProvider;
    this.notificationHelperProvider = notificationHelperProvider;
  }

  public static MembersInjector<BusTrackingService> create(
      Provider<TransitRepository> repositoryProvider,
      Provider<BusNotificationHelper> notificationHelperProvider) {
    return new BusTrackingService_MembersInjector(repositoryProvider, notificationHelperProvider);
  }

  @Override
  public void injectMembers(BusTrackingService instance) {
    injectRepository(instance, repositoryProvider.get());
    injectNotificationHelper(instance, notificationHelperProvider.get());
  }

  @InjectedFieldSignature("com.luddy.bloomington_transit.service.BusTrackingService.repository")
  public static void injectRepository(BusTrackingService instance, TransitRepository repository) {
    instance.repository = repository;
  }

  @InjectedFieldSignature("com.luddy.bloomington_transit.service.BusTrackingService.notificationHelper")
  public static void injectNotificationHelper(BusTrackingService instance,
      BusNotificationHelper notificationHelper) {
    instance.notificationHelper = notificationHelper;
  }
}
