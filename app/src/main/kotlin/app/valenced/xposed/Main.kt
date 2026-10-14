package app.valenced.xposed

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage
import app.valenced.bridge.RevengeBridge
import app.valenced.xposed.api.HostScope
import app.valenced.xposed.tweaks.*
import app.valenced.xposed.tweaks.base.lifecycleSupport
import app.valenced.xposed.tweaks.base.scriptLoader
import app.valenced.xposed.tweaks.bridge.RevengeBridgeRegistry
import app.valenced.xposed.tweaks.bridge.additionalBridgeMethods
import app.valenced.xposed.tweaks.bridge.revengeBridgeSupport
import app.valenced.xposed.tweaks.legacy.appearance.fonts
import app.valenced.xposed.tweaks.legacy.appearance.sysColors
import app.valenced.xposed.tweaks.legacy.appearance.themes
import app.valenced.xposed.tweaks.legacy.valencedPayloadGlobal
import app.valenced.xposed.tweaks.plugins.pluginInstallMethods
import app.valenced.xposed.tweaks.plugins.pluginLoader
import app.valenced.xposed.tweaks.plugins.pluginMethods
import app.valenced.xposed.tweaks.plugins.pluginStates
import app.valenced.xposed.tweaks.plugins.repos.pluginRepos

private lateinit var modulePath: String

@Suppress("UNUSED")
class Main : IXposedHookLoadPackage, IXposedHookZygoteInit {
    @Volatile
    private var hooked = false

    private val tweaks: List<TweakSpec> = listOf(
        // Framework
        lifecycleSupport,
        revengeBridgeSupport,
        scriptLoader,
        discordVersionRetriever,

        // Static patches
        fixResources,

        // Persistence
        caches,
        pluginStates,
        pluginRepos,

        // Async updater
        valencedUpdater,
        valencedBundleManifest,

        // Consumers
        recoveryCompanion,
        discordDevSupport,
        additionalBridgeMethods,
        fonts,
        themes,
        sysColors,
        pluginLoader,
        pluginMethods,
        pluginInstallMethods,
        valencedScriptLoader,
        valencedPayloadGlobal,
    )

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        modulePath = startupParam.modulePath
    }

    override fun handleLoadPackage(param: XC_LoadPackage.LoadPackageParam) {
        // Only hook the main process.
        // Discord uses ProcessPhoenix to spawn a ":phoenix" process to restart the app. It will cause concurrency issues.
        if (param.processName != param.packageName) return

        if (hooked) return
        hooked = true

        val ctx = HostScopeImpl(
            modulePath = modulePath,
            appInfo = param.appInfo,
            classLoader = param.classLoader,
        )
        for (spec in tweaks) spec.applyTo(ctx)
    }
}

private class HostScopeImpl(
    override val modulePath: String,
    override val appInfo: ApplicationInfo,
    override val classLoader: ClassLoader,
) : HostScope {
    override val bridge: RevengeBridge get() = RevengeBridgeRegistry
    override fun withAppContext(block: (Context) -> Unit) = app.valenced.xposed.tweaks.base.withAppContext(block)
    override fun withAppActivity(block: (Activity) -> Unit) =
        app.valenced.xposed.tweaks.base.withAppActivity(block)
}