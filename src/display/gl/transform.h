/*
** tilequad.h
**
** This file is part of mkxp. It is based on parts of "SFML 2.0" (license further below)
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

////////////////////////////////////////////////////////////
//
// SFML - Simple and Fast Multimedia Library
// Copyright (C) 2007-2012 Laurent Gomila (laurent.gom@gmail.com)
//
// This software is provided 'as-is', without any express or implied warranty.
// In no event will the authors be held liable for any damages arising from the use of this software.
//
// Permission is granted to anyone to use this software for any purpose,
// including commercial applications, and to alter it and redistribute it freely,
// subject to the following restrictions:
//
// 1. The origin of this software must not be misrepresented;
//    you must not claim that you wrote the original software.
//    If you use this software in a product, an acknowledgment
//    in the product documentation would be appreciated but is not required.
//
// 2. Altered source versions must be plainly marked as such,
//    and must not be misrepresented as being the original software.
//
// 3. This notice may not be removed or altered from any source distribution.
//
////////////////////////////////////////////////////////////


#ifndef TRANSFORM_H
#define TRANSFORM_H

#include "etc-internal.h"

#include <math.h>
#include <string.h>

class Transform
{
public:
	/* Smallest allowed taper. The projective divisor runs from 1 at the top
	 * edge to `taper` at the bottom one, so keeping taper positive is the whole
	 * w <= 0 guard: no vertex can ever land on or behind the camera plane. */
	static constexpr float minTaper = 0.01f;

	Transform()
	    : scale(1, 1),
	      rotation(0),
	      taper(1),
	      localSize(0, 0),
	      dirty(true)
	{
		memset(matrix, 0, sizeof(matrix));

		matrix[10] = 1;
		matrix[15] = 1;
	}

	Vec2 &getPosition() { return position; }
	Vec2 &getOrigin()   { return origin;   }
	Vec2 &getScale()    { return scale;    }
	float getRotation() { return rotation; }
	float getTaper()    { return taper;    }

	Vec2i getPositionI() const
	{
		return Vec2i(position.x, position.y);
	}

	Vec2i getOriginI() const
	{
		return Vec2i(origin.x, origin.y);
	}

	void setPosition(const Vec2 &value)
	{
		position = value;
		dirty = true;
	}

	void setOrigin(const Vec2 &value)
	{
		origin = value;
		dirty = true;
	}

	void setScale(const Vec2 &value)
	{
		scale = value;
		dirty = true;
	}

	void setRotation(float value)
	{
		rotation = value;
		dirty = true;
	}

	/* Width of the top edge relative to the bottom one. 1 = no taper (a plain
	 * affine transform, bit for bit what this class did before). Values below 1
	 * make the top narrower, which is what a ground tile seen by a camera
	 * looking down the map needs.
	 *
	 * Ignored while the sprite is rotated: the closed form below assumes the
	 * quad's parallel edges stay horizontal. */
	void setTaper(float value)
	{
		if (value < minTaper)
			value = minTaper;

		taper = value;
		dirty = true;
	}

	/* Untransformed size of the quad this matrix is applied to. Needed because
	 * the taper is defined against the quad's own edges, and the matrix alone
	 * cannot know how far they sit. */
	void setLocalSize(const Vec2 &value)
	{
		localSize = value;

		if (taper != 1)
			dirty = true;
	}

	void setGlobalOffset(const Vec2i &value)
	{
		offset = value;
		dirty = true;
	}

	const float *getMatrix()
	{
		if (dirty)
		{
			updateMatrix();
			dirty = false;
		}

		return matrix;
	}

private:
	void updateMatrix()
	{
		if (rotation >= 360 || rotation < -360)
			rotation = (float) fmod(rotation, 360);

        // RGSS allows negative angles
		//if (rotation < 0)
		//	rotation += 360;

		float angle  = rotation * 3.141592654f / 180.0f;
		float cosine = (float) cos(angle);
		float sine   = (float) sin(angle);
		float sxc    = scale.x * cosine;
		float syc    = scale.y * cosine;
		float sxs    = scale.x * sine;
		float sys    = scale.y * sine;
		float tx     = -origin.x * sxc - origin.y * sys + position.x + offset.x;
		float ty     =  origin.x * sxs - origin.y * syc + position.y + offset.y;

		matrix[0]  =  sxc;
		matrix[1]  = -sxs;
		matrix[4]  =  sys;
		matrix[5]  =  syc;
		matrix[12] =  tx;
		matrix[13] =  ty;

		/* Projective row. Zero unless a taper is set, which leaves the matrix
		 * exactly as it has always been. */
		matrix[3] = 0;
		matrix[7] = 0;

		if (taper == 1 || rotation != 0 || localSize.x <= 0 || localSize.y <= 0)
			return;

		applyTaper(tx, ty);
	}

	/* Turns the affine matrix built above into a projective one that draws the
	 * quad as a trapezoid: same bottom edge, top edge `taper` times as wide,
	 * narrowing about the quad's vertical centre line.
	 *
	 * The vertex shader computes gl_Position = projMat * spriteMat * vec4(u, v, 0, 1),
	 * and projMat (orthographic) leaves the bottom row alone, so
	 *
	 *     w(v) = matrix[3]*u + matrix[7]*v + 1
	 *
	 * reaches the rasteriser as the perspective divisor. Keeping matrix[3] at 0
	 * makes w depend on v only, so every point on a given scanline of the quad
	 * shares one divisor and the edges stay straight.
	 *
	 * Solving for "top edge half-width is `taper` times the bottom one" gives
	 * matrix[7] = (taper - 1) / height, and the remaining entries below just
	 * pre-multiply the affine result so that, after the divide, the four
	 * corners land exactly where the untapered quad's corners would have.
	 * Texture coordinates need no help: they travel as a varying, which GL
	 * already interpolates with perspective correction. */
	void applyTaper(float tx, float ty)
	{
		float wl = localSize.x * scale.x;   /* on-screen width  */
		float hl = localSize.y;             /* untransformed height (the v range) */
		float cx = tx + (wl / 2);           /* centre line to taper about */
		float yb = ty + (localSize.y * scale.y);   /* bottom edge, untapered */

		float m7 = (taper - 1) / hl;

		matrix[0]  = scale.x * taper;
		matrix[4]  = m7 * cx;
		matrix[12] = cx - ((wl / 2) * taper);

		matrix[1]  = 0;
		matrix[5]  = ((yb * taper) - ty) / hl;
		matrix[13] = ty;

		matrix[7]  = m7;
	}

	Vec2 position;
	Vec2 origin;
	Vec2 scale;
	float rotation;
	float taper;
	Vec2 localSize;

	/* Silently added to position */
	Vec2i offset;

	float matrix[16];

	bool dirty;
};

#endif // TRANSFORM_H
