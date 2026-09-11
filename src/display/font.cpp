/*
** font.cpp
**
** This file is part of mkxp.
**
** Copyright (C) 2013 - 2021 Amaryllis Kulla <ancurio@mapleshrine.eu>
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

#include "font.h"

#include "sharedstate.h"
#include "filesystem.h"
#include "exception.h"
#include "boost-hash.h"
#include "util.h"
#include "config.h"
#include "encoding.h"

#include "debugwriter.h"

#include <string>
#include <utility>
#include <algorithm>
#include <cctype>
#include <array>
#include <unordered_map>

#ifdef MKXPZ_BUILD_XCODE
#include "filesystem/filesystem.h"
#endif

#include <SDL_ttf.h>

#include <ft2build.h>
#include FT_FREETYPE_H
#include FT_SFNT_NAMES_H
#include FT_TRUETYPE_TABLES_H
#include FT_TRUETYPE_IDS_H

#ifndef MKXPZ_BUILD_XCODE
#ifndef MKXPZ_CJK_FONT
#include "liberation.ttf.xxd"
#else
#include "wqymicrohei.ttf.xxd"
#endif


#ifndef MKXPZ_CJK_FONT
#define BUNDLED_FONT liberation
#else
#define BUNDLED_FONT wqymicrohei
#endif

#define BUNDLED_FONT_DECL(FONT) \
	extern unsigned char ___assets_##FONT##_ttf[]; \
	extern unsigned int ___assets_##FONT##_ttf_len;

BUNDLED_FONT_DECL(liberation)

#define BUNDLED_FONT_D(f) ___assets_## f ##_ttf
#define BUNDLED_FONT_L(f) ___assets_## f ##_ttf_len

// Go fuck yourself CPP
#define BNDL_F_D(f) BUNDLED_FONT_D(f)
#define BNDL_F_L(f) BUNDLED_FONT_L(f)

#endif

/* Dirty hack to get the FT_Face.
 * SDL_ttf will probably never move it from the beginning of the struct. */
#define TTF_FONT_TO_FT_FACE(font) (*reinterpret_cast<FT_Face *>(font))

static SDL_RWops *openBundledFont()
{
#ifndef MKXPZ_BUILD_XCODE
    return SDL_RWFromConstMem(BNDL_F_D(BUNDLED_FONT), BNDL_F_L(BUNDLED_FONT));
#else
    return SDL_RWFromFile(mkxp_fs::getPathForAsset("Fonts/liberation", "ttf").c_str(), "rb");
#endif
}


/* <name, size> */
typedef std::pair<std::string, int> FontSizeKey;
/* <name, ppem> */
typedef std::pair<std::string, int> FontPPEMKey;

struct FontSet
{
	/* 'Regular' style */
	std::string regular;

	/* Any other styles (used in case no 'Regular' exists) */
	std::string other;

	/* 'Regular' style obtained via SFNT */
	std::string sfnt_regular;

	/* Any other styles obtained via SFNT */
	std::string sfnt_other;

	const std::string *operator->() const noexcept
	{
		if (!sfnt_regular.empty())
			return &sfnt_regular;
		if (!sfnt_other.empty())
			return &sfnt_other;
		if (!regular.empty())
			return &regular;
		return &other;
	}
};

struct SharedFontStatePrivate
{
	/* Maps: font family name, To: substituted family name,
	 * as specified via configuration file / arguments */
	BoostHash<std::string, std::string> subs;

	/* Maps: font family name, To: set of physical
	 * font filenames located in "Fonts/" */
	BoostHash<std::string, FontSet> sets;

	/* Pool of font size to ppem values */
	BoostHash<FontSizeKey, int> size_to_ppem;

	/* Pool of already opened fonts; once opened, they are reused
	 * and never closed until the termination of the program */
	BoostHash<FontPPEMKey, std::array<TTF_Font*, 2>> ppem_to_font;
    
    /* Internal default font family that is used anytime an
     * empty/invalid family is requested */
    std::string defaultFamily;

	float fontScale;
	bool fontKerning;
	int fontHinting;
	bool fontPixelSnap;
};

