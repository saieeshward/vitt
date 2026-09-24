import { PETS } from './pets.js';
import { drawPet, pickPet } from './pet.js';

const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;
const $ = (s, el = document) => el.querySelector(s);
const $$ = (s, el = document) => [...el.querySelectorAll(s)];
const clamp = (v, a = 0, b = 1) => Math.min(b, Math.max(a, v));

let pet = pickPet(PETS);

/* ── Loader: today's pet drawn in, rect by rect ───────────────────────── */

// Full screen only on the first page of a visit, and never waiting on the 3D
// scene: a loader on a page like this is time taken from every reader, so it
// lasts as long as the drawing does and not a frame more. Any other load, the
// pet draws itself in at the foot of the page while the content is already up.
const loader = $('#loader');
let firstOfVisit = true;
try { firstOfVisit = !sessionStorage.getItem('vitt-seen'); sessionStorage.setItem('vitt-seen', '1'); } catch {}

function drawIn(ctx, rects, length) {
  return new Promise(resolve => {
    if (reduced) { drawPet(ctx, rects); return resolve(); }
    const start = performance.now();
    (function frame(now) {
      const t = clamp((now - start) / length);
      drawPet(ctx, rects, { upTo: Math.ceil(rects.length * t) });
      if (t < 1) requestAnimationFrame(frame); else resolve();
    })(start);
  });
}

let introduced = Promise.resolve();
if (firstOfVisit) {
  $('p', loader).innerHTML = `<b>${pet.name}</b> is walking with you today.`;
  const domReady = new Promise(r => (document.readyState === 'loading' ? addEventListener('DOMContentLoaded', r, { once: true }) : r()));
  introduced = Promise.all([drawIn($('canvas', loader).getContext('2d'), pet.poses.front, 560), domReady])
    .then(() => loader.classList.add('done'));
} else {
  loader.remove();
}

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
// On a phone there is no room above her that is not text, so the bubble sits
// beside her in the strip she walks along, on whichever side has room.
const phone = matchMedia('(max-width: 560px), (max-height: 520px)');
function placeBubble() {
  const w = bubble.offsetWidth, pw = walkerWidth();
  if (phone.matches) {
    bubble.classList.add('side');
    const right = x + pw + 6 + w <= innerWidth - 8;
    bubble.style.left = (right ? pw + 6 : -w - 6).toFixed(1) + 'px';
    return;
  }
  bubble.classList.remove('side');
  const centre = x + pw / 2;
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
if (firstOfVisit) render(performance.now());
else drawIn(walkerCtx, pet.poses.stand, 480).then(() => render(performance.now()));
introduced.then(() => say(`Hi. I'm ${pet.name}.`, 2600));

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
  .catch(() => {});

/* ── Scrollytelling: the step in the middle of the screen drives the phone ── */

const narrow = matchMedia('(max-width: 860px)');
const shots = $$('.phone-rail img');
const steps = $$('.step');
const stepObserver = new IntersectionObserver(entries => {
  for (const e of entries) {
    if (!e.isIntersecting) continue;
    const s = e.target;
    steps.forEach(x => x.classList.toggle('on', x === s));
    shots.forEach(img => img.classList.toggle('on', img.dataset.shot === s.dataset.shot));
    dots.forEach(d => d.classList.toggle('on', d.dataset.shot === s.dataset.shot));
    // Beside the phone on a wide screen she has room to talk; on a narrow
    // one each step is a screenshot under her, so she keeps quiet.
    if (!narrow.matches) say(s.dataset.say);
    setPose('content');
  }
}, { rootMargin: '-45% 0px -45% 0px' });
steps.forEach(s => stepObserver.observe(s));

// Where the reader is in the six: a dot per step beside the phone.
const rail = $('.phone-rail');
const dotsWrap = document.createElement('div');
dotsWrap.className = 'rail-dots';
dotsWrap.setAttribute('aria-hidden', 'true');
const dots = steps.map((s, i) => {
  const d = document.createElement('i');
  d.dataset.shot = s.dataset.shot;
  if (i === 0) d.className = 'on';
  dotsWrap.append(d);
  return d;
});
$('.phone', rail)?.append(dotsWrap);

// Out of the way of any screenshot in the middle of a narrow screen.
const onScreen = new Set();
const asideObserver = new IntersectionObserver(entries => {
  for (const e of entries) e.isIntersecting ? onScreen.add(e.target) : onScreen.delete(e.target);
  walker.classList.toggle('aside', onScreen.size > 0 && narrow.matches);
}, { rootMargin: '-25% 0px -10% 0px' });
$$('.inline-phone').forEach(el => asideObserver.observe(el));

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
