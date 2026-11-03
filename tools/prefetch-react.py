"""Seed the Gradle cache with SHA-256 verified official large AARs, using bounded ranges."""
import concurrent.futures,hashlib,json,os,pathlib,subprocess,tempfile,urllib.request
cache=pathlib.Path(tempfile.gettempdir())/'link-rn-gradle'/'caches'/'modules-2'/'files-2.1'
def read(url):
 with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'Gradle/9.4.1'}),timeout=30) as response:return response.read()
for group,name,version in [('com.facebook.react','react-android','0.88.1'),('com.facebook.hermes','hermes-android','260318099.0.5')]:
 base=f'https://repo.maven.apache.org/maven2/{group.replace(".","/")}/{name}/{version}/'
 metadata=json.loads(read(base+f'{name}-{version}.module'))
 variant=next(v for v in metadata['variants'] if v['name'].lower().startswith('release') and any(f['name'].endswith('.aar') for f in v.get('files',[])))
 item=next(f for f in variant['files'] if f['name'].endswith('.aar'))
 target=cache/group/name/version/item['sha1']/item['name'];target.parent.mkdir(parents=True,exist_ok=True)
 if target.exists() and hashlib.file_digest(target.open('rb'),'sha256').hexdigest()==item['sha256']:print('Cached',name,flush=True);continue
 parts=cache.parent/('link-prefetch-1m-'+name);parts.mkdir(exist_ok=True);size=item['size'];chunk=1024*1024
 previous=cache.parent/('link-prefetch-'+name)
 if previous.exists():
  for old in previous.iterdir():
   if not old.name.isdigit():continue
   start=int(old.name)
   if old.stat().st_size!=min(4*chunk,size-start):continue
   data=old.read_bytes()
   for offset in range(0,len(data),chunk):(parts/str(start+offset)).write_bytes(data[offset:offset+chunk])
 print('Fetching',item['name'],size,flush=True)
 def download(start):
  end=min(size-1,start+chunk-1);part=parts/str(start)
  if not part.exists() or part.stat().st_size!=end-start+1:
   subprocess.run(['curl.exe','-A','Gradle/9.4.1','--silent','--show-error','--fail','--location','--retry','2','--max-time','60','--range',f'{start}-{end}','--output',str(part),base+item['url']+'?link_part='+str(start)],check=True)
  if part.stat().st_size!=end-start+1:raise RuntimeError('Invalid range length')
  return part
 with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:files=list(pool.map(download,range(0,size,chunk)))
 pending=target.with_suffix('.part')
 with pending.open('wb') as out:
  for part in files:out.write(part.read_bytes())
 if hashlib.file_digest(pending.open('rb'),'sha256').hexdigest()!=item['sha256']:raise RuntimeError('AAR checksum mismatch')
 pending.replace(target);print('Verified',name,flush=True)
