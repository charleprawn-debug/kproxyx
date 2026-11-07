import config from '../config';

type Meta = Record<string, unknown> | Error | unknown;
const serialize = (meta?: Meta) => meta instanceof Error ? { message: meta.message, stack: meta.stack } : meta;

export class Logger {
  constructor(private readonly moduleName: string) {}
  private write(level: string, message: string, meta?: Meta) {
    const record = { timestamp: new Date().toISOString(), level, module: this.moduleName, message, ...(meta === undefined ? {} : { meta: serialize(meta) }) };
    const output = JSON.stringify(record);
    if (level === 'error') console.error(output); else if (level === 'warn') console.warn(output); else console.log(output);
  }
  info(message: string, meta?: Meta) { this.write('info', message, meta); }
  error(message: string, error?: Meta, meta?: Record<string, unknown>) { this.write('error', message, error === undefined ? meta : { error: serialize(error), ...meta }); }
  warn(message: string, meta?: Meta) { this.write('warn', message, meta); }
  debug(message: string, meta?: Meta) { if (config.logging.level === 'debug') this.write('debug', message, meta); }
  trace(message: string, meta?: Meta) { if (config.logging.level === 'debug') this.write('trace', message, meta); }
}

export default new Logger('app');
