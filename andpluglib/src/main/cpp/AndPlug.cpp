#include "AndPlug.h"
#include "common.h"

#define LOG_TAG "AndPlug"

AndPlug::AndPlug() : m_p(NULL) {}

AndPlug::~AndPlug() {
    m_p = NULL;
}

CPlayer* AndPlug::Load(const char* song, Copl* opl) {
    m_p = CAdPlug::factory(std::string(song), opl);
    return m_p;
}

void AndPlug::Unload() {
    m_p = NULL;
}

bool AndPlug::isLoaded() {
    return (m_p != NULL);
}

const char *AndPlug::GetVersion() {
    return CAdPlug::get_version().c_str();
}

void AndPlug::Seek(unsigned long ms) {
    if (m_p != NULL) {
        m_p->seek(ms);
    }
}

bool AndPlug::Update() {
    bool playing = false;
    if (m_p != NULL) {
        playing = m_p->update();
    }
    return playing;
}

void AndPlug::Rewind(int subsong) {
    if (m_p != NULL) {
        m_p->rewind();
    }
}

float AndPlug::GetRefresh() {
    float refresh = 1.0;
    if (m_p != NULL) {
        refresh = m_p->getrefresh();
    }
    return refresh;
}

unsigned long AndPlug::SongLength(int subsong) {
    unsigned long slength = 0;
    if (m_p != NULL) {
        slength = m_p->songlength(subsong);
    }
    return slength;
}

std::string AndPlug::GetType() {
    std::string type;
    if (m_p != NULL) {
        type = m_p->gettype();
    }
    return type;
}

std::string AndPlug::GetTitle() {
    std::string title;
    if (m_p != NULL) {
        title = m_p->gettitle();
    }
    return title;
}

std::string AndPlug::GetAuthor() {
    std::string author;
    if (m_p != NULL) {
        author = m_p->getauthor();
    }
    return author;
}

std::string AndPlug::GetDesc() {
    std::string desc;
    if (m_p != NULL) {
        desc = m_p->getdesc();
    }
    return desc;
}

unsigned int AndPlug::GetPatterns() {
    unsigned int pattcnt = 0;
    if (m_p != NULL) {
        pattcnt = m_p->getpatterns();
    }
    return pattcnt;
}

unsigned int AndPlug::GetPattern() {
    unsigned int ord = 0;
    if (m_p != NULL) {
        ord = m_p->getpattern();
    }
    return ord;
}

unsigned int AndPlug::GetOrders() {
    unsigned int poscnt = 0;
    if (m_p != NULL) {
        poscnt = m_p->getorders();
    }
    return poscnt;
}

unsigned int AndPlug::GetOrder() {
    unsigned int songpos = 0;
    if (m_p != NULL) {
        songpos = m_p->getorder();
    }
    return songpos;
}

unsigned int AndPlug::GetRow() {
    unsigned int pattpos = 0;
    if (m_p != NULL) {
        pattpos = m_p->getrow();
    }
    return pattpos;
}

unsigned int AndPlug::GetSpeed() {
    unsigned int speed = 0;
    if (m_p != NULL) {
        speed = m_p->getspeed();
    }
    return speed;
}

unsigned int AndPlug::GetSubsongs() {
    unsigned int numsubsongs = 0;
    if (m_p != NULL) {
        numsubsongs = m_p->getsubsongs();
    }
    return numsubsongs;
}

unsigned int AndPlug::GetSubsong() {
    unsigned int cursubsong = 0;
    if (m_p != NULL) {
        cursubsong = m_p->getsubsong();
    }
    return cursubsong;
}

unsigned int AndPlug::GetInstruments() {
    unsigned int instnum = 0;
    if (m_p != NULL) {
        instnum = m_p->getinstruments();
    }
    return instnum;
}

const char* AndPlug::GetInstrument(unsigned int n) {
    const char* instname = 0;
    if (m_p != NULL) {
        instname = m_p->getinstrument(n).c_str();
    }
    return instname;
}
