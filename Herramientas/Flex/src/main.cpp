#include "iris/canvas.h"

#include <csignal>
#include <cstddef>
#include <cstring>
#include <iostream>
#include <atomic>
#include <unistd.h>
#include <getopt.h>
#include <sys/wait.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/time.h>
#include <cstdlib>
#include <vector>

static std::atomic<bool> g_running{true};
static pid_t g_ffmpeg_video_pid = 0;
static pid_t g_ffmpeg_audio_pid = 0;
static pid_t g_audio_bridge_pid = 0;

static void kill_all() {
    for (pid_t p : {g_ffmpeg_video_pid, g_ffmpeg_audio_pid, g_audio_bridge_pid}) {
        if (p > 0) { kill(p, SIGTERM); usleep(50000); kill(p, SIGKILL); }
    }
    for (pid_t p : {g_ffmpeg_video_pid, g_ffmpeg_audio_pid, g_audio_bridge_pid}) {
        if (p > 0) { waitpid(p, nullptr, WNOHANG); }
    }
}

static void signal_handler(int) {
    g_running = false;
    kill_all();
}

struct Config {
    std::string video_path;
    std::string display_socket = "canvas-display";
    std::string ffmpeg_path = "/usr/bin/ffmpeg";
    std::string audio_socket_path = "@flex_audio";
    int force_w = 0, force_h = 0;
    int max_w = 480, max_h = 480;
    int display_w = 0, display_h = 0;
    int fps = 24;
    int audio_rate = 44100;
    bool loop = false, verbose = false, no_audio = false;
};

static Config parse_args(int argc, char** argv) {
    Config cfg;
    static struct option long_opts[] = {
        {"display",      required_argument, nullptr, 'd'},
        {"size",         required_argument, nullptr, 's'},
        {"max-size",     required_argument, nullptr, 'M'},
        {"display-size", required_argument, nullptr, 'D'},
        {"fps",          required_argument, nullptr, 'f'},
        {"ffmpeg",       required_argument, nullptr, 'b'},
        {"audio-socket", required_argument, nullptr, 'a'},
        {"no-audio",     no_argument,       nullptr, 'N'},
        {"loop",         no_argument,       nullptr, 'l'},
        {"verbose",      no_argument,       nullptr, 'v'},
        {"help",         no_argument,       nullptr, 'h'},
        {nullptr, 0, nullptr, 0}
    };

    int opt;
    while ((opt = getopt_long(argc, argv, "d:s:M:D:f:b:a:Nlvh", long_opts, nullptr)) != -1) {
        switch (opt) {
            case 'd': cfg.display_socket = optarg; break;
            case 's': if (sscanf(optarg, "%dx%d", &cfg.force_w, &cfg.force_h) != 2) {
                          std::cerr << "Invalid size: " << optarg << "\n"; exit(1);
                      } break;
            case 'M': {
                int mw = 0, mh = 0;
                if (sscanf(optarg, "%dx%d", &mw, &mh) == 2) { cfg.max_w = mw; cfg.max_h = mh; }
            } break;
            case 'D': {
                int dw = 0, dh = 0;
                if (sscanf(optarg, "%dx%d", &dw, &dh) == 2) { cfg.display_w = dw; cfg.display_h = dh; }
            } break;
            case 'f': cfg.fps = std::atoi(optarg); break;
            case 'b': cfg.ffmpeg_path = optarg; break;
            case 'a': cfg.audio_socket_path = optarg; break;
            case 'N': cfg.no_audio = true; break;
            case 'l': cfg.loop = true; break;
            case 'v': cfg.verbose = true; break;
            case 'h':
                std::cout << "Usage: flex <video> [options]\n"
                          << "  -d, --display SOCKET       Display socket\n"
                          << "  -s, --size WxH             Force exact video size\n"
                          << "  -M, --max-size WxH         Max video size (default: 480x480)\n"
                          << "  -D, --display-size WxH     Canvas/window size for centering\n"
                          << "  -f, --fps N                Framerate (default: 24)\n"
                          << "  -b, --ffmpeg PATH          ffmpeg path (default: /usr/bin/ffmpeg)\n"
                          << "  -a, --audio-socket PATH    Audio socket (default: @flex_audio)\n"
                          << "  -N, --no-audio             Disable audio\n"
                          << "  -l, --loop                 Loop video\n"
                          << "  -v, --verbose\n";
                exit(0);
            default: exit(1);
        }
    }

    if (optind >= argc) {
        std::cerr << "Error: missing video path\nUsage: flex <video> [options]\n";
        exit(1);
    }
    cfg.video_path = argv[optind];
    return cfg;
}

static bool read_exact(int fd, void* buf, size_t size) {
    size_t total = 0;
    while (total < size) {
        ssize_t n = read(fd, (char*)buf + total, size - total);
        if (n <= 0) return false;
        total += n;
    }
    return true;
}

