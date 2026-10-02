export const PLEASANTNESS_MIN = -3;
export const PLEASANTNESS_MAX = 3;

// Parole della scala di piacevolezza: mostrate sempre insieme al numero
// (es. "Joy (+3)") per evitare ambiguità tra emozioni simili.
export const PLEASANTNESS_LABELS: Record<number, string> = {
  3: 'Joy',
  2: 'Pleasure',
  1: 'Contentment',
  0: 'Neutral',
  [-1]: 'Discontent',
  [-2]: 'Displeasure',
  [-3]: 'Anguish',
};

export function formatPleasantness(value: number): string {
  const label = PLEASANTNESS_LABELS[value];
  const num = value > 0 ? `+${value}` : `${value}`;
  return label ? `${label} (${num})` : num;
}