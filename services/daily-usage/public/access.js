export const PAGES = [
  { id: 'overview', href: '/', label: 'Overview', icon: 'home' },
  { id: 'app-usage', href: '/app-usage', label: 'App usage', icon: 'chart' },
  { id: 'app-controls', href: '/app-controls', label: 'App controls', icon: 'controls' },
];

export function canAccess(user, page) {
  if (!user) return false;
  if (page === 'account') return true;
  if (page === 'users') return user.isAdmin === true;
  return PAGES.some(item => item.id === page) && (user.isOwner === true || user.pages?.includes(page));
}

export function navigation(user) {
  return [...PAGES, { id: 'users', href: '/users', label: 'Users', icon: 'users' },
    { id: 'account', href: '/account', label: 'My account', icon: 'account' }].filter(page => canAccess(user, page.id));
}
