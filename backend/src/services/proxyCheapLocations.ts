type JsonObject = Record<string, unknown>;

export type ProxyCheapLocation = {
  country: string;
  region: string;
  city: string;
  ispId: string;
};

export type ProxyCheapMarketplaceLocation = {
  id: string;
  countryCode: string;
  country: string;
  state: string;
  isp: string;
  protocols: { http: true; socks5: true };
};

const object = (value: unknown): JsonObject =>
  value && typeof value === 'object' && !Array.isArray(value) ? value as JsonObject : {};

const array = (value: unknown): unknown[] => Array.isArray(value) ? value : [];

const optionObject = (value: unknown): JsonObject =>
  typeof value === 'string'
    ? { code: value, id: value, name: value, label: value }
    : object(value);

const firstText = (...values: unknown[]): string => {
  for (const value of values) {
    if (typeof value === 'string' && value.trim()) return value;
    if (typeof value === 'number' && Number.isFinite(value)) return String(value);
  }
  return '';
};

const optionText = (value: unknown): string => {
  if (typeof value !== 'object' || value === null) return firstText(value);
  const entry = object(value);
  return firstText(entry.code, entry.id, entry.value, entry.key, entry.name, entry.label);
};

const normalize = (value: string) => value.trim().toLowerCase();

export const encodeProxyCheapLocation = (location: ProxyCheapLocation): string =>
  `geo:${Buffer.from(JSON.stringify(location), 'utf8').toString('base64url')}`;

export const parseProxyCheapLocation = (value: unknown): ProxyCheapLocation => {
  const raw = String(value ?? '');
  if (raw.startsWith('geo:')) {
    try {
      const parsed = JSON.parse(Buffer.from(raw.slice(4), 'base64url').toString('utf8')) as Partial<ProxyCheapLocation>;
      return {
        country: String(parsed.country ?? ''),
        region: String(parsed.region ?? ''),
        city: String(parsed.city ?? ''),
        ispId: String(parsed.ispId ?? ''),
      };
    } catch {
      // Older clients used pipe-delimited location IDs.
    }
  }

  const parts = raw.split('|');
  if (parts.length >= 5) {
    return { country: parts[0] ?? '', region: parts[1] ?? '', city: parts[2] ?? '', ispId: parts[4] ?? parts[3] ?? '' };
  }
  if (parts.length === 4 && !parts[1] && !parts[2]) {
    return { country: parts[0] ?? '', region: '', city: parts[3] ?? '', ispId: '' };
  }
  return { country: parts[0] ?? '', region: parts[1] ?? '', city: parts[2] ?? '', ispId: parts[3] ?? '' };
};

