#!/system/bin/sh
MODDIR=${0%/*}
. "$MODDIR/features.sh"
load_features "$MODDIR/applied-features.conf"
if [ -f "$MODDIR/component-error" ]; then
    description='[⚠️组件准备失败，挂载已暂停]'
else
    google_status='⚠️Google未固化'
    enrollment='⚠️注册组件未就绪'
    dsp='⚠️DSP未运行'
    flags=$(dumpsys -t 10 package com.google.android.googlequicksearchbox 2>/dev/null | grep -m 1 '^    flags=\[')
    if printf '%s\n' "$flags" | grep -q '[[:space:]]SYSTEM[[:space:]]'; then
        google_status='✅Google系统应用'
    fi
    if [ "$hey" = 0 ]; then
        enrollment='⏸️Hey Google未修补'; dsp=''
    else
    library=$(pm list libraries 2>/dev/null)
    if pm path com.android.hotwordenrollment.xgoogle 2>/dev/null | grep -q '^package:' &&
       pm path com.android.hotwordenrollment.okgoogle 2>/dev/null | grep -q '^package:' &&
       printf '%s\n' "$library" | grep -q 'com.android.hotwordenrollment.common.util'; then
        enrollment='✅注册组件已加载'
    fi
    state=$(dumpsys -t 10 voiceinteraction 2>/dev/null | /data/adb/ksu/bin/busybox awk -f "$MODDIR/hotword-status.awk")
    case "$state" in
        dsp) dsp='✅DSP检测已初始化' ;;
        software) dsp='⚠️Google使用软件检测' ;;
        unknown) dsp='⏳DSP状态未知' ;;
    esac
    audio=$(cat "$MODDIR/audio-state" 2>/dev/null)
    case "$audio" in
        unsupported|missing) dsp="$dsp；音频配置不支持" ;;
        inherited-overlay) dsp="$dsp；原配置被挂载，未生成覆盖" ;;
        io-failed) dsp="$dsp；音频配置写入失败" ;;
        label-failed) dsp="$dsp；音频标签设置失败" ;;
    esac
    fi
    provider=$(cmd overlay lookup android android:string/config_defaultContextualSearchPackageName 2>/dev/null)
    capability=$(pm has-feature com.google.android.feature.CONTEXTUAL_SEARCH_LIVE_TRANSLATE 0 2>/dev/null)
    if [ "$provider" = com.google.android.googlequicksearchbox ] && [ "$capability" = true ] &&
        [ ! -f "$MODDIR/translation-error" ]; then
        translation='✅实时翻译配置已生效'
    elif [ -f "$MODDIR/translation-error" ]; then
        translation='⚠️实时翻译覆盖配置不兼容'
    else translation='⚠️实时翻译配置未生效'; fi
    if [ -n "$dsp" ]; then enrollment="$enrollment | $dsp"; fi
    description="[$google_status | $enrollment | $translation]"
    if [ -f "$MODDIR/features.conf" ] && [ -f "$MODDIR/applied-features.conf" ] &&
         ! cmp -s "$MODDIR/features.conf" "$MODDIR/applied-features.conf"; then
        description="$description [⏳选择已保存，待重启]"
    fi
fi
if ! grep -Fqx "description=$description" "$MODDIR/module.prop"; then
sed '/^description=/d' "$MODDIR/module.prop" > "$MODDIR/module.prop.tmp" || exit 1
printf 'description=%s\n' "$description" >> "$MODDIR/module.prop.tmp" &&
    chmod 0644 "$MODDIR/module.prop.tmp" &&
    mv "$MODDIR/module.prop.tmp" "$MODDIR/module.prop" || exit 1
fi
printf '%s\n' "$description"
