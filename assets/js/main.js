import { PETS } from './pets.js';
import { drawPet, pickPet } from './pet.js';

const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;
const $ = (s, el = document) => el.querySelector(s);
const $$ = (s, el = document) => [...el.querySelectorAll(s)];
const clamp = (v, a = 0, b = 1) => Math.min(b, Math.max(a, v));

let pet = pickPet(PETS);

/* ── Loader: today's pet drawn in, rect by rect ───────────────────────── */

const loader = $('#loader');
const loaderCtx = $('canvas', loader).getContext('2d');
$('p', loader).innerHTML = `<b>${pet.name}</b> is walking with you today.`;

const drawn = new Promise(resolve => {
  const rects = pet.poses.front;
  if (reduced) { drawPet(loaderCtx, rects); return resolve(); }
  const start = performance.now(), length = 620;
  (function frame(now) {
    const t = clamp((now - start) / length);
    drawPet(loaderCtx, rects, { upTo: Math.ceil(rects.length * t) });
    if (t < 1) requestAnimationFrame(frame); else resolve();
  })(start);
});

// Ready means the page and its 3D scene are, or 2.5s have passed: the pet is
// never a wall in front of content that is already there.
let sceneReady;
const scenePromise = new Promise(r => (sceneReady = r));
const ready = Promise.race([
  Promise.all([new Promise(r => (document.readyState === 'complete' ? r() : addEventListener('load', r, { once: true }))), scenePromise]),
  new Promise(r => setTimeout(r, 2500)),
]);
Promise.all([drawn, ready]).then(() => {
  loader.classList.add('done');
  say(`Hi. I'm ${pet.name}.`, 2600);
});

/* ── The walker ───────────────────────────────────────────────────────── */

const walker = $('#walker');
const walkerCanvas = $('canvas', walker);
const walkerCtx = walkerCanvas.getContext('2d');
const bubble = $('.bubble', walker);
let targetX = 0, x = 0, facing = 1, step = 0, lastStepAt = 0, lastTick = 0, raf = 0;
let pose = 'stand', hopUntil = 0, bubbleTimer = 0;

function walkerWidth() { return walker.offsetWidth || 96; }

function progress() {
  const max = document.documentElement.scrollHeight - innerHeight;
  return max > 0 ? clamp(scrollY / max) : 0;
}

function setTarget() {
  targetX = 8 + progress() * Math.max(0, innerWidth - walkerWidth() - 16);
  wake();
}

function render(now) {
  const moving = Math.abs(targetX - x) > 0.5;
  if (moving && now - lastStepAt > 110) { step = step === 1 ? 2 : 1; lastStepAt = now; }
  if (!moving) step = 0;
  const hop = now < hopUntil ? Math.sin(((hopUntil - now) / 500) * Math.PI) * 10 : 0;
  const bob = step ? -2 : 0;
  // The sprite faces left; walking right mirrors it.
  walker.style.transform = `translate3d(${x.toFixed(1)}px, ${(bob - hop).toFixed(1)}px, 0)`;
  walkerCanvas.style.transform = facing > 0 ? 'scaleX(-1)' : '';
  placeBubble();
  const rects = pet.poses[step ? 'walk' : pose] || pet.poses.stand;
  drawPet(walkerCtx, rects, { step });
}

function tick(now) {
  raf = 0;
  const dt = lastTick ? Math.min(now - lastTick, 50) : 16.7;
  lastTick = now;
  const before = x;
  // Time-based easing toward the scroll position: she walks, never teleports.
  x += (targetX - x) * (reduced ? 1 : 1 - Math.exp(-dt / 260));
  if (Math.abs(targetX - x) < 0.5) x = targetX;
  if (Math.abs(x - before) > 0.05) facing = x > before ? 1 : -1;
  render(now);
  if (x !== targetX || now < hopUntil) raf = requestAnimationFrame(tick);
  else { lastTick = 0; render(now); }
}
function wake() { if (!raf) raf = requestAnimationFrame(tick); }

// Centred over the pet, but never past a screen edge; the pointer keeps
// aiming at her wherever the bubble ends up.
function placeBubble() {
  const w = bubble.offsetWidth, centre = x + walkerWidth() / 2;
  const left = clamp(centre - w / 2, 8, Math.max(8, innerWidth - 8 - w)) - x;
  bubble.style.left = left.toFixed(1) + 'px';
  bubble.style.setProperty('--arrow', (centre - x - left).toFixed(1) + 'px');
}

function say(text, ms = 2400) {
  if (!text) return;
  bubble.textContent = text;
  placeBubble();
  bubble.classList.add('on');
  clearTimeout(bubbleTimer);
  bubbleTimer = setTimeout(() => bubble.classList.remove('on'), ms);
}

function setPose(p) { if (p !== pose) { pose = p; wake(); render(performance.now()); } }

walkerCanvas.addEventListener('click', () => {
  hopUntil = performance.now() + 500;
  const was = pose;
  setPose('glad');
  say(['Hello again.', 'Still here.', `${pet.name}, reporting.`][Math.floor(Math.random() * 3)], 1600);
  setTimeout(() => setPose(was), 1200);
  wake();
});

