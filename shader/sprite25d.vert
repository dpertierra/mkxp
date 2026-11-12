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

attribute vec2 position;
attribute vec2 texCoord;
varying vec2 v_texCoord;
varying vec2 v_patCoord;

vec4 tableValue(int index) {
    if (index < 32) {
        if (index < 16) {
            if (index < 8) {
                if (index < 4) {
                    if (index < 2) {
                        if (index < 1) {
                            return perspectiveTable[0];
                        } else {
                            return perspectiveTable[1];
                        }
                    } else {
                        if (index < 3) {
                            return perspectiveTable[2];
                        } else {
                            return perspectiveTable[3];
                        }
                    }
                } else {
                    if (index < 6) {
                        if (index < 5) {
                            return perspectiveTable[4];
                        } else {
                            return perspectiveTable[5];
                        }
                    } else {
                        if (index < 7) {
                            return perspectiveTable[6];
                        } else {
                            return perspectiveTable[7];
                        }
                    }
                }
            } else {
                if (index < 12) {
                    if (index < 10) {
                        if (index < 9) {
                            return perspectiveTable[8];
                        } else {
                            return perspectiveTable[9];
                        }
                    } else {
                        if (index < 11) {
                            return perspectiveTable[10];
                        } else {
                            return perspectiveTable[11];
                        }
                    }
                } else {
                    if (index < 14) {
                        if (index < 13) {
                            return perspectiveTable[12];
                        } else {
                            return perspectiveTable[13];
                        }
                    } else {
                        if (index < 15) {
                            return perspectiveTable[14];
                        } else {
                            return perspectiveTable[15];
                        }
                    }
                }
            }
        } else {
            if (index < 24) {
                if (index < 20) {
                    if (index < 18) {
                        if (index < 17) {
                            return perspectiveTable[16];
                        } else {
                            return perspectiveTable[17];
                        }
                    } else {
                        if (index < 19) {
                            return perspectiveTable[18];
                        } else {
                            return perspectiveTable[19];
                        }
                    }
                } else {
                    if (index < 22) {
                        if (index < 21) {
                            return perspectiveTable[20];
                        } else {
                            return perspectiveTable[21];
                        }
                    } else {
                        if (index < 23) {
                            return perspectiveTable[22];
                        } else {
                            return perspectiveTable[23];
                        }
                    }
                }
            } else {
                if (index < 28) {
                    if (index < 26) {
                        if (index < 25) {
                            return perspectiveTable[24];
                        } else {
                            return perspectiveTable[25];
                        }
                    } else {
                        if (index < 27) {
                            return perspectiveTable[26];
                        } else {
                            return perspectiveTable[27];
                        }
                    }
                } else {
                    if (index < 30) {
                        if (index < 29) {
                            return perspectiveTable[28];
                        } else {
                            return perspectiveTable[29];
                        }
                    } else {
                        if (index < 31) {
                            return perspectiveTable[30];
                        } else {
                            return perspectiveTable[31];
                        }
                    }
                }
            }
        }
    } else {
        if (index < 48) {
            if (index < 40) {
                if (index < 36) {
                    if (index < 34) {
                        if (index < 33) {
                            return perspectiveTable[32];
                        } else {
                            return perspectiveTable[33];
                        }
                    } else {
                        if (index < 35) {
                            return perspectiveTable[34];
                        } else {
                            return perspectiveTable[35];
                        }
                    }
                } else {
                    if (index < 38) {
                        if (index < 37) {
                            return perspectiveTable[36];
                        } else {
                            return perspectiveTable[37];
                        }
                    } else {
                        if (index < 39) {
                            return perspectiveTable[38];
                        } else {
                            return perspectiveTable[39];
                        }
                    }
                }
            } else {
                if (index < 44) {
                    if (index < 42) {
                        if (index < 41) {
                            return perspectiveTable[40];
                        } else {
                            return perspectiveTable[41];
                        }
                    } else {
                        if (index < 43) {
                            return perspectiveTable[42];
                        } else {
                            return perspectiveTable[43];
                        }
                    }
                } else {
                    if (index < 46) {
                        if (index < 45) {
                            return perspectiveTable[44];
                        } else {
                            return perspectiveTable[45];
                        }
                    } else {
                        if (index < 47) {
                            return perspectiveTable[46];
                        } else {
                            return perspectiveTable[47];
                        }
                    }
                }
            }
        } else {
            if (index < 56) {
                if (index < 52) {
                    if (index < 50) {
                        if (index < 49) {
                            return perspectiveTable[48];
                        } else {
                            return perspectiveTable[49];
                        }
                    } else {
                        if (index < 51) {
                            return perspectiveTable[50];
                        } else {
                            return perspectiveTable[51];
                        }
                    }
                } else {
                    if (index < 54) {
                        if (index < 53) {
                            return perspectiveTable[52];
                        } else {
                            return perspectiveTable[53];
                        }
                    } else {
                        if (index < 55) {
                            return perspectiveTable[54];
                        } else {
                            return perspectiveTable[55];
                        }
                    }
                }
            } else {
                if (index < 60) {
                    if (index < 58) {
                        if (index < 57) {
                            return perspectiveTable[56];
                        } else {
                            return perspectiveTable[57];
                        }
                    } else {
                        if (index < 59) {
                            return perspectiveTable[58];
                        } else {
                            return perspectiveTable[59];
                        }
                    }
                } else {
                    if (index < 62) {
                        if (index < 61) {
                            return perspectiveTable[60];
                        } else {
                            return perspectiveTable[61];
                        }
                    } else {
                        if (index < 63) {
                            return perspectiveTable[62];
                        } else {
                            return perspectiveTable[63];
                        }
                    }
                }
            }
        }
    }
}

vec4 sampleTableIndex(float index) {
    float sampleIndex = clamp(index, 0.0, 62.0);
    int base = int(floor(sampleIndex));
    float t = sampleIndex - float(base);
    if (index < 0.0) t = index;
    else if (index > 62.0) t = index - 62.0;
    return mix(tableValue(base), tableValue(base + 1), t);
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
    if (perspectiveMode == 1) {
        vec4 p = samplePerspective(point.y);
        point.x = centerX + (point.x - centerX) * p.y;
        point.y = p.x;
    } else {
        float feet = perspectiveAnchor;
        vec4 p = samplePerspective(feet);
        float unit = p.y + (p.z - p.y) * perspectiveGain;
        point.x = centerX + (point.x - centerX) * p.y;
        point.y = p.x - (feet - point.y) * unit - perspectiveLift;
    }
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
