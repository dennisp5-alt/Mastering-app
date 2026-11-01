import numpy as np
from scipy.signal import lfilter
def lufs(x, sr=48000):
    b1=[1.53512485958697,-2.69169618940638,1.19839281085285]; a1=[1,-1.69065929318241,0.73248077421585]
    b2=[1.0,-2.0,1.0]; a2=[1,-1.99004745483398,0.99007225036621]
    y=lfilter(b2,a2,lfilter(b1,a1,x,axis=0),axis=0)
    blk=int(.4*sr); hop=int(.1*sr); z=[]
    for i in range(0,len(y)-blk+1,hop): z.append(np.sum(np.mean(y[i:i+blk]**2,axis=0)))
    z=np.array(z); l=-0.691+10*np.log10(z+1e-20)
    g=z[l>-70]; rel=-0.691+10*np.log10(np.mean(g))-10
    g2=z[(l>-70)&(l>rel)]
    return -0.691+10*np.log10(np.mean(g2))
