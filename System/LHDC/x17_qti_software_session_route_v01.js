'use strict';

/*
 * PixelOS A17 / garnet — QTI software-session route probe v0.1
 *
 * Scope:
 *   btaudio_offload_if.so, live process memory only.
 *   The nine wrapper sites below hard-code AIDL SessionType=2
 *   (A2DP hardware offload). This probe changes them to SessionType=1
 *   (A2DP software encoding) after verifying the exact bytes.
 *
 * Run in android.hardware.audio.service, never in com.android.bluetooth.
 * Restarting the audio service restores the original code.
 */

const MODULE_NAME = 'btaudio_offload_if.so';
const EXPECTED_PATH = '/vendor/lib64/btaudio_offload_if.so';
const EXPECTED_SIZE = 0x30f80; // verify against the module on the phone
const OLD = 0x52800040; // mov w0, #2
const NEW = 0x52800020; // mov w0, #1

const SITES = [
  { name: 'audio_start_stream', offset: 0x167b4 },
  { name: 'audio_stream_start', offset: 0x17ba8 },
  { name: 'audio_stream_open', offset: 0x17bbc },
  { name: 'audio_stream_close', offset: 0x180dc },
  { name: 'audio_stream_stop', offset: 0x18198 },
  { name: 'audio_stream_suspend', offset: 0x186f0 },
  { name: 'audio_suspend_stream', offset: 0x18704 },
  { name: 'audio_stream_suspend_alias', offset: 0x18ac0 },
  { name: 'audio_get_codec_config', offset: 0x2651c }
];

let installed = false;
let moduleRef = null;
const patched = [];
const handles = [];
let openCalls = 0;
let startCalls = 0;
let closeCalls = 0;

function u32(p) {
  return p.readU32() >>> 0;
}

function log(tag, msg) {
  console.log('[ROUTE:' + tag + '] ' + msg);
}

function findExportPart(module, part) {
  for (const e of module.enumerateExports()) {
    if (e.type === 'function' && e.name.indexOf(part) >= 0) return e.address;
  }
  return null;
}

function attachExport(module, part, label, counterName) {
  const address = findExportPart(module, part);
  if (address === null) {
    log('OBSERVE:SKIP', label + ' export not found');
    return;
  }
  handles.push(Interceptor.attach(address, {
    onEnter(args) {
      const n = ++this[counterName];
      this.show = n <= 12;
      this.value = args[0].toUInt32();
      if (this.show) {
        log('CALL', label + ' session_arg=' + this.value + ' address=' + address);
      }
    },
    onLeave(retval) {
      if (!this.show) return;
      log('RET', label + ' session_arg=' + this.value + ' result=' + retval.toInt32());
    }
  }));
  log('OBSERVE:OK', label + '=' + address);
}

function install(module) {
  if (installed) return;
  moduleRef = module;

  if (Process.arch !== 'arm64') throw new Error('expected arm64, got ' + Process.arch);
  if (module.path !== EXPECTED_PATH) throw new Error('unexpected path: ' + module.path);
  if (module.size !== EXPECTED_SIZE) {
    throw new Error('unexpected size: 0x' + module.size.toString(16) +
                    ' expected 0x' + EXPECTED_SIZE.toString(16));
  }

  log('READY', 'pid=' + Process.id + ' module=' + module.path +
      ' base=' + module.base + ' size=0x' + module.size.toString(16));
  log('SAFETY', 'live memory only; restart android.hardware.audio.service to restore bytes');
  log('VERIFY', 'all ' + SITES.length + ' sites must contain mov w0,#2 before any patch');

  // Verify every site first. No partial mutation is allowed from a bad signature.
  for (const site of SITES) {
    const address = module.base.add(site.offset);
    const got = u32(address);
    if (got !== OLD) {
      throw new Error(site.name + ' signature mismatch at ' + address +
                      ': got=0x' + got.toString(16) +
                      ' expected=0x' + OLD.toString(16));
    }
    site.address = address;
  }

  try {
    for (const site of SITES) {
      Memory.patchCode(site.address, 4, writable => writable.writeU32(NEW));
      if (u32(site.address) !== NEW) {
        throw new Error(site.name + ' post-patch verification failed');
      }
      patched.push(site);
      log('PATCH:OK', site.name + ' ' + site.address + ' SessionType 2->1');
    }
  } catch (e) {
    // Restore any sites already changed if patching itself failed.
    for (const site of patched) {
      try { Memory.patchCode(site.address, 4, writable => writable.writeU32(OLD)); } catch (_) {}
    }
    patched.length = 0;
    throw e;
  }

  attachExport(module, '_Z17audio_stream_open13tSESSION_TYPE',
               'audio_stream_open', 'openCalls');
  attachExport(module, '_Z18audio_stream_start13tSESSION_TYPE',
               'audio_stream_start', 'startCalls');
  attachExport(module, '_Z18audio_stream_close13tSESSION_TYPE',
               'audio_stream_close', 'closeCalls');

  installed = true;
  log('INSTALLED', 'QTI A2DP wrappers now pass AIDL SessionType=1');
  log('ACTION', 'connect headphones, select LHDC, and play for 15-20 seconds');
  log('EXPECTED', 'audio_stream_open should report session_arg=1; check for PCM/read activity');
  log('LIMIT', 'this is a route experiment; it does not add an encoder or change system files');
}

function fail(e) {
  log('ABORT', String(e));
}

try {
  const module = Process.findModuleByName(MODULE_NAME);
  if (module !== null) {
    install(module);
  } else {
    log('WAIT', 'waiting for ' + MODULE_NAME);
    Process.attachModuleObserver({
      onAdded(m) {
        if (m.name !== MODULE_NAME) return;
        try { install(m); } catch (e) { fail(e); }
      }
    });
  }
} catch (e) {
  fail(e);
}
