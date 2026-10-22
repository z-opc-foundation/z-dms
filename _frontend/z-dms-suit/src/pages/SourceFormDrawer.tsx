import { useCallback, useEffect, useState } from 'react'
import {
  Alert,
  App as AntdApp,
  Button,
  Drawer,
  Form,
  Input,
  Select,
  Space,
} from 'antd'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import { listCredentialRefs, registerSource, testCandidate, updateSource } from '../api'
import type { SourceForm, SourceItem } from '../types'

interface PropRow { k: string; v: string }

interface FormValues {
  id: string
  name: string
  type: string
  credentialRef?: string
  properties: PropRow[]
}

/**
 * 数据源类型。
 *
 * <p>⚠️ 当前**只有 JDBC 的连接工厂落地了**（core 的 DruidDataSourcePool），
 * 其它类型（MEMORY / CSV / HTTP / SQL）的连接工厂还没写 ⇒ 这里把它们置灰，
 * 不给一个「选了必定登记失败」的假选项。
 */
const TYPE_OPTIONS = [
  { value: 'JDBC', label: 'JDBC（MySQL 等）' },
  { value: 'MEMORY', label: 'MEMORY（连接工厂未落地）', disabled: true },
  { value: 'CSV', label: 'CSV（连接工厂未落地）', disabled: true },
  { value: 'HTTP', label: 'HTTP（连接工厂未落地）', disabled: true },
  { value: 'SQL', label: 'SQL（连接工厂未落地）', disabled: true },
]

/**
 * 登记 / 编辑抽屉。
 *
 * <p><b>凭据面只有 credentialRef 下拉选择器 —— 这里没有任何口令输入框。</b>
 * 口令由 z-mist 按引用取出，只在服务端内存里拼进连接参数（core 纪律），
 * 既不进浏览器，也不入库、不进变更历史的快照。
 *
 * <p>properties 里如果出现 password / secret 这类敏感键，<b>后端写入口会直接拒绝</b>
 * （报错只列键名、不带值），界面这边也提示一句。
 */
