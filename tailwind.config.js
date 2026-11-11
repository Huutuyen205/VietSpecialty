/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/main/resources/templates/**/*.html",
    "./src/main/resources/static/js/**/*.js"
  ],
  theme: {
    extend: {
      colors: {
        cream: '#FFFCF5',
        surface: {
          DEFAULT: '#FFFFFF',
          muted: '#F7F3EA',
        },
        line: '#E8E2D6',
        ink: {
          DEFAULT: '#1F2A24',
          muted: '#5B6660',
        },
        primary: {
          50: '#EEF8F0',
          100: '#D3EEDB',
          500: '#2F9E55',
          600: '#23864A',
          700: '#1C6B3C',
        },
        secondary: {
          100: '#FDF1D6',
          400: '#F4B740',
          500: '#E9A21F',
        },
        accent: {
          50: '#FDEDE8',
          500: '#D9583B',
          600: '#B84528',
        },
        danger: '#DC2626',
        region: {
          bac: '#0F9D8A',
          trung: '#E9A21F',
          taynguyen: '#8B5E3C',
          nam: '#E8704A',
        }
      },
      fontFamily: {
        sans: ['"Be Vietnam Pro"', 'system-ui', 'sans-serif'],
        serif: ['Lora', 'Georgia', 'serif'],
      },
      boxShadow: {
        'soft': '0 1px 2px rgba(31,42,36,.06), 0 8px 24px rgba(31,42,36,.06)',
        'lift': '0 12px 32px rgba(31,42,36,.10)',
      },
      borderRadius: {
        'xl': '0.75rem',
        '2xl': '1rem',
      }
    },
  },
  plugins: [
    require('@tailwindcss/forms'),
    require('@tailwindcss/typography'),
  ],
}

