import { useState } from 'react';
import { Modal, Typography } from 'antd';
import { prettyJson } from '../utils/format';

interface Props {
  value?: string | null;
  title?: string;
}

/** 表格单元格内的 JSON 查看入口：截断展示 + 点击弹窗看美化后的完整内容 */
export default function JsonView({ value, title = 'JSON 内容' }: Props) {
  const [open, setOpen] = useState(false);
  if (value === undefined || value === null || value === '') {
    return <span>-</span>;
  }
  const brief = value.length > 40 ? `${value.slice(0, 40)}...` : value;
  return (
    <>
      <Typography.Link onClick={() => setOpen(true)} style={{ fontFamily: 'monospace', fontSize: 12 }}>
        {brief}
      </Typography.Link>
      <Modal title={title} open={open} footer={null} onCancel={() => setOpen(false)} width={720}>
        <pre style={{ maxHeight: 480, overflow: 'auto', background: '#f6f8fa', padding: 12, borderRadius: 6 }}>
          {prettyJson(value)}
        </pre>
      </Modal>
    </>
  );
}
