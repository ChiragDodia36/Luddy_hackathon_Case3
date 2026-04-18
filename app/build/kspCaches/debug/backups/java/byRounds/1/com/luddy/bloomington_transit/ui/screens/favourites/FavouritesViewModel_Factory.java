package com.luddy.bloomington_transit.ui.screens.favourites;

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
public final class FavouritesViewModel_Factory implements Factory<FavouritesViewModel> {
  private final Provider<TransitRepository> repositoryProvider;

  public FavouritesViewModel_Factory(Provider<TransitRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public FavouritesViewModel get() {
    return newInstance(repositoryProvider.get());
  }

  public static FavouritesViewModel_Factory create(Provider<TransitRepository> repositoryProvider) {
    return new FavouritesViewModel_Factory(repositoryProvider);
  }

  public static FavouritesViewModel newInstance(TransitRepository repository) {
    return new FavouritesViewModel(repository);
  }
}
