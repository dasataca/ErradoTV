#include <jni.h>
#include <string>
#include <string_view>
#include <vector>
#include <array>
#include <span>
#include <cstring>
#include <cstdint>
#include <android/log.h>

#define LOG_TAG "GarecaCryptoNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace crypto {

// ---------------------------------------------------------------------------
// Constantes Ofuscadas en Binario Nativo (Cero Texto Plano en DEX/APK)
// ---------------------------------------------------------------------------
constexpr uint8_t SHIELD_MASK = 0x5A;
constexpr uint8_t SHIELDED_KEY_BYTES[] = {
    0x08, 0x68, 0x63, 0x29, 0x03, 0x02, 0x2A, 0x2C,
    0x38, 0x6A, 0x1C, 0x2D, 0x39, 0x1C, 0x14, 0x36,
    0x03, 0x69, 0x10, 0x36, 0x3E, 0x1F, 0x2E, 0x36,
    0x3F, 0x0E, 0x13, 0x2D, 0x17, 0x30, 0x08, 0x0E,
    0x00, 0x0D, 0x14, 0x6B, 0x39, 0x37, 0x0F, 0x32,
    0x13, 0x0B, 0x67, 0x67
};

constexpr uint8_t ENVELOPE_SEED_MASK = 0x3C;
constexpr uint8_t ENVELOPE_SEED_BYTES[] = {
    0x7B, 0x5D, 0x4E, 0x59, 0x5F, 0x5D, 0x68, 0x6A,
    0x63, 0x79, 0x52, 0x4A, 0x59, 0x50, 0x53, 0x4C,
    0x59, 0x63, 0x6F, 0x59, 0x5F, 0x4E, 0x59, 0x48,
    0x63, 0x0E, 0x0C, 0x0E, 0x0A, 0x63, 0x6C, 0x4E,
    0x53, 0x48, 0x59, 0x5F, 0x48, 0x55, 0x53, 0x52
};

std::string get_factory_key() {
    std::string key;
    key.reserve(sizeof(SHIELDED_KEY_BYTES));
    for (uint8_t b : SHIELDED_KEY_BYTES) {
        key.push_back(static_cast<char>(b ^ SHIELD_MASK));
    }
    return key;
}

std::vector<uint8_t> get_envelope_seed() {
    std::vector<uint8_t> seed;
    seed.reserve(sizeof(ENVELOPE_SEED_BYTES));
    for (uint8_t b : ENVELOPE_SEED_BYTES) {
        seed.push_back(b ^ ENVELOPE_SEED_MASK);
    }
    return seed;
}

// ---------------------------------------------------------------------------
// Base64 Decoder (C++20 std::string_view)
// ---------------------------------------------------------------------------
std::vector<uint8_t> base64_decode(std::string_view in) {
    static const int T[256] = {
        -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
        -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
        -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,62,-1,-1,-1,63,
        52,53,54,55,56,57,58,59,60,61,-1,-1,-1,-1,-1,-1,
        -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9,10,11,12,13,14,
        15,16,17,18,19,20,21,22,23,24,25,-1,-1,-1,-1,-1,
        -1,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,
        41,42,43,44,45,46,47,48,49,50,51,-1,-1,-1,-1,-1,
        -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
        -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
        -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
        -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
        -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
        -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
        -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
        -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1
    };

    std::vector<uint8_t> out;
    out.reserve(in.size() * 3 / 4);

    uint32_t buf = 0;
    int bits = 0;
    for (char c : in) {
        if (c == '=' || c == '\r' || c == '\n' || c == ' ') continue;
        int val = T[static_cast<uint8_t>(c)];
        if (val < 0) continue;
        buf = (buf << 6) | val;
        bits += 6;
        if (bits >= 8) {
            bits -= 8;
            out.push_back(static_cast<uint8_t>((buf >> bits) & 0xFF));
        }
    }
    return out;
}

// ---------------------------------------------------------------------------
// Hex Decoder (C++20)
// ---------------------------------------------------------------------------
std::vector<uint8_t> hex_decode(std::string_view in) {
    std::vector<uint8_t> out;
    out.reserve(in.size() / 2);
    auto hex_val = [](char c) -> int {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    };
    for (size_t i = 0; i + 1 < in.size(); i += 2) {
        int h = hex_val(in[i]);
        int l = hex_val(in[i + 1]);
        if (h < 0 || l < 0) break;
        out.push_back(static_cast<uint8_t>((h << 4) | l));
    }
    return out;
}

