#include "Opl.h"
#include "common.h"
#include "emuopl.h"
#include "kemuopl.h"
#include "nemuopl.h"
#include "temuopl.h"
#include "wemuopl.h"
#include <mutex>

#define LOG_TAG "Opl"

extern std::mutex s_adplugmtx;

Opl::Opl(AndPlug* plug) : m_rate(0), m_usestereo(false), m_repeat(false), m_previous(0.0), m_plug(plug), m_stream(nullptr), m_file_index(0) {
    m_copl.reset(nullptr);
    m_path.clear();
}

Opl::~Opl() {
    Uninitialize();
    m_path.clear();
}

void Opl::Initialize(int emu, int rate, bool usestereo) {
    m_rate = rate;
    m_usestereo = usestereo;
    std::unique_lock<std::mutex> lock(s_adplugmtx);
    switch (emu) {
        case OPL_CEMU:
        default:
            m_copl.reset(new CEmuopl(rate, true, usestereo));
            break;
        case OPL_CKEMU:
            m_copl.reset(new CKemuopl(rate, true, usestereo));
            break;
        case OPL_CNEMU:
            m_copl.reset(new CNemuopl(rate));
            break;
        case OPL_CTEMU:
            m_copl.reset(new CTemuopl(rate, true, usestereo));
            break;
        case OPL_CWEMU:
            m_copl.reset(new CWemuopl(rate, true, usestereo));
           break;
    }
}

void Opl::Uninitialize() {
    CloseFile();
    std::unique_lock<std::mutex> lock(s_adplugmtx);
    m_copl.reset(nullptr);
}

Copl* Opl::GetCopl() {
    Copl* copl = nullptr;
    {
        std::unique_lock<std::mutex> lock(s_adplugmtx);
        if (m_copl != nullptr) {
            copl = m_copl.get();
        } else {
            LOGW(LOG_TAG, "GetCopl: no Copl instance");
        }
    }
    return copl;
}

void Opl::SetRepeat(bool repeat) {
    m_repeat = repeat;
}

void Opl::Write(int reg, int val) {
    std::unique_lock<std::mutex> lock(s_adplugmtx);
    if (m_copl != nullptr) {
        m_copl->write(reg, val);
    } else {
        LOGW(LOG_TAG, "Write: no Copl instance");
    }
}

void Opl::SetChip(int n) {
    std::unique_lock<std::mutex> lock(s_adplugmtx);
    if (m_copl != nullptr) {
        m_copl->setchip(n);
    } else {
        LOGW(LOG_TAG, "SetChip: no Copl instance");
    }
}

int Opl::GetChip() {
    int currChip = 0;
    std::unique_lock<std::mutex> lock(s_adplugmtx);
    if (m_copl != nullptr) {
        currChip = m_copl->getchip();
    } else {
        LOGW(LOG_TAG, "GetChip: no Copl instance");
    }
    return currChip;
}

void Opl::Init() {
    std::unique_lock<std::mutex> lock(s_adplugmtx);
    if (m_copl != nullptr) {
        m_copl->init();
    } else {
        LOGW(LOG_TAG, "Init: no Copl instance");
    }
}

Copl::ChipType Opl::GetType() {
    Copl::ChipType currType = (Copl::ChipType) 0;
    std::unique_lock<std::mutex> lock(s_adplugmtx);
    if (m_copl != nullptr) {
        currType = m_copl->gettype();
    } else {
        LOGW(LOG_TAG, "GetType: no Copl instance");
    }
    return currType;
}

int Opl::Update(void* buf, int size) {
    std::unique_lock<std::mutex> lock(s_adplugmtx);
    short* buf_offset = (short*) buf;
    int sampled_count = m_previous;
    int count = sampled_count;
    int channels = m_usestereo ? 2 : 1;
    int remain = 0;
    if (m_plug == nullptr || m_copl == nullptr || buf == nullptr || size <= 0) {
        LOGW(LOG_TAG, "Update: illegal arguments: %p, %p, %p, %d", m_plug, m_copl.get(), buf, size);
        return OPL_ERROR_ARGS;
    }

    if (count > size) {
        count = size;
    }
    if (count > 0) {
        m_copl->update((short *) buf_offset, count);
        WriteFile((short*) buf_offset, count);
        buf_offset += count * channels;
        m_previous = 0;
        remain = sampled_count - count;
    }

    while (count < size) {
        bool playing = m_plug->Update();
        if (!playing && !m_repeat) {
            if (count > 0) {
                m_copl->update((short *) buf, count);
                WriteFile((short*) buf, count);
            }
            break;
        }
        float refresh = m_plug->GetRefresh();
        if (refresh <= 0.0) {
            LOGW(LOG_TAG, "Update: illegal refresh rate: %f", refresh);
            return OPL_ERROR_RATE;
        }
        float refresh_samples = ((float) m_rate) / refresh;
        int samples = (int) refresh_samples;
        sampled_count += samples;
        count = sampled_count;
        samples += remain;
        remain = 0;
        if (count > size) {
            remain = sampled_count - size;
            samples -= remain;
            count = size;
        }
        m_copl->update(buf_offset, samples);
        WriteFile((short*) buf_offset, samples);
        buf_offset += samples * channels;
    }

    m_previous = remain;
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
        snprintf(filename, sizeof(filename), "%s/Opl_%dbit_%dch_%dHz_%03d.raw", m_path.c_str(), 16, (m_usestereo ? 2 : 1), m_rate, m_file_index);
        m_stream = fopen(filename, "w");
    }
}

void Opl::CloseFile() {
    if (m_stream != nullptr) {
        fclose(m_stream);
        m_stream = nullptr;
    }
}

void Opl::WriteFile(short* buf, int samples) {
    if (buf != nullptr && samples > 0 && m_stream != nullptr) {
        fwrite(buf, sizeof(short), (size_t) samples, m_stream);
    }
}
