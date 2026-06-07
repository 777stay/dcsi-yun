describe('runtimeApi', () => {
  const originalBaseApi = process.env.VUE_APP_BASE_API

  afterEach(() => {
    jest.resetModules()
    process.env.VUE_APP_BASE_API = originalBaseApi
    window.history.replaceState({}, '', 'http://localhost/')
  })

  test('uses same-origin URLs when the production API base is root', () => {
    process.env.VUE_APP_BASE_API = '/'
    const { getBackendBaseUrl, getBackendHost, getWsBaseUrl } = require('@/utils/runtimeApi')

    expect(getBackendBaseUrl()).toBe('')
    expect(getBackendHost()).toBe('localhost')
    expect(getWsBaseUrl()).toBe('ws://localhost')
  })

  test('preserves an explicitly configured backend origin without the api suffix', () => {
    process.env.VUE_APP_BASE_API = 'https://api.example.com/api/'
    const { getBackendBaseUrl, getBackendHost } = require('@/utils/runtimeApi')

    expect(getBackendBaseUrl()).toBe('https://api.example.com')
    expect(getBackendHost()).toBe('api.example.com')
  })

  test('uses secure websockets when the page is served over https', () => {
    process.env.VUE_APP_BASE_API = '/'
    window.history.replaceState({}, '', 'https://yun.example.com/app')
    const { getWsBaseUrl } = require('@/utils/runtimeApi')

    expect(getWsBaseUrl()).toBe('wss://yun.example.com')
  })
})