// ---------------------------------------------------------------------------
// SHA-256 (FIPS 180-4 en C++20 con std::span)
// ---------------------------------------------------------------------------
class SHA256 {
private:
    static constexpr uint32_t K[64] = {
        0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
        0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
        0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
        0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
        0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
        0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
        0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
        0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2
    };

    static inline uint32_t rotr(uint32_t x, uint32_t n) { return (x >> n) | (x << (32 - n)); }

public:
    static std::array<uint8_t, 32> hash(std::span<const uint8_t> data) {
        uint32_t state[8] = {
            0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
            0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19
        };

        std::vector<uint8_t> msg(data.begin(), data.end());
        uint64_t bit_len = msg.size() * 8;
        msg.push_back(0x80);
        while ((msg.size() % 64) != 56) {
            msg.push_back(0x00);
        }
        for (int i = 7; i >= 0; --i) {
            msg.push_back(static_cast<uint8_t>((bit_len >> (i * 8)) & 0xFF));
        }

        for (size_t chunk = 0; chunk < msg.size(); chunk += 64) {
            uint32_t w[64];
            for (int i = 0; i < 16; ++i) {
                w[i] = (static_cast<uint32_t>(msg[chunk + i * 4]) << 24) |
                       (static_cast<uint32_t>(msg[chunk + i * 4 + 1]) << 16) |
                       (static_cast<uint32_t>(msg[chunk + i * 4 + 2]) << 8) |
                       (static_cast<uint32_t>(msg[chunk + i * 4 + 3]));
            }
            for (int i = 16; i < 64; ++i) {
                uint32_t s0 = rotr(w[i - 15], 7) ^ rotr(w[i - 15], 18) ^ (w[i - 15] >> 3);
                uint32_t s1 = rotr(w[i - 2], 17) ^ rotr(w[i - 2], 19) ^ (w[i - 2] >> 10);
                w[i] = w[i - 16] + s0 + w[i - 7] + s1;
            }

            uint32_t a = state[0], b = state[1], c = state[2], d = state[3];
            uint32_t e = state[4], f = state[5], g = state[6], h = state[7];

            for (int i = 0; i < 64; ++i) {
                uint32_t s1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25);
                uint32_t ch = (e & f) ^ ((~e) & g);
                uint32_t temp1 = h + s1 + ch + K[i] + w[i];
                uint32_t s0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22);
                uint32_t maj = (a & b) ^ (a & c) ^ (b & c);
                uint32_t temp2 = s0 + maj;

                h = g; g = f; f = e; e = d + temp1;
                d = c; c = b; b = a; a = temp1 + temp2;
            }

            state[0] += a; state[1] += b; state[2] += c; state[3] += d;
            state[4] += e; state[5] += f; state[6] += g; state[7] += h;
        }

        std::array<uint8_t, 32> out;
        for (int i = 0; i < 8; ++i) {
            out[i * 4] = static_cast<uint8_t>((state[i] >> 24) & 0xFF);
            out[i * 4 + 1] = static_cast<uint8_t>((state[i] >> 16) & 0xFF);
            out[i * 4 + 2] = static_cast<uint8_t>((state[i] >> 8) & 0xFF);
            out[i * 4 + 3] = static_cast<uint8_t>(state[i] & 0xFF);
        }
        return out;
    }
};

