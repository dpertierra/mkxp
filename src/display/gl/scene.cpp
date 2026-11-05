/*
** scene.cpp
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

#include "scene.h"
#include <algorithm>

#include "sharedstate.h"

Scene::Scene()
    : needsSort(false)
{}

Scene::~Scene()
{
	/* Ensure elements don't unlink from a destructed Scene */
	IntruListLink<SceneElement> *iter;

	for (iter = elements.begin(); iter != elements.end(); iter = iter->next)
	{
		iter->data->scene = 0;
	}
}

void Scene::insert(SceneElement &element)
{
	elements.append(element.link);
	needsSort = true;
}

/* The lazy sort yields the same order as scanning for the position,
 * as long as 'after' sorts before 'element' (true for ZLayers) */
void Scene::insertAfter(SceneElement &element, SceneElement &)
{
	insert(element);
}

void Scene::reinsert(SceneElement &element)
{
	elements.remove(element.link);
	insert(element);
}

void Scene::sortIfNeeded()
{
	if (!needsSort)
		return;

	needsSort = false;

	sortBuf.clear();

	IntruListLink<SceneElement> *iter;
	for (iter = elements.begin(); iter != elements.end(); iter = iter->next)
		sortBuf.push_back(iter->data);

	std::sort(sortBuf.begin(), sortBuf.end(),
	          [](const SceneElement *a, const SceneElement *b) { return *a < *b; });

	elements.clear();
	for (SceneElement *e : sortBuf)
		elements.append(e->link);
}

void Scene::notifyGeometryChange()
{
	IntruListLink<SceneElement> *iter;

	for (iter = elements.begin(); iter != elements.end(); iter = iter->next)
	{
		iter->data->onGeometryChange(geometry);
	}
}

void Scene::composite()
{
	sortIfNeeded();

	IntruListLink<SceneElement> *iter;

	for (iter = elements.begin(); iter != elements.end(); iter = iter->next)
	{
		SceneElement *e = iter->data;

		if (e->visible)
			e->draw();
	}
}


SceneElement::SceneElement(Scene &scene, int z, int spriteY)
    : link(this),
      creationStamp(shState->genTimeStamp()),
      z(z),
      visible(true),
      scene(&scene),
      spriteY(spriteY)
{
	scene.insert(*this);
}

SceneElement::~SceneElement()
{
	unlink();
}

void SceneElement::setScene(Scene &scene)
{
	unlink();

	this->scene = &scene;

	scene.insert(*this);

	onGeometryChange(scene.getGeometry());
}

int SceneElement::getZ() const
{
	aboutToAccess();

	return z;
}

void SceneElement::setZ(int value)
{
	aboutToAccess();

	if (z == value)
		return;

	z = value;
	scene->needsSort = true;
}

bool SceneElement::getVisible() const
{
	aboutToAccess();

	return visible;
}

void SceneElement::setVisible(bool value)
{
	aboutToAccess();

	visible = value;
}

bool SceneElement::operator<(const SceneElement &o) const
{
	/* Element draw order is decided by their Z value.
	 * If two Z values are equal, the later created object
	 * has priority */

	if (z <= o.z)
	{
		if (z == o.z)
		{
			if (rgssVer >= 2)
			{
				/* RGSS2: If two sprites' Z values collide,
				 * their Y coordinates decide draw order. Only
				 * on equal Y does the creation time take effect */
				if (spriteY != o.spriteY)
					return (spriteY < o.spriteY);
			}

			return (creationStamp < o.creationStamp);
		}

		return true;
	}

	return false;
}

void SceneElement::setSpriteY(int value)
{
	if (spriteY == value)
		return;

	spriteY = value;

	/* spriteY only takes part in ordering from RGSS2 on */
	if (rgssVer >= 2)
		scene->needsSort = true;
}

void SceneElement::unlink()
{
	if (scene)
		scene->elements.remove(link);
}
