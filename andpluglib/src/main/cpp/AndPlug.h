#ifndef ANDPLUG_H
#define ANDPLUG_H

#include "adplug.h"
#include "emuopl.h"
#include <memory>

class AndPlug {
public:
    AndPlug();
    ~AndPlug();
    void Load(const char* song, Copl* opl);
    void Unload();
    bool isLoaded();

    // CAdPlug methods
    static const char* GetVersion();

    // CPlayer methods - Operational
    void Seek(unsigned long ms);
    bool Update(); // executes replay code for 1 tick
    void Rewind(int subsong); // rewinds to specified subsong
    float GetRefresh(); // returns needed timer refresh rate
    // CPlayer methods - Informational
    unsigned long SongLength(int subsong);
    std::string GetType(); // returns file type
    std::string GetTitle(); // returns song title
    std::string GetAuthor(); // returns song author name
    std::string GetDesc(); // returns song description
    unsigned int GetPatterns(); // returns number of patterns
    unsigned int GetPattern(); // returns currently playing pattern
    unsigned int GetOrders(); // returns size of orderlist
    unsigned int GetOrder(); // returns currently playing song position
    unsigned int GetRow(); // returns currently playing row
    unsigned int GetSpeed(); // returns current song speed
    unsigned int GetSubsongs(); // returns number of subsongs
    unsigned int GetSubsong(); // returns current subsong
    unsigned int GetInstruments(); // returns number of instruments
    const char* GetInstrument(unsigned int n); // returns n-th instrument name

private:
    std::unique_ptr<CPlayer> m_p;
};

#endif //ANDPLUG_H
