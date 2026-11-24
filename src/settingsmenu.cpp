/*
** settingsmenu.cpp
**
** This file is part of mkxp.
**
** Copyright (C) 2014 - 2021 Amaryllis Kulla <ancurio@mapleshrine.eu>
**
** mkxp is free software: you can redistribute it and/or modify
** it under the terms of the GNU General Public License as published by
** the Free Software Foundation, either version 2 of the License, or
** (at your option) any later version.
**
** mkxp is distributed in the hope that it will be useful,
** but WITHOUT ANY WARRANTY; without even the implied warranty of
** MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
** GNU General Public License for more details.
**
** You should have received a copy of the GNU General Public License
** along with mkxp.  If not, see <http://www.gnu.org/licenses/>.
*/

#include "settingsmenu.h"

#include <SDL_video.h>
#include <SDL_ttf.h>
#include <SDL_surface.h>
#include <SDL_keyboard.h>
#include <SDL_rect.h>
#include <SDL_timer.h>

#include "keybindings.h"
#include "eventthread.h"
#include "sharedstate.h"
#include "font.h"
#include "input.h"
#include "etc-internal.h"
#include "util.h"

#include <algorithm>
#include <assert.h>

const Vec2i winSize(960, 356);

const uint8_t cBgNorm = 50;
const uint8_t cBgDark = 20;
const uint8_t cLine = 0;
const uint8_t cText = 255;

const uint8_t frameWidth = 4;
const uint8_t fontSize = 15;

static bool pointInRect(const SDL_Rect &r, int x, int y)
{
	return (x >= r.x && x <= r.x+r.w && y >= r.y && y <= r.y+r.h);
}

typedef SettingsMenuPrivate SMP;

#define BTN_STRING_CUSTOM(btn, name) { Input:: btn, name }
#define BTN_STRING(btn) BTN_STRING_CUSTOM(btn, #btn)
struct VButton
{
	Input::ButtonCode code;
	const char *str;
} static vButtons[] =
{
	BTN_STRING(Up),
	BTN_STRING(Down),
	BTN_STRING(L),
	BTN_STRING(Left),
	BTN_STRING(Right),
	BTN_STRING(R),
	BTN_STRING(A),
	BTN_STRING(B),
  BTN_STRING(C),
	BTN_STRING(X),
	BTN_STRING(Y),
  BTN_STRING(Z)
};

static elementsN(vButtons);

/* One entry per action shown in the menu: the 12 base ones, then the
 * remappable built-in keys and the game's custom actions */
struct MenuButton
{
	Input::ButtonCode code;
	std::string str;
};

/* Actions per page: 3 columns x 4 rows */
const size_t pageSize = 12;

/* Human readable string representation */
std::string sourceDescString(const SourceDesc &src)
{
	char buf[128];
	//char pos;

	switch (src.type)
	{
	case Invalid:
		return std::string();

	case Key:
	{
		if (src.d.scan == SDL_SCANCODE_LSHIFT)
			return "Shift";

		SDL_Keycode key = SDL_GetKeyFromScancode(src.d.scan);
		const char *str = SDL_GetKeyName(key);

		if (*str == '\0')
			return "Unknown key";
		else
			return str;
	}
	case CButton:
		snprintf(buf, sizeof(buf), "%s", shState->input().getButtonName(src.d.cb));
		return buf;

	case CAxis:
		snprintf(buf, sizeof(buf), "%s%c",
		         shState->input().getAxisName(src.d.ca.axis), src.d.ca.dir == Negative ? '-' : '+');
		return buf;
	}

	assert(!"unreachable");
	return "";
}

struct Widget
{
	/* Widgets have a static size and position,
	 * defined at creation */
	Widget(SMP *p, const IntRect &rect);

	/* Public methods take coordinates in global
	 * window coordinates */
	bool hit(int x, int y);
	void draw(SDL_Surface *surf);
	void motion(int x, int y);
	void leave();
	void click(int x, int y, uint8_t button);

	/* Window-space rectangle, used for controller/keyboard navigation */
	IntRect area() const { return rect; }

protected:
	SMP *p;
	IntRect rect;

	/* Protected abstract methods are called with
	 * widget-local coordinates */
	virtual void drawHandler(SDL_Surface *surf) = 0;
	virtual void motionHandler(int x, int y) = 0;
	virtual void leaveHandler() = 0;
	virtual void clickHandler(int x, int y, uint8_t button) = 0;
};

struct BindingWidget : Widget
{
	MenuButton vb;
	/* Source slots */
	SourceDesc src[4];
	/* Flag indicating whether a slot source is used
	 * for multiple button targets (red indicator) */
	bool dupFlag[4];

	BindingWidget(const MenuButton &vb, SMP *p, const IntRect &rect)
	    : Widget(p, rect),
	      vb(vb),
	      hoveredCell(-1)
	{
        
    }

	void appendBindings(BDescVec &d) const;

	/* Window-space rectangle of slot cell i (0..3) */
	IntRect cellRect(int i) const;

protected:
	int hoveredCell;
	void setHoveredCell(int cell);
	/* Get the slot cell index that contains (x,y),
	 * or -1 if none */
	int cellIndex(int x, int y) const;

	void drawHandler(SDL_Surface *surf);
	void motionHandler(int x, int y);
	void leaveHandler();
	void clickHandler(int x, int y, uint8_t button);
};

struct Button : Widget
{
	typedef void (SMP::*Callback)();

	const char *str;
	Callback cb;

