#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>
#import <jsi/jsi.h>
#import <functional>
#import <unistd.h>
#import <dlfcn.h>

#import "Bridge.h"
#import "Updater.h"
#import "Logger.h"
#import "Utils.h"
#import "JSI.h"
#import "RCTHost.h"
#import "RCTInstance.h"
#import "Constants.h"
#import "Plugins/PluginLoader.h"

using namespace facebook;

static jsi::Runtime *gRuntime = NULL;
static NSTimeInterval shakeStartTime = 0;
static BOOL           isShaking      = NO;

static void executePreloads(jsi::Runtime &runtime, NSURL *valencedDir)
{
    NSURL *preloadsDirectory = [valencedDir URLByAppendingPathComponent:PRELOADS_DIR];
    NSFileManager *fm = [NSFileManager defaultManager];
    if (![fm fileExistsAtPath:preloadsDirectory.path]) return;

    NSArray<NSURL *> *contents = [fm contentsOfDirectoryAtURL:preloadsDirectory
                                   includingPropertiesForKeys:nil options:0 error:nil];
    NSArray *sorted = [contents sortedArrayUsingComparator:^NSComparisonResult(NSURL *a, NSURL *b) {
        return [a.path caseInsensitiveCompare:b.path];
    }];
    for (NSURL *fileURL in sorted)
    {
        BOOL isDir = NO;
        if ([fm fileExistsAtPath:fileURL.path isDirectory:&isDir] && !isDir)
        {
            NSData *data = [NSData dataWithContentsOfURL:fileURL];
            if (data) {
                ValencedLog(@"Running preload: %@", fileURL.lastPathComponent);
                [JSI evaluate:data tag:@"valenced:preload" runtime:runtime];
            }
        }
    }
}

static NSData *loadFallbackBundle(void)
{
    NSArray<NSBundle *> *bundles = @[
        [NSBundle bundleForClass:[PluginLoader class]],
        [NSBundle mainBundle],
        [NSBundle bundleWithIdentifier:@"app.valenced.tweak"],
    ];
    for (NSBundle *b in bundles) {
        if (!b) continue;
        NSString *path = [b pathForResource:@"valenced" ofType:@"bundle"];
        if (path) {
            NSData *d = [NSData dataWithContentsOfFile:path];
            if (d.length > 0) {
                ValencedLog(@"Using fallback bundle at %@", path);
                return d;
            }
        }
        path = [b pathForResource:@"revenge" ofType:@"bundle"];
        if (path) {
            NSData *d = [NSData dataWithContentsOfFile:path];
            if (d.length > 0) {
                ValencedLog(@"Using fallback bundle at %@", path);
                return d;
            }
        }
    }
    Dl_info info;
    if (dladdr((void *)&loadFallbackBundle, &info) && info.dli_fname) {
        NSString *dylibPath = [NSString stringWithUTF8String:info.dli_fname];
        NSString *dir = [dylibPath stringByDeletingLastPathComponent];
        for (NSString *name in @[@"valenced.bundle", @"revenge.bundle"]) {
            NSString *p = [dir stringByAppendingPathComponent:name];
            NSData *d = [NSData dataWithContentsOfFile:p];
            if (d.length > 0) {
                ValencedLog(@"Using fallback bundle at %@", p);
                return d;
            }
        }
    }
    return nil;
}

%hook RCTHost

- (void)instance:(id)instance didInitializeRuntime:(facebook::jsi::Runtime &)runtime
{
    SetJsiRuntime(&runtime);
    SetJsiInstance(instance);
    gRuntime = &runtime;

    Updater *updater = [Updater sharedUpdater];
    [updater initConfig];

    @try {
        injectPreBundle(runtime);
    } @catch (NSException *e) {
        ValencedLog(@"injectPreBundle exception: %@ %@", e.name, e.reason);
    }
    @try {
        [[PluginLoader sharedLoader] loadPlugins];
    } @catch (NSException *e) {
        ValencedLog(@"loadPlugins exception: %@ %@", e.name, e.reason);
    }

    NSURL *valencedDir = getValencedDirectory();
    NSFileManager *fm = [NSFileManager defaultManager];
    NSURL *bundleFileURL = [updater bundleFile];
    BOOL hasCache = [fm fileExistsAtPath:bundleFileURL.path];
    if (!hasCache) {
        ValencedLog(@"No cached bundle, fetching synchronously (10s timeout)...");
        [updater downloadScriptUserInitiated:NO showDialog:NO];
    } else {
        ValencedLog(@"Cached bundle exists, checking for update (5s timeout)...");
        [updater downloadScriptUserInitiated:NO showDialog:NO];
    }

    %orig;

    @try {
        executePreloads(runtime, valencedDir);

        NSData *bundle = [NSData dataWithContentsOfURL:bundleFileURL];
        if (bundle && bundle.length > 0)
        {
            ValencedLog(@"Loaded bundle from disk (%lu bytes)", (unsigned long)bundle.length);
            [JSI evaluate:bundle tag:@"valenced:bundle" runtime:runtime];
        }
        else
        {
            ValencedLog(@"No cached bundle after download, trying fallback");
            NSData *fallback = loadFallbackBundle();
            if (fallback && fallback.length > 0) {
                ValencedLog(@"Loaded fallback bundle (%lu bytes)", (unsigned long)fallback.length);
                [JSI evaluate:fallback tag:@"valenced:bundle:fallback" runtime:runtime];
            } else {
                ValencedLog(@"No bundle available at all");
            }
        }
    } @catch (NSException *e) {
        ValencedLog(@"sync injection exception: %@ %@", e.name, e.reason);
    }
}

%end

%hook UIWindow

- (void)motionBegan:(UIEventSubtype)motion withEvent:(UIEvent *)event
{
    if (motion == UIEventSubtypeMotionShake)
    {
        isShaking      = YES;
        shakeStartTime = [[NSDate date] timeIntervalSince1970];
    }
    %orig;
}

- (void)motionEnded:(UIEventSubtype)motion withEvent:(UIEvent *)event
{
    if (motion == UIEventSubtypeMotionShake && isShaking)
    {
        NSTimeInterval shakeDuration = [[NSDate date] timeIntervalSince1970] - shakeStartTime;
        if (shakeDuration >= 0.5 && shakeDuration <= 2.0)
            dispatch_async(dispatch_get_main_queue(), ^{ showRecoveryAlert(nil); });
        isShaking = NO;
    }
    %orig;
}

%end

%ctor
{
    @autoreleasepool
    {
        %init;
        RegisterBridgeMethods();
    }
}
