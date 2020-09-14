#ifndef ANDPLUGLIB_SONGINFO_H
#define ANDPLUGLIB_SONGINFO_H

#include <memory>
#include "adplug.h"
#include "silentopl.h"

class SongInfo {
public:
    SongInfo() = default;
    ~SongInfo();
    bool Load(const char* song);
    unsigned long SongLength(int subsong);
    std::string GetType();
    std::string GetTitle();
    std::string GetAuthor();
    std::string GetDesc();
    unsigned int GetSubsongs();
private:
    CSilentopl opl;
    std::unique_ptr<CPlayer> m_p;
};

#endif //ANDPLUGLIB_SONGINFO_H
