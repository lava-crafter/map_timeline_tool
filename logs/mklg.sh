#!/usr/bin/env bash

set -Eeuo pipefail

readonly package_name="${1:-com.lavacrafter.maptimelinetool}"
readonly script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly output_file="${2:-"${script_dir}/mklg-$(date +%Y%m%d-%H%M%S).log"}"

if ! command -v adb >/dev/null 2>&1; then
    printf '错误：找不到 adb，请先安装 Android SDK platform-tools。\n' >&2
    exit 1
fi

adb wait-for-device
adb shell pm path "$package_name" >/dev/null

package_info="$(adb shell cmd package list packages -U "$package_name" | tr -d '\r')"
uid="${package_info##*uid:}"
uid="${uid//[[:space:]]/}"

if [[ -z "$uid" || "$uid" == "$package_info" ]]; then
    printf '错误：无法获取应用 %s 的 UID。\n' "$package_name" >&2
    exit 1
fi

mkdir -p -- "$(dirname -- "$output_file")"
adb logcat -c

printf '已清空 logcat，开始采集 %s（UID %s）。\n' "$package_name" "$uid"
printf '日志文件：%s\n' "$output_file"
printf '按 Ctrl+C 结束采集。\n'

trap 'printf '\''\n已停止采集。\n'\''; exit 130' INT TERM
adb logcat --uid="$uid" -v threadtime >"$output_file"
