/**
 * OAISS CHAIN System Suite Environment Configuration
 */
export const ENV = {
  BASE_URL: process.env.BASE_URL || 'http://localhost:8080/api/v1',
  TIMEOUT: parseInt(process.env.TIMEOUT || '10000', 10),
  DEBUG: process.env.DEBUG === 'true'
};
