from pathlib import Path
import subprocess,os,json,zipfile
ROOT=Path(__file__).resolve().parent
SDK=Path(os.environ.get('ANDROID_SDK_ROOT',str(Path.home()/'Library/Android/sdk')))
BT=SDK/'build-tools/34.0.0'; JAR=SDK/'platforms/android-35/android.jar'
JAVA=Path('/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home')
env=os.environ.copy();env['JAVA_HOME']=str(JAVA);env['PATH']=str(JAVA/'bin')+os.pathsep+env['PATH']
OUT=ROOT.parent.parent/'outputs';OUT.mkdir(exist_ok=True)
KEY=ROOT.parent/'signing/shortcuts.p12';KEY.parent.mkdir(exist_ok=True)
SIGN_PASS=os.environ.get('TV_SHORTCUTS_KEYSTORE_PASSWORD')
if not SIGN_PASS: raise SystemExit('Set TV_SHORTCUTS_KEYSTORE_PASSWORD before building.')
def run(*args):subprocess.run([str(a) for a in args],check=True,env=env)
if not KEY.exists():
 run(JAVA/'bin/keytool','-genkeypair','-keystore',KEY,'-storetype','PKCS12','-storepass',SIGN_PASS,'-keypass',SIGN_PASS,'-alias','tv-shortcuts','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=TV Shortcuts, OU=Personal Apps, O=Local Development, C=US')
for key,label,pkg,target,clazz,name in json.loads((ROOT/'apps.json').read_text()):
 p=ROOT/key
 run(BT/'aapt2','compile','--dir',p/'res','-o',p/'resources.zip')
 run(BT/'aapt2','link','-o',p/'unsigned.apk','-I',JAR,'--manifest',p/'AndroidManifest.xml',p/'resources.zip')
 run(JAVA/'bin/javac','--release','8','-classpath',JAR,'-d',p/'classes',p/'src/dev/tvshortcuts/ShortcutActivity.java')
 run(BT/'d8','--release','--min-api','25','--lib',JAR,'--output',p/'dex',*sorted((p/'classes').rglob('*.class')))
 with zipfile.ZipFile(p/'unsigned.apk','a',compression=zipfile.ZIP_DEFLATED) as apk: apk.write(p/'dex/classes.dex','classes.dex')
 run(BT/'zipalign','-f','4',p/'unsigned.apk',p/'aligned.apk')
 run(BT/'apksigner','sign','--ks',KEY,'--ks-key-alias','tv-shortcuts','--ks-pass','env:TV_SHORTCUTS_KEYSTORE_PASSWORD','--key-pass','env:TV_SHORTCUTS_KEYSTORE_PASSWORD','--v1-signing-enabled','true','--v2-signing-enabled','true','--v3-signing-enabled','false','--v4-signing-enabled','false','--out',OUT/(name+'.apk'),p/'aligned.apk')
 run(BT/'apksigner','verify','--verbose','--print-certs',OUT/(name+'.apk'))
 run(BT/'zipalign','-c','-v','4',OUT/(name+'.apk'))
 run(BT/'aapt','dump','badging',OUT/(name+'.apk'))
 print(name,(OUT/(name+'.apk')).stat().st_size,'bytes',flush=True)
