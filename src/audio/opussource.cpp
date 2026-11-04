/*
** opussource.cpp
**
** This file is part of mkxp.
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

#include "aldatasource.h"
#include "exception.h"

#include <opusfile.h>
#include <vector>
#include <algorithm>
#include <cstdlib>

/* Opus always decodes at 48 kHz */
static const int OpusRate = 48000;

static int ofRead(void *ops, unsigned char *ptr, int nbytes)
{
	return SDL_RWread(static_cast<SDL_RWops*>(ops), ptr, 1, nbytes);
}

static int ofSeek(void *ops, opus_int64 offset, int whence)
{
	return SDL_RWseek(static_cast<SDL_RWops*>(ops), offset, whence) < 0 ? -1 : 0;
}

static opus_int64 ofTell(void *ops)
{
	return SDL_RWtell(static_cast<SDL_RWops*>(ops));
}

static OpusFileCallbacks OpusCallbacks =
{
	ofRead,
	ofSeek,
	ofTell,
	0
};

struct OpusSource : ALDataSource
{
	SDL_RWops src;
	OggOpusFile *of;

	uint32_t currentFrame;
	int channels;
	int frameSize;
	ALenum alFormat;

	struct
	{
		uint32_t start;
		uint32_t end;
		bool valid;
		bool requested;
	} loop;

	std::vector<opus_int16> sampleBuf;

	OpusSource(SDL_RWops &ops, bool looped)
	    : src(ops),
	      currentFrame(0)
	{
		int error = 0;
		of = op_open_callbacks(&src, &OpusCallbacks, 0, 0, &error);

		if (!of)
		{
			SDL_RWclose(&src);
			throw Exception(Exception::MKXPError,
			                "Opusfile: Cannot read opus file (error %d)", error);
		}

		channels = op_channel_count(of, -1);

		if (channels > 2)
		{
			op_free(of);
			SDL_RWclose(&src);
			throw Exception(Exception::MKXPError,
			                "Cannot handle audio with more than 2 channels");
		}

		alFormat = chooseALFormat(sizeof(opus_int16), channels);
		frameSize = sizeof(opus_int16) * channels;
		sampleBuf.resize(STREAM_BUF_SIZE);

		loop.requested = looped;
		loop.valid = false;
		loop.start = loop.end = 0;

		if (!looped)
			return;

		/* Same tags as the Vorbis source, in 48 kHz frames */
		const OpusTags *tags = op_tags(of, -1);
		const char *s = tags ? opus_tags_query(tags, "LOOPSTART", 0) : 0;
		const char *l = tags ? opus_tags_query(tags, "LOOPLENGTH", 0) : 0;

		if (s && l)
		{
			loop.start = strtol(s, 0, 10);
			loop.end = loop.start + strtol(l, 0, 10);
			loop.valid = (loop.end > loop.start);
		}
	}

	~OpusSource()
	{
		op_free(of);
		SDL_RWclose(&src);
	}

	int sampleRate()
	{
		return OpusRate;
	}

	void seekToOffset(double seconds)
	{
		currentFrame = seconds > 0 ? lround(seconds * OpusRate) : 0;

		if (loop.valid && currentFrame > loop.end)
			currentFrame = loop.start;

		/* If seeking fails, just seek back to start */
		if (op_pcm_seek(of, currentFrame) != 0)
		{
			op_pcm_seek(of, 0);
			currentFrame = 0;
		}
	}

	Status fillBuffer(AL::Buffer::ID alBuffer)
	{
		int bufUsed = 0; /* in samples (frames * channels) */
		int availBuf = sampleBuf.size();
		Status status = ALDataSource::NoError;
		bool readAgain = false;

		while (availBuf - bufUsed > 16)
		{
			int canRead = availBuf - bufUsed;

			if (loop.valid)
				canRead = std::min<int>(canRead,
				                        (loop.end - currentFrame) * channels);

			int frames = op_read(of, &sampleBuf[bufUsed], canRead, 0);

			if (frames < 0)
			{
				status = ALDataSource::Error;
				break;
			}

			if (frames == 0)
			{
				if (loop.requested)
				{
					status = ALDataSource::WrapAround;
					seekToOffset(0);
				}
				else
				{
					status = ALDataSource::EndOfStream;
				}

				/* Mustn't hand back an empty buffer; see VorbisSource */
				if (bufUsed > 0)
					break;

				if (readAgain)
				{
					status = ALDataSource::Error;
					break;
				}

				readAgain = true;
				continue;
			}

			bufUsed += frames * channels;
			currentFrame += frames;

			if (loop.valid && currentFrame >= loop.end)
			{
				status = ALDataSource::WrapAround;
				currentFrame = loop.start;

				if (op_pcm_seek(of, currentFrame) != 0)
					status = ALDataSource::Error;

				break;
			}
		}

		if (status != ALDataSource::Error)
			AL::Buffer::uploadData(alBuffer, alFormat, sampleBuf.data(),
			                       bufUsed * sizeof(opus_int16), OpusRate);

		return status;
	}

	uint32_t loopStartFrames()
	{
		return loop.valid ? loop.start : 0;
	}

	bool setPitch(float)
	{
		return false;
	}
};

ALDataSource *createOpusSource(SDL_RWops &ops, bool looped)
{
	return new OpusSource(ops, looped);
}

bool decodeOpusAll(SDL_RWops &ops, std::vector<int16_t> &pcm,
                   int &channels, int &rate)
{
	int error = 0;
	OggOpusFile *of = op_open_callbacks(&ops, &OpusCallbacks, 0, 0, &error);

	if (!of)
	{
		SDL_RWclose(&ops);
		return false;
	}

	channels = op_channel_count(of, -1);
	rate = OpusRate;
	bool ok = (channels >= 1 && channels <= 2);

	std::vector<opus_int16> chunk(STREAM_BUF_SIZE);

	while (ok)
	{
		int frames = op_read(of, chunk.data(), chunk.size(), 0);

		if (frames < 0)
			ok = false;
		else if (frames == 0)
			break;
		else
			pcm.insert(pcm.end(), chunk.begin(), chunk.begin() + frames * channels);
	}

	op_free(of);
	SDL_RWclose(&ops);

	return ok;
}
