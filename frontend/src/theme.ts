import type { ThemeConfig } from 'antd';

/**
 * 设计令牌：全站唯一色彩/字号/间距/阴影/圆角来源。
 * 页面与组件只允许消费这里的 token（或 index.css 中对应的 CSS 变量），不散落硬编码颜色。
 */
export const colors = {
  // 品牌主色（银行科技蓝）
  brand: '#2B5AED',
  brandHover: '#4A73F1',
  brandActive: '#1E44C9',
  brandSoft: 'rgba(43, 90, 237, 0.08)',
  brandBorder: 'rgba(43, 90, 237, 0.35)',
  // 语义色
  success: '#1FA97A',
  successSoft: 'rgba(31, 169, 122, 0.1)',
  warning: '#E58E0B',
  warningSoft: 'rgba(229, 142, 11, 0.1)',
  error: '#E5484D',
  errorSoft: 'rgba(229, 72, 77, 0.08)',
  // 辅助强调色
  purple: '#7C5CFC',
  purpleSoft: 'rgba(124, 92, 252, 0.1)',
  teal: '#12A5A0',
  tealSoft: 'rgba(18, 165, 160, 0.1)',
  orange: '#EE8112',
  orangeSoft: 'rgba(238, 129, 18, 0.1)',
  // 中性灰阶
  textPrimary: '#1B2130',
  textSecondary: '#59617A',
  textTertiary: '#98A0B4',
  borderLight: '#EBEEF5',
  borderStrong: '#D8DEEB',
  bgLayout: '#F4F6FB',
  bgHover: 'rgba(23, 43, 99, 0.04)',
  bgCard: '#FFFFFF',
  bgCode: '#F6F8FB',
  // 侧边栏深蓝灰
  siderFrom: '#0C1634',
  siderTo: '#1A2450',
  siderText: 'rgba(255, 255, 255, 0.62)',
  siderTextHover: '#FFFFFF',
};

export const fontSize = {
  xs: 12,
  sm: 13,
  base: 14,
  lg: 16,
  xl: 20,
  xxl: 28,
};

export const radius = {
  sm: 6,
  base: 8,
  lg: 10,
  xl: 14,
};

export const shadow = {
  card: '0 1px 2px rgba(23, 43, 99, 0.04), 0 4px 14px rgba(23, 43, 99, 0.05)',
  cardHover: '0 8px 24px rgba(23, 43, 99, 0.12)',
  pop: '0 10px 32px rgba(23, 43, 99, 0.16)',
};

/** 间距节奏：4 的倍数 */
export const space = (n: number): number => n * 4;

export const SIDER_GRADIENT = `linear-gradient(180deg, ${colors.siderFrom} 0%, ${colors.siderTo} 100%)`;

export const appTheme: ThemeConfig = {
  token: {
    colorPrimary: colors.brand,
    colorLink: colors.brand,
    colorInfo: colors.brand,
    colorSuccess: colors.success,
    colorWarning: colors.warning,
    colorError: colors.error,
    borderRadius: radius.base,
    colorBgLayout: colors.bgLayout,
    colorTextBase: colors.textPrimary,
    colorTextSecondary: colors.textSecondary,
    colorBorderSecondary: colors.borderLight,
    fontSize: fontSize.base,
    boxShadowTertiary: shadow.card,
  },
  components: {
    Card: {
      borderRadiusLG: radius.lg,
      boxShadowTertiary: shadow.card,
      paddingLG: 20,
    },
    Menu: {
      darkItemBg: 'transparent',
      darkSubMenuItemBg: 'transparent',
      darkPopupBg: colors.siderTo,
      darkItemColor: colors.siderText,
      darkItemHoverColor: colors.siderTextHover,
      darkItemSelectedColor: '#FFFFFF',
      darkItemSelectedBg: colors.brand,
      itemBorderRadius: radius.base,
      itemMarginInline: 10,
      itemHeight: 40,
    },
    Layout: {
      siderBg: colors.siderFrom,
      headerBg: 'rgba(255, 255, 255, 0.72)',
      headerHeight: 56,
      headerPadding: '0 24px',
    },
    Table: {
      headerBg: '#F7F9FD',
      headerSplitColor: 'transparent',
      rowHoverBg: 'rgba(43, 90, 237, 0.04)',
      cellPaddingBlock: 12,
    },
    Button: {
      borderRadius: radius.base,
      controlHeight: 34,
      primaryShadow: '0 4px 10px rgba(43, 90, 237, 0.25)',
    },
    Input: {
      borderRadius: radius.base,
      activeShadow: '0 0 0 3px rgba(43, 90, 237, 0.12)',
    },
    Select: {
      borderRadius: radius.base,
    },
    Tabs: {
      itemSelectedColor: colors.brand,
      inkBarColor: colors.brand,
    },
    Tag: {
      borderRadiusSM: radius.sm,
    },
    Modal: {
      borderRadiusLG: radius.xl,
    },
  },
};
