package com.novastore.app.data.updater.di

import com.novastore.app.data.updater.UpdateEngine
import com.novastore.app.domain.repository.UpdateCheckService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class UpdaterModule {

    @Binds
    abstract fun bindUpdateCheckService(engine: UpdateEngine): UpdateCheckService
}
