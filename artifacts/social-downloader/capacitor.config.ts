import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.dropdesk.app',
  appName: 'Dropdesk',
  webDir: 'dist/public',
  server: {
    // Load dari bundled assets (offline-ready)
    // API calls akan ke server yang dijalankan di komputer/VPS
    cleartext: true,
    allowNavigation: ['*'],
  },
  android: {
    allowMixedContent: true,
  },
  plugins: {
    SplashScreen: {
      launchShowDuration: 1500,
      backgroundColor: '#0f172a',
      showSpinner: true,
      spinnerColor: '#f59e0b',
    },
  },
};

export default config;
