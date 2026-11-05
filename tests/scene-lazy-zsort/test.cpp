/* Standalone check that lazy scene sorting yields the same draw order as
 * the former eager insert/reinsert. Uses the real intrulist.h and a copy
 * of Scene/SceneElement ordering logic (the real classes need shState).
 *
 * Build: g++ -O2 -std=c++17 -I../../src/util test.cpp -o /tmp/scene-test && /tmp/scene-test
 */
#include <algorithm>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <memory>
#include <random>
#include <vector>

#include "intrulist.h"

static int rgssVer = 1;
static unsigned stampCounter = 0;

struct Scene;

struct Elem
{
	IntruListLink<Elem> link;
	const unsigned creationStamp;
	int z, spriteY;
	Scene *scene;

	Elem(Scene &s, int z, int y);
	~Elem();

	/* Verbatim from SceneElement::operator< */
	bool operator<(const Elem &o) const
	{
		if (z <= o.z)
		{
			if (z == o.z)
			{
				if (rgssVer >= 2 && spriteY != o.spriteY)
					return spriteY < o.spriteY;
				return creationStamp < o.creationStamp;
			}
			return true;
		}
		return false;
	}
};

struct Scene
{
	bool lazy;
	bool needsSort = false;
	IntruList<Elem> elements;
	std::vector<Elem*> sortBuf;

	explicit Scene(bool lazy) : lazy(lazy) {}
	~Scene() { for (auto *i = elements.begin(); i != elements.end(); i = i->next) i->data->scene = 0; }

	/* Old algorithm */
	void insertOld(Elem &element)
	{
		for (auto *it = elements.begin(); it != elements.end(); it = it->next)
			if (element < *it->data) { elements.insertBefore(element.link, *it); return; }
		elements.append(element.link);
	}
	void insertAfterOld(Elem &element, Elem &after)
	{
		for (auto *it = &after.link; it != elements.end(); it = it->next)
			if (element < *it->data) { elements.insertBefore(element.link, *it); return; }
		elements.append(element.link);
	}

	void insert(Elem &e) { if (lazy) { elements.append(e.link); needsSort = true; } else insertOld(e); }
	void insertAfter(Elem &e, Elem &a) { if (lazy) insert(e); else insertAfterOld(e, a); }
	void reinsert(Elem &e) { elements.remove(e.link); insert(e); }

	void sortIfNeeded()
	{
		if (!needsSort) return;
		needsSort = false;
		sortBuf.clear();
		for (auto *i = elements.begin(); i != elements.end(); i = i->next) sortBuf.push_back(i->data);
		std::sort(sortBuf.begin(), sortBuf.end(), [](const Elem *a, const Elem *b) { return *a < *b; });
		elements.clear();
		for (Elem *e : sortBuf) elements.append(e->link);
	}

	void setZ(Elem &e, int v)
	{
		if (e.z == v) return;
		e.z = v;
		if (lazy) needsSort = true; else reinsert(e);
	}
	void setSpriteY(Elem &e, int v)
	{
		if (lazy)
		{
			if (e.spriteY == v) return;
			e.spriteY = v;
			if (rgssVer >= 2) needsSort = true;
		}
		else { e.spriteY = v; reinsert(e); }
	}

	std::vector<unsigned> order()
	{
		if (lazy) sortIfNeeded();
		std::vector<unsigned> r;
		for (auto *i = elements.begin(); i != elements.end(); i = i->next) r.push_back(i->data->creationStamp);
		return r;
	}
};

Elem::Elem(Scene &s, int z, int y) : link(this), creationStamp(stampCounter++), z(z), spriteY(y), scene(&s) { s.insert(*this); }
Elem::~Elem() { if (scene) scene->elements.remove(link); }

static int run(int ver, unsigned seed, int ops)
{
	rgssVer = ver;
	std::mt19937 rng(seed);
	auto R = [&](int n) { return (int)(rng() % n); };
	Scene so(false), sn(true);
	std::vector<std::unique_ptr<Elem>> eo, en; /* parallel, same stamps */
	std::vector<std::unique_ptr<Elem>> lo, ln; /* "zlayers": strictly increasing z, created in order */
	unsigned nextStamp;
	auto make = [&](int z, int y) {
		nextStamp = stampCounter;
		eo.emplace_back(new Elem(so, z, y));
		stampCounter = nextStamp;
		en.emplace_back(new Elem(sn, z, y));
	};
	for (int i = 0; i < 8; i++)
	{
		unsigned st = stampCounter;
		lo.emplace_back(new Elem(so, 0, 0)); stampCounter = st;
		ln.emplace_back(new Elem(sn, 0, 0));
	}
	int bad = 0;
	for (int k = 0; k < ops; k++)
	{
		int op = R(100);
		if (op < 12 || eo.empty()) make(R(40) - 10, R(30));
		else if (op < 20) { int i = R(eo.size()); std::swap(eo[i], eo.back()); std::swap(en[i], en.back()); eo.pop_back(); en.pop_back(); }
		else if (op < 55) { int i = R(eo.size()); int v = R(4) ? eo[i]->z + R(5) - 2 : R(400) - 100; so.setZ(*eo[i], v); sn.setZ(*en[i], v); }
		else if (op < 70) { int i = R(eo.size()); int v = R(30); so.setSpriteY(*eo[i], v); sn.setSpriteY(*en[i], v); }
		else if (op < 78)
		{ /* tilemap updateZOrder: unlink all, recompute z, insertAfter chain */
			int base = R(200) - 50;
			for (size_t i = 0; i < lo.size(); i++) { so.elements.remove(lo[i]->link); sn.elements.remove(ln[i]->link); }
			for (size_t i = 0; i < lo.size(); i++)
			{
				lo[i]->z = ln[i]->z = base + 32 * (int)i;
				if (i) { so.insertAfter(*lo[i], *lo[i - 1]); sn.insertAfter(*ln[i], *ln[i - 1]); }
				else { so.insert(*lo[i]); sn.insert(*ln[i]); }
			}
		}
		if (R(100) < 15 && so.order() != sn.order()) bad++; /* composite point */
	}
	if (so.order() != sn.order()) bad++;
	return bad;
}

static double bench(bool lazy)
{
	rgssVer = 1;
	std::mt19937 rng(7);
	Scene s(lazy);
	std::vector<std::unique_ptr<Elem>> e;
	for (int i = 0; i < 5000; i++) e.emplace_back(new Elem(s, rng() % 2000, 0));
	auto t0 = std::chrono::steady_clock::now();
	for (int f = 0; f < 100; f++)
	{
		for (int i = 0; i < 3000; i++) s.setZ(*e[rng() % e.size()], rng() % 2000);
		s.order(); /* composite */
	}
	return std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - t0).count();
}

int main()
{
	int bad = 0;
	for (int ver : {1, 3})
		for (unsigned seed = 1; seed <= 4; seed++)
			bad += run(ver, seed, 25000); /* 8 runs x 25k = 200k ops */
	std::printf("order mismatches: %d\n", bad);
	double o = bench(false), n = bench(true);
	std::printf("bench 5000 elems, 3000 setZ/frame x 100 frames: old %.1f ms, lazy %.1f ms (%.1fx)\n", o, n, o / n);
	return bad ? 1 : 0;
}
