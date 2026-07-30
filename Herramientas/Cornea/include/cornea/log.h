#pragma once

#define C_RESET   "\033[0m"
#define C_RED     "\033[1;31m"
#define C_GREEN   "\033[1;32m"
#define C_YELLOW  "\033[1;33m"
#define C_BLUE    "\033[1;34m"
#define C_MAGENTA "\033[1;35m"
#define C_CYAN    "\033[1;36m"
#define C_WHITE   "\033[1;37m"
#define C_GRAY    "\033[1;30m"
#define C_BOLD    "\033[1m"

// Runtime ANSI toggle — these pointers are swapped by set_ansi()
extern const char* TAG_ENGINE;
extern const char* TAG_IRIS;
extern const char* TAG_OVERLAY;
extern const char* TAG_OCR;
extern const char* TAG_VISUAL;
extern const char* TAG_DIAGNOSTICS;
extern const char* TAG_CLASSIFIER;
extern const char* TAG_VULNDB;
extern const char* TAG_DISCOVERY;
extern const char* TAG_FINGERPRINT;
extern const char* TAG_LOGO;
extern const char* TAG_CORNEA;
extern const char* TAG_CONFIG;
extern const char* TAG_CAPTURE;
extern const char* TAG_DUAL;
extern const char* TAG_VIEWER;

extern const char* ERR;
extern const char* RST;
extern bool g_ansi;

void set_ansi(bool on);
