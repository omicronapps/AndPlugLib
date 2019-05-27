#ifndef AUDIOPLAYER_OPL_H
#define AUDIOPLAYER_OPL_H

#include "AndPlug.h"

class Opl {
public:
    Opl(int rate, bool bit16, bool usestereo, bool left, bool right);
    ~Opl();
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
    bool m_usestereo;
    bool m_left;
    bool m_right;
    Copl* m_opl;
    AndPlug m_plug;
};

#endif //AUDIOPLAYER_OPL_H
