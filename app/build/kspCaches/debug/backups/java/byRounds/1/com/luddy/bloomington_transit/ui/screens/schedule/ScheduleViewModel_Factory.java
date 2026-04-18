package com.luddy.bloomington_transit.ui.screens.schedule;

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
public final class ScheduleViewModel_Factory implements Factory<ScheduleViewModel> {
  private final Provider<TransitRepository> repositoryProvider;

  public ScheduleViewModel_Factory(Provider<TransitRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public ScheduleViewModel get() {
    return newInstance(repositoryProvider.get());
  }

  public static ScheduleViewModel_Factory create(Provider<TransitRepository> repositoryProvider) {
    return new ScheduleViewModel_Factory(repositoryProvider);
  }

  public static ScheduleViewModel newInstance(TransitRepository repository) {
    return new ScheduleViewModel(repository);
  }
}
