const productionConfig = {
  VUE_APP_TIME_OUT: 180000,
  VUE_APP_GOOGLE_CLIENT_ID: '147123631762-p2149desosmqr59un7mbjm2p65k566gh.apps.googleusercontent.com',
  VUE_APP_EXPORT_MAX_FILE_SIZE: 50000000,
  VUE_APP_PROFILER_ENABLED: true,
  VUE_APP_CAAS_API_URL: 'http://localhost:8580',
  VUE_APP_BI_API_URL: 'http://localhost:8080',
  VUE_APP_SCHEMA_API_URL: 'http://localhost:8489',
  VUE_APP_DATA_COOK_API_URL: 'http://localhost:8489',
  VUE_APP_LAKE_API_URL: 'http://localhost:8489',
  VUE_APP_CDP_API_URL: 'http://localhost:8080',
  VUE_APP_STATIC_API_URL: 'http://localhost:8080/static',
  VUE_APP_BILLING_API_URL: 'http://localhost:8489',
  VUE_APP_WORKER_API_URL: 'http://localhost:8080',
  VUE_APP_SCHEDULER_API_URL: 'http://localhost:8080',
  VUE_APP_RELAY_API_URL: 'http://localhost:8080'
};

window.appConfig = productionConfig;