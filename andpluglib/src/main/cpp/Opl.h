#ifndef AUDIOPLAYER_OPL_H
#define AUDIOPLAYER_OPL_H

#include <memory>
#include <string>
#include "AndPlug.h"

#define OPL_ERROR_OK 0
#define OPL_ERROR_ARGS -1
#define OPL_ERROR_RATE -2

#define OPL_CEMU 0
#define OPL_CKEMU 1
#define OPL_CNEMU 2
#define OPL_CTEMU 3
#define OPL_CWEMU 4

class Opl {
public:
    Opl();
    ~Opl();
    void Initialize(int emu, int rate, bool usestereo, bool left, bool right);
    void Uninitialize();
    bool Load(const char* song);
    void Unload();
    bool isLoaded();
    AndPlug* GetPlug();

    // Copl methods
    void Write(int reg, int val); // combined register select + data write
    void SetChip(int n); // select OPL chip
    int GetChip(); // returns current OPL chip
    void Init(void); // reinitialize OPL chip(s)
    Copl::ChipType GetType();
    int Update(void *buf, int size, bool repeat);

private:
    int m_rate;
    bool m_usestereo;
    bool m_left;
    bool m_right;
    float m_previous;
    std::unique_ptr<Copl> m_opl;
    std::unique_ptr<AndPlug> m_plug;

    // Debug use
public:
    void DebugPath(const char* path);
private:
    void OpenFile();
    void CloseFile();
    void CopyStereo(short* buf, int samples);
    void WriteFile(short* buf, int samples);
    void PostProcess(void* buf, int count);
    std::string m_path;
    FILE* m_stream;
    int m_file_index;
};

#endif //AUDIOPLAYER_OPL_H