// ---------------------------------------------------------------------------
// AES-256-CBC Decryption (FIPS 197 en C++20)
// ---------------------------------------------------------------------------
class AES256CBC {
private:
    static constexpr uint8_t sbox[256] = {
        0x63, 0x7c, 0x77, 0x7b, 0xf2, 0x6b, 0x6f, 0xc5, 0x30, 0x01, 0x67, 0x2b, 0xfe, 0xd7, 0xab, 0x76,
        0xca, 0x82, 0xc9, 0x7d, 0xfa, 0x59, 0x47, 0xf0, 0xad, 0xd4, 0xa2, 0xaf, 0x9c, 0xa4, 0x72, 0xc0,
        0xb7, 0xfd, 0x93, 0x26, 0x36, 0x3f, 0xf7, 0xcc, 0x34, 0xa5, 0xe5, 0xf1, 0x71, 0xd8, 0x31, 0x15,
        0x04, 0xc7, 0x23, 0xc3, 0x18, 0x96, 0x05, 0x9a, 0x07, 0x12, 0x80, 0xe2, 0xeb, 0x27, 0xb2, 0x75,
        0x09, 0x83, 0x2c, 0x1a, 0x1b, 0x6e, 0x5a, 0xa0, 0x52, 0x3b, 0xd6, 0xb3, 0x29, 0xe3, 0x2f, 0x84,
        0x53, 0xd1, 0x00, 0xed, 0x20, 0xfc, 0xb1, 0x5b, 0x6a, 0xcb, 0xbe, 0x39, 0x4a, 0x4c, 0x58, 0xcf,
        0xd0, 0xef, 0xaa, 0xfb, 0x43, 0x4d, 0x33, 0x85, 0x45, 0xf9, 0x02, 0x7f, 0x50, 0x3c, 0x9f, 0xa8,
        0x51, 0xa3, 0x40, 0x8f, 0x92, 0x9d, 0x38, 0xf5, 0xbc, 0xb6, 0xda, 0x21, 0x10, 0xff, 0xf3, 0xd2,
        0xcd, 0x0c, 0x13, 0xec, 0x5f, 0x97, 0x44, 0x17, 0xc4, 0xa7, 0x7e, 0x3d, 0x64, 0x5d, 0x19, 0x73,
        0x60, 0x81, 0x4f, 0xdc, 0x22, 0x2a, 0x90, 0x88, 0x46, 0xee, 0xb8, 0x14, 0xde, 0x5e, 0x0b, 0xdb,
        0xe0, 0x32, 0x3a, 0x0a, 0x49, 0x06, 0x24, 0x5c, 0xc2, 0xd3, 0xac, 0x62, 0x91, 0x95, 0xe4, 0x79,
        0xe7, 0xc8, 0x37, 0x6d, 0x8d, 0xd5, 0x4e, 0xa9, 0x6c, 0x56, 0xf4, 0xea, 0x65, 0x7a, 0xae, 0x08,
        0xba, 0x78, 0x25, 0x2e, 0x1c, 0xa6, 0xb4, 0xc6, 0xe8, 0xdd, 0x74, 0x1f, 0x4b, 0xbd, 0x8b, 0x8a,
        0x70, 0x3e, 0xb5, 0x66, 0x48, 0x03, 0xf6, 0x0e, 0x61, 0x35, 0x57, 0xb9, 0x86, 0xc1, 0x1d, 0x9e,
        0xe1, 0xf8, 0x98, 0x11, 0x69, 0xd9, 0x8e, 0x94, 0x9b, 0x1e, 0x87, 0xe9, 0xce, 0x55, 0x28, 0xdf,
        0x8c, 0xa1, 0x89, 0x0d, 0xbf, 0xe6, 0x42, 0x68, 0x41, 0x99, 0x2d, 0x0f, 0xb0, 0x54, 0xbb, 0x16
    };

