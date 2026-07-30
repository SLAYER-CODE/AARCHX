#include "cornea/log.h"

static const char* TAG_ENGINE_COL = "\033[1;32m[Engine] \033[0m";
static const char* TAG_ENGINE_PLN = "[Engine] ";
static const char* TAG_IRIS_COL   = "\033[1;33m[Iris] \033[0m";
static const char* TAG_IRIS_PLN   = "[Iris] ";
static const char* TAG_OVERLAY_COL = "\033[1;37m[Overlay] \033[0m";
static const char* TAG_OVERLAY_PLN = "[Overlay] ";
static const char* TAG_OCR_COL    = "\033[1;36m[OCR] \033[0m";
static const char* TAG_OCR_PLN    = "[OCR] ";
static const char* TAG_VISUAL_COL = "\033[1;31m[VisualDetector] \033[0m";
static const char* TAG_VISUAL_PLN = "[VisualDetector] ";
static const char* TAG_DIAG_COL   = "\033[1;35m[Diagnostics] \033[0m";
static const char* TAG_DIAG_PLN   = "[Diagnostics] ";
static const char* TAG_CLASS_COL  = "\033[1;34m[DeviceClassifier] \033[0m";
static const char* TAG_CLASS_PLN  = "[DeviceClassifier] ";
static const char* TAG_VULNDB_COL = "\033[1;33m[VulnDB] \033[0m";
static const char* TAG_VULNDB_PLN = "[VulnDB] ";
static const char* TAG_DISC_COL   = "\033[1;30m[Discovery] \033[0m";
static const char* TAG_DISC_PLN   = "[Discovery] ";
static const char* TAG_FP_COL     = "\033[1;30m[Fingerprint] \033[0m";
static const char* TAG_FP_PLN     = "[Fingerprint] ";
static const char* TAG_LOGO_COL   = "\033[1;30m[LogoDetector] \033[0m";
static const char* TAG_LOGO_PLN   = "[LogoDetector] ";
static const char* TAG_CORNEA_COL = "\033[1;32m[Cornea] \033[0m";
static const char* TAG_CORNEA_PLN = "[Cornea] ";
static const char* TAG_CFG_COL    = "\033[1;31m[Config] \033[0m";
static const char* TAG_CFG_PLN    = "[Config] ";
static const char* TAG_CAP_COL    = "\033[1;33m[capture_test] \033[0m";
static const char* TAG_CAP_PLN    = "[capture_test] ";
static const char* TAG_DUAL_COL   = "\033[1;35m[dual_camera] \033[0m";
static const char* TAG_DUAL_PLN   = "[dual_camera] ";
static const char* TAG_VIEW_COL   = "\033[1;34m[Viewer] \033[0m";
static const char* TAG_VIEW_PLN   = "[Viewer] ";

const char* TAG_ENGINE      = TAG_ENGINE_COL;
const char* TAG_IRIS        = TAG_IRIS_COL;
const char* TAG_OVERLAY     = TAG_OVERLAY_COL;
const char* TAG_OCR         = TAG_OCR_COL;
const char* TAG_VISUAL      = TAG_VISUAL_COL;
const char* TAG_DIAGNOSTICS = TAG_DIAG_COL;
const char* TAG_CLASSIFIER  = TAG_CLASS_COL;
const char* TAG_VULNDB      = TAG_VULNDB_COL;
const char* TAG_DISCOVERY   = TAG_DISC_COL;
const char* TAG_FINGERPRINT = TAG_FP_COL;
const char* TAG_LOGO        = TAG_LOGO_COL;
const char* TAG_CORNEA      = TAG_CORNEA_COL;
const char* TAG_CONFIG      = TAG_CFG_COL;
const char* TAG_CAPTURE     = TAG_CAP_COL;
const char* TAG_DUAL        = TAG_DUAL_COL;
const char* TAG_VIEWER      = TAG_VIEW_COL;

bool g_ansi = true;

const char* ERR = "\033[1;31m\033[1m";
const char* RST = "\033[0m";

void set_ansi(bool on) {
    TAG_ENGINE      = on ? TAG_ENGINE_COL      : TAG_ENGINE_PLN;
    TAG_IRIS        = on ? TAG_IRIS_COL        : TAG_IRIS_PLN;
    TAG_OVERLAY     = on ? TAG_OVERLAY_COL     : TAG_OVERLAY_PLN;
    TAG_OCR         = on ? TAG_OCR_COL         : TAG_OCR_PLN;
    TAG_VISUAL      = on ? TAG_VISUAL_COL      : TAG_VISUAL_PLN;
    TAG_DIAGNOSTICS = on ? TAG_DIAG_COL        : TAG_DIAG_PLN;
    TAG_CLASSIFIER  = on ? TAG_CLASS_COL       : TAG_CLASS_PLN;
    TAG_VULNDB      = on ? TAG_VULNDB_COL      : TAG_VULNDB_PLN;
    TAG_DISCOVERY   = on ? TAG_DISC_COL        : TAG_DISC_PLN;
    TAG_FINGERPRINT = on ? TAG_FP_COL          : TAG_FP_PLN;
    TAG_LOGO        = on ? TAG_LOGO_COL        : TAG_LOGO_PLN;
    TAG_CORNEA      = on ? TAG_CORNEA_COL      : TAG_CORNEA_PLN;
    TAG_CONFIG      = on ? TAG_CFG_COL         : TAG_CFG_PLN;
    TAG_CAPTURE     = on ? TAG_CAP_COL         : TAG_CAP_PLN;
    TAG_DUAL        = on ? TAG_DUAL_COL        : TAG_DUAL_PLN;
    TAG_VIEWER      = on ? TAG_VIEW_COL        : TAG_VIEW_PLN;
    // ERR/RST are always defined, they just get prepended/appended conditionally
}
