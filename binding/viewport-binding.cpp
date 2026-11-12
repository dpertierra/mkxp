/*
 ** viewport-binding.cpp
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

#include "binding-types.h"
#include "binding-util.h"
#include "disposable-binding.h"
#include "flashable-binding.h"
#include "sceneelement-binding.h"
#include "sharedstate.h"
#include "viewport.h"
#include <algorithm>
#include <cmath>

#if RAPI_FULL > 187
DEF_TYPE(Viewport);
#else
DEF_ALLOCFUNC(Viewport);
#endif

RB_METHOD(viewportInitialize) {
    Viewport *v;
    
    if (argc == 0 && rgssVer >= 3) {
        GFX_LOCK;
        v = new Viewport();
    } else if (argc == 1) {
        /* The rect arg is only used to init the viewport,
         * and does NOT replace its 'rect' property */
        VALUE rectObj;
        Rect *rect;
        
        rb_get_args(argc, argv, "o", &rectObj RB_ARG_END);
        
        rect = getPrivateDataCheck<Rect>(rectObj, RectType);
        
        GFX_LOCK;
        v = new Viewport(rect);
    } else {
        int x, y, width, height;
        
        rb_get_args(argc, argv, "iiii", &x, &y, &width, &height RB_ARG_END);
        GFX_LOCK;
        v = new Viewport(x, y, width, height);
    }
    
    setPrivateData(self, v);
    
    /* Wrap property objects */
    v->initDynAttribs();
    
    wrapProperty(self, &v->getRect(), "rect", RectType);
    wrapProperty(self, &v->getColor(), "color", ColorType);
    wrapProperty(self, &v->getTone(), "tone", ToneType);
    
    GFX_UNLOCK;
    return self;
}

DEF_GFX_PROP_OBJ_VAL(Viewport, Rect, Rect, "rect")
DEF_GFX_PROP_OBJ_VAL(Viewport, Color, Color, "color")
DEF_GFX_PROP_OBJ_VAL(Viewport, Tone, Tone, "tone")

DEF_GFX_PROP_I(Viewport, OX)
DEF_GFX_PROP_I(Viewport, OY)

RB_METHOD_GUARD(viewportPerspectiveSet) {
    VALUE segmentsObj, tableObj;
    rb_get_args(argc, argv, "oo", &segmentsObj, &tableObj RB_ARG_END);
    Viewport *v = getPrivateData<Viewport>(self);
    v->guardDisposed();
    if (!RB_TYPE_P(segmentsObj, RUBY_T_ARRAY) || RARRAY_LEN(segmentsObj) < 1 ||
        RARRAY_LEN(segmentsObj) > PERSPECTIVE_MAX_SEGMENTS)
        rb_raise(rb_eArgError, "perspective segments must contain 1 to 4 entries");
    if (!RB_TYPE_P(tableObj, RUBY_T_ARRAY) || RARRAY_LEN(tableObj) != PERSPECTIVE_SAMPLES * 3)
        rb_raise(rb_eArgError, "perspective table must contain exactly 192 numbers");
    PerspectiveSegment segments[PERSPECTIVE_MAX_SEGMENTS];
    int totalSamples = 0;
    for (int i = 0; i < RARRAY_LEN(segmentsObj); ++i) {
        VALUE segment = rb_ary_entry(segmentsObj, i);
        if (!RB_TYPE_P(segment, RUBY_T_ARRAY) || RARRAY_LEN(segment) != 3)
            rb_raise(rb_eArgError, "each perspective segment must be [y_start, y_step, count]");
        VALUE yStartObj = rb_ary_entry(segment, 0), yStepObj = rb_ary_entry(segment, 1), countObj = rb_ary_entry(segment, 2);
        if (!RB_TYPE_P(yStartObj, RUBY_T_FLOAT) || !RB_TYPE_P(yStepObj, RUBY_T_FLOAT) || !RB_INTEGER_TYPE_P(countObj))
            rb_raise(rb_eTypeError, "segment start and step must be floats, count must be an integer");
        double ys = NUM2DBL(yStartObj), step = NUM2DBL(yStepObj);
        int count = NUM2INT(countObj);
        if (!std::isfinite(ys) || !std::isfinite(step) || !std::isfinite((float)ys) ||
            !std::isfinite((float)step) || step <= 0 || count < 2)
            rb_raise(rb_eArgError, "segment values must be finite, step positive and count at least 2");
        if (count > PERSPECTIVE_SAMPLES - totalSamples)
            rb_raise(rb_eArgError, "perspective segment counts must sum to 64");
        segments[i].yStart = (float)ys;
        segments[i].yStep = (float)step;
        segments[i].firstIndex = totalSamples;
        segments[i].count = count;
        totalSamples += count;
        if (i > 0) {
            double expected = segments[i - 1].yStart + segments[i - 1].yStep * segments[i - 1].count;
            double tolerance = std::max(0.0001, std::abs(expected) * 0.000001);
            if (std::abs(ys - expected) > tolerance)
                rb_raise(rb_eArgError, "perspective segments must be contiguous and increasing");
        }
    }
    if (totalSamples != PERSPECTIVE_SAMPLES)
        rb_raise(rb_eArgError, "perspective segment counts must sum to 64");
    float values[PERSPECTIVE_SAMPLES * 3];
    for (int i = 0; i < PERSPECTIVE_SAMPLES * 3; ++i) {
        VALUE entry = rb_ary_entry(tableObj, i);
        if (!RB_TYPE_P(entry, RUBY_T_FLOAT))
            rb_raise(rb_eTypeError, "perspective table values must be floats");
        double value = NUM2DBL(entry);
        if (!std::isfinite(value) || !std::isfinite((float)value))
            rb_raise(rb_eArgError, "perspective table values must be finite floats");
        values[i] = (float)value;
    }
    GFX_LOCK;
    v->setPerspective(segments, (int)RARRAY_LEN(segmentsObj), values);
    GFX_UNLOCK;
    return Qnil;
} RB_METHOD_GUARD_END

