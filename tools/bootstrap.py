"""Download portable build tools into this checkout; no global installation."""
import concurrent.futures
import hashlib
import json
import pathlib
import subprocess
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1] / 'android'
TOOLS = ROOT / '.tools'
DOWNLOADS = TOOLS / 'downloads'
DOWNLOADS.mkdir(parents=True, exist_ok=True)

def fetch(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'LinkMVP-build'}), timeout=120) as r:
        return r.read()

def package(name, url, checksum, destination, extract=True):
    target = DOWNLOADS / name
    if not target.exists():
        print('Downloading ' + name, flush=True)
        temp = target.with_suffix('.part')
        with urllib.request.urlopen(urllib.request.Request(url,method='HEAD'), timeout=60) as response:
            size = int(response.headers['Content-Length'])
        parts = DOWNLOADS / (name + '.parts')
        parts.mkdir(exist_ok=True)
        chunk_size = 4 * 1024 * 1024
        def chunk(start):
            end = min(size-1, start+chunk_size-1)
            part = parts / str(start)
            if not part.exists() or part.stat().st_size != end-start+1:
                chunk_url = url + ('&' if '?' in url else '?') + 'link_part=' + str(start)
                subprocess.run(['curl.exe','--silent','--show-error','--fail','--location','--retry','2','--max-time','60','--range',f'{start}-{end}','--output',str(part),chunk_url],check=True)
            if part.stat().st_size != end-start+1:
                raise RuntimeError('Unexpected download chunk size: '+name)
            return part
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as executor:
            chunks=list(executor.map(chunk,range(0,size,chunk_size)))
        with temp.open('wb') as output:
            for part in chunks:
                output.write(part.read_bytes())
        temp.replace(target)
    actual = hashlib.file_digest(target.open('rb'), 'sha1' if len(checksum)==40 else 'sha256').hexdigest()
    if actual.lower() != checksum.lower():
        raise RuntimeError('Checksum mismatch: ' + name)
    if not extract:
        return str(target)
    marker = destination / '.extracted'
    if not marker.exists():
        print('Extracting ' + name, flush=True)
        destination.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(target) as z:
            z.extractall(destination)
        marker.touch()
    return str(destination)

def jdk():
    assets = json.loads(fetch('https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=windows'))
    p = assets[0]['binary']['package']
    return package('jdk21.zip', p['link'], p['checksum'], TOOLS / 'java')
