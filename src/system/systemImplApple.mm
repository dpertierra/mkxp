//
//  systemImplApple.m
//  Player
//
//  Created by ゾロアーク on 11/22/20.
//

#import <AppKit/AppKit.h>
#import <Metal/Metal.h>
#import <QuartzCore/CAMetalLayer.h>

#import <SDL_syswm.h>

#import <sys/sysctl.h>
#import "system.h"
#import "SettingsMenuController.h"

void getWindowBackingSize(SDL_Window *win, int *w, int *h) {
    @autoreleasepool {
        SDL_SysWMinfo info{};
        if (SDL_GetWindowWMInfo(win, &info) && info.subsystem == SDL_SYSWM_COCOA) {
            NSView *view = info.info.cocoa.window.contentView;
            NSSize backing = [view convertSizeToBacking:view.bounds.size];
            *w = (int)backing.width;
            *h = (int)backing.height;
        } else {
            SDL_GL_GetDrawableSize(win, w, h);
        }
    }
}

static void applyScaleToLayerTree(CALayer *layer, CGFloat scale) {
    if ([layer isKindOfClass:[CAMetalLayer class]]) {
        CAMetalLayer *metal = (CAMetalLayer *)layer;
        metal.contentsScale = scale;
        CGSize b = metal.bounds.size;
        metal.drawableSize = CGSizeMake(b.width * scale, b.height * scale);
    }
    for (CALayer *sub in layer.sublayers)
        applyScaleToLayerTree(sub, scale);
}

bool syncWindowBackingScale(SDL_Window *win) {
    @autoreleasepool {
        SDL_SysWMinfo info{};
        if (!SDL_GetWindowWMInfo(win, &info) || info.subsystem != SDL_SYSWM_COCOA)
            return false;

        NSView *view = info.info.cocoa.window.contentView;
        CGFloat scale = info.info.cocoa.window.backingScaleFactor;
        if (!view.layer)
            return false;

        /* Nothing to do if the root layer already matches */
        if (view.layer.contentsScale == scale)
            return false;

        applyScaleToLayerTree(view.layer, scale);
        return true;
    }
}

std::string systemImpl::getSystemLanguage() {
    @autoreleasepool {
        NSString *languageCode = NSLocale.currentLocale.languageCode;
        NSString *countryCode = NSLocale.currentLocale.countryCode;
        return std::string([NSString stringWithFormat:@"%@_%@", languageCode, countryCode].UTF8String);
    }
}

std::string systemImpl::getUserName() {
    @autoreleasepool {
        return std::string(NSUserName().UTF8String);
    }
}

int systemImpl::getScalingFactor() {
    return NSApplication.sharedApplication.mainWindow.backingScaleFactor;
}

bool systemImpl::isWine() {
    return false;
}

bool systemImpl::isRosetta() {
    int translated = 0;
    size_t size = sizeof(translated);
    int result = sysctlbyname("sysctl.proc_translated", &translated, &size, NULL, 0);
    
    if (result == -1)
        return false;
    
    return translated;
}

systemImpl::WineHostType systemImpl::getRealHostType() {
    return WineHostType::Mac;
}


// constant, if it's not nil then just raise the menu instead
SettingsMenu *smenu = nil;
void openSettingsWindow() {
    if (smenu == nil) {
        smenu = [SettingsMenu openWindow];
        return;
    }
    [smenu raise];
}

bool isMetalSupported() {
    if (@available(macOS 10.13.0, *)) {
        return MTLCreateSystemDefaultDevice() != nil;
    }
    return false;
}

std::string getPlistValue(const char *key) {
    @autoreleasepool {
        NSString *hash = [[NSBundle mainBundle] objectForInfoDictionaryKey:@(key)];
        if (hash != nil) {
            return std::string(hash.UTF8String);
        }
        return "";
    }
}