	Button(SMP *p, const IntRect &rect,
	       const char *str, Callback cb)
	    : Widget(p, rect),
	      str(str), cb(cb), hovered(false)
	{}

protected:
	bool hovered;

	void setHovered(bool val);

	void drawHandler(SDL_Surface *surf);
	void motionHandler(int, int);
	void leaveHandler();
	void clickHandler(int, int, uint8_t button);
};

struct Label : Widget
{
	const char *str;
	SDL_Color c;

	Label() : Widget(0, IntRect()) {}

	Label(SMP *p, const IntRect &rect,
	      const char *str, uint8_t r, uint8_t g, uint8_t b)
	    : Widget(p, rect),
	      str(str),
	      visible(true)
	{
		c.r = r;
		c.g = g;
		c.b = b;
		c.a = 255;
	}

	void setVisible(bool val);

protected:
	bool visible;

	void drawHandler(SDL_Surface *surf);
	void motionHandler(int, int) {}
	void leaveHandler() {}
	void clickHandler(int, int, uint8_t) {}
};

enum State
{
	Idle,
	AwaitingInput
};

enum Justification
{
	Left,
	Center
};

struct SettingsMenuPrivate
{
	State state;

	/* Necessary to decide which window gets to
	 * process joystick events */
	bool hasFocus;

	/* Tell the outer EventThread to destroy us */
	bool destroyReq;

	/* Offset added for all draw calls */
	Vec2i drawOff;

	SDL_Window *window;
	SDL_Surface *winSurf;
	uint32_t winID;

	TTF_Font *font;
	SDL_PixelFormat *rgb;

	RGSSThreadData &rtData;

	std::vector<BindingWidget> bWidgets;
	std::vector<Button> buttons;
	Label infoLabel;
	Label dupWarnLabel;
	std::vector<Widget*> widgets;
	Widget *hovered;

	/* Paging: only the binding widgets of the current page are in `widgets` */
	size_t page, pageCount;
	char pageStr[32];
	Label pageLabel;
	size_t firstButton; /* index in `buttons` of the page buttons, if any */

	SourceDesc *captureDesc;
	const char *captureName;

	/* Controller / keyboard navigation: every clickable spot on the page
	 * (binding cells and buttons), and the selected one */
	struct NavTarget
	{
		Widget *w;
		IntRect r;
	};
	std::vector<NavTarget> navTargets;
	size_t navSel;
	/* Stick direction already acted on, per axis (-1, 0, 1) */
	int stickDir[2];

	/* A controller can not cancel a capture (any button gets bound),
	 * so it times out instead */
	SDL_TimerID captureTimer;

	/* Esc / pad B during a capture: a short press binds it, holding it
	 * cancels. 0 = none pending, 1 = Esc held, 2 = pad B held */
	int holdPending;
	SDL_TimerID holdTimer;
	static Uint32 captureTimeoutEvent;

	SettingsMenuPrivate(RGSSThreadData &rtData)
	    : rtData(rtData), navSel(0), stickDir{0, 0}, captureTimer(0), holdPending(0), holdTimer(0)
	{}

	SDL_Surface *createSurface(int w, int h)
	{
		int bpp;
		Uint32 rMask, gMask, bMask, aMask;
		SDL_PixelFormatEnumToMasks(SDL_PIXELFORMAT_ABGR8888,
		                           &bpp, &rMask, &gMask, &bMask, &aMask);

		return SDL_CreateRGBSurface(0, w, h, bpp, rMask, gMask, bMask, 0);
	}

	void fillSurface(SDL_Surface *surf, uint8_t grey)
	{
		SDL_FillRect(surf, 0, SDL_MapRGBA(rgb, grey, grey, grey, 255));
	}

	void fillRect(SDL_Surface *surf,
	              int x, int y, int w, int h,
	              uint8_t r, uint8_t g, uint8_t b)
	{
		SDL_Rect rect = { drawOff.x+x, drawOff.y+y, w, h };
		SDL_FillRect(surf, &rect, SDL_MapRGB(rgb, r, g, b));
	}

	void fillRect(SDL_Surface *surf, uint8_t grey,
	              int x, int y, int w, int h)
	{
		fillRect(surf, x, y, w, h, grey, grey, grey);
	}

	void strokeLineH(SDL_Surface *surf, uint8_t grey,
	                 int x, int y, int length, int width)
	{
		fillRect(surf, grey, x, y-width/2, length, width);
	}

	void strokeLineV(SDL_Surface *surf, uint8_t grey,
	                 int x, int y, int length, int width)
	{
		fillRect(surf, grey, x-width/2, y, width, length);
	}

	void strokeLineH(SDL_Surface *surf, uint8_t r, uint8_t g, uint8_t b,
	                 int x, int y, int length, int width)
	{
		fillRect(surf, r, g, b, x, y-width/2, length, width);
	}

	void strokeLineV(SDL_Surface *surf, uint8_t r, uint8_t g, uint8_t b,
	                 int x, int y, int length, int width)
	{
		fillRect(surf, r, g, b, x-width/2, y, width, length);
	}

	void strokeRect(SDL_Surface *surf, uint8_t grey,
	                int x, int y, int w, int h,
	                int lineW)
	{
		strokeLineH(surf, grey, x, y, w, lineW);
		strokeLineH(surf, grey, x, y+h, w, lineW);

		strokeLineV(surf, grey, x, y, h, lineW);
		strokeLineV(surf, grey, x+w, y, h, lineW);
	}

