#include "Opl.h"
#include "common.h"

#define LOG_TAG "OUpdate16pl"

Opl::Opl(int rate, bool bit16, bool usestereo, bool left, bool right) :
        m_rate(rate), m_usestereo(usestereo), m_left(left), m_right(right) {
    m_opl = new CEmuopl(rate, bit16, usestereo);
}

Opl::~Opl() {
    if (m_opl != NULL) {
        delete m_opl;
        m_opl = NULL;
    }
}

CPlayer* Opl::Load(const char* song) {
    return m_plug.Load(song, m_opl);
}

void Opl::Unload() {
    m_plug.Unload();
}

bool Opl::isLoaded() {
    return m_plug.isLoaded();
}

AndPlug* Opl::GetPlug() {
    return &m_plug;
}

void Opl::Write(int reg, int val) {
    if (m_opl != NULL) {
        m_opl->write(reg, val);
    } else {
        LOGW(LOG_TAG, "Write: no Copl instance");
    }
}

void Opl::SetChip(int n) {
    if (m_opl != NULL) {
        m_opl->setchip(n);
    } else {
        LOGW(LOG_TAG, "SetChip: no Copl instance");
    }
}

int Opl::GetChip() {
    int currChip = 0;
    if (m_opl != NULL) {
        currChip = m_opl->getchip();
    } else {
        LOGW(LOG_TAG, "GetChip: no Copl instance");
    }
    return currChip;
}

void Opl::Init(void) {
    if (m_opl != NULL) {
        m_opl->init();
    } else {
        LOGW(LOG_TAG, "Init: no Copl instance");
    }
}

Copl::ChipType Opl::GetType() {
    Copl::ChipType currType = (Copl::ChipType) 0;
    if (m_opl != NULL) {
        currType = m_opl->gettype();
    } else {
        LOGW(LOG_TAG, "GetType: no Copl instance");
    }
    return currType;
}

unsigned long Opl::Update16(short *buf, int samples, bool repeat) {
    if ((!m_plug.Update() && !repeat) || (m_opl == NULL)) {
        return 0;
    }

    float refresh = m_plug.GetRefresh();
    if (refresh == 0.0) {
        LOGW(LOG_TAG, "Update16: illegal refresh rate");
        return 0;
    }
    unsigned long towrite = (unsigned long) (m_rate / refresh);
    unsigned long write, newsamples = 0;
    while (towrite) {
        write = (towrite > samples) ? samples : towrite;
        m_opl->update(buf, write);
        newsamples += write;
        towrite -= write;
    }

    if (m_usestereo && (newsamples <= samples)) {
        if (m_left && !m_right) {
            for (int i = 0; i < newsamples * 2; i = i + 2) {
                buf[i + 1] = buf[i];
            }
        } else if (!m_left && m_right) {
            for (int i = 0; i < newsamples * 2; i = i + 2) {
                buf[i] = buf[i + 1];
            }
        }
    }

    return newsamples;
}

unsigned long Opl::Update8(char *buf, int samples, bool repeat) {
    if ((!m_plug.Update() && !repeat) || (m_opl == NULL)) {
        return 0;
    }

    float refresh = m_plug.GetRefresh();
    if (refresh == 0.0) {
        LOGW(LOG_TAG, "Update8: illegal refresh rate");
        return 0;
    }
    unsigned long towrite = (unsigned long) (m_rate / refresh);
    unsigned long write, newsamples = 0;
    while (towrite) {
        write = (towrite > samples) ? samples : towrite;
        m_opl->update((short*) buf, write);
        newsamples += write;
        towrite -= write;
    }

    if (m_usestereo && (newsamples <= samples)) {
        if (m_left && !m_right) {
            for (int i = 0; i < newsamples * 2; i = i + 2) {
                buf[i + 1] = buf[i];
            }
        } else if (!m_left && m_right) {
            for (int i = 0; i < newsamples * 2; i = i + 2) {
                buf[i] = buf[i + 1];
            }
        }
    }

    return newsamples;
}