    static constexpr uint8_t rsbox[256] = {
        0x52, 0x09, 0x6a, 0xd5, 0x30, 0x36, 0xa5, 0x38, 0xbf, 0x40, 0xa3, 0x9e, 0x81, 0xf3, 0xd7, 0xfb,
        0x7c, 0xe3, 0x39, 0x82, 0x9b, 0x2f, 0xff, 0x87, 0x34, 0x8e, 0x43, 0x44, 0xc4, 0xde, 0xe9, 0xcb,
        0x54, 0x7b, 0x94, 0x32, 0xa6, 0xc2, 0x23, 0x3d, 0xee, 0x4c, 0x95, 0x0b, 0x42, 0xfa, 0xc3, 0x4e,
        0x08, 0x2e, 0xa1, 0x66, 0x28, 0xd9, 0x24, 0xb2, 0x76, 0x5b, 0xa2, 0x49, 0x6d, 0x8b, 0xd1, 0x25,
        0x72, 0xf8, 0xf6, 0x64, 0x86, 0x68, 0x98, 0x16, 0xd4, 0xa4, 0x5c, 0xcc, 0x5d, 0x65, 0xb6, 0x92,
        0x6c, 0x70, 0x48, 0x50, 0xfd, 0xed, 0xb9, 0xda, 0x5e, 0x15, 0x46, 0x57, 0xa7, 0x8d, 0x9d, 0x84,
        0x90, 0xd8, 0xab, 0x00, 0x8c, 0xbc, 0xd3, 0x0a, 0xf7, 0xe4, 0x58, 0x05, 0xb8, 0xb3, 0x45, 0x06,
        0xd0, 0x2c, 0x1e, 0x8f, 0xca, 0x3f, 0x0f, 0x02, 0xc1, 0xaf, 0xbd, 0x03, 0x01, 0x13, 0x8a, 0x6b,
        0x3a, 0x91, 0x11, 0x41, 0x4f, 0x67, 0xdc, 0xea, 0x97, 0xf2, 0xcf, 0xce, 0xf0, 0xb4, 0xe6, 0x73,
        0x96, 0xac, 0x74, 0x22, 0xe7, 0xad, 0x35, 0x85, 0xe2, 0xf9, 0x37, 0xe8, 0x1c, 0x75, 0xdf, 0x6e,
        0x47, 0xf1, 0x1a, 0x71, 0x1d, 0x29, 0xc5, 0x89, 0x6f, 0xb7, 0x62, 0x0e, 0xaa, 0x18, 0xbe, 0x1b,
        0xfc, 0x56, 0x3e, 0x4b, 0xc6, 0xd2, 0x79, 0x20, 0x9a, 0xdb, 0xc0, 0xfe, 0x78, 0xcd, 0x5a, 0xf4,
        0x1f, 0xdd, 0xa8, 0x33, 0x88, 0x07, 0xc7, 0x31, 0xb1, 0x12, 0x10, 0x59, 0x27, 0x80, 0xec, 0x5f,
        0x60, 0x51, 0x7f, 0xa9, 0x19, 0xb5, 0x4a, 0x0d, 0x2d, 0xe5, 0x7a, 0x9f, 0x93, 0xc9, 0x9c, 0xef,
        0xa0, 0xe0, 0x3b, 0x4d, 0xae, 0x2a, 0xf5, 0xb0, 0xc8, 0xeb, 0xbb, 0x3c, 0x83, 0x53, 0x99, 0x61,
        0x17, 0x2b, 0x04, 0x7e, 0xba, 0x77, 0xd6, 0x26, 0xe1, 0x69, 0x14, 0x63, 0x55, 0x21, 0x0c, 0x7d
    };

    static constexpr uint8_t Rcon[11] = {
        0x00, 0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80, 0x1B, 0x36
    };

    static inline uint8_t xtime(uint8_t x) {
        return (x << 1) ^ (((x >> 7) & 1) * 0x1B);
    }

    static inline uint8_t multiply(uint8_t x, uint8_t y) {
        return (((y & 1) * x) ^
                ((y >> 1 & 1) * xtime(x)) ^
                ((y >> 2 & 1) * xtime(xtime(x))) ^
                ((y >> 3 & 1) * xtime(xtime(xtime(x)))) ^
                ((y >> 4 & 1) * xtime(xtime(xtime(xtime(x))))));
    }

    static void key_expansion(const uint8_t* key, uint8_t* round_keys) {
        std::memcpy(round_keys, key, 32);
        uint8_t temp[4];

        for (int i = 8; i < 60; ++i) {
            std::memcpy(temp, &round_keys[(i - 1) * 4], 4);
            if (i % 8 == 0) {
                uint8_t t = temp[0];
                temp[0] = sbox[temp[1]] ^ Rcon[i / 8];
                temp[1] = sbox[temp[2]];
                temp[2] = sbox[temp[3]];
                temp[3] = sbox[t];
            } else if (i % 8 == 4) {
                temp[0] = sbox[temp[0]];
                temp[1] = sbox[temp[1]];
                temp[2] = sbox[temp[2]];
                temp[3] = sbox[temp[3]];
            }
            round_keys[i * 4 + 0] = round_keys[(i - 8) * 4 + 0] ^ temp[0];
            round_keys[i * 4 + 1] = round_keys[(i - 8) * 4 + 1] ^ temp[1];
            round_keys[i * 4 + 2] = round_keys[(i - 8) * 4 + 2] ^ temp[2];
            round_keys[i * 4 + 3] = round_keys[(i - 8) * 4 + 3] ^ temp[3];
        }
    }

