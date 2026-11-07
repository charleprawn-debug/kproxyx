import {
  buildProxyCheapExecutePayload,
  buildProxyCheapLocations,
  buildProxyCheapOrderPayload,
  normalizeProxyCheapPlanId,
  parseProxyCheapLocation,
  validateProxyCheapLocation,
} from './proxyCheapLocations';

describe('Proxy-Cheap provider location mapping', () => {
  const setup = {
    countries: [{ code: 'US', name: 'United States' }],
    regions: {
      US: [{ id: 'Virginia', code: 'VA', label: 'Virginia', isps: [{ id: 'isp-17', label: 'Provider 17' }] }],
    },
    cities: {
      US: [{ id: 'ashburn-va', name: 'Ashburn', region: 'Virginia', isps: [{ id: 'isp-17', label: 'Provider 17' }] }],
    },
    isps: {
      US: [{ id: 'isp-17', label: 'Provider 17' }],
    },
  };

  it('sends the provider region id while keeping the human-readable label for UI', () => {
    const locations = buildProxyCheapLocations(setup);
    const ashburn = locations.find((location) => location.state === 'Virginia' && location.isp.startsWith('Ashburn •'));

    expect(ashburn).toBeDefined();
    expect(parseProxyCheapLocation(ashburn?.id)).toEqual({
      country: 'US',
      region: 'Virginia',
      city: 'ashburn-va',
      ispId: 'isp-17',
    });
  });

  it('shares the same exact location payload for quote and checkout', () => {
    const location = buildProxyCheapLocations(setup).find((candidate) => candidate.state === 'Virginia' && candidate.isp.startsWith('Ashburn •'));
    const quotePayload = buildProxyCheapOrderPayload({ locationId: location?.id, planId: 'basic', periodMonths: 1 });
    const checkoutPayload = buildProxyCheapOrderPayload({ locationId: location?.id, planId: 'basic', periodMonths: 1 });

    expect(quotePayload).toEqual(checkoutPayload);
    expect(quotePayload).toMatchObject({
      country: 'US',
      region: 'Virginia',
      city: 'ashburn-va',
      ispId: 'isp-17',
    });
  });

  it('omits empty optional location fields from provider payloads', () => {
    const payload = buildProxyCheapOrderPayload({ country: 'DE', planId: 'basic' });
    expect(payload).toEqual({ planId: 'basic', quantity: 1, period: { unit: 'months', value: 1 }, country: 'DE' });
  });

  it('builds the execute payload without enabling auto-extension and without blank values', () => {
    const payload = buildProxyCheapExecutePayload({ planId: 'premium', quantity: 1, country: 'US', period: { unit: 'months', value: 1 } });
    expect(payload).toEqual({ planId: 'premium', quantity: 1, country: 'US', period: { unit: 'months', value: 1 }, autoExtend: { isEnabled: false } });
    expect(payload).not.toHaveProperty('couponCode');
    expect(payload).not.toHaveProperty('traffic');
  });

  it('only sends a coupon code when a real one is given', () => {
    expect(buildProxyCheapExecutePayload({ planId: 'basic' }, '   ')).not.toHaveProperty('couponCode');
    expect(buildProxyCheapExecutePayload({ planId: 'basic' }, ' SAVE10 ')).toMatchObject({ couponCode: 'SAVE10' });
  });

  it('continues to parse legacy pipe-delimited location IDs', () => {
    expect(parseProxyCheapLocation('US|VA|Ashburn|isp-17')).toEqual({
      country: 'US',
      region: 'VA',
      city: 'Ashburn',
      ispId: 'isp-17',
    });
  });
});

describe('Proxy-Cheap plan-specific location validation (documented setup shape)', () => {
  // Shape copied from the official docs: region/city ids are names, isps are string ids.
  const premiumSetup = {
    serviceId: 'static-residential-ipv4',
    countries: ['US', 'DE'],
    regions: { US: [{ id: 'New York', label: 'New York', isps: ['isp-ny'] }] },
    cities: {
      US: [{ id: 'New York City', label: 'New York City', region: 'New York', isps: ['isp-ny'] }],
      DE: [{ id: 'Frankfurt am Main', label: 'Frankfurt am Main', isps: ['isp-de'] }],
    },
    isps: { US: [{ id: 'isp-ny', label: 'NY ISP' }], DE: [{ id: 'isp-de', label: 'DE ISP' }] },
  };

  it('accepts every location built from the same plan setup', () => {
    for (const location of buildProxyCheapLocations(premiumSetup)) {
      expect(() => validateProxyCheapLocation(premiumSetup, parseProxyCheapLocation(location.id))).not.toThrow();
    }
  });

  it('rejects a region taken from another plan with NO_SUCH_REGION', () => {
    expect(() => validateProxyCheapLocation(premiumSetup, { country: 'US', region: 'Virginia', city: '', ispId: '' }))
      .toThrow(expect.objectContaining({ code: 'NO_SUCH_REGION' }));
  });

  it('rejects a city from another plan with NO_SUCH_CITY', () => {
    expect(() => validateProxyCheapLocation(premiumSetup, { country: 'US', region: 'New York', city: 'Ashburn', ispId: '' }))
      .toThrow(expect.objectContaining({ code: 'NO_SUCH_CITY' }));
  });

  it('rejects an ISP not listed for the selected city with NO_SUCH_ISP', () => {
    expect(() => validateProxyCheapLocation(premiumSetup, { country: 'DE', region: '', city: 'Frankfurt am Main', ispId: 'isp-ny' }))
      .toThrow(expect.objectContaining({ code: 'NO_SUCH_ISP' }));
  });

  it('normalizes composite plan ids', () => {
    expect(normalizeProxyCheapPlanId('static-residential-ipv4::premium')).toBe('premium');
    expect(normalizeProxyCheapPlanId('basic')).toBe('basic');
  });
});
