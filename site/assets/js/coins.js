// The hero scene: three currencies, three stacks. Coins arc in from one spot
// and each lands in its own stack, never another's, which is the app's one
// hard rule drawn without words. In the last chapter a single arc links two
// stacks: a transfer, the one place two currencies touch.
//
// Scroll sets a target progress; the scene eases toward it on
// requestAnimationFrame and stops rendering once settled.

import * as THREE from 'three';

const STACKS = [
  { code: 'EUR', symbol: '€', light: 0x2D9D75, dark: 0x35B98A },
  { code: 'INR', symbol: '₹', light: 0xBB7F0F, dark: 0xE39A12 },
  { code: 'USD', symbol: '$', light: 0x2C8CF6, dark: 0x4C9DF7 },
];
const PER_STACK = 8;
const COIN_H = 0.17;

function faceTexture(symbol, hex) {
  const size = 256, c = document.createElement('canvas');
  c.width = c.height = size;
  const g = c.getContext('2d');
  const base = '#' + hex.toString(16).padStart(6, '0');
  g.fillStyle = base; g.fillRect(0, 0, size, size);
  g.strokeStyle = 'rgba(255,255,255,0.55)'; g.lineWidth = 10;
  g.beginPath(); g.arc(size / 2, size / 2, size * 0.40, 0, Math.PI * 2); g.stroke();
  g.fillStyle = 'rgba(255,255,255,0.95)';
  g.font = `600 ${size * 0.46}px Geist, system-ui, sans-serif`;
  g.textAlign = 'center'; g.textBaseline = 'middle';
  g.fillText(symbol, size / 2, size / 2 + size * 0.02);
  const t = new THREE.CanvasTexture(c);
  t.colorSpace = THREE.SRGBColorSpace;
  t.anisotropy = 4;
  // A cylinder's cap maps its texture a quarter-turn off; turn it back.
  t.center.set(0.5, 0.5);
  t.rotation = Math.PI / 2;
  return t;
}

const easeOutBack = t => { const c = 1.5; return 1 + (c + 1) * (t - 1) ** 3 + c * (t - 1) ** 2; };
const smooth = t => t * t * (3 - 2 * t);

