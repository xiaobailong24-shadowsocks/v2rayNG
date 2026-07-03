/* Minimal public API of hev-socks5-tunnel used via Kotlin/Native cinterop.
 * https://github.com/heiher/hev-socks5-tunnel */
#ifndef HEV_SOCKS5_TUNNEL_H
#define HEV_SOCKS5_TUNNEL_H

int hev_socks5_tunnel_main_from_str(const unsigned char *config_str,
                                    unsigned int config_len,
                                    int tun_fd);

void hev_socks5_tunnel_quit(void);

#endif /* HEV_SOCKS5_TUNNEL_H */
