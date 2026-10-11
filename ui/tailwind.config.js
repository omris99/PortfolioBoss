/**
 * The design system made in Claude Design ("PortfolioBoss", its tokens.json): the colors by role, the type scale, the
 * radii and the shadows. The color values themselves are CSS variables in src/index.css.
 *
 * Only the roles exist as colors — a raw Tailwind color (slate-500, emerald-400…) is not defined here, so it can't creep
 * back in. The token "text" is `text-ink` (`text-text` would read badly), "text-muted" `text-muted`, "text-faint"
 * `text-faint`; the token "border" is the plain `border` class's color, "border-strong" `border-strong`, and both are
 * `bg-line` / `bg-line-strong` as a fill. Spacing needs nothing: the design's 4px steps are Tailwind's own (space-5 is p-5).
 *
 * @type {import('tailwindcss').Config}
 */
const role = (name) => `var(--color-${name})`;

export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    colors: {
      transparent: 'transparent',
      current: 'currentColor',
      // The text color as a color of its own too, for an SVG's fill or stroke (the minor warning's "!").
      ink: role('text'),
      page: role('page'),
      surface: { DEFAULT: role('surface'), raised: role('surface-raised') },
      scrim: { DEFAULT: role('scrim'), modal: role('scrim-modal') },
      subtle: role('subtle'),
      line: { DEFAULT: role('border'), strong: role('border-strong') },
      accent: { DEFAULT: role('accent'), soft: role('accent-soft') },
      'on-accent': role('on-accent'),
      selected: role('selected'),
      link: role('link'),
      positive: { DEFAULT: role('positive'), soft: role('positive-soft') },
      negative: { DEFAULT: role('negative'), soft: role('negative-soft'), line: role('negative-line') },
      'on-negative': role('on-negative'),
      inbetween: { DEFAULT: role('inbetween'), soft: role('inbetween-soft') },
      unknown: { DEFAULT: role('unknown'), soft: role('unknown-soft') },
      'warn-minor': { DEFAULT: role('warn-minor'), edge: role('warn-minor-edge'), soft: role('warn-minor-soft') },
      'warn-real': { DEFAULT: role('warn-real'), soft: role('warn-real-soft') },
      'on-warn-real': role('on-warn-real'),
      signal: { green: role('signal-green'), yellow: role('signal-yellow'), red: role('signal-red'), none: role('signal-none') },
      status: { loading: role('status-loading'), ready: role('status-ready'), error: role('status-error') },
    },
    textColor: ({ theme }) => ({
      ...theme('colors'),
      ink: role('text'),
      muted: role('text-muted'),
      faint: role('text-faint'),
    }),
    borderColor: ({ theme }) => ({
      ...theme('colors'),
      DEFAULT: role('border'),
      strong: role('border-strong'),
    }),
    borderRadius: {
      none: '0',
      sm: '4px',
      DEFAULT: '4px',
      md: '6px',
      lg: '8px',
      full: '9999px',
    },
    fontFamily: {
      sans: ['"IBM Plex Sans"', 'ui-sans-serif', 'system-ui', '"Segoe UI"', 'sans-serif'],
      mono: ['ui-monospace', '"SF Mono"', 'Menlo', 'Consolas', 'monospace'],
    },
    extend: {
      fontSize: {
        'page-title': ['20px', { lineHeight: '28px', letterSpacing: '-0.005em', fontWeight: '600' }],
        'figure-lg': ['26px', { lineHeight: '32px', letterSpacing: '-0.01em', fontWeight: '500' }],
        figure: ['18px', { lineHeight: '24px', fontWeight: '500' }],
        'section-title': ['15px', { lineHeight: '20px', fontWeight: '600' }],
        body: ['14px', { lineHeight: '20px' }],
        label: ['12px', { lineHeight: '16px', letterSpacing: '0.01em', fontWeight: '500' }],
        caption: ['12px', { lineHeight: '16px' }],
        wordmark: ['16px', { lineHeight: '20px', letterSpacing: '-0.01em', fontWeight: '600' }],
        code: ['13px', { lineHeight: '20px' }],
      },
      boxShadow: {
        raised: '0 2px 4px #1d20260d, 0 12px 32px -4px #1d202629',
      },
      maxWidth: {
        content: '1200px',
      },
      // The top bar's height, as a spacing step: `h-topbar` for the bar, `top-topbar` for the drawer that starts under it.
      spacing: {
        topbar: '56px',
      },
    },
  },
  plugins: [],
};