	void strokeRectInner(SDL_Surface *surf,
	                     int x, int y, int w, int h, int lineW,
	                     uint8_t r, uint8_t g, uint8_t b)
	{
		fillRect(surf, x, y, w, lineW, r, g, b);
		fillRect(surf, x, y+h-lineW, w, lineW, r, g, b);
		fillRect(surf, x, y, lineW, h, r, g, b);
		fillRect(surf, x+w-lineW, y, lineW, h, r, g ,b);
	}

	void strokeRectInner(SDL_Surface *surf, uint8_t grey,
	                     int x, int y, int w, int h,
	                     int lineW)
	{
		strokeRectInner(surf, x, y, w, h, lineW, grey, grey, grey);
	}

	void applyFontStyle(bool bold)
	{
		if (bold)
			TTF_SetFontStyle(font, TTF_STYLE_BOLD);
		else
			TTF_SetFontStyle(font, TTF_STYLE_NORMAL);
	}

	SDL_Surface *createTextSurface(const char *str, bool bold)
	{
		SDL_Color c = { cText, cText, cText, 255 };
		applyFontStyle(bold);

		return TTF_RenderUTF8_Blended(font, str, c);
	}

	SDL_Surface *createTextSurface(const char *str, SDL_Color c,
	                               bool bold)
	{
		applyFontStyle(bold);
		return TTF_RenderUTF8_Blended(font, str, c);
	}

	/* Horizontally centered */
	void blitTextSurf(SDL_Surface *surf, int x, int y,
	                  int alignW, SDL_Surface *txtSurf,
	                  Justification just)
	{
		SDL_Rect dstRect;
		dstRect.x = drawOff.x;
		dstRect.y = drawOff.y + y - txtSurf->h / 2;
		dstRect.w = txtSurf->w;
		dstRect.h = txtSurf->h;

		switch (just)
		{
		case Left:
			dstRect.x += x;
			break;
		case Center:
			dstRect.x += x - (txtSurf->w - alignW) / 2;
			break;
		}

		if (txtSurf->w <= alignW)
		{
			SDL_BlitSurface(txtSurf, 0, surf, &dstRect);
		}
		else
		{
			dstRect.w = alignW;
			dstRect.x = x;
			SDL_BlitScaled(txtSurf, 0, surf, &dstRect);
		}
	}

	void drawText(SDL_Surface *surf, const char *str,
	              int x, int y, int alignW,
	              Justification just, SDL_Color c, bool bold = false)
	{
		SDL_Surface *txt = createTextSurface(str, c, bold);
		blitTextSurf(surf, x, y, alignW, txt, just);
		SDL_FreeSurface(txt);
	}

	void drawText(SDL_Surface *surf, const char *str,
	              int x, int y, int alignW,
	              Justification just, bool bold = false)
	{
		SDL_Color c = { cText, cText, cText, 255 };
		drawText(surf, str, x, y, alignW, just, c, bold);
	}

	void setupBindingData(const BDescVec &d)
	{
		std::vector<size_t> slotI(bWidgets.size(), 0);

		for (size_t i = 0; i < bWidgets.size(); ++i)
			for (size_t j = 0; j < 4; ++j)
				bWidgets[i].src[j].type = Invalid;

		for (size_t i = 0; i < d.size(); ++i)
		{
			const BindingDesc &desc = d[i];
			const Input::ButtonCode trg = desc.target;

			size_t j;
			for (j = 0; j < bWidgets.size(); ++j)
				if (bWidgets[j].vb.code == trg)
					break;

			/* Binding for an action that is not listed */
			if (j == bWidgets.size())
				continue;

			size_t &slot = slotI[j];
			BindingWidget &w = bWidgets[j];

			if (slot == 4)
				continue;

			w.src[slot++] = desc.src;
		}
	}

	/* Modifiers coexist with other actions on the same key by design
	 * (e.g. Shift is both A and Shift), so they never raise the warning */
	static bool isModifier(Input::ButtonCode code)
	{
		return code == Input::Shift || code == Input::Ctrl || code == Input::Alt;
	}

	void updateDuplicateStatus()
	{
		for (size_t i = 0; i < bWidgets.size(); ++i)
			for (size_t j = 0; j < 4; ++j)
				bWidgets[i].dupFlag[j] = false;

		bool haveDup = false;

		for (size_t i = 0; i < bWidgets.size(); ++i)
		{
			for (size_t j = 0; j < 4; ++j)
			{
				const SourceDesc &src = bWidgets[i].src[j];

				if (src.type == Invalid)
					continue;

				for (size_t k = 0; k < bWidgets.size(); ++k)
				{
					if (k == i)
						continue;

					if (isModifier(bWidgets[i].vb.code) || isModifier(bWidgets[k].vb.code))
						continue;

					for (size_t l = 0; l < 4; ++l)
					{
						if (bWidgets[k].src[l] != src)
							continue;

						bWidgets[i].dupFlag[j] = true;
						bWidgets[k].dupFlag[l] = true;
						haveDup = true;
					}
				}
			}
		}

		dupWarnLabel.setVisible(haveDup);
	}

