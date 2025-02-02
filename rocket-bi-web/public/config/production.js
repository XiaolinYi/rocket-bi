const productionConfig = {
  VUE_APP_TIME_OUT: 180000,
  VUE_APP_GOOGLE_CLIENT_ID: '147123631762-p2149desosmqr59un7mbjm2p65k566gh.apps.googleusercontent.com',
  VUE_APP_EXPORT_MAX_FILE_SIZE: 50000000,
  VUE_APP_PROFILER_ENABLED: true,
  VUE_APP_CAAS_API_URL: '/api',
  VUE_APP_BI_API_URL: '/api',
  VUE_APP_SCHEMA_API_URL: '/api',
  VUE_APP_DATA_COOK_API_URL: '/api',
  VUE_APP_LAKE_API_URL: '/api',
  VUE_APP_CDP_API_URL: '/api',
  VUE_APP_STATIC_API_URL: '/api/static',
  VUE_APP_BILLING_API_URL: 'https://license.datainsider.co/api',
  VUE_APP_WORKER_API_URL: '/api',
  VUE_APP_SCHEDULER_API_URL: '/api',
  VUE_APP_RELAY_API_URL: '/api',
  VUE_APP_STATIC_FILE_URL: '/static',
  VUE_APP_FACEBOOK_APP_ID: '1371850590286877',
  VUE_APP_FACEBOOK_APP_SECRET: '27c4375073eeabebba910688aba028de',
  VUE_APP_FACEBOOK_SCOPE: 'ads_management,ads_read',
  VUE_APP_TIKTOK_REDIRECT_URL: 'https://rocketbi.cf/third-party-auth/tik-tok',
  VUE_APP_TIKTOK_ID: '7174346320419766274',

  VUE_APP_IS_DISABLE_LAKE_HOUSE: true,
  VUE_APP_IS_DISABLE_STREAMING: true,
  VUE_APP_IS_DISABLE_CDP: true,
  VUE_APP_IS_DISABLE_INGESTION: false,
  VUE_APP_IS_DISABLE_USER_ACTIVITIES: false,
  VUE_APP_IS_DISABLE_CLICKHOUSE_CONFIG: false,
  VUE_APP_IS_DISABLE_BILLING: true,
  VUE_APP_LOGIN_SAMPLE: {
    isShowHint: true,
    hintMessage: 'Default account: hello@gmail.com / 123456'
  },
  VUE_APP_DEFAULT_PASSWORD: 'di@123456',
  VUE_APP_VERSION: 'v1.4.19'
};

window.appConfig = productionConfig;
