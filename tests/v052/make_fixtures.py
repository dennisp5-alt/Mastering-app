"""Synthetic test songs for the v0.5.2 host checks (no real music is stored in the repo)."""
import sys, wave, numpy as np
here = sys.argv[1]; out = sys.argv[2]
exec(open(here + '/gen_song.py').read().replace("if __name__=='__main__':", "if False:"))
from scipy.signal import butter, lfilter
def rd(p):
    w = wave.open(p); b = np.frombuffer(w.readframes(w.getnframes()), np.uint8).reshape(-1, 3).astype(np.int32)
    v = b[:, 0] | (b[:, 1] << 8) | (b[:, 2] << 16); v = np.where(v >= 1 << 23, v - (1 << 24), v)
    return v.reshape(-1, 2) / 8388608.
# Mixes for the Studio path (old vs new must give identical base masters).
write(out + '/mix.wav', song(40, 1, -18, harsh=.4, peaky=.5))
x = song(40, 21, -18, harsh=.6, peaky=.4); rng = np.random.default_rng(21)
b, a = butter(2, [200 / 24000, 330 / 24000], 'band'); lm = lfilter(b, a, rng.standard_normal((len(x), 2)), axis=0)
x = x + lm * np.sqrt(np.mean(x ** 2)) / np.sqrt(np.mean(lm ** 2)) * .9
b, a = butter(2, [3000 / 24000, 4200 / 24000], 'band'); pr = lfilter(b, a, rng.standard_normal((len(x), 2)), axis=0)
x = x + pr * np.sqrt(np.mean(x ** 2)) / np.sqrt(np.mean(pr ** 2)) * .4
write(out + '/lm1.wav', x * .5)
write(out + '/lm1c.wav', np.tanh(x * .5 * 3) / 3 * 1.2)
# A loud finished master whose own true peak is already above -1 dBTP.
exec(open(here + '/lufs.py').read())
y = song(40, 5, -16, harsh=.3, peaky=.6); lo, hi = .3, 60.
for _ in range(40):
    d = (lo * hi) ** .5; z = np.tanh(y * d); z *= 10 ** (-1.0 / 20) / np.max(np.abs(z))
    if lufs(z) < -14: lo = d
    else: hi = d
write(out + '/hot.wav', z)
