/*
** keybindings.cpp
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

#include "keybindings.h"

#include "config.h"
#include "util.h"

#include <stdio.h>
#include <string.h>
#include <algorithm>

struct KbBindingData
{
	SDL_Scancode source;
	Input::ButtonCode target;

	void add(BDescVec &d) const
	{
		SourceDesc src;
		src.type = Key;
		src.d.scan = source;

		BindingDesc desc;
		desc.src = src;
		desc.target = target;

		d.push_back(desc);
	}
};

struct CtrlBindingData
{
    SDL_GameControllerButton source;
    Input::ButtonCode target;
    
    void add(BDescVec &d) const
    {
        SourceDesc src;
        src.type = CButton;
        src.d.cb = source;
        
        BindingDesc desc;
        desc.src = src;
        desc.target = target;
        
        d.push_back(desc);
    }
};

/* Common */
static const KbBindingData defaultKbBindings[] =
{
	{ SDL_SCANCODE_LEFT,   Input::Left  },
	{ SDL_SCANCODE_RIGHT,  Input::Right },
	{ SDL_SCANCODE_UP,     Input::Up    },
	{ SDL_SCANCODE_DOWN,   Input::Down  },
    
	{ SDL_SCANCODE_SPACE,  Input::C     },
	{ SDL_SCANCODE_RETURN, Input::C     },
	{ SDL_SCANCODE_ESCAPE, Input::B     },
	{ SDL_SCANCODE_KP_0,   Input::B     },
	{ SDL_SCANCODE_LSHIFT, Input::A     },
	{ SDL_SCANCODE_X,      Input::B     },
	{ SDL_SCANCODE_D,      Input::Z     },
	{ SDL_SCANCODE_Q,      Input::L     },
	{ SDL_SCANCODE_W,      Input::R     },
	{ SDL_SCANCODE_A,      Input::X     },
	{ SDL_SCANCODE_S,      Input::Y     }
};

/* RGSS1 */
static const KbBindingData defaultKbBindings1[] =
{
	{ SDL_SCANCODE_Z,      Input::A     },
	{ SDL_SCANCODE_C,      Input::C     },
};

/* RGSS2 and higher */
static const KbBindingData defaultKbBindings2[] =
{
	{ SDL_SCANCODE_Z,      Input::C     }
};

static elementsN(defaultKbBindings);
static elementsN(defaultKbBindings1);
static elementsN(defaultKbBindings2);

static const CtrlBindingData defaultCtrlBindings[] =
{
	{ SDL_CONTROLLER_BUTTON_X, Input::A  },
	{ SDL_CONTROLLER_BUTTON_B, Input::B  },
	{ SDL_CONTROLLER_BUTTON_A, Input::C },
	{ SDL_CONTROLLER_BUTTON_Y, Input::X  },
	{ SDL_CONTROLLER_BUTTON_LEFTSTICK, Input::Y  },
	{ SDL_CONTROLLER_BUTTON_RIGHTSTICK, Input::Z },
	{ SDL_CONTROLLER_BUTTON_LEFTSHOULDER, Input::L  },
	{ SDL_CONTROLLER_BUTTON_RIGHTSHOULDER, Input::R  },
    
    { SDL_CONTROLLER_BUTTON_DPAD_UP, Input::Up },
    { SDL_CONTROLLER_BUTTON_DPAD_DOWN, Input::Down },
    { SDL_CONTROLLER_BUTTON_DPAD_LEFT, Input::Left },
    { SDL_CONTROLLER_BUTTON_DPAD_RIGHT, Input::Right }
};

static elementsN(defaultCtrlBindings);

static void addAxisBinding(BDescVec &d, SDL_GameControllerAxis axis, AxisDir dir, Input::ButtonCode target)
{
	SourceDesc src;
	src.type = CAxis;
	src.d.ca.axis = axis;
	src.d.ca.dir = dir;

	BindingDesc desc;
	desc.src = src;
	desc.target = target;

	d.push_back(desc);
}

/* Built-in keys that can be made remappable through "remappableKeys".
 * They are fixed (static) bindings otherwise. */
