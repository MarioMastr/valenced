#import "Bridge.h"
#import "Plugins/PluginStates.h"
#import "JSI.h"
#import "Utils.h"
#import "Logger.h"

#import <objc/message.h>
#import <mutex>

using namespace facebook;

static NSMutableDictionary<NSString *, BridgeHandler> *gBridgeHandlers = nil;
static jsi::Runtime *gJsiRuntime = nullptr;
static id gJsiInstance = nil;

NSString * const EVENT_DOWNLOAD_PROGRESS = @"revenge.plugins.events.downloadProgress";
NSString * const EVENT_PLUGIN_INSTALL_READY = @"revenge.plugins.events.pluginInstallFileReady";
NSString * const EVENT_PLUGIN_INSTALL_RESULT = @"revenge.plugins.events.pluginInstallResult";
NSString * const EVENT_PLUGIN_UPDATED = @"revenge.plugins.events.pluginUpdated";
NSString * const EVENT_PLUGIN_ERRORED = @"revenge.plugins.events.pluginErrored";
NSString * const EVENT_REPO_STATE_UPDATE = @"revenge.plugins.events.repoStateUpdate";
NSString * const EVENT_DEPENDENCIES_UPDATED = @"revenge.plugins.events.dependenciesUpdated";
NSString * const EVENT_PLUGIN_STOPPING = @"revenge.plugins.stopping";

NSMutableDictionary<NSString *, BridgeHandler> *bridgeHandlers(void) {
    static std::once_flag once;
    std::call_once(once, [] { gBridgeHandlers = [NSMutableDictionary dictionary]; });
    return gBridgeHandlers;
}

static void ensureHandler(NSDictionary<NSString *, BridgeHandler> *handlers) {
    NSMutableDictionary *registry = bridgeHandlers();
    [registry addEntriesFromDictionary:handlers];
}

void SetJsiRuntime(jsi::Runtime *runtime) {
    gJsiRuntime = runtime;
}

void SetJsiInstance(id instance) {
    gJsiInstance = instance;
}

static jsi::Value callJSFunctionOnRuntime(jsi::Runtime &runtime, NSString *eventName, NSArray *args) {
    @try {
        try {
            std::vector<jsi::Value> callArgs;
            for (id arg in args) {
                callArgs.emplace_back([JSI fromObjC:arg runtime:runtime]);
            }

            jsi::Value callable = runtime.global().getProperty(runtime, "RN$CallableModules");
            if (callable.isObject()) {
                jsi::Object modules = callable.asObject(runtime);
                jsi::Value module = modules.getProperty(runtime, "RevengeBridge");
                if (module.isObject()) {
                    jsi::Object bridge = module.asObject(runtime);
                    jsi::Value fn = bridge.getProperty(runtime, [eventName UTF8String]);
                    if (fn.isObject()) {
                        fn.asObject(runtime).asFunction(runtime).call(runtime, static_cast<const jsi::Value *>(callArgs.data()), callArgs.size());
                        return jsi::Value::undefined();
                    }
                }
            }

            ValencedLog(@"EmitJSEvent: no handler for %@", eventName);
        }
        catch (const std::exception &e) {
            ValencedLog(@"EmitJSEvent error (%@): %s", eventName, e.what());
        }
    }
    @catch (NSException *exc) {
        ValencedLog(@"EmitJSEvent ObjC exception (%@): %@", eventName, exc.reason ?: @"");
    }
    return jsi::Value::undefined();
}

static void dispatchOnJsThread(void (^block)(jsi::Runtime &runtime)) {
    id instance = gJsiInstance;
    if (instance && [instance respondsToSelector:@selector(callFunctionOnBufferedRuntimeExecutor:)]) {
        std::function<void(jsi::Runtime &)> executor = [block](jsi::Runtime &rt) {
            try {
                block(rt);
            } catch (const std::exception &e) {
                ValencedLog(@"EmitJSEvent: executor exception: %s", e.what());
            } catch (...) {
                ValencedLog(@"EmitJSEvent: unknown executor exception");
            }
        };
        ((void (*)(id, SEL, std::function<void(jsi::Runtime &)> &&))objc_msgSend)(
            instance, @selector(callFunctionOnBufferedRuntimeExecutor:), std::move(executor));
        return;
    }
    ValencedLog(@"EmitJSEvent: no RCTInstance, dropping event");
}

