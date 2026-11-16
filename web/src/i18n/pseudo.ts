const ACCENTS: Readonly<Record<string, string>> = {
  a: 'á',
  b: 'ƀ',
  c: 'ç',
  d: 'ð',
  e: 'é',
  f: 'ƒ',
  g: 'ĝ',
  h: 'ĥ',
  i: 'í',
  j: 'ĵ',
  k: 'ķ',
  l: 'ļ',
  m: 'ɱ',
  n: 'ñ',
  o: 'ó',
  p: 'þ',
  q: ' q',
  r: 'ŕ',
  s: 'š',
  t: 'ţ',
  u: 'ú',
  v: 'ṽ',
  w: 'ŵ',
  x: 'ẋ',
  y: 'ý',
  z: 'ž',
  A: 'Á',
  B: 'Ɓ',
  C: 'Ç',
  D: 'Ð',
  E: 'É',
  F: 'Ƒ',
  G: 'Ĝ',
  H: 'Ĥ',
  I: 'Í',
  J: 'Ĵ',
  K: 'Ķ',
  L: 'Ļ',
  M: 'Ṁ',
  N: 'Ñ',
  O: 'Ó',
  P: 'Þ',
  Q: 'Q',
  R: 'Ŕ',
  S: 'Š',
  T: 'Ţ',
  U: 'Ú',
  V: 'Ṽ',
  W: 'Ŵ',
  X: 'Ẋ',
  Y: 'Ý',
  Z: 'Ž',
};

function accent(text: string): string {
  return [...text].map((character) => ACCENTS[character] ?? character).join('');
}

/**
 * Expands a message into pseudo-localized text so UI tests can detect hard-coded strings and layout
 * overflow. Balanced ICU expressions such as {count, plural, ...} are preserved verbatim, including
 * nested braces.
 */
export function pseudoLocalize(message: string): string {
  const parts: string[] = [];
  let depth = 0;
  let textStart = 0;
  let placeholderStart = 0;

  for (let index = 0; index < message.length; index += 1) {
    const character = message[index];
    if (character === '{') {
      if (depth === 0) {
        if (index > textStart) {
          parts.push(accent(message.slice(textStart, index)));
        }
        placeholderStart = index;
      }
      depth += 1;
    } else if (character === '}' && depth > 0) {
      depth -= 1;
      if (depth === 0) {
        parts.push(message.slice(placeholderStart, index + 1));
        textStart = index + 1;
      }
    }
  }

  if (textStart < message.length) {
    parts.push(accent(message.slice(textStart)));
  }

  return `[${parts.join('')}···]`;
}