static const struct
{
	const char *id;
	const char *name;
	Input::ButtonCode code;
	SDL_Scancode keys[2];
} builtinExtras[] =
{
	{ "SHIFT", "Shift", Input::Shift, { SDL_SCANCODE_LSHIFT, SDL_SCANCODE_RSHIFT } },
	{ "CTRL",  "Ctrl",  Input::Ctrl,  { SDL_SCANCODE_LCTRL,  SDL_SCANCODE_RCTRL  } },
	{ "ALT",   "Alt",   Input::Alt,   { SDL_SCANCODE_LALT,   SDL_SCANCODE_RALT   } },
	{ "F5", "F5", Input::F5, { SDL_SCANCODE_F5, SDL_SCANCODE_UNKNOWN } },
	{ "F6", "F6", Input::F6, { SDL_SCANCODE_F6, SDL_SCANCODE_UNKNOWN } },
	{ "F7", "F7", Input::F7, { SDL_SCANCODE_F7, SDL_SCANCODE_UNKNOWN } },
	{ "F8", "F8", Input::F8, { SDL_SCANCODE_F8, SDL_SCANCODE_UNKNOWN } },
	{ "F9", "F9", Input::F9, { SDL_SCANCODE_F9, SDL_SCANCODE_UNKNOWN } }
};

static elementsN(builtinExtras);

/* Ruby constants already taken by the engine; custom ids must not shadow them */
static const char *reservedIds[] =
{
	"DOWN", "LEFT", "RIGHT", "UP", "A", "B", "C", "X", "Y", "Z", "L", "R",
	"MOUSELEFT", "MOUSEMIDDLE", "MOUSERIGHT", "MOUSEX1", "MOUSEX2"
};

static elementsN(reservedIds);

std::vector<ExtraAction> extraActions(const Config &conf)
{
	std::vector<ExtraAction> out;
	int customCount = 0;

	for (size_t i = 0; i < builtinExtrasN; ++i)
	{
		const auto &b = builtinExtras[i];

		if (std::find(conf.remappableKeys.begin(), conf.remappableKeys.end(),
		              b.id) == conf.remappableKeys.end())
			continue;

		ExtraAction a;
		a.id = b.id;
		a.name = b.name;
		a.code = b.code;
		a.builtin = true;
		out.push_back(a);
	}

	for (const Config::CustomAction &c : conf.customActions)
	{
		bool clash = false;

		for (size_t i = 0; i < reservedIdsN; ++i)
			clash = clash || c.id == reservedIds[i];

		for (size_t i = 0; i < builtinExtrasN; ++i)
			clash = clash || c.id == builtinExtras[i].id;

		if (clash || customCount > Input::CustomMax - Input::CustomBase)
			continue;

		ExtraAction a;
		a.id = c.id;
		a.name = c.name;
		a.code = (Input::ButtonCode) (Input::CustomBase + customCount++);
		a.builtin = false;
		a.key = c.key;
		a.button = c.button;
		a.rawKey = c.rawKey;
		out.push_back(a);
	}

	return out;
}

static void addExtraDefaults(BDescVec &d, const ExtraAction &a)
{
	if (a.builtin)
	{
		for (size_t i = 0; i < builtinExtrasN; ++i)
		{
			if (builtinExtras[i].code != a.code)
				continue;

			for (SDL_Scancode sc : builtinExtras[i].keys)
				if (sc != SDL_SCANCODE_UNKNOWN)
					KbBindingData{ sc, a.code }.add(d);
		}
		return;
	}

	std::string key = a.key, btn = a.button;
	std::transform(key.begin(), key.end(), key.begin(), ::toupper);
	std::transform(btn.begin(), btn.end(), btn.begin(), ::toupper);

	auto k = strToScancode.find(key);
	if (k != strToScancode.end())
		KbBindingData{ (SDL_Scancode) k->second, a.code }.add(d);

	auto b = strToGCButton.find(btn);
	if (b != strToGCButton.end())
		CtrlBindingData{ b->second, a.code }.add(d);
}

