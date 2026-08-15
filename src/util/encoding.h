//
//  encoding.h
//  mkxp-z
//
//  Created by ゾロアーク on 6/22/21.
//

#ifndef encoding_h
#define encoding_h

#include <string>
#include <string.h>

#include "util/encoding.h"
#include <iconv.h>
#include <uchardet.h>
#include <errno.h>

namespace Encoding {

static bool isValidUTF8(const std::string &str) {
    const unsigned char *s = (const unsigned char *)str.data();
    size_t i = 0, n = str.size();
    while (i < n) {
        if (s[i] < 0x80) { i++; continue; }
        if (s[i] < 0xC2) return false; // stray continuation or overlong lead
        int len;
        if ((s[i] & 0xE0) == 0xC0) len = 2;
        else if ((s[i] & 0xF0) == 0xE0) len = 3;
        else if ((s[i] & 0xF8) == 0xF0) len = 4;
        else return false;
        if (i + len > n) return false;
        for (int j = 1; j < len; j++)
            if ((s[i + j] & 0xC0) != 0x80) return false;
        i += len;
    }
    return true;
}

static std::string getCharset(std::string &str) {
    uchardet_t ud = uchardet_new();
    uchardet_handle_data(ud, str.c_str(), str.length());
    uchardet_data_end(ud);
    
    std::string ret(uchardet_get_charset(ud));
    uchardet_delete(ud);
    
    if (ret.empty())
        throw Exception(Exception::MKXPError, "Could not detect string encoding", str.c_str());
    return ret;
}

static std::string convertString(std::string &str, const char *charset) {
    // Conversion doesn't need to happen if it's already UTF-8
    if (!strcmp(charset, "UTF-8") || !strcmp(charset, "ASCII")) {
        return std::string(str);
    }
    
    iconv_t cd = iconv_open("UTF-8", charset);
    
    size_t inLen = str.size();
    size_t outLen = inLen * 4;
    std::string buf(outLen, '\0');
    char *inPtr = const_cast<char*>(str.c_str());
    char *outPtr = const_cast<char*>(buf.c_str());
    
    errno = 0;
    size_t result = iconv(cd, &inPtr, &inLen, &outPtr, &outLen);
    
    iconv_close(cd);
    
    if (result != (size_t)-1 && errno == 0)
    {
        buf.resize(buf.size()-outLen);
    }
    else {
        throw Exception(Exception::MKXPError, "Unable to convert string (Guessed encoding: %s)", charset);
    }
    
    return buf;
}

static std::string convertString(std::string &str) {
    /* uchardet (especially older versions) misdetects valid UTF-8 as
     * MacRoman/windows-1252 on short or mostly-ASCII input, mangling e.g. an
     * accented windowTitle in mkxp.json. Skip detection if it's already valid
     * UTF-8. See https://github.com/mkxp-z/mkxp-z/issues/373.
     *
     * The overload above deliberately doesn't do this: its callers know the
     * charset, and UTF-16/UTF-32 text can pass an UTF-8 validity check. */
    if (isValidUTF8(str))
        return std::string(str);

    return convertString(str, getCharset(str).c_str());
}
}

#endif /* encoding_h */