    static void inv_cipher_block(uint8_t* state, const uint8_t* round_keys) {
        for (int i = 0; i < 16; ++i) state[i] ^= round_keys[14 * 16 + i];

        for (int round = 13; round >= 1; --round) {
            uint8_t temp[16];
            temp[0]  = state[0];  temp[1]  = state[13]; temp[2]  = state[10]; temp[3]  = state[7];
            temp[4]  = state[4];  temp[5]  = state[1];  temp[6]  = state[14]; temp[7]  = state[11];
            temp[8]  = state[8];  temp[9]  = state[5];  temp[10] = state[2];  temp[11] = state[15];
            temp[12] = state[12]; temp[13] = state[9];  temp[14] = state[6];  temp[15] = state[3];

            const uint8_t* rk = &round_keys[round * 16];
            for (int i = 0; i < 16; ++i) {
                temp[i] = rsbox[temp[i]] ^ rk[i];
            }

            for (int c = 0; c < 4; ++c) {
                int col = c * 4;
                uint8_t a = temp[col];
                uint8_t b = temp[col + 1];
                uint8_t c_ = temp[col + 2];
                uint8_t d = temp[col + 3];

                state[col]     = multiply(a, 0x0e) ^ multiply(b, 0x0b) ^ multiply(c_, 0x0d) ^ multiply(d, 0x09);
                state[col + 1] = multiply(a, 0x09) ^ multiply(b, 0x0e) ^ multiply(c_, 0x0b) ^ multiply(d, 0x0d);
                state[col + 2] = multiply(a, 0x0d) ^ multiply(b, 0x09) ^ multiply(c_, 0x0e) ^ multiply(d, 0x0b);
                state[col + 3] = multiply(a, 0x0b) ^ multiply(b, 0x0d) ^ multiply(c_, 0x09) ^ multiply(d, 0x0e);
            }
        }

        uint8_t temp[16];
        temp[0]  = state[0];  temp[1]  = state[13]; temp[2]  = state[10]; temp[3]  = state[7];
        temp[4]  = state[4];  temp[5]  = state[1];  temp[6]  = state[14]; temp[7]  = state[11];
        temp[8]  = state[8];  temp[9]  = state[5];  temp[10] = state[2];  temp[11] = state[15];
        temp[12] = state[12]; temp[13] = state[9];  temp[14] = state[6];  temp[15] = state[3];

        for (int i = 0; i < 16; ++i) {
            state[i] = rsbox[temp[i]] ^ round_keys[i];
        }
    }

public:
    static std::vector<uint8_t> decrypt(
        std::span<const uint8_t> ciphertext,
        const std::array<uint8_t, 32>& key,
        const std::array<uint8_t, 16>& iv
    ) {
        if (ciphertext.empty() || (ciphertext.size() % 16) != 0) {
            return {};
        }

        uint8_t round_keys[240];
        key_expansion(key.data(), round_keys);

        std::vector<uint8_t> plaintext(ciphertext.size());
        std::array<uint8_t, 16> prev_block = iv;

        for (size_t offset = 0; offset < ciphertext.size(); offset += 16) {
            uint8_t block[16];
            std::memcpy(block, &ciphertext[offset], 16);
            inv_cipher_block(block, round_keys);

            for (int i = 0; i < 16; ++i) {
                plaintext[offset + i] = block[i] ^ prev_block[i];
            }
            std::memcpy(prev_block.data(), &ciphertext[offset], 16);
        }

        // PKCS7 Unpadding
        if (plaintext.empty()) return {};
        uint8_t pad = plaintext.back();
        if (pad < 1 || pad > 16 || pad > plaintext.size()) return {};
        for (size_t i = plaintext.size() - pad; i < plaintext.size(); ++i) {
            if (plaintext[i] != pad) return {};
        }
        plaintext.resize(plaintext.size() - pad);
        return plaintext;
    }
};

} // namespace crypto