BDescVec genDefaultBindings(const Config &conf)
{
	BDescVec d;

	for (size_t i = 0; i < defaultKbBindingsN; ++i)
		defaultKbBindings[i].add(d);

	if (conf.rgssVersion == 1)
		for (size_t i = 0; i < defaultKbBindings1N; ++i)
			defaultKbBindings1[i].add(d);
	else
		for (size_t i = 0; i < defaultKbBindings2N; ++i)
			defaultKbBindings2[i].add(d);

	for (size_t i = 0; i < defaultCtrlBindingsN; ++i)
		defaultCtrlBindings[i].add(d);

	addAxisBinding(d, SDL_CONTROLLER_AXIS_LEFTX, Negative, Input::Left );
	addAxisBinding(d, SDL_CONTROLLER_AXIS_LEFTX, Positive, Input::Right);
	addAxisBinding(d, SDL_CONTROLLER_AXIS_LEFTY, Negative, Input::Up   );
	addAxisBinding(d, SDL_CONTROLLER_AXIS_LEFTY, Positive, Input::Down );

	for (const ExtraAction &a : extraActions(conf))
		addExtraDefaults(d, a);

	return d;
}

/* File layout (v5; v4 records have no second source): Header, then idCount x char[ID_LEN] (every extra action
 * known when saving), then `count` BindingDescs. Custom targets are stored as
 * CustomBase + index into that id table so reordering "customActions" in
 * mkxp.json does not scramble saved bindings. v3 files have no id table. */
#define FORMAT_VER 5
#define FORMAT_VER_SINGLE 4 /* v4: 16-byte records, no combo source */
#define FORMAT_VER_NO_IDS 3
#define ID_LEN 32

struct Header
{
	uint32_t formVer;
	uint32_t rgssVer;
	uint32_t count;
};

static void buildPath(const std::string &dir, uint32_t rgssVersion,
                      char *out, size_t outSize)
{
	snprintf(out, outSize, "%skeybindings.mkxp%u", dir.c_str(), rgssVersion);
}

static bool writeBindings(const BDescVec &d, const std::string &dir,
                          uint32_t rgssVersion, const std::vector<ExtraAction> &extras)
{
	if (dir.empty())
		return false;

	char path[1024];
	buildPath(dir, rgssVersion, path, sizeof(path));

	FILE *f = fopen(path, "wb");

	if (!f)
		return false;

	BDescVec out = d;
	for (BindingDesc &desc : out)
		for (size_t i = 0; i < extras.size(); ++i)
			if (!extras[i].builtin && desc.target == extras[i].code)
				desc.target = (Input::ButtonCode) (Input::CustomBase + i);

	Header hd;
	hd.formVer = FORMAT_VER;
	hd.rgssVer = rgssVersion;
	hd.count = out.size();
	uint32_t idCount = extras.size();

	bool ok = fwrite(&hd, sizeof(hd), 1, f) == 1 &&
	          fwrite(&idCount, sizeof(idCount), 1, f) == 1;

	for (size_t i = 0; ok && i < extras.size(); ++i)
	{
		char id[ID_LEN] = {};
		strncpy(id, extras[i].id.c_str(), ID_LEN - 1);
		ok = fwrite(id, ID_LEN, 1, f) == 1;
	}

	if (ok && hd.count > 0)
		ok = fwrite(&out[0], sizeof(out[0]), hd.count, f) == hd.count;

	fclose(f);
	return ok;
}

void storeBindings(const BDescVec &d, const Config &conf)
{
    writeBindings(d, conf.customDataPath, conf.rgssVersion, extraActions(conf));
}

static bool verifySource(SourceType type, const SourceDesc::Data &d)
{
	switch (type)
	{
	case Invalid:
		return true;
	case Key:
		return d.scan < SDL_NUM_SCANCODES;
	case CButton:
		return true;
	case CAxis:
		return d.ca.dir == Negative || d.ca.dir == Positive;
	default:
		return false;
	}
}

