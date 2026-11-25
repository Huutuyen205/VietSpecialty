/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/main/resources/templates/**/*.html",
    "./src/main/resources/static/js/**/*.js"
  ],
  theme: {
    extend: {
      colors: {
        cream: '#FAF6EE',
        surface: {
          DEFAULT: '#FFFFFF',
          muted: '#F3EDE0',
        },
        line: '#E3DAC9',
        ink: {
          DEFAULT: '#1E2420',
          muted: '#57605A',
        },
        primary: {
          50: '#EBF5EE',
          100: '#D2EAD9',
          200: '#A9D9B8',
          500: '#278450',
          600: '#1F6E43',
          700: '#175734',
          800: '#114227',
        },
        secondary: {
          50: '#FEF9EC',
          100: '#FEF6E4',
          200: '#FCE8B6',
          400: '#E5A52E',
          500: '#D9911A',
          600: '#A86708',
          700: '#6E4306',
        },
        accent: {
          50: '#FDF0EB',
          100: '#FAE0D6',
          500: '#B94922',
          600: '#9A3B19',
        },
        danger: '#B91C1C',
        region: {
          bac: '#0D7A6B',
          trung: '#A86708',
          taynguyen: '#7B5033',
          nam: '#B8472A',
        }
      },
      fontFamily: {
        sans: ['"Be Vietnam Pro"', 'system-ui', 'sans-serif'],
        serif: ['Lora', 'Georgia', 'serif'],
      },
      boxShadow: {
        'soft': '0 1px 3px rgba(30,36,32,.05), 0 8px 24px rgba(30,36,32,.05)',
        'lift': '0 14px 34px rgba(30,36,32,.10), 0 2px 6px rgba(30,36,32,.04)',
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

