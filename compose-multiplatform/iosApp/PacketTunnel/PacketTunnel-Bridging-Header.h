#ifndef PacketTunnel_Bridging_Header_h
#define PacketTunnel_Bridging_Header_h

// hev-socks5-tunnel public API (from libhev-socks5-tunnel.a, iOS build).
// See https://github.com/heiher/hev-socks5-tunnel

int hev_socks5_tunnel_main_from_str(const unsigned char *config_str,
                                    unsigned int config_len,
                                    int tun_fd);

void hev_socks5_tunnel_quit(void);

#endif /* PacketTunnel_Bridging_Header_h */
