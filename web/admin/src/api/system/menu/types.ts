import type { MenuTypeEnum } from '@/enums/MenuTypeEnum';

/**
 * 菜单树形结构类型
 */
export interface MenuTreeOption {
  id: string;
  label: string;
  parentId: string;
  weight: number;
  menuType?: MenuTypeEnum | string;
  visible?: string;
  status?: string;
  disabled?: boolean;
  children?: MenuTreeOption[];
}

export interface RoleMenuTree {
  menus: MenuTreeOption[];
  checkedKeys: string[];
}

/**
 * 角色菜单分配中的按钮节点类型
 */
export interface RoleMenuButtonOption {
  menuId: string;
  menuName: string;
  parentId: string;
  perms?: string;
  status?: string;
  disabled?: boolean;
}

/**
 * 菜单查询参数类型
 */
export interface MenuQuery {
  keywords?: string;
  menuName?: string;
  status?: string;
}

/**
 * 菜单视图对象类型
 */
export interface MenuVO extends BaseEntity {
  parentName: string;
  parentId: string;
  children: MenuVO[];
  menuId: string;
  menuName: string;
  orderNum: number;
  path: string;
  component: string;
  queryParam: string;
  isFrame: string;
  isCache: string;
  menuType: MenuTypeEnum;
  visible: string;
  status: string;
  icon: string;
  activeMenu: string;
  ext: string;
  remark: string;
}

export interface MenuForm {
  parentName?: string;
  parentId?: string;
  children?: MenuForm[];
  menuId?: string;
  menuName: string;
  orderNum: number;
  path: string;
  component?: string;
  queryParam?: string;
  isFrame?: string;
  isCache?: string;
  menuType?: MenuTypeEnum;
  visible?: string;
  status?: string;
  icon?: string;
  activeMenu?: string;
  ext?: string;
  remark?: string;
  query?: string;
  perms?: string;
}
