#ifndef AUDIOPLAYER_OPL_H
#define AUDIOPLAYER_OPL_H

#include <memory>
#include <string>
#include "AndPlug.h"

class Opl {
public:
    Opl();
    ~Opl();
    void Initialize(int rate, bool bit16, bool usestereo, bool left, bool right);
    void Uninitialize();
    CPlayer* Load(const char* song);
    void Unload();
    bool isLoaded();
    AndPlug* GetPlug();

    // Copl methods
    void Write(int reg, int val); // combined register select + data write
    void SetChip(int n); // select OPL chip
    int GetChip(); // returns current OPL chip
    void Init(void); // reinitialize OPL chip(s)
    Copl::ChipType GetType();
    unsigned long Update16(short *buf, int samples, bool repeat);
    unsigned long Update8(char *buf, int samples, bool repeat);

private:
    int m_rate;
    bool m_bit16;
    bool m_usestereo;
    bool m_left;
    bool m_right;
    std::unique_ptr<Copl> m_opl;
    std::unique_ptr<AndPlug> m_plug;

    // Debug use
public:
    void DebugPath(const char* path);
private:
    void OpenFile();
    void CloseFile();
    void WriteFile16(short *buf, unsigned long samples);
    void WriteFile8(char *buf, unsigned long samples);
    std::string m_path;
    FILE* m_stream;
    int m_FileIndex;
};

#endif //AUDIOPLAYER_OPL_H
