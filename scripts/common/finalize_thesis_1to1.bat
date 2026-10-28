@echo off
chcp 65001 >nul
cd /d "%~dp0..\.."
echo ============================================
echo  OAISS CHAIN 毕业设计文档 1:1 还原最终落盘
echo  （请先关闭 WPS / Word 中已打开的目标文档）
echo ============================================
echo.
python scripts\common\restore_template_1to1.py --finalize
echo.
pause
