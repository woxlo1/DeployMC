/**
 * Catalog keys used by the application shell.
 *
 * Keeping the keys in one module lets the i18n checker see every message key without scattering
 * string literals through layout code.
 */
export const SHELL_KEYS = {
  appTitle: 'app.title',
  appTagline: 'app.tagline',
  appStatusPreAlpha: 'app.status.preAlpha',
  appLanguage: 'app.language',
  languageEn: 'language.en',
  languageJa: 'language.ja',
  appPluginsCount: 'app.plugins.count',

  navIdentity: 'shell.nav.identity',
  navDashboard: 'shell.nav.dashboard',
  navFiles: 'shell.nav.files',
  navDeployments: 'shell.nav.deployments',
  navConsole: 'shell.nav.console',
  navSnapshots: 'shell.nav.snapshots',
  navAudit: 'shell.nav.audit',
  navTokens: 'shell.nav.tokens',
  navSettings: 'shell.nav.settings',
  navDoctor: 'shell.nav.doctor',
  navAbout: 'shell.nav.about',

  navLabel: 'shell.nav.label',
  headerLabel: 'shell.header.label',
  contentLabel: 'shell.content.label',
  statusLabel: 'shell.status.label',
  statusReady: 'shell.status.ready',
  statusRoute: 'shell.status.route',
  skip: 'shell.skip',

  theme: 'shell.theme',
  themeLight: 'shell.theme.light',
  themeDark: 'shell.theme.dark',
  themeSystem: 'shell.theme.system',

  shortcuts: 'shell.shortcuts',
  shortcutsTitle: 'shell.shortcuts.title',
  shortcutsDescription: 'shell.shortcuts.description',
  shortcutsAction: 'shell.shortcuts.action',
  shortcutsKeys: 'shell.shortcuts.keys',
  shortcutsClose: 'shell.shortcuts.close',
  shortcutsOpen: 'shell.shortcuts.open',

  notifications: 'shell.notifications',
  toastRegion: 'shell.toast.region',
  toastSample: 'shell.toast.sample',
  toastDismiss: 'shell.toast.dismiss',

  stateLoading: 'shell.state.loading',
  stateLoadingBody: 'shell.state.loading.body',
  stateEmpty: 'shell.state.empty',
  stateEmptyBody: 'shell.state.empty.body',
  stateError: 'shell.state.error',
  stateErrorBody: 'shell.state.error.body',
  stateRetry: 'shell.state.retry',
  requestId: 'shell.requestId',

  errorTitle: 'shell.error.title',
  errorBody: 'shell.error.body',
  errorDetails: 'shell.error.details',
  errorCode: 'shell.error.code',
  errorReload: 'shell.error.reload',

  routeInvalidTitle: 'shell.route.invalid.title',
  routeInvalidBody: 'shell.route.invalid.body',
  routeInvalidReturn: 'shell.route.invalid.return',
} as const;