	void redraw()
	{
		fillSurface(winSurf, cBgNorm);

		for (size_t i = 0; i < widgets.size(); ++i)
			widgets[i]->draw(winSurf);

		if (navSel < navTargets.size())
		{
			const IntRect &r = navTargets[navSel].r;
			drawOff = Vec2i();
			strokeRectInner(winSurf, r.x+2, r.y+2, r.w-4, r.h-4, 2, 255, 200, 0);
		}

		if (state == AwaitingInput)
		{
			char buf[192];
			snprintf(buf, sizeof(buf), "Press key or joystick button for \"%s\" (hold Esc / B or wait 5 s to cancel)", captureName);

			drawOff = Vec2i();

			SDL_Surface *dark = createSurface(winSize.x, winSize.y);
			fillSurface(dark, 0);
			SDL_SetSurfaceAlphaMod(dark, 128);
			SDL_SetSurfaceBlendMode(dark, SDL_BLENDMODE_BLEND);
			SDL_Surface *txt = createTextSurface(buf, false);

			SDL_BlitSurface(dark, 0, winSurf, 0);

			SDL_Rect fill;
			fill.x = (winSize.x - txt->w - 20) / 2;
			fill.y = (winSize.y - txt->h - 20) / 2;
			fill.w = txt->w + 20;
			fill.h = txt->h + 20;

			fillRect(winSurf, cBgNorm, fill.x, fill.y, fill.w, fill.h);
			strokeRectInner(winSurf, cLine, fill.x, fill.y, fill.w, fill.h, 2);

			fill.x += 10;
			fill.y += 10;
			fill.w = txt->w;
			fill.h = txt->h;

			SDL_BlitSurface(txt, 0, winSurf, &fill);

			SDL_FreeSurface(txt);
			SDL_FreeSurface(dark);
		}

		SDL_UpdateWindowSurface(window);
	}

	Widget *findWidget(int x, int y)
	{
		Widget *w = 0;

		for (size_t i = 0; i < widgets.size(); ++i)
			if (widgets[i]->hit(x, y))
			{
				w = widgets[i];
				break;
			}

		return w;
	}

	void onClick(const SDL_MouseButtonEvent &e)
	{
		if (e.button != SDL_BUTTON_LEFT && e.button != SDL_BUTTON_RIGHT)
			return;

		if (state == AwaitingInput)
		{
			stopCaptureTimer();
			state = Idle;
			redraw();
			return;
		}

		Widget *w = findWidget(e.x, e.y);

		if (w)
			w->click(e.x, e.y, e.button);
	}

	void onMotion(const SDL_MouseMotionEvent &e)
	{
		if (state == AwaitingInput)
			return;

		Widget *w = findWidget(e.x, e.y);

		if (w != hovered)
		{
			if (hovered)
				hovered->leave();
			hovered = w;
		}

		if (hovered)
			hovered->motion(e.x, e.y);
	}

	bool onCaptureInputEvent(const SDL_Event &event)
	{
		assert(captureDesc);
		SourceDesc &desc = *captureDesc;

		switch (event.type)
		{
		case SDL_KEYDOWN:
			desc.type = Key;
			desc.d.scan = event.key.keysym.scancode;

			/* Special case aliases */
			if (desc.d.scan == SDL_SCANCODE_RSHIFT)
				desc.d.scan = SDL_SCANCODE_LSHIFT;

			if (desc.d.scan == SDL_SCANCODE_KP_ENTER)
				desc.d.scan = SDL_SCANCODE_RETURN;

			break;

		case SDL_CONTROLLERBUTTONDOWN:
			desc.type = CButton;
			desc.d.cb = (SDL_GameControllerButton)event.cbutton.button;
			break;

		case SDL_CONTROLLERAXISMOTION:
		{
			int v = event.caxis.value;

			/* Only register if pushed halfway through */
			if (v > -JAXIS_THRESHOLD && v < JAXIS_THRESHOLD)
				return true;

			desc.type = CAxis;
			desc.d.ca.axis = (SDL_GameControllerAxis)event.caxis.axis;
			desc.d.ca.dir = v < 0 ? Negative : Positive;
			break;
		}
		default:
			return false;
		}

		stopCaptureTimer();
		captureDesc = 0;
		state = Idle;
		updateDuplicateStatus();
		redraw();

		return true;
	}

	void onBWidgetCellClicked(SourceDesc &desc, const char *str, uint8_t button)
	{
		if (state != Idle)
			return;

		if (button == SDL_BUTTON_LEFT)
		{
			captureDesc = &desc;
			captureName = str;
			state = AwaitingInput;
			stopCaptureTimer();
			captureTimer = SDL_AddTimer(5000, onCaptureTimeout, 0);
		}
		else /* e.button == SDL_BUTTON_RIGHT */
		{
			/* Clear binding */
			desc.type = Invalid;
		}

		updateDuplicateStatus();
		redraw();
	}

	void setPage(size_t newPage)
	{
		page = newPage;

		if (hovered)
		{
			hovered->leave();
			hovered = 0;
		}

		widgets.clear();

		for (size_t i = page*pageSize; i < bWidgets.size() && i < (page+1)*pageSize; ++i)
			widgets.push_back(&bWidgets[i]);

		for (size_t i = 0; i < buttons.size(); ++i)
			widgets.push_back(&buttons[i]);

		widgets.push_back(&infoLabel);
		widgets.push_back(&dupWarnLabel);

		if (pageCount > 1)
		{
			snprintf(pageStr, sizeof(pageStr), "Page %zu/%zu", page+1, pageCount);
			widgets.push_back(&pageLabel);
		}

		/* Keep a selected button (e.g. Next) selected across pages */
		Widget *prevSel = navSel < navTargets.size() ? navTargets[navSel].w : 0;
		bool keepButton = false;
		for (size_t i = 0; i < buttons.size(); ++i)
			keepButton = keepButton || prevSel == &buttons[i];

		navTargets.clear();
		navSel = 0;

		for (size_t i = page*pageSize; i < bWidgets.size() && i < (page+1)*pageSize; ++i)
			for (int c = 0; c < 4; ++c)
				navTargets.push_back({ &bWidgets[i], bWidgets[i].cellRect(c) });

		for (size_t i = 0; i < buttons.size(); ++i)
		{
			if (keepButton && prevSel == &buttons[i])
				navSel = navTargets.size();
			navTargets.push_back({ &buttons[i], buttons[i].area() });
		}

		redraw();
	}

