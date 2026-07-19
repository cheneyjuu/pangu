# Excel 报价附件 PDF 预览部署说明

## 运行依赖

后端通过 LibreOffice Calc 无界面进程将 `.xls`、`.xlsx` 转换为派生 PDF。服务器需要安装：

- LibreOffice（提供 `soffice`）
- 中文字体（推荐 Noto Sans CJK SC）

Ubuntu/Debian 示例：

```bash
apt-get update
apt-get install -y libreoffice-calc fonts-noto-cjk
```

## 环境变量

```bash
PANGU_LIBREOFFICE_COMMAND=/usr/bin/soffice
PANGU_EXCEL_PREVIEW_TIMEOUT_SECONDS=45
PANGU_EXCEL_PREVIEW_CJK_FONT=Noto Sans CJK SC
```

转换使用独立临时目录、独立 LibreOffice profile 和 Fontconfig 缓存。原 Excel 始终作为审计原件保留；PDF 使用确定性的版本化派生对象键，删除未绑定原件时同步清理。

部署后应使用一份包含中文、合并单元格、公式结果和多工作表的真实报价文件完成预览验收。
