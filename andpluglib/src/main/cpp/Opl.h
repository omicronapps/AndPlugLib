#ifndef AUDIOPLAYER_OPL_H
#define AUDIOPLAYER_OPL_H

#include <memory>
#include <mutex>
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
    Opl(AndPlug* plug, std::mutex& adplugmtx);
    ~Opl();
    void Initialize(int emu, int rate, bool usestereo);
    void Uninitialize();
    Copl* GetCopl();
    void SetRepeat(bool repeat);

    // Copl methods
    void Write(int reg, int val); // combined register select + data write
    void SetChip(int n); // select OPL chip
    int GetChip(); // returns current OPL chip
    void Init(); // reinitialize OPL chip(s)
    Copl::ChipType GetType();
    int Update(void *buf, int size);

private:
    int m_rate;
    bool m_usestereo;
    bool m_repeat;
    int m_previous;
    AndPlug* m_plug;
    std::mutex& m_adplugmtx;
    std::unique_ptr<Copl> m_copl;

    // Debug use
public:
    void DebugPath(const char* path);
    void OpenFile();
    void CloseFile();
private:
    void WriteFile(short* buf, int samples);
    std::string m_path;
    FILE* m_stream;
    int m_file_index;
};

#endif //AUDIOPLAYER_OPL_H
