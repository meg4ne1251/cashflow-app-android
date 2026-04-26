package com.kakeibo.android.di

import com.kakeibo.android.core.data.auth.SessionManager
import com.kakeibo.android.core.network.auth.SessionInvalidator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindSessionInvalidator(sessionManager: SessionManager): SessionInvalidator
}