addEventListener('scroll', setTarget, { passive: true });
addEventListener('resize', setTarget);
setTarget();
x = targetX;
render(performance.now());

/* ── Hero: the scroll scrubs the coin scene and steps the chapters ───── */

const hero = $('#hero');
const chapters = $$('.chapter', hero);
const cue = $('.cue', hero);
const heroLines = [null, 'One stack each. They never mix.', 'That rate was real.'];
let chapter = 0, coins = null;

function heroProgress() {
  const r = hero.getBoundingClientRect();
  const span = r.height - innerHeight;
  return span > 0 ? clamp(-r.top / span) : 1;
}

function onHeroScroll() {
  const p = heroProgress();
  coins?.setProgress(p);
  if (cue) cue.style.opacity = p > 0.03 ? '0' : '';
  if (reduced) return;
  // Coarse thresholds: the copy changes when the chapter does, not every frame.
  const next = p < 0.3 ? 0 : p < 0.64 ? 1 : 2;
  if (next !== chapter) {
    chapters[chapter].classList.remove('on');
    chapters[next].classList.add('on');
    chapter = next;
    if (heroLines[next]) say(heroLines[next]);
    setPose(next === 2 ? 'expect' : 'stand');
  }
}
addEventListener('scroll', onHeroScroll, { passive: true });

// The 3D scene is ornament: a failure leaves the copy and a still behind.
import('./coins.js')
  .then(({ createCoins }) => {
    coins = createCoins($('#coins'), { still: reduced });
    if (coins) {
      document.documentElement.classList.add('has-3d');
      addEventListener('resize', () => { coins.resize(); onHeroScroll(); });
      coins.setProgress(reduced ? 1 : heroProgress());
    }
  })
  .catch(() => {})
  .finally(() => sceneReady());

/* ── Scrollytelling: the step in the middle of the screen drives the phone ── */

const shots = $$('.phone-rail img');
const steps = $$('.step');
const stepObserver = new IntersectionObserver(entries => {
  for (const e of entries) {
    if (!e.isIntersecting) continue;
    const s = e.target;
    steps.forEach(x => x.classList.toggle('on', x === s));
    shots.forEach(img => img.classList.toggle('on', img.dataset.shot === s.dataset.shot));
    say(s.dataset.say);
    setPose('content');
  }
}, { rootMargin: '-45% 0px -45% 0px' });
steps.forEach(s => stepObserver.observe(s));

/* ── Sections the pet has something to say about ─────────────────────── */

const sayObserver = new IntersectionObserver(entries => {
  for (const e of entries) {
    if (!e.isIntersecting) continue;
    say(e.target.dataset.say);
    setPose(e.target.dataset.pose || 'content');
  }
}, { rootMargin: '-40% 0px -40% 0px' });
$$('section[data-say]').forEach(s => sayObserver.observe(s));

/* ── The sheet: rows go in one at a time, the way the app appends them ── */

const rows = $$('#sheet tbody tr');
const sheetObserver = new IntersectionObserver(entries => {
  if (!entries.some(e => e.isIntersecting)) return;
  sheetObserver.disconnect();
  rows.forEach((row, i) => setTimeout(() => {
    row.classList.add('in');
    if (i === rows.length - 1) { row.classList.add('fresh'); setTimeout(() => row.classList.remove('fresh'), 1400); }
  }, reduced ? 0 : 160 * i));
}, { threshold: 0.35 });
if (rows.length) sheetObserver.observe($('#sheet .sheet'));

/* ── Reveals and the nav's hairline ───────────────────────────────────── */

const revealObserver = new IntersectionObserver(entries => {
  for (const e of entries) if (e.isIntersecting) { e.target.classList.add('in'); revealObserver.unobserve(e.target); }
}, { threshold: 0.15 });
$$('.reveal').forEach(el => revealObserver.observe(el));

const nav = $('#nav');
addEventListener('scroll', () => nav.classList.toggle('scrolled', scrollY > 8), { passive: true });

/* ── The six companions; one is today's, and any can take over ───────── */

const grid = $('#pets');
function paintGrid() {
  grid.innerHTML = '';
  for (const p of PETS) {
    const card = document.createElement('button');
    card.type = 'button';
    card.className = 'pet' + (p.id === pet.id ? ' today' : '');
    card.setAttribute('aria-pressed', String(p.id === pet.id));
    card.innerHTML = `<canvas width="24" height="20" aria-hidden="true"></canvas>
      <em>${p.id === pet.id ? 'With you today' : ''}</em>
      <b>${p.name}</b><span>A ${p.kind}. Carries your currencies as ${p.marker}.</span>`;
    const ctx = $('canvas', card).getContext('2d');
    drawPet(ctx, p.poses.stand);
    card.addEventListener('mouseenter', () => drawPet(ctx, p.poses.glad));
    card.addEventListener('mouseleave', () => drawPet(ctx, p.poses.stand));
    card.addEventListener('click', () => {
      pet = p;
      try { localStorage.setItem('vitt-pet', p.id); } catch {}
      paintGrid();
      hopUntil = performance.now() + 500;
      say(`${p.name} will take it from here.`);
      wake();
    });
    grid.append(card);
  }
}
paintGrid();
onHeroScroll();