SharedFontState::SharedFontState(const Config &conf)
{
	p = new SharedFontStatePrivate;

	/* Parse font substitutions */
	for (size_t i = 0; i < conf.fontSubs.size(); ++i)
	{
		const std::string &raw = conf.fontSubs[i];
		size_t sepPos = raw.find_first_of('>');

		if (sepPos == std::string::npos)
			continue;

		std::string from = raw.substr(0, sepPos);
		std::string to   = raw.substr(sepPos+1);

		p->subs.insert(from, to);
	}
	
	p->fontScale = conf.fontScale;
	if (p->fontScale < 0.1f)
	{
		p->fontScale = 1.0f;
	}
	p->fontKerning = conf.fontKerning;
	p->fontHinting = conf.fontHinting;
	p->fontPixelSnap = conf.fontPixelSnap;
}

SharedFontState::~SharedFontState()
{
	BoostHash<FontPPEMKey, std::array<TTF_Font*, 2>>::const_iterator iter;
	for (iter = p->ppem_to_font.cbegin(); iter != p->ppem_to_font.cend(); ++iter)
	{
		for (int i=0; i < iter->second.size(); i++)
			if (iter->second[i] != 0)
				TTF_CloseFont(iter->second[i]);
	}

	delete p;
}

static std::string decodeSfntName(const FT_SfntName &aname)
{
	std::string str = std::string((const char *)aname.string, (size_t)aname.string_len);
	if ((aname.platform_id == TT_PLATFORM_MICROSOFT && aname.encoding_id == TT_MS_ID_UNICODE_CS) || aname.platform_id == TT_PLATFORM_APPLE_UNICODE)
		try
		{
			str = Encoding::convertString(str, "UTF-16BE");
		} catch (Exception)
		{}
	else if (aname.platform_id == TT_PLATFORM_MICROSOFT && aname.encoding_id == TT_MS_ID_UCS_4)
		try
		{
			str = Encoding::convertString(str, "UTF-32BE");
		} catch (Exception)
		{}
	return str;
}

void SharedFontState::initFontSetCB(SDL_RWops &ops,
                                    const std::string &filename)
{
	TTF_Font *font = TTF_OpenFontRW(&ops, 0, 0);

	if (!font)
		return;

	std::string family = TTF_FontFaceFamilyName(font);
	std::string style = TTF_FontFaceStyleName(font);

	std::transform(family.begin(), family.end(), family.begin(),
		[](unsigned char c){ return std::tolower(c); });

	FontSet &set = p->sets[family];

	if (style == "Regular" && set.regular.empty())
		set.regular = filename;
	else if (style != "Regular" && set.other.empty())
		set.other = filename;

	FT_Face face = TTF_FONT_TO_FT_FACE(font);

	if (FT_IS_SFNT(face))
	{
		std::unordered_map<uint32_t, std::pair<std::string, std::string>> name_map;

		for (unsigned int i = 0, name_count = FT_Get_Sfnt_Name_Count(face); i < name_count; ++i)
		{
			FT_SfntName aname;
			if (FT_Get_Sfnt_Name(face, i, &aname))
				continue;
			uint32_t key = aname.platform_id;
			key <<= 16;
			key |= aname.language_id;
			switch (aname.name_id)
			{
				case TT_NAME_ID_FONT_FAMILY:
					name_map[key].first = decodeSfntName(aname);
					break;
				case TT_NAME_ID_FONT_SUBFAMILY:
					name_map[key].second = decodeSfntName(aname);
					break;
			}
		}

		for (const auto &entry : name_map)
		{
			const std::string &sfnt_family_raw = entry.second.first;
			const std::string &sfnt_style = entry.second.second;
			if (sfnt_family_raw.empty())
				continue;

			std::string sfnt_family(sfnt_family_raw);

			std::transform(sfnt_family.begin(), sfnt_family.end(), sfnt_family.begin(),
				[](unsigned char c){ return std::tolower(c); });

			FontSet &set = p->sets[sfnt_family];

			if (sfnt_style == "Regular" && set.sfnt_regular.empty())
				set.sfnt_regular = filename;
			else if (sfnt_style != "Regular" && set.sfnt_other.empty())
				set.sfnt_other = filename;
		}
	}

	TTF_CloseFont(font);
}

