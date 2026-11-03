import sys,pathlib,xml.etree.ElementTree as E,shutil,json
from xml.dom import minidom
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[2]/'android'/'tools'))
from bootstrap import fetch,package,TOOLS
# Official Corretto distribution avoids GitHub release rate limits.
package('amazon-corretto-17.0.21.0.11-windows-x64-jdk.zip',
 'https://corretto.aws/downloads/resources/17.0.21.0.11/amazon-corretto-17.0.21.0.11-windows-x64-jdk.zip',
 '59d6514305f6fab3b18955fb17185562f0a0bbf20e1466eda33a5429f3b31618',TOOLS/'java17')
print('READY Java 17 compiler toolchain',flush=True)
repository=fetch('https://dl.google.com/android/repository/repository2-4.xml?rn=20261103')
r=E.fromstring(repository)
def record(path,target):
 # New semantic SDK versions need package.xml; source.properties alone is insufficient.
 d=minidom.parseString(repository);root=d.documentElement
 p=next(p for p in d.getElementsByTagName('remotePackage') if p.getAttribute('path')==path)
 for child in list(root.childNodes):
  if child is not p and not(child.nodeType==child.ELEMENT_NODE and child.tagName=='license'):root.removeChild(child)
 d.renameNode(root,'http://schemas.android.com/repository/android/common/02','common:repository')
 root.setAttribute('xmlns:common','http://schemas.android.com/repository/android/common/02')
 d.renameNode(p,None,'localPackage')
 for child in list(p.childNodes):
  if child.nodeType==child.ELEMENT_NODE and child.tagName in ('archives','channelRef'):p.removeChild(child)
 (target/'package.xml').write_bytes(d.toxml(encoding='UTF-8'))
for path in ['platforms;android-37.0','build-tools;37.0.0','ndk;27.1.12297006','cmake;3.22.1']:
 target=TOOLS/'sdk'/path.replace(';','/')
 if (target/'source.properties').exists():
  record(path,target)
  print('Installed',path,flush=True)
  continue
 p=next(p for p in r if p.attrib.get('path')==path)
 archives=p.findall('archives/archive');a=next((a for a in archives if a.findtext('host-os')=='windows'),archives[0]);c=a.find('complete')
 print('SDK',path,c.findtext('size'),c.findtext('url'),flush=True)
 folder=pathlib.Path(package('rn-'+c.findtext('url').split('/')[-1],'https://dl.google.com/android/repository/'+c.findtext('url'),c.findtext('checksum'),TOOLS/('rn-'+path.replace(';','-'))))
 roots=[x for x in folder.iterdir() if x.is_dir()];source=folder if (folder/'source.properties').exists() else roots[0]
 if path.startswith('platforms;'):source=next(folder.rglob('android.jar')).parent
 shutil.copytree(source,target,dirs_exist_ok=True)
 record(path,target)
 print('READY',path,flush=True)
sha=fetch('https://services.gradle.org/distributions/gradle-9.4.1-bin.zip.sha256').decode().strip()
package('gradle-9.4.1.zip','https://services.gradle.org/distributions/gradle-9.4.1-bin.zip',sha,TOOLS/'gradle94')
print('RN Android toolchain ready',flush=True)
