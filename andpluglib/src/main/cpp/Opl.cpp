#include "Opl.h"
#include "common.h"
#include "emuopl.h"
#include "kemuopl.h"
#include "nemuopl.h"
#include "temuopl.h"
#include "wemuopl.h"

#define LOG_TAG "Opl"

Opl::Opl() : m_rate(0), m_usestereo(false), m_left(false), m_right(false), m_previous(0.0), m_stream(nullptr), m_file_index(0) {
    m_path.clear();
}

Opl::~Opl() {
    Uninitialize();
    m_path.clear();
}

void Opl::Initialize(int emu, int rate, bool usestereo, bool left, bool right) {
    m_rate = rate;
    m_usestereo = usestereo;
    m_left = left;
    m_right = right;
    switch (emu) {
        case OPL_CEMU:
        default:
            m_opl.reset(new CEmuopl(rate, true, usestereo));
            break;
        case OPL_CKEMU:
            m_opl.reset(new CKemuopl(rate, true, usestereo));
            break;
        case OPL_CNEMU:
            m_opl.reset(new CNemuopl(rate));
            break;
        case OPL_CTEMU:
            m_opl.reset(new CTemuopl(rate, true, usestereo));
            break;
        case OPL_CWEMU:
            m_opl.reset(new CWemuopl(rate, true, usestereo));
           break;
    }
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

int Opl::Update(void* buf, int size, bool repeat) {
    static long total_ = 0;
    short* buf_offset = (short*) buf;
    float sampled_count = m_previous;
    int count = (int) sampled_count;
    float remain = 0.0;
    if (m_plug == nullptr || m_opl == nullptr || buf == nullptr || size <= 0) {
        LOGW(LOG_TAG, "Update: illegal arguments: %p, %p, %p, %d", m_plug.get(), m_opl.get(), buf, size);
        return OPL_ERROR_ARGS;
    }

    if (count > size) {
        count = size;
    }
    if (count > 0) {
        m_opl->update((short*) buf_offset, count);
        PostProcess(buf_offset, count);
        buf_offset += count * (m_usestereo ? 2: 1);
        m_previous = 0.0;
        remain = sampled_count - (float) count;
    }

    while (count < size) {
        if (!m_plug->Update() && !repeat) {
            if (count > 0) {
                m_opl->update((short*) buf, count);
                PostProcess(buf, count);
            }
            break;
        }
        float refresh = m_plug->GetRefresh();
        if (refresh <= 0.0) {
            LOGW(LOG_TAG, "Update: illegal refresh rate: %f", refresh);
            return OPL_ERROR_RATE;
        }
        float refresh_samples = ((float) m_rate) / refresh;
        sampled_count += refresh_samples;
        count = (int) sampled_count;
        refresh_samples += remain;
        int samples = (int) refresh_samples;
        remain = (refresh_samples - (float) samples);
        if (count > size) {
            remain = sampled_count - (float) size;
            refresh_samples -= remain;
            samples = (int) refresh_samples;
            count = size;
        }
        m_opl->update(buf_offset, samples);
        PostProcess(buf_offset, samples);
        buf_offset += samples * (m_usestereo ? 2 : 1);
    }

    m_previous = remain;
    total_ += count;
    return count;
}

void Opl::DebugPath(const char* path) {
    m_path = std::string(path);
}

void Opl::OpenFile() {
    if (m_stream != nullptr) {
        CloseFile();
    }
    if (!m_path.empty()) {
        m_file_index++;
        char filename[256];
        snprintf(filename, sizeof(filename), "%s/Opl_%dbit_%dch_%dHz.%03d.raw", m_path.c_str(), 16, (m_usestereo ? 2 : 1), m_rate, m_file_index);
        m_stream = fopen(filename, "w");
    }
}

void Opl::CloseFile() {
    if (m_stream != nullptr) {
        fclose(m_stream);
        m_stream = nullptr;
    }
}

void Opl::CopyStereo(short* buf, int samples) {
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

void Opl::WriteFile(short* buf, int samples) {
    if (buf != nullptr && samples > 0 && m_stream != nullptr) {
        fwrite(buf, sizeof(short), (size_t) samples, m_stream);
    }
}

void Opl::PostProcess(void* buf, int count) {
    if (m_usestereo && (m_left != m_right)) {
        CopyStereo((short*) buf, count);
    }
    WriteFile((short*) buf, count);
}
