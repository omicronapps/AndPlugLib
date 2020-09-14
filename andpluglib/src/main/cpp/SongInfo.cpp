#include "SongInfo.h"
#include "common.h"

#define LOG_TAG "SongInfo"

SongInfo::~SongInfo() {
    m_p.reset(nullptr);
}

bool SongInfo::Load(const char* song) {
    CPlayer* player = CAdPlug::factory(std::string(song), &opl);
    m_p.reset(player);
    bool isLoaded = (player != nullptr);
    if (!isLoaded) {
//        LOGW(LOG_TAG, "Load: failed to load song: %s", song);
    }
    return isLoaded;
}

unsigned long SongInfo::SongLength(int subsong) {
    unsigned long slength = 0;
    if (m_p != nullptr) {
        slength = m_p->songlength(subsong);
    }
    return slength;
}

std::string SongInfo::GetType() {
    std::string type;
    if (m_p != nullptr) {
        type = m_p->gettype();
    }
    return type;
}

std::string SongInfo::GetTitle() {
    std::string title;
    if (m_p != nullptr) {
        title = m_p->gettitle();
    }
    return title;
}

std::string SongInfo::GetAuthor() {
    std::string author;
    if (m_p != nullptr) {
        author = m_p->getauthor();
    }
    return author;
}

std::string SongInfo::GetDesc() {
    std::string desc;
    if (m_p != nullptr) {
        desc = m_p->getdesc();
    }
    return desc;
}

unsigned int SongInfo::GetSubsongs() {
    unsigned int numsubsongs = 0;
    if (m_p != nullptr) {
        numsubsongs = m_p->getsubsongs();
    }
    return numsubsongs;
}
