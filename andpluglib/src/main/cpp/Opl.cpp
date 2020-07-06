#include "Opl.h"
#include "common.h"

#define LOG_TAG "Opl"

Opl::Opl() : m_stream(nullptr), m_FileIndex(0) {
    m_path.clear();
}

Opl::~Opl() {
    Uninitialize();
    m_path.clear();
}

void Opl::Initialize(int rate, bool bit16, bool usestereo, bool left, bool right) {
    m_rate = rate;
    m_bit16 = bit16;
    m_usestereo = usestereo;
    m_left = left;
    m_right = right;
    m_opl.reset(new CEmuopl(rate, bit16, usestereo));
    m_plug.reset(new AndPlug());
}

void Opl::Uninitialize() {
    Unload();
//    m_opl.reset(nullptr);
    m_plug.reset(nullptr);
}

bool Opl::Load(const char* song) {
    bool isLoaded = false;
    if (m_plug != nullptr && m_opl != nullptr) {
        isLoaded = m_plug->Load(song, m_opl.get());
        OpenFile();
    } else {
        LOGW(LOG_TAG, "Load: can't load song %s", song);
    }
    return isLoaded;
}

void Opl::Unload() {
    if (m_plug != nullptr) {
        m_plug->Unload();
    }
    CloseFile();
}

bool Opl::isLoaded() {
    bool loaded = false;
    if (m_plug != nullptr) {
        loaded = m_plug->isLoaded();
    }
    return loaded;
}

AndPlug* Opl::GetPlug() {
    return m_plug.get();
}

void Opl::Write(int reg, int val) {
    if (m_opl != nullptr) {
        m_opl->write(reg, val);
    } else {
        LOGW(LOG_TAG, "Write: no Copl instance");
    }
}

void Opl::SetChip(int n) {
    if (m_opl != nullptr) {
        m_opl->setchip(n);
    } else {
        LOGW(LOG_TAG, "SetChip: no Copl instance");
    }
}

int Opl::GetChip() {
    int currChip = 0;
    if (m_opl != nullptr) {
        currChip = m_opl->getchip();
    } else {
        LOGW(LOG_TAG, "GetChip: no Copl instance");
    }
    return currChip;
}

void Opl::Init(void) {
    if (m_opl != nullptr) {
        m_opl->init();
    } else {
        LOGW(LOG_TAG, "Init: no Copl instance");
    }
}

Copl::ChipType Opl::GetType() {
    Copl::ChipType currType = (Copl::ChipType) 0;
    if (m_opl != nullptr) {
        currType = m_opl->gettype();
    } else {
        LOGW(LOG_TAG, "GetType: no Copl instance");
    }
    return currType;
}

int Opl::Update(void *buf, int size, bool repeat) {
    if (m_plug == nullptr || m_opl == nullptr || buf == nullptr || size <= 0) {
        LOGW(LOG_TAG, "Update: illegal arguments: %p, %p, %p, %d", m_plug.get(), m_opl.get(), buf, size);
        return OPL_ERROR_ARGS;
    }

    if (!m_plug->Update() && !repeat) {
        return OPL_ERROR_OK;
    }

    float refresh = m_plug->GetRefresh();
    if (refresh <= 0.0) {
        LOGW(LOG_TAG, "Update: illegal refresh rate: %f", refresh);
        return OPL_ERROR_RATE;
    }

    int samples = (int) (m_rate / refresh);
    if (samples > size) {
        LOGW(LOG_TAG, "Update: insufficient buffer size: %d > %d", samples, size);
        return OPL_ERROR_BUFFER;
    }
    m_opl->update((short*) buf, samples);

    if (m_usestereo && (m_left != m_right)) {
        if ((2 * samples) > size) {
            LOGW(LOG_TAG, "Update: insufficient buffer size for stereo: %d > %d", 2 * samples, size);
            return OPL_ERROR_STEREO;
        }
        if (m_bit16) {
            CopyStereo16((short*) buf, samples);
        } else {
            CopyStereo8((char*) buf, samples);
        }
    }

    if (m_bit16) {
        WriteFile16((short*) buf, samples);
    } else {
        WriteFile8((char*) buf, samples);
    }

    return samples;
}

void Opl::DebugPath(const char* path) {
    m_path = std::string(path);
}

void Opl::OpenFile() {
    if (m_stream != nullptr) {
        CloseFile();
    }
    if (!m_path.empty()) {
        m_FileIndex++;
        char filename[256];
        snprintf(filename, sizeof(filename), "%s/Opl_%dbit_%dch_%dHz.%03d.raw", m_path.c_str(), (m_bit16 ? 16 : 8), (m_usestereo ? 2 : 1), m_rate, m_FileIndex);
        m_stream = fopen(filename, "w");
    }
}

void Opl::CloseFile() {
    if (m_stream != nullptr) {
        fclose(m_stream);
        m_stream = nullptr;
    }
}

void Opl::CopyStereo16(short* buf, int samples) {
    if (m_left && !m_right) {
        for (int i = 0; i < (2 * samples); i = i + 2) {
            buf[i + 1] = buf[i];
        }
    } else if (!m_left && m_right) {
        for (int i = 0; i < (2 * samples); i = i + 2) {
            buf[i] = buf[i + 1];
        }
    }
}

void Opl::CopyStereo8(char* buf, int samples) {
    if (m_left && !m_right) {
        for (int i = 0; i < (2 * samples); i = i + 2) {
            buf[i + 1] = buf[i];
        }
    } else if (!m_left && m_right) {
        for (int i = 0; i < (2 * samples); i = i + 2) {
            buf[i] = buf[i + 1];
        }
    }
}

void Opl::WriteFile16(short *buf, int samples) {
    if (buf != nullptr && samples > 0 && m_stream != nullptr) {
        fwrite(buf, sizeof(short), (size_t) samples, m_stream);
    }
}

void Opl::WriteFile8(char *buf, int samples) {
    if (buf != nullptr && samples > 0 && m_stream != nullptr) {
        fwrite(buf, sizeof(char), (size_t) samples, m_stream);
    }
}