/* Fraction of mid-gray (anti-aliased) alpha pixels in a rendered probe
 * string. ~0 means the glyph edges fall exactly on the pixel grid,
 * i.e. the font is a pixel font rendered at its design size. */
static float probe_mid_gray_ratio(_TTF_Font *font)
{
	SDL_Color white = { 255, 255, 255, 255 };
	SDL_Surface *surf = TTF_RenderUTF8_Blended(font, "Agy0", white);
	if (!surf)
		return 1.0f;

	uint32_t total = 0, gray = 0;
	const int bpp = surf->format->BytesPerPixel;
	for (int y = 0; y < surf->h; ++y)
	{
		const uint8_t *row = (const uint8_t *)surf->pixels + y * surf->pitch;
		for (int x = 0; x < surf->w; ++x)
		{
			uint8_t alpha = bpp == 4 ? row[x * 4 + 3] : row[x];
			if (alpha > 10)
			{
				++total;
				if (alpha < 245)
					++gray;
			}
		}
	}
	float ratio = total ? (float)gray / (float)total : 1.0f;
	SDL_FreeSurface(surf);
	return ratio;
}

/* Snap ppem to the nearest size where a pixel font renders without
 * anti-aliased edges (ties prefer the smaller candidate).
 * Returns basePpem unchanged if no crisp candidate is found nearby. */
static int snap_ppem_to_pixel_grid(_TTF_Font *font, int basePpem, int reqHeight)
{
	const float CRISP = 0.02f; /* <2% mid-gray counts as pixel-perfect */
	(void)reqHeight; /* ponytail: si el snap hacia arriba llega a solapar
	 * lineas en alguna UI, usar reqHeight para rechazar candidatos que
	 * superen TTF_FontHeight > reqHeight (modo shrink-only) */

	if (TTF_SetFontSize(font, basePpem))
		return basePpem;
	if (probe_mid_gray_ratio(font) < CRISP)
		return basePpem; /* already crisp */

	int best = basePpem;
	int bestDist = 9;
	for (int delta = -8; delta <= 8; ++delta)
	{
		int cand = basePpem + delta;
		if (delta == 0 || cand < 4)
			continue;
		if (TTF_SetFontSize(font, cand))
			continue;
		if (probe_mid_gray_ratio(font) >= CRISP)
			continue;
		int dist = delta < 0 ? -delta : delta;
		if (dist < bestDist)
		{
			best = cand;
			bestDist = dist;
		}
	}
	TTF_SetFontSize(font, best);
	return best;
}

