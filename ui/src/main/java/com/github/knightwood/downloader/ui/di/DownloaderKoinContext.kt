package com.github.knightwood.downloader.ui.di

import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.component.KoinComponent
import org.koin.core.component.KoinScopeComponent
import org.koin.core.context.KoinContext
import org.koin.core.context.startKoin
import org.koin.core.error.KoinApplicationAlreadyStartedException
import org.koin.core.module.Module
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.qualifier.Qualifier
import org.koin.core.scope.Scope
import org.koin.dsl.KoinAppDeclaration
import org.koin.mp.KoinPlatformTools


object DownloaderKoinContext : KoinContext {

    private var _koin: Koin? = null
    private var _koinApplication: KoinApplication? = null

    override fun get(): Koin = _koin ?: error("KoinApplication has not been started")

    override fun getOrNull(): Koin? = _koin

    fun getKoinApplicationOrNull(): KoinApplication? = _koinApplication

    private fun register(koinApplication: KoinApplication) {
        if (_koin != null) {
            throw KoinApplicationAlreadyStartedException("A Koin Application has already been started")
        }
        _koinApplication = koinApplication
        _koin = koinApplication.koin
    }

    override fun stopKoin() = synchronized(this) {
        _koin?.close()
        _koin = null
    }

    override fun startKoin(koinApplication: KoinApplication): KoinApplication = synchronized(this) {
        register(koinApplication)
        koinApplication.createEagerInstances()
        return koinApplication
    }

    override fun startKoin(appDeclaration: KoinAppDeclaration): KoinApplication = synchronized(this) {
        val koinApplication = KoinApplication.init()
        register(koinApplication)
        appDeclaration(koinApplication)
        koinApplication.createEagerInstances()
        return koinApplication
    }

    override fun loadKoinModules(module: Module, createEagerInstances: Boolean) = synchronized(this) {
        get().loadModules(listOf(module), createEagerInstances = createEagerInstances)
    }

    override fun loadKoinModules(modules: List<Module>, createEagerInstances: Boolean) = synchronized(this) {
        get().loadModules(modules, createEagerInstances = createEagerInstances)
    }

    override fun unloadKoinModules(module: Module) = synchronized(this) {
        get().unloadModules(listOf(module))
    }

    override fun unloadKoinModules(modules: List<Module>) = synchronized(this) {
        get().unloadModules(modules)
    }
}

interface DownloaderKoinComponent {

    /**
     * Get the associated Koin instance
     */
    fun getKoin(): Koin = DownloaderKoinContext.get()
}

interface DownloaderKoinScopeComponent : KoinComponent {

    val scope: Scope
}

/**
 * Get instance from Koin
 * @param qualifier
 * @param parameters
 */
inline fun <reified T : Any> DownloaderKoinComponent.get(
    qualifier: Qualifier? = null,
    noinline parameters: ParametersDefinition? = null,
): T {
    return if (this is DownloaderKoinScopeComponent) {
        scope.get(qualifier, parameters)
    } else {
        getKoin().get(qualifier, parameters)
    }
}

/**
 * Lazy inject instance from Koin
 * @param qualifier
 * @param mode - LazyThreadSafetyMode
 * @param parameters
 */
inline fun <reified T : Any> DownloaderKoinComponent.inject(
    qualifier: Qualifier? = null,
    mode: LazyThreadSafetyMode = KoinPlatformTools.defaultLazyMode(),
    noinline parameters: ParametersDefinition? = null,
): Lazy<T> =
    lazy(mode) { get<T>(qualifier, parameters) }

fun startDownloaderKoin(appDeclaration: KoinAppDeclaration){
    DownloaderKoinContext.startKoin(appDeclaration)
}