static bool verifyDesc(const BindingDesc &desc,
                       const std::vector<ExtraAction> &extras)
{
	const Input::ButtonCode codes[] =
	{
	    Input::None,
	    Input::Down, Input::Left, Input::Right, Input::Up,
	    Input::A, Input::B, Input::C,
	    Input::X, Input::Y, Input::Z,
	    Input::L, Input::R
	};

	elementsN(codes);
	bool known = false;

	for (size_t i = 0; i < codesN; ++i)
		known = known || desc.target == codes[i];

	for (const ExtraAction &a : extras)
		known = known || desc.target == a.code;

	if (!known)
		return false;

	const SourceDesc &src = desc.src;

	if (!verifySource(src.type, src.d))
		return false;

	/* A combo needs a real first source */
	if (src.type2 != Invalid && src.type == Invalid)
		return false;

	return verifySource(src.type2, src.d2);
}

static bool readBindings(BDescVec &out, const std::string &dir,
                         uint32_t rgssVersion, const std::vector<ExtraAction> &extras)
{
	if (dir.empty())
		return false;

	char path[1024];
	buildPath(dir, rgssVersion, path, sizeof(path));

	FILE *f = fopen(path, "rb");

	if (!f)
		return false;

	struct Closer { FILE *f; ~Closer() { fclose(f); } } closer = { f };

	Header hd;
	if (fread(&hd, sizeof(hd), 1, f) < 1)
		return false;

	if (hd.formVer != FORMAT_VER && hd.formVer != FORMAT_VER_SINGLE &&
	    hd.formVer != FORMAT_VER_NO_IDS)
		return false;
	if (hd.rgssVer != rgssVersion)
		return false;
	/* Arbitrary max value */
	if (hd.count > 1024)
		return false;

	std::vector<std::string> fileIds;

	if (hd.formVer != FORMAT_VER_NO_IDS)
	{
		uint32_t idCount;
		if (fread(&idCount, sizeof(idCount), 1, f) < 1 || idCount > 64)
			return false;

		for (uint32_t i = 0; i < idCount; ++i)
		{
			char id[ID_LEN + 1] = {};
			if (fread(id, ID_LEN, 1, f) < 1)
				return false;
			fileIds.push_back(id);
		}
	}

	BDescVec raw(hd.count);

	if (hd.formVer == FORMAT_VER)
	{
		if (hd.count > 0 && fread(&raw[0], sizeof(raw[0]), hd.count, f) < hd.count)
			return false;
	}
	else
	{
		/* Older files: single-source records */
		struct OldDesc
		{
			SourceType type;
			SourceDesc::Data d;
			Input::ButtonCode target;
		};

		std::vector<OldDesc> old(hd.count);
		if (hd.count > 0 && fread(&old[0], sizeof(old[0]), hd.count, f) < hd.count)
			return false;

		for (uint32_t i = 0; i < hd.count; ++i)
		{
			raw[i].src.type = old[i].type;
			raw[i].src.d = old[i].d;
			raw[i].target = old[i].target;
		}
	}

	/* Re-target stored descs onto the current action codes. Descs whose
	 * action no longer exists are dropped. */
	out.clear();
	for (BindingDesc desc : raw)
	{
		int t = desc.target;

		if (t >= Input::CustomBase)
		{
			size_t idx = t - Input::CustomBase;
			if (idx >= fileIds.size())
				return false;

			const ExtraAction *cur = 0;
			for (const ExtraAction &a : extras)
				if (!a.builtin && a.id == fileIds[idx])
					cur = &a;

			if (!cur)
				continue;

			desc.target = cur->code;
		}
		else if (t >= Input::Shift && t <= Input::F9)
		{
			bool active = false;
			for (const ExtraAction &a : extras)
				active = active || a.code == t;

			if (!active)
				continue;
		}

		if (!verifyDesc(desc, extras))
			return false;

		out.push_back(desc);
	}

	/* Actions added since the file was written get their defaults */
	for (const ExtraAction &a : extras)
		if (std::find(fileIds.begin(), fileIds.end(), a.id) == fileIds.end())
			addExtraDefaults(out, a);

	return true;
}

BDescVec loadBindings(const Config &conf)
{
	BDescVec d;

	if (readBindings(d, conf.customDataPath, conf.rgssVersion, extraActions(conf)))
		return d;

	return genDefaultBindings(conf);
}