_TTF_Font *SharedFontState::getFont(std::string family,
                                    int size, float hiresMult, int outline_size)
{
	std::transform(family.begin(), family.end(), family.begin(),
		[](unsigned char c){ return std::tolower(c); });

	if (family.empty())
		family = p->defaultFamily;

	/* Check for substitutions */
	if (p->subs.contains(family))
		family = p->subs[family];

	/* Find out if the font asset exists */
	const FontSet &req = p->sets[family];

	if (req->empty())
	{
		/* Doesn't exist; use built-in font */
		family = "";
	}

	FontSizeKey key(family, size);

	TTF_Font *font;
	int &ppem = p->size_to_ppem[key];
	int ppemMult;
	
	if (ppem != 0)
	{
		ppemMult = std::max<int>(ppem * hiresMult, 1);
		auto &group = p->ppem_to_font[FontPPEMKey(family, ppemMult)];
		if(outline_size == 0)
			font = group[0];
		else
			font = group[1];
		
		if (font)
		{
			if(outline_size && TTF_GetFontOutline(font) != outline_size)
				TTF_SetFontOutline(font, outline_size);
			return font;
		}
	}
	
	/* Not in pool; open new handle */
	SDL_RWops *ops;

	if (family.empty())
	{
		/* Built-in font */
		ops = openBundledFont();
	}
	else
	{
		/* Use 'other' path as alternative in case
		 * we have no 'regular' styled font asset */
		const char *path = req->c_str();

		ops = SDL_AllocRW();
		try{
			shState->fileSystem().openReadRaw(*ops, path, true);
		} catch (const Exception &e) {
			SDL_FreeRW(ops);
			p->size_to_ppem.remove(key);
			throw e;
		}
	}

	/* Try to compute the size the same way Windows does. */
	font = TTF_OpenFontRW(ops, 1, 0);

	if (font)
	{
		/* RGSS doesn't use font hinting; set before pixel grid probing
		 * so the probe measures the same conditions as the final render */
		TTF_SetFontHinting(font, p->fontHinting);

		FT_Face face = TTF_FONT_TO_FT_FACE(font);
		/* This is should always be true, but we may as well check... */
		if (FT_IS_SCALABLE( face ))
		{
			if (ppem == 0)
			{
				/* ponytail: la emulacion wine de ppem (VDMX/GDI) que
				 * trajo el merge rompia las fuentes pixel; se usa size
				 * en px directo, igual que pre-merge. */
				ppem = std::max<int>(size * p->fontScale, 1);
				if (p->fontPixelSnap)
					ppem = snap_ppem_to_pixel_grid(font, ppem,
					                               std::max<int>(size * p->fontScale, 1));
				ppemMult = std::max<int>(ppem * hiresMult, 1);
			}
			if (TTF_SetFontSize(font, ppemMult))
			{
				TTF_CloseFont(font);
				font = 0;
			}
		} else {
			/* Someone must have renamed a non-scalable font file to ttf or otf.
			 * Wine has a scaling setup for these, but I'll just fall back to
			 * the mkxp method for now. */
			if (ppem == 0)
			{
				ppem = std::max<int>(size * p->fontScale, 5);
				if (p->fontPixelSnap)
					ppem = snap_ppem_to_pixel_grid(font, ppem,
					                               std::max<int>(size * p->fontScale, 1));
				ppemMult = std::max<int>(ppem * hiresMult, 1);
			}
			if (TTF_SetFontSize(font, ppemMult))
			{
				TTF_CloseFont(font);
				font = 0;
			}
		}
	}
	
	if (!font)
	{
		p->size_to_ppem.remove(key);
		throw Exception(Exception::SDLError, "%s", SDL_GetError());
	}
	
	auto &group = p->ppem_to_font[FontPPEMKey(family, std::max<int>(ppem * hiresMult, 1))];
	if(outline_size == 0)
	{
		group[0] = font;
	} else {
		if(TTF_GetFontOutline(font) != outline_size)
			TTF_SetFontOutline(font, outline_size);
		group[1] = font;
	}
	
	if (!p->fontKerning)
		TTF_SetFontKerning(font, 0);
	
	return font;
}

bool SharedFontState::fontPresent(std::string family) const
{
	std::transform(family.begin(), family.end(), family.begin(),
		[](unsigned char c){ return std::tolower(c); });

	/* Check for substitutions */
	if (p->subs.contains(family))
		family = p->subs[family];

	const FontSet &set = p->sets[family];

	return !set->empty();
}

_TTF_Font *SharedFontState::openBundled(int size)
{
	SDL_RWops *ops = openBundledFont();

	return TTF_OpenFontRW(ops, 1, size);
}

void SharedFontState::setDefaultFontFamily(const std::string &family) {
    p->defaultFamily = family;
}

static bool pickExistingFontName(const std::vector<std::string> &names,
                          std::string &out,
                          const SharedFontState &sfs)
{
	/* Note: In RMXP, a names array with no existing entry
	 * results in no text being drawn at all (same for "" and []);
	 * we can't replicate this in mkxp due to the default substitute. */

	for (size_t i = 0; i < names.size(); ++i)
	{
		if (sfs.fontPresent(names[i]))
		{
			if (out == names[i])
				return false;
			out = names[i];
			return true;
		}
		else
		{
			if (i == 0)
			{
				Debug() << "Primary font not found:" << names[i];
			}
			else
			{
				Debug() << "Fallback font not found:" << names[i];
			}
		}
	}

	if (out[0] == '\0')
		return false;
	out = "";
	return true;
}


struct FontPrivate
{
	std::string name;
	int size;
	float hiresMult;
	bool bold;
	bool italic;
	bool outline;
	bool shadow;
	Color *color;
	Color *outColor;

	Color colorTmp;
	Color outColorTmp;

	static std::string defaultName;
	static int defaultSize;
	static bool defaultBold;
	static bool defaultItalic;
	static bool defaultOutline;
	static bool defaultShadow;
	static Color *defaultColor;
	static Color *defaultOutColor;