	/* Pick the nearest target in direction (dx, dy); off-axis distance
	 * weighs more so moving stays in the same row/column when possible */
	void moveSelection(int dx, int dy)
	{
		if (state != Idle || navTargets.empty())
			return;

		const IntRect &cur = navTargets[navSel].r;
		const int cx = cur.x + cur.w/2, cy = cur.y + cur.h/2;
		long best = -1;
		size_t bestI = navSel;

		for (size_t i = 0; i < navTargets.size(); ++i)
		{
			const IntRect &r = navTargets[i].r;
			const int vx = r.x + r.w/2 - cx, vy = r.y + r.h/2 - cy;
			const int along = vx*dx + vy*dy;
			const int across = abs(vx*dy - vy*dx);

			if (along <= 0)
				continue;

			const long score = along + 3L*across;
			if (best < 0 || score < best)
			{
				best = score;
				bestI = i;
			}
		}

		navSel = bestI;
		redraw();
	}

	/* Same as clicking the selected target: left binds a cell or presses a
	 * button, right clears a cell */
	void activateSelection(uint8_t button)
	{
		if (state != Idle || navSel >= navTargets.size())
			return;

		const IntRect &r = navTargets[navSel].r;
		navTargets[navSel].w->click(r.x + r.w/2, r.y + r.h/2, button);
	}

	void onPadButton(uint8_t b)
	{
		switch (b)
		{
		case SDL_CONTROLLER_BUTTON_DPAD_UP:    moveSelection(0, -1); break;
		case SDL_CONTROLLER_BUTTON_DPAD_DOWN:  moveSelection(0, 1);  break;
		case SDL_CONTROLLER_BUTTON_DPAD_LEFT:  moveSelection(-1, 0); break;
		case SDL_CONTROLLER_BUTTON_DPAD_RIGHT: moveSelection(1, 0);  break;
		case SDL_CONTROLLER_BUTTON_A:          activateSelection(SDL_BUTTON_LEFT);  break;
		case SDL_CONTROLLER_BUTTON_X:          activateSelection(SDL_BUTTON_RIGHT); break;
		case SDL_CONTROLLER_BUTTON_LEFTSHOULDER:  onPrevPage(); break;
		case SDL_CONTROLLER_BUTTON_RIGHTSHOULDER: onNextPage(); break;
		case SDL_CONTROLLER_BUTTON_START:      onAccept(); break;
		case SDL_CONTROLLER_BUTTON_B:          onCancel(); break;
		default: break;
		}
	}

	/* Remember where the left stick is without acting on it. Used while
	 * capturing, so the push that was just bound does not also move the
	 * selection once the capture ends */
	void trackStick(uint8_t axis, int value)
	{
		if (axis <= SDL_CONTROLLER_AXIS_LEFTY)
			stickDir[axis] = value > JAXIS_THRESHOLD ? 1 : value < -JAXIS_THRESHOLD ? -1 : 0;
	}

	/* Left stick: one step per push; it has to return to center to move again */
	void onPadAxis(uint8_t axis, int value)
	{
		if (axis > SDL_CONTROLLER_AXIS_LEFTY)
			return;

		const int dir = value > JAXIS_THRESHOLD ? 1 : value < -JAXIS_THRESHOLD ? -1 : 0;
		if (dir == stickDir[axis])
			return;

		stickDir[axis] = dir;
		if (dir == 0)
			return;

		if (axis == SDL_CONTROLLER_AXIS_LEFTX)
			moveSelection(dir, 0);
		else
			moveSelection(0, dir);
	}

	/* param: 0 = capture timed out, 1 = Esc / pad B held long enough */
	static Uint32 onCaptureTimeout(Uint32, void *param)
	{
		SDL_Event e = {};
		e.type = captureTimeoutEvent;
		e.user.code = (int) (intptr_t) param;
		SDL_PushEvent(&e);
		return 0;
	}

	void stopCaptureTimer()
	{
		if (captureTimer)
			SDL_RemoveTimer(captureTimer);
		if (holdTimer)
			SDL_RemoveTimer(holdTimer);
		captureTimer = holdTimer = 0;
		holdPending = 0;
	}

	void cancelCapture()
	{
		stopCaptureTimer();
		captureDesc = 0;
		state = Idle;
		redraw();
	}

	void commitCapture(const SourceDesc &src)
	{
		*captureDesc = src;
		stopCaptureTimer();
		captureDesc = 0;
		state = Idle;
		updateDuplicateStatus();
		redraw();
	}

	void startHold(int which)
	{
		holdPending = which;
		if (!holdTimer)
			holdTimer = SDL_AddTimer(1000, onCaptureTimeout, (void *) (intptr_t) 1);
	}

	void onPrevPage()
	{
		if (state == Idle && page > 0)
			setPage(page-1);
	}

	void onNextPage()
	{
		if (state == Idle && page+1 < pageCount)
			setPage(page+1);
	}

	void onResetToDefault()
	{
		setupBindingData(genDefaultBindings(rtData.config));
		updateDuplicateStatus();
		redraw();
	}

	void onAccept()
	{
		BDescVec binds;

		for (size_t i = 0; i < bWidgets.size(); ++i)
			bWidgets[i].appendBindings(binds);

		rtData.bindingUpdateMsg.post(binds);

		/* Store the key bindings to disk as well to prevent config loss */
		storeBindings(binds, rtData.config);

		destroyReq = true;
	}

