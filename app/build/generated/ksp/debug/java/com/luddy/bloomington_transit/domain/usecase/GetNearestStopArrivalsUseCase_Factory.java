package com.luddy.bloomington_transit.domain.usecase;

import com.luddy.bloomington_transit.domain.repository.TransitRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class GetNearestStopArrivalsUseCase_Factory implements Factory<GetNearestStopArrivalsUseCase> {
  private final Provider<TransitRepository> repositoryProvider;

  public GetNearestStopArrivalsUseCase_Factory(Provider<TransitRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public GetNearestStopArrivalsUseCase get() {
    return newInstance(repositoryProvider.get());
  }

  public static GetNearestStopArrivalsUseCase_Factory create(
      Provider<TransitRepository> repositoryProvider) {
    return new GetNearestStopArrivalsUseCase_Factory(repositoryProvider);
  }

  public static GetNearestStopArrivalsUseCase newInstance(TransitRepository repository) {
    return new GetNearestStopArrivalsUseCase(repository);
  }
}