	static Color defaultColorTmp;
	static Color defaultOutColorTmp;

	static std::vector<std::string> initialDefaultNames;

	/* The actual font is opened as late as possible
	 * (when it is queried by a Bitmap), prior it is
	 * set to null */
	TTF_Font *sdlFont;
	TTF_Font *sdlFontOutline;
    
    bool isSolid;

	FontPrivate(int size)
	    : size(size),
	      hiresMult(1.0f),
	      bold(defaultBold),
	      italic(defaultItalic),
	      outline(defaultOutline),
	      shadow(defaultShadow),
	      color(&colorTmp),
	      outColor(&outColorTmp),
	      colorTmp(*defaultColor),
	      outColorTmp(*defaultOutColor),
	      sdlFont(0),
	      sdlFontOutline(0),
          isSolid(false)
	{}

	FontPrivate(const FontPrivate &other)
	    : name(other.name),
	      size(other.size),
	      hiresMult(1.0f),
	      bold(other.bold),
	      italic(other.italic),
	      outline(other.outline),
	      shadow(other.shadow),
	      color(&colorTmp),
	      outColor(&outColorTmp),
	      colorTmp(*other.color),
	      outColorTmp(*other.outColor),
	      sdlFont(other.sdlFont),
	      sdlFontOutline(other.sdlFontOutline),
          isSolid(false)
	{}

	void operator=(const FontPrivate &o)
	{
		if (size != o.size || name != o.name)
		{
			sdlFont = 0;
			sdlFontOutline = 0;
		}
		if (hiresMult == o.hiresMult)
		{
			sdlFont = sdlFont == 0 ? o.sdlFont : sdlFont;
			sdlFontOutline = sdlFontOutline == 0 ? o.sdlFontOutline : sdlFontOutline;
		}

		 name     =  o.name;
		 size     =  o.size;
		 bold     =  o.bold;
		 italic   =  o.italic;
		 outline  =  o.outline;
		 shadow   =  o.shadow;
		*color    = *o.color;
		*outColor = *o.outColor;

        isSolid = o.isSolid;
	}
};

std::string FontPrivate::defaultName     = "Arial";
int         FontPrivate::defaultSize     = 22;
bool        FontPrivate::defaultBold     = false;
bool        FontPrivate::defaultItalic   = false;
bool        FontPrivate::defaultOutline  = false; /* Inited at runtime */
bool        FontPrivate::defaultShadow   = false; /* Inited at runtime */
Color      *FontPrivate::defaultColor    = &FontPrivate::defaultColorTmp;
Color      *FontPrivate::defaultOutColor = &FontPrivate::defaultOutColorTmp;

Color FontPrivate::defaultColorTmp(255, 255, 255, 255);
Color FontPrivate::defaultOutColorTmp(0, 0, 0, 128);

std::vector<std::string> FontPrivate::initialDefaultNames;

bool Font::isSolid() const {
    return p->isSolid;
}

bool Font::doesExist(const char *name)
{
	if (!name)
		return false;

	return shState->fontState().fontPresent(name);
}

Font::Font(const std::vector<std::string> *names,
           int size)
{
	p = new FontPrivate(size ? size : FontPrivate::defaultSize);

	if (names)
		setName(*names);
	else
		p->name = FontPrivate::defaultName;
}

Font::Font(const Font &other)
{
	p = new FontPrivate(*other.p);
}

Font::~Font()
{
	delete p;
}

const Font &Font::operator=(const Font &o)
{
	*p = *o.p;

	return o;
}

void Font::setName(const std::vector<std::string> &names)
{
	if (pickExistingFontName(names, p->name, shState->fontState()))
	{
		p->sdlFont = 0;
		p->sdlFontOutline = 0;
	}
	p->isSolid = strcmp(p->name.c_str(), "") && shState->config().fontIsSolid(p->name.c_str());
}

void Font::setSize(int value, bool checkIllegal)
{
	if (p->size == value)
		return;

	/* Catch illegal values (according to RMXP) */
	if (value < 6 || value > 96) {
		if (checkIllegal) {
			throw Exception(Exception::ArgumentError, "%s", "bad value for size");
		}
	}

	p->size = value;
	p->sdlFont = 0;
	p->sdlFontOutline = 0;
}

