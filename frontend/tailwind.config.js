/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  // 启用暗黑模式，使用 class 策略支持手动切换
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        // 中国传统色系 (Source)
        'danqing': '#88ada6',      // 黛青
        'qianhong': '#f0a1a8',     // 浅红
        'yuebai': '#d6ecf0',       // 月白
        'shuimo': '#50616d',       // 水墨
        'qingbai': '#c0ebd7',      // 青白
        'yanzhi': '#c45a65',       // 胭脂
        'zhizi': '#eacd76',        // 栀子
        'qingsong': '#5dbe8a',     // 青松
        'tianlv': '#20a162',       // 田绿
        'zijinghui': '#815c94',    // 紫荆灰
        'qianniuzi': '#681752',    // 牵牛紫
        'halanzi': '#1781b5',      // 花蓝紫
        'yanzhihong': '#c04851',   // 胭脂红
        'danya': '#789262',        // 淡雅绿
        'qinghua': '#2e59a7',      // 青花蓝
        'tanxiang': '#b78d71',     // 檀香棕

        // 语义化映射 (Semantic)
        primary: '#2e59a7',        // Default: 青花蓝 (原黛青改为青花蓝，提升可读性和专业感)
        secondary: '#20a162',      // Default: 田绿 (用于辅助操作或通过状态)
        success: '#5dbe8a',        // Default: 青松
        warning: '#eacd76',        // Default: 栀子
        danger: '#c45a65',         // Default: 胭脂
        info: '#1781b5',           // Default: 花蓝紫
        text: {
          main: '#50616d',         // Default: 水墨
          muted: '#94a3b8',        // Default: Slate-400 (标准弱化灰，原为黛青)
        },
      },
      fontFamily: {
        'song': ['"Noto Serif SC"', 'serif'],
        'hei': ['"Noto Sans SC"', 'sans-serif'],
      },
      borderRadius: {
        'xl': '1rem',
        '2xl': '1.5rem',
        '3xl': '2rem',
      },
      animation: {
        'fade-in': 'fadeIn 0.5s ease-out',
        'slide-up': 'slideUp 0.4s ease-out',
        'slide-down': 'slideDown 0.3s ease-out',
        'scale-in': 'scaleIn 0.3s ease-out',
        'float': 'float 3s ease-in-out infinite',
        'shimmer': 'shimmer 1.5s infinite',
      },
      keyframes: {
        fadeIn: {
          '0%': { opacity: '0' },
          '100%': { opacity: '1' },
        },
        slideUp: {
          '0%': { opacity: '0', transform: 'translateY(20px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        slideDown: {
          '0%': { opacity: '0', transform: 'translateY(-10px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        scaleIn: {
          '0%': { opacity: '0', transform: 'scale(0.95)' },
          '100%': { opacity: '1', transform: 'scale(1)' },
        },
        float: {
          '0%, 100%': { transform: 'translateY(0)' },
          '50%': { transform: 'translateY(-10px)' },
        },
      },
    },
  },
  plugins: [],
}