export function buildProxyCheapLocations(setup: JsonObject): ProxyCheapMarketplaceLocation[] {
  const countries = array(setup.countries).map(optionObject);
  const regionsByCountry = object(setup.regions);
  const citiesByCountry = object(setup.cities);
  const ispsByCountry = object(setup.isps);

  return countries.flatMap((countryOption) => {
    const country = firstText(countryOption.code, countryOption.countryCode, countryOption.country_code, countryOption.country, countryOption.id, countryOption.value);
    if (!country) return [];
    const countryName = firstText(countryOption.name, countryOption.label, countryOption.countryName, country);

    const regions = array(regionsByCountry[country]).map((value) => {
      const entry = optionObject(value);
      // Proxy-Cheap documents region.id as the value to send. Keep display
      // labels separate, while accepting aliases from compatible responses.
      const target = firstText(entry.id, entry.code, entry.regionCode, entry.region_code, entry.value, entry.key, entry.name, entry.label);
      const label = firstText(entry.label, entry.name, entry.region, entry.code, entry.regionCode, entry.region_code, entry.id, target);
      const aliases = [entry.code, entry.regionCode, entry.region_code, entry.id, entry.value, entry.key, entry.name, entry.label, target]
        .map((alias) => firstText(alias))
        .filter(Boolean);
      return { target, label, aliases, ispOptions: entry.isps };
    }).filter((region) => region.target);

    const regionsByAlias = new Map<string, typeof regions[number]>();
    for (const region of regions) {
      for (const alias of region.aliases) regionsByAlias.set(normalize(alias), region);
    }

    const resolveRegion = (value: unknown) => {
      const raw = optionText(value);
      const resolved = regionsByAlias.get(normalize(raw));
      return { target: resolved?.target ?? raw, label: resolved?.label ?? raw };
    };

    const countryCities = array(citiesByCountry[country]).map(optionObject);
    const regionsWithCities = new Set(countryCities.map((city) => {
      const ref = city.regionCode ?? city.region_code ?? city.regionId ?? city.region_id ?? city.region ?? city.stateCode ?? city.state_code ?? city.state;
      return resolveRegion(ref).target;
    }).filter(Boolean));

    const ispLabels = new Map<string, string>();
    for (const value of array(ispsByCountry[country])) {
      const isp = optionObject(value);
      const id = firstText(isp.id, isp.ispId, isp.isp_id, isp.code, isp.value, isp.name);
      if (id) ispLabels.set(id, firstText(isp.label, isp.name, isp.id, isp.code));
    }

    const ispChoices = (value: unknown, fallbackLabel: string) => {
      const choices = array(value).map((item) => {
        const isp = optionObject(item);
        const id = firstText(isp.id, isp.ispId, isp.isp_id, isp.code, isp.value, isp.key, isp.name);
        return { id, label: ispLabels.get(id) ?? firstText(isp.label, isp.name, id, fallbackLabel) };
      }).filter((isp) => isp.id);
      return choices.length ? choices : [{ id: '', label: fallbackLabel }];
    };

    const result: ProxyCheapMarketplaceLocation[] = [{
      id: encodeProxyCheapLocation({ country, region: '', city: '', ispId: '' }),
      countryCode: country,
      country: countryName,
      state: '',
      isp: '',
      protocols: { http: true, socks5: true },
    }];

    for (const region of regions) {
      if (regionsWithCities.has(region.target)) continue;
      for (const isp of ispChoices(region.ispOptions, 'الولاية مباشرة')) {
        result.push({
          id: encodeProxyCheapLocation({ country, region: region.target, city: '', ispId: isp.id }),
          countryCode: country,
          country: countryName,
          state: region.label,
          isp: isp.label,
          protocols: { http: true, socks5: true },
        });
      }
    }

    for (const city of countryCities) {
      const cityId = firstText(city.id, city.cityId, city.city_id, city.code, city.value, city.key, city.name, city.city, city.label);
      if (!cityId) continue;
      const cityName = firstText(city.label, city.name, city.city, city.id, city.cityId, city.city_id, city.code, cityId);
      const regionRef = city.regionCode ?? city.region_code ?? city.regionId ?? city.region_id ?? city.region ?? city.stateCode ?? city.state_code ?? city.state;
      const region = resolveRegion(regionRef);
      for (const isp of ispChoices(city.isps, cityName)) {
        result.push({
          id: encodeProxyCheapLocation({ country, region: region.target, city: cityId, ispId: isp.id }),
          countryCode: country,
          country: countryName,
          state: region.label,
          isp: `${cityName} • ${isp.label}`,
          protocols: { http: true, socks5: true },
        });
      }
    }

    for (const value of array(ispsByCountry[country])) {
      const isp = optionObject(value);
      const ispId = firstText(isp.id, isp.code, isp.value, isp.name);
      if (!ispId) continue;
      result.push({
        id: encodeProxyCheapLocation({ country, region: '', city: '', ispId }),
        countryCode: country,
        country: countryName,
        state: '',
        isp: firstText(isp.label, isp.name, isp.id, isp.code),
        protocols: { http: true, socks5: true },
      });
    }

    return result;
  }).filter((location) => location.id);
}

export function buildProxyCheapOrderPayload(input: {
  locationId?: unknown;
  country?: unknown;
  planId?: unknown;
  periodMonths?: unknown;
}) {
  const location = parseProxyCheapLocation(input.locationId ?? input.country);
  const payload: JsonObject = {
    planId: String(input.planId ?? ''),
    quantity: 1,
    period: { unit: 'months', value: Number(input.periodMonths ?? 1) },
  };
  // These are optional in the provider API. An omitted property means “any”,
  // while an empty string can be rejected as an invalid value.
  if (location.country) payload.country = location.country;
  if (location.region) payload.region = location.region;
  if (location.city) payload.city = location.city;
  if (location.ispId) payload.ispId = location.ispId;
  return payload;
}

/**
 * Execute payload = the exact payload that was priced + the auto-extend switch.
 * Blank values are NEVER sent: the provider answers an empty `couponCode` with NOT_BLANK, and
 * `traffic` is only sent when the quote step proved the plan needs it (it is already part of
 * `pricePayload` in that case).
 */
export const buildProxyCheapExecutePayload = (pricePayload: JsonObject, couponCode?: unknown): JsonObject => {
  const payload: JsonObject = { ...pricePayload, autoExtend: { isEnabled: false } };
  if (typeof couponCode === 'string' && couponCode.trim()) payload.couponCode = couponCode.trim();
  return payload;
};