	void onCancel()
	{
		destroyReq = true;
	}
};

Uint32 SettingsMenuPrivate::captureTimeoutEvent = (Uint32) -1;

Widget::Widget(SMP *p, const IntRect &rect)
    : p(p), rect(rect)
{}

bool Widget::hit(int x, int y)
{
	return pointInRect(rect, x, y);
}

void Widget::draw(SDL_Surface *surf)
{
	Vec2i prev = p->drawOff;
	p->drawOff = rect.pos();
	drawHandler(surf);
	p->drawOff = prev;
}

void Widget::motion(int x, int y)
{
	motionHandler(x - rect.x, y - rect.y);
}

void Widget::leave()
{
	leaveHandler();
}

void Widget::click(int x, int y, uint8_t button)
{
	clickHandler(x - rect.x, y - rect.y, button);
}

/* Ratio of cell area to total widget width */
#define BW_CELL_R 0.55f

void BindingWidget::drawHandler(SDL_Surface *surf)
{
	const int cellW = (rect.w*BW_CELL_R) / 2;
	const int cellH = rect.h / 2;
	const int cellOffX = (1.0f-BW_CELL_R) * rect.w;

	const int cellOff[] =
	{
	    cellOffX, 1,
	    cellOffX+cellW, 1,
	    cellOffX, cellH,
	    cellOffX+cellW, cellH
	};

	const int lbOff[] =
	{
	    0, cellH / 2,
	    cellW, cellH / 2,
	    0, cellH + cellH / 2,
	    cellW, cellH + cellH / 2
	};

	/* Hovered cell background */
	if (hoveredCell != -1)
		p->fillRect(surf, cBgDark,
		            cellOff[hoveredCell*2], cellOff[hoveredCell*2+1],
		            cellW, cellH);

	/* Frame */
	p->strokeRectInner(surf, cLine, 0, 0, rect.w, rect.h, 2);

	/* Virtual button name */
	p->drawText(surf, vb.str.c_str(), 1, rect.h/2, cellOffX, Center, true);

	/* Cell frames */
	p->strokeLineV(surf, cLine, cellOffX, 0, rect.h, 2);
	p->strokeLineV(surf, cLine, cellOffX+cellW, 0, rect.h, 2);
	p->strokeLineH(surf, cLine, cellOffX, cellH, cellW*2, 2);

	/* Draw binding labels */
	for (size_t i = 0; i < 4; ++i)
	{
		std::string lb = sourceDescString(src[i]);
		if (lb.empty())
			continue;

		const int x = lbOff[i*2+0];
		const int y = lbOff[i*2+1];
		p->drawText(surf, lb.c_str(), cellOffX+x+1, y, cellW-2, Center);
	}

	for (size_t i = 0; i < 4; ++i)
	{
		if (!dupFlag[i])
			continue;

		p->strokeRectInner(surf, cellOff[i*2]+1, cellOff[i*2+1]+1,
		        cellW-2, cellH-3, 1, 255, 0, 0);
	}
}

void BindingWidget::setHoveredCell(int cell)
{
	if (hoveredCell == cell)
		return;

	hoveredCell = cell;
	p->redraw();
}

void BindingWidget::motionHandler(int x, int y)
{
	setHoveredCell(cellIndex(x, y));
}

void BindingWidget::leaveHandler()
{
	setHoveredCell(-1);
}

void BindingWidget::clickHandler(int x, int y, uint8_t button)
{
	int cell = cellIndex(x, y);

	if (cell == -1)
		return;

	p->onBWidgetCellClicked(src[cell], vb.str.c_str(), button);
}

int BindingWidget::cellIndex(int x, int y) const
{
	const int cellW = (rect.w*BW_CELL_R) / 2;
	const int cellH = rect.h / 2;
	const int cellOff = (1.0f-BW_CELL_R) * rect.w;

	if (x < cellOff)
		return -1;

	x -= cellOff;

	if (y < cellH)
		if (x < cellW)
			return 0;
		else
			return 1;
	else
		if (x < cellW)
			return 2;
		else
			return 3;

	return -1;
}

IntRect BindingWidget::cellRect(int i) const
{
	const int cellW = (rect.w*BW_CELL_R) / 2;
	const int cellH = rect.h / 2;
	const int cellOffX = (1.0f-BW_CELL_R) * rect.w;

	return IntRect(rect.x + cellOffX + (i % 2)*cellW, rect.y + (i / 2)*cellH,
	               cellW, cellH);
}

void BindingWidget::appendBindings(BDescVec &d) const
{
	for (size_t i = 0; i < 4; ++i)
	{
		if (src[i].type == Invalid)
			continue;

		BindingDesc desc;
		desc.src = src[i];
		desc.target = vb.code;
		d.push_back(desc);
	}
}

void Button::setHovered(bool val)
{
	if (hovered == val)
		return;

	hovered = val;
	p->redraw();
}

void Button::drawHandler(SDL_Surface *surf)
{
	if (hovered)
		p->fillRect(surf, cBgDark, 0, 0, rect.w, rect.h);

	p->strokeRectInner(surf, cLine, 0, 0, rect.w, rect.h, 2);
	p->drawText(surf, str, 0, rect.h/2, rect.w, Center);
}

void Button::motionHandler(int, int)
{
	setHovered(true);
}

void Button::leaveHandler()
{
	setHovered(false);
}

