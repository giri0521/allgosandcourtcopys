import { describe, expect, it } from 'vitest';
import { notificationLink } from '@/features/notifications/api';

/**
 * Where a notification leads when you open it.
 *
 * <p>Worth its own test because the failure is silent and lands on the worst possible person: a
 * member opened "Your account has been approved", the `user:` reference sent them to
 * `/admin/members`, and the route guard showed them Access Restricted. Nothing threw, nothing was
 * logged, and the one screen that told them they were welcome told them they were not.
 */
describe('notificationLink', () => {
  it('sends a member with a user reference to their own account, not the admin list', () => {
    expect(notificationLink('user:44444444-4444-4444-4444-444444444441', false)).toBe('/profile');
  });

  it('sends an admin with a user reference to the members list', () => {
    expect(notificationLink('user:44444444-4444-4444-4444-444444444441', true)).toBe(
      '/admin/members',
    );
  });

  it('defaults to the member reading, since that is the larger and less privileged audience', () => {
    expect(notificationLink('user:44444444-4444-4444-4444-444444444441')).toBe('/profile');
  });

  it('opens a document, and a batch upload at its folder', () => {
    expect(notificationLink('file:33333333-3333-3333-3333-333333333331')).toBe(
      '/files/33333333-3333-3333-3333-333333333331',
    );
    expect(notificationLink('folder:22222222-2222-2222-2222-222222222221')).toBe(
      '/folders/22222222-2222-2222-2222-222222222221',
    );
  });

  it('sends a deleted folder\'s notification to the department it sat in, since the folder itself is gone', () => {
    expect(notificationLink('department:11111111-1111-1111-1111-111111111111')).toBe(
      '/departments/11111111-1111-1111-1111-111111111111',
    );
  });

  it('yields no link rather than a broken one', () => {
    expect(notificationLink(null)).toBeNull();
    expect(notificationLink('')).toBeNull();
    // A kind this build does not know, and a known kind with nothing to point at.
    expect(notificationLink('circular:11111111-1111-1111-1111-111111111111')).toBeNull();
    expect(notificationLink('file:')).toBeNull();
  });
});
