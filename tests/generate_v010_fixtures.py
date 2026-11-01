from pathlib import Path
import numpy as np, wave
out=Path(__import__('sys').argv[1]);out.mkdir(parents=True,exist_ok=True)
rate=48000;t=np.arange(rate*20)/rate
fade=np.minimum(1,np.minimum(t/.5,(20-t)/1))
x=np.stack([.1*np.sin(2*np.pi*220*t)+.06*np.sin(2*np.pi*880*t+.2),.095*np.sin(2*np.pi*220*t+.1)+.06*np.sin(2*np.pi*660*t+.3)],1)*fade[:,None]
def save(name,y):
 q=np.clip(np.round(y*8388608),-8388608,8388607).astype(np.int32).reshape(-1)
 b=np.stack([q&255,(q>>8)&255,(q>>16)&255],1).astype('u1')
 with wave.open(str(out/name),'wb') as w:w.setnchannels(2);w.setsampwidth(3);w.setframerate(rate);w.writeframes(b.tobytes())
sparse=x.copy()*1.17;sparse[rate*8,0]=.90;sparse[rate*8,1]=.88
save('sparse.wav',sparse)
clips=x.copy()
for start,ch in [(rate*6,0),(rate*6+2,1),(rate*8,0)]:
 clips[start-1,ch]=.25;clips[start:start+4,ch]=1;clips[start+4,ch]=.27
# Eight protected plateau samples remain untouched.
for start,ch in [(rate,0),(rate*17,1)]:
 clips[start-1,ch]=.25;clips[start:start+4,ch]=1;clips[start+4,ch]=.27
save('isolated-clips.wav',clips)
dense=x.copy()
for j in range(24):
 start=rate*4+j*1000;dense[start-1,0]=.25;dense[start:start+4,0]=1;dense[start+4,0]=.27
save('dense-clips.wav',dense)
rounded=x.copy();start=rate*6;rounded[start-1,0]=.98;rounded[start:start+4,0]=1;rounded[start+4,0]=.98
save('rounded-peaks.wav',rounded)
save('clean.wav',x)