RB_METHOD_GUARD(viewportPerspectiveClear) {
    RB_UNUSED_PARAM;
    Viewport *v = getPrivateData<Viewport>(self);
    v->guardDisposed();
    GFX_LOCK; v->clearPerspective(); GFX_UNLOCK;
    return Qnil;
} RB_METHOD_GUARD_END

RB_METHOD_GUARD(viewportPerspectiveQ) {
    RB_UNUSED_PARAM;
    Viewport *v = getPrivateData<Viewport>(self);
    v->guardDisposed();
    GFX_LOCK;
    bool enabled = v->perspectiveTable().enabled && shState->shaders().perspectiveSpriteAvailable();
    GFX_UNLOCK;
    return enabled ? Qtrue : Qfalse;
} RB_METHOD_GUARD_END

RB_METHOD_GUARD(viewportPerspectiveCenterXSet) {
    VALUE valueObj;
    rb_get_args(argc, argv, "o", &valueObj RB_ARG_END);
    Viewport *v = getPrivateData<Viewport>(self);
    v->guardDisposed();
    if (!(RB_TYPE_P(valueObj, RUBY_T_FLOAT) || RB_INTEGER_TYPE_P(valueObj)))
        rb_raise(rb_eTypeError, "perspective_center_x must be numeric");
    double value = NUM2DBL(valueObj);
    if (!std::isfinite(value) || !std::isfinite((float)value))
        rb_raise(rb_eArgError, "perspective_center_x must be finite");
    GFX_LOCK; v->setPerspectiveCenterX((float)value); GFX_UNLOCK;
    return valueObj;
} RB_METHOD_GUARD_END

void viewportBindingInit() {
    VALUE klass = rb_define_class("Viewport", rb_cObject);
#if RAPI_FULL > 187
    rb_define_alloc_func(klass, classAllocate<&ViewportType>);
#else
    rb_define_alloc_func(klass, ViewportAllocate);
#endif
    
    disposableBindingInit<Viewport>(klass);
    flashableBindingInit<Viewport>(klass);
    sceneElementBindingInit<Viewport>(klass);
    
    _rb_define_method(klass, "initialize", viewportInitialize);
    
    INIT_PROP_BIND(Viewport, Rect, "rect");
    INIT_PROP_BIND(Viewport, OX, "ox");
    INIT_PROP_BIND(Viewport, OY, "oy");
    INIT_PROP_BIND(Viewport, Color, "color");
    INIT_PROP_BIND(Viewport, Tone, "tone");
    _rb_define_method(klass, "perspective_set", viewportPerspectiveSet);
    _rb_define_method(klass, "perspective_clear", viewportPerspectiveClear);
    _rb_define_method(klass, "perspective?", viewportPerspectiveQ);
    _rb_define_method(klass, "perspective_center_x=", viewportPerspectiveCenterXSet);
}
