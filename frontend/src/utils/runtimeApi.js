export function getHttpBaseUrl() {
  const configured = process.env.VUE_APP_BASE_API
  if (configured && configured !== '/') {
    return configured.replace(/\/$/, '')
  }
  return ''
}

export function getBackendHost() {
  const configured = getBackendBaseUrl()
  if (/^https?:\/\//.test(configured)) {
    return configured.replace(/^https?:\/\//, '')
  }
  return window.location.host
}

export function getBackendBaseUrl() {
  return getHttpBaseUrl().replace(/\/api$/, '')
}

export function getWsBaseUrl() {
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return `${protocol}://${window.location.host}`
}
