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
public final class GetArrivalsForStopUseCase_Factory implements Factory<GetArrivalsForStopUseCase> {
  private final Provider<TransitRepository> repositoryProvider;

  public GetArrivalsForStopUseCase_Factory(Provider<TransitRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public GetArrivalsForStopUseCase get() {
    return newInstance(repositoryProvider.get());
  }

  public static GetArrivalsForStopUseCase_Factory create(
      Provider<TransitRepository> repositoryProvider) {
    return new GetArrivalsForStopUseCase_Factory(repositoryProvider);
  }

  public static GetArrivalsForStopUseCase newInstance(TransitRepository repository) {
    return new GetArrivalsForStopUseCase(repository);
  }
}
