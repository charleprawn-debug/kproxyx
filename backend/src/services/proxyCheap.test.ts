jest.mock('../config', () => ({
  __esModule: true,
  default: {
    proxyCheap: {
      baseUrl: 'https://api.proxy-cheap.com/v2',
      apiKey: 'test-api-key',
      apiSecret: 'test-api-secret',
      timeoutMs: 1000,
    },
  },
}));

import { proxyCheapService } from './proxyCheap';

describe('Proxy-Cheap fulfillment', () => {
  afterEach(() => {
    jest.useRealTimers();
    jest.restoreAllMocks();
  });

  it('selects the connection port matching the provider proxy type', async () => {
    const response = [{
      id: 'proxy-1',
      status: 'ACTIVE',
      proxyType: 'HTTP',
      authentication: { username: 'user', password: 'password' },
      connection: {
        connectIp: 'proxy.example.test',
        httpPort: 8080,
        httpsPort: 8443,
        socks5Port: 1080,
      },
    }];
    jest.spyOn(global, 'fetch').mockResolvedValue({
      ok: true,
      text: async () => JSON.stringify(response),
    } as Response);

    await expect(proxyCheapService.proxies('order-1')).resolves.toEqual([
      expect.objectContaining({ protocol: 'http', port: 8080 }),
    ]);
  });

  it('waits for ACTIVE instead of returning a pending proxy', async () => {
    const pending = {
      id: 'proxy-1',
      status: 'PENDING',
      protocol: 'http',
      host: 'proxy.example.test',
      port: 8080,
      username: 'user',
      password: 'password',
    };
    const active = { ...pending, status: 'Active' };
    const fetchProxies = jest.spyOn(proxyCheapService, 'proxies')
      .mockResolvedValueOnce([pending])
      .mockResolvedValueOnce([active]);
    jest.useFakeTimers();

    const result = proxyCheapService.waitForProxy('order-1');
    await jest.advanceTimersByTimeAsync(2500);

    await expect(result).resolves.toEqual(active);
    expect(fetchProxies).toHaveBeenCalledTimes(2);
  });
});
