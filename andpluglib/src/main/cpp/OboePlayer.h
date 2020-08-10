#ifndef ANDPLUGLIB_OBOEPLAYER_H
#define ANDPLUGLIB_OBOEPLAYER_H

#include "oboe/Oboe.h"
#include "Opl.h"

class Opl;

class OboePlayer : public oboe::AudioStreamCallback {
public:
    OboePlayer(Opl* opl);
    ~OboePlayer();
    bool Initialize(int rate, bool usestereo);
    bool Uninitialize();
    bool Restart();
    bool Play();
    bool Pause();
    bool Stop();
private:
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *oboeStream, void *audioData, int32_t numFrames);
    void onErrorBeforeClose(oboe::AudioStream* oboeStream, oboe::Result error);
    void onErrorAfterClose(oboe::AudioStream* oboeStream, oboe::Result error);
    int m_rate;
    bool m_usestereo;
    bool m_isrunning;
    Opl* m_opl;
    std::shared_ptr<oboe::AudioStream> m_stream;
};

#endif //ANDPLUGLIB_OBOEPLAYER_H