export function createCoins(canvas, { still = false } = {}) {
  let renderer;
  try {
    renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true, powerPreference: 'low-power' });
  } catch { return null; }
  if (!renderer.getContext()) return null;

  const dark = matchMedia('(prefers-color-scheme: dark)').matches;
  renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = dark ? 1.05 : 1.15;

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(32, 1, 0.1, 100);

  scene.add(new THREE.HemisphereLight(0xffffff, dark ? 0x241F33 : 0xE9E4F5, dark ? 0.9 : 1.2));
  const key = new THREE.DirectionalLight(0xffffff, 2.2);
  key.position.set(4, 9, 7);
  scene.add(key);
  const rim = new THREE.DirectionalLight(0x9C8DFF, 0.9);
  rim.position.set(-6, 3, -5);
  scene.add(rim);

  const world = new THREE.Group();
  scene.add(world);

  const coinGeo = new THREE.CylinderGeometry(0.62, 0.62, COIN_H * 0.9, 56);
  const tubeGeo = new THREE.CylinderGeometry(0.8, 0.8, 3.1, 64, 1, true);
  const ringGeo = new THREE.TorusGeometry(0.8, 0.018, 12, 96);
  const baseGeo = new THREE.CylinderGeometry(0.98, 1.02, 0.1, 64);

  const spacing = 2.05;
  const coins = [];
  STACKS.forEach((s, i) => {
    const hue = dark ? s.dark : s.light;
    const x = (i - 1) * spacing;
    const group = new THREE.Group();
    group.position.x = x;
    world.add(group);

    const base = new THREE.Mesh(baseGeo, new THREE.MeshStandardMaterial({ color: dark ? 0x2B2540 : 0xF2EFE7, roughness: 0.8 }));
    base.position.y = -0.05;
    group.add(base);

    // The stack's walls are a whisper of glass; its colour is in the rings,
    // lines and glows, as everywhere in the identity.
    const tube = new THREE.Mesh(tubeGeo, new THREE.MeshStandardMaterial({
      color: hue, transparent: true, opacity: dark ? 0.05 : 0.035, side: THREE.DoubleSide, depthWrite: false, roughness: 0.2,
    }));
    tube.position.y = 1.55;
    group.add(tube);
    for (const y of [0.02, 3.1]) {
      const ring = new THREE.Mesh(ringGeo, new THREE.MeshBasicMaterial({ color: hue, transparent: true, opacity: 0.85 }));
      ring.rotation.x = Math.PI / 2;
      ring.position.y = y;
      group.add(ring);
    }

    const side = new THREE.MeshStandardMaterial({ color: hue, metalness: 0.55, roughness: 0.32 });
    const face = new THREE.MeshStandardMaterial({ map: faceTexture(s.symbol, hue), metalness: 0.35, roughness: 0.38 });
    for (let k = 0; k < PER_STACK; k++) {
      const m = new THREE.Mesh(coinGeo, [side, face, face]);
      // Interleaved, so the three currencies take turns: each coin still
      // finds its own stack however the drops are shuffled together.
      const order = k * STACKS.length + i;
      // The first two rows are already in when the page opens, so the first
      // screen shows the idea before anyone scrolls. None is caught mid-air
      // there: a still of a euro over the rupee stack would say the opposite.
      const pre = STACKS.length * 2;
      const t0 = order < pre ? -1 : 0.02 + ((order - pre) / (PER_STACK * STACKS.length - pre)) * 0.56;
      coins.push({ mesh: m, stack: i, x, k, t0, spin: (order % 5) - 2 });
      scene.add(m);
    }
  });

  // The transfer: one arc from EUR to INR, drawn in the accent, growing in
  // the last chapter. A rate recorded is a fact, so it is drawn as a line.
  const arcCurve = new THREE.QuadraticBezierCurve3(
    new THREE.Vector3(-spacing, 3.3, 0), new THREE.Vector3(-spacing / 2, 4.6, 0), new THREE.Vector3(0, 3.3, 0),
  );
  const arcGeo = new THREE.TubeGeometry(arcCurve, 80, 0.03, 8, false);
  const arc = new THREE.Mesh(arcGeo, new THREE.MeshBasicMaterial({ color: dark ? 0x9C8DFF : 0x6B5BFF }));
  arc.geometry.setDrawRange(0, 0);
  scene.add(arc);
  const arcIndexCount = arcGeo.index.count;
  const spark = new THREE.Mesh(new THREE.SphereGeometry(0.09, 20, 20), new THREE.MeshBasicMaterial({ color: dark ? 0x9C8DFF : 0x6B5BFF }));
  spark.visible = false;
  scene.add(spark);

  // The canvas is laid out by CSS (beside the copy on a landscape screen,
  // under it on a portrait one), so the camera frames the scene to whatever
  // box it is given: far enough back that the three stacks and the transfer
  // arc fit both across and up, at any size or aspect.
  const SCENE_HALF_W = spacing + 1.45;   // outer stack edge, plus room for the camera's swing
  const SCENE_HALF_H = 2.55;             // base to the top of the arc, halved
  const LOOK_Y = 2.2;                    // the middle of that height
  let fitDistance = 13;
  function resize() {
    const w = canvas.clientWidth, h = canvas.clientHeight;
    if (!w || !h) return;
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
    const tanV = Math.tan(THREE.MathUtils.degToRad(camera.fov / 2));
    fitDistance = Math.max(SCENE_HALF_H / tanV, SCENE_HALF_W / (tanV * camera.aspect)) * 1.12 + 1.2;
    dirty = true; wake();
  }

  let target = 0, current = 0, raf = 0, last = 0, dirty = true;
  const spawn = new THREE.Vector3(0, 6.2, 0);

  function place(p) {
    const wx = 0, wy = 0;
    for (const c of coins) {
      const t = THREE.MathUtils.clamp((p - c.t0) / 0.1, 0, 1);
      const m = c.mesh;
      m.visible = t > 0;
      if (!m.visible) continue;
      const land = COIN_H / 2 + c.k * COIN_H;
      const e = smooth(t);
      // A short arc from the shared spawn point into its own stack.
      m.position.set(
        wx + THREE.MathUtils.lerp(spawn.x, c.x, e),
        wy + THREE.MathUtils.lerp(spawn.y, land, easeOutBack(t)) + Math.sin(e * Math.PI) * 0.6,
        Math.sin(e * Math.PI) * 0.4,
      );
      m.rotation.set((1 - t) * Math.PI * 2.5, 0, (1 - t) * c.spin * 0.5);
    }
    const a = THREE.MathUtils.clamp((p - 0.7) / 0.22, 0, 1);
    arc.geometry.setDrawRange(0, Math.floor(arcIndexCount * a / 3) * 3);
    arc.position.set(wx, wy, 0);
    spark.visible = a > 0 && a < 1;
    if (spark.visible) spark.position.copy(arcCurve.getPoint(a)).add(arc.position);

    // The camera drifts round and a little up as the stacks fill, always at
    // the distance that fits the box it is drawing into.
    const angle = THREE.MathUtils.lerp(-0.34, 0.06, smooth(p));
    const lift = THREE.MathUtils.lerp(0.16, 0.26, p);
    const radius = fitDistance * (1 + p * 0.04);
    const flat = Math.cos(lift) * radius;
    camera.position.set(Math.sin(angle) * flat, LOOK_Y + Math.sin(lift) * radius, Math.cos(angle) * flat);
    camera.lookAt(0, LOOK_Y, 0);
  }

  function tick(now) {
    raf = 0;
    if (document.hidden) { last = 0; return; }
    const dt = last ? Math.min(now - last, 50) : 16.7;
    last = now;
    current += (target - current) * (still ? 1 : 1 - Math.exp(-dt / 120));
    if (Math.abs(target - current) < 0.0005) current = target;
    place(current);
    renderer.render(scene, camera);
    dirty = false;
    if (current !== target) raf = requestAnimationFrame(tick); else last = 0;
  }
  function wake() { if (!raf) raf = requestAnimationFrame(tick); }
  document.addEventListener('visibilitychange', wake);

  resize();
  // The layout moves the canvas (rotation, a fold opening, a window resize),
  // so it re-fits whenever its own box changes, not only on window resize.
  if ('ResizeObserver' in window) new ResizeObserver(resize).observe(canvas);
  return {
    setProgress(p) { target = still ? 1 : p; if (target !== current || dirty) wake(); },
    resize,
    dispose() { cancelAnimationFrame(raf); renderer.dispose(); },
  };
}
