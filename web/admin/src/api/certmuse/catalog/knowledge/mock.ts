/**
 * 考纲页面的前端 mock 数据入口。
 *
 * 后端接口就绪后，仅需在此处改为请求适配，不需要改动页面搜索、定位和树展示逻辑。
 */
export function createMockKnowledgeSyllabuses<T>(createSystemArchitectTree: () => T, createSoftwareEngineerTree: () => T) {
  return [
    {
      id: 'system-architect-2026',
      year: '2026',
      name: '2026 系统架构设计师考纲',
      certification: 'SYSTEM_ARCHITECT' as const,
      certificationLabel: '系统架构设计师',
      version: 'V2',
      versionLabel: '第二版',
      status: '0' as const,
      importedAt: '2026-07-30 14:47',
      sourceFile: '暂无（待导入正式知识点）',
      createTree: createSystemArchitectTree
    },
    {
      id: 'software-engineer-2026',
      year: '2026',
      name: '2026 软件设计师考纲',
      certification: 'SOFTWARE_ENGINEER' as const,
      certificationLabel: '软件设计师',
      version: 'V2',
      versionLabel: '第二版',
      status: '0' as const,
      importedAt: '2026-07-31 09:00',
      sourceFile: '软件设计师考纲-2026.jsonl',
      createTree: createSoftwareEngineerTree
    }
  ];
}
