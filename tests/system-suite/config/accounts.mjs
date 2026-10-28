/**
 * OAISS CHAIN Preset Accounts Configuration
 */
export const ACCOUNTS = {
  admin: {
    username: 'admin',
    password: process.env.ADMIN_PASSWORD || 'admin123',
    role: 'ADMIN'
  },
  enterprise001: {
    username: 'enterprise001',
    password: process.env.ENTERPRISE_PASSWORD || 'admin123',
    role: 'ENTERPRISE',
    userId: 1,
    enterpriseId: 1
  },
  enterprise002: {
    username: 'enterprise002',
    password: process.env.ENTERPRISE2_PASSWORD || 'admin123',
    role: 'ENTERPRISE',
    userId: 2,
    enterpriseId: 2
  },
  reviewer001: {
    username: 'reviewer001',
    password: process.env.REVIEWER_PASSWORD || 'admin123',
    role: 'REVIEWER',
    userId: 3
  },
  thirdparty001: {
    username: 'thirdparty001',
    password: process.env.THIRDPARTY_PASSWORD || 'admin123',
    role: 'THIRD_PARTY',
    userId: 4
  }
};
