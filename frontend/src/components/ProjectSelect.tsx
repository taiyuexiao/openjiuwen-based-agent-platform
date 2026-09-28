import { useEffect, useState } from 'react';
import { Select } from 'antd';
import { api } from '../api/client';
import { useProject } from '../context/ProjectContext';
import type { Project } from '../types';

interface Props {
  /** 显式传入则脱离全局 context（如详情页锁定项目） */
  value?: number;
  onChange?: (value: number | undefined) => void;
  style?: React.CSSProperties;
  allowClear?: boolean;
}

/** 项目下拉选择器：默认读写全局当前项目（顶栏切换器同源） */
export default function ProjectSelect({ value, onChange, style, allowClear = true }: Props) {
  const ctx = useProject();
  const controlled = value !== undefined || onChange !== undefined;
  const [projects, setProjects] = useState<Project[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setLoading(true);
    api<Project[]>('/v1/api/projects/list')
      .then(setProjects)
      .catch(() => undefined)
      .finally(() => setLoading(false));
  }, []);

  return (
    <Select
      style={{ minWidth: 220, ...style }}
      placeholder="选择项目"
      loading={loading}
      allowClear={allowClear}
      showSearch
      optionFilterProp="label"
      value={controlled ? value : ctx.projectId}
      onChange={controlled ? onChange : ctx.setProjectId}
      options={projects.map((p) => ({
        value: p.id,
        label: `${p.code} - ${p.name}`,
      }))}
    />
  );
}
