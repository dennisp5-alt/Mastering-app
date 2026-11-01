#!/usr/bin/env python3
"""Independent FFmpeg checks. Usage: report.json output.json. Audio is not altered."""
from pathlib import Path
import hashlib,json,re,subprocess,sys
report=Path(sys.argv[1]);data=json.loads(report.read_text());root=report.parent
formats=data.get('release_formats',data);assert formats['status']=='VERIFIED',formats
results={}
for key in ['wav16','mp3']:
 expected=formats[key];f=root/expected['file']
 info=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-show_format','-of','json',str(f)]))
 s=info['streams'][0];assert int(s['sample_rate'])==44100 and s['channels']==2
 if key=='wav16':assert s['codec_name']=='pcm_s16le'
 else:assert s['codec_name']=='mp3' and int(s['bit_rate'])==320000 and info['format']['tags'].get('title')
 p=subprocess.Popen(['ffmpeg','-v','error','-i',str(f),'-f','f32le','-c:a','pcm_f32le','-'],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 count=0
 while chunk:=p.stdout.read(262144):count+=len(chunk)
 err=p.stderr.read().decode();assert p.wait()==0,err
 assert count%8==0 and count//8==expected['frames'],(f.name,count//8,expected['frames'])
 meter=subprocess.run(['ffmpeg','-hide_banner','-nostats','-i',str(f),'-af','loudnorm=I=-14:TP=-1:LRA=11:print_format=json','-f','null','-'],capture_output=True,text=True,check=True)
 stats=json.loads(re.findall(r'\{[^{}]*\}',meter.stderr,re.S)[-1]);lufs=float(stats['input_i']);tp=float(stats['input_tp'])
 assert tp<=-1,(f.name,tp)
 assert abs(lufs-expected['lufs_i'])<.12,(f.name,lufs,expected['lufs_i'])
 assert abs(tp-expected['true_peak_dbtp'])<.25,(f.name,tp,expected['true_peak_dbtp'])
 with f.open('rb') as stream:sha=hashlib.file_digest(stream,'sha256').hexdigest()
 results[key]={'file':f.name,'sha256':sha,'decoded_frames':count//8,'ffmpeg_loudnorm_lufs_i':lufs,'ffmpeg_true_peak_dbtp':tp,'app_lufs_i':expected['lufs_i'],'app_true_peak_dbtp':expected['true_peak_dbtp'],'flat_gain_db':expected['additional_flat_gain_db']}
Path(sys.argv[2]).write_text(json.dumps({'status':'PASS','measurement_note':'FFmpeg loudnorm measures at 192 kHz. Duration independently decoded, including gapless metadata.','formats':results},indent=2)+'\n')
print('INDEPENDENT_RELEASE_FORMATS_PASS',report.name,[(k,v['ffmpeg_loudnorm_lufs_i'],v['ffmpeg_true_peak_dbtp'],v['decoded_frames']) for k,v in results.items()])
