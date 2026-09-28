import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';

const PROJECT_KEY = 'agentops_project';

interface ProjectContextValue {
  projectId?: number;
  setProjectId: (id: number | undefined) => void;
}

const ProjectContext = createContext<ProjectContextValue>({
  projectId: undefined,
  setProjectId: () => undefined,
});

/** 全局当前项目：顶栏切换器写入，各业务页面默认消费 */
export function ProjectProvider({ children }: { children: ReactNode }) {
  const [projectId, setProjectIdState] = useState<number | undefined>(() => {
    const saved = Number(localStorage.getItem(PROJECT_KEY));
    return Number.isFinite(saved) && saved > 0 ? saved : undefined;
  });

  const setProjectId = useCallback((id: number | undefined) => {
    setProjectIdState(id);
    if (id === undefined) {
      localStorage.removeItem(PROJECT_KEY);
    } else {
      localStorage.setItem(PROJECT_KEY, String(id));
    }
  }, []);

  const value = useMemo(() => ({ projectId, setProjectId }), [projectId, setProjectId]);
  return <ProjectContext.Provider value={value}>{children}</ProjectContext.Provider>;
}

export function useProject(): ProjectContextValue {
  return useContext(ProjectContext);
}
