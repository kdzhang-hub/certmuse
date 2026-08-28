/**
 * 部门查询参数
 */
export interface DeptQuery extends PageQuery {
  deptName?: string;
  deptCategory?: string;
  status?: number;
}

/**
 * 部门类型
 */
export interface DeptVO extends BaseEntity {
  id: string;
  parentName: string;
  parentId: string;
  children: DeptVO[];
  deptId: string;
  deptName: string;
  deptCategory: string;
  orderNum: number;
  leader: string;
  phone: string;
  email: string;
  status: string;
  delFlag: string;
  ancestors: string;
  menuId: string;
}

/**
 * 部门类型
 */
export interface DeptTreeVO extends BaseEntity {
  id: string;
  label: string;
  parentId: string;
  weight: number;
  children: DeptTreeVO[];
  disabled: boolean;
}

/**
 * 部门表单类型
 */
export interface DeptForm {
  parentName?: string;
  parentId?: string;
  children?: DeptForm[];
  deptId?: string;
  deptName?: string;
  deptCategory?: string;
  orderNum?: number;
  leader?: string;
  phone?: string;
  email?: string;
  status?: string;
  delFlag?: string;
  ancestors?: string;
}
