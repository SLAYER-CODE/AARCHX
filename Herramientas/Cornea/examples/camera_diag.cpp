/*
 * Iris Camera Diagnostic Tool
 *
 * Enlista dispositivos v4l2 disponibles, sus capacidades,
 * y prueba abrir cada uno como VIDEO_CAPTURE.
 *
 * Uso: camera_diag
 */

#include <cstdio>
#include <cstring>
#include <string>
#include <vector>
#include <glob.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <linux/videodev2.h>

static void probe_device(const char* path) {
    int fd = ::open(path, O_RDWR);
    if (fd < 0) {
        printf("  %-20s CANNOT OPEN\n", path);
        return;
    }

    struct v4l2_capability cap;
    memset(&cap, 0, sizeof(cap));
    if (ioctl(fd, VIDIOC_QUERYCAP, &cap) < 0) {
        printf("  %-20s VIDIOC_QUERYCAP failed\n", path);
        close(fd);
        return;
    }

    const char* type_str = "?";
    if (cap.capabilities & V4L2_CAP_VIDEO_CAPTURE)
        type_str = "CAPTURE";
    if (cap.capabilities & V4L2_CAP_VIDEO_OUTPUT)
        type_str = "OUTPUT";
    if ((cap.capabilities & V4L2_CAP_VIDEO_CAPTURE) &&
        (cap.capabilities & V4L2_CAP_VIDEO_OUTPUT))
        type_str = "CAPTURE+OUTPUT";
    if (cap.capabilities & V4L2_CAP_VIDEO_CAPTURE_MPLANE)
        type_str = "CAPTURE_MPLANE";

    printf("  %-20s %s | driver=%s card=%s\n",
           path, type_str, cap.driver, cap.card);

    // Try to set format as VIDEO_CAPTURE
    struct v4l2_format fmt;
    memset(&fmt, 0, sizeof(fmt));
    fmt.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    fmt.fmt.pix.width = 640;
    fmt.fmt.pix.height = 480;
    fmt.fmt.pix.pixelformat = V4L2_PIX_FMT_YUYV;

    if (ioctl(fd, VIDIOC_S_FMT, &fmt) == 0) {
        char fourcc[5] = {0};
        memcpy(fourcc, &fmt.fmt.pix.pixelformat, 4);
        printf("    -> CAPTURE OK: %dx%d fourcc=%s\n",
               fmt.fmt.pix.width, fmt.fmt.pix.height, fourcc);
    } else {
        printf("    -> CAPTURE FAILED (not a capture device)\n");
    }

    // Try VIDEO_OUTPUT format
    memset(&fmt, 0, sizeof(fmt));
    fmt.type = V4L2_BUF_TYPE_VIDEO_OUTPUT;
    fmt.fmt.pix.width = 640;
    fmt.fmt.pix.height = 480;
    fmt.fmt.pix.pixelformat = V4L2_PIX_FMT_YUYV;

    if (ioctl(fd, VIDIOC_S_FMT, &fmt) == 0) {
        char fourcc[5] = {0};
        memcpy(fourcc, &fmt.fmt.pix.pixelformat, 4);
        printf("    -> OUTPUT OK: %dx%d fourcc=%s\n",
               fmt.fmt.pix.width, fmt.fmt.pix.height, fourcc);
    } else {
        printf("    -> OUTPUT FAILED (not an output device)\n");
    }

    // List capture formats
    struct v4l2_fmtdesc fmtd;
    memset(&fmtd, 0, sizeof(fmtd));
    fmtd.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    while (ioctl(fd, VIDIOC_ENUM_FMT, &fmtd) == 0) {
        char fourcc[5] = {0};
        memcpy(fourcc, &fmtd.pixelformat, 4);
        printf("    -> Capture fmt [%d] %s (%s)\n", fmtd.index, fourcc, fmtd.description);
        fmtd.index++;
    }

    close(fd);
}

int main() {
    printf("=== Iris Camera Diagnostic ===\n\n");

    // Enumerate /dev/video*
    glob_t globbuf;
    if (glob("/dev/video*", 0, nullptr, &globbuf) == 0) {
        printf("Devices found: %zu\n\n", globbuf.gl_pathc);
        for (size_t i = 0; i < globbuf.gl_pathc; i++) {
            probe_device(globbuf.gl_pathv[i]);
            printf("\n");
        }
        globfree(&globbuf);
    } else {
        printf("No /dev/video* devices found\n");
    }

    printf("=== Done ===\n");
    return 0;
}