static void hide_cam_btn() {
    int fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (fd < 0) return;
    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    addr.sun_path[0] = '\0';
    strcpy(addr.sun_path + 1, "cam-ctrl");
    socklen_t len = offsetof(struct sockaddr_un, sun_path) + 1 + 8;
    if (connect(fd, (struct sockaddr*)&addr, len) < 0) { close(fd); return; }
    write(fd, "btn hide\n", 9);
    shutdown(fd, SHUT_WR);
    close(fd);
}

static pid_t spawn_video_ffmpeg(const Config& cfg, int vw, int vh, int out_fd) {
    pid_t pid = fork();
    if (pid < 0) return -1;
    if (pid > 0) return pid;

    dup2(out_fd, STDOUT_FILENO);
    close(out_fd);

    std::string size_arg = std::to_string(vw) + "x" + std::to_string(vh);
    execlp(cfg.ffmpeg_path.c_str(), cfg.ffmpeg_path.c_str(),
           "-i", cfg.video_path.c_str(),
           "-f", "rawvideo",
           "-pix_fmt", "bgra",
           "-s", size_arg.c_str(),
           "-r", std::to_string(cfg.fps).c_str(),
           "-an", "-sn", "-dn",
           "-loglevel", "error",
           "-", nullptr);
    _exit(1);
}

static bool write_all(int fd, const void* buf, size_t size) {
    size_t total = 0;
    while (total < size) {
        ssize_t n = write(fd, (const char*)buf + total, size - total);
        if (n <= 0) return false;
        total += n;
    }
    return true;
}

static int connect_socket(const std::string& path) {
    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;

    socklen_t addr_len;
    bool use_abstract = (!path.empty() && path[0] == '@');
    if (use_abstract) {
        addr.sun_path[0] = '\0';
        size_t namelen = path.size() - 1;
        if (namelen > sizeof(addr.sun_path) - 1) namelen = sizeof(addr.sun_path) - 1;
        memcpy(addr.sun_path + 1, path.data() + 1, namelen);
        addr_len = offsetof(struct sockaddr_un, sun_path) + 1 + namelen;
    } else {
        size_t plen = path.size();
        if (plen > sizeof(addr.sun_path) - 1) plen = sizeof(addr.sun_path) - 1;
        memcpy(addr.sun_path, path.data(), plen);
        addr_len = sizeof(addr);
    }

    int fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (fd < 0) return -1;
    if (connect(fd, (struct sockaddr*)&addr, addr_len) < 0) { close(fd); return -1; }
    return fd;
}

static pid_t spawn_audio_pipeline(const Config& cfg) {
    int audio_pipe[2];
    if (pipe(audio_pipe) < 0) return -1;

    g_ffmpeg_audio_pid = fork();
    if (g_ffmpeg_audio_pid < 0) { close(audio_pipe[0]); close(audio_pipe[1]); return -1; }

    if (g_ffmpeg_audio_pid == 0) {
        close(audio_pipe[0]);
        dup2(audio_pipe[1], STDOUT_FILENO);
        close(audio_pipe[1]);
        execlp(cfg.ffmpeg_path.c_str(), cfg.ffmpeg_path.c_str(),
               "-i", cfg.video_path.c_str(),
               "-vn", "-sn", "-dn",
               "-f", "s16le",
               "-acodec", "pcm_s16le",
               "-ar", std::to_string(cfg.audio_rate).c_str(),
               "-ac", "2",
               "-loglevel", "error",
               "-", nullptr);
        _exit(1);
    }

    close(audio_pipe[1]);

    // Bridge process: read PCM from pipe, write to audioplay socket
    g_audio_bridge_pid = fork();
    if (g_audio_bridge_pid < 0) { close(audio_pipe[0]); return -1; }

    if (g_audio_bridge_pid == 0) {
        int sock = -1;
        // Retry connection to audioplay socket
        for (int retry = 0; retry < 20 && g_running; retry++) {
            sock = connect_socket(cfg.audio_socket_path);
            if (sock >= 0) break;
            usleep(250000);
        }
        if (sock < 0) {
            if (cfg.verbose) std::cerr << "Audio: cannot connect to audioplay socket\n";
            _exit(1);
        }
        if (cfg.verbose) std::cerr << "Audio: connected to audioplay\n";

        char buf[65536];
        ssize_t n;
        while (g_running && (n = read(audio_pipe[0], buf, sizeof(buf))) > 0) {
            if (!write_all(sock, buf, n)) break;
        }
        close(sock);
        close(audio_pipe[0]);
        if (cfg.verbose) std::cerr << "Audio: bridge done\n";
        _exit(0);
    }

    close(audio_pipe[0]);
    return 0;
}

