#include "OboePlayer.h"
#include "andplayer-jni.h"
#include "common.h"
#include <ctime>

#define LOG_TAG "OboePlayer"

OboePlayer::OboePlayer(Opl* opl) : m_rate(0), m_usestereo(false), m_isrunning(false), m_totalsamples(0), m_opl(opl) {}

OboePlayer::~OboePlayer() {
    Uninitialize();
    m_stream.reset();
}

bool OboePlayer::Initialize(int rate, bool usestereo) {
    bool initialized = false;
    m_rate = rate;
    m_usestereo = usestereo;
    int channels = (usestereo ? 2 : 1);
    oboe::AudioStreamBuilder builder;
    builder.setChannelCount(channels);
    builder.setDirection(oboe::Direction::Output);
    builder.setSampleRate(m_rate);
    builder.setFramesPerCallback(oboe::kUnspecified);
    builder.setFormat(oboe::AudioFormat::I16);
    builder.setBufferCapacityInFrames(oboe::kUnspecified);
    builder.setAudioApi(oboe::AudioApi::Unspecified);
    builder.setSharingMode(oboe::SharingMode::Shared);
    builder.setPerformanceMode(oboe::PerformanceMode::PowerSaving);
    builder.setUsage(oboe::Usage::Media);
    builder.setContentType(oboe::ContentType::Music);
    builder.setSessionId(oboe::SessionId::None);
    builder.setDeviceId(oboe::kUnspecified);
    builder.setCallback(this);
    builder.setChannelConversionAllowed(true);
    builder.setFormatConversionAllowed(true);
    builder.setSampleRateConversionQuality(oboe::SampleRateConversionQuality::None);

    oboe::Result result = builder.openStream(m_stream);
    if (result != oboe::Result::OK) {
        LOGE(LOG_TAG, "OboePlayer::Initialize: %s", oboe::convertToText(result));
    }

    int32_t channelCount = m_stream->getChannelCount();
    oboe::Direction direction = m_stream->getDirection();
    int32_t sampleRate = m_stream->getSampleRate();
    oboe::AudioFormat format = m_stream->getFormat();
    if (sampleRate != m_rate || direction != oboe::Direction::Output || channelCount != channels || format != oboe::AudioFormat::I16) {
        LOGE(LOG_TAG, "OboePlayer::Initialize: channelCount:%d, direction:%d, sampleRate:%d, format:%d", channelCount, direction, sampleRate, format);
    } else {
        initialized = true;
        m_totalsamples = 0;
    }

    return initialized;
}

bool OboePlayer::Uninitialize() {
    bool uninitialized = false;
    if (m_stream != nullptr) {
        oboe::Result result = m_stream->close();
        if (result != oboe::Result::OK) {
            LOGE(LOG_TAG, "OboePlayer::Uninitialize: %s", oboe::convertToText(result));
        } else {
            uninitialized = true;
            m_totalsamples = 0;
        }
    }
    return uninitialized;
}

bool OboePlayer::Restart() {
    bool restarted = false;
    restarted = Uninitialize();
    restarted &= Initialize(m_rate, m_usestereo);
    m_totalsamples = 0;
    return restarted;
}

bool OboePlayer::Play() {
    if (m_stream != nullptr) {
        oboe::StreamState state = m_stream->getState();
        if (state == oboe::StreamState::Open ||
        state == oboe::StreamState::Pausing || state == oboe::StreamState::Paused ||
        state == oboe::StreamState::Flushing || state == oboe::StreamState::Flushed ||
        state == oboe::StreamState::Stopping || state == oboe::StreamState::Stopped) {
            m_isrunning = true;
            oboe::Result result = m_stream->requestStart();
            if (result != oboe::Result::OK) {
                LOGE(LOG_TAG, "OboePlayer::Play: %s", oboe::convertToText(result));
                m_isrunning = false;
            }
        }
    }
    return m_isrunning;
}

bool OboePlayer::Pause() {
    bool paused = false;
    if (m_stream != nullptr) {
        oboe::StreamState state = m_stream->getState();
        if (state == oboe::StreamState::Starting || state == oboe::StreamState::Started) {
            oboe::Result result = m_stream->requestPause();
            if (result != oboe::Result::OK) {
                LOGE(LOG_TAG, "OboePlayer::Pause: %s", oboe::convertToText(result));
            } else {
                paused = true;
            }
        }
    }
    return paused;
}

bool OboePlayer::Stop() {
    if (m_stream != nullptr) {
        oboe::StreamState state = m_stream->getState();
        if (state == oboe::StreamState::Started || state == oboe::StreamState::Paused) {
            oboe::Result result = m_stream->requestStop();
            if (result != oboe::Result::OK) {
                LOGE(LOG_TAG, "OboePlayer::Stop: %s", oboe::convertToText(result));
            } else {
                m_isrunning = false;
                m_totalsamples = 0;
            }
        }
    }
    return !m_isrunning;
}

void OboePlayer::Seek(long ms) {
    m_totalsamples = m_rate * (m_usestereo ? 2 : 1) * ms / 1000;
}

oboe::DataCallbackResult OboePlayer::onAudioReady(oboe::AudioStream *oboeStream, void *audioData, int32_t numFrames) {
    int samples = 0;
    oboe::DataCallbackResult ret = oboe::DataCallbackResult::Continue;

    // Debug use
#ifdef DEBUG_OBOE_UNDERFLOW
    struct timespec res;
    clock_gettime(CLOCK_MONOTONIC, &res);
    int64_t start = (1000000 * res.tv_sec) + (res.tv_nsec / 1000);
#endif

    if (oboeStream != nullptr && oboeStream->getFormat() != oboe::AudioFormat::I16) {
        LOGE(LOG_TAG, "OboePlayer::onAudioReady: unsupported format %d", oboeStream->getFormat());
        return oboe::DataCallbackResult::Stop;
    }
    if (m_isrunning && audioData != nullptr && numFrames > 0) {
        samples = m_opl->Update(audioData, numFrames);
        m_totalsamples += samples;
        long ms = 1000 * m_totalsamples / m_rate / (m_usestereo ? 2 : 1);
        setTime(ms);
        if (samples == 0) {
            ret = oboe::DataCallbackResult::Stop;
            setState(4, 6, nullptr); // PlayerRequest.RUN, PlayerState.ENDED
        }
    } else {
        ret = oboe::DataCallbackResult::Stop;
        setState(4, 5, nullptr); // PlayerRequest.RUN, PlayerState.STOPPED
    }

    // Debug use
#ifdef DEBUG_OBOE_UNDERFLOW
    clock_gettime(CLOCK_MONOTONIC, &res);
    int64_t end = (1000000 * res.tv_sec) + (res.tv_nsec / 1000);
    int64_t opl_time = end - start;
    int64_t frames = ((int64_t) 1000000 * numFrames) / m_rate;
    if (opl_time > frames) {
        LOGW(LOG_TAG, "OboePlayer::onAudioReady: underflow: %lld ms > %lld ms", opl_time, frames);
    }
#endif

    return ret;
}

void OboePlayer::onErrorBeforeClose(oboe::AudioStream* oboeStream, oboe::Result error) {
    LOGW(LOG_TAG, "OboePlayer::onErrorBeforeClose: %s", oboe::convertToText(error));
    m_isrunning = false;
}

void OboePlayer::onErrorAfterClose(oboe::AudioStream* oboeStream, oboe::Result error) {
    LOGW(LOG_TAG, "OboePlayer::onErrorAfterClose: %s", oboe::convertToText(error));
    m_isrunning = false;
}
