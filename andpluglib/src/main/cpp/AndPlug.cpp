#include "AndPlug.h"
#include "common.h"

#define LOG_TAG "AndPlug"

AndPlug::AndPlug() {}

AndPlug::~AndPlug() {
    Unload();
}

void AndPlug::Load(const char* song, Copl* opl) {
    m_p.reset(CAdPlug::factory(std::string(song), opl));
}

void AndPlug::Unload() {
    m_p.reset(nullptr);
}

bool AndPlug::isLoaded() {
    return (m_p != nullptr);
}

const char *AndPlug::GetVersion() {
    return CAdPlug::get_version().c_str();
}

void AndPlug::Seek(unsigned long ms) {
    if (m_p != nullptr) {
        m_p->seek(ms);
    }
}

bool AndPlug::Update() {
    bool playing = false;
    if (m_p != nullptr) {
        playing = m_p->update();
    }
    return playing;
}

void AndPlug::Rewind(int subsong) {
    if (m_p != nullptr) {
        m_p->rewind(subsong);
    }
}

float AndPlug::GetRefresh() {
    float refresh = 1.0;
    if (m_p != nullptr) {
        refresh = m_p->getrefresh();
    }
    return refresh;
}

unsigned long AndPlug::SongLength(int subsong) {
    unsigned long slength = 0;
    if (m_p != nullptr) {
        slength = m_p->songlength(subsong);
    }
    return slength;
}

std::string AndPlug::GetType() {
    std::string type;
    if (m_p != nullptr) {
        type = m_p->gettype();
    }
    return type;
}

std::string AndPlug::GetTitle() {
    std::string title;
    if (m_p != nullptr) {
        title = m_p->gettitle();
    }
    return title;
}

std::string AndPlug::GetAuthor() {
    std::string author;
    if (m_p != nullptr) {
        author = m_p->getauthor();
    }
    return author;
}

std::string AndPlug::GetDesc() {
    std::string desc;
    if (m_p != nullptr) {
        desc = m_p->getdesc();
    }
    return desc;
}

unsigned int AndPlug::GetPatterns() {
    unsigned int pattcnt = 0;
    if (m_p != nullptr) {
        pattcnt = m_p->getpatterns();
    }
    return pattcnt;
}

unsigned int AndPlug::GetPattern() {
    unsigned int ord = 0;
    if (m_p != nullptr) {
        ord = m_p->getpattern();
    }
    return ord;
}

unsigned int AndPlug::GetOrders() {
    unsigned int poscnt = 0;
    if (m_p != nullptr) {
        poscnt = m_p->getorders();
    }
    return poscnt;
}

unsigned int AndPlug::GetOrder() {
    unsigned int songpos = 0;
    if (m_p != nullptr) {
        songpos = m_p->getorder();
    }
    return songpos;
}

unsigned int AndPlug::GetRow() {
    unsigned int pattpos = 0;
    if (m_p != nullptr) {
        pattpos = m_p->getrow();
    }
    return pattpos;
}

unsigned int AndPlug::GetSpeed() {
    unsigned int speed = 0;
    if (m_p != nullptr) {
        speed = m_p->getspeed();
    }
    return speed;
}

unsigned int AndPlug::GetSubsongs() {
    unsigned int numsubsongs = 0;
    if (m_p != nullptr) {
        numsubsongs = m_p->getsubsongs();
    }
    return numsubsongs;
}

unsigned int AndPlug::GetSubsong() {
    unsigned int cursubsong = 0;
    if (m_p != nullptr) {
        cursubsong = m_p->getsubsong();
    }
    return cursubsong;
}

unsigned int AndPlug::GetInstruments() {
    unsigned int instnum = 0;
    if (m_p != nullptr) {
        instnum = m_p->getinstruments();
    }
    return instnum;
}

const char* AndPlug::GetInstrument(unsigned int n) {
    const char* instname = 0;
    if (m_p != nullptr) {
        instname = m_p->getinstrument(n).c_str();
    }
    return instname;
}