void Button::clickHandler(int, int, uint8_t button)
{
	if (button != SDL_BUTTON_LEFT)
		return;

	(p->*cb)();
}

void Label::setVisible(bool val)
{
	if (visible == val)
		return;

	visible = val;
	p->redraw();
}

void Label::drawHandler(SDL_Surface *surf)
{
	if (visible)
		p->drawText(surf, str, 0, rect.h/2, rect.w, Left, c);
}

SettingsMenu::SettingsMenu(RGSSThreadData &rtData)
{
    // Names shown in the menu: base actions first, then the extra ones
    std::vector<MenuButton> menuButtons;
    {
#define SET_BUTTON_NAME(n, b) vButtons[n].str = rtData.config.kbActionNames.b.c_str();
        SET_BUTTON_NAME(2, l);
        SET_BUTTON_NAME(5, r);
        SET_BUTTON_NAME(6, a);
        SET_BUTTON_NAME(7, b);
        SET_BUTTON_NAME(8, c);
        SET_BUTTON_NAME(9, x);
        SET_BUTTON_NAME(10, y);
        SET_BUTTON_NAME(11, z);
#undef SET_BUTTON_NAME
        
        for (size_t i = 0; i < vButtonsN; ++i)
            menuButtons.push_back({ vButtons[i].code, vButtons[i].str });
        
        for (const ExtraAction &a : extraActions(rtData.config))
            menuButtons.push_back({ a.code, a.name });
    }
    
	if (SMP::captureTimeoutEvent == (Uint32) -1)
		SMP::captureTimeoutEvent = SDL_RegisterEvents(1);

	p = new SettingsMenuPrivate(rtData);
	p->state = Idle;

	p->hasFocus = false;
	p->destroyReq = false;

	p->window = SDL_CreateWindow("Key bindings",
	                             SDL_WINDOWPOS_UNDEFINED, SDL_WINDOWPOS_UNDEFINED,
	                             winSize.x, winSize.y, SDL_WINDOW_INPUT_FOCUS);
	p->winSurf = SDL_GetWindowSurface(p->window);
	p->winID = SDL_GetWindowID(p->window);

	p->font = SharedFontState::openBundled(fontSize);

	p->rgb = p->winSurf->format;

	const size_t layoutW = 4;
	const size_t layoutH = 3;
	assert(layoutW*layoutH == pageSize);

	const int bWidgetW = winSize.x / layoutH;
	const int bWidgetH = 64;
	const int bWidgetY = winSize.y - layoutW*bWidgetH - 48;

	/* Widget i sits in slot i % pageSize of its page */
	for (size_t i = 0; i < menuButtons.size(); ++i)
	{
		const int x = (i % pageSize) % layoutH;
		const int y = (i % pageSize) / layoutH;
		BindingWidget w(menuButtons[i], p, IntRect(x*bWidgetW, bWidgetY+y*bWidgetH,
		                                           bWidgetW, bWidgetH));
		p->bWidgets.push_back(w);
	}

	p->pageCount = (menuButtons.size() + pageSize - 1) / pageSize;

	BDescVec binds;
	rtData.bindingUpdateMsg.get(binds);
	p->setupBindingData(binds);

	/* Buttons */
	const int buttonH = 32;
	const int buttonY = winSize.y - buttonH - 8;

	IntRect btRects[] =
	{
	    IntRect(16, buttonY, 112, buttonH),
	    IntRect(winSize.x-16-64*2-8, buttonY, 64, buttonH),
	    IntRect(winSize.x-16-64, buttonY, 64, buttonH)
	};

	p->buttons.push_back(Button(p, btRects[0], "Reset defaults", &SMP::onResetToDefault));
	p->buttons.push_back(Button(p, btRects[1], "Cancel", &SMP::onCancel));
	p->buttons.push_back(Button(p, btRects[2], "Accept", &SMP::onAccept));

	if (p->pageCount > 1)
	{
		const int cx = winSize.x / 2;
		p->buttons.push_back(Button(p, IntRect(cx-120, buttonY, 72, buttonH), "< Prev", &SMP::onPrevPage));
		p->buttons.push_back(Button(p, IntRect(cx+48, buttonY, 72, buttonH), "Next >", &SMP::onNextPage));
		p->pageLabel = Label(p, IntRect(cx-40, buttonY, 88, buttonH), p->pageStr, cText, cText, cText);
	}

	/* Labels */
	const char *info = "Click / A / Space: bind   Right click / X / Del: clear   Wheel / LB RB: page   Start / Enter: accept   B / Esc: cancel";
	p->infoLabel = Label(p, IntRect(16, 6, winSize.x, 16), info, cText, cText, cText);

	const char *warn = "Warning: Same physical key bound to multiple slots";
	p->dupWarnLabel = Label(p, IntRect(16, 26, winSize.x, 16), warn, 255, 0, 0);

	p->hovered = 0;
	p->captureDesc = 0;
	p->captureName = 0;

	p->updateDuplicateStatus();

	p->setPage(0);
}

SettingsMenu::~SettingsMenu()
{
	p->stopCaptureTimer();
	TTF_CloseFont(p->font);
	SDL_DestroyWindow(p->window);

	delete p;
}

