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
public final class GetLiveBusPositionsUseCase_Factory implements Factory<GetLiveBusPositionsUseCase> {
  private final Provider<TransitRepository> repositoryProvider;

  public GetLiveBusPositionsUseCase_Factory(Provider<TransitRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public GetLiveBusPositionsUseCase get() {
    return newInstance(repositoryProvider.get());
  }

  public static GetLiveBusPositionsUseCase_Factory create(
      Provider<TransitRepository> repositoryProvider) {
    return new GetLiveBusPositionsUseCase_Factory(repositoryProvider);
  }

  public static GetLiveBusPositionsUseCase newInstance(TransitRepository repository) {
    return new GetLiveBusPositionsUseCase(repository);
  }
}
