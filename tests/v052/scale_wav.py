import sys, wave, numpy as np
exec(open(sys.argv[1] + '/gen_song.py').read().replace("if __name__=='__main__':", "if False:"))
w = wave.open(sys.argv[2]); b = np.frombuffer(w.readframes(w.getnframes()), np.uint8).reshape(-1, 3).astype(np.int32)
v = b[:, 0] | (b[:, 1] << 8) | (b[:, 2] << 16); v = np.where(v >= 1 << 23, v - (1 << 24), v)
write(sys.argv[3], v.reshape(-1, 2) / 8388608. * 10 ** (float(sys.argv[4]) / 20))
