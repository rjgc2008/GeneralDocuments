#!/bin/bash
# test
function getfiles()
{
	filePath="/media/pi/all/200.JAPAN"   #路径
	# echo "`ls $filePath`"
	for file in $(ls $filePath)
	do
		# echo $file
		if [ -d "$filePath/$file" ]; then
			#变量num是指变过滤某文件夹下文件的数量
			num="$( ls -lR $filePath/$file | grep "^-"| wc -l )"
			#变量filenames是文件夹的文件
			filenames=$(ls -l $filePath/$file | grep "^-" | awk -F " " '{print $9}')
			echo "folder $file has $num files, it's $filenames" 
			# Version 2，优化脚本，将2个判断合并成1个
			if [[ $num == 1 || $num == 2 ]]; then
				for tmpFileNames in $filenames; do
					mv $filePath/$file/$tmpFileNames $filePath
				done
				rmdir $filePath/$file
			fi
		fi
	done
}
getfiles $1