static void play_video(const Config& cfg) {
    hide_cam_btn();

    int vw = cfg.force_w, vh = cfg.force_h;

    if (vw == 0 || vh == 0) {
        // locate ffprobe alongside ffmpeg
        std::string probe = cfg.ffmpeg_path;
        auto pfx = cfg.ffmpeg_path.rfind('/');
        if (pfx != std::string::npos) {
            auto dir = cfg.ffmpeg_path.substr(0, pfx + 1);
            auto bn  = cfg.ffmpeg_path.substr(pfx + 1);
            if (bn.rfind("ffmpeg", 0) == 0) {
                probe = dir + "ffprobe" + bn.substr(6);
            }
        }
        std::string cmd = probe + " -v error -select_streams v:0"
            " -show_entries stream=width,height -of csv=s=x:p=0 \""
            + cfg.video_path + "\" 2>&1";
        FILE* fp = popen(cmd.c_str(), "r");
        if (fp) {
            char buf[64];
            if (fgets(buf, sizeof(buf), fp)) {
                int tw = 0, th = 0;
                if (sscanf(buf, "%dx%d", &tw, &th) == 2) { vw = tw; vh = th; }
            }
            pclose(fp);
        }
        if (vw == 0 || vh == 0) { std::cerr << "Cannot detect video size, use -s WxH\n"; return; }
    }

    // constrain to max-size (keeping aspect ratio)
    if (vw > cfg.max_w || vh > cfg.max_h) {
        double scale = (double)cfg.max_w / vw;
        if ((double)cfg.max_h / vh < scale) scale = (double)cfg.max_h / vh;
        vw = (int)(vw * scale);
        vh = (int)(vh * scale);
        if ((vw & 1)) ++vw;   // keep even for ffmpeg
        if ((vh & 1)) ++vh;
    }

    if (cfg.verbose) std::cout << "Video: " << vw << "x" << vh << " @ " << cfg.fps << " fps\n";

    int cw = cfg.display_w > 0 ? cfg.display_w : vw;
    int ch = cfg.display_h > 0 ? cfg.display_h : vh;
    int ox = (cw - vw) / 2;
    int oy = (ch - vh) / 2;
    bool centered = (cw != vw || ch != vh);

    iris::Canvas canvas;
    if (!canvas.init(cw, ch)) { std::cerr << "Canvas init failed\n"; return; }
    if (!canvas.connect_overlay(cfg.display_socket)) { std::cerr << "Cannot connect to overlay\n"; return; }

    const size_t frame_size = (size_t)vw * vh * 4;
    std::vector<uint8_t> frame(frame_size);

    do {
        int pipe_video[2];
        if (pipe(pipe_video) < 0) { perror("pipe"); return; }

        g_ffmpeg_video_pid = spawn_video_ffmpeg(cfg, vw, vh, pipe_video[1]);
        if (g_ffmpeg_video_pid < 0) { close(pipe_video[0]); close(pipe_video[1]); return; }
        close(pipe_video[1]);

        if (!cfg.no_audio && !cfg.loop) {
            if (spawn_audio_pipeline(cfg) < 0) {
                if (cfg.verbose) std::cout << "Audio pipeline failed, video only\n";
            }
        }

        canvas.clear(0xFF000000);
        canvas.present();

        int64_t frame_interval_us = 1000000 / cfg.fps;
        struct timeval tv;
        gettimeofday(&tv, nullptr);
        int64_t next_frame_us = (int64_t)tv.tv_sec * 1000000 + tv.tv_usec;

        while (g_running) {
            if (!read_exact(pipe_video[0], frame.data(), frame_size)) break;

            if (centered) {
                canvas.clear(0xFF000000);
                uint32_t* px = canvas.pixels();
                const uint32_t* src = reinterpret_cast<const uint32_t*>(frame.data());
                for (int y = 0; y < vh; y++)
                    memcpy(px + (oy + y) * cw + ox, src + y * vw, vw * 4);
            } else {
                canvas.load_frame(frame.data(), frame_size);
            }
            canvas.present();

            if (canvas.poll_commands() && canvas.touch_down()) {
                if (cfg.verbose) std::cout << "Touch — exiting\n";
                g_running = false;
                break;
            }

            // pace to target fps
            next_frame_us += frame_interval_us;
            gettimeofday(&tv, nullptr);
            int64_t now_us = (int64_t)tv.tv_sec * 1000000 + tv.tv_usec;
            int64_t wait_us = next_frame_us - now_us;
            if (wait_us > 0) usleep(wait_us);
            else if (-wait_us > frame_interval_us) {
                // fell behind more than 1 frame — reset
                next_frame_us = now_us;
            }
        }

        close(pipe_video[0]);
        kill_all();

    } while (g_running && cfg.loop);
}

int main(int argc, char** argv) {
    signal(SIGINT, signal_handler);
    signal(SIGTERM, signal_handler);

    auto cfg = parse_args(argc, argv);
    if (cfg.verbose) {
        std::cout << "FFmpeg: " << cfg.ffmpeg_path << "\n";
        if (!cfg.no_audio)
            std::cout << "Audio socket: " << cfg.audio_socket_path << "\n";
        std::cout << (cfg.no_audio ? "Audio: disabled\n" : "Audio: enabled\n");
    }

    play_video(cfg);
    kill_all();
    return 0;
}
