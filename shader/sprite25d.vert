uniform mat4 projMat;
uniform mat4 spriteMat;
uniform vec2 texSizeInv;
uniform vec2 patternSizeInv;
uniform vec2 patternScroll;
uniform vec2 patternZoom;
uniform bool renderPattern;
uniform bool patternTile;
uniform vec4 perspectiveTable[64];
uniform vec4 perspectiveSegments[4];
uniform int segmentCount;
uniform float centerX;
uniform int perspectiveMode;
uniform float perspectiveAnchor;
uniform float perspectiveGain;
uniform float perspectiveLift;
uniform vec2 viewportOffset;

attribute vec2 position;
attribute vec2 texCoord;
varying vec2 v_texCoord;
varying vec2 v_patCoord;

vec4 sampleTableIndex(float index) {
    float sampleIndex = clamp(index, 0.0, 62.0);
    int base = int(floor(sampleIndex));
    float t = sampleIndex - float(base);
    if (index < 0.0) t = index;
    else if (index > 62.0) t = index - 62.0;
    return mix(perspectiveTable[base], perspectiveTable[base + 1], t);
}


vec4 samplePerspective(float y) {
    float index;
    if (segmentCount > 3 && y >= perspectiveSegments[3].x)
        index = (y - perspectiveSegments[3].x) / perspectiveSegments[3].y + perspectiveSegments[3].z;
    else if (segmentCount > 2 && y >= perspectiveSegments[2].x)
        index = (y - perspectiveSegments[2].x) / perspectiveSegments[2].y + perspectiveSegments[2].z;
    else if (segmentCount > 1 && y >= perspectiveSegments[1].x)
        index = (y - perspectiveSegments[1].x) / perspectiveSegments[1].y + perspectiveSegments[1].z;
    else
        index = (y - perspectiveSegments[0].x) / perspectiveSegments[0].y + perspectiveSegments[0].z;
    return sampleTableIndex(index);
}
void main() {
    vec4 point = spriteMat * vec4(position, 0.0, 1.0);
    point.xy -= viewportOffset;
    if (perspectiveMode == 1) {
        vec4 p = samplePerspective(point.y);
        point.x = centerX + (point.x - centerX) * p.y;
        point.y = p.x;
    } else if (perspectiveMode == 2) {
        float feet = perspectiveAnchor;
        vec4 p = samplePerspective(feet);
        float unit = p.y + (p.z - p.y) * perspectiveGain;
        point.x = centerX + (point.x - centerX) * p.y;
        point.y = p.x - (feet - point.y) * unit - perspectiveLift;
    }
    point.xy += viewportOffset;
    gl_Position = projMat * point;
    v_texCoord = texCoord * texSizeInv;
    if (renderPattern) {
        if (patternTile) {
            vec2 scroll = patternScroll * (patternSizeInv / texSizeInv);
            v_patCoord = (texCoord * (patternSizeInv / patternZoom)) - (scroll * patternSizeInv);
        } else {
            vec2 scroll = patternScroll * (patternSizeInv / texSizeInv);
            v_patCoord = (texCoord * (texSizeInv / patternZoom)) - (scroll * texSizeInv);
        }
    }
}