void Font::setHiresMult(float value)
{
	if (p->hiresMult == value)
		return;

	p->hiresMult = value;
	p->sdlFont = 0;
	p->sdlFontOutline = 0;
}

static void guardDisposed() {}

DEF_ATTR_RD_SIMPLE(Font, Size, int, p->size)

DEF_ATTR_SIMPLE(Font, Bold,     bool,    p->bold)
DEF_ATTR_SIMPLE(Font, Italic,   bool,    p->italic)
DEF_ATTR_SIMPLE(Font, Shadow,   bool,    p->shadow)
DEF_ATTR_SIMPLE(Font, Outline,  bool,    p->outline)
DEF_ATTR_SIMPLE(Font, Color,    Color&, *p->color)
DEF_ATTR_SIMPLE(Font, OutColor, Color&, *p->outColor)

DEF_ATTR_SIMPLE_STATIC(Font, DefaultSize,     int,     FontPrivate::defaultSize)
DEF_ATTR_SIMPLE_STATIC(Font, DefaultBold,     bool,    FontPrivate::defaultBold)
DEF_ATTR_SIMPLE_STATIC(Font, DefaultItalic,   bool,    FontPrivate::defaultItalic)
DEF_ATTR_SIMPLE_STATIC(Font, DefaultShadow,   bool,    FontPrivate::defaultShadow)
DEF_ATTR_SIMPLE_STATIC(Font, DefaultOutline,  bool,    FontPrivate::defaultOutline)
DEF_ATTR_SIMPLE_STATIC(Font, DefaultColor,    Color&, *FontPrivate::defaultColor)
DEF_ATTR_SIMPLE_STATIC(Font, DefaultOutColor, Color&, *FontPrivate::defaultOutColor)

void Font::setDefaultName(const std::vector<std::string> &names,
                          const SharedFontState &sfs)
{
	pickExistingFontName(names, FontPrivate::defaultName, sfs);
}

const std::vector<std::string> &Font::getInitialDefaultNames()
{
	return FontPrivate::initialDefaultNames;
}

void Font::initDynAttribs()
{
	p->color = new Color(p->colorTmp);

	if (rgssVer >= 3)
		p->outColor = new Color(p->outColorTmp);;
}

void Font::initDefaultDynAttribs()
{
	FontPrivate::defaultColor = new Color(FontPrivate::defaultColorTmp);

	if (rgssVer >= 3)
		FontPrivate::defaultOutColor = new Color(FontPrivate::defaultOutColorTmp);
}

void Font::initDefaults(const SharedFontState &sfs)
{
	std::vector<std::string> &names = FontPrivate::initialDefaultNames;

	switch (rgssVer)
	{
	case 1 :
		// FIXME: Japanese version has "MS PGothic" instead
		names.push_back("Arial");
		break;

	case 2 :
		names.push_back("UmePlus Gothic");
		names.push_back("MS Gothic");
		names.push_back("Courier New");
		FontPrivate::defaultSize = 20;
		break;

	default:
	case 3 :
		names.push_back("VL Gothic");
		FontPrivate::defaultSize = 24;
	}

	setDefaultName(names, sfs);

	FontPrivate::defaultOutline = (rgssVer >= 3 ? true : false);
	FontPrivate::defaultShadow  = (rgssVer == 2 ? true : false);
}

_TTF_Font *Font::getSdlFont(int outline_size)
{
	_TTF_Font **font;
	if (outline_size == 0)
		font = &p->sdlFont;
	else
		font = &p->sdlFontOutline;

	if (!*font)
		*font = shState->fontState().getFont(p->name.c_str(),
		                                     p->size, p->hiresMult, outline_size);

	if(outline_size && TTF_GetFontOutline(*font) != outline_size)
		TTF_SetFontOutline(*font, outline_size);

	int style = TTF_STYLE_NORMAL;

	if (p->bold)
		style |= TTF_STYLE_BOLD;

	if (p->italic)
		style |= TTF_STYLE_ITALIC;

	TTF_SetFontStyle(*font, style);

	return *font;
}