void EmitJSEventArgs(NSString *eventName, NSArray *args) {
    @try {
        id instance = gJsiInstance;
        if (instance && [instance respondsToSelector:@selector(callFunctionOnJSModule:method:args:)]) {
            ((void (*)(id, SEL, id, id, id))objc_msgSend)(
                instance, @selector(callFunctionOnJSModule:method:args:),
                @"RevengeBridge", eventName, args);
            return;
        }
    }
    @catch (NSException *exc) {
        ValencedLog(@"EmitJSEvent callFunctionOnJSModule exception (%@): %@",
                    eventName, exc.reason ?: @"");
        return;
    }

    dispatchOnJsThread(^(jsi::Runtime &runtime) {
        callJSFunctionOnRuntime(runtime, eventName, args);
    });
}

void EmitJSEvent(NSString *eventName, NSDictionary *payload) {
    EmitJSEventArgs(eventName, payload ? @[payload] : @[]);
}

NSString *argString(NSArray *args, NSUInteger idx, NSString *fallback) {
    if (idx < args.count && [args[idx] isKindOfClass:[NSString class]]) return args[idx];
    return fallback;
}

BOOL argBool(NSArray *args, NSUInteger idx, BOOL fallback) {
    if (idx < args.count && [args[idx] isKindOfClass:[NSNumber class]]) return [args[idx] boolValue];
    return fallback;
}

id argPresent(NSArray *args, NSUInteger idx) {
    if (idx >= args.count) return nil;
    id v = args[idx];
    return v == (id)[NSNull null] ? nil : v;
}

BOOL argIsString(NSArray *args, NSUInteger idx) {
    return idx < args.count && [args[idx] isKindOfClass:[NSString class]];
}

extern "C" {
void RegisterPluginLoaderMethods(void);
void RegisterRepoMethods(void);
void RegisterCachesMethods(void);
void RegisterAdditionalBridgeMethods(void);
}

void RegisterBridgeMethods(void) {
    ensureHandler(@{
        @"revenge.__callableReturn": ^NSDictionary *(NSArray *args) {
            return @{@"result": [NSNull null]};
        },

        @"revenge.info": ^NSDictionary *(NSArray *args) {
            return @{@"result": @{@"name": @"ValencedTweak", @"version": VERSION_CODE}};
        },

    });

    RegisterPluginLoaderMethods();
    RegisterPluginStatesMethods();
    RegisterRepoMethods();
    RegisterCachesMethods();
    RegisterAdditionalBridgeMethods();
    RegisterRecoveryBridgeMethods();
}

NSDictionary *callBridgeMethodHelper(NSString *methodName, NSArray *args) {
    BOOL pluginSystem = [methodName hasPrefix:@"revenge.plugins."];
    @try {
        BridgeHandler handler = bridgeHandlers()[methodName];
        if (!handler) {
            id error = pluginSystem
                ? @{@"error": @{@"code": @"UNKNOWN", @"message": [NSString stringWithFormat:@"Method not implemented: %@", methodName]}}
                : @{@"error": [NSString stringWithFormat:@"Method not implemented: %@", methodName]};
            return pluginSystem ? @{@"result": error} : error;
        }

        __block NSDictionary *result;
        dispatchSyncFs(^{
            result = handler(args);
        });

        if (pluginSystem) return @{@"result": (id)result ?: [NSNull null]};
        return result;
    }
    @catch (NSException *exception) {
        id error = pluginSystem
            ? @{@"error": @{@"code": @"UNKNOWN", @"message": exception.reason ?: @"Unknown Objective-C exception"}}
            : @{@"error": exception.reason ?: @"Unknown Objective-C exception"};
        return pluginSystem ? @{@"result": error} : error;
    }
}