export default function SourceFormDrawer(props: {
  open: boolean
  editing: SourceItem | null
  onClose: () => void
  onSaved: () => void
}) {
  const { open, editing, onClose, onSaved } = props
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm<FormValues>()
  const [saving, setSaving] = useState(false)
  const [testing, setTesting] = useState(false)
  const [refOptions, setRefOptions] = useState<string[]>([])

  const loadRefs = useCallback(async () => {
    try {
      setRefOptions(await listCredentialRefs())
    } catch {
      setRefOptions([])
    }
  }, [])

  useEffect(() => {
    if (!open) return
    void loadRefs()
    if (editing) {
      const rows: PropRow[] = Object.entries(editing.properties || {}).map(([k, v]) => ({ k, v }))
      form.setFieldsValue({
        id: editing.id,
        name: editing.name,
        type: editing.type,
        credentialRef: editing.credentialRef ?? undefined,
        properties: rows,
      })
    } else {
      form.setFieldsValue({
        id: '',
        name: '',
        type: 'JDBC',
        credentialRef: undefined,
        properties: [{ k: 'host', v: '' }, { k: 'port', v: '3306' }, { k: 'database', v: '' }],
      })
    }
  }, [open, editing, form, loadRefs])

  /** 表单值 → 接口入参（properties 数组 → 对象）。 */
  const toPayload = (): SourceForm => {
    const v = form.getFieldsValue()
    const properties: Record<string, string> = {}
    ;(v.properties || []).forEach((row) => {
      const k = (row?.k || '').trim()
      if (!k) return
      properties[k] = row?.v ?? ''
    })
    return {
      id: (v.id || '').trim(),
      name: (v.name || '').trim(),
      type: v.type || 'JDBC',
      credentialRef: v.credentialRef,
      properties,
    }
  }

  const handleTest = async () => {
    setTesting(true)
    try {
      const payload = toPayload()
      const probe = await testCandidate(payload)
      if (probe.up) {
        message.success(`连通：${probe.detail ?? 'SELECT 1 ok'}（${probe.latencyMs ?? '-'} ms，目标 ${probe.target ?? '-'}）`)
      } else {
        message.error(`连不通：${probe.detail ?? '未知原因'}`)
      }
    } catch (e: any) {
      message.error(e?.message || '测连接失败')
    } finally {
      setTesting(false)
    }
  }

  const handleSave = async () => {
    try {
      await form.validateFields()
    } catch {
      return
    }
    setSaving(true)
    try {
      const payload = toPayload()
      if (editing) {
        await updateSource(editing.id, payload)
        message.success(`已保存 ${editing.id}`)
      } else {
        await registerSource(payload)
        message.success(`已登记 ${payload.id}`)
      }
      onSaved()
    } catch (e: any) {
      message.error(e?.message || '保存失败')
    } finally {
      setSaving(false)
    }
  }

  // 选项目录 + 当前值（编辑时当前引用未必在目录里，补进去免得被下拉吞掉）
  const options = Array.from(new Set([...(refOptions || []), ...(editing?.credentialRef ? [editing.credentialRef] : [])]))
    .filter(Boolean)
    .map((v) => ({ value: v, label: v }))

  return (
    <Drawer
      open={open}
      onClose={onClose}
      width={760}
      title={editing ? `编辑数据源 ${editing.id}` : '登记数据源'}
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="凭据面只有 credentialRef 选择器"
        description="口令永远不进界面、不进浏览器、不入库：它由 z-mist 按引用取出，只在服务端内存里拼进连接参数。properties 里出现 password / secret 这类敏感键，后端写入口会直接拒绝。"
      />

      <Form form={form} layout="vertical" size="middle">
        <Space size={16} style={{ display: 'flex' }} align="start">
          <Form.Item
            name="id"
            label="数据源 ID"
            style={{ width: 220 }}
            rules={[{ required: true, message: '请填写数据源 ID' }]}
            extra={editing ? 'ID 建后不复用，不可改' : '稳定标识，建后不可改'}
          >
            <Input disabled={!!editing} placeholder="例如 oc-main" />
          </Form.Item>
          <Form.Item
            name="name"
            label="名称"
            style={{ width: 220 }}
            rules={[{ required: true, message: '请填写名称' }]}
          >
            <Input placeholder="例如 中央库主库" />
          </Form.Item>
          <Form.Item name="type" label="类型" style={{ width: 220 }}>
            <Select options={TYPE_OPTIONS} />
          </Form.Item>
        </Space>

        <Form.Item
          name="credentialRef"
          label="凭据引用（credentialRef）"
          style={{ maxWidth: 460 }}
          extra="形如 <group>/<key>，指向 z-mist 里的键名。JDBC 源必填。"
        >
          <Select
            allowClear
            showSearch
            placeholder="选择凭据引用"
            options={options}
            optionFilterProp="label"
          />
        </Form.Item>

        <Form.Item label="连接属性（非敏感）">
          <Form.List name="properties">
            {(fields, { add, remove }) => (
              <>
                {fields.map((field) => (
                  <Space key={field.key} align="baseline" style={{ display: 'flex', marginBottom: 8 }}>
                    <Form.Item
                      name={[field.name, 'k']}
                      style={{ width: 220, marginBottom: 0 }}
                      rules={[{ required: true, message: '键名不能为空' }]}
                    >
                      <Input placeholder="键，例如 host" />
                    </Form.Item>
                    <Form.Item
                      name={[field.name, 'v']}
                      style={{ width: 380, marginBottom: 0 }}
                    >
                      <Input placeholder="值（非敏感）" />
                    </Form.Item>
                    <Button icon={<DeleteOutlined />} onClick={() => remove(field.name)} />
                  </Space>
                ))}
                <Button
                  type="dashed"
                  icon={<PlusOutlined />}
                  onClick={() => add({ k: '', v: '' })}
                  block
                >
                  添加一条属性
                </Button>
              </>
            )}
          </Form.List>
        </Form.Item>
      </Form>

      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 8 }}>
        <Button onClick={onClose}>取消</Button>
        <Button loading={testing} onClick={() => void handleTest()}>
          自测连接（真实发起）
        </Button>
        <Button type="primary" loading={saving} onClick={() => void handleSave()}>
          保存
        </Button>
      </div>
    </Drawer>
  )
}
