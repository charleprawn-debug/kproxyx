import crypto from 'crypto';
import config from '../config';
import { Logger } from './logger';

const logger = new Logger('Encryption');

/**
 * AES encryption helper for at-rest secrets (e.g. stored proxy credentials).
 *
 * Ciphertext format: `<ivHex>:<authTagHex>:<cipherHex>`. `authTagHex` is
 * empty for non-AEAD algorithms (e.g. aes-256-cbc) and populated for AEAD
 * algorithms (e.g. aes-256-gcm). A fresh, random IV is generated on every
 * call — reusing a fixed IV with the same key (as this file previously did)
 * breaks CBC's semantic security: identical plaintexts would always produce
 * identical ciphertexts, leaking structure to anyone who can read the
 * database.
 */
class EncryptionService {
  private algorithm: string;
  private key: Buffer;

  constructor() {
    this.algorithm = config.encryption.algorithm;

    // Ensure key is 32 bytes for aes-256
    const keyString = config.encryption.key;
    if (keyString.length < 32) {
      this.key = Buffer.alloc(32);
      Buffer.from(keyString).copy(this.key);
      logger.warn('Encryption key is shorter than 32 bytes, padding with zeros');
    } else {
      this.key = Buffer.from(keyString.substring(0, 32));
    }
  }

  private ivLength(): number {
    return crypto.getCipherInfo(this.algorithm)?.ivLength ?? 16;
  }

  private isAuthenticated(): boolean {
    return /gcm|ccm|ocb|chacha20-poly1305/i.test(this.algorithm);
  }

  /**
   * Encrypt sensitive data. Returns `ivHex:authTagHex:cipherHex`.
   */
  encrypt(text: string): string {
    try {
      const iv = crypto.randomBytes(this.ivLength());
      const cipher = crypto.createCipheriv(this.algorithm, this.key, iv);

      let encrypted = cipher.update(text, 'utf8', 'hex');
      encrypted += cipher.final('hex');

      const authTag = this.isAuthenticated()
        ? (cipher as crypto.CipherGCM).getAuthTag().toString('hex')
        : '';

      return `${iv.toString('hex')}:${authTag}:${encrypted}`;
    } catch (error) {
      logger.error('Encryption failed', error as Error);
      throw new Error('Failed to encrypt data');
    }
  }

  /**
   * Decrypt data produced by [encrypt].
   */
  decrypt(encryptedText: string): string {
    try {
      const [ivHex, authTagHex, cipherHex] = encryptedText.split(':');
      if (!ivHex || cipherHex === undefined) {
        throw new Error('Malformed ciphertext');
      }

      const decipher = crypto.createDecipheriv(this.algorithm, this.key, Buffer.from(ivHex, 'hex'));
      if (authTagHex) {
        (decipher as crypto.DecipherGCM).setAuthTag(Buffer.from(authTagHex, 'hex'));
      }

      let decrypted = decipher.update(cipherHex, 'hex', 'utf8');
      decrypted += decipher.final('utf8');

      return decrypted;
    } catch (error) {
      logger.error('Decryption failed', error as Error);
      throw new Error('Failed to decrypt data');
    }
  }

  /**
   * Hash a password (for verification purposes — not used for the primary
   * login flow, which delegates to Supabase Auth). Returns `saltHex:hashHex`
   * when no salt is supplied, so the salt travels with the hash instead of
   * relying on a fixed value shared across every password.
   */
  hashPassword(password: string, salt?: string): string {
    try {
      const saltHex = salt ?? crypto.randomBytes(16).toString('hex');
      const hash = crypto.pbkdf2Sync(password, saltHex, PBKDF2_ITERATIONS, 64, 'sha512').toString('hex');
      return salt ? hash : `${saltHex}:${hash}`;
    } catch (error) {
      logger.error('Password hashing failed', error as Error);
      throw new Error('Failed to hash password');
    }
  }

  /**
   * Verify a password against a `saltHex:hashHex` string produced by
   * [hashPassword].
   */
  verifyPassword(password: string, stored: string): boolean {
    const [saltHex, hashHex] = stored.split(':');
    if (!saltHex || !hashHex) return false;
    const candidate = crypto.pbkdf2Sync(password, saltHex, PBKDF2_ITERATIONS, 64, 'sha512').toString('hex');
    const a = Buffer.from(candidate, 'hex');
    const b = Buffer.from(hashHex, 'hex');
    return a.length === b.length && crypto.timingSafeEqual(a, b);
  }

  /**
   * Generate a random string (for tokens, codes, etc.)
   */
  generateRandomString(length: number = 32): string {
    return crypto.randomBytes(length).toString('hex');
  }

  /**
   * Generate a UUID
   */
  generateUUID(): string {
    return crypto.randomUUID();
  }

  /**
   * Create HMAC for signing
   */
  createHMAC(data: string, secret: string = this.key.toString('hex')): string {
    return crypto.createHmac('sha256', secret).update(data).digest('hex');
  }

  /**
   * Verify HMAC signature
   */
  verifyHMAC(data: string, signature: string, secret: string = this.key.toString('hex')): boolean {
    const calculated = Buffer.from(this.createHMAC(data, secret), 'hex');
    const provided = Buffer.from(signature, 'hex');
    return calculated.length === provided.length && crypto.timingSafeEqual(calculated, provided);
  }
}

// OWASP-recommended minimum for PBKDF2-HMAC-SHA512 (2024 guidance).
const PBKDF2_ITERATIONS = 210_000;

export const encryptionService = new EncryptionService();

export default encryptionService;
