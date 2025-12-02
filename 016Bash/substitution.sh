#!/usr/bin/bash
FLAGS=$(grep "^flags" /proc/cpuinfo | sed 's/.*://' | head -1)

echo Your processor supports:

for f in $FLAGS; do
    case "${f}" in
        fpu)
            MSG="floating point unit"
        ;;
        3ndow)
            MSG="3DNOW graphics extentions"
        ;;
        mtrr)
            MSG="memory type range register"
        ;;
        *)
            MSG="unknow"
        ;;
    esac
    echo "$f"
    echo "$MSG"
done
