export const TOPBAR_MENU_ITEM_MIN_WIDTH = 112;
export const TOPBAR_MORE_MENU_WIDTH = 104;
export const TOPBAR_MENU_GAP = 6;

/**
 * Returns how many top-level routes can be shown without shrinking a menu item.
 * When not all routes fit, the remainder is rendered by the "更多菜单" popover.
 */
export function resolveTopbarVisibleMenuCount(menuCount: number, containerWidth: number): number {
  if (menuCount <= 0) return 0;

  const fullMenuWidth = menuCount * TOPBAR_MENU_ITEM_MIN_WIDTH + Math.max(0, menuCount - 1) * TOPBAR_MENU_GAP;
  if (containerWidth >= fullMenuWidth) return menuCount;

  const visibleMenuWidth = containerWidth - TOPBAR_MORE_MENU_WIDTH - TOPBAR_MENU_GAP;
  const visibleCount = Math.floor(
    (visibleMenuWidth + TOPBAR_MENU_GAP) / (TOPBAR_MENU_ITEM_MIN_WIDTH + TOPBAR_MENU_GAP)
  );
  return Math.max(1, Math.min(menuCount - 1, visibleCount));
}
