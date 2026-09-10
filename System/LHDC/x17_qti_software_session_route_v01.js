'use strict';

/*
 * PixelOS A17 / garnet — QTI software-session route probe v0.2
 *
 * btaudio_offload_if.so has nine thin wrappers that hard-code
 * AIDL SessionType=2 (A2DP hardware offload). This live probe changes
 * only those exact instructions to SessionType=1 (A2DP software encoding).
 *
 * Run in android.hardware.audio.service, never com.android.bluetooth.
 * Restarting android.hardware.audio.service restores all original bytes.
 */

const MODULE_NAME = 'btaudio_offload_if.so';
const EXPECTED_PATH = '/vendor/lib64/btaudio_offload_if.so';
const MIN_SIZE = 0x26520;
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
const patched = [];
const handles = [];
const counters = { open: 0, start: 0, close: 0 };

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

function attachExport(module, part, label, counterKey) {
  const address = findExportPart(module, part);
  if (address === null) {
    log('OBSERVE:SKIP', label + ' export not found');
    return;
  }
  handles.push(Interceptor.attach(address, {
    onEnter(args) {
      const n = ++counters[counterKey];
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

  if (Process.arch !== 'arm64') {
    throw new Error('expected arm64, got ' + Process.arch);
  }
  if (module.path !== EXPECTED_PATH) {
    throw new Error('unexpected path: ' + module.path);
  }
  if (module.size < MIN_SIZE) {
    throw new Error('module too small: 0x' + module.size.toString(16));
  }

  log('READY', 'pid=' + Process.id + ' module=' + module.path +
      ' base=' + module.base + ' size=0x' + module.size.toString(16));
  log('SAFETY', 'live memory only; restart android.hardware.audio.service to restore bytes');
  log('VERIFY', 'all ' + SITES.length + ' sites must contain mov w0,#2 before any patch');

  // Verify every site first. A bad signature aborts before any mutation.
  for (const site of SITES) {
    const address = module.base.add(site.offset);
    const got = u32(address);
    if (got !== OLD) {
      throw new Error(site.name + ' signature mismatch at ' + address +
                      ': got=0x' + got.toString(16) +
                      ' expected=0x' + OLD.toString(16));
    }
    site.address = address;
    log('VERIFY:OK', site.name + ' ' + address + ' word=0x' + got.toString(16));
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
    for (const site of patched) {
      try {
        Memory.patchCode(site.address, 4, writable => writable.writeU32(OLD));
      } catch (_) {}
    }
    patched.length = 0;
    throw e;
  }

  attachExport(module, '_Z17audio_stream_open13tSESSION_TYPE',
               'audio_stream_open', 'open');
  attachExport(module, '_Z18audio_stream_start13tSESSION_TYPE',
               'audio_stream_start', 'start');
  attachExport(module, '_Z18audio_stream_close13tSESSION_TYPE',
               'audio_stream_close', 'close');

  installed = true;
  log('INSTALLED', 'QTI wrappers now pass AIDL SessionType=1');
  log('ACTION', 'connect headphones, select LHDC, and play for 15-20 seconds');
  log('EXPECTED', 'audio_stream_open/start should report session_arg=1');
  log('LIMIT', 'route experiment only; no encoder, OTA .so, or system file is changed');
}

function fail(e) {
  log('ABORT', String(e));
}

try {
  const current = Process.findModuleByName(MODULE_NAME);
  if (current !== null) {
    install(current);
  } else {
    log('WAIT', 'waiting for ' + MODULE_NAME);
    Process.attachModuleObserver({
      onAdded(module) {
        if (module.name !== MODULE_NAME) return;
        try { install(module); } catch (e) { fail(e); }
      }
    });
  }
} catch (e) {
  fail(e);
}