// ---------------------------------------------------------------------------
// JNI Exports para com.gareca.tv.api.CryptoUtils
// ---------------------------------------------------------------------------
extern "C" {

JNIEXPORT jstring JNICALL
Java_com_gareca_tv_api_CryptoUtils_nativeGetFactoryKey(JNIEnv* env, jobject /* this */) {
    std::string key = crypto::get_factory_key();
    return env->NewStringUTF(key.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_gareca_tv_api_CryptoUtils_nativeDecryptEnvelope(
    JNIEnv* env,
    jobject /* this */,
    jstring jEnvelopeHex
) {
    if (!jEnvelopeHex) return env->NewStringUTF("");
    const char* hexChars = env->GetStringUTFChars(jEnvelopeHex, nullptr);
    std::string_view hexView(hexChars);

    if (hexView.size() < 64) {
        env->ReleaseStringUTFChars(jEnvelopeHex, hexChars);
        return env->NewStringUTF("");
    }

    auto rawBytes = crypto::hex_decode(hexView);
    env->ReleaseStringUTFChars(jEnvelopeHex, hexChars);

    if (rawBytes.size() < 32) return env->NewStringUTF("");

    std::array<uint8_t, 16> iv;
    std::memcpy(iv.data(), rawBytes.data(), 16);
    std::span<const uint8_t> cipherSpan(rawBytes.data() + 16, rawBytes.size() - 16);

    auto seed = crypto::get_envelope_seed();
    auto keyHash = crypto::SHA256::hash(seed);

    auto decrypted = crypto::AES256CBC::decrypt(cipherSpan, keyHash, iv);
    if (decrypted.empty()) return env->NewStringUTF("");

    std::string result(decrypted.begin(), decrypted.end());
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_gareca_tv_api_CryptoUtils_nativeDecrypt(
    JNIEnv* env,
    jobject /* this */,
    jstring jPayload,
    jstring jB64Key
) {
    if (!jPayload) return env->NewStringUTF("");
    const char* payloadChars = env->GetStringUTFChars(jPayload, nullptr);
    std::string_view payloadView(payloadChars);

    // Si ya viene descifrado (JSON plano)
    if (payloadView.starts_with('{') || payloadView.starts_with('[')) {
        env->ReleaseStringUTFChars(jPayload, payloadChars);
        return jPayload;
    }

    auto rawPayload = crypto::base64_decode(payloadView);
    env->ReleaseStringUTFChars(jPayload, payloadChars);

    if (rawPayload.size() < 16) return env->NewStringUTF("");

    std::array<uint8_t, 16> iv;
    std::memcpy(iv.data(), rawPayload.data(), 16);
    std::span<const uint8_t> cipherSpan(rawPayload.data() + 16, rawPayload.size() - 16);

    auto try_decrypt_with = [&](std::string_view b64KeyStr) -> std::string {
        std::span<const uint8_t> keyInputSpan(
            reinterpret_cast<const uint8_t*>(b64KeyStr.data()),
            b64KeyStr.size()
        );
        auto keyHash = crypto::SHA256::hash(keyInputSpan);
        auto decrypted = crypto::AES256CBC::decrypt(cipherSpan, keyHash, iv);
        if (decrypted.empty()) return "";
        std::string decStr(decrypted.begin(), decrypted.end());
        if (decStr.starts_with('{') || decStr.starts_with('[')) {
            return decStr;
        }
        return "";
    };

    // 1. Intentar con la clave activa provista
    if (jB64Key) {
        const char* keyChars = env->GetStringUTFChars(jB64Key, nullptr);
        std::string res = try_decrypt_with(keyChars);
        env->ReleaseStringUTFChars(jB64Key, keyChars);
        if (!res.empty()) {
            return env->NewStringUTF(res.c_str());
        }
    }

    // 2. Fallback autónomo a la clave de fábrica en memoria
    std::string factoryKey = crypto::get_factory_key();
    std::string fallbackRes = try_decrypt_with(factoryKey);
    if (!fallbackRes.empty()) {
        return env->NewStringUTF(fallbackRes.c_str());
    }

    return env->NewStringUTF("");
}

} // extern "C"
