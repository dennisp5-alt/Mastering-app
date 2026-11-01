import numpy as np, sys, wave
sr=48000
def song(seconds, seed, level_db, harsh=0.0, peaky=0.0):
    rng=np.random.default_rng(seed); n=int(sr*seconds); t=np.arange(n)/sr
    L=np.zeros(n); R=np.zeros(n); bpm=96; beat=60/bpm
    # kick
    for k in np.arange(0,seconds,beat):
        i=int(k*sr); m=min(n-i,int(.35*sr)); tt=np.arange(m)/sr
        s=np.sin(2*np.pi*(50+90*np.exp(-tt*30))*tt)*np.exp(-tt*9)
        L[i:i+m]+=.9*s; R[i:i+m]+=.9*s
    # snare on 2,4
    for k in np.arange(beat,seconds,2*beat):
        i=int(k*sr); m=min(n-i,int(.25*sr)); tt=np.arange(m)/sr
        s=rng.standard_normal(m)*np.exp(-tt*22)*.5 + np.sin(2*np.pi*190*tt)*np.exp(-tt*30)*.4
        L[i:i+m]+=s*(1+peaky*2); R[i:i+m]+=s*.9*(1+peaky*2)
    # hats
    for k in np.arange(0,seconds,beat/2):
        i=int(k*sr); m=min(n-i,int(.05*sr)); tt=np.arange(m)/sr
        s=np.diff(np.concatenate([[0],rng.standard_normal(m)]))*np.exp(-tt*80)*.12
        L[i:i+m]+=s*.7; R[i:i+m]+=s
    # bass + chords
    roots=[55,46.25,41.2,49]
    for bi,k in enumerate(np.arange(0,seconds,4*beat)):
        i=int(k*sr); m=min(n-i,int(4*beat*sr)); tt=np.arange(m)/sr; f=roots[bi%4]
        b=np.tanh(2*np.sin(2*np.pi*f*tt))*.35*(1-np.exp(-tt*40))
        L[i:i+m]+=b; R[i:i+m]+=b
        for j,mul in enumerate([4,5,6]):
            ph=rng.uniform(0,6.28)
            c=.07*np.sin(2*np.pi*f*mul*tt+ph)+.03*np.sin(2*np.pi*f*mul*2*tt)
            L[i:i+m]+=c*(1.1-j*.2); R[i:i+m]+=c*(.7+j*.2)
    # "vocal": formant-ish buzz with vibrato, sections
    f0=220*(1+.01*np.sin(2*np.pi*5*t)); ph=2*np.pi*np.cumsum(f0)/sr
    buzz=sum(np.sin(k*ph)/k for k in range(1,25))
    env=(np.sin(2*np.pi*t/(8*beat))>-.3).astype(float); env=np.convolve(env,np.ones(2400)/2400,'same')
    from scipy.signal import lfilter, butter
    for fc,g in [(700,1),(1200,.6),(2600,.35+harsh)]:
        b,a=butter(2,[fc*.85/(sr/2),fc*1.15/(sr/2)],'band'); v=lfilter(b,a,buzz)*g*.5*env
        L+=v; R+=v
    if harsh>0:
        b,a=butter(2,[3000/(sr/2),5000/(sr/2)],'band'); fz=lfilter(b,a,rng.standard_normal(n))*harsh*.08*env
        L+=fz; R+=fz*.95
    x=np.stack([L,R],1)
    x/=np.sqrt(np.mean(x**2))+1e-12; x*=10**((level_db+3)/20)
    return x
def write(path,x):
    pk=np.max(np.abs(x))
    if pk>.999: x=x*.999/pk
    y=np.clip(np.round(x*8388607),-8388608,8388607).astype(np.int64)
    b=bytearray()
    flat=y.reshape(-1)
    arr=np.zeros(len(flat)*3,np.uint8); u=flat & 0xFFFFFF
    arr[0::3]=u&255; arr[1::3]=(u>>8)&255; arr[2::3]=(u>>16)&255
    with wave.open(path,'wb') as w: w.setnchannels(2); w.setsampwidth(3); w.setframerate(sr); w.writeframes(arr.tobytes())
if __name__=='__main__':
    kind=sys.argv[1]; out=sys.argv[2]
    if kind=='mix': x=song(40,1,-18,harsh=.4,peaky=.5)
    elif kind=='mix2': x=song(40,7,-20,harsh=.1,peaky=1.2)
    elif kind=='master':  # hot finished master: soft-clipped to approx -9.5 LUFS, peaks ~ -0.3 dBFS
        x=song(40,3,-12,harsh=.3,peaky=.6); x=np.tanh(x*2.2)/np.tanh(2.2)*.95
    elif kind=='master14':  # streaming master around -14, peaks near -1 dBFS
        x=song(40,5,-15,harsh=.3,peaky=.4); g=10**(-1.05/20)/np.max(np.abs(x)); x=np.tanh(x*g*1.5)/1.5*1.0
    write(out,x)

def make_master(seed, target_lufs, peak_dbfs, harsh=.3):
    import pyloudnorm as pyln
    meter=pyln.Meter(sr)
    x=song(40,seed,-16,harsh=harsh,peaky=.6)
    lo,hi=0.5,40.0
    for _ in range(40):
        d=(lo*hi)**.5
        y=np.tanh(x*d); y*=10**(peak_dbfs/20)/np.max(np.abs(y))
        l=meter.integrated_loudness(y)
        if l<target_lufs: lo=d
        else: hi=d
    return y
