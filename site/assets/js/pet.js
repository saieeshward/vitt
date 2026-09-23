// Draws a companion from the app's own sprite rects onto a 24x20 canvas.
// c0..c5 are the currency hues the design authored them in; the app binds
// them to its palette the same way.

export const HUES = ['#35B98A', '#E39A12', '#4C9DF7', '#EE6E9C', '#6B5BFF', '#17A2A2'];

const colour = c => (c[0] === 'c' ? HUES[+c.slice(1)] : '#' + c);

/**
 * step: 0 standing, 1 or 2 for the two halves of a walk cycle. A walking
 * foot is a leg rect one pixel shorter, the diagonal pairs taking turns,
 * which is how the app's rig reads at this size too.
 */
export function drawPet(ctx, rects, { step = 0, upTo = rects.length } = {}) {
  ctx.clearRect(0, 0, 24, 20);
  // Feet, left to right; alternate ones lift together.
  const feet = step ? rects.filter(r => r[5] === 'l' && r[1] >= 14).map(r => r[0]).sort((a, b) => a - b) : [];
  for (let i = 0; i < Math.min(upTo, rects.length); i++) {
    const [x, y, w, h, c, part] = rects[i];
    let hh = h;
    if (step && part === 'l' && y >= 14 && feet.indexOf(x) % 2 === step - 1) hh = h - 1;
    ctx.fillStyle = colour(c);
    ctx.fillRect(x, y, w, hh);
  }
}

export function pickPet(pets) {
  // A different animal from last time where storage allows; random either way.
  let last = null;
  try { last = localStorage.getItem('vitt-pet'); } catch {}
  const pool = pets.filter(p => p.id !== last);
  const pet = pool[Math.floor(Math.random() * pool.length)] || pets[0];
  try { localStorage.setItem('vitt-pet', pet.id); } catch {}
  return pet;
}
