#!/bin/bash
# 临时保活：不点击窗口、不修改电源设置或管理描述文件。
set -u

task_dry_run=false
if [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ]; then
  echo "用法：$0 [--dry-run] [运行分钟数，默认30] [间隔秒数，默认150]"
  echo "运行分钟数：1–120；间隔秒数：120–180。按 Control+C 停止。"
  exit 0
fi
if [ "${1:-}" = "--dry-run" ]; then
  task_dry_run=true
  shift
fi
task_minutes_arg=${1:-30}
task_interval_arg=${2:-150}
if [ "$#" -gt 2 ] || ! [[ "$task_minutes_arg" =~ ^[0-9]{1,3}$ ]] || ! [[ "$task_interval_arg" =~ ^[0-9]{1,3}$ ]]; then
  echo "参数需为整数。使用 --help 查看用法。" >&2
  exit 2
fi
task_minutes=$((10#$task_minutes_arg))
task_interval=$((10#$task_interval_arg))
if [ "$task_minutes" -lt 1 ] || [ "$task_minutes" -gt 120 ] || [ "$task_interval" -lt 120 ] || [ "$task_interval" -gt 180 ]; then
  echo "运行分钟数需为1–120，间隔秒数需为120–180。" >&2
  exit 2
fi
if [ "$(/usr/bin/uname -s)" != Darwin ] || [ ! -x /usr/bin/caffeinate ]; then
  echo "此脚本需要 macOS 自带的 caffeinate。" >&2
  exit 1
fi
echo "临时保活 $task_minutes 分钟，每 $task_interval 秒发送一次用户活跃请求。"
echo "公司强制锁屏策略可能仍会生效；本脚本不修改该策略。"
if "$task_dry_run"; then
  echo "参数检查通过；未启动保活。"
  exit 0
fi

task_hold_pid=
task_pulse_pid=
task_wait_pid=
task_cleanup() {
  for task_child_pid in "$task_wait_pid" "$task_pulse_pid" "$task_hold_pid"; do
    if [ -n "$task_child_pid" ]; then
      kill "$task_child_pid" 2>/dev/null || :
      wait "$task_child_pid" 2>/dev/null || :
    fi
  done
  echo "已停止临时保活。"
}
trap task_cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP

task_total_seconds=$((task_minutes * 60))
task_started=$(/bin/date +%s)
task_deadline=$((task_started + task_total_seconds))
/usr/bin/caffeinate -di -t "$task_total_seconds" &
task_hold_pid=$!
echo "按 Control+C 随时停止；到时自动结束。"
while :; do
  task_remaining=$((task_deadline - $(/bin/date +%s)))
  [ "$task_remaining" -gt 0 ] || break
  task_pulse_seconds=5
  [ "$task_remaining" -ge 5 ] || task_pulse_seconds=$task_remaining
  /usr/bin/caffeinate -u -t "$task_pulse_seconds" &
  task_pulse_pid=$!
  if ! wait "$task_pulse_pid"; then
    echo "保活请求失败，已停止。" >&2
    exit 1
  fi
  task_pulse_pid=
  task_remaining=$((task_deadline - $(/bin/date +%s)))
  [ "$task_remaining" -gt 0 ] || break
  task_delay=$((task_interval - task_pulse_seconds))
  [ "$task_delay" -le "$task_remaining" ] || task_delay=$task_remaining
  /bin/sleep "$task_delay" &
  task_wait_pid=$!
  wait "$task_wait_pid" || exit 1
  task_wait_pid=
done
