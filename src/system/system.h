//
//  system.h
//  Player
//
//  Created by ゾロアーク on 11/22/20.
//

#ifndef system_h
#define system_h

#include <string>

#define MKXPZ_PLATFORM_WINDOWS 0
#define MKXPZ_PLATFORM_MACOS 1
#define MKXPZ_PLATFORM_LINUX 2

#ifdef __WIN32__
#define MKXPZ_PLATFORM MKXPZ_PLATFORM_WINDOWS
#elif defined __APPLE__
#define MKXPZ_PLATFORM MKXPZ_PLATFORM_MACOS
#elif defined __linux__
#define MKXPZ_PLATFORM MKXPZ_PLATFORM_LINUX
#else
#error "Can't identify platform."
#endif

namespace systemImpl {
enum WineHostType {
    Windows,
    Linux,
    Mac
};
std::string getSystemLanguage();
std::string getUserName();
int getScalingFactor();

bool isWine();
bool isRosetta();
WineHostType getRealHostType();
}

#ifdef MKXPZ_BUILD_XCODE
struct SDL_Window;
std::string getPlistValue(const char *key);
void openSettingsWindow();
bool isMetalSupported();
/* Actual drawable size in pixels; SDL_GL_GetDrawableSize misreports
 * Retina backing sizes when using ANGLE on macOS */
void getWindowBackingSize(SDL_Window *win, int *w, int *h);
/* Push the window's current backing scale onto every CAMetalLayer (ANGLE's
 * included) so the Metal drawable follows the window across displays with
 * different scale factors. Returns true if the scale actually changed. */
bool syncWindowBackingScale(SDL_Window *win);
#endif

namespace mkxp_sys = systemImpl;

#endif /* system_h */
