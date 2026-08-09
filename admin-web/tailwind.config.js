/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // navy/blue government branding, from docs/Logo.png
        navy: {
          50: '#eef2f9',
          100: '#d6e0f0',
          200: '#adc1e1',
          300: '#7f9ccd',
          400: '#5077b6',
          500: '#2f5794',
          600: '#1f4076',
          700: '#163059',
          800: '#0f2340',
          900: '#0a1729',
        },
      },
    },
  },
  plugins: [],
};