bool SettingsMenu::onEvent(const SDL_Event &event)
{
	if (event.type == SMP::captureTimeoutEvent)
	{
		if (event.user.code == 0)
			p->captureTimer = 0;
		else
			p->holdTimer = 0;

		if (p->state == AwaitingInput && (event.user.code == 0 || p->holdPending))
			p->cancelCapture();

		return true;
	}

	/* First, check whether this event is for us */
	switch (event.type)
	{
	case SDL_WINDOWEVENT :
	case SDL_MOUSEBUTTONDOWN :
	case SDL_MOUSEBUTTONUP :
	case SDL_MOUSEMOTION :
	case SDL_MOUSEWHEEL :
	case SDL_KEYDOWN :
	case SDL_KEYUP :
		/* We can do this because windowID has the same
		 * struct offset in all these event types */
		if (event.window.windowID != p->winID)
			return false;
		break;

	case SDL_CONTROLLERBUTTONDOWN :
	case SDL_CONTROLLERBUTTONUP :
	case SDL_CONTROLLERAXISMOTION :
		if (!p->hasFocus)
			return false;
		break;

	/* Don't try to handle something we don't understand */
	default:
		return false;
	}

	/* Now process it.. */
	switch (event.type)
	{
	/* Ignore these event */
	case SDL_MOUSEBUTTONUP :
		return true;

	/* A short Esc / pad B press during a capture binds it on release */
	case SDL_KEYUP :
		if (p->state == AwaitingInput && p->holdPending == 1 &&
		    event.key.keysym.scancode == SDL_SCANCODE_ESCAPE)
		{
			SourceDesc src;
			src.type = Key;
			src.d.scan = SDL_SCANCODE_ESCAPE;
			p->commitCapture(src);
		}
		return true;

	case SDL_CONTROLLERBUTTONUP :
		if (p->state == AwaitingInput && p->holdPending == 2 &&
		    event.cbutton.button == SDL_CONTROLLER_BUTTON_B)
		{
			SourceDesc src;
			src.type = CButton;
			src.d.cb = SDL_CONTROLLER_BUTTON_B;
			p->commitCapture(src);
		}
		return true;

	case SDL_WINDOWEVENT :
		switch (event.window.event)
		{
		case SDL_WINDOWEVENT_SHOWN : // SDL is bugged and doesn't give us a first FOCUS_GAINED event
		case SDL_WINDOWEVENT_FOCUS_GAINED :
			p->hasFocus = true;
			break;

		case SDL_WINDOWEVENT_FOCUS_LOST :
			p->hasFocus = false;
			break;

		case SDL_WINDOWEVENT_EXPOSED :
			SDL_UpdateWindowSurface(p->window);
			break;

		case SDL_WINDOWEVENT_LEAVE:
			if (p->hovered)
			{
				p->hovered->leave();
				p->hovered = 0;
			}
			break;

		case SDL_WINDOWEVENT_CLOSE:
			p->onCancel();
		}

		return true;

	case SDL_MOUSEMOTION:
		p->onMotion(event.motion);
		return true;

	case SDL_MOUSEWHEEL:
		if (event.wheel.y > 0)
			p->onPrevPage();
		else if (event.wheel.y < 0)
			p->onNextPage();
		return true;

	case SDL_KEYDOWN:
		if (p->state != AwaitingInput)
		{
			switch (event.key.keysym.sym)
			{
			case SDLK_RETURN:    p->onAccept(); break;
			case SDLK_ESCAPE:    p->onCancel(); break;
			case SDLK_UP:        p->moveSelection(0, -1); break;
			case SDLK_DOWN:      p->moveSelection(0, 1);  break;
			case SDLK_LEFT:      p->moveSelection(-1, 0); break;
			case SDLK_RIGHT:     p->moveSelection(1, 0);  break;
			case SDLK_SPACE:     p->activateSelection(SDL_BUTTON_LEFT);  break;
			case SDLK_DELETE:
			case SDLK_BACKSPACE: p->activateSelection(SDL_BUTTON_RIGHT); break;
			case SDLK_PAGEUP:    p->onPrevPage(); break;
			case SDLK_PAGEDOWN:  p->onNextPage(); break;
			default: break;
			}

			return true;
		}

		/* Holding the key that started the capture must not bind itself */
		if (event.key.repeat)
			return true;

		/* Esc: bound on a short press (on release), held = cancel */
		if (event.key.keysym.scancode == SDL_SCANCODE_ESCAPE)
		{
			p->startHold(1);
			return true;
		}

		/* Don't let the user bind keys that trigger
		 * mkxp functions */
		switch(event.key.keysym.scancode)
		{
		case SDL_SCANCODE_F1:
		case SDL_SCANCODE_F2:
		case SDL_SCANCODE_F12:
			return true;
		default:
			break;
		}

		/* Not a controller event: skip the controller cases below, which
		 * would read the key event's fields as a button */
		break;

	case SDL_CONTROLLERBUTTONDOWN:
		if (p->state != AwaitingInput)
		{
			p->onPadButton(event.cbutton.button);
			return true;
		}

		/* Pad B: bound on a short press (on release), held = cancel */
		if (event.cbutton.button == SDL_CONTROLLER_BUTTON_B)
		{
			p->startHold(2);
			return true;
		}
		break;

	case SDL_CONTROLLERAXISMOTION:
		if (p->state != AwaitingInput)
		{
			p->onPadAxis(event.caxis.axis, event.caxis.value);
			return true;
		}

		p->trackStick(event.caxis.axis, event.caxis.value);
		break;

	case SDL_MOUSEBUTTONDOWN:
		p->onClick(event.button);
		return true;
	default:
		return true;
	}

	if (p->state == AwaitingInput)
		return p->onCaptureInputEvent(event);

	return true;
}

void SettingsMenu::raise()
{
	SDL_RaiseWindow(p->window);
}

bool SettingsMenu::destroyReq() const
{
	return p->destroyReq;
}
