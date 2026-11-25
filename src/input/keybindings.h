/*
** keybindings.h
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

#ifndef KEYBINDINGS_H
#define KEYBINDINGS_H

#include "input.h"

#include <SDL_scancode.h>
#include <SDL_gamecontroller.h>
#include <stdint.h>
#include <assert.h>
#include <string>
#include <string.h>
#include <vector>

enum AxisDir
{
	Negative,
	Positive
};

enum SourceType
{
	Invalid,
	Key,
    CButton,
    CAxis
};

struct SourceDesc
{
	SourceType type;

	union Data
	{
		/* Keyboard scancode */
		SDL_Scancode scan;
		/* Joystick button index */
		SDL_GameControllerButton cb;
		struct
		{
			/* Joystick axis index */
			SDL_GameControllerAxis axis;
			/* Joystick axis direction */
			AxisDir dir;
		} ca;
	} d;

	/* Optional second source: when set the binding is a combo and both
	 * sources have to be held (in any order) */
	SourceType type2;
	Data d2;

	SourceDesc()
	    : type(Invalid), type2(Invalid)
	{
		memset(&d, 0, sizeof(d));
		memset(&d2, 0, sizeof(d2));
	}

	void clear() { *this = SourceDesc(); }

	static bool sameData(SourceType t, const Data &a, const Data &b)
	{
		switch (t)
		{
		case Invalid:
			return true;
		case Key:
			return a.scan == b.scan;
		case CButton:
			return a.cb == b.cb;
		case CAxis:
			return (a.ca.axis == b.ca.axis) && (a.ca.dir == b.ca.dir);
		default:
			assert(!"unreachable");
			return false;
		}
	}

	bool operator==(const SourceDesc &o) const
	{
		return type == o.type && type2 == o.type2
		    && sameData(type, d, o.d) && sameData(type2, d2, o.d2);
	}

	bool operator!=(const SourceDesc &o) const
	{
		return !(*this == o);
	}
};

#define JAXIS_THRESHOLD 0x4000

struct BindingDesc
{
	SourceDesc src;
	Input::ButtonCode target;
};

typedef std::vector<BindingDesc> BDescVec;
struct Config;

/* Actions beyond the 12 base ones: built-in keys made remappable
 * ("remappableKeys") followed by game-defined "customActions". */
struct ExtraAction
{
	std::string id;
	std::string name;
	Input::ButtonCode code;
	bool builtin;
	std::string key, button; /* custom defaults only */
	std::string rawKey;      /* custom only: also drives this raw key */
};

std::vector<ExtraAction> extraActions(const Config &conf);

BDescVec genDefaultBindings(const Config &conf);

void storeBindings(const BDescVec &d, const Config &conf);
BDescVec loadBindings(const Config &conf);

#endif // KEYBINDINGS_H