/**
 * Proxy-Cheap plans are sent as `planId` (e.g. "basic"). Older clients may send
 * the composite marketplace id "static-residential-ipv4::basic".
 */
export const normalizeProxyCheapPlanId = (value: unknown): string => {
  const raw = String(value ?? '').trim();
  const separator = raw.lastIndexOf('::');
  return separator >= 0 ? raw.slice(separator + 2).trim() : raw;
};

const PROXY_CHEAP_LOCATION_ERRORS: Record<string, string> = {
  NO_SUCH_COUNTRY: 'الدولة المختارة غير متاحة لهذه الباقة.',
  NO_SUCH_REGION: 'الولاية المختارة غير متاحة لهذه الباقة.',
  NO_SUCH_CITY: 'المدينة المختارة غير متاحة لهذه الباقة أو لا تتبع الولاية المختارة.',
  NO_SUCH_ISP: 'المزود المختار غير متاح في هذا الموقع لهذه الباقة.',
  NOT_ALLOWED: 'هذا المنتج لا يدعم تحديد هذا النوع من المواقع.',
};

export class ProxyCheapLocationError extends Error {
  constructor(public readonly code: keyof typeof PROXY_CHEAP_LOCATION_ERRORS | string) {
    super(PROXY_CHEAP_LOCATION_ERRORS[code] ?? 'الموقع المختار غير متاح لدى المزود.');
    this.name = 'ProxyCheapLocationError';
  }
}

/** Converts raw provider error text such as "NO_SUCH_REGION Virginia" into a clear Arabic message. */
export const friendlyProxyCheapError = (message: string): string => {
  const code = Object.keys(PROXY_CHEAP_LOCATION_ERRORS).find((key) => message.includes(key));
  if (code) return `${PROXY_CHEAP_LOCATION_ERRORS[code]} أعد اختيار الموقع ثم حاول مرة أخرى.`;
  return message;
};

const idsOf = (values: unknown): Set<string> =>
  new Set(array(values).map((value) => optionText(value)).filter(Boolean));

/**
 * Validates a location against the setup returned for the SAME service + plan,
 * following the documented Proxy-Cheap rules:
 *  - country must be in `countries`
 *  - region must be an id in `regions[country]`
 *  - city must be an id in `cities[country]` and belong to `region` when sent
 *  - ispId must be in `isps[country]` and, when a city (or only a region) is
 *    sent, listed in that city's (or region's) `isps`.
 */
export function validateProxyCheapLocation(setup: JsonObject, location: ProxyCheapLocation): void {
  const { country, region, city, ispId } = location;
  if (!country || !idsOf(setup.countries).has(country)) throw new ProxyCheapLocationError('NO_SUCH_COUNTRY');

  const regions = array(object(setup.regions)[country]).map(optionObject);
  const cities = array(object(setup.cities)[country]).map(optionObject);
  const regionEntry = region ? regions.find((entry) => firstText(entry.id, entry.code, entry.regionCode, entry.region_code, entry.value, entry.key, entry.name, entry.label) === region) : undefined;
  if (region && !regionEntry) throw new ProxyCheapLocationError('NO_SUCH_REGION');

  const cityEntry = city ? cities.find((entry) => firstText(entry.id, entry.code, entry.name, entry.label) === city) : undefined;
  if (city && !cityEntry) throw new ProxyCheapLocationError('NO_SUCH_CITY');
  if (cityEntry && region) {
    const cityRegion = firstText(cityEntry.region, cityEntry.regionCode, cityEntry.region_code, cityEntry.regionId, cityEntry.state);
    if (cityRegion && cityRegion !== region && cityRegion !== firstText(regionEntry?.label, regionEntry?.name)) throw new ProxyCheapLocationError('NO_SUCH_CITY');
  }

  if (ispId) {
    const countryIsps = idsOf(array(object(setup.isps)[country]).map((value) => {
      const isp = optionObject(value);
      return firstText(isp.id, isp.ispId, isp.isp_id, isp.code, isp.value, isp.name);
    }));
    if (!countryIsps.has(ispId)) throw new ProxyCheapLocationError('NO_SUCH_ISP');
    const scope = cityEntry ?? regionEntry;
    if (scope && Array.isArray(scope.isps) && !idsOf(scope.isps).has(ispId)) throw new ProxyCheapLocationError('NO_SUCH_ISP');
  }
}