void injectPreBundle(jsi::Runtime &runtime) {
    try {
        jsi::Object loaderObj(runtime);
        loaderObj.setProperty(runtime, "loaderName", jsi::String::createFromUtf8(runtime, "ValencedTweak"));
        loaderObj.setProperty(runtime, "loaderVersion", jsi::String::createFromUtf8(runtime, [VERSION_CODE UTF8String]));
        runtime.global().setProperty(runtime, "__VALENCED_LOADER__", loaderObj);

        jsi::Function symbolFor = runtime.global()
            .getPropertyAsObject(runtime, "Symbol")
            .getPropertyAsFunction(runtime, "for");
        jsi::Function reflectSet = runtime.global()
            .getPropertyAsObject(runtime, "Reflect")
            .getPropertyAsFunction(runtime, "set");

        jsi::Value syncKey = symbolFor.call(runtime, jsi::String::createFromUtf8(runtime, "revenge.bridge.call.sync"));
        jsi::Value asyncKey = symbolFor.call(runtime, jsi::String::createFromUtf8(runtime, "revenge.bridge.call.async"));

        auto syncCall = jsi::Function::createFromHostFunction(
            runtime,
            jsi::PropNameID::forUtf8(runtime, "revengeBridgeCallSync"),
            1,
            [](jsi::Runtime &rt, const jsi::Value &, const jsi::Value *args, size_t count) -> jsi::Value {
                NSString *methodName = @"";
                NSArray *objcArgs = @[];
                if (count >= 1 && args[0].isObject()) {
                    jsi::Object payload = args[0].asObject(rt);
                    jsi::Value revengeVal = payload.getProperty(rt, "revenge");
                    if (revengeVal.isObject()) {
                        jsi::Object revenge = revengeVal.asObject(rt);
                        jsi::Value methodVal = revenge.getProperty(rt, "method");
                        if (methodVal.isString()) methodName = [JSI toNSString:methodVal runtime:rt];
                        jsi::Value argsVal = revenge.getProperty(rt, "args");
                        if (argsVal.isObject() && argsVal.asObject(rt).isArray(rt))
                            objcArgs = [JSI toObjC:argsVal runtime:rt];
                    }
                }
                NSDictionary *res = callBridgeMethodHelper(methodName, objcArgs);
                return [JSI fromObjC:res runtime:rt];
            }
        );
        {
            jsi::Value setArgs[] = { jsi::Value(runtime, runtime.global()), std::move(syncKey), jsi::Value(runtime, syncCall) };
            reflectSet.call(runtime, static_cast<const jsi::Value *>(setArgs), static_cast<size_t>(3));
        }

        auto asyncCall = jsi::Function::createFromHostFunction(
            runtime,
            jsi::PropNameID::forUtf8(runtime, "revengeBridgeCallAsync"),
            1,
            [](jsi::Runtime &rt, const jsi::Value &, const jsi::Value *args, size_t count) -> jsi::Value {
                NSString *methodName = @"";
                NSArray *objcArgs = @[];
                if (count >= 1 && args[0].isObject()) {
                    jsi::Object payload = args[0].asObject(rt);
                    jsi::Value revengeVal = payload.getProperty(rt, "revenge");
                    if (revengeVal.isObject()) {
                        jsi::Object revenge = revengeVal.asObject(rt);
                        jsi::Value methodVal = revenge.getProperty(rt, "method");
                        if (methodVal.isString()) methodName = [JSI toNSString:methodVal runtime:rt];
                        jsi::Value argsVal = revenge.getProperty(rt, "args");
                        if (argsVal.isObject() && argsVal.asObject(rt).isArray(rt))
                            objcArgs = [JSI toObjC:argsVal runtime:rt];
                    }
                }
                jsi::Function promiseCtor = rt.global().getPropertyAsFunction(rt, "Promise");
                jsi::Function executor = jsi::Function::createFromHostFunction(rt, jsi::PropNameID::forUtf8(rt, "executor"), 2,
                    [methodName, objcArgs](jsi::Runtime &innerRt, const jsi::Value &, const jsi::Value *innerArgs, size_t innerCount) -> jsi::Value {
                        jsi::Function resolve = innerArgs[0].asObject(innerRt).asFunction(innerRt);
                        NSDictionary *res = callBridgeMethodHelper(methodName, objcArgs);
                        resolve.call(innerRt, [JSI fromObjC:res runtime:innerRt]);
                        return jsi::Value::undefined();
                    });
                return promiseCtor.callAsConstructor(rt, executor);
            }
        );
        {
            jsi::Value setArgs[] = { jsi::Value(runtime, runtime.global()), std::move(asyncKey), jsi::Value(runtime, asyncCall) };
            reflectSet.call(runtime, static_cast<const jsi::Value *>(setArgs), static_cast<size_t>(3));
        }
    }
    catch (const jsi::JSError &e) { ValencedLog(@"injectPreBundle: JSError: %s", e.what()); }
    catch (const std::exception &e) { ValencedLog(@"injectPreBundle: exception: %s", e.what()); }
}
