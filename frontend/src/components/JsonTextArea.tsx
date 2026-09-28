import { Input } from 'antd';

interface Props {
  value?: string;
  onChange?: (value: string) => void;
  rows?: number;
  placeholder?: string;
}

/** JSON 文本输入框（配合 Form.Item 使用，值为字符串，提交时用 parseJson 转换） */
export default function JsonTextArea({ value, onChange, rows = 4, placeholder }: Props) {
  return (
    <Input.TextArea
      value={value}
      onChange={(e) => onChange?.(e.target.value)}
      rows={rows}
      placeholder={placeholder ?? '请输入 JSON（可留空）'}
      style={{ fontFamily: 'monospace' }}
    />
  );
}

/** antd Form 校验规则：允许空，非空必须是合法 JSON */
export const jsonRule = (required = false) => ({
  validator: (_: unknown, value?: string) => {
    if (value === undefined || value.trim() === '') {
      return required ? Promise.reject(new Error('请输入 JSON')) : Promise.resolve();
    }
    try {
      JSON.parse(value);
      return Promise.resolve();
    } catch {
      return Promise.reject(new Error('JSON 格式不合法'));
    }
  },
});
